package com.drivingcoach.data.location

import android.location.Location
import android.os.SystemClock

/**
 * How old a GNSS fix may be before it must not be trusted as *where the user is now*.
 *
 * ### Why this exists (Incident 12, finding F4)
 *
 * Keeping the receiver warm across the walk from the paddock to the start line fixed the
 * complaint that was reported, and made a quieter one reachable: the last fix the app holds
 * is now much more likely to be from where the user *was*. Captured as Point A, it offsets
 * every lap time in the session by the same amount, and the times still look like lap times.
 * A wrong number that looks right is worse than a visible failure.
 *
 * ### The rule is deliberately forgiving at the edges
 *
 * A fix with no monotonic timestamp, or one stamped in the future by a clock that moved,
 * is treated as fresh. Both are cases where the age cannot be established, and refusing to
 * capture would strand the user at the track edge — the very experience Incident 12 was
 * raised about. The accuracy gate still applies in those cases.
 *
 * [Location.getElapsedRealtimeNanos] is used rather than [Location.getTime], because wall
 * clock time can be adjusted by the network mid-session while the monotonic clock cannot.
 */
object FixFreshness {

    /**
     * The freshness window for capturing a start line.
     *
     * Track Setup requests fixes once a second, so three seconds is three missed updates:
     * long enough that ordinary jitter never trips the gate, short enough that the user
     * cannot have walked a meaningful distance since.
     */
    const val MAX_FIX_AGE_MS = 3_000L

    /** Age of [fixElapsedRealtimeNanos] in milliseconds, or `0` when it cannot be known. */
    fun ageMs(fixElapsedRealtimeNanos: Long, nowElapsedRealtimeNanos: Long): Long {
        if (fixElapsedRealtimeNanos <= 0L) return 0L
        val ageNanos = nowElapsedRealtimeNanos - fixElapsedRealtimeNanos
        if (ageNanos <= 0L) return 0L
        return ageNanos / 1_000_000L
    }

    fun isFresh(
        fixElapsedRealtimeNanos: Long,
        nowElapsedRealtimeNanos: Long,
        maxAgeMs: Long = MAX_FIX_AGE_MS
    ): Boolean = ageMs(fixElapsedRealtimeNanos, nowElapsedRealtimeNanos) <= maxAgeMs

    /** Convenience for callers holding a real fix. */
    fun isFresh(location: Location, maxAgeMs: Long = MAX_FIX_AGE_MS): Boolean = isFresh(
        fixElapsedRealtimeNanos = location.elapsedRealtimeNanos,
        nowElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
        maxAgeMs = maxAgeMs
    )
}
