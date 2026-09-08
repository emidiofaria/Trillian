package com.drivingcoach.lap

import com.drivingcoach.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.cos

/**
 * The guards around lap detection, exercised individually against synthetic
 * sessions where the intended answer is known by construction.
 *
 * The real-session fixture in [LapDetectionRealSessionTest] proves the detector
 * works on a track. These tests prove each rule does what it claims, including
 * the cases a single recorded session cannot contain.
 */
class LocalLapDetectorGuardsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val detector = LocalLapDetector()

    // A point on the Greenwich meridian, to catch a validity check that confuses
    // "longitude zero" with "no coordinate".
    private val greenwichLine = LocalLapDetector.StartLine(
        lat1 = 51.4779, lng1 = 0.0,
        lat2 = 51.4780, lng2 = 0.0001
    )

    // ---------------------------------------------------------------- isValid

    @Test
    fun aLineWithBothEndpointsSetIsValid() {
        assertTrue(
            LocalLapDetector.StartLine(41.2005, -8.6109, 41.2005, -8.6110).isValid()
        )
    }

    @Test
    fun aLineOnTheGreenwichMeridianIsValid() {
        assertTrue(
            "Longitude 0 is Greenwich, not a missing coordinate",
            greenwichLine.isValid()
        )
    }

    @Test
    fun aLineWithAnEndpointAtNullIslandIsRejected() {
        assertFalse(
            "An endpoint at (0, 0) is in the Gulf of Guinea, not on a race track",
            LocalLapDetector.StartLine(41.2005, -8.6109, 0.0, 0.0).isValid()
        )
        assertFalse(
            LocalLapDetector.StartLine(0.0, 0.0, 41.2005, -8.6109).isValid()
        )
    }

    @Test
    fun aCompletelyEmptyLineIsRejected() {
        assertFalse(LocalLapDetector.StartLine(0.0, 0.0, 0.0, 0.0).isValid())
    }

    // ------------------------------------------------------- crossing detection

    @Test
    fun aLineCapturedAlongTheDirectionOfTravelStillDetectsLaps() {
        // The incident 13 geometry, reduced to its essentials: the start line points
        // the same way the car drives. Under the previous algorithm this was
        // undetectable at any line length.
        val session = syntheticLaps(lapCount = 4)

        val result = detector.detectLaps(session, startLineFor(startLineAlongTrack = true))

        assertTrue(
            "Detection must not depend on how the user happened to orient the line. " +
                LapReplayHarness.describe(result),
            result is LocalLapDetector.DetectionResult.Success
        )
        assertEquals(3, (result as LocalLapDetector.DetectionResult.Success).laps.size)
    }

    @Test
    fun aLineCapturedAcrossTheTrackDetectsTheSameLaps() {
        val session = syntheticLaps(lapCount = 4)

        val result = detector.detectLaps(session, startLineFor(startLineAlongTrack = false))

        assertTrue(LapReplayHarness.describe(result), result is LocalLapDetector.DetectionResult.Success)
        assertEquals(
            "A correctly captured line must give the same answer as a badly captured one",
            3, (result as LocalLapDetector.DetectionResult.Success).laps.size
        )
    }

    @Test
    fun aStartFinishOnACornerCountsOnePassPerLapRatherThanTwo() {
        // Adversarial: with the start/finish on a 90 degree corner, a single pass
        // offers two candidates a second apart - the car arriving, and the car
        // leaving - because the detection plane turns with the car. Only one of
        // them is the lap boundary.
        val session = syntheticLaps(lapCount = 4, startOffsetM = 0.0)

        val outcome = detector.detectLapsWithDiagnostics(
            session, startLineFor(startLineAlongTrack = false, startOffsetM = 0.0)
        )

        assertEquals(
            "One accepted crossing per pass. " + LapReplayHarness.describe(outcome.result),
            4, outcome.diagnostics!!.acceptedCrossings.size
        )
        assertTrue(
            "The arriving candidate should be rejected on heading",
            outcome.diagnostics!!.rejectedCrossings.any {
                it.reason == LocalLapDetector.RejectionReason.HEADING_MISMATCH
            }
        )
        assertEquals(3, (outcome.result as LocalLapDetector.DetectionResult.Success).laps.size)
    }

    @Test
    fun distanceTravelledSurvivesARejectedCandidate() {
        // Regression guard. Resetting the distance when a candidate is rejected
        // makes the legitimate crossing that follows it - a second later, on the far
        // side of the same corner - look as though the car had barely moved, and the
        // lap is silently lost.
        val session = syntheticLaps(lapCount = 4, startOffsetM = 0.0)

        val outcome = detector.detectLapsWithDiagnostics(
            session, startLineFor(startLineAlongTrack = false, startOffsetM = 0.0)
        )

        assertFalse(
            "No crossing should be rejected for being too close to the start on a " +
                "circuit that runs 100m from it: " + outcome.diagnostics!!.rejectedCrossings,
            outcome.diagnostics!!.rejectedCrossings.any {
                it.reason == LocalLapDetector.RejectionReason.TOO_CLOSE_TO_START
            }
        )
    }

    // -------------------------------------------------------------- diagnostics

    @Test
    fun theDiagnosticsNameTheIncident13FailureDirectly() {
        val fixture = LapReplayHarness.load("teste3", tempFolder.newFolder())

        val outcome = detector.detectLapsWithDiagnostics(fixture.telemetryFile, fixture.startLine)
        val diagnostics = requireNotNull(outcome.diagnostics)

        assertEquals("start line length", 7.15, diagnostics.startLineLengthM, 0.05)
        assertEquals("start line bearing", 283.7, diagnostics.startLineBearingDeg, 0.5)
        assertTrue(
            "The line lay along the racing direction; the angle should be near zero, was " +
                "${diagnostics.angleBetweenLineAndTravelDeg}",
            (diagnostics.angleBetweenLineAndTravelDeg ?: 90.0) < 10.0
        )
        assertEquals("observed sample rate", 1.0, diagnostics.observedSampleRateHz, 0.1)
        assertEquals("five passes of the start/finish", 5, diagnostics.acceptedCrossings.size)
    }

    @Test
    fun everyCrossingRecordsHowFarToTheSideTheCarPassed() {
        val fixture = LapReplayHarness.load("teste3", tempFolder.newFolder())

        val outcome = detector.detectLapsWithDiagnostics(fixture.telemetryFile, fixture.startLine)

        outcome.diagnostics!!.acceptedCrossings.forEach { crossing ->
            assertTrue(
                "This driver was repeatable to about two metres; got ${crossing.lateralOffsetM}",
                crossing.lateralOffsetM < 5.0
            )
        }
    }

    // ------------------------------------------------------------ interpolation

    @Test
    fun crossingTimesAreInterpolatedRatherThanSnappedToASample() {
        val fixture = LapReplayHarness.load("teste3", tempFolder.newFolder())

        val outcome = detector.detectLapsWithDiagnostics(fixture.telemetryFile, fixture.startLine)
        val crossings = outcome.diagnostics!!.acceptedCrossings

        // Samples in this session land on whole seconds. If crossing times were taken
        // from a sample they would all be multiples of 1000 ms; interpolation puts
        // them between samples, where the car actually was.
        val onASampleBoundary = crossings.count { it.timestampMs % 1000L == 0L }
        assertTrue(
            "Crossing instants look snapped to samples, not interpolated: " +
                crossings.map { it.timestampMs % 1000L },
            onASampleBoundary < crossings.size
        )
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Builds a session driving a square circuit, passing the start point once per
     * lap in a consistent direction.
     *
     * @param startOffsetM where on the circuit the start/finish sits, measured
     *   along the perimeter from the south-west corner. The default of 50 m puts it
     *   in the middle of a straight, as a start/finish normally is; 0 m puts it on a
     *   corner, which is the awkward case.
     */
    private fun syntheticLaps(
        lapCount: Int,
        lapSeconds: Int = 40,
        startOffsetM: Double = 50.0
    ): File {
        val lines = StringBuilder()
        var timestamp = 1_700_000_000_000L

        repeat(lapCount) {
            for (step in 0 until lapSeconds) {
                val (lat, lng) = squareCircuitPoint(step.toDouble() / lapSeconds, startOffsetM)
                lines.append(
                    """{"latitude":$lat,"longitude":$lng,"speedMs":15.0,"headingDeg":0.0,""" +
                        """"accelX":0.0,"accelY":0.0,"accelZ":9.8,"gyroX":0.0,"gyroY":0.0,""" +
                        """"gyroZ":0.0,"gpsAccuracyM":3.0,"timestampMs":$timestamp}"""
                ).append('\n')
                timestamp += 1000L
            }
        }

        return File(tempFolder.newFolder(), "synthetic.jsonl").apply { writeText(lines.toString()) }
    }

    /**
     * A 400 m square lap. Coordinates are returned relative to the start/finish, so
     * fraction 0 is always the start point and the car is always heading east there.
     */
    private fun squareCircuitPoint(fraction: Double, startOffsetM: Double): Pair<Double, Double> {
        val sideM = 100.0
        val alongPerimeter = (fraction * 4 * sideM + startOffsetM) % (4 * sideM)
        val (cornerEastM, cornerNorthM) = when {
            alongPerimeter < sideM -> Pair(alongPerimeter, 0.0)
            alongPerimeter < 2 * sideM -> Pair(sideM, alongPerimeter - sideM)
            alongPerimeter < 3 * sideM -> Pair(sideM - (alongPerimeter - 2 * sideM), sideM)
            else -> Pair(0.0, sideM - (alongPerimeter - 3 * sideM))
        }
        return Pair(
            BASE_LAT + cornerNorthM / 111_320.0,
            BASE_LNG + (cornerEastM - startOffsetM) / (111_320.0 * cos(Math.toRadians(BASE_LAT)))
        )
    }

    /**
     * The start/finish sits at the origin, where the car travels due east.
     * "Along the track" therefore means an east-west line, "across" a north-south one.
     */
    private fun startLineFor(
        startLineAlongTrack: Boolean,
        startOffsetM: Double = 50.0
    ): LocalLapDetector.StartLine {
        val halfLineM = 3.5   // the incident 13 baseline: shorter than the GPS error
        val anchor = squareCircuitPoint(0.0, startOffsetM)
        return if (startLineAlongTrack) {
            val dLng = halfLineM / (111_320.0 * cos(Math.toRadians(BASE_LAT)))
            LocalLapDetector.StartLine(
                anchor.first, anchor.second - dLng, anchor.first, anchor.second + dLng
            )
        } else {
            val dLat = halfLineM / 111_320.0
            LocalLapDetector.StartLine(
                anchor.first - dLat, anchor.second, anchor.first + dLat, anchor.second
            )
        }
    }

    @Test
    fun theSyntheticCircuitIsTheShapeTheseTestsAssume() {
        // Guards the helpers above: if the geometry drifts, the tests that rely on it
        // would fail for reasons that have nothing to do with lap detection.
        val start = squareCircuitPoint(0.0, 50.0)
        val quarter = squareCircuitPoint(0.25, 50.0)
        assertEquals(
            "a quarter of the way round from mid-straight is the middle of the next " +
                "side: half a side east and half a side north of the start",
            70.71,
            GeoUtils.haversineDistance(start.first, start.second, quarter.first, quarter.second),
            1.0
        )
        assertEquals(
            "the car passes the start/finish heading due east",
            90.0,
            GeoUtils.bearingDegrees(
                start.first, start.second,
                squareCircuitPoint(0.05, 50.0).first, squareCircuitPoint(0.05, 50.0).second
            ),
            1.0
        )
    }

    private companion object {
        const val BASE_LAT = 41.2005
        const val BASE_LNG = -8.6109
    }
}
