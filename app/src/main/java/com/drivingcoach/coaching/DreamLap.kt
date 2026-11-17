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
 * enough that a minimum over it means anything?
 *
 * ### Reliable or Estimate, never silence (OC-13, OC-14, OC-35)
 *
 * Withholding the number whenever a rule failed left most real sessions with no dream
 * lap at all, and a missing number teaches the driver nothing. So the stitching is now
 * always done whenever any lap carries sector times, and the rules decide the *label*
 * rather than the existence of the answer:
 *
 * - **Reliable** - every gate that used to make the number available passes, and the
 *   number is exactly the one those gates used to allow.
 * - **Estimate** - at least one gate failed. The worst evidence is still kept out where
 *   it can be located (a lap that swallowed a start-line pass is left out; laps with
 *   implausible sector shares are used only when nothing better exists), and the card
 *   says in a few words why the number deserves less trust.
 *
 * The only remaining "not available" is a session in which no lap has sector times:
 * there is nothing to add up.
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
     * @param bestLapMs the quickest lap actually driven, and [bestLapNumber] its number
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
        val bestLapNumber: Int,
        val gainMs: Long
    ) {
        /** True when a single lap already held every best sector. */
        val isCompleteLap: Boolean
            get() = sector1LapNumber == sector2LapNumber && sector2LapNumber == sector3LapNumber
    }

    /** How far the stitched number can be trusted (OC-35). */
    enum class Confidence {
        /** Every gate passed: the number is what the strict rules always produced. */
        RELIABLE,

        /** Calculated anyway; see [Evaluation.Available.reasons] for why it is softer. */
        ESTIMATE
    }

    /** Why a dream lap is labelled an estimate. Each maps to one short line on the card. */
    sealed class EstimateReason {
        /**
         * Lap detection refused a start-line pass inside these laps, so each may be two
         * laps timed as one. They were left out of the stitching (OC-14).
         */
        data class SuspectLapsLeftOut(val lapNumbers: List<Int>) : EstimateReason()

        /**
         * Lap detection suspects merged laps but none could be left out - every lap
         * with sector times was suspect, or the refused passes could not be placed.
         */
        object MergedLapsSuspected : EstimateReason()

        /** Only [laps] laps went into the stitching - fewer than [MIN_LAPS]. */
        data class FewLaps(val laps: Int) : EstimateReason()

        /** No lap's sectors fell inside the believable share of their lap. */
        object UnusualSectorShares : EstimateReason()

        /** The stitched lap beats the best real lap by more than [MAX_GAIN_FRACTION]. */
        object LargeGain : EstimateReason()

        /**
         * The stitched lap came out slower than a lap actually driven. Cannot happen when
         * every sector set sums to its lap, which is checked; kept so arithmetic that
         * somehow disagrees is labelled rather than presented as reliable.
         */
        object SectorsInconsistent : EstimateReason()

        /**
         * The merged-lap signal was unknown, no lap near the median lap time carried
         * sector times, and so laps far from the typical time were used (OC-33).
         */
        object OffPaceLapsUsed : EstimateReason()
    }

    /** Why there is no dream lap at all. Only one case remains. */
    sealed class Withheld {
        /** No lap carries sector times that add up to the lap: nothing to stitch. */
        object NoSectorTimes : Withheld()
    }

    /** Either a dream lap with its label, or the reason there is none. */
    sealed class Evaluation {
        data class Available(
            val result: Result,
            val confidence: Confidence,
            val reasons: List<EstimateReason>
        ) : Evaluation() {
            /** Adds a reason; any reason makes the number an estimate. */
            fun withReason(reason: EstimateReason): Available =
                copy(confidence = Confidence.ESTIMATE, reasons = reasons + reason)
        }

        data class NotAvailable(val reason: Withheld) : Evaluation()
    }

    /**
     * The dream lap only when it is [Confidence.RELIABLE], otherwise null.
     *
     * Deliberately strict: anything that wants a number to *reward* or *record* - as
     * opposed to the coaching card, which can say "estimate" - must not be handed one
     * the gates did not pass. See [evaluate] for the labelled answer.
     */
    fun of(laps: List<LapEntity>, mergedLapCaveat: String? = null): Result? =
        (evaluate(laps, mergedLapCaveat) as? Evaluation.Available)
            ?.takeIf { it.confidence == Confidence.RELIABLE }
            ?.result

    /**
     * Computes the dream lap, labelled Reliable or Estimate, or says why there is none.
     *
     * @param laps the session's laps
     * @param mergedLapCaveat the warning produced by `MergedLapCaveat`, or null. When
     *   present, lap detection itself has said that two laps may have been reported as
     *   one - and a sector stitched out of a merged lap is meaningless. Nothing here
     *   can detect that from the sector times alone, which is precisely why the signal
     *   has to be passed in rather than inferred. Its presence alone rules out
     *   [Confidence.RELIABLE].
     * @param suspectCrossingTimesMs `MergedLapCaveat.suspectCrossingTimes`: when each
     *   swallowed pass happened. A lap whose window `[startTs, endTs]` contains one -
     *   ends **inclusive**, because wrongly leaving a sound lap out costs only a label,
     *   while wrongly keeping a merged lap in corrupts the number - is left out.
     *   Ignored when [mergedLapCaveat] is null.
     */
    fun evaluate(
        laps: List<LapEntity>,
        mergedLapCaveat: String? = null,
        suspectCrossingTimesMs: List<Long> = emptyList()
    ): Evaluation {
        val timed = laps.filter { it.hasSectorTimes() }
        if (timed.isEmpty()) return Evaluation.NotAvailable(Withheld.NoSectorTimes)

        val reasons = mutableListOf<EstimateReason>()

        // 1. Merged laps: leave out the laps that swallowed a pass, where they can be named.
        val pool = if (mergedLapCaveat == null) {
            timed
        } else {
            val suspects = timed.filter { lap ->
                suspectCrossingTimesMs.any { t -> t >= lap.startTs && t <= lap.endTs }
            }
            if (suspects.isEmpty() || suspects.size == timed.size) {
                reasons += EstimateReason.MergedLapsSuspected
                timed
            } else {
                reasons += EstimateReason.SuspectLapsLeftOut(suspects.map { it.lapNumber }.sorted())
                timed - suspects.toSet()
            }
        }

        // 2. Sector shares: the believable laps when there are any, else whatever there is.
        val plausible = pool.filter { it.hasPlausibleShares() }
        val usable = if (plausible.isNotEmpty()) {
            plausible
        } else {
            reasons += EstimateReason.UnusualSectorShares
            pool
        }

        // 3. Too few laps for the stitching to say more than the lap list.
        if (usable.size < MIN_LAPS) reasons += EstimateReason.FewLaps(usable.size)

        val best1 = usable.minBy { it.sector1Ms }
        val best2 = usable.minBy { it.sector2Ms }
        val best3 = usable.minBy { it.sector3Ms }

        val total = best1.sector1Ms + best2.sector2Ms + best3.sector3Ms

        // The best real lap is taken from the usable set, not from every lap. Comparing
        // a stitched lap against a lap whose own sectors were rejected would compare
        // two different things, and could report a "gain" that is only the difference
        // between a sound lap and an unsound one.
        val bestLap = usable.minBy { it.durationMs }

        // A dream lap can never be slower than a lap actually driven: the best lap's own
        // sectors are candidates, so the minimum is at worst that lap. If this fails,
        // the sectors do not sum to their laps and the arithmetic is not trustworthy.
        if (total > bestLap.durationMs) reasons += EstimateReason.SectorsInconsistent

        val gain = (bestLap.durationMs - total).coerceAtLeast(0L)
        if (gain > bestLap.durationMs * MAX_GAIN_FRACTION) reasons += EstimateReason.LargeGain

        return Evaluation.Available(
            result = Result(
                sector1Ms = best1.sector1Ms,
                sector1LapNumber = best1.lapNumber,
                sector2Ms = best2.sector2Ms,
                sector2LapNumber = best2.lapNumber,
                sector3Ms = best3.sector3Ms,
                sector3LapNumber = best3.lapNumber,
                totalMs = total,
                bestLapMs = bestLap.durationMs,
                bestLapNumber = bestLap.lapNumber,
                gainMs = gain
            ),
            confidence = if (reasons.isEmpty()) Confidence.RELIABLE else Confidence.ESTIMATE,
            reasons = reasons.toList()
        )
    }

    /** All three sectors present and adding up to the lap: something to stitch. */
    private fun LapEntity.hasSectorTimes(): Boolean =
        sector1Ms > 0L && sector2Ms > 0L && sector3Ms > 0L && durationMs > 0L &&
            sector1Ms + sector2Ms + sector3Ms == durationMs

    /** None of the sectors an implausible share of the lap. Assumes [hasSectorTimes]. */
    private fun LapEntity.hasPlausibleShares(): Boolean =
        listOf(sector1Ms, sector2Ms, sector3Ms).all { sector ->
            val share = sector.toDouble() / durationMs
            share in MIN_SECTOR_SHARE..MAX_SECTOR_SHARE
        }
}
