package com.drivingcoach.lap

import com.drivingcoach.data.telemetry.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Holds [SectorSplitter] to the two promises the rest of the feature is built on:
 * the three sector times always sum to exactly the lap, and a lap that cannot be
 * divided honestly gets no sectors at all rather than invented ones.
 *
 * Both matter downstream. The sum is what [com.drivingcoach.coaching.DreamLap]
 * uses to decide whether a lap's sectors can be believed, so a splitter that is
 * a millisecond out would quietly disqualify every lap in the session. And a lap
 * split from too little data would produce three plausible-looking numbers with
 * nothing behind them, which is the failure this project has learned to fear more
 * than an empty screen.
 */
class SectorSplitterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ==================== The arithmetic promise ====================

    @Test
    fun `sectors sum to exactly the lap duration`() {
        val samples = straightRun(count = 40, intervalMs = 1000L, metresPerStep = 20.0)
        val startTs = samples.first().timestampMs
        val endTs = samples.last().timestampMs

        val sectors = SectorSplitter.split(samples, startTs, endTs)

        assertNotNull("a 40-sample, 780 m lap should be splittable", sectors)
        assertEquals(
            "the three sectors must account for the whole lap and nothing more",
            endTs - startTs,
            sectors!!.totalMs
        )
    }

    @Test
    fun `an even lap splits into three near-equal sectors`() {
        // Constant speed and constant sample spacing: thirds of distance are also
        // thirds of time, so any large skew here is a boundary that landed wrong.
        val samples = straightRun(count = 31, intervalMs = 1000L, metresPerStep = 20.0)
        val sectors = SectorSplitter.split(
            samples, samples.first().timestampMs, samples.last().timestampMs
        )!!

        val third = (samples.last().timestampMs - samples.first().timestampMs) / 3.0
        listOf(sectors.sector1Ms, sectors.sector2Ms, sectors.sector3Ms).forEach { sector ->
            assertEquals("constant pace should give even thirds", third, sector.toDouble(), 100.0)
        }
    }

    @Test
    fun `the boundary is interpolated rather than snapped to a sample`() {
        // Six samples, 100 m apart: a third of the 500 m lap is 166.67 m, which falls
        // two thirds of the way along the second segment. Snapping to the nearest fix
        // would give a whole number of seconds; interpolation must not.
        val samples = straightRun(count = 6, intervalMs = 1000L, metresPerStep = 100.0)
        val sectors = SectorSplitter.split(
            samples, samples.first().timestampMs, samples.last().timestampMs
        )!!

        assertTrue(
            "sector 1 of ${sectors.sector1Ms} ms looks snapped to a whole sample interval",
            sectors.sector1Ms % 1000L != 0L
        )
        assertEquals(
            "a third of 500 m at 100 m/s is 1.667 s in",
            1667.0, sectors.sector1Ms.toDouble(), 30.0
        )
    }

    // ==================== The refusals ====================

    @Test
    fun `a lap with too few samples gets no sectors`() {
        val samples = straightRun(
            count = SectorSplitter.MIN_SAMPLES_PER_LAP - 1,
            intervalMs = 1000L,
            metresPerStep = 100.0
        )

        assertNull(
            "below ${SectorSplitter.MIN_SAMPLES_PER_LAP} samples the boundaries would be pure guesswork",
            SectorSplitter.split(samples, samples.first().timestampMs, samples.last().timestampMs)
        )
    }

    @Test
    fun `a lap that covered almost no ground gets no sectors`() {
        // Twenty fixes scattered over a few metres: plenty of samples, no lap.
        val samples = straightRun(count = 20, intervalMs = 1000L, metresPerStep = 1.0)

        assertNull(
            "under ${SectorSplitter.MIN_LAP_DISTANCE_M} m there is nothing to divide",
            SectorSplitter.split(samples, samples.first().timestampMs, samples.last().timestampMs)
        )
    }

    @Test
    fun `an inverted or empty window gets no sectors`() {
        val samples = straightRun(count = 40, intervalMs = 1000L, metresPerStep = 20.0)
        val startTs = samples.first().timestampMs
        val endTs = samples.last().timestampMs

        assertNull("end before start", SectorSplitter.split(samples, endTs, startTs))
        assertNull("zero-length window", SectorSplitter.split(samples, startTs, startTs))
        assertNull("no samples at all", SectorSplitter.split(emptyList(), startTs, endTs))
    }

    @Test
    fun `only samples inside the lap window are used`() {
        // A full lap followed by a long out-lap. If the splitter ignored the window
        // the boundaries would be pushed far down the out-lap.
        val lap = straightRun(count = 31, intervalMs = 1000L, metresPerStep = 20.0)
        val trailing = straightRun(
            count = 60, intervalMs = 1000L, metresPerStep = 20.0,
            firstTimestampMs = lap.last().timestampMs + 1000L,
            firstOffsetM = 31 * 20.0
        )

        val windowed = SectorSplitter.split(
            lap + trailing, lap.first().timestampMs, lap.last().timestampMs
        )!!
        val lapOnly = SectorSplitter.split(
            lap, lap.first().timestampMs, lap.last().timestampMs
        )!!

        assertEquals("trailing samples must not move sector 1", lapOnly.sector1Ms, windowed.sector1Ms)
        assertEquals("trailing samples must not move sector 2", lapOnly.sector2Ms, windowed.sector2Ms)
    }

    // ==================== Against a real recorded session ====================

    @Test
    fun `every lap of a real session carries sectors that sum to the lap`() {
        val fixture = LapReplayHarness.load("s_mamede", tempFolder.newFolder())
        val result = LapReplayHarness.detect(fixture)

        val laps = (result as? LocalLapDetector.DetectionResult.Success)?.laps
            ?: error("s_mamede should detect laps, got ${LapReplayHarness.describe(result)}")

        laps.forEach { lap ->
            assertTrue(
                "lap ${lap.lapNumber} came back with no sector 1",
                lap.sector1Ms > 0L
            )
            assertEquals(
                "lap ${lap.lapNumber} sectors do not sum to its duration",
                lap.durationMs,
                lap.sector1Ms + lap.sector2Ms + lap.sector3Ms
            )
        }
    }

    @Test
    fun `sectors of a real session are plausible shares of their lap`() {
        val fixture = LapReplayHarness.load("cabo_do_mundo", tempFolder.newFolder())
        val result = LapReplayHarness.detect(fixture)

        val laps = (result as? LocalLapDetector.DetectionResult.Success)?.laps
            ?: error("cabo_do_mundo should detect laps, got ${LapReplayHarness.describe(result)}")

        laps.forEach { lap ->
            listOf(lap.sector1Ms, lap.sector2Ms, lap.sector3Ms).forEachIndexed { index, sector ->
                val share = sector.toDouble() / lap.durationMs
                assertTrue(
                    "lap ${lap.lapNumber} sector ${index + 1} is $share of the lap, " +
                        "which is not a third of a circuit by any driving style",
                    share in 0.10..0.75
                )
            }
        }
    }

    // ==================== Helpers ====================

    /**
     * A run of fixes heading due north at a fixed spacing. Straight-line geometry
     * keeps the expected distances arithmetic, so a failure points at the splitter
     * rather than at the test's own trigonometry.
     */
    private fun straightRun(
        count: Int,
        intervalMs: Long,
        metresPerStep: Double,
        firstTimestampMs: Long = 1_000_000L,
        firstOffsetM: Double = 0.0
    ): List<TelemetrySample> {
        val metresPerDegreeLat = 111_320.0
        return (0 until count).map { i ->
            TelemetrySample(
                timestampMs = firstTimestampMs + i * intervalMs,
                latitude = 41.0 + (firstOffsetM + i * metresPerStep) / metresPerDegreeLat,
                longitude = -8.5,
                speedMs = (metresPerStep / (intervalMs / 1000.0)).toFloat(),
                headingDeg = 0f,
                accelX = 0f, accelY = 0f, accelZ = 9.8f,
                gyroX = 0f, gyroY = 0f, gyroZ = 0f,
                gpsAccuracyM = 5f
            )
        }
    }
}
