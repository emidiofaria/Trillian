package com.drivingcoach.ui.splash

/**
 * Timing budget for the branded loading screen.
 *
 * Injected so instrumented tests can collapse the minimum display time to zero
 * instead of paying the brand pause on every test case.
 *
 * @param minDisplayMs SRS UI-02 — minimum time the brand moment stays on screen.
 * @param timeoutMs SRS UI-03 — ceiling on the *essential* preferences read. Measured cold
 *   starts spend up to ~3.5 s here on a low-end device, so this is deliberately generous:
 *   it exists to stop an indefinite hang, not to race normal startup.
 * @param warmUpTimeoutMs Ceiling on the *optional* Room warm-up. Exceeding it costs only
 *   the pending-upload count, never the destination decision.
 */
data class SplashTimings(
    val minDisplayMs: Long = DEFAULT_MIN_DISPLAY_MS,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val warmUpTimeoutMs: Long = DEFAULT_WARM_UP_TIMEOUT_MS
) {
    companion object {
        const val DEFAULT_MIN_DISPLAY_MS = 1200L
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val DEFAULT_WARM_UP_TIMEOUT_MS = 2000L
    }
}
