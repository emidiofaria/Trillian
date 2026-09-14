package com.drivingcoach.ui.splash

/**
 * Where the splash screen hands control to once startup state has been resolved.
 */
enum class SplashDestination {
    ONBOARDING,

    /** First-run local driver naming (SRS DR-02). The V1 replacement for [LOGIN]. */
    DRIVER_NAME,

    /**
     * Firebase sign-in. Unreachable in V1 — retained so that restoring authentication in V2
     * is a change to the resolution rules rather than a reconstruction of the startup path.
     * See SRS UM-01 … UM-17 (deferred to V2).
     */
    LOGIN,

    HOME
}
