package com.drivingcoach.lap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Establishes that lap times do not depend on how the start/finish was captured.
 *
 * This is the seed for PLAN-003 (one-tap start/finish capture), and it is *not*
 * ignored, because the property it asserts already holds. That is the finding: since
 * the incident 13 fix the detector takes only the **midpoint** of the captured line
 * and derives the crossing plane from the car's direction of travel, so the second
 * captured point contributes nothing to the answer.
 *
 * The consequence for the backlog is worth stating plainly: PLAN-003 is a change to
 * the capture screen alone. No detector work is needed, and this test is the standing
 * proof of that — if it ever starts failing, the detector has quietly regained a
 * dependence on the captured orientation and PLAN-003 has become expensive again.
 *
 * See `docs/plans/PLAN_003_One_Tap_Start_Finish_Capture.md`.
 */
class OneTapStartFinishCaptureTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val detector = LocalLapDetector()

    @Test
    fun `a single captured point yields the same laps as the two-point line`() {
        // A one-tap capture has no second point, so the detector would be handed a
        // degenerate line: both endpoints at the position the driver tapped.
        val fixture = LapReplayHarness.load("teste3", temporaryFolder.root)
        val (midLat, midLng) = fixture.startLine.midpoint()
        val singleTap = LocalLapDetector.StartLine(midLat, midLng, midLat, midLng)

        val fromTwoPoints = LapReplayHarness.lapSeconds(
            detector.detectLaps(fixture.telemetryFile, fixture.startLine)
        )
        val fromOneTap = LapReplayHarness.lapSeconds(
            detector.detectLaps(fixture.telemetryFile, singleTap)
        )

        assertTrue("the two-point capture should still find laps", fromTwoPoints.isNotEmpty())
        assertEquals(
            "capturing one point instead of two must not change a single lap time",
            fromTwoPoints,
            fromOneTap
        )
    }

    @Test
    fun `swapping the two captured points changes nothing`() {
        // Under the old geometry the order of the endpoints set the line's bearing.
        // It is now irrelevant, and a user cannot get it "backwards".
        val fixture = LapReplayHarness.load("teste3", temporaryFolder.root)
        val reversed = LocalLapDetector.StartLine(
            lat1 = fixture.startLine.lat2,
            lng1 = fixture.startLine.lng2,
            lat2 = fixture.startLine.lat1,
            lng2 = fixture.startLine.lng1
        )

        assertEquals(
            LapReplayHarness.lapSeconds(
                detector.detectLaps(fixture.telemetryFile, fixture.startLine)
            ),
            LapReplayHarness.lapSeconds(
                detector.detectLaps(fixture.telemetryFile, reversed)
            )
        )
    }
}
