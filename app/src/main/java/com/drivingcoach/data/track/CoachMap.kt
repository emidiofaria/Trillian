package com.drivingcoach.data.track

import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.util.GeoUtils
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The circuit drawing shown on the Coach tab, together with where its sector
 * boundaries fall.
 *
 * Two shapes can be drawn and they are not equally good:
 *
 * - A **surveyed centreline** (TL-01) is the real circuit, free of GPS error. When the
 *   session was driven on a catalogue track this is always the better picture.
 * - A **shape derived from the driver's own laps** ([SessionOutline]) is all that exists
 *   for a circuit the driver set up themselves, which is most of them. It carries the
 *   driver's line and their GPS error, so it is drawn with a caveat.
 *
 * ### Why the boundaries are projected rather than recomputed
 *
 * Sector times are thirds of the *driven* distance, because that is the only definition
 * that works without a centreline (see [com.drivingcoach.lap.SectorSplitter]). Thirds of
 * the *centreline's* length are a slightly different place - a lap taking a wide line
 * covers more ground, so its third falls fractionally earlier on the circuit.
 *
 * Drawing centreline-thirds next to driven-thirds times would mean the picture and the
 * numbers describe different stretches of road, and the driver would have no way of
 * knowing. So the boundary is taken from the driving and *projected* onto the centreline
 * with [TrackStation]. What is shown is then what was measured, which is the whole point
 * of showing it.
 */
object CoachMap {

    /** Where the drawn shape came from. Recorded, never assumed. */
    enum class Provenance {
        /** A surveyed centreline from the bundled catalogue. The real circuit. */
        SURVEYED_CENTRELINE,

        /** The median of the driver's own laps. Good, but it is their line. */
        DERIVED_FROM_LAPS,

        /** One lap only - too few to median. Carries that lap's GPS error. */
        SINGLE_LAP
    }

    /**
     * A drawable circuit.
     *
     * @param points closed loop, normalised to 0..1, y already screen-down.
     * @param firstBoundary index at which sector 1 becomes sector 2.
     * @param secondBoundary index at which sector 2 becomes sector 3.
     * @param spreadM lap-to-lap scatter in metres; 0 when a surveyed centreline is drawn.
     */
    data class Drawing(
        val points: List<SessionOutline.Point>,
        val firstBoundary: Int,
        val secondBoundary: Int,
        val provenance: Provenance,
        val spreadM: Double,
        val lapsUsed: Int
    )

    /**
     * Widest lap-to-lap scatter that still makes a recognisable picture, in metres.
     *
     * Measured spreads on the recorded sessions were 0.81 m (S. Mamede), 2.77 m (Cabo do
     * Mundo) and 3.85 m (Baltar). An outline whose laps disagree by more than this is not
     * a circuit the driver would recognise, and a shape they cannot recognise is worse
     * than no shape: it invites them to read corners into noise.
     */
    const val MAX_SPREAD_M = 25.0

    /**
     * Furthest the driven laps may sit from a centreline before it is treated as
     * belonging to a different circuit, in metres.
     *
     * The measured offsets on the shipped circuits are 2.8 m (Baltar), 6.4 m (Cabo do
     * Mundo) and 2.7 m (S. Mamede) - the width of a racing line away from the middle of
     * the road, as expected. A session tagged with the wrong track projects hundreds or
     * thousands of metres away, so this threshold has a very wide margin on both sides.
     */
    const val MAX_CENTRELINE_OFFSET_M = 60.0

    /**
     * Builds the drawing for a session.
     *
     * @param centreline the catalogue centreline, when the session was driven on a
     *   catalogue track. Null for a circuit the driver captured themselves.
     * @return the drawing, or null when there is nothing honest to show.
     */
    fun build(
        samples: List<TelemetrySample>,
        laps: List<SessionOutline.LapWindow>,
        centreline: Centreline? = null
    ): Drawing? {
        val outline = SessionOutline.build(samples, laps) ?: return null

        // A shape built from laps that do not agree with each other is refused outright.
        // The surveyed centreline has no such problem, which is why the check sits here
        // and not after the centreline branch.
        if (!outline.isSingleLap && outline.spreadM > MAX_SPREAD_M) return null

        val surveyed = fromCentreline(centreline, outline)
        if (surveyed != null) return surveyed

        return Drawing(
            points = outline.points,
            firstBoundary = outline.firstBoundary,
            secondBoundary = outline.secondBoundary,
            provenance = if (outline.isSingleLap) Provenance.SINGLE_LAP else Provenance.DERIVED_FROM_LAPS,
            spreadM = outline.spreadM,
            lapsUsed = outline.lapsUsed
        )
    }

    /**
     * Draws the surveyed centreline, with the driver's sector boundaries projected onto
     * it. Returns null when there is no usable centreline, which is the ordinary case.
     *
     * ### Why the centreline is rotated
     *
     * A catalogue centreline's first point is not necessarily the start/finish line -
     * on the shipped circuits the driven laps begin 400 m into Baltar's centreline and
     * 771 m into Cabo do Mundo's. Sector 1, however, begins at the start/finish by
     * definition, because that is where a lap window opens. Drawing the centreline from
     * its own first point would therefore put sector 1 somewhere in the middle of the
     * circuit, and the start/finish marker in the wrong place entirely.
     *
     * Rotating so that index 0 is the start/finish gives both shapes - surveyed and
     * derived - the same convention: index 0 is the line, and thirds of the index range
     * are thirds of the lap.
     */
    private fun fromCentreline(centreline: Centreline?, outline: SessionOutline.Outline): Drawing? {
        val station = TrackStation.from(centreline) ?: return null
        val points = centreline?.points ?: return null

        // Where the start/finish sits on the centreline, and how far the laps are from
        // the centreline at all. A session tagged with the wrong circuit projects onto a
        // line it was never driven on, and the giveaway is the lateral distance, not the
        // order of the boundaries - which wraps legitimately whenever the start/finish
        // falls late in the centreline's own numbering.
        val start = station.project(outline.startAt.first, outline.startAt.second)
        if (!agrees(station, outline)) return null

        val resampled = resampleCentreline(points, station, start.s) ?: return null

        return Drawing(
            points = resampled,
            firstBoundary = boundaryIndex(station, outline.firstBoundaryAt, start.s),
            secondBoundary = boundaryIndex(station, outline.secondBoundaryAt, start.s),
            provenance = Provenance.SURVEYED_CENTRELINE,
            spreadM = 0.0,
            lapsUsed = outline.lapsUsed
        )
    }

    /**
     * Whether the laps were actually driven on this centreline.
     *
     * Uses the median lateral distance over the whole outline, so neither one stray
     * point nor one corner the survey cut differently decides it.
     */
    private fun agrees(station: TrackStation, outline: SessionOutline.Outline): Boolean {
        val offsets = listOf(outline.startAt, outline.firstBoundaryAt, outline.secondBoundaryAt)
            .map { station.project(it.first, it.second).lateralDistanceM }
            .sorted()
        return offsets[offsets.size / 2] <= MAX_CENTRELINE_OFFSET_M
    }

    /**
     * Where a driven boundary falls on the drawing, measured forwards from the
     * start/finish so that index 0 is the line.
     */
    private fun boundaryIndex(station: TrackStation, at: Pair<Double, Double>, originS: Double): Int {
        val s = station.project(at.first, at.second).s
        val fromStart = station.forwardDistance(originS, s)
        val index = (fromStart / station.lengthM * SessionOutline.POINTS).roundToInt()
        return index.coerceIn(1, SessionOutline.POINTS - 1)
    }

    /**
     * Resamples the centreline to the same point count as a derived outline, evenly in
     * distance and starting at the start/finish, so that an index means the same
     * fraction of the way round whichever shape is being drawn.
     */
    private fun resampleCentreline(
        points: List<TrackPoint>,
        station: TrackStation,
        originS: Double
    ): List<SessionOutline.Point>? {
        val closed = points + points[0]

        val cumulative = DoubleArray(closed.size)
        for (i in 1 until closed.size) {
            cumulative[i] = cumulative[i - 1] + GeoUtils.haversineDistance(
                closed[i - 1].latitude, closed[i - 1].longitude,
                closed[i].latitude, closed[i].longitude
            )
        }
        val total = cumulative[closed.size - 1]
        if (total < SessionOutline.MIN_LAP_DISTANCE_M) return null

        val refLat = closed[0].latitude
        val refLng = closed[0].longitude

        val local = ArrayList<Pair<Double, Double>>(SessionOutline.POINTS)
        for (i in 0 until SessionOutline.POINTS) {
            // Offset by the start/finish and wrap, so point 0 of the drawing is the line.
            val target = (originS + total * i / SessionOutline.POINTS) % total

            var cursor = 1
            while (cursor < closed.size - 1 && cumulative[cursor] < target) cursor++

            val prev = closed[cursor - 1]
            val next = closed[cursor]
            val spanM = cumulative[cursor] - cumulative[cursor - 1]
            val f = if (spanM <= 0.0) 0.0 else ((target - cumulative[cursor - 1]) / spanM).coerceIn(0.0, 1.0)

            local.add(
                GeoUtils.toLocalMetres(
                    prev.latitude + (next.latitude - prev.latitude) * f,
                    prev.longitude + (next.longitude - prev.longitude) * f,
                    refLat, refLng
                )
            )
        }

        return normalise(local)
    }

    /** Scales into 0..1 by the larger dimension, so the circuit is never stretched. */
    private fun normalise(local: List<Pair<Double, Double>>): List<SessionOutline.Point>? {
        val minX = local.minOf { it.first }
        val maxX = local.maxOf { it.first }
        val minY = local.minOf { it.second }
        val maxY = local.maxOf { it.second }

        val width = maxX - minX
        val height = maxY - minY
        val extent = max(width, height)
        if (extent < SessionOutline.MIN_EXTENT_M) return null

        val padX = (extent - width) / 2.0
        val padY = (extent - height) / 2.0

        return local.map { (x, y) ->
            SessionOutline.Point(
                x = (((x - minX) + padX) / extent).toFloat(),
                y = (1.0 - (((y - minY) + padY) / extent)).toFloat()
            )
        }
    }
}
