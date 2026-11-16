package com.drivingcoach.ui.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.R
import com.drivingcoach.data.db.DrivingCoachDatabase
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.dao.TrophyDao
import com.drivingcoach.data.profile.DriverProfileStore
import com.drivingcoach.data.profile.DriverProfileStore.NameValidation
import com.drivingcoach.data.profile.LocalUser
import com.drivingcoach.progression.TrophyType
import com.drivingcoach.di.IoDispatcher
import com.drivingcoach.lap.LapDiagnosticsWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class UserProfile(
    val displayName: String,
    val initials: String
)

/** The Profile stats row (SRS DP-11): career distance, time on track, level trophies. */
data class UserStats(
    val drivenKm: Double,
    /** Sum of every recorded lap's duration. */
    val drivenMs: Long,
    /** Level trophies only; session medals are not counted here. */
    val trophies: Int
)

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val stats: UserStats = UserStats(0.0, 0L, 0),
    val error: String? = null
)

sealed class ProfileEvent {
    /** Data was cleared; the driver must onboard again (SRS DR-07). */
    object NavigateToOnboarding : ProfileEvent()
    data class ShowMessage(@StringRes val messageLabel: Int) : ProfileEvent()
    data class ShowError(val message: String) : ProfileEvent()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val lapDao: LapDao,
    private val trophyDao: TrophyDao,
    private val database: DrivingCoachDatabase,
    private val dataStore: DataStore<Preferences>,
    private val driverProfileStore: DriverProfileStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    /**
     * Session ownership key. Intentionally *not* derived from the driver's display name
     * (SRS DP-01, DR-06): the name is editable, and keying rows to it would orphan a
     * driver's entire history the moment they corrected a typo. Shared with the recorder
     * through [LocalUser], so the two can never drift apart again.
     */
    private val currentUserId = LocalUser.ID

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                val displayName = driverProfileStore.readProfile()?.displayName
                    ?: DEFAULT_DISPLAY_NAME

                val profile = UserProfile(
                    displayName = displayName,
                    initials = driverProfileStore.initialsOf(displayName)
                )

                _uiState.value = _uiState.value.copy(isLoading = false, profile = profile, error = null)
            } catch (e: Exception) {
                _uiState.value = ProfileUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load profile"
                )
                return@launch
            }

            // Kept live, so a session finished while Profile is open is counted at once.
            combine(
                sessionDao.getAllSessionsForUser(currentUserId),
                lapDao.observeAllLaps(),
                trophyDao.observeAll()
            ) { sessions, laps, trophies ->
                val ids = sessions.map { it.id }.toSet()
                UserStats(
                    drivenKm = sessions.sumOf { it.distanceM ?: 0.0 } / 1000.0,
                    drivenMs = laps.filter { it.sessionId in ids }.sumOf { it.durationMs.coerceAtLeast(0L) },
                    trophies = trophies.count {
                        it.sessionId in ids && TrophyType.fromName(it.type)?.countsTowardLevels == true
                    }
                )
            }.flowOn(ioDispatcher)
                .catch { Log.e(TAG, "profile stats failed", it) }
                .collect { stats -> _uiState.value = _uiState.value.copy(isLoading = false, stats = stats) }
        }
    }

    /** Validates a candidate name without committing it, so the dialog can gate its button. */
    fun validateName(name: String): NameValidation = driverProfileStore.validate(name)

    /**
     * Renames the driver (SRS DR-06).
     *
     * Purely cosmetic — no session, lap or telemetry record references the name, so this
     * cannot affect recorded data.
     */
    fun renameDriver(name: String) {
        viewModelScope.launch {
            val result = runCatching { driverProfileStore.saveName(name) }.getOrNull()

            if (result is NameValidation.Valid) {
                _uiState.value = _uiState.value.copy(
                    profile = UserProfile(
                        displayName = result.value,
                        initials = driverProfileStore.initialsOf(result.value)
                    )
                )
                _events.emit(ProfileEvent.ShowMessage(R.string.driver_name_edit_success))
            } else {
                _events.emit(ProfileEvent.ShowError("Could not save your name"))
            }
        }
    }

    /**
     * Erases everything this app holds about the driver (SRS DR-07).
     *
     * Previously presented as "Sign Out", which was misleading: with no backend there is no
     * remote copy, so the action has always been an irreversible local wipe. The name now
     * matches the behaviour, and the behaviour is complete — the old implementation cleared
     * Room and preferences but left every telemetry JSONL file on disk, silently accumulating
     * recordings the driver believed they had deleted.
     *
     * Order matters: file paths are read *before* the tables are cleared, because once the
     * rows are gone there is no record of which files belong to this app. Files are deleted
     * by their recorded path only — never by emptying the telemetry directory — so nothing
     * the app did not create can be caught in the sweep. Each recording's lap-diagnostics
     * sidecar (`LapDiagnosticsWriter.SUFFIX`) is derived from that same path and goes with it.
     */
    fun clearUserData() {
        viewModelScope.launch {
            try {
                val filePaths = withContext(ioDispatcher) { sessionDao.getAllRawFilePaths() }

                // Room refuses to run this on the main thread; until v3.07 it was called from
                // viewModelScope directly, so the wipe threw and nothing was ever cleared.
                withContext(ioDispatcher) { database.clearAllTables() }

                withContext(ioDispatcher) {
                    filePaths.forEach { path ->
                        val telemetry = File(path)
                        listOf(telemetry, File(telemetry.parentFile, telemetry.name + LapDiagnosticsWriter.SUFFIX))
                            .forEach { file ->
                                runCatching { file.takeIf { it.isFile }?.delete() }
                                    .onFailure { Log.w(TAG, "could not delete $file", it) }
                            }
                    }
                }

                // Full reset, including the onboarding flag, so the driver returns to a
                // genuinely first-run app rather than a half-configured one.
                dataStore.edit { it.clear() }

                _events.emit(ProfileEvent.NavigateToOnboarding)
            } catch (e: Exception) {
                Log.e(TAG, "clear user data failed", e)
                _events.emit(
                    ProfileEvent.ShowError(e.message ?: "Failed to clear data")
                )
            }
        }
    }

    companion object {
        private const val TAG = "ProfileViewModel"

        /**
         * Only reachable if the profile read fails, since naming is mandatory before Home.
         * A neutral label beats an empty header.
         */
        const val DEFAULT_DISPLAY_NAME = "Driver"
    }
}
