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
import com.drivingcoach.data.profile.DriverProfileStore
import com.drivingcoach.data.profile.DriverProfileStore.NameValidation
import com.drivingcoach.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class UserProfile(
    val displayName: String,
    val initials: String
)

data class UserStats(
    val totalSessions: Int,
    val totalLaps: Int,
    val bestLapMs: Long?
)

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val stats: UserStats = UserStats(0, 0, null),
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
     * driver's entire history the moment they corrected a typo.
     */
    private val currentUserId = "demo_user"

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

                // Load aggregate stats
                val sessions = sessionDao.getAllSessionsForUser(currentUserId).first()
                var totalLaps = 0
                var bestLapMs: Long? = null

                for (session in sessions) {
                    val laps = lapDao.getLapsForSession(session.id).first()
                    totalLaps += laps.size
                    val sessionBest = laps.minByOrNull { it.durationMs }
                    if (sessionBest != null && (bestLapMs == null || sessionBest.durationMs < bestLapMs)) {
                        bestLapMs = sessionBest.durationMs
                    }
                }

                val stats = UserStats(
                    totalSessions = sessions.size,
                    totalLaps = totalLaps,
                    bestLapMs = bestLapMs
                )

                _uiState.value = ProfileUiState(
                    isLoading = false,
                    profile = profile,
                    stats = stats
                )
            } catch (e: Exception) {
                _uiState.value = ProfileUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load profile"
                )
            }
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
     * the app did not create can be caught in the sweep.
     */
    fun clearUserData() {
        viewModelScope.launch {
            try {
                val filePaths = withContext(ioDispatcher) { sessionDao.getAllRawFilePaths() }

                database.clearAllTables()

                withContext(ioDispatcher) {
                    filePaths.forEach { path ->
                        runCatching { File(path).takeIf { it.isFile }?.delete() }
                            .onFailure { Log.w(TAG, "could not delete telemetry file $path", it) }
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
