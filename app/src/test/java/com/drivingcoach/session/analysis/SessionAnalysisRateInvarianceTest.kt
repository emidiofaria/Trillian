package com.drivingcoach.session.analysis

import com.drivingcoach.ui.session.tabs.analysis.SessionAnalysisProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.abs

/**
 * The analysis must describe the drive, not the sample rate.
 *
 * Telemetry is captured at 10 Hz, while the sessions exported from earlier
 * versions - including every fixture in this repository - are 1 Hz. A detector
 * whose thresholds are expressed per sample rather than per second silently
 * changes behaviour between the two: bearing changes shrink by a factor of ten
 * and no corner is ever found, which on screen is indistinguishable from a
 * session that simply had no corners.
 *
 * These tests replay the same recorded drive at both rates - the 10 Hz version
 * interpolated from the 1 Hz original, so it contains no information the
 * original did not - and require the answers to agree.
 */
internal class SessionAnalysisRateInvarianceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val apexToleranceKmh = 1.0
    private val gTolerance = 0.05
    private val speedDropToleranceKmh = 3.0

    @Test
    fun theSameCornersAreFoundAtOneHertzAndAtTenHertz() {
        val (oneHz, tenHz) = analyseBothRates()

        assertEquals("Sample rate should be measured, not assumed", 1.0, oneHz.stats.sampleRateHz.toDouble(), 0.01)
        assertEquals(10.0, tenHz.stats.sampleRateHz.toDouble(), 0.01)

        assertEquals(
            "Corner count must not depend on sample rate",
            oneHz.corners.size, tenHz.corners.size
        )
        assertEquals(oneHz.corners.map { it.name }, tenHz.corners.map { it.name })
        assertEquals(
            "Corner directions must not depend on sample rate",
            oneHz.corners.map { it.direction }, tenHz.corners.map { it.direction }
        )

        oneHz.corners.zip(tenHz.corners).forEach { (slow, fast) ->
            assertEquals(
                "${slow.name} apex speed",
                slow.apexSpeedKmh.toDouble(), fast.apexSpeedKmh.toDouble(), apexToleranceKmh
            )
        }
    }

    @Test
    fun theSameBrakingZonesAreFoundAtOneHertzAndAtTenHertz() {
        val (oneHz, tenHz) = analyseBothRates()

        assertEquals(
            "Braking zone count must not depend on sample rate",
            oneHz.brakingZones.size, tenHz.brakingZones.size
        )

        oneHz.brakingZones.zip(tenHz.brakingZones).forEach { (slow, fast) ->
            assertEquals(
                "Peak g",
                slow.peakG.toDouble(), fast.peakG.toDouble(), gTolerance
            )
            assertEquals(
                "Speed drop",
                slow.speedDropKmh.toDouble(), fast.speedDropKmh.toDouble(), speedDropToleranceKmh
            )
            assertEquals(
                "Corner the zone leads into",
                slow.entersCorner, fast.entersCorner
            )
        }
    }

    @Test
    fun sessionStatisticsAgreeAtBothRates() {
        val (oneHz, tenHz) = analyseBothRates()

        val distanceErrorPct = abs(oneHz.stats.distanceM - tenHz.stats.distanceM) / oneHz.stats.distanceM * 100
        assertTrue("Distance differs by $distanceErrorPct%", distanceErrorPct < 1.0)
        assertEquals(
            "Max speed",
            oneHz.stats.maxSpeedKmh.toDouble(), tenHz.stats.maxSpeedKmh.toDouble(), 1.0
        )
        assertEquals(
            "Average speed",
            oneHz.stats.avgSpeedKmh.toDouble(), tenHz.stats.avgSpeedKmh.toDouble(), 1.0
        )
    }

    @Test
    fun theRenderedTrackStaysWithinItsBudgetAsTheSampleRateRises() {
        val (oneHz, tenHz) = analyseBothRates()

        assertTrue(
            "A 1 Hz lap is small enough to draw in full",
            oneHz.path.points.size <= SessionAnalysisProcessor.TRACK_PATH_POINT_BUDGET
        )
        assertEquals(
            "A 10 Hz lap must be thinned to the budget rather than drawn in full",
            SessionAnalysisProcessor.TRACK_PATH_POINT_BUDGET, tenHz.path.points.size
        )
        assertTrue(
            "The speed trace is bounded too",
            tenHz.speedByTime.size <= SessionAnalysisProcessor.SPEED_TRACE_POINT_BUDGET
        )
    }

    private fun analyseBothRates(): Pair<
        com.drivingcoach.ui.session.tabs.analysis.SessionAnalysis,
        com.drivingcoach.ui.session.tabs.analysis.SessionAnalysis
        > {
        val fixture = AnalysisFixtures.teste3(tempFolder.newFolder())
        val referenceLapId = fixture.laps.first { it.isBestLap }.lapId

        val oneHz = SessionAnalysisProcessor.analyzeSamples(
            fixture.samples, fixture.laps, referenceLapId, fixture.lapWindows, fixture.startLine
        )
        val tenHz = SessionAnalysisProcessor.analyzeSamples(
            AnalysisFixtures.resample(fixture.samples, 10.0),
            fixture.laps, referenceLapId, fixture.lapWindows, fixture.startLine
        )
        return oneHz to tenHz
    }
}
