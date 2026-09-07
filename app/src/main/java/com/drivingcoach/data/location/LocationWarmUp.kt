package com.drivingcoach.data.location

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts acquiring a GPS fix *before* the user reaches the screen that needs one.
 *
 * ### Why this exists
 *
 * Track Setup used to be the first thing in the app to touch the GNSS chip, so the entire
 * cold time-to-first-fix landed on the one screen where the user can do nothing but stare at
 * a disabled "Capture" button. A cold fix has to download ephemeris from the satellite
 * signal itself; no amount of software removes that wait. It can only be *moved* somewhere
 * the user is already busy, and *made visible* so they know when to walk out to the line.
 *
 * ### What it deliberately does not do
 *
 * It reports readiness and nothing else — never a position. See [GpsReadiness]: a start line
 * captured from a stale warm-up fix would silently corrupt every lap time in the session.
 * Capture still reads live updates and still enforces its own accuracy gate.
 *
 * ### Cost control
 *
 * High-accuracy updates are not free, so the warm-up is bounded by the user's *task* rather
 * than by whichever screen started it: it is released when the app leaves the foreground,
 * when recording takes the chip over, and by [WarmUpTimings.idleCeilingMs] as a backstop if
 * the user never starts a session. Binding it to a screen instead made the Home → Track
 * Setup navigation its own stop condition — see Incident 12.
 *
 * Process-scoped, holding no context, so warmth survives navigation between Home and Track
 * Setup instead of being torn down and re-acquired at the worst possible moment.
 *
 * Not thread-safe: [start] and [stop] are called from lifecycle callbacks on the main thread.
 */
@Singleton
class LocationWarmUp(
    private val locationUpdates: LocationUpdates,
    private val metricsStore: GpsAcquisitionMetricsStore,
    private val timings: WarmUpTimings,
    private val scope: CoroutineScope,
    private val now: () -> Long
) {

    /**
     * Injected form. The clock is a constructor parameter rather than a captured default so
     * unit tests can run the idle ceiling on virtual time; Dagger cannot supply a lambda, so
     * it uses this overload and the real clock.
     */
    @Inject
    constructor(
        locationUpdates: LocationUpdates,
        metricsStore: GpsAcquisitionMetricsStore,
        timings: WarmUpTimings,
        @ApplicationScope scope: CoroutineScope
    ) : this(locationUpdates, metricsStore, timings, scope, { System.currentTimeMillis() })

    private val _readiness = MutableStateFlow<GpsReadiness>(GpsReadiness.Idle)
    val readiness: StateFlow<GpsReadiness> = _readiness.asStateFlow()

    private var updatesJob: Job? = null
    private var idleCeilingJob: Job? = null

    private var subscribedAtMs: Long = 0L
    private var timeToFirstFixMs: Long? = null
    private var timeToAccurateFixMs: Long? = null

    /**
     * Begins (or extends) warm-up.
     *
     * Idempotent: calling it again while already warming refreshes the idle ceiling but does
     * not restart the subscription, so navigating Home → Track Setup keeps the fix already
     * acquired rather than throwing it away.
     *
     * A no-op without location permission — the feature degrades to exactly the old
     * behaviour rather than crashing or prompting from somewhere the user did not expect.
     */
    fun start() {
        if (!locationUpdates.hasPermission()) {
            Log.d(TAG, "no location permission; warm-up skipped")
            return
        }

        restartIdleCeiling()

        if (updatesJob != null) return

        subscribedAtMs = now()
        timeToFirstFixMs = null
        timeToAccurateFixMs = null
        _readiness.value = GpsReadiness.Acquiring

        Log.d(TAG, "warm-up started")

        updatesJob = scope.launch {
            locationUpdates.updates(timings.intervalMs).collect(::onFix)
        }
    }

    /** Releases the chip. Safe to call when not started. */
    fun stop() {
        if (updatesJob == null && idleCeilingJob == null) return

        updatesJob?.cancel()
        updatesJob = null
        // May be cancelling the job this call is running inside (the idle ceiling firing).
        // Everything below is non-suspending, so it still completes.
        idleCeilingJob?.cancel()
        idleCeilingJob = null
        _readiness.value = GpsReadiness.Idle

        Log.d(TAG, "warm-up stopped")
    }

    private suspend fun onFix(fix: LocationFix) {
        val elapsed = now() - subscribedAtMs

        _readiness.value = if (fix.accuracyM <= GpsReadiness.READY_ACCURACY_M) {
            GpsReadiness.Ready(fix.accuracyM)
        } else {
            GpsReadiness.Acquiring
        }

        // Each milestone is recorded once per warm-up cycle, so a 1 Hz stream costs at most
        // two writes rather than one per fix.
        val isFirstFix = timeToFirstFixMs == null
        val isFirstAccurateFix =
            timeToAccurateFixMs == null && fix.accuracyM <= GpsReadiness.READY_ACCURACY_M

        if (!isFirstFix && !isFirstAccurateFix) return

        if (isFirstFix) {
            timeToFirstFixMs = elapsed
            Log.i(TAG, "first fix after ${elapsed}ms (±${fix.accuracyM}m)")
        }
        if (isFirstAccurateFix) {
            timeToAccurateFixMs = elapsed
            Log.i(TAG, "accurate fix after ${elapsed}ms (±${fix.accuracyM}m)")
        }

        metricsStore.record(
            timeToFirstFixMs = timeToFirstFixMs,
            timeToAccurateFixMs = timeToAccurateFixMs,
            recordedAtMs = now()
        )
    }

    private fun restartIdleCeiling() {
        idleCeilingJob?.cancel()
        idleCeilingJob = scope.launch {
            delay(timings.idleCeilingMs)
            Log.d(TAG, "idle ceiling reached; releasing GPS")
            stop()
        }
    }

    companion object {
        private const val TAG = "LocationWarmUp"
    }
}
