package com.drivingcoach.lap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The session from incident 14, replayed.
 *
 * Fixture `ines3` is a real kart session: three laps driven on a ZTE Blade A53+,
 * for which the app reported no laps at all. The telemetry was complete - the
 * Analysis tab drew the circuit correctly from the same file - and the start line
 * was captured normally, at the two edges of the track.
 *
 * What went wrong is visible two seconds either side of the kart accelerating
 * away. While the phone was walked to the kart and then held still in it, its
 * reported position sat 11-18 m to one side of the track. The instant the kart
 * was moving, the reported position moved onto the racing line and stayed within
 * 4.5 m of it for the rest of the session. The start line, captured during the
 * stationary phase, inherited that error: 8.15 m long and correctly across the
 * track, but 16 m to the side of it - and the detector looks 15 m to each side.
 *
 * Four passes of the start straight were therefore discarded, and because
 * out-of-corridor passes were dropped without being recorded, the diagnostics
 * reported no crossings and no rejections for a session containing three laps.
 *
 * These tests hold the fix to the session it was built from.
 */
class LapDetectionIncident14Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun outcome(): LocalLapDetector.DetectionOutcome {
        val fixture = LapReplayHarness.load("ines3", tempFolder.newFolder())
        return LocalLapDetector().detectLapsWithDiagnostics(
            fixture.telemetryFile, fixture.startLine
        )
    }

    @Test
    fun theFixtureIsTheSessionFromIncident14() {
        val fixture = LapReplayHarness.load("ines3", tempFolder.newFolder())

        assertEquals("Inês 3 - no lap detection", fixture.trackName)
        assertEquals("2.94", fixture.appVersionName)
        assertEquals("ZTE ZTE Blade A53+", fixture.deviceModel)
        assertEquals(
            "the app recorded no laps for this session - that is the incident",
            0, fixture.lapsRecordedByApp
        )
    }

    @Test
    fun theThreeLapsTheDriverDroveAreDetected() {
        val result = outcome().result

        assertTrue(
            "expected three laps, got ${LapReplayHarness.describe(result)}",
            result is LocalLapDetector.DetectionResult.Success
        )
        assertEquals(3, (result as LocalLapDetector.DetectionResult.Success).laps.size)
    }

    @Test
    fun theDetectedLapTimesMatchTheLapsThatWereActuallyDriven() {
        val seconds = LapReplayHarness.lapSeconds(outcome().result)

        // Read off the four passes of the start straight in the raw telemetry,
        // independently of the detector: roughly 104, 86 and 95 seconds.
        assertEquals(3, seconds.size)
        assertEquals(104.0, seconds[0], 2.0)
        assertEquals(86.0, seconds[1], 2.0)
        assertEquals(95.0, seconds[2], 2.0)
    }

    @Test
    fun theLapsAreMeasuredAgainstThePathTheCarDroveRatherThanTheCapturedLine() {
        val diagnostics = requireNotNull(outcome().diagnostics)

        assertEquals(
            LocalLapDetector.AnchorSource.PROJECTED_ONTO_PATH, diagnostics.anchor
        )
        val moved = requireNotNull(diagnostics.anchorProjectionM)
        assertEquals(
            "the start point should move about 16 m onto the track", 16.1, moved, 1.0
        )
        assertTrue(
            "the correction must stay inside its bound",
            moved <= LocalLapDetector.MAX_ANCHOR_PROJECTION_M
        )
    }

    @Test
    fun theDiscardedPassesAreReportedRatherThanDroppedInSilence() {
        // The failure this fixture records was invisible: every pass of the start
        // straight was discarded by a bare `continue`, so the diagnostics showed
        // no crossings and no rejections and named no cause. Whatever the detector
        // now decides, passes it turns away must appear in the record.
        val detector = LocalLapDetector()
        val fixture = LapReplayHarness.load("ines3", tempFolder.newFolder())
        val captured = detector.detectLapsWithDiagnostics(
            fixture.telemetryFile,
            // 56 m north. Far enough that the correction is refused -- the driven
            // path never comes within MAX_ANCHOR_PROJECTION_M of it -- yet close
            // enough that passes still fall inside the reporting radius. Pushing
            // it further would be a weaker test: the passes would vanish entirely
            // and there would be nothing left to report.
            fixture.startLine.copy(
                lat1 = fixture.startLine.lat1 + 0.0005,
                lat2 = fixture.startLine.lat2 + 0.0005
            )
        )

        val diagnostics = requireNotNull(captured.diagnostics)
        assertEquals(0, diagnostics.lapCount)
        assertEquals(
            "the anchor must stay where it was captured when the bound refuses it",
            LocalLapDetector.AnchorSource.CAPTURED,
            diagnostics.anchor
        )
        assertTrue(
            "a session that finds nothing must still say what it turned away",
            diagnostics.rejectedCrossings.any {
                it.reason == LocalLapDetector.RejectionReason.TOO_FAR_TO_THE_SIDE
            }
        )
    }

    @Test
    fun theStartLineWasCapturedWhileTheDeviceWasStandingStill() {
        val diagnostics = requireNotNull(outcome().diagnostics)

        val speed = requireNotNull(diagnostics.captureWindowSpeedMs)
        assertTrue(
            "the session opened at walking pace, not at racing speed: $speed m/s",
            speed < 2.0
        )
        val scatter = requireNotNull(diagnostics.captureWindowScatterM)
        assertTrue(
            "a position that wanders $scatter m while still is the incident itself",
            scatter > 5.0
        )
    }

    @Test
    fun swappingTheTwoCapturedPointsChangesNothing() {
        // The tester asked whether the left and right points could have been
        // captured the wrong way round. They are combined into a midpoint, and
        // since incident 13 the line's own bearing is not used to detect anything,
        // so the order cannot matter. Nothing proved that until now.
        val fixture = LapReplayHarness.load("ines3", tempFolder.newFolder())
        val swapped = LocalLapDetector.StartLine(
            lat1 = fixture.startLine.lat2, lng1 = fixture.startLine.lng2,
            lat2 = fixture.startLine.lat1, lng2 = fixture.startLine.lng1
        )

        val asCaptured = LapReplayHarness.lapSeconds(
            LocalLapDetector().detectLaps(fixture.telemetryFile, fixture.startLine)
        )
        val reversed = LapReplayHarness.lapSeconds(
            LocalLapDetector().detectLaps(fixture.telemetryFile, swapped)
        )

        assertEquals(asCaptured, reversed)
    }
}
