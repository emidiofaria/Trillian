package com.drivingcoach.data.track

import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the Cabo do Mundo entry to its own internal claims.
 *
 * [BundledTrackCatalogTest] does this for Baltar, and six of its seven tests look up
 * `id == "baltar"` directly, so a second circuit inherits almost no coverage from it.
 * This class is the missing half. [CaboDoMundoSurveyCorroborationTest] then asks the
 * harder question of whether the numbers agree with a kart.
 *
 * ## Why every one of these is worth a test
 *
 * `Track` declares no validation - no `require`, no `init` block, and every field
 * except `id`, `name` and `startLine` is nullable with a default. A circuit with a
 * backwards heading, a missing length or a four-point centreline parses cleanly and
 * produces laps. It simply produces the wrong ones, with nothing logged anywhere.
 * The catalogue has no runtime guardrail, so the guardrail has to live here.
 */
class CaboDoMundoCatalogueTest {

    private val catalogueFile = File("src/main/assets/tracks/tracks.json")

    private fun track(): Track =
        BundledTrackCatalog.parse(catalogueFile.readText())
            .firstOrNull { it.id == "cabo_do_mundo" }
            ?: error("Cabo do Mundo is missing from the catalogue")

    @Test
    fun theCircuitIsInTheCatalogueAndItsIdIsUnique() {
        val all = BundledTrackCatalog.parse(catalogueFile.readText())
        assertEquals(
            "a duplicate id would shadow one circuit with another, silently",
            1, all.count { it.id == "cabo_do_mundo" }
        )
        assertEquals("Cabo do Mundo", track().name)
        assertEquals("Leça da Palmeira, Matosinhos, Portugal", track().location)
    }

    /**
     * The four fields without which the detector is no better informed than it was
     * before the circuit existed.
     */
    @Test
    fun theCircuitCarriesThePriorsTheDetectorNeeds() {
        val priors = track().priors()

        assertEquals("the direction the circuit is driven", 59.8, priors.travelHeadingDeg!!, 0.001)
        assertEquals("surveyed lap length, what LD-22 judges a lap against", 826, priors.lengthM)
        assertEquals("what a quick pilot laps this circuit in", 50_000L, priors.fastestLapMs)
        assertEquals(
            "the circuit's fastest lap should tighten the minimum gap between crossings to 40 s",
            40_000L, priors.minLapTimeMs()
        )
    }

    /**
     * `slowestLapMs` was the last number in the entry with nothing behind it.
     *
     * It arrived as a copy of Baltar's 120 s, on a circuit 194 m shorter, and the
     * assertion that used to stand here read `assertEquals(120_000L, slowestLapMs)` -
     * which restates the catalogue rather than testing it, and would have passed just as
     * contentedly on any figure at all. TL-15 asks for the envelope to be corroborated,
     * and a tautology is not corroboration.
     *
     * There is no measurement that fixes the slow end the way the survey fixes the
     * length, because a slow lap is a driver having a bad one rather than a property of
     * the circuit. What can be pinned is the band the figure has to fall in to be doing
     * its job, and both edges are real:
     *
     * Below it, the flying laps in the recorded session. A `slowestLapMs` under those is
     * an envelope that excludes laps known to have been driven.
     *
     * Above it, LD-22's discard floor. The app keeps any lap averaging 5 m/s or better,
     * which over 826 m is a lap of up to 165.2 s, so a `slowestLapMs` beyond that would
     * promise a driver a window wider than the one the detector actually enforces.
     */
    @Test
    fun theSlowEndOfTheEnvelopeSitsAboveRealLapsAndBelowWhatTheDetectorWillKeep() {
        val priors = track().priors()
        val slowest = priors.slowestLapMs!! / 1000.0

        val slowestFlyingLapS = 70.9
        assertTrue(
            "a slow-end envelope of $slowest s would exclude laps this circuit is known " +
                "to have been driven in ($slowestFlyingLapS s)",
            slowest > slowestFlyingLapS
        )

        val ld22FloorS = priors.lengthM!! / LocalLapDetector.MIN_PLAUSIBLE_LAP_SPEED_MS
        assertTrue(
            "the screen promises laps of at most $slowest s while LD-22 keeps anything " +
                "up to ${"%.1f".format(ld22FloorS)} s, so the envelope must not claim " +
                "to be tighter than the detector is",
            slowest <= ld22FloorS
        )
    }

    /**
     * A start/finish is only usable if it lies across the direction of travel.
     * Incident 13 was a line lying along it, which is geometrically undetectable,
     * and this single angle is what tells the two cases apart.
     */
    @Test
    fun theStartLineLiesSquareAcrossTheDirectionOfTravel() {
        val track = track()
        val line = track.startLine

        assertEquals("surveyed on foot across the track", 7.01, track.startLineLengthM(), 0.1)

        val bearing = GeoUtils.bearingDegrees(line.lat1, line.lng1, line.lat2, line.lng2)
        val difference = GeoUtils.angularDifferenceDegrees(bearing, track.travelHeadingDeg!!)
        val angleToTravel = if (difference > 90.0) 180.0 - difference else difference
        assertTrue(
            "the line should lie roughly square across the track, measured " +
                "${"%.1f".format(angleToTravel)} deg",
            angleToTravel >= 80.0
        )
    }

    /**
     * The heading is the field that fails catastrophically and silently: reversed, it
     * rejects every genuine crossing as wrong-way and the driver is shown no laps and
     * no error. This derives the direction of travel independently, from the walked
     * centreline leaving the start line, and checks the declared value against it.
     *
     * 25 degrees of tolerance because the centreline is hand-placed waypoints roughly
     * 5 m apart, so a bearing taken over the first few carries real noise. The
     * measured disagreement is 1.9 degrees - the tolerance is nowhere near binding,
     * and it does not need to be. What it has to catch is 180.
     */
    @Test
    fun theDeclaredHeadingAgreesWithTheWalkedCentreline() {
        val track = track()
        val points = track.centreline!!.points

        // far enough along the ring that the bearing is not derived from points
        // closer together than the survey's own error - FP-DEGENERATE-BASELINE
        var travelled = 0.0
        var index = 0
        while (travelled < 18.0 && index < points.size - 1) {
            travelled += GeoUtils.haversineDistance(
                points[index].latitude, points[index].longitude,
                points[index + 1].latitude, points[index + 1].longitude
            )
            index++
        }
        assertTrue("need a baseline of at least 18 m, got ${"%.1f".format(travelled)} m", travelled >= 18.0)

        val fromCentreline = GeoUtils.bearingDegrees(
            points.first().latitude, points.first().longitude,
            points[index].latitude, points[index].longitude
        )
        val deviation = GeoUtils.angularDifferenceDegrees(fromCentreline, track.travelHeadingDeg!!)

        assertTrue(
            "the centreline leaves the start/finish on ${"%.1f".format(fromCentreline)} deg but the " +
                "catalogue says the circuit is driven at ${track.travelHeadingDeg} deg, a " +
                "${"%.1f".format(deviation)} deg disagreement",
            deviation <= 25.0
        )
    }

    @Test
    fun theCentrelineIsAClosedRingStartingAtTheStartFinish() {
        val track = track()
        val centreline = track.centreline
        assertNotNull("Cabo do Mundo should ship with a centreline", centreline)
        val points = centreline!!.points

        val (midLat, midLng) = track.startLine.midpoint()
        val first = points.first()
        assertEquals(
            "the centreline must start at the start/finish, so distance along it is lap distance",
            0.0,
            GeoUtils.haversineDistance(first.latitude, first.longitude, midLat, midLng),
            1.0
        )

        val segments = (1 until points.size).map { i ->
            GeoUtils.haversineDistance(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
        }
        val closing = GeoUtils.haversineDistance(
            points.last().latitude, points.last().longitude, first.latitude, first.longitude
        )
        val perimeter = segments.sum() + closing

        assertEquals("closed ring length", 826.0, perimeter, 10.0)
        assertEquals(
            "the published length should be the measured one",
            perimeter, track.lengthM!!.toDouble(), 10.0
        )

        // A closing segment far longer than the rest means the ring does not close
        // where it claims to - the waypoints stop short and the gap is being papered
        // over by the perimeter sum.
        val mean = perimeter / (segments.size + 1)
        assertTrue(
            "the closing segment is ${"%.1f".format(closing)} m against a mean spacing of " +
                "${"%.1f".format(mean)} m, which suggests the ring does not close where it should",
            closing < mean * 3.0
        )

        // last, because the checks above describe the shape and this one only counts it:
        // a resurvey may legitimately change the count, and should fail on geometry first
        assertEquals("walked mid-track, 159 waypoints", 159, centreline.size)
    }

    /**
     * The start line and the centreline are two different datasets gathered on the
     * same day but in different ways, and one blanket claim across both would be
     * untrue of at least one of them.
     */
    @Test
    fun provenanceIsRecordedPerDatasetRatherThanPerTrack() {
        val track = track()

        assertEquals(
            "the start line was walked, not read off a map",
            GeometrySource.SURVEYED_ON_FOOT, track.startLine.source
        )
        assertNotNull("when the line was surveyed", track.startLine.recordedAt)

        assertEquals(
            "the centreline was walked too - same method, but a separate dataset",
            GeometrySource.SURVEYED_ON_FOOT, track.centreline!!.source
        )
        assertNotNull("how the survey was done should survive in the file", track.centreline!!.method)
        assertNotNull("when it was done should too", track.centreline!!.surveyedAt)
        assertNotNull(
            "and who walked it, so the dataset can be questioned later",
            track.centreline!!.surveyedBy
        )
    }

    /**
     * The detector's corridor is a fixed [LocalLapDetector.DETECTION_HALF_WIDTH_M]
     * around the start point, not the width of the line. That is deliberate - a
     * captured line is narrower than GPS error, which is what incident 13 proved -
     * but it means a circuit whose own tarmac loops back within that radius can have
     * a second piece of track inside its own detection corridor.
     *
     * Cabo do Mundo does. Its return section passes 18.4 m from the start point,
     * against a 15 m half width: a margin of 3.4 m, where Baltar has 9.7 m. Nothing
     * fails today, and the direction filter catches what the corridor lets through
     * (see [CaboDoMundoSurveyCorroborationTest.theDirectionPriorIsLoadBearingOnThisCircuit]).
     * This exists so that shrinking the margin - by moving the line, or by widening
     * the corridor - has to be a decision rather than an accident.
     */
    @Test
    fun theNearestOtherPartOfTheCircuitStaysOutsideTheDetectionCorridor() {
        val track = track()
        val (startLat, startLng) = track.startLine.midpoint()
        val points = track.centreline!!.points

        // skip the waypoints either side of the start/finish, which are the start
        // straight itself rather than another part of the circuit
        val elsewhere = points.drop(8).dropLast(8)
        val nearest = elsewhere.minOf {
            GeoUtils.haversineDistance(startLat, startLng, it.latitude, it.longitude)
        }

        assertTrue(
            "the nearest other part of the circuit is ${"%.1f".format(nearest)} m from the start " +
                "point, inside the ${LocalLapDetector.DETECTION_HALF_WIDTH_M} m detection corridor - " +
                "the direction filter is then the only thing preventing a double count",
            nearest > LocalLapDetector.DETECTION_HALF_WIDTH_M
        )
    }
}
