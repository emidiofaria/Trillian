package com.drivingcoach.data.track

import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the Test Circuit S.Mamede entry to its own internal claims.
 *
 * Shaped on [CaboDoMundoCatalogueTest]. `Track` validates nothing, so a backwards heading,
 * a stale length or a ring that starts in the wrong place parses cleanly and produces the
 * wrong laps with nothing logged. These assertions are the only guardrail the entry has.
 * [SMamedeSurveyCorroborationTest] then asks whether a kart agrees.
 *
 * ## Where this entry differs from the other two
 *
 * `lengthM` (802 m) was **measured from the 2026-09-14 kart session**, not derived from the
 * walk. The walked ring measures 821.6 m. That makes the walk, not the session, the
 * independent check on the length (TL-15) - and the two disagree by 2.5 %, more than the
 * fixed 10 m tolerance the other circuits are held to. See
 * [theWalkedRingCorroboratesTheDeclaredLengthWithinThreePercent].
 */
class SMamedeCatalogueTest {

    private val catalogueFile = File("src/main/assets/tracks/tracks.json")

    private fun track(): Track =
        BundledTrackCatalog.parse(catalogueFile.readText())
            .firstOrNull { it.id == ID }
            ?: error("Test Circuit S.Mamede is missing from the catalogue")

    @Test
    fun theCircuitIsInTheCatalogueAndItsIdIsUnique() {
        val all = BundledTrackCatalog.parse(catalogueFile.readText())
        assertEquals(
            "a duplicate id would shadow one circuit with another, silently",
            1, all.count { it.id == ID }
        )
        assertEquals("Test Circuit S.Mamede", track().name)
        assertEquals("Matosinhos, Porto, Portugal", track().location)
        assertEquals(6, track().cornerCount)
    }

    @Test
    fun theCircuitCarriesThePriorsTheDetectorNeeds() {
        val priors = track().priors()

        assertEquals("the direction the circuit is driven", 284.7, priors.travelHeadingDeg!!, 0.001)
        assertEquals("lap length measured from the recorded session", 802, priors.lengthM)
        assertEquals("what a quick pilot laps this circuit in", 50_000L, priors.fastestLapMs)
        assertEquals("a weekend driver's worst", 120_000L, priors.slowestLapMs)
        assertEquals(
            "the circuit's fastest lap should tighten the minimum gap between crossings to 40 s",
            40_000L, priors.minLapTimeMs()
        )
    }

    /**
     * The slow end has to admit every lap known to have been driven here (the recording's
     * slowest flying lap is 79.9 s) and must not promise more than LD-22 keeps, which over
     * 802 m is anything up to 160.4 s.
     */
    @Test
    fun theSlowEndOfTheEnvelopeSitsAboveRealLapsAndBelowWhatTheDetectorWillKeep() {
        val priors = track().priors()
        val slowest = priors.slowestLapMs!! / 1000.0

        val slowestRecordedLapS = 79.9
        assertTrue(
            "a slow-end envelope of $slowest s would exclude laps this circuit is known " +
                "to have been driven in ($slowestRecordedLapS s)",
            slowest > slowestRecordedLapS
        )

        val ld22FloorS = priors.lengthM!! / LocalLapDetector.MIN_PLAUSIBLE_LAP_SPEED_MS
        assertTrue(
            "the envelope promises laps of at most $slowest s while LD-22 keeps anything up " +
                "to ${"%.1f".format(ld22FloorS)} s",
            slowest <= ld22FloorS
        )
    }

    @Test
    fun theStartLineLiesSquareAcrossTheDirectionOfTravel() {
        val track = track()
        val line = track.startLine

        assertEquals("surveyed on foot across the track", 10.16, track.startLineLengthM(), 0.1)

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
     * The heading was supplied as two points a kart passes through, not as a number, and
     * is the field that fails silently when wrong. This derives the direction of travel
     * independently from the walked ring leaving the start/finish. Measured: 284.5 deg
     * against the declared 284.7. The tolerance exists to catch 180, not 0.2.
     */
    @Test
    fun theDeclaredHeadingAgreesWithTheWalkedCentreline() {
        val track = track()
        val points = track.centreline!!.points

        // waypoints here are ~11 m apart, so two segments are needed for an 18 m
        // baseline - FP-DEGENERATE-BASELINE
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

    /**
     * The walk began 173 m round the lap from the start/finish. The ring was rotated to
     * start there, with one point inserted at the start line's midpoint - which the walked
     * segment passes within 0.3 m of. No walked waypoint was moved.
     */
    @Test
    fun theCentrelineIsAClosedRingStartingAtTheStartFinish() {
        val track = track()
        val centreline = track.centreline
        assertNotNull("S.Mamede should ship with a centreline", centreline)
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

        assertEquals("closed ring length as walked", 821.6, perimeter, 2.0)

        val mean = perimeter / (segments.size + 1)
        assertTrue(
            "the closing segment is ${"%.1f".format(closing)} m against a mean spacing of " +
                "${"%.1f".format(mean)} m, which suggests the ring does not close where it should",
            closing < mean * 3.0
        )

        assertEquals("73 walked waypoints plus the start/finish", 74, centreline.size)
    }

    /**
     * `lengthM` came from the kart session, so the session cannot corroborate it (TL-15:
     * a session used to derive a figure can only agree with it). The walked ring is the
     * independent measurement.
     *
     * They differ by 19.6 m, 2.5 %. The other circuits are held to a fixed 10 m because
     * their length *is* their ring; here the two are different instruments - a phone walked
     * mid-track against 1 Hz fixes from a kart that cuts corners - and a percentage is the
     * honest tolerance. 3 % passes the measured 2.5 % and fails on a length entered for the
     * wrong circuit, or a ring that loses a section.
     */
    @Test
    fun theWalkedRingCorroboratesTheDeclaredLengthWithinThreePercent() {
        val track = track()
        val points = track.centreline!!.points
        val perimeter = points.indices.sumOf { i ->
            val a = points[i]
            val b = points[(i + 1) % points.size]
            GeoUtils.haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        val declared = track.lengthM!!.toDouble()

        assertEquals(
            "the declared $declared m and the walked ring's ${"%.1f".format(perimeter)} m should " +
                "agree within 3 %",
            declared, perimeter, declared * 0.03
        )
    }

    @Test
    fun provenanceIsRecordedPerDatasetRatherThanPerTrack() {
        val track = track()

        assertEquals(
            "the start line was walked, not read off a map",
            GeometrySource.SURVEYED_ON_FOOT, track.startLine.source
        )
        assertEquals("2026-10-05", track.startLine.recordedAt)

        assertEquals(
            "the centreline was walked with a phone, independently of the kart session",
            GeometrySource.SURVEYED_ON_FOOT, track.centreline!!.source
        )
        assertNotNull("how the survey was done should survive in the file", track.centreline!!.method)
        assertEquals("2026-10-05", track.centreline!!.surveyedAt)
        assertNotNull("and who walked it", track.centreline!!.surveyedBy)
    }

    /**
     * Nothing else on this circuit comes near the start/finish - the nearest other part of
     * the ring is the far side of the infield, ~80 m away against a 15 m corridor - so
     * unlike Cabo do Mundo the direction prior is not what prevents a double count here.
     */
    @Test
    fun theNearestOtherPartOfTheCircuitStaysOutsideTheDetectionCorridor() {
        val track = track()
        val (startLat, startLng) = track.startLine.midpoint()
        val points = track.centreline!!.points

        // ~11 m spacing: four waypoints either side is the start straight itself
        val elsewhere = points.drop(4).dropLast(4)
        val nearest = elsewhere.minOf {
            GeoUtils.haversineDistance(startLat, startLng, it.latitude, it.longitude)
        }

        assertTrue(
            "the nearest other part of the circuit is ${"%.1f".format(nearest)} m from the start " +
                "point, inside the ${LocalLapDetector.DETECTION_HALF_WIDTH_M} m detection corridor",
            nearest > LocalLapDetector.DETECTION_HALF_WIDTH_M * 2
        )
    }

    private companion object {
        const val ID = "test_circuit_s_mamede"
    }
}
