package com.drivingcoach.coaching

import com.drivingcoach.data.db.entity.LapEntity
import kotlin.math.abs

/**
 * The laps that describe how the driver actually drives, as opposed to the out-lap,
 * the in-lap, a lap behind traffic, or a lap that lap detection got wrong.
 *
 * The reference is the **median** lap time, never the mean: a single 167 s out-lap in a
 * session of 65 s laps drags the mean far enough that the out-lap starts to look normal.
 *
 * The same filter is also the guard that lets a Dream Lap be shown for a session whose
 * merged-lap signal is unknown (OC-33): a merged lap is about twice the typical lap and
 * a half-lap about half of it, so both fall well outside the tolerance.
 */
object CleanLaps {

    /** How far from the median lap time a lap may be and still count as representative. */
    const val TOLERANCE_FRACTION = 0.15

    fun of(laps: List<LapEntity>): List<LapEntity> {
        val durations = laps.map { it.durationMs }.filter { it > 0L }
        if (durations.isEmpty()) return emptyList()

        val median = medianOf(durations)
        return laps.filter { it.durationMs > 0L && isRepresentative(it.durationMs, median) }
    }

    /** Median of non-empty [durations]; the mean of the middle two for an even count. */
    fun medianOf(durations: List<Long>): Double {
        val sorted = durations.sorted()
        return if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else {
            sorted[sorted.size / 2].toDouble()
        }
    }

    fun isRepresentative(durationMs: Long, median: Double): Boolean =
        median > 0.0 && abs(durationMs - median) / median <= TOLERANCE_FRACTION
}
