package com.drivingcoach.lap

import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.util.GeoUtils

/**
 * Splits a lap into three sectors at one third and two thirds of the distance the
 * car actually drove during that lap.
 *
 * ### Why distance and not time
 *
 * Thirds of the lap's *duration* would move with the driver: a lap in which the
 * driver lost a second in one corner would place its boundaries somewhere else
 * than a clean lap, and the sector times of the two laps would no longer describe
 * the same stretches of road. Comparing them - which is the entire purpose - would
 * then be measuring the misalignment rather than the driving. Distance does not
 * have that property: a third of the way round is a third of the way round however
 * long it took to get there.
 *
 * ### Why the lap's own distance and not the circuit's
 *
 * A surveyed centreline would give fixed boundaries, identical on every lap of
 * every session, and that is the better answer. It is also only available for a
 * circuit in the bundled catalogue (TL-01), and the driver who captured their own
 * start/finish line would get no sectors at all. Measuring each lap against itself
 * works everywhere.
 *
 * The cost is that a lap driven on a different line has slightly different boundary
 * positions - a lap that runs wide covers more ground, so its third falls fractionally
 * earlier on the circuit. On the recorded sessions this is a fraction of a percent of
 * a sector, far below the lap-to-lap differences the sectors exist to show. It is
 * nonetheless a real approximation, which is why the UI states that these are the
 * app's sectors and not the circuit's official ones.
 *
 * ### Why the boundary instant is interpolated
 *
 * The reference device delivers roughly 1 Hz. Taking the timestamp of the nearest
 * sample would quantise every sector boundary to a whole second - on a 25 s sector
 * that is a 4% error, far larger than the differences being measured. The crossing
 * instant is interpolated between the two samples either side of the boundary, for
 * the same reason and by the same reasoning as NF-16 does for lap boundaries.
 *
 * ### Why the ruler starts at the line and not at the first fix
 *
 * The clock starts the instant the car crossed the start/finish plane. The ruler
 * used to start at the first fix *after* it, which at 1 Hz is fifteen to twenty
 * metres further on. The lap's measured distance was therefore short by that
 * stretch at the start and by another at the end, while its duration was not -
 * so every boundary landed late on the circuit by a drifting amount, and the
 * drift was different on every lap because the GPS clock's phase against the
 * crossing is arbitrary. Sector 1 carried roughly half a second of pure artefact
 * lap to lap, which matters most to the dream lap, since it takes the *fastest*
 * sector and so preferentially selects whichever lap's artefact flattered it.
 *
 * Both ends of the lap are now anchored at the interpolated crossing position
 * recovered by [LapAnchor], so the ruler and the clock measure the same stretch
 * of road (LD-27).
 *
 * Sector times therefore always sum to exactly the lap duration.
 */
object SectorSplitter {

    /**
 * Fewest samples a lap must contain before it can be divided into three.
 *
 * Three sectors need at least one interior sample each plus the two endpoints.
 * Below this the boundaries would be decided by interpolation alone, and a
 * "sector time" that is a straight-line guess between two distant fixes is not
 * a measurement. Such a lap gets no sectors rather than invented ones.
 *
 * This counts *recorded* fixes only. The two anchor points at the start/finish
 * line are interpolated rather than measured, so counting them would quietly
 * admit a four-fix lap past a floor that was written to demand six (LD-28).
 */
    const val MIN_SAMPLES_PER_LAP = 6

    /**
     * Shortest lap distance that can be split, in metres.
     *
     * A lap over a shorter distance than this is not a lap of anything - it is GPS
     * scatter recorded between two crossings that should not both have been accepted.
     * Splitting it would produce three sector times that describe noise.
     */
    const val MIN_LAP_DISTANCE_M = 50.0

    /** The three sector durations of one lap, in milliseconds. They sum to the lap. */
    data class Sectors(
        val sector1Ms: Long,
        val sector2Ms: Long,
        val sector3Ms: Long
    ) {
        val totalMs: Long get() = sector1Ms + sector2Ms + sector3Ms
    }

    /**
     * Derives the three sector times for a single lap.
     *
     * @param samples the whole session, sorted by timestamp. Those falling inside the
     *   lap window are used, plus the two fixes bracketing each boundary instant, from
     *   which the crossing positions are recovered.
     * @param startTs the lap's start instant, as decided by lap detection
     * @param endTs the lap's end instant, as decided by lap detection
     * @return the three sector times, or null when the lap carries too little data
     *   to be divided honestly.
     */
    fun split(samples: List<TelemetrySample>, startTs: Long, endTs: Long): Sectors? {
        if (endTs <= startTs) return null

        val recorded = samples.filter { it.timestampMs in startTs..endTs }
        if (recorded.size < MIN_SAMPLES_PER_LAP) return null

        // Where the car actually was when the clock started and stopped. A null here
        // means the boundary instant falls outside the telemetry - a truncated file,
        // not a normal lap - and a ruler anchored on a guess is worse than no sectors.
        val startPos = LapAnchor.positionAt(samples, startTs) ?: return null
        val endPos = LapAnchor.positionAt(samples, endTs) ?: return null

        val lap = ArrayList<Point>(recorded.size + 2)
        // A fix landing exactly on the boundary already *is* the anchor; adding it
        // again would insert a zero-length span for the interpolator to divide by.
        if (recorded.first().timestampMs != startTs) {
            lap.add(Point(startPos.latitude, startPos.longitude, startTs))
        }
        recorded.forEach { lap.add(Point(it.latitude, it.longitude, it.timestampMs)) }
        if (recorded.last().timestampMs != endTs) {
            lap.add(Point(endPos.latitude, endPos.longitude, endTs))
        }

        // Cumulative distance along the lap, one entry per point, zeroed at the line.
        val cumulative = DoubleArray(lap.size)
        for (i in 1 until lap.size) {
            cumulative[i] = cumulative[i - 1] + GeoUtils.haversineDistance(
                lap[i - 1].latitude, lap[i - 1].longitude,
                lap[i].latitude, lap[i].longitude
            )
        }

        val total = cumulative[lap.size - 1]
        if (total < MIN_LAP_DISTANCE_M) return null

        val firstBoundary = timeAtDistance(lap, cumulative, total / 3.0)
        val secondBoundary = timeAtDistance(lap, cumulative, total * 2.0 / 3.0)
        if (firstBoundary == null || secondBoundary == null) return null

        // The boundaries must fall strictly inside the lap and in order, or the
        // "sectors" are not three distinct stretches of road.
        if (firstBoundary <= startTs || secondBoundary >= endTs) return null
        if (secondBoundary <= firstBoundary) return null

        // The third sector takes the remainder so that the three always sum to the
        // lap exactly, rather than to the lap plus or minus a rounding error.
        val sector1 = firstBoundary - startTs
        val sector2 = secondBoundary - firstBoundary
        val sector3 = (endTs - startTs) - sector1 - sector2

        return Sectors(sector1, sector2, sector3)
    }

    /** One point along the lap: a recorded fix, or an interpolated line crossing. */
    private data class Point(
        val latitude: Double,
        val longitude: Double,
        val timestampMs: Long
    )

    /**
     * The instant at which the car had travelled [target] metres into the lap,
     * interpolated between the two points either side of that point.
     */
    private fun timeAtDistance(
        lap: List<Point>,
        cumulative: DoubleArray,
        target: Double
    ): Long? {
        for (i in 1 until lap.size) {
            if (cumulative[i] < target) continue

            val spanM = cumulative[i] - cumulative[i - 1]
            val spanMs = lap[i].timestampMs - lap[i - 1].timestampMs

            // Two fixes at the same place, or out of order: nothing to interpolate
            // along, so take the later sample rather than divide by zero.
            if (spanM <= 0.0 || spanMs <= 0L) return lap[i].timestampMs

            val fraction = ((target - cumulative[i - 1]) / spanM).coerceIn(0.0, 1.0)
            return lap[i - 1].timestampMs + Math.round(fraction * spanMs)
        }
        return null
    }
}
