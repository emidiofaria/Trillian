package com.drivingcoach.ui.session.tabs.analysis

import com.drivingcoach.data.telemetry.TelemetryFileReader
import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.util.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns a recorded telemetry file into the report shown on the ANALYSIS tab:
 * session statistics, a drawable track outline, corners, braking zones and a
 * session-wide speed trace.
 *
 * Everything here runs offline from the local JSONL file and is a pure function
 * of its inputs, so it is unit-testable without a device (see
 * `SessionAnalysisProcessorTest`).
 *
 * ## Why every threshold is expressed per second
 *
 * The obvious way to detect a corner is "the bearing changed by more than N
 * degrees between two samples", and the obvious way to detect braking is "speed
 * dropped by more than M between two samples". Both are wrong here, because the
 * answer then depends on how fast the phone happened to be sampling. Recording
 * runs at 10 Hz, while exported reference sessions are 1 Hz; a per-sample
 * threshold tuned on one produces either no corners at all or a corner every few
 * metres on the other. All thresholds below are therefore rates (deg/s, m/s²)
 * and all windows are durations, measured against the file's own sample
 * interval. `SessionAnalysisRateInvarianceTest` locks this property down.
 *
 * ## Why lap boundaries are not computed here
 *
 * Laps come from [com.drivingcoach.lap.LocalLapDetector] via the database. This
 * class only ever consumes them. Two independent lap truths in one app would be
 * a defect waiting to happen.
 */
object SessionAnalysisProcessor {

    // --- Corner detection -------------------------------------------------

    /** Bearing is measured across this much travel time, not sample to sample. */
    private const val BEARING_WINDOW_S = 1.0

    /**
     * Yaw rate is averaged over this window to suppress GPS jitter. Wide enough
     * to span roughly a second either side of each point at any sample rate.
     */
    private const val YAW_SMOOTHING_S = 3.0

    /** A sustained yaw rate above this counts as cornering. */
    private const val YAW_RATE_THRESHOLD_DPS = 6.0

    /** Shorter deviations than this are noise, not corners. */
    private const val MIN_CORNER_DURATION_S = 1.5

    /**
     * A bearing is only taken when the car actually moved this far across the
     * window. A stationary car still reports a scatter of positions, and the
     * bearing between two of them is noise pointing in an arbitrary direction.
     */
    private const val MIN_BEARING_TRAVEL_M = 2.0

    /**
     * A corner must contain at least this speed somewhere. Without it, sitting in
     * the pits or shuffling around the paddock registers as a sequence of violent
     * corners: the position scatter of a parked phone wanders through hundreds of
     * degrees of apparent heading in a few seconds, which is a much higher yaw
     * rate than any real corner produces.
     */
    private const val MIN_CORNERING_SPEED_KMH = 10.0

    // --- Braking detection ------------------------------------------------

    /** Speed is averaged over this window before differentiating. */
    private const val SPEED_SMOOTHING_S = 0.6

    /** Deceleration is measured across this much travel time. */
    private const val DECEL_WINDOW_S = 0.6

    /** Deceleration beyond this (m/s²) counts as braking. */
    private const val DECEL_THRESHOLD_MS2 = -0.8

    /** Braking segments separated by less than this are one braking event. */
    private const val BRAKING_GAP_MERGE_S = 1.0

    /** A braking zone must last at least this long. */
    private const val MIN_BRAKING_DURATION_S = 0.5

    /** A braking zone must shed at least this much speed. */
    private const val MIN_SPEED_DROP_KMH = 4.0

    /**
     * A zone is attributed to a corner whose apex is no earlier than this
     * relative to the end of braking (a small negative tolerance allows for
     * trail braking past the turn-in point).
     */
    private const val CORNER_ASSOCIATION_TOLERANCE_S = -1.0

    private const val GRAVITY_MS2 = 9.81

    // --- Rendering budgets ------------------------------------------------

    /** Maximum points drawn for the track outline. */
    const val TRACK_PATH_POINT_BUDGET = 600

    /** Maximum points plotted on the session speed trace. */
    const val SPEED_TRACE_POINT_BUDGET = 500

    /**
     * A telemetry sample reduced to the fields the analysis needs, with time in
     * milliseconds and speed already in km/h.
     */
    internal data class Point(
        val timestampMs: Long,
        val latitude: Double,
        val longitude: Double,
        val speedKmh: Double
    )

    /** Inclusive index range into the prepared sample list. */
    internal data class Range(val from: Int, val to: Int) {
        val size: Int get() = to - from + 1
    }

    /**
     * Reads [filePath] and produces the full analysis.
     *
     * @param laps laps already detected for this session, in any order.
     * @param referenceLapId lap the corners and braking zones relate to. When
     *   null, or not present in [laps], the best lap is used; when there are no
     *   laps at all the whole session is used instead.
     * @param startLine midpoint of the captured start/finish line, if the
     *   session has one, used to place the start/finish marker.
     */
    suspend fun analyze(
        filePath: String?,
        laps: List<LapOption>,
        referenceLapId: Long?,
        lapWindows: Map<Long, LongRange> = emptyMap(),
        startLine: Pair<Double, Double>? = null
    ): SessionAnalysis {
        val samples = readSamples(filePath)
        return withContext(Dispatchers.Default) { analyzeSamples(samples, laps, referenceLapId, lapWindows, startLine) }
    }

    /**
     * Reads the telemetry file and discards anything that cannot be analysed.
     *
     * [TelemetryFileReader.readAll] deserialises every line into a
     * [TelemetrySample], including the leading `type: "header"` line, which has
     * none of the sample fields and therefore arrives as an all-zero sample.
     * Samples with no timestamp or a null-island position are dropped for the
     * same reason: they are absent data, not a car at 0°N 0°E.
     */
    internal suspend fun readSamples(filePath: String?): List<Point> {
        if (filePath.isNullOrBlank()) return emptyList()
        val raw = TelemetryFileReader.readAll(filePath)
        return prepare(raw)
    }

    /** Converts raw samples into the internal representation, ordered by time. */
    internal fun prepare(raw: List<TelemetrySample>): List<Point> =
        raw.asSequence()
            .filter { it.timestampMs > 0L }
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .map {
                Point(
                    timestampMs = it.timestampMs,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    speedKmh = (it.speedMs * 3.6).toDouble()
                )
            }
            .sortedBy { it.timestampMs }
            .toList()

    /** Analyses already-prepared samples. Exposed for tests. */
    internal fun analyzeSamples(
        samples: List<Point>,
        laps: List<LapOption>,
        referenceLapId: Long?,
        lapWindows: Map<Long, LongRange>,
        startLine: Pair<Double, Double>?
    ): SessionAnalysis {
        if (samples.size < SessionAnalysis.MIN_SAMPLES_FOR_ANALYSIS) return SessionAnalysis.EMPTY

        val dtSeconds = medianIntervalSeconds(samples)
        val sortedLaps = laps.sortedBy { it.lapNumber }

        val reference = resolveReference(sortedLaps, referenceLapId)
        val lapRange = reference?.let { lap ->
            lapWindows[lap.lapId]?.let { window -> rangeForWindow(samples, window) }
        }
        // A lap whose window does not line up with the telemetry - a lap row that
        // outlived its file, or a file truncated by a crash - is not usable as a
        // reference. Fall back to the whole session and say so, rather than
        // labelling whole-session figures as if they were that lap's.
        val range = lapRange ?: Range(0, samples.lastIndex)
        val isWholeSession = lapRange == null

        val corners = detectCorners(samples, range, dtSeconds)
        val brakingZones = detectBrakingZones(samples, range, dtSeconds, corners)

        return SessionAnalysis(
            stats = computeStats(samples, dtSeconds, sortedLaps),
            path = buildTrackPath(samples, range, brakingZones, corners, startLine),
            corners = corners,
            brakingZones = brakingZones,
            speedByTime = buildSpeedTrace(samples),
            lapOptions = sortedLaps,
            referenceLapId = reference?.lapId,
            referenceLapLabel = reference?.takeUnless { isWholeSession }?.let { "Lap ${it.lapNumber}" },
            referenceIsWholeSession = isWholeSession
        )
    }

    private fun resolveReference(laps: List<LapOption>, referenceLapId: Long?): LapOption? {
        if (laps.isEmpty()) return null
        return laps.firstOrNull { it.lapId == referenceLapId }
            ?: laps.firstOrNull { it.isBestLap }
            ?: laps.minByOrNull { it.durationMs }
    }

    /** Maps a lap's timestamp window onto indices in [samples]. */
    internal fun rangeForWindow(samples: List<Point>, window: LongRange): Range? {
        val from = samples.indexOfFirst { it.timestampMs >= window.first }
        if (from < 0) return null
        val to = samples.indexOfLast { it.timestampMs <= window.last }
        if (to <= from) return null
        return Range(from, to)
    }

    /**
     * Median interval between consecutive samples, in seconds.
     *
     * The median rather than the mean because a session can contain gaps - a
     * lost GPS fix, or the writer's 30 s flush landing badly - and one long gap
     * would drag a mean far enough to mis-size every analysis window.
     */
    internal fun medianIntervalSeconds(samples: List<Point>): Double {
        if (samples.size < 2) return 0.0
        val deltas = ArrayList<Double>(samples.size - 1)
        for (i in 1 until samples.size) {
            val dt = (samples[i].timestampMs - samples[i - 1].timestampMs) / 1000.0
            if (dt > 0.0) deltas.add(dt)
        }
        if (deltas.isEmpty()) return 0.0
        deltas.sort()
        return deltas[deltas.size / 2]
    }

    /** Number of samples spanning [seconds], at least 1. */
    private fun windowSamples(seconds: Double, dtSeconds: Double): Int {
        if (dtSeconds <= 0.0) return 1
        return max(1, (seconds / dtSeconds).roundToInt())
    }

    // ------------------------------------------------------------------
    // 1. Session statistics
    // ------------------------------------------------------------------

    internal fun computeStats(
        samples: List<Point>,
        dtSeconds: Double,
        laps: List<LapOption>
    ): SessionStats {
        var distance = 0.0
        var maxSpeed = 0.0
        var speedSum = 0.0

        for (i in samples.indices) {
            val s = samples[i]
            if (i > 0) {
                val prev = samples[i - 1]
                distance += GeoUtils.haversineDistance(prev.latitude, prev.longitude, s.latitude, s.longitude)
            }
            if (s.speedKmh > maxSpeed) maxSpeed = s.speedKmh
            speedSum += s.speedKmh
        }

        val durationMs = samples.last().timestampMs - samples.first().timestampMs
        val bestLapMs = laps.firstOrNull { it.isBestLap }?.durationMs
            ?: laps.minByOrNull { it.durationMs }?.durationMs

        return SessionStats(
            distanceM = distance,
            durationMs = durationMs,
            maxSpeedKmh = maxSpeed.toFloat(),
            avgSpeedKmh = (speedSum / samples.size).toFloat(),
            bestLapMs = bestLapMs,
            sampleCount = samples.size,
            sampleRateHz = if (dtSeconds > 0.0) (1.0 / dtSeconds).toFloat() else 0f
        )
    }

    // ------------------------------------------------------------------
    // 2. Corner detection
    // ------------------------------------------------------------------

    /**
     * Detects corners from the rate of change of the direction of travel.
     *
     * Bearing is taken across a fixed *duration* rather than between adjacent
     * samples: two consecutive 10 Hz fixes taken while crawling through the
     * paddock can be less than a metre apart, and the bearing between two points
     * that close is dominated by GPS error rather than by where the car is
     * pointing - the same reasoning that made the app derive lap crossings from
     * motion rather than from the captured start line (SRS LD-02).
     */
    internal fun detectCorners(samples: List<Point>, range: Range, dtSeconds: Double): List<Corner> {
        if (range.size < 4) return emptyList()

        val bearingWindow = windowSamples(BEARING_WINDOW_S, dtSeconds)
        val centres = ArrayList<Int>()
        val bearings = ArrayList<Double>()

        var i = range.from
        while (i + bearingWindow <= range.to) {
            val a = samples[i]
            val b = samples[i + bearingWindow]
            if (GeoUtils.haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude) >= MIN_BEARING_TRAVEL_M) {
                bearings.add(GeoUtils.bearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude))
                centres.add(i + bearingWindow / 2)
            }
            i++
        }
        if (bearings.size < 3) return emptyList()

        // Signed yaw rate in deg/s. Positive is a right-hand (clockwise) turn.
        val yawRate = DoubleArray(bearings.size - 1)
        val yawDt = DoubleArray(bearings.size - 1)
        for (k in 0 until bearings.size - 1) {
            val dt = (samples[centres[k + 1]].timestampMs - samples[centres[k]].timestampMs) / 1000.0
            val delta = signedBearingDelta(bearings[k], bearings[k + 1])
            yawRate[k] = if (dt > 0.0) delta / dt else 0.0
            yawDt[k] = dt
        }

        val smoothed = movingAverage(yawRate, windowSamples(YAW_SMOOTHING_S, dtSeconds))

        val segments = ArrayList<IntArray>()
        var startK = -1
        for (k in smoothed.indices) {
            val cornering = abs(smoothed[k]) > YAW_RATE_THRESHOLD_DPS
            if (cornering && startK < 0) startK = k
            if (!cornering && startK >= 0) {
                segments.add(intArrayOf(startK, k - 1))
                startK = -1
            }
        }
        if (startK >= 0) segments.add(intArrayOf(startK, smoothed.lastIndex))

        val corners = ArrayList<Corner>()
        for (seg in segments) {
            val firstIdx = centres[seg[0]]
            val lastIdx = centres[min(seg[1] + 1, centres.lastIndex)]
            val durationS = (samples[lastIdx].timestampMs - samples[firstIdx].timestampMs) / 1000.0
            if (durationS < MIN_CORNER_DURATION_S) continue

            var apexIdx = firstIdx
            var maxSpeed = 0.0
            for (idx in firstIdx..lastIdx) {
                if (samples[idx].speedKmh < samples[apexIdx].speedKmh) apexIdx = idx
                if (samples[idx].speedKmh > maxSpeed) maxSpeed = samples[idx].speedKmh
            }
            if (maxSpeed < MIN_CORNERING_SPEED_KMH) continue

            var turnDeg = 0.0
            for (k in seg[0]..seg[1]) turnDeg += smoothed[k] * yawDt[k]

            corners.add(
                Corner(
                    name = "",
                    startMs = samples[firstIdx].timestampMs,
                    endMs = samples[lastIdx].timestampMs,
                    apexMs = samples[apexIdx].timestampMs,
                    apexSpeedKmh = samples[apexIdx].speedKmh.toFloat(),
                    turnDeg = turnDeg.toFloat(),
                    direction = if (turnDeg >= 0.0) TurnDirection.RIGHT else TurnDirection.LEFT
                )
            )
        }

        // Numbered in order of passing, which is not necessarily the circuit's
        // own numbering - the UI says so explicitly.
        return corners.sortedBy { it.apexMs }.mapIndexed { index, corner ->
            corner.copy(name = "T${index + 1}")
        }
    }

    /** Difference between two bearings, normalised to (-180, 180]. */
    internal fun signedBearingDelta(from: Double, to: Double): Double {
        var d = (to - from) % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }

    /** Centred moving average with a window of [windowSamples] samples. */
    internal fun movingAverage(values: DoubleArray, windowSamples: Int): DoubleArray {
        if (values.isEmpty()) return values
        val half = max(0, windowSamples / 2)
        if (half == 0) return values.copyOf()

        val out = DoubleArray(values.size)
        for (i in values.indices) {
            val lo = max(0, i - half)
            val hi = min(values.lastIndex, i + half)
            var sum = 0.0
            for (j in lo..hi) sum += values[j]
            out[i] = sum / (hi - lo + 1)
        }
        return out
    }

    // ------------------------------------------------------------------
    // 3. Braking zones
    // ------------------------------------------------------------------

    /**
     * Detects braking from GPS deceleration.
     *
     * The phone's accelerometer is recorded but deliberately not used: its axes
     * depend on the mounting orientation, which is unknown and uncalibrated, so
     * they show no dependable correlation with real longitudinal acceleration.
     * Change of GPS speed over time is direct, mount-independent, and is what the
     * reported g figure is derived from.
     */
    internal fun detectBrakingZones(
        samples: List<Point>,
        range: Range,
        dtSeconds: Double,
        corners: List<Corner>
    ): List<BrakingZone> {
        if (range.size < 4) return emptyList()

        val speeds = DoubleArray(range.size) { samples[range.from + it].speedKmh }
        val smoothSpeed = movingAverage(speeds, windowSamples(SPEED_SMOOTHING_S, dtSeconds))

        val decelWindow = windowSamples(DECEL_WINDOW_S, dtSeconds)
        if (decelWindow >= range.size) return emptyList()

        val decel = DoubleArray(range.size - decelWindow)
        for (i in decel.indices) {
            val dt = (samples[range.from + i + decelWindow].timestampMs -
                    samples[range.from + i].timestampMs) / 1000.0
            decel[i] = if (dt > 0.0) ((smoothSpeed[i + decelWindow] - smoothSpeed[i]) / 3.6) / dt else 0.0
        }

        // Contiguous runs of "decelerating hard enough", then merged across short
        // gaps so that one braking event with a moment of released pressure is not
        // reported as two.
        val runs = ArrayList<IntArray>()
        var startI = -1
        for (i in decel.indices) {
            val braking = decel[i] < DECEL_THRESHOLD_MS2
            if (braking && startI < 0) startI = i
            if (!braking && startI >= 0) {
                runs.add(intArrayOf(startI, i - 1))
                startI = -1
            }
        }
        if (startI >= 0) runs.add(intArrayOf(startI, decel.lastIndex))

        val merged = ArrayList<IntArray>()
        for (run in runs) {
            val prev = merged.lastOrNull()
            if (prev != null) {
                val gapS = (samples[range.from + run[0]].timestampMs -
                        samples[range.from + prev[1]].timestampMs) / 1000.0
                if (gapS <= BRAKING_GAP_MERGE_S) {
                    prev[1] = run[1]
                    continue
                }
            }
            merged.add(intArrayOf(run[0], run[1]))
        }

        val zones = ArrayList<BrakingZone>()
        for (run in merged) {
            val i0 = run[0]
            val i1 = min(run[1] + decelWindow, smoothSpeed.lastIndex)
            val entry = smoothSpeed[i0]
            val exit = smoothSpeed[i1]
            val drop = entry - exit
            val durationS = (samples[range.from + i1].timestampMs -
                    samples[range.from + i0].timestampMs) / 1000.0

            if (drop < MIN_SPEED_DROP_KMH) continue
            if (durationS < MIN_BRAKING_DURATION_S) continue

            var peak = 0.0
            for (i in run[0]..run[1]) if (decel[i] < peak) peak = decel[i]

            zones.add(
                BrakingZone(
                    startMs = samples[range.from + i0].timestampMs,
                    endMs = samples[range.from + i1].timestampMs,
                    entrySpeedKmh = entry.toFloat(),
                    exitSpeedKmh = exit.toFloat(),
                    speedDropKmh = drop.toFloat(),
                    peakDecelMs2 = peak.toFloat(),
                    peakG = (abs(peak) / GRAVITY_MS2).toFloat(),
                    entersCorner = null
                )
            )
        }

        return zones.map { it.copy(entersCorner = associateCorner(it, corners)) }
    }

    /** The corner a braking zone leads into: the next apex after the zone ends. */
    private fun associateCorner(zone: BrakingZone, corners: List<Corner>): String? {
        var best: Corner? = null
        var bestDelta = Double.MAX_VALUE
        for (corner in corners) {
            val deltaS = (corner.apexMs - zone.endMs) / 1000.0
            if (deltaS >= CORNER_ASSOCIATION_TOLERANCE_S && deltaS < bestDelta) {
                best = corner
                bestDelta = deltaS
            }
        }
        return best?.name
    }

    // ------------------------------------------------------------------
    // 4. Track outline
    // ------------------------------------------------------------------

    /**
     * Projects the reference range onto a normalised, aspect-preserving square
     * ready for drawing, and flags the points that fall inside a braking zone.
     *
     * The projection is the same equirectangular approximation lap detection
     * uses, valid for the track sizes this app supports (SRS LD-13).
     */
    internal fun buildTrackPath(
        samples: List<Point>,
        range: Range,
        brakingZones: List<BrakingZone>,
        corners: List<Corner>,
        startLine: Pair<Double, Double>?
    ): TrackPath {
        if (range.size < 2) return TrackPath(emptyList(), emptyList(), null, 0f, 0f)

        val refLat = samples[range.from].latitude
        val refLng = samples[range.from].longitude

        val indices = decimate(range, TRACK_PATH_POINT_BUDGET)

        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        val local = ArrayList<Pair<Double, Double>>(indices.size)

        for (idx in indices) {
            val (x, y) = GeoUtils.toLocalMetres(samples[idx].latitude, samples[idx].longitude, refLat, refLng)
            local.add(x to y)
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        val width = maxX - minX
        val height = maxY - minY
        val extent = max(width, height)
        if (extent <= 0.0) return TrackPath(emptyList(), emptyList(), null, 0f, 0f)

        // Scale by the larger dimension so the shape is never stretched, and
        // centre the smaller one.
        val offsetX = (extent - width) / 2.0
        val offsetY = (extent - height) / 2.0

        fun normalise(x: Double, y: Double): Pair<Float, Float> {
            val nx = ((x - minX) + offsetX) / extent
            // y grows north, screen y grows down.
            val ny = 1.0 - (((y - minY) + offsetY) / extent)
            return nx.toFloat() to ny.toFloat()
        }

        var minSpeed = Double.MAX_VALUE
        var maxSpeed = 0.0
        val points = ArrayList<TrackPoint>(indices.size)
        for (k in indices.indices) {
            val sample = samples[indices[k]]
            val (nx, ny) = normalise(local[k].first, local[k].second)
            if (sample.speedKmh < minSpeed) minSpeed = sample.speedKmh
            if (sample.speedKmh > maxSpeed) maxSpeed = sample.speedKmh
            points.add(
                TrackPoint(
                    x = nx,
                    y = ny,
                    speedKmh = sample.speedKmh.toFloat(),
                    timestampMs = sample.timestampMs,
                    isBraking = brakingZones.any { sample.timestampMs in it.startMs..it.endMs }
                )
            )
        }

        val cornerMarkers = corners.mapNotNull { corner ->
            val idx = nearestIndexByTime(samples, range, corner.apexMs) ?: return@mapNotNull null
            val (x, y) = GeoUtils.toLocalMetres(samples[idx].latitude, samples[idx].longitude, refLat, refLng)
            val (nx, ny) = normalise(x, y)
            TrackMarker(corner.name, nx, ny)
        }

        val startFinish = startLine?.let { (lat, lng) ->
            val (x, y) = GeoUtils.toLocalMetres(lat, lng, refLat, refLng)
            val (nx, ny) = normalise(x, y)
            TrackMarker(START_FINISH_LABEL, nx, ny)
        } ?: normalise(local.first().first, local.first().second).let { (nx, ny) ->
            TrackMarker(START_FINISH_LABEL, nx, ny)
        }

        return TrackPath(
            points = points,
            cornerMarkers = cornerMarkers,
            startFinish = startFinish,
            minSpeedKmh = minSpeed.toFloat(),
            maxSpeedKmh = maxSpeed.toFloat()
        )
    }

    const val START_FINISH_LABEL = "S/F"

    private fun nearestIndexByTime(samples: List<Point>, range: Range, timestampMs: Long): Int? {
        if (range.size < 1) return null
        var best = range.from
        var bestDelta = Long.MAX_VALUE
        for (i in range.from..range.to) {
            val delta = abs(samples[i].timestampMs - timestampMs)
            if (delta < bestDelta) {
                best = i
                bestDelta = delta
            }
        }
        return best
    }

    /**
     * Uniformly thins [range] down to at most [budget] indices, always keeping
     * both endpoints so the outline still closes where the real one did.
     */
    internal fun decimate(range: Range, budget: Int): IntArray {
        val size = range.size
        if (size <= budget) return IntArray(size) { range.from + it }

        val step = size.toDouble() / budget
        val indices = IntArray(budget)
        for (i in 0 until budget) {
            indices[i] = range.from + min((i * step).toInt(), size - 1)
        }
        indices[budget - 1] = range.to
        return indices
    }

    // ------------------------------------------------------------------
    // 5. Session speed trace
    // ------------------------------------------------------------------

    /** Speed against elapsed time for the whole session, thinned for plotting. */
    internal fun buildSpeedTrace(samples: List<Point>): List<SpeedTimePoint> {
        if (samples.size < 2) return emptyList()
        val t0 = samples.first().timestampMs
        return decimate(Range(0, samples.lastIndex), SPEED_TRACE_POINT_BUDGET).map { idx ->
            SpeedTimePoint(
                elapsedS = ((samples[idx].timestampMs - t0) / 1000.0).toFloat(),
                speedKmh = samples[idx].speedKmh.toFloat()
            )
        }
    }
}
