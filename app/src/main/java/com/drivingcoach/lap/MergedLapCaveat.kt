package com.drivingcoach.lap

/**
 * Qualifies a lap count that may have had a lap folded into another one.
 *
 * ## The failure this exists for
 *
 * LD-21 raises the minimum gap between crossings to 80 % of the circuit's declared
 * fastest lap. A driver quicker than that figure crosses the line sooner than the
 * detector is prepared to believe, the crossing is rejected as
 * [LocalLapDetector.RejectionReason.TOO_SOON], and - because a rejected candidate is
 * deliberately not treated as a lap boundary - the next accepted crossing measures
 * from the one *before* it. Two laps arrive as one lap of roughly double the time.
 *
 * Nothing downstream catches this. A merged lap sits inside the declared envelope and
 * well above LD-22's 5 m/s discard floor, so it looks exactly like a lap somebody drove
 * slowly, and [LocalLapDetector.TrackPriors.lapsArePlausible] passes it. No tightening
 * of that check could ever separate the two cases: a merged lap is a credible lap. The
 * only place the truth survives is the rejection log, which is why this reads from
 * there and not from the laps.
 *
 * Until now that log went no further than the diagnostics file. The success path
 * reported `"N laps detected"` whatever had been thrown away, so the driver was handed
 * a wrong answer stated with complete confidence - the shape of failure incident 15 was
 * about, arriving by a different route.
 *
 * ## Why a fraction of the floor, and not any rejection at all
 *
 * `TOO_SOON` fires routinely on healthy sessions. At racing speed a kart is inside the
 * [LocalLapDetector.DETECTION_HALF_WIDTH_M] corridor for more than one 1 Hz fix, so a
 * single pass can offer several candidate crossings and every one after the first is
 * correctly refused. Those arrive a second or two after the accepted crossing.
 *
 * A swallowed lap is the other shape: it arrives close to the floor but under it. The
 * gap tells the two apart, and [MERGED_LAP_GAP_FRACTION] is where the line is drawn.
 * Warning on every `TOO_SOON` would put a caveat on ordinary sessions and teach drivers
 * to disregard it, which is worse than saying nothing.
 *
 * ## Why it offers no remedy
 *
 * [NoLapsExplanation] ends with something the driver can do, because there is something:
 * set the line again. Here there is not. The cause is a catalogue figure that is slower
 * than the driver, which no control in the app exposes. Naming the observation and
 * stopping is the honest end of the sentence; inventing an instruction the app cannot
 * honour is the mistake that object's `theAdviceMustBeSomethingTheAppLetsYouDo` guards
 * against, and it would be no better made here.
 */
object MergedLapCaveat {

    /**
     * How far towards the minimum gap a refused crossing must land before it is read as
     * a lost lap rather than a second look at the pass just counted.
     *
     * Half is deliberately far from both shapes it has to separate: same-pass duplicates
     * sit at a few per cent of the floor, a swallowed lap at eighty or ninety. Nothing
     * observed lands near the middle, so the exact value is not load-bearing - what
     * matters is that it is nowhere near either cluster.
     */
    const val MERGED_LAP_GAP_FRACTION = 0.5

    /**
     * A sentence to append to the lap count, or null when the count needs no qualifying.
     *
     * Null is the common answer and the intended one. This speaks only when the session
     * contains positive evidence that a boundary was lost.
     */
    fun of(diagnostics: LocalLapDetector.DetectionDiagnostics?): String? {
        diagnostics ?: return null

        val floorMs = diagnostics.minLapTimeMs
        if (floorMs <= 0L) return null
        val threshold = floorMs * MERGED_LAP_GAP_FRACTION

        // Sorted because the gap is measured against the accepted crossing immediately
        // before each refusal, and nothing guarantees the detector recorded them in
        // order once the three-stage fallback has had a turn.
        val acceptedTimes = diagnostics.acceptedCrossings.map { it.timestampMs }.sorted()
        if (acceptedTimes.isEmpty()) return null

        val swallowed = diagnostics.rejectedCrossings.count { rejected ->
            if (rejected.reason != LocalLapDetector.RejectionReason.TOO_SOON) return@count false
            // A refusal with no accepted crossing before it has no gap to measure and is
            // not evidence of anything. It cannot arise from the current detector, which
            // only applies the floor once a boundary exists, but the diagnostics are a
            // record rather than a guarantee.
            val previous = acceptedTimes.lastOrNull { it < rejected.timestampMs } ?: return@count false
            (rejected.timestampMs - previous) >= threshold
        }
        if (swallowed == 0) return null

        val passes = if (swallowed == 1) "pass" else "passes"
        val were = if (swallowed == 1) "was" else "were"
        return "$swallowed $passes of the start line $were too soon after the one before " +
            "to be counted -- some laps here may be timed as one."
    }
}
