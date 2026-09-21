package com.drivingcoach.data.track

import com.drivingcoach.lap.LocalLapDetector

/**
 * A circuit the app knows about, either shipped with it or saved by the driver.
 *
 * A track is not a second way of detecting laps. It is the same detection, better
 * informed: everything here is fed to [LocalLapDetector] as optional input, and a
 * session driven without one behaves exactly as it always has.
 */
data class Track(
    val id: String,
    val name: String,
    val location: String? = null,
    val startLine: TrackStartLine,
    /**
     * Direction the circuit is driven through the start/finish, in degrees.
     *
     * At Baltar this is load-bearing rather than decorative. The nearest other part
     * of that circuit passes 24.7 m from the start/finish against a 15 m detection
     * half width, on a device reporting +/-6 m, so the heading guard is the margin
     * between twelve laps and a double count.
     */
    val travelHeadingDeg: Double? = null,
    val lengthM: Int? = null,
    val cornerCount: Int? = null,
    val fastestLapMs: Long? = null,
    val slowestLapMs: Long? = null,
    val centreline: Centreline? = null,
    /** True for circuits shipped with the app, which the driver may hide but not delete. */
    val isBundled: Boolean = false
) {
    /** What this circuit tells the detector before it reads the telemetry. */
    fun priors(): LocalLapDetector.TrackPriors = LocalLapDetector.TrackPriors(
        travelHeadingDeg = travelHeadingDeg,
        fastestLapMs = fastestLapMs,
        slowestLapMs = slowestLapMs,
        lengthM = lengthM
    )

    fun detectorStartLine(): LocalLapDetector.StartLine = LocalLapDetector.StartLine(
        lat1 = startLine.lat1,
        lng1 = startLine.lng1,
        lat2 = startLine.lat2,
        lng2 = startLine.lng2
    )

    /** Distance between the two points defining the start/finish, in metres. */
    fun startLineLengthM(): Double = startLine.lengthM()
}

/**
 * The two points defining a start/finish line, and where they came from.
 *
 * Provenance is recorded per dataset rather than per track because the two can
 * differ: Baltar's line was read off mapping imagery while its centreline was
 * walked. Writing one blanket claim over both would be wrong about one of them.
 */
data class TrackStartLine(
    val lat1: Double,
    val lng1: Double,
    val lat2: Double,
    val lng2: Double,
    val source: GeometrySource = GeometrySource.CAPTURED_IN_APP,
    val recordedAt: String? = null
) {
    fun lengthM(): Double =
        com.drivingcoach.util.GeoUtils.haversineDistance(lat1, lng1, lat2, lng2)

    fun midpoint(): Pair<Double, Double> = Pair((lat1 + lat2) / 2, (lng1 + lng2) / 2)
}

/**
 * The line through the middle of the track, ordered in the direction of travel and
 * starting at the start/finish. The last point joins back to the first.
 *
 * Not used for lap detection - the crossing geometry needs a point and a direction,
 * both of which the start line already supplies. Its value is everything measured
 * *along* the circuit: which samples belong to the track at all, and, later,
 * position-based sectors and corner identity.
 */
data class Centreline(
    val source: GeometrySource,
    val method: String? = null,
    val surveyedAt: String? = null,
    val surveyedBy: String? = null,
    /** Latitude/longitude pairs, closed loop, first point at the start/finish. */
    val points: List<TrackPoint>
) {
    val size: Int get() = points.size
}

/** A single latitude/longitude on a circuit. */
data class TrackPoint(val latitude: Double, val longitude: Double)

/** Where a piece of track geometry came from. Recorded, never assumed. */
enum class GeometrySource {
    /** Captured by the driver on the Track Setup screen. */
    CAPTURED_IN_APP,

    /** Read off mapping imagery. */
    MAP_COORDINATES,

    /** Walked, with waypoints placed by hand. */
    SURVEYED_ON_FOOT,

    /** Derived from telemetry recorded on the circuit. */
    DERIVED_FROM_LAPS
}
