package com.drivingcoach.coaching

import com.drivingcoach.data.db.entity.LapEntity

/**
 * The lap the driver has already driven, in pieces, but never in one go: the fastest
 * sector 1 joined to the fastest sector 2 joined to the fastest sector 3.
 *
 * ### Why this needs a gate and a lap list does not
 *
 * A lap list reports what was detected. This takes a **minimum** over what was
 * detected, and a minimum actively seeks out the worst data in the set. A lap that
 * was counted twice (`FP-LAP-DOUBLE-COUNT`, an accepted limitation) produces two
 * half-laps whose sectors are roughly half as long as they should be - and those are
 * exactly the values `min()` will choose. The resulting "theoretical best" would be a
 * detection artefact presented as an achievement, and it would look entirely credible.
 *
 * So every rule below exists to answer one question: is this set of sectors good
 * enough that a minimum over it means anything? When the answer is no, this returns
 * null and the driver is shown nothing. A dream lap is a claim about what the driver
 * is capable of, and an unfounded one is worse than silence.
 */
object DreamLap {

    /**
     * Fewest laps with complete sectors before a dream lap may be offered.
     *
     * With two laps the "best sector" is a choice between two samples and the dream
     * lap is barely distinguishable from the better lap. Three is the point at which
     * the stitching says something the lap list does not.
     */
    const val MIN_LAPS = 3

    /**
     * Narrowest and widest share of a lap that a single sector may occupy.
     *
     * Sectors are equal thirds of distance, so on a lap driven at an even pace each
     * takes about a third of the time. A circuit with a long straight and a tight
     * complex will skew that legitimately - a sector can easily be half the lap, or
     * a fifth of it. What these bounds reject is the arithmetic signature of a lap
     * whose boundaries are wrong: a "sector" of 2% or 90% of its own lap did not come
     * from a car going round a circuit.
     */
    const val MIN_SECTOR_SHARE = 0.10
    const val MAX_SECTOR_SHARE = 0.75

    /**
     * How far under the real best lap a dream lap may plausibly fall.
     *
     * Stitching three bests from laps that are seconds apart gains tenths, not halves.
     * A theoretical lap 30% quicker than anything actually driven has not found hidden
     * pace; it has found a half-lap. The bound is deliberately generous - a genuinely
     * erratic session can yield a real 10-15% gain - and is there to catch the
     * order-of-magnitude error, not to trim honest results.
     */
    const val MAX_GAIN_FRACTION = 0.25

    /**
     * A stitched theoretical lap.
     *
     * @param sector1Ms best sector 1 across the session, and the lap it came from
     * @param totalMs the three bests added together
     * @param bestLapMs the quickest lap actually driven
     * @param gainMs how much the stitched lap is quicker than the best real lap.
     *   Zero when one lap held all three best sectors - which is a real and
     *   meaningful answer: there was nothing left on the table.
     */
    data class Result(
        val sector1Ms: Long,
        val sector1LapNumber: Int,
        val sector2Ms: Long,
        val sector2LapNumber: Int,
        val sector3Ms: Long,
        val sector3LapNumber: Int,
        val totalMs: Long,
        val bestLapMs: Long,
        val gainMs: Long
    ) {
        /** True when a single lap already held every best sector. */
        val isCompleteLap: Boolean
            get() = sector1LapNumber == sector2LapNumber && sector2LapNumber == sector3LapNumber
    }

    /**
     * Computes the dream lap, or returns null when the data does not support one.
     *
     * @param laps the session's laps
     * @param mergedLapCaveat the warning produced by `MergedLapCaveat`, or null. When
     *   present, lap detection itself has said that two laps may have been reported as
     *   one - and a sector stitched out of a merged lap is meaningless. Nothing here
     *   can detect that from the sector times alone, which is precisely why the signal
     *   has to be passed in rather than inferred.
     */
    fun of(laps: List<LapEntity>, mergedLapCaveat: String? = null): Result? {
        if (mergedLapCaveat != null) return null

        val usable = laps.filter { it.hasPlausibleSectors() }
        if (usable.size < MIN_LAPS) return null

        val best1 = usable.minByOrNull { it.sector1Ms } ?: return null
        val best2 = usable.minByOrNull { it.sector2Ms } ?: return null
        val best3 = usable.minByOrNull { it.sector3Ms } ?: return null

        val total = best1.sector1Ms + best2.sector2Ms + best3.sector3Ms

        // The best real lap is taken from the usable set, not from every lap. Comparing
        // a stitched lap against a lap whose own sectors were rejected would compare
        // two different things, and could report a "gain" that is only the difference
        // between a sound lap and an unsound one.
        val bestLap = usable.minByOrNull { it.durationMs } ?: return null

        // A dream lap can never be slower than a lap actually driven: the best lap's own
        // sectors are candidates, so the minimum is at worst that lap. If this fails,
        // the sectors do not sum to their laps and the arithmetic is not trustworthy.
        if (total > bestLap.durationMs) return null

        val gain = bestLap.durationMs - total
        if (gain > bestLap.durationMs * MAX_GAIN_FRACTION) return null

        return Result(
            sector1Ms = best1.sector1Ms,
            sector1LapNumber = best1.lapNumber,
            sector2Ms = best2.sector2Ms,
            sector2LapNumber = best2.lapNumber,
            sector3Ms = best3.sector3Ms,
            sector3LapNumber = best3.lapNumber,
            totalMs = total,
            bestLapMs = bestLap.durationMs,
            gainMs = gain
        )
    }

    /**
     * Whether a lap's sectors can be believed: all three present, summing to the lap,
     * and none of them an implausible share of it.
     */
    private fun LapEntity.hasPlausibleSectors(): Boolean {
        if (sector1Ms <= 0L || sector2Ms <= 0L || sector3Ms <= 0L) return false
        if (durationMs <= 0L) return false
        if (sector1Ms + sector2Ms + sector3Ms != durationMs) return false

        return listOf(sector1Ms, sector2Ms, sector3Ms).all { sector ->
            val share = sector.toDouble() / durationMs
            share in MIN_SECTOR_SHARE..MAX_SECTOR_SHARE
        }
    }
}
