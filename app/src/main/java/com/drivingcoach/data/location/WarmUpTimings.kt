package com.drivingcoach.data.location

/**
 * Timing budget for the GPS warm-up. Injected so unit tests can drive the idle ceiling on
 * virtual time instead of waiting three real minutes.
 *
 * @param intervalMs Requested update interval. 1 s matches the Track Setup screen, so
 *   handing over from warm-up to capture does not change what the chip is asked for.
 * @param idleCeilingMs How long the warm-up may run without the user starting a session.
 *   A backstop, not the primary bound: releasing the chip is normally driven by the app
 *   leaving the foreground or by recording starting. Sized well past a realistic
 *   paddock-to-line walk — at three minutes it used to expire *during* the very journey the
 *   warm-up exists to cover, discarding the fix somewhere between Home and the track edge
 *   (Incident 12).
 */
data class WarmUpTimings(
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    val idleCeilingMs: Long = DEFAULT_IDLE_CEILING_MS
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 1000L
        const val DEFAULT_IDLE_CEILING_MS = 1_800_000L
    }
}
