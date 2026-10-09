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
 * Holds [DreamLap] to its one hard promise: it never presents a detection artefact
 * as an achievement.
 *
 * A dream lap is a minimum over the session's sectors, and a minimum actively seeks
 * out the worst data in the set. Every test below is either the feature working or
 * the gate refusing; there is deliberately no test that merely checks a number comes
 * back, because a number always comes back - the question is whether it means anything.
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

    // ==================== The gate refusing ====================

    @Test
    fun `no dream lap when lap detection warned about a merged lap`() {
        val laps = listOf(
            lap(1, s1 = 14_000, s2 = 16_000, s3 = 15_000),
            lap(2, s1 = 15_000, s2 = 15_000, s3 = 16_000),
            lap(3, s1 = 15_500, s2 = 16_500, s3 = 14_500)
        )

        assertNotNull("control: these laps do support a dream lap", DreamLap.of(laps))
        assertNull(
            "a session whose laps may have been merged cannot support a stitched lap",
            DreamLap.of(laps, mergedLapCaveat = "Two laps may have been reported as one.")
        )
    }

    @Test
    fun `a half-lap artefact can never produce a fabricated gain`() {
        // The signature of FP-LAP-DOUBLE-COUNT: one "lap" of roughly half the duration,
        // whose sectors are the quickest in the session by a wide margin and are exactly
        // what an ungated minimum would reach for.
        //
        // The artefact cannot be excluded by inspection - its sectors are internally
        // consistent and evenly shared, because half a lap driven normally looks like a
        // lap driven normally, only shorter. What can be guaranteed is that stitching it
        // in never invents pace: either it is itself the quickest "lap", in which case the
        // dream lap merely restates it with a gain of zero and claims nothing the lap list
        // does not already show, or mixing it with real laps pushes the total above the
        // best lap and the whole result is refused.
        //
        // In practice it is always the former: every sector of a half-lap is shorter than
        // every real one, so the artefact wins all three and becomes the best lap itself.
        // That is the limit of what arithmetic can do here, and it is why the caveat below
        // is the actual defence.
        val clean = (1..4).map { n ->
            val duration = 45_000L + n * 500L
            lap(n, s1 = duration / 3, s2 = duration / 3, s3 = duration - 2 * (duration / 3))
        }
        val halfLap = lap(5, s1 = 7_500, s2 = 7_500, s3 = 7_500)   // 22.5s

        val dream = DreamLap.of(clean + halfLap)

        if (dream != null) {
            assertEquals(
                "stitching a half-lap in must not invent pace over the laps on the list",
                0L, dream.gainMs
            )
        }
    }

    @Test
    fun `the merged lap caveat is the real defence against a double counted lap`() {
        // Since a half-lap cannot be recognised from its sector times, the signal has to
        // come from detection, which is the one place that knows a crossing was refused
        // for being too soon. This is why the caveat is a parameter rather than inferred.
        val clean = (1..4).map { n ->
            val duration = 45_000L + n * 500L
            lap(n, s1 = duration / 3, s2 = duration / 3, s3 = duration - 2 * (duration / 3))
        }
        val halfLap = lap(5, s1 = 7_500, s2 = 7_500, s3 = 7_500)

        assertNull(
            "when detection suspects a merged or split lap, nothing is stitched at all",
            DreamLap.of(clean + halfLap, mergedLapCaveat = "Lap 5 may be a partial lap.")
        )
    }

    @Test
    fun `no dream lap below the minimum number of usable laps`() {
        val two = listOf(
            lap(1, s1 = 14_000, s2 = 16_000, s3 = 15_000),
            lap(2, s1 = 15_000, s2 = 15_000, s3 = 16_000)
        )

        assertNull(
            "with ${DreamLap.MIN_LAPS - 1} laps the stitching says nothing the lap list does not",
            DreamLap.of(two)
        )
    }

    @Test
    fun `laps with no sectors are not usable`() {
        // Sessions recorded before sectors existed, or laps the splitter refused.
        val laps = (1..5).map { n -> lap(n, s1 = 0, s2 = 0, s3 = 0, durationMs = 45_000L) }

        assertNull("zero sectors carry no information", DreamLap.of(laps))
    }

    @Test
    fun `laps whose sectors do not sum to the lap are discarded`() {
        val laps = (1..4).map { n ->
            lap(n, s1 = 15_000, s2 = 15_000, s3 = 15_000, durationMs = 60_000L)
        }

        assertNull(
            "sectors that do not add up to their own lap are not trustworthy arithmetic",
            DreamLap.of(laps)
        )
    }

    @Test
    fun `a lap with an implausibly lopsided sector is discarded`() {
        val laps = (1..4).map { n ->
            // Sector 1 is 2% of the lap: no car goes round a third of a circuit in that.
            lap(n, s1 = 900, s2 = 22_050, s3 = 22_050)
        }

        assertNull("a 2% sector is a boundary in the wrong place", DreamLap.of(laps))
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

        val dream = DreamLap.of(entities, caveat)
            ?: error("12 clean laps with derived sectors should support a dream lap")

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
