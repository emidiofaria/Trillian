package com.drivingcoach.data.track

import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.lap.SectorSplitter
import com.drivingcoach.util.GeoUtils
import kotlin.math.max

/**
 * The shape of the circuit as the driver actually drove it, drawn in the same
 * normalised-distance space the sectors live in.
 *
 * This exists so the Coach tab can point at a place. "Sector 2 is costing you the
 * most" is a sentence about a stretch of road, and until there is a picture the
 * driver has to translate it into a memory of tarmac themselves.
 *
 * ### Why a median across laps rather than one lap
 *
 * A single lap's trace carries that lap's GPS error, and at roughly 1 Hz with a
 * phone that error is metres. Drawn alone it wobbles. The useful property, measured
 * on the recorded sessions, is that most of the error is a *session-wide bias* -
 * the whole trace is shifted, not scrambled - so the lap-to-lap scatter about the
 * driver's own line is small: 3.85 m at Baltar, 2.77 m at Cabo do Mundo, 0.81 m at
 * S. Mamede. Scatter that is random lap to lap averages down with more laps.
 *
 * The per-fraction *median* is used rather than the mean for the same reason the
 * outlier insight uses one: a single wild lap - a spin, a trip through the gravel,
 * a fix lost under a bridge - drags a mean towards itself and leaves a bulge in the
 * drawn outline that the driver never drove. The median ignores it.
 *
 * ### Why normalised distance is the right axis
 *
 * Each lap is resampled at the same fractions of *its own* driven distance, which
 * is exactly how [com.drivingcoach.lap.SectorSplitter] defines a sector. Index
 * [POINTS] / 3 therefore *is* the first sector boundary, by construction, and not
 * an approximation of it. Averaging in any other space - time, or raw sample index -
 * would mix a fast lap's corner with a slow lap's straight and blur the shape.
 *
 * This does mean the outline is the driver's *line*, not the circuit's centreline.
 * Where a surveyed centreline exists it is the better thing to draw, and the caller
 * is expected to prefer it; this is what makes a map possible on a circuit the
 * driver captured themselves, which is most of them.
 */
object SessionOutline {

    /**
     * How many points the outline is resampled to.
     *
     * On a 2.5 km circuit this is a point roughly every 10 m, which is finer than
     * the GPS that produced it and smooth enough to draw. It is divisible by three,
     * so the sector boundaries fall exactly on a point rather than between two.
     */
    const val POINTS = 240

    /**
     * Fewest laps before a median says anything.
     *
     * With two laps a "median" is just their midpoint, which is a mean, which is
     * the thing being avoided. Below this the outline falls back to a single lap
     * and says so, so the UI can caveat it rather than present a noisy trace as if
     * it were the circuit.
     */
    const val MIN_LAPS_FOR_MEDIAN = 3

    /**
     * Shortest lap worth resampling, in metres of ground covered.
     *
     * Deliberately the same threshold [SectorSplitter] uses to decide whether a lap
     * can be divided at all. A lap that earned sector times must be drawable, or the
     * Coach tab would state three sector times and then refuse to show where they
     * are - which is precisely the gap this outline exists to close.
     */
    const val MIN_LAP_DISTANCE_M = SectorSplitter.MIN_LAP_DISTANCE_M

    /**
     * Smallest bounding box worth drawing, in metres across.
     *
     * Distinct from [MIN_LAP_DISTANCE_M], which measures ground covered: a lap can
     * drive 400 m round a box 60 m across, and the two numbers answer different
     * questions. This one asks whether there is a shape, rather than a dot, to put
     * on the screen.
     */
    const val MIN_EXTENT_M = 25.0

    /** One point of the outline, normalised to 0..1 with y already screen-down. */
    data class Point(val x: Float, val y: Float)

    /**
     * A drawable outline.
     *
     * @param points [POINTS] positions, evenly spaced in driven distance, closed loop.
     * @param lapsUsed how many laps contributed. 1 means [isSingleLap].
     * @param spreadM the typical lap-to-lap scatter about the outline, in metres.
     *   This is the honest measure of how much to trust the shape.
     * @param firstBoundaryAt the latitude/longitude where sector 1 becomes sector 2.
     * @param secondBoundaryAt the latitude/longitude where sector 2 becomes sector 3.
     * @param startAt the latitude/longitude of index 0 - the start/finish line, since
     *   every lap window begins at a crossing of it.
     *
     * The three positions are carried in degrees, not as indices, because the caller may
     * be drawing a *surveyed centreline* instead of this outline. A boundary is defined
     * by the driving, so it has to be expressed somewhere both shapes can agree on, and
     * then projected onto whichever one is drawn.
     */
    data class Outline(
        val points: List<Point>,
        val lapsUsed: Int,
        val spreadM: Double,
        val firstBoundaryAt: Pair<Double, Double>,
        val secondBoundaryAt: Pair<Double, Double>,
        val startAt: Pair<Double, Double>
    ) {
        val isSingleLap: Boolean get() = lapsUsed < MIN_LAPS_FOR_MEDIAN

        /** Index where sector 1 ends and sector 2 begins. Exact, by construction. */
        val firstBoundary: Int get() = points.size / 3

        /** Index where sector 2 ends and sector 3 begins. Exact, by construction. */
        val secondBoundary: Int get() = points.size * 2 / 3
    }

    /** One lap's window, as lap detection decided it. */
    data class LapWindow(val startTs: Long, val endTs: Long)

    /**
     * Builds the outline from whole-session telemetry and the accepted lap windows.
     *
     * @return the outline, or null when there is not enough to draw honestly.
     */
    fun build(samples: List<TelemetrySample>, laps: List<LapWindow>): Outline? {
        if (samples.isEmpty() || laps.isEmpty()) return null

        // Resample every lap onto the same distance fractions. A lap that cannot be
        // resampled - too few fixes, or too little ground covered - is dropped
        // rather than padded, so it cannot pull the median towards a shape it never
        // described.
        val resampled = laps.mapNotNull { resample(samples, it) }
        if (resampled.isEmpty()) return null

        // Every lap is projected about one shared reference so the positions are
        // directly comparable. Using each lap's own first fix would re-centre every
        // lap on itself and quietly remove the very differences being averaged.
        val refLat = samples.first().latitude
        val refLng = samples.first().longitude

        val local = resampled.map { lap ->
            lap.map { (lat, lng) -> GeoUtils.toLocalMetres(lat, lng, refLat, refLng) }
        }

        val merged = ArrayList<Pair<Double, Double>>(POINTS)
        for (i in 0 until POINTS) {
            merged.add(median(local.map { it[i].first }) to median(local.map { it[i].second }))
        }

        val spread = if (local.size >= MIN_LAPS_FOR_MEDIAN) spreadMetres(local, merged) else 0.0

        // The boundaries are read off the merged shape in metres and converted back to
        // degrees, so they describe a place on the planet rather than a place on this
        // particular drawing. A caller drawing a surveyed centreline can then ask where
        // that place falls on *its* shape.
        val firstAt = GeoUtils.fromLocalMetres(
            merged[POINTS / 3].first, merged[POINTS / 3].second, refLat, refLng
        )
        val secondAt = GeoUtils.fromLocalMetres(
            merged[POINTS * 2 / 3].first, merged[POINTS * 2 / 3].second, refLat, refLng
        )
        val startAt = GeoUtils.fromLocalMetres(merged[0].first, merged[0].second, refLat, refLng)

        return normalise(merged, local.size, spread, firstAt, secondAt, startAt)
    }

    /**
     * One lap resampled to [POINTS] positions at equal fractions of its own driven
     * distance, as latitude/longitude.
     *
     * The position at each fraction is interpolated between the two fixes either
     * side of it, for the same reason the sector boundary instant is: snapping to
     * the nearest fix at ~1 Hz would quantise the shape to wherever the car happened
     * to be sampled, which at 40 m/s is a 40 m step.
     */
    private fun resample(samples: List<TelemetrySample>, lap: LapWindow): List<Pair<Double, Double>>? {
        if (lap.endTs <= lap.startTs) return null

        val window = samples.filter { it.timestampMs in lap.startTs..lap.endTs }
        if (window.size < MIN_SAMPLES_PER_LAP) return null

        val cumulative = DoubleArray(window.size)
        for (i in 1 until window.size) {
            cumulative[i] = cumulative[i - 1] + GeoUtils.haversineDistance(
                window[i - 1].latitude, window[i - 1].longitude,
                window[i].latitude, window[i].longitude
            )
        }

        val total = cumulative[window.size - 1]
        if (total < MIN_LAP_DISTANCE_M) return null

        val out = ArrayList<Pair<Double, Double>>(POINTS)
        var cursor = 1
        for (i in 0 until POINTS) {
            val target = total * i / POINTS
            while (cursor < window.size - 1 && cumulative[cursor] < target) cursor++

            val prev = window[cursor - 1]
            val next = window[cursor]
            val spanM = cumulative[cursor] - cumulative[cursor - 1]

            val f = if (spanM <= 0.0) 0.0 else ((target - cumulative[cursor - 1]) / spanM).coerceIn(0.0, 1.0)
            out.add(
                prev.latitude + (next.latitude - prev.latitude) * f to
                    prev.longitude + (next.longitude - prev.longitude) * f
            )
        }
        return out
    }

    /**
     * The typical distance between an individual lap and the merged outline.
     *
     * Reported so the caller can refuse to draw a shape built from traces that do
     * not agree with each other. It is the median over every lap and every point,
     * so neither one bad lap nor one bad corner decides it.
     */
    private fun spreadMetres(
        laps: List<List<Pair<Double, Double>>>,
        merged: List<Pair<Double, Double>>
    ): Double {
        val deviations = ArrayList<Double>(laps.size * POINTS)
        for (lap in laps) {
            for (i in 0 until POINTS) {
                val dx = lap[i].first - merged[i].first
                val dy = lap[i].second - merged[i].second
                deviations.add(Math.hypot(dx, dy))
            }
        }
        return median(deviations)
    }

    /**
     * Scales the outline into 0..1, by the larger dimension so the shape is never
     * stretched, and flips y because screen y grows downwards while north does not.
     */
    private fun normalise(
        local: List<Pair<Double, Double>>,
        lapsUsed: Int,
        spreadM: Double,
        firstAt: Pair<Double, Double>,
        secondAt: Pair<Double, Double>,
        startAt: Pair<Double, Double>
    ): Outline? {
        val minX = local.minOf { it.first }
        val maxX = local.maxOf { it.first }
        val minY = local.minOf { it.second }
        val maxY = local.maxOf { it.second }

        val width = maxX - minX
        val height = maxY - minY
        val extent = max(width, height)
        if (extent < MIN_EXTENT_M) return null

        val padX = (extent - width) / 2.0
        val padY = (extent - height) / 2.0

        val points = local.map { (x, y) ->
            Point(
                x = (((x - minX) + padX) / extent).toFloat(),
                y = (1.0 - (((y - minY) + padY) / extent)).toFloat()
            )
        }
        return Outline(points, lapsUsed, spreadM, firstAt, secondAt, startAt)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    /** Matches [SectorSplitter.MIN_SAMPLES_PER_LAP]. */
    private const val MIN_SAMPLES_PER_LAP = SectorSplitter.MIN_SAMPLES_PER_LAP
}
