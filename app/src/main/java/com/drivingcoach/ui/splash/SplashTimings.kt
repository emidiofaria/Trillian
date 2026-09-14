package com.drivingcoach.ui.splash

/**
 * Timing budget for the branded loading screen.
 *
 * Injected so instrumented tests can collapse the minimum display time to zero
 * instead of paying the brand pause on every test case.
 *
 * @param minDisplayMs SRS UI-02 — minimum time the brand moment stays on screen once the
 *   introduction window has been spent.
 * @param introDisplayMs SRS UI-02 — minimum display time for the first
 *   [SplashTimings.INTRO_LAUNCH_COUNT] launches, sized so the engineering manifesto can
 *   actually be read. The manifesto is 14 words; at ~240 wpm that is ~3.5 s, plus roughly
 *   0.6 s before the eye finds the card (attention lands on the emblem first). The previous
 *   flat 1200 ms budget showed perhaps the title.
 * @param timeoutMs SRS UI-03 — ceiling on the *essential* preferences read. Measured cold
 *   starts spend up to ~3.5 s here on a low-end device, so this is deliberately generous:
 *   it exists to stop an indefinite hang, not to race normal startup.
 * @param warmUpTimeoutMs Ceiling on the *optional* Room warm-up. Exceeding it costs only
 *   the pending-upload count, never the destination decision.
 * @param visibilityTimeoutMs Ceiling on waiting for the system splash to report that it has
 *   handed the window over. Only reached if that report never arrives, in which case the
 *   hold falls back to being measured from startup. Kept short: it is dead time when it
 *   fires, and on every measured launch the handoff arrives long before it is needed.
 */
data class SplashTimings(
    val minDisplayMs: Long = DEFAULT_MIN_DISPLAY_MS,
    val introDisplayMs: Long = DEFAULT_INTRO_DISPLAY_MS,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val warmUpTimeoutMs: Long = DEFAULT_WARM_UP_TIMEOUT_MS,
    val visibilityTimeoutMs: Long = DEFAULT_VISIBILITY_TIMEOUT_MS
) {
    companion object {
        const val DEFAULT_MIN_DISPLAY_MS = 1200L
        const val DEFAULT_INTRO_DISPLAY_MS = 4000L
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val DEFAULT_WARM_UP_TIMEOUT_MS = 2000L
        const val DEFAULT_VISIBILITY_TIMEOUT_MS = 2000L

        /**
         * Number of launches that get the longer introduction hold. Deliberately more than
         * one: the very first launch continues straight into onboarding, where the user has
         * plenty else to attend to, so a single exposure is easy to miss entirely.
         */
        const val INTRO_LAUNCH_COUNT = 3
    }
}
