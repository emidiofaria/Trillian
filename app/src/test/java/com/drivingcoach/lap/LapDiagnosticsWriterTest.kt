package com.drivingcoach.lap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Covers the sidecar diagnostics file introduced after incident 13.
 *
 * The point of these tests is not the file format but the guarantee behind it: that a
 * future "no laps detected" report arrives with the evidence needed to answer it,
 * which is precisely what incident 09 lacked when it was closed without a cause.
 */
class LapDiagnosticsWriterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val writer = LapDiagnosticsWriter()
    private val detector = LocalLapDetector()

    private lateinit var fixture: LapReplayHarness.Fixture

    @Before
    fun loadRealSession() {
        fixture = LapReplayHarness.load("teste3", temporaryFolder.root)
    }

    @Test
    fun `reports how nearly parallel the captured line was to the direction of travel`() {
        val outcome = detector.detectLapsWithDiagnostics(fixture.telemetryFile, fixture.startLine)

        val angle = outcome.diagnostics?.angleBetweenLineAndTravelDeg
        assertNotNull("the line-versus-travel angle must be recorded", angle)
        assertTrue(
            "incident 13's line lay within a few degrees of the direction of travel; " +
                "the diagnostics must say so even though the detector now copes with " +
                "it, because that angle is what identifies a badly captured line. " +
                "Measured $angle degrees",
            angle!! < 10.0
        )
    }

    @Test
    fun `reports the angle even when nothing crossed at all`() {
        // A start line nowhere near the track: no candidate crossing can be accepted.
        // This is the shape of the incident 13 report as the user experienced it, and
        // the case where a diagnostic is worth most - so the angle must still be
        // derived, here from the car's heading at its closest approach.
        val elsewhere = LocalLapDetector.StartLine(
            lat1 = fixture.startLine.lat1 + 0.02,
            lng1 = fixture.startLine.lng1,
            lat2 = fixture.startLine.lat2 + 0.02,
            lng2 = fixture.startLine.lng2
        )
        val outcome = detector.detectLapsWithDiagnostics(fixture.telemetryFile, elsewhere)

        assertTrue(
            "expected no laps from a start line 2km off the track, got " +
                LapReplayHarness.describe(outcome.result),
            outcome.result is LocalLapDetector.DetectionResult.InsufficientLaps
        )
        assertEquals(0, outcome.diagnostics!!.acceptedCrossings.size)
        assertNotNull(
            "a run that accepted nothing is the one most in need of explaining; the " +
                "angle must not go missing exactly then",
            outcome.diagnostics!!.angleBetweenLineAndTravelDeg
        )
        assertNotNull(writer.write(fixture.telemetryFile, 1L, outcome))
    }

    @Test
    fun `the written file names the session and the outcome`() {
        val outcome = detector.detectLapsWithDiagnostics(fixture.telemetryFile, fixture.startLine)

        val sidecar = writer.write(fixture.telemetryFile, sessionId = 7L, outcome = outcome)!!
        val contents = sidecar.readText()

        assertTrue("session id should be recorded", contents.contains("\"sessionId\": 7"))
        assertTrue(
            "the outcome should be readable without running the app, was:\n$contents",
            contents.contains("success:4_laps")
        )
        assertTrue(
            "the telemetry it was derived from should be named",
            contents.contains(fixture.telemetryFile.name)
        )
    }

    @Test
    fun `is written beside the telemetry, not over it`() {
        val originalLength = fixture.telemetryFile.length()
        val outcome = detector.detectLapsWithDiagnostics(fixture.telemetryFile, fixture.startLine)

        val sidecar = writer.write(fixture.telemetryFile, sessionId = 1L, outcome = outcome)!!

        assertEquals(
            "the telemetry must be left untouched",
            originalLength,
            fixture.telemetryFile.length()
        )
        assertEquals(
            "the sidecar belongs in the same folder as the telemetry",
            fixture.telemetryFile.parentFile,
            sidecar.parentFile
        )
        assertTrue(
            "it must not be mistaken for telemetry by anything scanning the folder",
            sidecar.name.endsWith(LapDiagnosticsWriter.SUFFIX)
        )
    }

    @Test
    fun `an unwritable location does not fail the session`() {
        val outcome = detector.detectLapsWithDiagnostics(fixture.telemetryFile, fixture.startLine)

        // A path whose parent folder does not exist: writing cannot succeed.
        val unreachable = File(File(temporaryFolder.root, "no-such-folder"), "telemetry.jsonl")

        assertNull(
            "a failure to record diagnostics must be swallowed - a lost diagnostic " +
                "must never cost the user a session that was otherwise processed",
            writer.write(unreachable, sessionId = 1L, outcome = outcome)
        )
    }

    @Test
    fun `nothing is written when detection never ran`() {
        val nullIsland = LocalLapDetector.StartLine(0.0, 0.0, 0.0, 0.0)
        val outcome = detector.detectLapsWithDiagnostics(fixture.telemetryFile, nullIsland)

        assertNull(
            "with no usable start line there is nothing observed to report",
            writer.write(fixture.telemetryFile, sessionId = 1L, outcome = outcome)
        )
    }

    @Test
    fun `carries the incident 14 evidence into the file that outlives the session`() {
        // Incident 14's cause -- passes going wide of a start point captured while
        // standing still -- was fully present in memory and absent from disk. The
        // writer serialises the diagnostics object whole, so these fields ride along
        // for free; this test exists so that stays true, because the fields are only
        // worth having if an investigator finds them months later.
        val incident14 = LapReplayHarness.load("ines3", temporaryFolder.newFolder())
        val outcome = detector.detectLapsWithDiagnostics(
            incident14.telemetryFile,
            incident14.startLine
        )

        val sidecar = writer.write(incident14.telemetryFile, sessionId = 14L, outcome = outcome)
        val json = requireNotNull(sidecar).readText()

        assertTrue(
            "the file must say which start point the laps were measured against",
            json.contains("PROJECTED_ONTO_PATH")
        )
        assertTrue("the file must say how far that point moved", json.contains("anchorProjectionM"))
        assertTrue(
            "the file must record that the line was captured standing still",
            json.contains("captureWindowSpeedMs")
        )
        assertTrue(
            "the file must record how far the position wandered during capture",
            json.contains("captureWindowScatterM")
        )
    }
}
