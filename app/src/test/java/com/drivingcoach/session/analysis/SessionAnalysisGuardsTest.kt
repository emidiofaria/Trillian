package com.drivingcoach.session.analysis

import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.ui.session.tabs.analysis.LapOption
import com.drivingcoach.ui.session.tabs.analysis.SessionAnalysis
import com.drivingcoach.ui.session.tabs.analysis.SessionAnalysisProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What the analysis does when there is nothing to analyse.
 *
 * These paths are all reachable in the shipped app: telemetry files are deleted
 * when a session is deleted or the user clears their data, sessions recorded
 * without a start/finish line have no laps at all (SRS LD-03), and a recording
 * stopped immediately after starting produces a handful of samples. None of them
 * may crash the tab.
 */
internal class SessionAnalysisGuardsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun aSessionWithNoSamplesIsEmptyRatherThanAnError() {
        val analysis = SessionAnalysisProcessor.analyzeSamples(
            emptyList(), emptyList(), null, emptyMap(), null
        )

        assertSame(SessionAnalysis.EMPTY, analysis)
        assertFalse(analysis.hasData)
    }

    @Test
    fun aSessionWithASingleSampleIsEmpty() {
        val analysis = SessionAnalysisProcessor.analyzeSamples(
            listOf(SessionAnalysisProcessor.Point(1_000L, 41.2, -8.6, 10.0)),
            emptyList(), null, emptyMap(), null
        )

        assertFalse(analysis.hasData)
    }

    @Test
    fun aMissingTelemetryFileYieldsNoSamples() = runBlocking {
        assertTrue(SessionAnalysisProcessor.readSamples(null).isEmpty())
        assertTrue(SessionAnalysisProcessor.readSamples("").isEmpty())
        assertTrue(
            SessionAnalysisProcessor.readSamples(
                tempFolder.newFolder().resolve("gone.jsonl").absolutePath
            ).isEmpty()
        )
    }

    @Test
    fun aDeletedTelemetryFileProducesAnEmptyAnalysisRatherThanThrowing() = runBlocking {
        val analysis = SessionAnalysisProcessor.analyze(
            filePath = tempFolder.newFolder().resolve("deleted.jsonl").absolutePath,
            laps = listOf(LapOption(1L, 1, 80_000L, true)),
            referenceLapId = 1L
        )

        assertFalse(analysis.hasData)
        assertTrue(analysis.corners.isEmpty())
        assertTrue(analysis.brakingZones.isEmpty())
        assertTrue(analysis.path.isEmpty)
    }

    @Test
    fun samplesWithoutPositionOrTimeAreDiscarded() {
        val prepared = SessionAnalysisProcessor.prepare(
            listOf(
                sample(timestampMs = 0L, lat = 41.2, lng = -8.6),
                sample(timestampMs = 1_000L, lat = 0.0, lng = 0.0),
                sample(timestampMs = 2_000L, lat = 41.2, lng = -8.6)
            )
        )

        assertEquals(1, prepared.size)
        assertEquals(2_000L, prepared.first().timestampMs)
    }

    @Test
    fun samplesOutOfOrderAreSortedBeforeAnalysis() {
        val prepared = SessionAnalysisProcessor.prepare(
            listOf(
                sample(timestampMs = 3_000L, lat = 41.2, lng = -8.6),
                sample(timestampMs = 1_000L, lat = 41.2, lng = -8.6),
                sample(timestampMs = 2_000L, lat = 41.2, lng = -8.6)
            )
        )

        assertEquals(listOf(1_000L, 2_000L, 3_000L), prepared.map { it.timestampMs })
    }

    @Test
    fun aLapWindowThatDoesNotOverlapTheTelemetryFallsBackToTheWholeSession() {
        val fixture = AnalysisFixtures.teste3(tempFolder.newFolder())
        val phantomLap = LapOption(99L, 9, 80_000L, true)

        val analysis = SessionAnalysisProcessor.analyzeSamples(
            fixture.samples,
            listOf(phantomLap),
            phantomLap.lapId,
            mapOf(phantomLap.lapId to 1L..2L),
            fixture.startLine
        )

        assertTrue("Nothing may be lost when a lap window is unusable", analysis.hasData)
        assertTrue(analysis.referenceIsWholeSession)
        assertTrue(analysis.path.points.isNotEmpty())
    }

    @Test
    fun aSessionThatNeverMovedHasNoCornersAndNoBraking() {
        val stationary = (0 until 120).map { i ->
            SessionAnalysisProcessor.Point(
                timestampMs = 1_000L + i * 1_000L,
                // A few metres of GPS scatter, as a parked phone reports.
                latitude = 41.2005 + (i % 3) * 0.00002,
                longitude = -8.6109 + (i % 5) * 0.00002,
                speedKmh = 0.4
            )
        }

        val analysis = SessionAnalysisProcessor.analyzeSamples(
            stationary, emptyList(), null, emptyMap(), null
        )

        assertTrue("A parked car has no corners", analysis.corners.isEmpty())
        assertTrue("A parked car has no braking zones", analysis.brakingZones.isEmpty())
    }

    @Test
    fun theMedianSampleIntervalIgnoresGapsInTheRecording() {
        val withGap = listOf(0L, 1_000L, 2_000L, 3_000L, 60_000L, 61_000L).map {
            SessionAnalysisProcessor.Point(it + 1L, 41.2, -8.6, 10.0)
        }

        assertEquals(
            "A single dropped-fix gap must not change the measured sample rate",
            1.0, SessionAnalysisProcessor.medianIntervalSeconds(withGap), 0.001
        )
    }

    private fun sample(timestampMs: Long, lat: Double, lng: Double) = TelemetrySample(
        timestampMs = timestampMs,
        latitude = lat,
        longitude = lng,
        speedMs = 5f,
        headingDeg = 0f,
        accelX = 0f, accelY = 0f, accelZ = 0f,
        gyroX = 0f, gyroY = 0f, gyroZ = 0f,
        gpsAccuracyM = 3f
    )
}
