package com.drivingcoach.session.analysis

import com.drivingcoach.ui.session.tabs.analysis.SessionAnalysisProcessor
import com.drivingcoach.ui.session.tabs.analysis.TurnDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.abs

/**
 * The analysis engine replayed against a real recorded session.
 *
 * Fixture `teste3` is the six-minute kart session from incident 13, and its lap
 * boundaries come from the production detector rather than from anything this
 * test invents, so the corners and braking zones asserted here are the ones the
 * ANALYSIS tab will show for that session.
 *
 * Expected session totals were cross-checked against an independent
 * implementation of the same analysis (`telemetry_analysis.py`, the reference
 * report this feature is modelled on), which reads the same file and reports
 * 3.33 km, 360 s, 72.6 km/h peak and 31.9 km/h average.
 */
internal class SessionAnalysisProcessorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun theHeaderLineIsNotMistakenForATelemetrySample() {
        val raw = AnalysisFixtures.rawSamples()
        val prepared = SessionAnalysisProcessor.prepare(raw)

        // The file's first line is the session header; deserialised as a sample
        // it arrives with every field zeroed, which would otherwise place the car
        // at 0N 0E at the epoch.
        assertEquals("One header line should be dropped", raw.size - 1, prepared.size)
        assertTrue("No sample may be left without a timestamp", prepared.all { it.timestampMs > 0 })
        assertTrue(
            "No sample may be left at null island",
            prepared.none { it.latitude == 0.0 && it.longitude == 0.0 }
        )
    }

    @Test
    fun sessionStatisticsMatchTheRecordedDrive() {
        val stats = analyse().stats

        assertEquals("Distance (km)", 3.33, stats.distanceM / 1000.0, 0.05)
        assertEquals("Duration (s)", 360.0, stats.durationMs / 1000.0, 1.0)
        assertEquals("Max speed (km/h)", 72.6, stats.maxSpeedKmh.toDouble(), 0.5)
        assertEquals("Average speed (km/h)", 31.9, stats.avgSpeedKmh.toDouble(), 0.5)
        assertEquals("Sample rate (Hz)", 1.0, stats.sampleRateHz.toDouble(), 0.01)
    }

    @Test
    fun theBestLapIsUsedAsTheReferenceWhenNoneIsChosen() {
        val fixture = AnalysisFixtures.teste3(tempFolder.newFolder())

        val analysis = SessionAnalysisProcessor.analyzeSamples(
            fixture.samples, fixture.laps, null, fixture.lapWindows, fixture.startLine
        )

        val best = fixture.laps.first { it.isBestLap }
        assertEquals(best.lapId, analysis.referenceLapId)
        assertEquals("Lap ${best.lapNumber}", analysis.referenceLapLabel)
        assertFalse(analysis.referenceIsWholeSession)
        assertEquals("Best lap in the stats table", best.durationMs, analysis.stats.bestLapMs)
    }

    @Test
    fun aChosenLapOverridesTheBestLap() {
        val fixture = AnalysisFixtures.teste3(tempFolder.newFolder())
        val chosen = fixture.laps.last { !it.isBestLap }

        val analysis = SessionAnalysisProcessor.analyzeSamples(
            fixture.samples, fixture.laps, chosen.lapId, fixture.lapWindows, fixture.startLine
        )

        assertEquals(chosen.lapId, analysis.referenceLapId)
        assertTrue(
            "Corners must come from the chosen lap's window",
            analysis.corners.all { it.apexMs in fixture.lapWindows.getValue(chosen.lapId) }
        )
    }

    @Test
    fun theKartTrackCornersAreDetectedAndNumberedInPassingOrder() {
        val analysis = analyse()

        assertEquals("This lap of the circuit has five corners", 5, analysis.corners.size)
        assertEquals(
            listOf("T1", "T2", "T3", "T4", "T5"),
            analysis.corners.map { it.name }
        )
        assertEquals(
            "Numbering follows the order of passing",
            analysis.corners.map { it.apexMs }.sorted(),
            analysis.corners.map { it.apexMs }
        )
        assertEquals(
            "Four right-handers and one left, as the reference report shows",
            4, analysis.corners.count { it.direction == TurnDirection.RIGHT }
        )
    }

    @Test
    fun theApexIsTheSlowestPointOfEachCorner() {
        val fixture = AnalysisFixtures.teste3(tempFolder.newFolder())
        val analysis = analyse(fixture)

        analysis.corners.forEach { corner ->
            val within = fixture.samples.filter { it.timestampMs in corner.startMs..corner.endMs }
            val slowest = within.minOf { it.speedKmh }
            assertEquals(
                "${corner.name} apex should be the slowest point of the corner",
                slowest, corner.apexSpeedKmh.toDouble(), 0.01
            )
        }
    }

    @Test
    fun aParkedCarIsNotReportedAsCornering() {
        val fixture = AnalysisFixtures.teste3(tempFolder.newFolder())

        // The whole session includes the standing start and the slow return to
        // the pits. A stationary GPS wanders far enough to fake a very high yaw
        // rate, and before this was guarded the out-lap produced "corners" of
        // over 300 degrees.
        val analysis = SessionAnalysisProcessor.analyzeSamples(
            fixture.samples, emptyList(), null, emptyMap(), fixture.startLine
        )

        assertTrue("Whole session should still find the circuit's corners", analysis.corners.size >= 15)
        assertTrue(
            "No corner may turn more than a hairpin's worth: " +
                analysis.corners.maxOf { abs(it.turnDeg) },
            analysis.corners.all { abs(it.turnDeg) < 200f }
        )
        assertTrue("With no laps the whole session is the reference", analysis.referenceIsWholeSession)
    }

    @Test
    fun brakingZonesShedSpeedAndReportPlausibleG() {
        val analysis = analyse()

        assertTrue("The lap has braking zones", analysis.brakingZones.isNotEmpty())
        analysis.brakingZones.forEach { zone ->
            assertTrue("Braking must lose speed", zone.exitSpeedKmh < zone.entrySpeedKmh)
            assertEquals(
                zone.entrySpeedKmh - zone.exitSpeedKmh,
                zone.speedDropKmh,
                0.01f
            )
            assertTrue("Deceleration is negative by definition", zone.peakDecelMs2 < 0f)
            assertTrue("g is reported as a positive magnitude", zone.peakG > 0f)
            assertTrue("A kart on GPS never brakes at more than 2 g", zone.peakG < 2f)
            assertTrue("A braking zone has duration", zone.durationMs > 0L)
        }
    }

    @Test
    fun eachBrakingZoneIsAttributedToTheCornerItLeadsInto() {
        val analysis = analyse()
        val cornersByName = analysis.corners.associateBy { it.name }

        val attributed = analysis.brakingZones.filter { it.entersCorner != null }
        assertTrue("Braking should be linked to corners", attributed.isNotEmpty())

        attributed.forEach { zone ->
            val corner = cornersByName[zone.entersCorner]
            assertNotNull("Unknown corner ${zone.entersCorner}", corner)
            assertTrue(
                "${zone.entersCorner} apex must not be before the braking that leads into it",
                corner!!.apexMs >= zone.endMs - 1_000L
            )
        }
    }

    @Test
    fun theSessionSpeedTraceCoversTheWholeSessionRegardlessOfReferenceLap() {
        val analysis = analyse()

        assertTrue(analysis.speedByTime.isNotEmpty())
        assertEquals("Trace starts at t=0", 0f, analysis.speedByTime.first().elapsedS, 0.01f)
        assertEquals(
            "Trace ends at the end of the session, not the end of the lap",
            analysis.stats.durationMs / 1000.0,
            analysis.speedByTime.last().elapsedS.toDouble(),
            1.0
        )
    }

    private fun analyse(
        fixture: AnalysisFixtures.Fixture = AnalysisFixtures.teste3(tempFolder.newFolder())
    ) = SessionAnalysisProcessor.analyzeSamples(
        fixture.samples,
        fixture.laps,
        fixture.laps.first { it.isBestLap }.lapId,
        fixture.lapWindows,
        fixture.startLine
    )
}
