package com.drivingcoach.lap

import android.util.Log
import com.drivingcoach.data.telemetry.StartLineData
import com.drivingcoach.data.telemetry.TelemetryHeader
import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.util.GeoUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot
import kotlin.math.roundToLong

private const val TAG = "LocalLapDetector"

/**
 * Detects laps locally from a JSONL telemetry file using start/finish line crossing.
 * 
 * This enables offline lap time display immediately after a session ends,
 * without requiring network connectivity to the backend.
 */
@Singleton
class LocalLapDetector @Inject constructor() {

    companion object {
        /** Minimum lap time in milliseconds (20 seconds) to filter false triggers */
        const val MIN_LAP_TIME_MS = 20_000L
        
        /** Minimum distance from the start line before a crossing counts (SRS LD-06) */
        const val MIN_DISTANCE_FROM_START_M = 50.0  // 50m works for kart tracks (300-500m)
        
        /** Minimum number of samples required for valid detection */
        const val MIN_SAMPLES = 50

        /**
         * How far to either side of the start point a pass still counts, in metres.
         *
         * This replaces the length of the user's captured line as the width of the
         * detection target. Incident 13 showed the captured line cannot serve that
         * purpose: it was 7.15 m long, while GPS error in the same session averaged
         * 4.8 m.
         *
         * The value is deliberately generous. On the incident 13 session the result
         * is identical for every value from 10 m to 25 m, because the driver passed
         * within 0.8-2.3 m of the start point on all five laps. A detector whose
         * answer does not depend on this number is one that is measuring the track
         * rather than the threshold.
         */
        const val DETECTION_HALF_WIDTH_M = 15.0

        /**
         * How far the direction of travel may differ from the first crossing of the
         * session before a crossing is rejected, in degrees.
         *
         * Every pass through a start/finish is made in substantially the same
         * direction: across the five laps of the incident 13 session the heading
         * varied by only 9 degrees. 60 is therefore generous towards real driving
         * while still rejecting a pass made in the opposite direction, or one made
         * by a different part of the circuit that happens to run near the start
         * point.
         *
         * Not set to 90: where the start/finish sits on a corner, the car arriving
         * and the car leaving differ by exactly 90 degrees, which would leave the
         * guard deciding a real case on floating-point rounding.
         */
        const val MAX_HEADING_DIFFERENCE_DEG = 60.0

        /** Segments shorter than this carry no reliable direction (car stationary). */
        private const val MIN_SEGMENT_LENGTH_M = 0.5
    }

    /**
     * Represents a start/finish line defined by two GPS coordinates.
     *
     * Only the midpoint of these two points is used to locate the start/finish.
     * Their orientation is deliberately ignored - see [detectCrossings].
     */
    data class StartLine(
        val lat1: Double,
        val lng1: Double,
        val lat2: Double,
        val lng2: Double
    ) {
        /**
         * A line is usable if neither endpoint is the null island (0, 0).
         *
         * The previous form of this check used `||`, which passed as long as any
         * single coordinate was non-zero: a line with one endpoint off the coast of
         * Africa was accepted as valid. Note that testing each coordinate against
         * zero individually would be wrong in the other direction, since longitude
         * 0 is Greenwich and a perfectly legitimate place to drive.
         */
        fun isValid(): Boolean {
            val firstIsNullIsland = lat1 == 0.0 && lng1 == 0.0
            val secondIsNullIsland = lat2 == 0.0 && lng2 == 0.0
            return !firstIsNullIsland && !secondIsNullIsland
        }
        
        /** Returns the midpoint of the start line */
        fun midpoint(): Pair<Double, Double> = Pair((lat1 + lat2) / 2, (lng1 + lng2) / 2)
    }

    /**
     * A crossing of the start/finish that was accepted.
     *
     * @param timestampMs interpolated instant of the crossing, not the timestamp of
     *   the sample that followed it
     * @param lateralOffsetM how far to the side of the start point the car passed
     * @param headingDeg direction of travel through the crossing
     */
    data class Crossing(
        val timestampMs: Long,
        val lateralOffsetM: Double,
        val headingDeg: Double
    )

    /** Why a candidate crossing was not counted as a lap boundary. */
    enum class RejectionReason {
        /** Came sooner after the previous crossing than [MIN_LAP_TIME_MS]. */
        TOO_SOON,
        /** The car never got [MIN_DISTANCE_FROM_START_M] away since the last crossing. */
        TOO_CLOSE_TO_START,
        /** Direction of travel differed from the first crossing by too much. */
        HEADING_MISMATCH,
        /** Passed the start point further to the side than [DETECTION_HALF_WIDTH_M]. */
        TOO_FAR_TO_THE_SIDE
    }

    /** A candidate crossing that was discarded, recorded so failures can be diagnosed. */
    data class RejectedCrossing(
        val timestampMs: Long,
        val reason: RejectionReason,
        val detail: String
    )

    /**
     * Represents a detected lap with timing information.
     */
    data class DetectedLap(
        val lapNumber: Int,
        val startTs: Long,
        val endTs: Long,
        val durationMs: Long
    )

    /**
     * Result of lap detection containing laps and any error/warning messages.
     */
    sealed class DetectionResult {
        data class Success(val laps: List<DetectedLap>) : DetectionResult()
        data class InsufficientLaps(val lapCount: Int) : DetectionResult()
        data class NoStartLine(val message: String = "No start line defined") : DetectionResult()
        data class Error(val message: String) : DetectionResult()
    }

    private val gson = Gson()

    /**
     * What the detector observed and decided during a run.
     *
     * Recorded because incident 09 - the same "no laps detected" symptom - was
     * closed at 55% confidence and its cause never established, for want of any
     * record of what the detector had decided. Incident 13 was only diagnosable
     * because the raw telemetry happened to be retained by hand. These figures
     * make the next occurrence answerable from the session itself.
     *
     * @param angleBetweenLineAndTravelDeg 0 means the captured start line lies
     *   along the direction the car travels, 90 means squarely across it. Incident
     *   13 measured 1.2 degrees. This single number identifies that failure.
     */
    data class DetectionDiagnostics(
        val sampleCount: Int,
        val durationMs: Long,
        val observedSampleRateHz: Double,
        val startLineLengthM: Double,
        val startLineBearingDeg: Double,
        val angleBetweenLineAndTravelDeg: Double?,
        val acceptedCrossings: List<Crossing>,
        val rejectedCrossings: List<RejectedCrossing>,
        val lapCount: Int
    )

    /** A detection result together with the reasoning that produced it. */
    data class DetectionOutcome(
        val result: DetectionResult,
        val diagnostics: DetectionDiagnostics?
    )

    /**
     * Detects laps from a JSONL telemetry file.
     *
     * @param jsonlFile The telemetry file to process
     * @param startLine The start/finish line coordinates
     * @return DetectionResult containing detected laps or error information
     */
    fun detectLaps(jsonlFile: File, startLine: StartLine): DetectionResult =
        detectLapsWithDiagnostics(jsonlFile, startLine).result

    /**
     * As [detectLaps], but also returns what the detector observed and why it
     * accepted or rejected each candidate crossing.
     *
     * Diagnostics are null when detection could not run at all (no start line, no
     * file, too few samples) - in those cases the result itself is the explanation.
     */
    fun detectLapsWithDiagnostics(jsonlFile: File, startLine: StartLine): DetectionOutcome {
        Log.d(TAG, "=== LAP DETECTION START ===")
        Log.d(TAG, "Start line: (${startLine.lat1}, ${startLine.lng1}) to (${startLine.lat2}, ${startLine.lng2})")
        Log.d(TAG, "Start line valid: ${startLine.isValid()}")
        
        // Validate start line
        if (!startLine.isValid()) {
            Log.w(TAG, "Invalid start line!")
            return DetectionOutcome(DetectionResult.NoStartLine(), null)
        }

        // Check file exists
        if (!jsonlFile.exists()) {
            Log.e(TAG, "File not found: ${jsonlFile.absolutePath}")
            return DetectionOutcome(
                DetectionResult.Error("Telemetry file not found: ${jsonlFile.absolutePath}"), null
            )
        }

        // Read and parse samples
        val samples = readSamples(jsonlFile)
        Log.d(TAG, "Read ${samples.size} telemetry samples")
        if (samples.size < MIN_SAMPLES) {
            return DetectionOutcome(
                DetectionResult.Error(
                    "Insufficient telemetry data (${samples.size} samples, need $MIN_SAMPLES)"
                ),
                null
            )
        }
        
        // Log GPS bounds
        val minLat = samples.minOf { it.latitude }
        val maxLat = samples.maxOf { it.latitude }
        val minLng = samples.minOf { it.longitude }
        val maxLng = samples.maxOf { it.longitude }
        Log.d(TAG, "GPS bounds: lat[$minLat to $maxLat], lng[$minLng to $maxLng]")

        // Detect crossings of the start/finish point
        val rejections = mutableListOf<RejectedCrossing>()
        val crossings = detectCrossings(samples, startLine, rejections)
        Log.d(TAG, "Detected ${crossings.size} crossings, rejected ${rejections.size} candidates")
        rejections.forEach { Log.d(TAG, "  rejected at ${it.timestampMs}: ${it.reason} - ${it.detail}") }
        
        // Build laps from crossings
        val laps = buildLaps(crossings)
        Log.d(TAG, "Built ${laps.size} laps from crossings")

        val diagnostics = buildDiagnostics(samples, startLine, crossings, rejections, laps.size)
        Log.d(
            TAG,
            "Start line: %.2fm long, bearing %.1f, %s to the direction of travel".format(
                diagnostics.startLineLengthM,
                diagnostics.startLineBearingDeg,
                diagnostics.angleBetweenLineAndTravelDeg
                    ?.let { "%.1f degrees".format(it) } ?: "angle unknown"
            )
        )

        val result = if (laps.size >= 2) {
            DetectionResult.Success(laps)
        } else {
            DetectionResult.InsufficientLaps(laps.size)
        }
        return DetectionOutcome(result, diagnostics)
    }

    private fun buildDiagnostics(
        samples: List<TelemetrySample>,
        startLine: StartLine,
        crossings: List<Crossing>,
        rejections: List<RejectedCrossing>,
        lapCount: Int
    ): DetectionDiagnostics {
        val durationMs = samples.last().timestampMs - samples.first().timestampMs
        val rateHz = if (durationMs > 0) (samples.size - 1) * 1000.0 / durationMs else 0.0

        val lengthM = GeoUtils.haversineDistance(
            startLine.lat1, startLine.lng1, startLine.lat2, startLine.lng2
        )
        val bearingDeg = GeoUtils.bearingDegrees(
            startLine.lat1, startLine.lng1, startLine.lat2, startLine.lng2
        )

        // When nothing was accepted there is no crossing to take a heading from, and
        // that is precisely the case this figure exists to explain. Fall back to the
        // heading of the sample nearest the start point: the car passed the start at
        // some point even in a session where no crossing was recognised.
        val travelHeading = crossings.firstOrNull()?.headingDeg
            ?: headingAtClosestApproach(samples, startLine)

        // Folded onto 0..90: a line pointing back down the track is just as parallel
        // to the racing direction as one pointing up it.
        val angleToTravel = travelHeading?.let {
            val difference = GeoUtils.angularDifferenceDegrees(bearingDeg, it)
            if (difference > 90.0) 180.0 - difference else difference
        }

        return DetectionDiagnostics(
            sampleCount = samples.size,
            durationMs = durationMs,
            observedSampleRateHz = rateHz,
            startLineLengthM = lengthM,
            startLineBearingDeg = bearingDeg,
            angleBetweenLineAndTravelDeg = angleToTravel,
            acceptedCrossings = crossings,
            rejectedCrossings = rejections,
            lapCount = lapCount
        )
    }

    /**
     * Direction the car was travelling as it came closest to the start point.
     *
     * Used to report the line-versus-travel angle for sessions where no crossing was
     * accepted at all. Returns null only if no segment in the session was long
     * enough to yield a meaningful bearing.
     */
    private fun headingAtClosestApproach(
        samples: List<TelemetrySample>,
        startLine: StartLine
    ): Double? {
        val (startLat, startLng) = startLine.midpoint()
        var bestDistance = Double.MAX_VALUE
        var bestHeading: Double? = null

        for (i in 0 until samples.size - 1) {
            val from = samples[i]
            val to = samples[i + 1]
            if (GeoUtils.haversineDistance(
                    from.latitude, from.longitude, to.latitude, to.longitude
                ) < MIN_SEGMENT_LENGTH_M
            ) continue

            val distance = GeoUtils.haversineDistance(
                startLat, startLng, from.latitude, from.longitude
            )
            if (distance < bestDistance) {
                bestDistance = distance
                bestHeading = GeoUtils.bearingDegrees(
                    from.latitude, from.longitude, to.latitude, to.longitude
                )
            }
        }
        return bestHeading
    }

    /**
     * Data class to hold both header and samples from a telemetry file.
     */
    private data class TelemetryFileData(
        val header: TelemetryHeader? = null,
        val samples: List<TelemetrySample> = emptyList()
    )

    /**
     * Reads telemetry file, parsing both header and samples.
     * Header line (if present) has type="header" field.
     */
    private fun readTelemetryFile(file: File): TelemetryFileData {
        val samples = mutableListOf<TelemetrySample>()
        var header: TelemetryHeader? = null
        
        try {
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { jsonLine ->
                        if (jsonLine.isNotBlank()) {
                            try {
                                // Check if this is a header line
                                val jsonObj = gson.fromJson(jsonLine, JsonObject::class.java)
                                if (jsonObj.has("type") && jsonObj.get("type").asString == "header") {
                                    header = gson.fromJson(jsonLine, TelemetryHeader::class.java)
                                    Log.d(TAG, "Parsed header: startLine=${header?.startLine}")
                                } else {
                                    // Regular telemetry sample
                                    val sample = gson.fromJson(jsonLine, TelemetrySample::class.java)
                                    samples.add(sample)
                                }
                            } catch (e: Exception) {
                                // Skip malformed lines
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading telemetry file", e)
        }
        
        return TelemetryFileData(header, samples.sortedBy { it.timestampMs })
    }

    /**
     * Reads telemetry samples from a JSONL file (legacy method for compatibility).
     */
    private fun readSamples(file: File): List<TelemetrySample> {
        return readTelemetryFile(file).samples
    }

    /**
     * Reads start line from file header if available.
     */
    fun readStartLineFromFile(file: File): StartLine? {
        val data = readTelemetryFile(file)
        val startLineData = data.header?.startLine ?: return null
        return StartLine(
            lat1 = startLineData.lat1,
            lng1 = startLineData.lng1,
            lat2 = startLineData.lat2,
            lng2 = startLineData.lng2
        )
    }

    /**
     * Detects the instants at which the car passed the start/finish point.
     *
     * ## Why this does not use the user's line
     *
     * The obvious implementation - test each pair of GPS samples for intersection
     * with the line the user captured - was the shipped implementation, and it
     * failed completely in incident 13. The user's two points were 7.15 m apart
     * while GPS accuracy in that session averaged 4.8 m, so the *bearing* of the
     * line they defined was dominated by measurement noise. It came out at 283.7
     * degrees; the car drove through it at 280-289 degrees. The car travelled
     * *along* the line rather than across it, and no intersection was ever
     * possible. The driver's five laps were reported as none. Making the line
     * longer does not help: none of those passes crosses even the infinite
     * extension of it.
     *
     * The rule this replaces it with: never derive a direction from two
     * measurements that are closer together than the measurement error.
     *
     * ## What it does instead
     *
     * The user's two points are used only to say *where* the start/finish is -
     * their midpoint. The direction is taken from the car, which is something GPS
     * measures well. For each pair of consecutive samples, the detector asks
     * whether the car passed the plane through the start point perpendicular to
     * its own direction of travel, and if so, how far to the side of that point it
     * passed. Within [DETECTION_HALF_WIDTH_M], that is a crossing.
     *
     * The instant of the crossing is interpolated between the two samples. At the
     * 1 Hz that many phones deliver, the car covers 15-20 m between samples, so
     * attributing the crossing to whichever sample came next costs up to a full
     * second on the lap time - well over 1% on a kart lap, enough to hide a real
     * improvement from the driver.
     *
     * ## Guards
     *
     * - a crossing sooner than [MIN_LAP_TIME_MS] after the last one is rejected;
     * - a crossing is rejected unless the car got [MIN_DISTANCE_FROM_START_M] away
     *   from the start point since the last one;
     * - a crossing whose direction of travel differs from the first crossing of the
     *   session by more than [MAX_HEADING_DIFFERENCE_DEG] is rejected (SRS LD-14).
     *
     * Distance travelled is measured since the last *accepted* crossing, and is
     * deliberately not reset when a candidate is rejected. The incident 13 RCA
     * flagged this as suspicious (finding F2) on the grounds that a rejected
     * crossing leaks distance into the next candidate. Testing it showed the
     * opposite: where the start/finish sits on a corner, one pass produces two
     * candidates a second apart - the car arriving, then the car leaving. The
     * first is rejected on heading. Resetting the distance there makes the
     * legitimate second candidate look as though the car had travelled ten metres,
     * and the lap is lost. A rejected candidate is not a lap boundary, so the
     * window it measures must not restart.
     */
    private fun detectCrossings(
        samples: List<TelemetrySample>,
        startLine: StartLine,
        rejections: MutableList<RejectedCrossing> = mutableListOf()
    ): List<Crossing> {
        if (samples.size < 2) return emptyList()

        val crossings = mutableListOf<Crossing>()
        val (anchorLat, anchorLng) = startLine.midpoint()

        var lastCrossingTs: Long? = null
        var referenceHeadingDeg: Double? = null
        var maxDistanceFromStart = 0.0

        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val curr = samples[i]

            maxDistanceFromStart = maxOf(
                maxDistanceFromStart,
                GeoUtils.haversineDistance(curr.latitude, curr.longitude, anchorLat, anchorLng)
            )

            // Both samples in metres relative to the start point, which sits at the origin.
            val (px, py) = GeoUtils.toLocalMetres(prev.latitude, prev.longitude, anchorLat, anchorLng)
            val (qx, qy) = GeoUtils.toLocalMetres(curr.latitude, curr.longitude, anchorLat, anchorLng)

            val dx = qx - px
            val dy = qy - py
            val segmentLength = hypot(dx, dy)
            if (segmentLength < MIN_SEGMENT_LENGTH_M) continue  // stationary: no direction

            val ux = dx / segmentLength
            val uy = dy / segmentLength

            // Distance of each sample from the plane through the start point that is
            // perpendicular to this direction of travel. A sign change means the car
            // passed it somewhere within this segment.
            val before = px * ux + py * uy
            val after = qx * ux + qy * uy
            if (!(before <= 0.0 && after >= 0.0) && !(before >= 0.0 && after <= 0.0)) continue
            if (before == after) continue

            val fraction = (0.0 - before) / (after - before)
            val crossingX = px + dx * fraction
            val crossingY = py + dy * fraction
            val lateralOffset = hypot(crossingX, crossingY)

            val crossingTs = prev.timestampMs +
                ((curr.timestampMs - prev.timestampMs) * fraction).roundToLong()
            val headingDeg = GeoUtils.bearingDegrees(
                prev.latitude, prev.longitude, curr.latitude, curr.longitude
            )

            if (lateralOffset > DETECTION_HALF_WIDTH_M) {
                // Not near enough to be this track's start/finish. Not noteworthy:
                // every lap passes this plane somewhere out on the far side of the
                // circuit, so these are not recorded as rejections.
                continue
            }

            if (lastCrossingTs == null) {
                crossings.add(Crossing(crossingTs, lateralOffset, headingDeg))
                lastCrossingTs = crossingTs
                referenceHeadingDeg = headingDeg
                maxDistanceFromStart = 0.0
                continue
            }

            val timeSinceLastCrossing = crossingTs - lastCrossingTs

            if (timeSinceLastCrossing < MIN_LAP_TIME_MS) {
                rejections.add(
                    RejectedCrossing(
                        crossingTs, RejectionReason.TOO_SOON,
                        "${timeSinceLastCrossing}ms since the previous crossing, minimum is $MIN_LAP_TIME_MS"
                    )
                )
                continue
            }

            if (maxDistanceFromStart < MIN_DISTANCE_FROM_START_M) {
                rejections.add(
                    RejectedCrossing(
                        crossingTs, RejectionReason.TOO_CLOSE_TO_START,
                        "got %.1fm from the start point, minimum is %.1fm"
                            .format(maxDistanceFromStart, MIN_DISTANCE_FROM_START_M)
                    )
                )
                continue
            }

            val headingDifference = referenceHeadingDeg?.let {
                GeoUtils.angularDifferenceDegrees(headingDeg, it)
            } ?: 0.0

            if (headingDifference > MAX_HEADING_DIFFERENCE_DEG) {
                rejections.add(
                    RejectedCrossing(
                        crossingTs, RejectionReason.HEADING_MISMATCH,
                        "travelling %.0f degrees, %.0f from the first crossing's %.0f"
                            .format(headingDeg, headingDifference, referenceHeadingDeg ?: 0.0)
                    )
                )
                continue
            }

            crossings.add(Crossing(crossingTs, lateralOffset, headingDeg))
            lastCrossingTs = crossingTs
            maxDistanceFromStart = 0.0
        }

        return crossings
    }

    /**
     * Builds lap objects from crossing timestamps.
     * 
     * A lap is defined as the time between two consecutive crossings.
     * The last incomplete lap (if session ended before re-crossing) is discarded.
     */
    private fun buildLaps(crossings: List<Crossing>): List<DetectedLap> {
        if (crossings.size < 2) return emptyList()

        val laps = mutableListOf<DetectedLap>()
        
        for (i in 1 until crossings.size) {
            val startTs = crossings[i - 1].timestampMs
            val endTs = crossings[i].timestampMs
            val durationMs = endTs - startTs

            laps.add(
                DetectedLap(
                    lapNumber = i,
                    startTs = startTs,
                    endTs = endTs,
                    durationMs = durationMs
                )
            )
        }

        return laps
    }

    /**
     * Finds the best (fastest) lap from a list of detected laps.
     */
    fun findBestLap(laps: List<DetectedLap>): DetectedLap? {
        return laps.minByOrNull { it.durationMs }
    }
}
