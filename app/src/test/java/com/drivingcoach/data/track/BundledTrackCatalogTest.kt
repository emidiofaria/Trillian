package com.drivingcoach.data.track

import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * Holds the shipped track catalogue to the measurements it claims.
 *
 * Baltar's entry is the first of its kind, and every number in it came from a real
 * session or a real survey rather than an estimate. An entry that drifts away from
 * the circuit it describes is worse than no entry at all: the detector now trusts
 * the heading it supplies, so a wrong one would reject laps that happened. These
 * assertions are what stops that changing by accident.
 *
 * The file parsed here is the one packaged into the APK, not a copy of it.
 */
class BundledTrackCatalogTest {

    private val catalogueFile = File("src/main/assets/tracks/tracks.json")

    private fun tracks(): List<Track> =
        BundledTrackCatalog.parse(catalogueFile.readText())

    private fun baltar(): Track =
        tracks().firstOrNull { it.id == "baltar" } ?: error("Baltar is missing from the catalogue")

    @Test
    fun theShippedCatalogueParses() {
        assertTrue("catalogue asset is missing: ${catalogueFile.absolutePath}", catalogueFile.exists())
        assertTrue("the catalogue should contain at least one circuit", tracks().isNotEmpty())
    }

    @Test
    fun baltarsStartLineIsTheOneValidatedAgainstTheSession() {
        val track = baltar()
        val line = track.startLine

        assertEquals("line length", 10.97, track.startLineLengthM(), 0.1)

        // A start/finish is only usable if it lies across the direction of travel.
        // Incident 13 was a line lying along it, which is geometrically undetectable,
        // and this single angle is what tells the two cases apart.
        val bearing = GeoUtils.bearingDegrees(line.lat1, line.lng1, line.lat2, line.lng2)
        val difference = GeoUtils.angularDifferenceDegrees(bearing, track.travelHeadingDeg!!)
        val angleToTravel = if (difference > 90.0) 180.0 - difference else difference
        assertEquals("the line should lie square across the track", 88.7, angleToTravel, 1.0)
    }

    @Test
    fun baltarCarriesThePriorsTheDetectorNeeds() {
        val priors = baltar().priors()

        assertEquals(137.8, priors.travelHeadingDeg!!, 0.001)
        assertEquals(40_000L, priors.fastestLapMs)
        assertEquals(120_000L, priors.slowestLapMs)
        assertEquals("the surveyed length is what decides whether a lap is credible", 1020, priors.lengthM)
        assertEquals(
            "the circuit's own best lap should tighten the minimum gap between crossings",
            32_000L, priors.minLapTimeMs()
        )
    }

    @Test
    fun baltarsCentrelineIsAClosedRingStartingAtTheStartFinish() {
        val track = baltar()
        val centreline = track.centreline
        assertNotNull("Baltar should ship with a centreline", centreline)
        val points = centreline!!.points

        assertEquals("surveyed on foot, 140 waypoints", 140, centreline.size)

        val (midLat, midLng) = track.startLine.midpoint()
        val first = points.first()
        assertEquals(
            "the centreline must start at the start/finish, so distance along it is lap distance",
            0.0,
            GeoUtils.haversineDistance(first.latitude, first.longitude, midLat, midLng),
            1.0
        )

        var length = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            length += GeoUtils.haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        val last = points.last()
        length += GeoUtils.haversineDistance(
            last.latitude, last.longitude, first.latitude, first.longitude
        )

        assertEquals("closed ring length", 1020.0, length, 10.0)
        assertEquals(
            "the published length should be the measured one",
            length.toInt().toDouble(), track.lengthM!!.toDouble(), 10.0
        )
    }

    @Test
    fun provenanceIsRecordedPerDatasetRatherThanPerTrack() {
        val track = baltar()

        assertEquals(
            "the line was read off mapping imagery",
            GeometrySource.MAP_COORDINATES, track.startLine.source
        )
        assertEquals(
            "the centreline was walked - a different dataset with a different provenance",
            GeometrySource.SURVEYED_ON_FOOT, track.centreline!!.source
        )
        assertNotNull("how the survey was done should survive in the file", track.centreline!!.method)
        assertNotNull("when it was done should too", track.centreline!!.surveyedAt)
        assertEquals(
            "and who walked it, so the dataset can be questioned later",
            "Emidio Costa", track.centreline!!.surveyedBy
        )
    }

    /**
     * A circuit's declared lap envelope and its surveyed length have to agree with
     * each other, because together they imply an average speed and only a narrow
     * band of speeds belongs to a kart.
     *
     * This is a cross-check rather than a value test. Any single field can look
     * reasonable on its own; it is the combination that exposes a length in the
     * wrong unit, an envelope guessed for a different circuit, or - the easiest
     * mistake to make when adding a track - a lap time entered in seconds where
     * the field wants milliseconds. That last one would put the implied speed
     * three orders of magnitude out, and nothing else in the suite would notice.
     */
    @Test
    fun everyCircuitsLengthAndLapEnvelopeImplyAPlausibleSpeed() {
        val floorKmh = LocalLapDetector.MIN_PLAUSIBLE_LAP_SPEED_MS * 3.6
        val ceilingKmh = 126.0

        tracks().forEach { track ->
            val length = track.lengthM
            val fastest = track.fastestLapMs
            val slowest = track.slowestLapMs
            if (length == null || fastest == null || slowest == null) return@forEach

            assertTrue(
                "${track.id}: fastest lap must not be slower than the slowest",
                fastest <= slowest
            )

            val topKmh = length / (fastest / 1000.0) * 3.6
            val bottomKmh = length / (slowest / 1000.0) * 3.6

            assertTrue(
                "${track.id}: a ${fastest / 1000} s lap over $length m is ${"%.0f".format(topKmh)} km/h, " +
                    "which is beyond a kart",
                topKmh <= ceilingKmh
            )
            assertTrue(
                "${track.id}: a ${slowest / 1000} s lap over $length m is ${"%.0f".format(bottomKmh)} km/h, " +
                    "below the speed at which the detector stops believing a lap happened",
                bottomKmh >= floorKmh
            )
        }
    }

    /**
     * The catalogue is only worth trusting if it describes the tarmac the karts are
     * on. Measured against the twelve laps of the incident 15 session: the racing
     * line sits a median of 4.0 m from the surveyed centreline, while the sixteen
     * minutes spent queuing beside the track sit 29.5 m away. That separation is
     * what makes a corridor filter possible at all.
     */
    @Test
    fun theCentrelineAgreesWithTheSessionRecordedOnIt() {
        val centreline = baltar().centreline!!.points
        val samples = File("src/test/resources/lapfixtures/baltar2/telemetry.jsonl")
            .readLines()
            .mapNotNull { line ->
                if (!line.contains("\"latitude\"")) return@mapNotNull null
                val o = org.json.JSONObject(line)
                Triple(o.getDouble("latitude"), o.getDouble("longitude"), o.getDouble("speedMs"))
            }
        assertTrue("fixture should be readable", samples.size > 1_000)

        val originLat = centreline.first().latitude
        val originLng = centreline.first().longitude
        val ring = centreline.map { GeoUtils.toLocalMetres(it.latitude, it.longitude, originLat, originLng) }

        fun distanceToRing(lat: Double, lng: Double): Double {
            val (px, py) = GeoUtils.toLocalMetres(lat, lng, originLat, originLng)
            var best = Double.MAX_VALUE
            for (i in ring.indices) {
                val (ax, ay) = ring[i]
                val (bx, by) = ring[(i + 1) % ring.size]
                val dx = bx - ax
                val dy = by - ay
                val lengthSquared = dx * dx + dy * dy
                val t = if (lengthSquared == 0.0) {
                    0.0
                } else {
                    (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
                }
                best = minOf(best, hypot(px - ax - t * dx, py - ay - t * dy))
            }
            return best
        }

        val racing = samples.filter { it.third >= 8.0 }.map { distanceToRing(it.first, it.second) }.sorted()
        val queuing = samples.filter { it.third < 3.0 }.map { distanceToRing(it.first, it.second) }.sorted()

        val racingMedian = racing[racing.size / 2]
        val queuingMedian = queuing[queuing.size / 2]

        // Measured against this fixture: racing median 3.99 m, p90 9.6 m, p95 11.9 m,
        // max 34.9 m; queue median 29.5 m. The 5 m ceiling is not arbitrary -- GPS
        // accuracy averages 6.4 m here and the circuit is roughly 10 m wide, so a
        // racing line sitting within half a track width of the surveyed centreline is
        // the best agreement the instrument can express. The 20 m floor for the queue
        // is set well below its real 29.5 m so that the test states "the queue is
        // somewhere else entirely" rather than pinning a number nobody chose.
        assertTrue(
            "the karts should be driving on the surveyed centreline, median was $racingMedian m",
            racingMedian <= 5.0
        )
        assertTrue(
            "the queue is beside the track, not on it, median was $queuingMedian m",
            queuingMedian >= 20.0
        )
    }
}
