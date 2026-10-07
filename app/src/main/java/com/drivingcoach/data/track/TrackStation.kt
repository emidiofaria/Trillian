package com.drivingcoach.data.track

import com.drivingcoach.util.GeoUtils

/**
 * Places a GPS fix on the circuit: how far round it is (`s`, metres from the
 * start/finish along the centreline) and how far off the centreline it sits
 * (`d`, metres, positive to the left of the direction of travel).
 *
 * ### What this is for
 *
 * Comparing two laps by time is circular. "Where was the driver 20 seconds in?" has a
 * different answer on a fast lap than on a slow one, so a time-aligned comparison is
 * largely measuring the misalignment. Comparing by *position* does not have that
 * problem: the exit of turn 4 is the exit of turn 4 on every lap, whatever the clock
 * says. Turn-by-turn speed, braking points and racing-line comparison all reduce to
 * "the same place on two different laps", and `s` is what makes "the same place"
 * expressible.
 *
 * ### What this is not
 *
 * **Not used by lap detection.** Detection has its own geometry, it works, and its
 * failures are well understood (`FP-REIMPLEMENTED-GEOMETRY` records what happens when
 * a second implementation of the same idea is introduced alongside the first). Nothing
 * here may be wired into it.
 *
 * **Not yet used by anything.** This ships dark and unreferenced by production code,
 * tested in isolation. That is intentional: it is the foundation for the next slice of
 * coaching features, and the alternative - writing it at the same time as its first
 * consumer - is how a foundation ends up shaped by one caller's convenience.
 *
 * ### Accuracy, honestly
 *
 * `d` is only as good as the GPS fix, and the reference device records 5-10 m of
 * accuracy at a circuit. Measured against the recorded sessions, the *lateral*
 * repeatability between laps is several times better than the absolute accuracy,
 * because much of the error is a slow bias that is common to all laps in a session and
 * cancels when laps are compared to each other. That makes `d` usable for comparing
 * one lap to another and **not** usable as an absolute statement about where the car
 * was on the road. Any future consumer must respect that distinction.
 *
 * `s` is far more robust than `d`: an error perpendicular to the track barely moves
 * the projection along it.
 */
class TrackStation private constructor(
    private val xs: DoubleArray,
    private val ys: DoubleArray,
    /** Cumulative distance to the start of each segment. */
    private val cumulative: DoubleArray,
    private val refLat: Double,
    private val refLng: Double,
    /** Total length of the closed centreline loop, in metres. */
    val lengthM: Double
) {

    /**
     * Where a fix sits on the circuit.
     *
     * @param s metres travelled from the start/finish along the centreline, in [0, lengthM)
     * @param d metres from the centreline. Positive is left of the direction of travel,
     *   negative is right. The sign is what distinguishes "ran wide at a right-hander"
     *   from "cut the apex", so it is kept rather than taking a magnitude.
     */
    data class Station(val s: Double, val d: Double) {
        val lateralDistanceM: Double get() = kotlin.math.abs(d)
    }

    /**
     * Projects a fix onto the centreline.
     *
     * Searches every segment rather than tracking the previous match. Tracking would be
     * faster, but it carries state across calls, and on a circuit where two parts of the
     * track run close together a stateful search that goes wrong stays wrong for the rest
     * of the lap. At the catalogue's sizes - under 200 points - the exhaustive search is
     * trivial, and it cannot drift.
     */
    fun project(latitude: Double, longitude: Double): Station {
        val (px, py) = GeoUtils.toLocalMetres(latitude, longitude, refLat, refLng)

        var bestSq = Double.MAX_VALUE
        var bestS = 0.0
        var bestD = 0.0

        for (i in 0 until xs.size - 1) {
            val ax = xs[i]; val ay = ys[i]
            val bx = xs[i + 1]; val by = ys[i + 1]

            val vx = bx - ax; val vy = by - ay
            val lenSq = vx * vx + vy * vy
            if (lenSq <= 0.0) continue

            // Fraction along this segment of the closest point, clamped to the segment
            // so that a fix beside a corner lands on the corner and not on the infinite
            // line through it.
            val t = (((px - ax) * vx + (py - ay) * vy) / lenSq).coerceIn(0.0, 1.0)

            val cx = ax + t * vx; val cy = ay + t * vy
            val dx = px - cx; val dy = py - cy
            val distSq = dx * dx + dy * dy

            if (distSq < bestSq) {
                bestSq = distSq
                bestS = cumulative[i] + t * kotlin.math.sqrt(lenSq)
                // 2D cross product of the segment direction with the offset: its sign
                // says which side of the direction of travel the fix is on.
                bestD = (vx * dy - vy * dx) / kotlin.math.sqrt(lenSq)
            }
        }

        return Station(s = bestS, d = bestD)
    }

    /**
     * Signed distance from [from] to [to] going forwards round the circuit, handling
     * the wrap at the start/finish.
     *
     * Without this, a comparison spanning the start line produces a distance of almost
     * a whole lap in the wrong direction - the loop-closure error that is easy to write
     * and hard to see in a result that still looks like a number.
     */
    fun forwardDistance(from: Double, to: Double): Double {
        val raw = (to - from) % lengthM
        return if (raw < 0) raw + lengthM else raw
    }

    companion object {
        /**
         * Fewest centreline points for a projection to mean anything. Below this the
         * "circuit" is a handful of straight lines and `s` would be a fiction.
         */
        const val MIN_POINTS = 8

        /**
         * Builds a station from a circuit's centreline, or returns null when the
         * centreline cannot support one.
         *
         * Returns null rather than throwing because a missing or thin centreline is an
         * ordinary, expected state - a driver who captured their own start line has no
         * centreline at all (TL-01) - and the caller's correct response is to offer
         * fewer features, not to fail.
         */
        fun from(centreline: Centreline?): TrackStation? {
            val points = centreline?.points ?: return null
            if (points.size < MIN_POINTS) return null

            val refLat = points[0].latitude
            val refLng = points[0].longitude

            // The centreline is a closed loop whose last point joins back to the first.
            // The closing segment is appended explicitly so that a fix just before the
            // start/finish projects onto real track rather than onto the nearest end.
            val closed = points + points[0]

            val n = closed.size
            val xs = DoubleArray(n)
            val ys = DoubleArray(n)
            for (i in 0 until n) {
                val (x, y) = GeoUtils.toLocalMetres(
                    closed[i].latitude, closed[i].longitude, refLat, refLng
                )
                xs[i] = x
                ys[i] = y
            }

            val cumulative = DoubleArray(n)
            for (i in 1 until n) {
                cumulative[i] = cumulative[i - 1] + GeoUtils.haversineDistance(
                    closed[i - 1].latitude, closed[i - 1].longitude,
                    closed[i].latitude, closed[i].longitude
                )
            }

            val length = cumulative[n - 1]
            if (length <= 0.0) return null

            return TrackStation(xs, ys, cumulative, refLat, refLng, length)
        }
    }
}
