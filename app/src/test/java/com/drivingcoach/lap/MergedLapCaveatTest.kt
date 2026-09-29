package com.drivingcoach.lap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words a driver reads when the detector quietly lost a lap.
 *
 * This object has two ways to be wrong and they pull against each other. Say nothing
 * when a lap was swallowed and the driver is handed a wrong lap count stated as fact.
 * Say something on an ordinary session and the caveat becomes noise a driver learns to
 * scroll past, which costs more than it ever buys back. Every test here pins one side
 * or the other, and the pair of them is the point.
 *
 * The numbers used are Cabo do Mundo's: a 50 s declared fastest lap gives LD-21 a 40 s
 * floor, and the circuit is driven in the sixties by an average driver.
 */
class MergedLapCaveatTest {

    private val floorMs = 40_000L

    private fun diagnostics(
        accepted: List<Long> = listOf(100_000L),
        rejections: List<LocalLapDetector.RejectedCrossing> = emptyList(),
        minLapTimeMs: Long = floorMs
    ) = LocalLapDetector.DetectionDiagnostics(
        sampleCount = 1200,
        durationMs = 600_000,
        observedSampleRateHz = 1.0,
        startLineLengthM = 7.01,
        startLineBearingDeg = 149.8,
        angleBetweenLineAndTravelDeg = 90.0,
        acceptedCrossings = accepted.map {
            LocalLapDetector.Crossing(timestampMs = it, lateralOffsetM = 1.2, headingDeg = 59.8)
        },
        rejectedCrossings = rejections,
        lapCount = maxOf(0, accepted.size - 1),
        minLapTimeMs = minLapTimeMs
    )

    private fun tooSoon(atMs: Long) = LocalLapDetector.RejectedCrossing(
        timestampMs = atMs,
        reason = LocalLapDetector.RejectionReason.TOO_SOON,
        detail = "too soon"
    )

    private fun wide(atMs: Long) = LocalLapDetector.RejectedCrossing(
        timestampMs = atMs,
        reason = LocalLapDetector.RejectionReason.TOO_FAR_TO_THE_SIDE,
        detail = "wide",
        lateralOffsetM = 22.0
    )

    // --- Staying quiet -------------------------------------------------------------------

    /**
     * The case that decides whether this is shippable.
     *
     * A kart crossing a 15 m half-width corridor at racing speed is inside it for more
     * than one 1 Hz fix, so a single pass offers several candidates and the detector
     * refuses all but the first. Those refusals land a second or two after the crossing
     * they duplicate. They are the detector working, and a session full of them is an
     * ordinary session.
     */
    @Test
    fun `stays quiet when refusals are a second look at the pass just counted`() {
        val d = diagnostics(
            accepted = listOf(100_000L, 165_000L, 230_000L),
            rejections = listOf(tooSoon(101_200L), tooSoon(166_400L), tooSoon(231_100L))
        )

        assertNull(
            "duplicate fixes from one pass must never raise a caveat",
            MergedLapCaveat.of(d)
        )
    }

    @Test
    fun `stays quiet on a session with nothing refused at all`() {
        assertNull(MergedLapCaveat.of(diagnostics(accepted = listOf(100_000L, 165_000L))))
    }

    @Test
    fun `stays quiet when refusals were for other reasons`() {
        val d = diagnostics(
            accepted = listOf(100_000L),
            // Far enough out to clear the fraction, so only the reason keeps it quiet.
            rejections = listOf(wide(135_000L), wide(170_000L))
        )

        assertNull("only TOO_SOON is evidence of a merged lap: ${MergedLapCaveat.of(d)}", MergedLapCaveat.of(d))
    }

    @Test
    fun `stays quiet when there are no diagnostics to read`() {
        assertNull(MergedLapCaveat.of(null))
    }

    /**
     * A refusal before any crossing was accepted has nothing to measure a gap against.
     * The shipped detector cannot produce one, because the floor only applies once a
     * boundary exists - but the diagnostics are a record of what happened rather than a
     * promise about it, and reading them must not depend on that staying true.
     */
    @Test
    fun `stays quiet when a refusal has no accepted crossing before it`() {
        val d = diagnostics(
            accepted = listOf(200_000L),
            rejections = listOf(tooSoon(50_000L))
        )

        assertNull(MergedLapCaveat.of(d))
    }

    // --- Speaking up ---------------------------------------------------------------------

    /**
     * The failure itself. A driver laps in 38 s on a circuit whose catalogue says 50 s,
     * so LD-21's 40 s floor refuses the crossing, the boundary is lost, and the next
     * accepted one measures from the crossing before it. The driver is shown half their
     * laps at roughly double the time, and every check downstream agrees with it.
     */
    @Test
    fun `speaks up when a refusal landed close under the floor`() {
        val d = diagnostics(
            accepted = listOf(100_000L, 176_000L),
            rejections = listOf(tooSoon(138_000L))
        )

        val caveat = MergedLapCaveat.of(d)
        assertNotNull("a lost lap must not pass without comment", caveat)
        assertTrue("must count what was lost: $caveat", caveat!!.contains("1 pass"))
        assertTrue("must say what it means for the times: $caveat", caveat.contains("timed as one"))
    }

    @Test
    fun `counts only the refusals that look like lost laps`() {
        val d = diagnostics(
            accepted = listOf(100_000L, 176_000L, 252_000L),
            rejections = listOf(
                tooSoon(101_100L),  // duplicate fix, same pass
                tooSoon(138_000L),  // 38 s after: a lost lap
                tooSoon(177_300L),  // duplicate fix, same pass
                tooSoon(214_000L)   // 38 s after: a lost lap
            )
        )

        val caveat = MergedLapCaveat.of(d)
        assertNotNull(caveat)
        assertTrue(
            "two lost, two duplicates, so the driver must be told about two: $caveat",
            caveat!!.contains("2 passes")
        )
    }

    /**
     * A caveat reading "1 passes were" would tell the driver the app is careless before
     * it told them anything about their laps.
     */
    @Test
    fun `reads correctly for one lost lap and for several`() {
        val one = MergedLapCaveat.of(
            diagnostics(accepted = listOf(100_000L), rejections = listOf(tooSoon(138_000L)))
        )
        val two = MergedLapCaveat.of(
            diagnostics(
                accepted = listOf(100_000L, 176_000L),
                rejections = listOf(tooSoon(138_000L), tooSoon(214_000L))
            )
        )

        assertTrue("singular: $one", one!!.contains("1 pass of") && one.contains("was too soon"))
        assertTrue("plural: $two", two!!.contains("2 passes of") && two.contains("were too soon"))
    }

    // --- Where the line actually sits ----------------------------------------------------

    /**
     * Pins [MergedLapCaveat.MERGED_LAP_GAP_FRACTION] to behaviour rather than to its own
     * value, so the constant cannot be moved without a test having an opinion about it.
     */
    @Test
    fun `the boundary between noise and a lost lap sits at half the floor`() {
        val threshold = (floorMs * MergedLapCaveat.MERGED_LAP_GAP_FRACTION).toLong()
        val crossing = 100_000L

        assertNull(
            "a hair under the fraction is still read as noise",
            MergedLapCaveat.of(
                diagnostics(
                    accepted = listOf(crossing),
                    rejections = listOf(tooSoon(crossing + threshold - 1))
                )
            )
        )
        assertNotNull(
            "landing on the fraction is read as a lost lap",
            MergedLapCaveat.of(
                diagnostics(
                    accepted = listOf(crossing),
                    rejections = listOf(tooSoon(crossing + threshold))
                )
            )
        )
    }

    /**
     * The floor comes from the circuit, so the same refusal means different things on
     * different tracks. A 25 s gap is most of an untracked session's 20 s floor - a lost
     * lap - and a modest fraction of Cabo do Mundo's 40 s, where it is noise.
     */
    @Test
    fun `reads the same gap against whichever floor the circuit set`() {
        val rejections = listOf(tooSoon(115_000L))

        assertNull(
            "25 s against a 40 s floor is not yet evidence",
            MergedLapCaveat.of(diagnostics(rejections = rejections, minLapTimeMs = 40_000L))
        )
        assertNotNull(
            "25 s against a 20 s floor is a lap that was nearly a lap",
            MergedLapCaveat.of(diagnostics(rejections = rejections, minLapTimeMs = 20_000L))
        )
    }

    /**
     * Guards the divide-by-nothing case rather than the arithmetic: a floor of zero
     * would make every refusal clear the threshold and put a caveat on every session.
     */
    @Test
    fun `stays quiet when no floor was in force`() {
        assertNull(
            MergedLapCaveat.of(
                diagnostics(rejections = listOf(tooSoon(115_000L)), minLapTimeMs = 0L)
            )
        )
    }

    @Test
    fun `describes what was seen without guessing why`() {
        val caveat = MergedLapCaveat.of(
            diagnostics(accepted = listOf(100_000L), rejections = listOf(tooSoon(138_000L)))
        )!!

        // The cause is a catalogue figure slower than the driver. There is no control in
        // the app for that, so the message must not imply the driver can act on it.
        listOf("set the", "try again", "drive", "check your").forEach {
            assertEquals(
                "must not instruct the driver to do something the app gives no way to do: $caveat",
                false, caveat.lowercase().contains(it)
            )
        }
    }
}
