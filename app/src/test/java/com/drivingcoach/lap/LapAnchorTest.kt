package com.drivingcoach.lap

import com.drivingcoach.data.telemetry.TelemetryFileReader
import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.util.GeoUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Holds [LapAnchor] to the one claim that makes it worth having: that the position it
 * returns for a crossing instant is the position lap detection itself computed at that
 * instant and then threw away.
 *
 * If that claim is false, [LapAnchor] is a second opinion about where the start/finish
 * line is, and a second opinion that disagrees with the first is worse than no opinion
 * at all. The test against a real recorded session checks it against a number the
 * detector did keep - the crossing's lateral offset - so the two are compared through
 * something neither of them can fudge.
 */
class LapAnchorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ==================== Interpolation ====================

    @Test
    fun `an instant halfway between two fixes gives the halfway position`() {
        val samples = straightRun(count = 3, intervalMs = 1000L, metresPerStep = 20.0)

        val position = LapAnchor.positionAt(samples, samples[0].timestampMs + 500L)

        assertNotNull(position)
        val metres = GeoUtils.haversineDistance(
            samples[0].latitude, samples[0].longitude,
            position!!.latitude, position.longitude
        )
        assertEquals("half of a 20 m step is 10 m", 10.0, metres, 0.1)
    }

    @Test
    fun `an instant on a fix gives that fix exactly`() {
        val samples = straightRun(count = 4, intervalMs = 1000L, metresPerStep = 20.0)

        val position = LapAnchor.positionAt(samples, samples[2].timestampMs)!!

        assertEquals(samples[2].latitude, position.latitude, 0.0)
        assertEquals(samples[2].longitude, position.longitude, 0.0)
    }

    @Test
    fun `interpolation is proportional across the whole span`() {
        val samples = straightRun(count = 2, intervalMs = 1000L, metresPerStep = 100.0)

        listOf(0L to 0.0, 250L to 25.0, 750L to 75.0, 1000L to 100.0).forEach { (offset, expected) ->
            val position = LapAnchor.positionAt(samples, samples[0].timestampMs + offset)!!
            val metres = GeoUtils.haversineDistance(
                samples[0].latitude, samples[0].longitude, position.latitude, position.longitude
            )
            // The fixture lays points out on a flat 111_320 m/degree approximation
            // while the check measures them with haversine, so the two disagree by
            // about a tenth of a percent. The claim under test is proportionality,
            // not the fixture's own spherical arithmetic.
            assertEquals(
                "at +$offset ms the car has gone $expected m",
                expected, metres, 0.002 * expected + 0.05
            )
        }
    }

    // ==================== Refusing to invent ====================

    @Test
    fun `an instant before the first fix has no position`() {
        val samples = straightRun(count = 5, intervalMs = 1000L, metresPerStep = 20.0)

        assertNull(
            "extrapolating backwards past the data would be inventing a position",
            LapAnchor.positionAt(samples, samples.first().timestampMs - 1L)
        )
    }

    @Test
    fun `an instant after the last fix has no position`() {
        val samples = straightRun(count = 5, intervalMs = 1000L, metresPerStep = 20.0)

        assertNull(
            LapAnchor.positionAt(samples, samples.last().timestampMs + 1L)
        )
    }

    @Test
    fun `an empty session has no positions`() {
        assertNull(LapAnchor.positionAt(emptyList(), 1_000_000L))
    }

    @Test
    fun `two fixes sharing a timestamp do not divide by zero`() {
        val base = straightRun(count = 2, intervalMs = 0L, metresPerStep = 20.0)

        // Both fixes carry the same instant, so there is no span to interpolate along.
        // The requirement is only that this returns a real position rather than NaN.
        val position = LapAnchor.positionAt(base, base.first().timestampMs)!!

        assertTrue(position.latitude.isFinite() && position.longitude.isFinite())
    }

    // ==================== Against a real recorded session ====================

    @Test
    fun `the recovered position matches the crossing offset the detector recorded`() = runBlocking {
        // The detector derives the crossing position and the crossing instant from one
        // shared fraction, keeps the instant, and keeps the position only as a lateral
        // offset from the start-line midpoint. Recovering the position from the instant
        // and re-measuring that offset therefore closes the loop: agreement here means
        // the inversion reproduces the detector's own arithmetic.
        val fixture = LapReplayHarness.load("cabo_do_mundo", tempFolder.newFolder())
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(
            fixture.telemetryFile, fixture.startLine, LocalLapDetector.TrackPriors.NONE
        )
        val diagnostics = requireNotNull(outcome.diagnostics) { "fixture produced no diagnostics" }
        assertTrue("fixture should accept crossings", diagnostics.acceptedCrossings.isNotEmpty())

        val samples = TelemetryFileReader.readAll(fixture.telemetryFile.absolutePath)
        val midLat = (fixture.startLine.lat1 + fixture.startLine.lat2) / 2.0
        val midLng = (fixture.startLine.lng1 + fixture.startLine.lng2) / 2.0

        diagnostics.acceptedCrossings.forEach { crossing ->
            val position = LapAnchor.positionAt(samples, crossing.timestampMs)
            assertNotNull(
                "crossing at ${crossing.timestampMs} fell outside its own telemetry",
                position
            )

            val measured = GeoUtils.haversineDistance(
                midLat, midLng, position!!.latitude, position.longitude
            )
            assertEquals(
                "recovered position is ${measured} m from the line midpoint but the " +
                    "detector recorded ${crossing.lateralOffsetM} m",
                crossing.lateralOffsetM, measured, 1.0
            )
        }
    }

    // ==================== Helpers ====================

    /** A run of fixes heading due north at a fixed spacing, as in [SectorSplitterTest]. */
    private fun straightRun(
        count: Int,
        intervalMs: Long,
        metresPerStep: Double,
        firstTimestampMs: Long = 1_000_000L
    ): List<TelemetrySample> {
        val metresPerDegreeLat = 111_320.0
        return (0 until count).map { i ->
            TelemetrySample(
                timestampMs = firstTimestampMs + i * intervalMs,
                latitude = 41.0 + (i * metresPerStep) / metresPerDegreeLat,
                longitude = -8.5,
                speedMs = 20f,
                headingDeg = 0f,
                accelX = 0f, accelY = 0f, accelZ = 9.8f,
                gyroX = 0f, gyroY = 0f, gyroZ = 0f,
                gpsAccuracyM = 5f
            )
        }
    }
}
