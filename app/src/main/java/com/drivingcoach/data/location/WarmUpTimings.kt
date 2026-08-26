package com.drivingcoach.data.location

/**
 * Timing budget for the GPS warm-up. Injected so unit tests can drive the idle ceiling on
 * virtual time instead of waiting three real minutes.
 *
 * @param intervalMs Requested update interval. 1 s matches the Track Setup screen, so
 *   handing over from warm-up to capture does not change what the chip is asked for.
 * @param idleCeilingMs How long the warm-up may run without the user starting a session.
 *   Sized to the paddock habit the feature is built around — open the app, then walk out to
 *   the start line a minute or two later. Someone who instead sits browsing old sessions
 *   stops paying the GNSS bill after this.
 */
data class WarmUpTimings(
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    val idleCeilingMs: Long = DEFAULT_IDLE_CEILING_MS
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 1000L
        const val DEFAULT_IDLE_CEILING_MS = 180_000L
    }
}
