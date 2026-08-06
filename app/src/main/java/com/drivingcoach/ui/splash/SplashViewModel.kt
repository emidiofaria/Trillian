package com.drivingcoach.ui.splash

import android.util.Log
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.R
import com.drivingcoach.data.api.AuthInterceptor
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.di.IoDispatcher
import com.drivingcoach.ui.onboarding.OnboardingFragment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Drives the branded loading screen (SRS UI-01 … UI-04).
 *
 * Progress reported here tracks real startup work — DataStore read, Room open,
 * pending-upload lookup — rather than a decorative timer. All of it runs off the main
 * thread, which is what allows [com.drivingcoach.ui.MainActivity] to drop its former
 * `runBlocking` DataStore read and the ANR risk that came with it.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val sessionDao: SessionDao,
    private val timings: SplashTimings,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    data class UiState(
        val progress: Int = PROGRESS_PREFERENCES_START,
        @StringRes val stepLabel: Int = R.string.splash_step_preferences,
        val destination: SplashDestination? = null,
        val usedFallback: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Warm-up result, kept for diagnostics and Home's upload banner. */
    var pendingUploadCount: Int = 0
        private set

    @Volatile
    private var skipRequested = false
    private var started = false

    /** Idempotent — safe to call again after a configuration change. */
    fun start(now: () -> Long = { System.currentTimeMillis() }) {
        if (started) return
        started = true

        viewModelScope.launch {
            val startedAt = now()

            val resolved = withTimeoutOrNull(timings.timeoutMs) { resolveStartupState() }
            Log.d(TAG, "startup resolved=$resolved in ${now() - startedAt}ms")

            // SRS UI-03: a slow or failed initialisation must never block the user.
            //
            // The fallback is ONBOARDING, not LOGIN. If the preferences read fails we do not
            // know whether this user has onboarded, and the two mistakes are not equally
            // costly: sending an onboarded user through onboarding again is a recoverable
            // annoyance that still ends at Home, whereas sending a *fresh* user to Login
            // skips permission granting entirely and leaves the app unable to record.
            val destination = resolved ?: SplashDestination.ONBOARDING

            // SRS UI-02: hold the brand moment for the minimum display time, walking the
            // bar to 100% across whatever time is left. A skip request cuts this short.
            awaitMinimumDisplay(startedAt, now)

            _uiState.value = _uiState.value.copy(
                progress = PROGRESS_COMPLETE,
                stepLabel = R.string.splash_step_ready,
                destination = destination,
                usedFallback = resolved == null
            )
        }
    }

    /**
     * User tapped the splash (SRS UI-02). This only shortens the cosmetic hold — real
     * initialisation still has to finish, so we can never navigate to a blind destination.
     */
    fun skip() {
        skipRequested = true
    }

    private suspend fun resolveStartupState(): SplashDestination {
        publish(PROGRESS_PREFERENCES_START, R.string.splash_step_preferences)

        val prefsStart = System.currentTimeMillis()
        val preferences = withContext(ioDispatcher) { dataStore.data.first() }
        Log.d(TAG, "datastore read took ${System.currentTimeMillis() - prefsStart}ms")
        val onboardingComplete =
            preferences[OnboardingFragment.KEY_ONBOARDING_COMPLETE] ?: false
        val hasToken = !preferences[AuthInterceptor.KEY_JWT].isNullOrBlank()

        publish(PROGRESS_DATABASE_START, R.string.splash_step_database)

        // Optional warm-up. Forces Room to open the database and apply any pending migration
        // before the first screen queries it. Bounded separately and deliberately non-fatal:
        // a slow or broken database must not change where the user lands.
        val roomStart = System.currentTimeMillis()
        val pendingUploads = runCatching {
            withTimeoutOrNull(timings.warmUpTimeoutMs) {
                withContext(ioDispatcher) { sessionDao.getPendingUploadSessions() }
            }
        }.getOrNull()
        Log.d(TAG, "room warm-up took ${System.currentTimeMillis() - roomStart}ms, result=${pendingUploads?.size}")

        publish(PROGRESS_UPLOADS_START, R.string.splash_step_uploads)
        pendingUploadCount = pendingUploads?.size ?: 0

        publish(PROGRESS_RESOLVE_START, R.string.splash_step_ready)

        return when {
            !onboardingComplete -> SplashDestination.ONBOARDING
            hasToken -> SplashDestination.HOME
            else -> SplashDestination.LOGIN
        }
    }

    private suspend fun awaitMinimumDisplay(startedAt: Long, now: () -> Long) {
        if (skipRequested) return

        val remaining = timings.minDisplayMs - (now() - startedAt)
        if (remaining <= 0) return

        val steps = (remaining / PROGRESS_TICK_MS).toInt().coerceAtLeast(1)
        val stepDuration = (remaining / steps).coerceAtLeast(1L)
        val from = _uiState.value.progress

        for (index in 0 until steps) {
            if (skipRequested) return
            delay(stepDuration)
            val fraction = (index + 1).toFloat() / steps
            publish(
                from + ((PROGRESS_COMPLETE - from) * fraction).toInt(),
                R.string.splash_step_ready
            )
        }
    }

    private fun publish(progress: Int, @StringRes stepLabel: Int) {
        _uiState.value = _uiState.value.copy(
            progress = progress.coerceIn(PROGRESS_PREFERENCES_START, PROGRESS_COMPLETE),
            stepLabel = stepLabel
        )
    }

    companion object {
        private const val TAG = "SplashViewModel"

        const val PROGRESS_PREFERENCES_START = 0
        const val PROGRESS_DATABASE_START = 25
        const val PROGRESS_UPLOADS_START = 55
        const val PROGRESS_RESOLVE_START = 80
        const val PROGRESS_COMPLETE = 100

        private const val PROGRESS_TICK_MS = 60L
    }
}
