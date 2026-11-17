package com.drivingcoach.coaching

import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.lap.LapReplayHarness
import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.lap.MergedLapCaveat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Holds [DreamLap] to its promise: it never presents a detection artefact as a
 * *reliable* achievement (OC-13, OC-14, OC-35).
 *
 * A dream lap is a minimum over the session's sectors, and a minimum actively seeks
 * out the worst data in the set. A number now always comes back when any lap has
 * sector times, so the tests pin the label and the reasons: Reliable exactly when the
 * strict gates pass, Estimate with the right reason otherwise, and the worst evidence
 * kept out wherever it can be located.
 */
class DreamLapTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ==================== The feature working ====================

    @Test
    fun `stitches the best sector from each lap`() {
        val laps = listOf(
            lap(1, s1 = 14_000, s2 = 16_000, s3 = 15_000),   // 45.0s, best S1
            lap(2, s1 = 15_000, s2 = 15_000, s3 = 16_000),   // 46.0s, best S2
            lap(3, s1 = 15_500, s2 = 16_500, s3 = 14_500)    // 46.5s, best S3
        )

        val dream = DreamLap.of(laps)

        assertNotNull("three clean laps should support a dream lap", dream)
        assertEquals(1, dream!!.sector1LapNumber)
        assertEquals(2, dream.sector2LapNumber)
        assertEquals(3, dream.sector3LapNumber)
        assertEquals(14_000 + 15_000 + 14_500L, dream.totalMs)
    }

    @Test
    fun `the gain is the distance to the best lap actually driven`() {
        val laps = listOf(
            lap(1, s1 = 14_000, s2 = 16_000, s3 = 15_000),   // 45.0s
            lap(2, s1 = 15_000, s2 = 15_000, s3 = 16_000),   // 46.0s
            lap(3, s1 = 15_500, s2 = 16_500, s3 = 14_500)    // 46.5s
        )

        val dream = DreamLap.of(laps)!!

        assertEquals("best real lap", 45_000L, dream.bestLapMs)
        assertEquals("45.000 - 43.500", 1_500L, dream.gainMs)
    }

    @Test
    fun `a dream lap is never slower than a lap actually driven`() {
        // The best lap's own sectors are always candidates, so the minimum is at worst
        // that lap. If this ever fails the arithmetic has come apart somewhere upstream.
        val laps = (1..8).map { n ->
            val duration = 44_000L + n * 400L
            lap(n, s1 = duration / 3, s2 = duration / 3, s3 = duration - 2 * (duration / 3))
        }

        val dream = DreamLap.of(laps)!!

        assertTrue(
            "dream ${dream.totalMs} ms is slower than the best real lap ${dream.bestLapMs} ms",
            dream.totalMs <= dream.bestLapMs
        )
        assertTrue("gain cannot be negative", dream.gainMs >= 0L)
    }

    @Test
    fun `one lap holding every best sector is reported as a complete lap`() {
        val laps = listOf(
            lap(1, s1 = 14_000, s2 = 14_000, s3 = 14_000),   // 42.0s, best everywhere
            lap(2, s1 = 15_000, s2 = 15_000, s3 = 16_000),
            lap(3, s1 = 15_500, s2 = 16_500, s3 = 15_500)
        )

        val dream = DreamLap.of(laps)!!

        assertTrue("lap 1 held all three bests", dream.isCompleteLap)
        assertEquals("there was nothing left on the table", 0L, dream.gainMs)
    }

    // ==================== Reliable: every gate passes ====================

    @Test
    fun `clean laps are reliable with no reasons and the same numbers of() returns`() {
        val laps = listOf(
            lap(1, s1 = 14_000, s2 = 16_000, s3 = 15_000),
            lap(2, s1 = 15_000, s2 = 15_000, s3 = 16_000),
            lap(3, s1 = 15_500, s2 = 16_500, s3 = 14_500)
        )

        val available = available(DreamLap.evaluate(laps))

        assertEquals(DreamLap.Confidence.RELIABLE, available.confidence)
        assertTrue(available.reasons.isEmpty())
        assertEquals(DreamLap.of(laps), available.result)
        assertEquals(43_500L, available.result.totalMs)
    }

    @Test
    fun `of() stays strict and returns nothing for an estimate`() {
        val two = listOf(
            lap(1, s1 = 14_000, s2 = 16_000, s3 = 15_000),
            lap(2, s1 = 15_000, s2 = 15_000, s3 = 16_000)
        )

        assertEquals(DreamLap.Confidence.ESTIMATE, available(DreamLap.evaluate(two)).confidence)
        assertNull("a number that rewards or records must have passed every gate", DreamLap.of(two))
    }

    // ==================== Estimate: merged laps (OC-14) ====================

    @Test
    fun `a merged lap caveat that cannot be located makes every lap an estimate`() {
        val laps = listOf(
            lap(1, s1 = 14_000, s2 = 16_000, s3 = 15_000),
            lap(2, s1 = 15_000, s2 = 15_000, s3 = 16_000),
            lap(3, s1 = 15_500, s2 = 16_500, s3 = 14_500)
        )

        assertNotNull("control: these laps are reliable without the caveat", DreamLap.of(laps))
        val available = available(
            DreamLap.evaluate(laps, mergedLapCaveat = "Two laps may have been reported as one.")
        )

        assertEquals(DreamLap.Confidence.ESTIMATE, available.confidence)
        assertEquals(listOf(DreamLap.EstimateReason.MergedLapsSuspected), available.reasons)
        assertEquals("all laps are still used", 43_500L, available.result.totalMs)
        assertNull("of() never hands out a number lap detection doubts", DreamLap.of(laps, "caveat"))
    }

    @Test
    fun `the lap that swallowed a start line pass is left out and named`() {
        val laps = listOf(
            lap(1, s1 = 14_000, s2 = 16_000, s3 = 15_000),
            lap(2, s1 = 15_000, s2 = 15_000, s3 = 16_000),
            lap(3, s1 = 15_500, s2 = 16_500, s3 = 14_500),
            // Lap 4 is two laps timed as one: its sectors are each about two thirds of a
            // lap. Its own S1 is not the minimum, but nothing about it can be trusted.
            lap(4, s1 = 13_000, s2 = 37_000, s3 = 40_000)
        )
        val swallowedAt = laps[3].startTs + 45_000L

        val available = available(
            DreamLap.evaluate(laps, mergedLapCaveat = "caveat", suspectCrossingTimesMs = listOf(swallowedAt))
        )

        assertEquals(DreamLap.Confidence.ESTIMATE, available.confidence)
        assertEquals(listOf(DreamLap.EstimateReason.SuspectLapsLeftOut(listOf(4))), available.reasons)
        assertEquals("lap 4's 13 s sector 1 must not be stitched in", 1, available.result.sector1LapNumber)
        assertEquals(43_500L, available.result.totalMs)
    }

    @Test
    fun `a pass exactly on a lap's start or end marks that lap - the window is inclusive`() {
        val laps = (1..4).map { n -> lap(n, s1 = 15_000, s2 = 15_000, s3 = 15_000 + n * 100L) }

        val atStart = available(
            DreamLap.evaluate(laps, "caveat", suspectCrossingTimesMs = listOf(laps[2].startTs))
        )
        assertEquals(listOf(DreamLap.EstimateReason.SuspectLapsLeftOut(listOf(3))), atStart.reasons)

        val atEnd = available(
            DreamLap.evaluate(laps, "caveat", suspectCrossingTimesMs = listOf(laps[1].endTs))
        )
        assertEquals(listOf(DreamLap.EstimateReason.SuspectLapsLeftOut(listOf(2))), atEnd.reasons)

        val justOutside = available(
            DreamLap.evaluate(laps, "caveat", suspectCrossingTimesMs = listOf(laps[1].endTs + 1))
        )
        assertEquals(
            "a pass in the gap between laps belongs to no lap",
            listOf(DreamLap.EstimateReason.MergedLapsSuspected), justOutside.reasons
        )
    }

    @Test
    fun `when every lap is suspect all laps are used and labelled an estimate`() {
        val laps = listOf(
            lap(1, s1 = 14_000, s2 = 16_000, s3 = 15_000),
            lap(2, s1 = 15_000, s2 = 15_000, s3 = 16_000)
        )
        val inEach = laps.map { it.startTs + 10_000L }

        val available = available(DreamLap.evaluate(laps, "caveat", inEach))

        assertEquals(
            listOf(DreamLap.EstimateReason.MergedLapsSuspected, DreamLap.EstimateReason.FewLaps(2)),
            available.reasons
        )
        assertEquals(44_000L, available.result.totalMs)
    }

    @Test
    fun `suspect crossing times are ignored without a caveat`() {
        val laps = (1..3).map { n -> lap(n, s1 = 15_000, s2 = 15_000, s3 = 15_000 + n * 100L) }

        val available = available(
            DreamLap.evaluate(laps, mergedLapCaveat = null, suspectCrossingTimesMs = listOf(laps[0].startTs))
        )

        assertEquals(DreamLap.Confidence.RELIABLE, available.confidence)
    }

    @Test
    fun `a half-lap artefact can never produce a fabricated gain`() {
        // The signature of FP-LAP-DOUBLE-COUNT: one "lap" of roughly half the duration,
        // whose sectors are the quickest in the session by a wide margin and are exactly
        // what an ungated minimum would reach for. Its sectors are internally consistent,
        // so arithmetic cannot exclude it; what is guaranteed is that it never invents
        // pace - every sector of a half-lap beats every real one, so it becomes the best
        // lap itself and the gain is zero. The caveat is the real defence (see below).
        val clean = (1..4).map { n ->
            val duration = 45_000L + n * 500L
            lap(n, s1 = duration / 3, s2 = duration / 3, s3 = duration - 2 * (duration / 3))
        }
        val halfLap = lap(5, s1 = 7_500, s2 = 7_500, s3 = 7_500)   // 22.5s

        val dream = available(DreamLap.evaluate(clean + halfLap)).result

        assertEquals(
            "stitching a half-lap in must not invent pace over the laps on the list",
            0L, dream.gainMs
        )
    }

    @Test
    fun `the merged lap caveat keeps a double counted lap from ever being reliable`() {
        val clean = (1..4).map { n ->
            val duration = 45_000L + n * 500L
            lap(n, s1 = duration / 3, s2 = duration / 3, s3 = duration - 2 * (duration / 3))
        }
        val halfLap = lap(5, s1 = 7_500, s2 = 7_500, s3 = 7_500)

        assertNull(
            "when detection suspects a merged or split lap, nothing reliable is stitched",
            DreamLap.of(clean + halfLap, mergedLapCaveat = "Lap 5 may be a partial lap.")
        )
        val located = available(
            DreamLap.evaluate(clean + halfLap, "caveat", listOf(halfLap.startTs + 1_000L))
        )
        assertEquals("with the lap located it is left out", 1, located.result.bestLapNumber)
        assertEquals(45_500L, located.result.totalMs)
        assertEquals(listOf(DreamLap.EstimateReason.SuspectLapsLeftOut(listOf(5))), located.reasons)
    }

    // ==================== Estimate: few laps ====================

    @Test
    fun `two laps give an estimate based on 2 laps`() {
        val two = listOf(
            lap(1, s1 = 14_000, s2 = 16_000, s3 = 15_000),
            lap(2, s1 = 15_000, s2 = 15_000, s3 = 16_000)
        )

        val available = available(DreamLap.evaluate(two))

        assertEquals(DreamLap.Confidence.ESTIMATE, available.confidence)
        assertEquals(listOf(DreamLap.EstimateReason.FewLaps(2)), available.reasons)
        assertEquals(44_000L, available.result.totalMs)
        assertEquals(1_000L, available.result.gainMs)
    }

    @Test
    fun `one lap is its own dream lap with a gain of zero`() {
        val one = listOf(lap(7, s1 = 14_000, s2 = 16_000, s3 = 15_000))

        val available = available(DreamLap.evaluate(one))

        assertEquals(listOf(DreamLap.EstimateReason.FewLaps(1)), available.reasons)
        assertEquals(45_000L, available.result.totalMs)
        assertEquals(0L, available.result.gainMs)
        assertEquals(7, available.result.bestLapNumber)
        assertTrue(available.result.isCompleteLap)
    }

    // ==================== Estimate: unusual sectors and gain ====================

    @Test
    fun `lopsided sectors are used only when no lap has believable shares`() {
        val laps = (1..4).map { n ->
            // Sector 1 is 2% of the lap: no car goes round a third of a circuit in that.
            lap(n, s1 = 900, s2 = 22_050 + n * 10L, s3 = 22_050)
        }

        assertNull("a 2% sector is never reliable", DreamLap.of(laps))
        val available = available(DreamLap.evaluate(laps))

        assertEquals(listOf(DreamLap.EstimateReason.UnusualSectorShares), available.reasons)
        assertEquals(900L + 22_060L + 22_050L, available.result.totalMs)
    }

    @Test
    fun `believable laps are preferred over lopsided ones even when fewer`() {
        val good = listOf(
            lap(1, s1 = 14_000, s2 = 16_000, s3 = 15_000),
            lap(2, s1 = 15_000, s2 = 15_000, s3 = 16_000)
        )
        val lopsided = (3..5).map { n -> lap(n, s1 = 900, s2 = 22_050, s3 = 22_050) }

        val available = available(DreamLap.evaluate(good + lopsided))

        assertEquals(listOf(DreamLap.EstimateReason.FewLaps(2)), available.reasons)
        assertEquals("the 0.9 s sector is not stitched in", 44_000L, available.result.totalMs)
    }

    @Test
    fun `a gain beyond a quarter of the best lap is shown as an estimate`() {
        // Each lap is slow in two sectors, so the stitched lap is 50% under the best.
        val laps = listOf(
            lap(1, s1 = 10_000, s2 = 25_000, s3 = 25_000),
            lap(2, s1 = 25_000, s2 = 10_000, s3 = 25_000),
            lap(3, s1 = 25_000, s2 = 25_000, s3 = 10_000)
        )

        assertNull(DreamLap.of(laps))
        val available = available(DreamLap.evaluate(laps))

        assertEquals(listOf(DreamLap.EstimateReason.LargeGain), available.reasons)
        assertEquals(30_000L, available.result.totalMs)
        assertEquals(30_000L, available.result.gainMs)
    }

    @Test
    fun `reasons accumulate in a fixed order`() {
        val laps = listOf(
            lap(1, s1 = 8_000, s2 = 25_000, s3 = 25_000),     // 58 s; 8 + 10 + 25 = 43 s
            lap(2, s1 = 25_000, s2 = 10_000, s3 = 25_000),
            lap(3, s1 = 30_000, s2 = 30_000, s3 = 30_000)
        )

        val available = available(DreamLap.evaluate(laps, "caveat", listOf(laps[2].startTs + 5L)))

        assertEquals(
            listOf(
                DreamLap.EstimateReason.SuspectLapsLeftOut(listOf(3)),
                DreamLap.EstimateReason.FewLaps(2),
                DreamLap.EstimateReason.LargeGain
            ),
            available.reasons
        )
    }

    // ==================== Not available: nothing to stitch ====================

    @Test
    fun `laps with no sectors leave nothing to stitch`() {
        // Sessions recorded before sectors existed, or laps the splitter refused.
        val laps = (1..5).map { n -> lap(n, s1 = 0, s2 = 0, s3 = 0, durationMs = 45_000L) }

        assertEquals(
            DreamLap.Evaluation.NotAvailable(DreamLap.Withheld.NoSectorTimes),
            DreamLap.evaluate(laps)
        )
        assertNull(DreamLap.of(laps))
    }

    @Test
    fun `no laps at all leave nothing to stitch`() {
        assertEquals(
            DreamLap.Evaluation.NotAvailable(DreamLap.Withheld.NoSectorTimes),
            DreamLap.evaluate(emptyList())
        )
    }

    @Test
    fun `laps whose sectors do not sum to the lap have no sector times to stitch`() {
        val laps = (1..4).map { n ->
            lap(n, s1 = 15_000, s2 = 15_000, s3 = 15_000, durationMs = 60_000L)
        }

        assertEquals(
            DreamLap.Evaluation.NotAvailable(DreamLap.Withheld.NoSectorTimes),
            DreamLap.evaluate(laps)
        )
    }

    // ==================== Against a real recorded session ====================

    @Test
    fun `the Baltar session produces an honest dream lap end to end`() {
        // baltar2 is the Incident 15 session. The app of the day recorded 2 laps; the
        // repaired detector finds the 12 the driver actually drove, of 72-85 s each.
        // Running it all the way through - detection, sector derivation, stitching -
        // is the only check that the three agree on real data rather than on fixtures
        // built to suit them.
        val fixture = LapReplayHarness.load("baltar2", tempFolder.newFolder())
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(
            fixture.telemetryFile, fixture.startLine
        )

        val detected = (outcome.result as? LocalLapDetector.DetectionResult.Success)?.laps
            ?: error("baltar2 should detect laps: ${LapReplayHarness.describe(outcome.result)}")
        val caveat = MergedLapCaveat.of(outcome.diagnostics)

        val entities = detected.mapIndexed { index, lap ->
            LapEntity(
                sessionId = 1L,
                lapNumber = index + 1,
                startTs = lap.startTs,
                endTs = lap.endTs,
                durationMs = lap.durationMs,
                sector1Ms = lap.sector1Ms,
                sector2Ms = lap.sector2Ms,
                sector3Ms = lap.sector3Ms
            )
        }

        val evaluation = available(
            DreamLap.evaluate(entities, caveat, MergedLapCaveat.suspectCrossingTimes(outcome.diagnostics))
        )
        assertEquals("12 clean laps with derived sectors are reliable", DreamLap.Confidence.RELIABLE, evaluation.confidence)
        val dream = evaluation.result

        val bestReal = entities.minOf { it.durationMs }
        assertEquals("the comparison must be against the quickest real lap", bestReal, dream.bestLapMs)
        assertTrue(
            "a stitched lap of ${dream.totalMs} ms cannot beat the best real lap of $bestReal ms",
            dream.totalMs <= bestReal
        )
        assertTrue(
            "a gain of ${dream.gainMs} ms on a ${bestReal} ms lap is not pace, it is an artefact",
            dream.gainMs <= bestReal * DreamLap.MAX_GAIN_FRACTION
        )
        assertEquals(
            "the stitched total must be its three parts",
            dream.sector1Ms + dream.sector2Ms + dream.sector3Ms,
            dream.totalMs
        )
    }

    // ==================== Helpers ====================

    private fun available(evaluation: DreamLap.Evaluation): DreamLap.Evaluation.Available =
        evaluation as? DreamLap.Evaluation.Available ?: error("expected a dream lap, got $evaluation")

    private fun lap(
        lapNumber: Int,
        s1: Long,
        s2: Long,
        s3: Long,
        durationMs: Long = s1 + s2 + s3
    ) = LapEntity(
        sessionId = 1L,
        lapNumber = lapNumber,
        startTs = lapNumber * 100_000L,
        endTs = lapNumber * 100_000L + durationMs,
        durationMs = durationMs,
        sector1Ms = s1,
        sector2Ms = s2,
        sector3Ms = s3
    )
}
