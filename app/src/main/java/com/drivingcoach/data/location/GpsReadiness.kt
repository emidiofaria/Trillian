package com.drivingcoach.data.location

/**
 * How close the GNSS chip is to being usable for capturing a start/finish line.
 *
 * Deliberately carries *no* [android.location.Location]. The warm-up exists to shorten the
 * wait, never to lower the bar: a point captured from a warm-up fix could be seconds or
 * minutes stale, and a stale start line silently corrupts every lap time in the session.
 * Capture keeps reading live updates on the Track Setup screen.
 */
sealed interface GpsReadiness {

    /** Nothing subscribed — warm-up stopped, or location permission not granted. */
    data object Idle : GpsReadiness

    /** Subscribed, but no fix yet or the fix is still coarser than [READY_ACCURACY_M]. */
    data object Acquiring : GpsReadiness

    /** A fix accurate enough to capture a start line (SRS TS-04). */
    data class Ready(val accuracyM: Float) : GpsReadiness

    companion object {
        /**
         * Mirrors the Track Setup capture gate (SRS TS-04, `MAX_GPS_ACCURACY_M`). Held here
         * rather than imported from the UI layer so the data layer does not depend upwards;
         * the two must be changed together.
         */
        const val READY_ACCURACY_M = 10.0f
    }
}
