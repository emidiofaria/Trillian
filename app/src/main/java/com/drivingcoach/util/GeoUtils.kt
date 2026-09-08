package com.drivingcoach.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utility object for geographic calculations used in lap detection.
 */
object GeoUtils {

    private const val EARTH_RADIUS_M = 6371000.0

    /**
     * Calculates the haversine distance between two GPS coordinates in metres.
     *
     * @param lat1 Latitude of first point in degrees
     * @param lng1 Longitude of first point in degrees
     * @param lat2 Latitude of second point in degrees
     * @param lng2 Longitude of second point in degrees
     * @return Distance in metres
     */
    fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_M * c
    }

    /**
     * Determines if a GPS path segment crosses a defined line (start/finish line).
     *
     * Uses 2D line-segment intersection via cross product method.
     * Projects GPS coordinates to a local Cartesian plane for accuracy.
     *
     * @param lineLat1 Latitude of line point 1 (e.g., track edge left)
     * @param lineLng1 Longitude of line point 1
     * @param lineLat2 Latitude of line point 2 (e.g., track edge right)
     * @param lineLng2 Longitude of line point 2
     * @param prevLat Latitude of previous GPS sample
     * @param prevLng Longitude of previous GPS sample
     * @param currLat Latitude of current GPS sample
     * @param currLng Longitude of current GPS sample
     * @return true if the path segment crosses the line
     */
    fun lineIntersection(
        lineLat1: Double, lineLng1: Double,
        lineLat2: Double, lineLng2: Double,
        prevLat: Double, prevLng: Double,
        currLat: Double, currLng: Double
    ): Boolean {
        // Convert to local Cartesian coordinates (metres) relative to line midpoint
        val refLat = (lineLat1 + lineLat2) / 2
        val refLng = (lineLng1 + lineLng2) / 2

        val lineP1 = toLocal(lineLat1, lineLng1, refLat, refLng)
        val lineP2 = toLocal(lineLat2, lineLng2, refLat, refLng)
        val segP1 = toLocal(prevLat, prevLng, refLat, refLng)
        val segP2 = toLocal(currLat, currLng, refLat, refLng)

        return segmentsIntersect(
            lineP1.first, lineP1.second, lineP2.first, lineP2.second,
            segP1.first, segP1.second, segP2.first, segP2.second
        )
    }

    /**
     * Converts GPS coordinates to local Cartesian coordinates (x, y) in metres.
     * Uses equirectangular approximation which is accurate for small distances.
     */
    private fun toLocal(lat: Double, lng: Double, refLat: Double, refLng: Double): Pair<Double, Double> {
        val x = Math.toRadians(lng - refLng) * EARTH_RADIUS_M * cos(Math.toRadians(refLat))
        val y = Math.toRadians(lat - refLat) * EARTH_RADIUS_M
        return Pair(x, y)
    }

    /**
     * Checks if two line segments intersect using cross product method.
     *
     * Segment 1: (ax, ay) to (bx, by)
     * Segment 2: (cx, cy) to (dx, dy)
     */
    private fun segmentsIntersect(
        ax: Double, ay: Double, bx: Double, by: Double,
        cx: Double, cy: Double, dx: Double, dy: Double
    ): Boolean {
        // Direction vectors
        val abx = bx - ax
        val aby = by - ay
        val cdx = dx - cx
        val cdy = dy - cy

        // Cross products to determine orientation
        val d1 = crossProduct(cdx, cdy, ax - cx, ay - cy)
        val d2 = crossProduct(cdx, cdy, bx - cx, by - cy)
        val d3 = crossProduct(abx, aby, cx - ax, cy - ay)
        val d4 = crossProduct(abx, aby, dx - ax, dy - ay)

        // Segments intersect if points are on opposite sides of each other's lines
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
        ) {
            return true
        }

        // Check for collinear cases (points on the segment)
        if (d1 == 0.0 && onSegment(cx, cy, dx, dy, ax, ay)) return true
        if (d2 == 0.0 && onSegment(cx, cy, dx, dy, bx, by)) return true
        if (d3 == 0.0 && onSegment(ax, ay, bx, by, cx, cy)) return true
        if (d4 == 0.0 && onSegment(ax, ay, bx, by, dx, dy)) return true

        return false
    }

    /**
     * Converts a GPS coordinate to local Cartesian metres relative to a reference
     * point, using the equirectangular approximation.
     *
     * x is metres east of the reference, y is metres north. Valid for the track
     * sizes this application supports (see SRS LD-13).
     */
    fun toLocalMetres(lat: Double, lng: Double, refLat: Double, refLng: Double): Pair<Double, Double> =
        toLocal(lat, lng, refLat, refLng)

    /**
     * Initial bearing from one coordinate to another, in degrees clockwise from north.
     *
     * Used in preference to [android.location.Location.getBearing] when detecting
     * laps: the recorded `headingDeg` is 0.0 whenever the provider had no bearing
     * to report, which is indistinguishable from genuinely heading due north.
     * A bearing computed from two consecutive positions is always available.
     */
    fun bearingDegrees(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLng = Math.toRadians(lng2 - lng1)

        val y = sin(dLng) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLng)

        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * Smallest absolute difference between two bearings, in degrees (0..180).
     *
     * Wraps correctly across north: 350° and 010° differ by 20°, not 340°.
     */
    fun angularDifferenceDegrees(bearingA: Double, bearingB: Double): Double {
        val raw = kotlin.math.abs(bearingA - bearingB) % 360.0
        return if (raw > 180.0) 360.0 - raw else raw
    }

    /**
     * 2D cross product of vectors (v1x, v1y) and (v2x, v2y).
     */
    private fun crossProduct(v1x: Double, v1y: Double, v2x: Double, v2y: Double): Double {
        return v1x * v2y - v1y * v2x
    }

    /**
     * Checks if point (px, py) lies on segment from (ax, ay) to (bx, by).
     */
    private fun onSegment(ax: Double, ay: Double, bx: Double, by: Double, px: Double, py: Double): Boolean {
        return px >= minOf(ax, bx) && px <= maxOf(ax, bx) &&
               py >= minOf(ay, by) && py <= maxOf(ay, by)
    }
}
