package com.drivingcoach.lap

/**
 * Turns an empty detection result into something the driver can act on.
 *
 * Kept out of the ViewModel deliberately. This is the text a driver reads at the
 * end of a session that produced nothing, which makes it worth testing directly
 * rather than through a screen -- the previous wording survived for months
 * because nothing ever asserted on it.
 */
object NoLapsExplanation {

    /**
     * Explains an empty result in terms of what the session actually contained.
     *
     * The old message was "No laps detected. Complete at least 2 laps." In
     * incident 14 the driver had completed three laps, so the app told him to do
     * something he had already done and gave him nothing to act on. The detector
     * knew why it had found nothing -- passes went by 16-30m to the side of a
     * start point captured while the phone was standing still -- and none of that
     * reached the screen.
     *
     * This reports what was observed rather than guessing at a cause, and only
     * says a thing when the evidence for it is present.
     */
    fun of(diagnostics: LocalLapDetector.DetectionDiagnostics?): String {
        diagnostics ?: return "No laps detected."

        val missedBySide = diagnostics.rejectedCrossings
            .filter { it.reason == LocalLapDetector.RejectionReason.TOO_FAR_TO_THE_SIDE }
        val nearest = missedBySide.mapNotNull { it.lateralOffsetM }.minOrNull()

        // Nothing came near the start point at all. Naming a distance here would
        // be inventing one, so say only what is known.
        if (nearest == null) {
            return if (diagnostics.pathRepeats == true) {
                "No laps detected. You drove a repeating circuit, but never passed " +
                    "close enough to the start point to time it. Set the start line " +
                    "again on the part of the track you actually drive."
            } else {
                "No laps detected."
            }
        }

        val passes = missedBySide.size
        val lapsWord = if (passes == 1) "pass" else "passes"
        val builder = StringBuilder(
            "No laps detected, but $passes $lapsWord went by the start point " +
                "%.0fm to the side of it -- too far to count.".format(nearest)
        )

        // Only offered when the evidence for it is in this session's own numbers.
        if (diagnostics.captureWindowSpeedMs != null &&
            diagnostics.captureWindowSpeedMs < LocalLapDetector.MIN_ANCHOR_SPEED_MS
        ) {
            builder.append(
                " The start line was set while you were stopped, when GPS is at its " +
                    "least accurate, so it may be recorded off to one side of the track."
            )
        }
        builder.append(" Set the start line again while driving past it.")
        return builder.toString()
    }
}
