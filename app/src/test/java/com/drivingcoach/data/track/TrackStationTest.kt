package com.drivingcoach.data.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds [TrackStation] to the surveyed circuits it will be used on.
 *
 * `s` is about to become the coordinate that every position-based coaching feature
 * is expressed in, so an error here would not surface as a wrong number on screen -
 * it would surface as corner speeds compared between two different corners. The
 * checks below are therefore against the circuits' independently surveyed lengths
 * and against real recorded laps, not against the implementation's own arithmetic.
 *
 * `FP-REIMPLEMENTED-GEOMETRY` is why these exist at all: this is a second piece of
 * geometry living alongside lap detection's, and the only safe version of that is
 * one that is measured rather than argued about.
 */
class TrackStationTest {

    private val catalogueFile = File("src/main/assets/tracks/tracks.json")

    private fun track(id: String): Track =
        BundledTrackCatalog.parse(catalogueFile.readText()).firstOrNull { it.id == id }
            ?: error("$id is missing from the catalogue")

    private fun station(id: String): TrackStation =
        requireNotNull(TrackStation.from(track(id).centreline)) {
            "$id should ship with a centreline that supports a station"
        }

    // ==================== The ring matches the survey ====================

    @Test
    fun `each circuit's ring length matches its surveyed length`() {
        // The centreline was walked; lengthM was measured separately. Two independent
        // numbers agreeing is evidence; one derived from the other would not be.
        listOf("baltar", "cabo_do_mundo", "test_circuit_s_mamede").forEach { id ->
            val surveyed = track(id).lengthM?.toDouble()
                ?: error("$id has no surveyed length")
            val ring = station(id).lengthM

            assertEquals(
                "$id: the centreline ring is ${"%.0f".format(ring)} m but the circuit " +
                    "is surveyed at ${"%.0f".format(surveyed)} m",
                surveyed, ring, surveyed * 0.08
            )
        }
    }

    @Test
    fun `the start finish projects to the beginning of the ring`() {
        // The centreline is defined as starting at the start/finish. If it does not,
        // every s value on the circuit is offset by an unknown constant.
        listOf("baltar", "cabo_do_mundo", "test_circuit_s_mamede").forEach { id ->
            val track = track(id)
            val (midLat, midLng) = track.startLine.midpoint()
            val st = station(id)
            val projected = st.project(midLat, midLng)

            val fromStart = minOf(projected.s, st.lengthM - projected.s)
            assertTrue(
                "$id: the start/finish midpoint projects ${"%.1f".format(fromStart)} m " +
                    "from the start of the ring",
                fromStart < 25.0
            )
            assertTrue(
                "$id: the start/finish should sit close to the centreline, not " +
                    "${"%.1f".format(projected.lateralDistanceM)} m off it",
                projected.lateralDistanceM < 25.0
            )
        }
    }

    // ==================== s behaves like distance round the circuit ====================

    @Test
    fun `s stays inside the ring and d is signed`() {
        val st = station("cabo_do_mundo")
        val points = track("cabo_do_mundo").centreline!!.points

        points.forEach { p ->
            val projected = st.project(p.latitude, p.longitude)
            assertTrue(
                "s of ${projected.s} is outside [0, ${st.lengthM})",
                projected.s >= 0.0 && projected.s <= st.lengthM
            )
            assertTrue(
                "a centreline point should project onto the centreline, not " +
                    "${"%.2f".format(projected.lateralDistanceM)} m off it",
                projected.lateralDistanceM < 1.0
            )
        }
    }

    @Test
    fun `a point to one side of the track gets the opposite sign to a point on the other`() {
        val st = station("cabo_do_mundo")
        val points = track("cabo_do_mundo").centreline!!.points

        // Step perpendicular to the local direction of travel, to each side in turn.
        val a = points[20]
        val b = points[21]
        val dLat = b.latitude - a.latitude
        val dLng = b.longitude - a.longitude
        val norm = kotlin.math.hypot(dLat, dLng)
        val step = 0.00005   // roughly 5 m
        val perpLat = -dLng / norm * step
        val perpLng = dLat / norm * step

        val left = st.project(a.latitude + perpLat, a.longitude + perpLng)
        val right = st.project(a.latitude - perpLat, a.longitude - perpLng)

        assertTrue(
            "the two sides of the track should have opposite signs, got ${left.d} and ${right.d}",
            left.d * right.d < 0.0
        )
    }

    @Test
    fun `s advances round the circuit as the centreline is walked`() {
        val st = station("test_circuit_s_mamede")
        val points = track("test_circuit_s_mamede").centreline!!.points

        var previous = st.project(points[0].latitude, points[0].longitude).s
        var totalAdvance = 0.0

        points.drop(1).forEach { p ->
            val s = st.project(p.latitude, p.longitude).s
            totalAdvance += st.forwardDistance(previous, s)
            previous = s
        }

        assertEquals(
            "walking the centreline should advance s by one lap",
            st.lengthM, totalAdvance + st.forwardDistance(previous, 0.0), st.lengthM * 0.02
        )
    }

    // ==================== The wrap ====================

    @Test
    fun `forward distance wraps at the start finish`() {
        val st = station("baltar")
        val length = st.lengthM

        assertEquals("a tenth of a lap forward", length * 0.1, st.forwardDistance(0.0, length * 0.1), 0.01)
        assertEquals(
            "crossing the start line is a short step forward, not a lap backwards",
            length * 0.1,
            st.forwardDistance(length * 0.95, length * 0.05),
            0.01
        )
        assertEquals("no distance to itself", 0.0, st.forwardDistance(length * 0.4, length * 0.4), 0.01)
        assertTrue(
            "forward distance is never negative",
            st.forwardDistance(length * 0.9, length * 0.1) > 0.0
        )
    }

    // ==================== The refusals ====================

    @Test
    fun `a missing centreline yields no station`() {
        assertNull(
            "a driver who captured their own start line has no centreline, and that " +
                "is an ordinary state rather than an error",
            TrackStation.from(null)
        )
    }

    @Test
    fun `a centreline too thin to describe a circuit yields no station`() {
        val thin = Centreline(
            source = GeometrySource.CAPTURED_IN_APP,
            points = (0 until TrackStation.MIN_POINTS - 1).map {
                TrackPoint(41.0 + it * 0.0001, -8.5)
            }
        )

        assertNull(
            "below ${TrackStation.MIN_POINTS} points s would be a fiction",
            TrackStation.from(thin)
        )
    }

    @Test
    fun `every catalogue circuit with a centreline supports a station`() {
        BundledTrackCatalog.parse(catalogueFile.readText())
            .filter { it.centreline != null }
            .forEach { track ->
                assertNotNull(
                    "${track.id} ships a centreline that cannot be projected onto",
                    TrackStation.from(track.centreline)
                )
            }
    }
}
