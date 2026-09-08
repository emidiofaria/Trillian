package com.drivingcoach.lap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Lap detection replayed against a real recorded session.
 *
 * Fixture `teste3` is the session from incident 13: six minutes on a kart track,
 * recorded on a ZTE Blade A53+ running v2.8. The driver completed five passes of
 * the start/finish line. The app reported "No laps detected. Complete at least 2
 * laps."
 *
 * The expected lap times below are not invented. They were measured from this
 * telemetry during the incident 13 RCA, and are reproducible offline with
 * `03_incidents/13_.../analysis/replay_analysis.py`. They are what the driver
 * actually drove.
 *
 * Every prior lap-detection fix (incidents 02, 03, 09) was reasoned about
 * analytically because no real session could be replayed. This test is the
 * ground truth that was missing.
 */
class LapDetectionRealSessionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Measured from the recorded telemetry — see incident 13 RCA. */
    private val expectedLapSeconds = listOf(79.83, 77.08, 77.26, 83.44)

    /** Generous: the point is the laps exist and are right, not sub-tick precision. */
    private val toleranceSeconds = 0.5

    @Test
    fun theFixtureIsTheSessionFromIncident13() {
        val fixture = LapReplayHarness.load("teste3", tempFolder.newFolder())

        assertEquals("teste. 3", fixture.trackName)
        assertEquals("2.8", fixture.appVersionName)
        assertTrue(
            "Fixture should be the ZTE session, was ${fixture.deviceModel}",
            fixture.deviceModel.contains("Blade A53")
        )
        assertEquals(
            "The app recorded no laps for this session - that is the defect being fixed",
            0, fixture.lapsRecordedByApp
        )
    }

    @Test
    fun theDriverFiveStartLineCrossingsAreDetectedAsFourLaps() {
        val fixture = LapReplayHarness.load("teste3", tempFolder.newFolder())

        val result = LapReplayHarness.detect(fixture)

        assertTrue(
            "The driver completed five passes of the start/finish line, which bound four " +
                "laps. Detection returned: ${LapReplayHarness.describe(result)}",
            result is LocalLapDetector.DetectionResult.Success
        )

        val laps = (result as LocalLapDetector.DetectionResult.Success).laps
        assertEquals(
            "Five crossings bind four laps; the final incomplete lap is discarded",
            4, laps.size
        )
    }

    @Test
    fun theDetectedLapTimesMatchTheLapsThatWereActuallyDriven() {
        val fixture = LapReplayHarness.load("teste3", tempFolder.newFolder())

        val result = LapReplayHarness.detect(fixture)
        val actual = LapReplayHarness.lapSeconds(result)

        assertEquals(
            "Expected the four laps measured from this telemetry. Got: " +
                LapReplayHarness.describe(result),
            expectedLapSeconds.size, actual.size
        )

        expectedLapSeconds.forEachIndexed { index, expected ->
            assertEquals(
                "Lap ${index + 1} of ${LapReplayHarness.describe(result)}",
                expected, actual[index], toleranceSeconds
            )
        }
    }

    @Test
    fun theBestLapIsTheFastestOfTheFour() {
        val fixture = LapReplayHarness.load("teste3", tempFolder.newFolder())

        val result = LapReplayHarness.detect(fixture)
        assertTrue(
            "Cannot pick a best lap from ${LapReplayHarness.describe(result)}",
            result is LocalLapDetector.DetectionResult.Success
        )

        val laps = (result as LocalLapDetector.DetectionResult.Success).laps
        val best = LocalLapDetector().findBestLap(laps)

        assertEquals(
            "Fastest of the four laps",
            77.08, (best?.durationMs ?: 0L) / 1000.0, toleranceSeconds
        )
    }
}
