package com.drivingcoach.coaching

import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.lap.SectorSplitter
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The four beginner fundamentals shown on the Coach tab (CF-01 to CF-08).
 *
 * The tip text is always shown. Two of the tips can also be *assessed* from the
 * session, and only where 1 Hz GPS can honestly tell:
 *
 * - **Final corner** - the speed the car carries over the start/finish line, which
 *   on a circuit whose line sits on the main straight is the exit speed of the last
 *   corner. A driver who gets that corner right repeatably crosses the line at much
 *   the same speed every lap; a large gap between the typical lap and the best one
 *   means the exit is being compromised.
 * - **Braking reference points** - the spread of each sector's time across laps. At
 *   1 Hz the braking point itself moves by a whole sample (15-20 m) between laps for
 *   no reason but the GPS clock, so it cannot be measured. A sector whose time wanders
 *   by a tenth of its length lap to lap, however, is a driver without fixed references.
 *
 * Slow-in-fast-out and smooth steering are not assessed: at 1 Hz neither brake release
 * nor steering input is visible in the data, and a verdict built on it would be noise
 * presented as coaching.
 *
 * Every assessment uses clean laps only ([CleanLaps]) and needs at least
 * [MIN_CLEAN_LAPS] of them; otherwise the tip is shown without a verdict.
 */
object FundamentalsAnalyzer {

    enum class Tip { FINAL_CORNER, BRAKING_REFERENCES, SLOW_IN_FAST_OUT, SMOOTH_STEERING }

    enum class Verdict {
        /** The driver already does this well: the tip is shown green with a tick. */
        GOOD,
        /** The session shows this is where the driver should work. */
        NEEDS_WORK,
        /** Not assessed - not enough data, or not measurable at all. */
        NOT_ASSESSED
    }

    data class Fundamental(
        val tip: Tip,
        val verdict: Verdict,
        /** Only for [Tip.BRAKING_REFERENCES] with [Verdict.NEEDS_WORK]: the sector to name. */
        val focusSector: Int? = null
    )

    const val MIN_CLEAN_LAPS = 3

    /** Median line speed within this of the best line speed counts as a repeatable exit. */
    const val LINE_SPEED_GOOD_GAP_KMH = 6.0

    /** Median line speed this far below the best means the exit is being compromised. */
    const val LINE_SPEED_NEEDS_WORK_GAP_KMH = 10.0

    /** How far from the line instant a fix may be and still describe the line speed. */
    const val LINE_SPEED_MAX_OFFSET_MS = 2_000L

    /** Every sector's lap-to-lap spread at or below this share of its mean is consistent. */
    const val SECTOR_SPREAD_GOOD = 0.05

    /** Any sector spreading this much is a driver without fixed references. */
    const val SECTOR_SPREAD_NEEDS_WORK = 0.10

    /** Every tip, unassessed, in display order. Shown before telemetry has loaded. */
    val DEFAULT: List<Fundamental> = Tip.values().map { Fundamental(it, Verdict.NOT_ASSESSED) }

    fun analyze(laps: List<LapEntity>, samples: List<TelemetrySample>): List<Fundamental> {
        val clean = CleanLaps.of(laps).sortedBy { it.lapNumber }
        val sorted = samples.sortedBy { it.timestampMs }
        return listOf(
            finalCorner(clean, sorted),
            brakingReferences(clean, sorted),
            Fundamental(Tip.SLOW_IN_FAST_OUT, Verdict.NOT_ASSESSED),
            Fundamental(Tip.SMOOTH_STEERING, Verdict.NOT_ASSESSED)
        )
    }

    private fun finalCorner(clean: List<LapEntity>, samples: List<TelemetrySample>): Fundamental {
        val speeds = clean.mapNotNull { lineSpeedKmh(samples, it.endTs) }
        if (speeds.size < MIN_CLEAN_LAPS) return Fundamental(Tip.FINAL_CORNER, Verdict.NOT_ASSESSED)

        val gap = speeds.max() - median(speeds)
        val verdict = when {
            gap <= LINE_SPEED_GOOD_GAP_KMH -> Verdict.GOOD
            gap >= LINE_SPEED_NEEDS_WORK_GAP_KMH -> Verdict.NEEDS_WORK
            else -> Verdict.NOT_ASSESSED
        }
        return Fundamental(Tip.FINAL_CORNER, verdict)
    }

    private fun brakingReferences(clean: List<LapEntity>, samples: List<TelemetrySample>): Fundamental {
        val sectors = clean.mapNotNull { SectorSplitter.split(samples, it.startTs, it.endTs) }
        if (sectors.size < MIN_CLEAN_LAPS) {
            return Fundamental(Tip.BRAKING_REFERENCES, Verdict.NOT_ASSESSED)
        }

        val spreads = listOf(
            spread(sectors.map { it.sector1Ms.toDouble() }),
            spread(sectors.map { it.sector2Ms.toDouble() }),
            spread(sectors.map { it.sector3Ms.toDouble() })
        )
        val worst = spreads.indices.maxBy { spreads[it] }

        return when {
            spreads.all { it <= SECTOR_SPREAD_GOOD } ->
                Fundamental(Tip.BRAKING_REFERENCES, Verdict.GOOD)
            spreads[worst] >= SECTOR_SPREAD_NEEDS_WORK ->
                Fundamental(Tip.BRAKING_REFERENCES, Verdict.NEEDS_WORK, focusSector = worst + 1)
            else -> Fundamental(Tip.BRAKING_REFERENCES, Verdict.NOT_ASSESSED)
        }
    }

    /** Speed of the fix nearest to [ts], or null when no fix is close enough to stand for it. */
    private fun lineSpeedKmh(samples: List<TelemetrySample>, ts: Long): Double? {
        val nearest = samples.minByOrNull { abs(it.timestampMs - ts) } ?: return null
        if (abs(nearest.timestampMs - ts) > LINE_SPEED_MAX_OFFSET_MS) return null
        return nearest.speedMs * 3.6
    }

    /** Population standard deviation as a share of the mean. */
    private fun spread(values: List<Double>): Double {
        val mean = values.average()
        if (mean <= 0.0) return 0.0
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance) / mean
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        return if (s.size % 2 == 0) (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0 else s[s.size / 2]
    }
}
