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
        
        /** Minimum distance from start line before a crossing counts (200 metres) */
        const val MIN_DISTANCE_FROM_START_M = 50.0  // 50m works for kart tracks (300-500m)
        
        /** Minimum number of samples required for valid detection */
        const val MIN_SAMPLES = 50
    }

    /**
     * Represents a start/finish line defined by two GPS coordinates.
     */
    data class StartLine(
        val lat1: Double,
        val lng1: Double,
        val lat2: Double,
        val lng2: Double
    ) {
        fun isValid(): Boolean = lat1 != 0.0 || lng1 != 0.0 || lat2 != 0.0 || lng2 != 0.0
        
        /** Returns the midpoint of the start line */
        fun midpoint(): Pair<Double, Double> = Pair((lat1 + lat2) / 2, (lng1 + lng2) / 2)
    }

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
     * Detects laps from a JSONL telemetry file.
     *
     * @param jsonlFile The telemetry file to process
     * @param startLine The start/finish line coordinates
     * @return DetectionResult containing detected laps or error information
     */
    fun detectLaps(jsonlFile: File, startLine: StartLine): DetectionResult {
        Log.d(TAG, "=== LAP DETECTION START ===")
        Log.d(TAG, "Start line: (${startLine.lat1}, ${startLine.lng1}) to (${startLine.lat2}, ${startLine.lng2})")
        Log.d(TAG, "Start line valid: ${startLine.isValid()}")
        
        // Validate start line
        if (!startLine.isValid()) {
            Log.w(TAG, "Invalid start line!")
            return DetectionResult.NoStartLine()
        }

        // Check file exists
        if (!jsonlFile.exists()) {
            Log.e(TAG, "File not found: ${jsonlFile.absolutePath}")
            return DetectionResult.Error("Telemetry file not found: ${jsonlFile.absolutePath}")
        }

        // Read and parse samples
        val samples = readSamples(jsonlFile)
        Log.d(TAG, "Read ${samples.size} telemetry samples")
        if (samples.size < MIN_SAMPLES) {
            return DetectionResult.Error("Insufficient telemetry data (${samples.size} samples, need $MIN_SAMPLES)")
        }
        
        // Log GPS bounds
        val minLat = samples.minOf { it.latitude }
        val maxLat = samples.maxOf { it.latitude }
        val minLng = samples.minOf { it.longitude }
        val maxLng = samples.maxOf { it.longitude }
        Log.d(TAG, "GPS bounds: lat[$minLat to $maxLat], lng[$minLng to $maxLng]")

        // Detect line crossings
        val crossings = detectCrossings(samples, startLine)
        Log.d(TAG, "Detected ${crossings.size} line crossings")
        
        // Build laps from crossings
        val laps = buildLaps(crossings)
        Log.d(TAG, "Built ${laps.size} laps from crossings")

        return if (laps.size >= 2) {
            DetectionResult.Success(laps)
        } else {
            DetectionResult.InsufficientLaps(laps.size)
        }
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
     * Detects timestamps when the car crosses the start/finish line.
     * 
     * Applies filters:
     * - Must have traveled MIN_DISTANCE_FROM_START_M from line before counting
     * - Must have at least MIN_LAP_TIME_MS between crossings
     */
    private fun detectCrossings(samples: List<TelemetrySample>, startLine: StartLine): List<Long> {
        if (samples.size < 2) return emptyList()

        val crossings = mutableListOf<Long>()
        val lineMidpoint = startLine.midpoint()
        var lastCrossingTs: Long? = null
        var maxDistanceFromStart = 0.0

        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val curr = samples[i]

            // Calculate current distance from start line midpoint
            val distanceFromStart = GeoUtils.haversineDistance(
                curr.latitude, curr.longitude,
                lineMidpoint.first, lineMidpoint.second
            )
            maxDistanceFromStart = maxOf(maxDistanceFromStart, distanceFromStart)

            // Check if this segment crosses the start line
            val crosses = GeoUtils.lineIntersection(
                startLine.lat1, startLine.lng1,
                startLine.lat2, startLine.lng2,
                prev.latitude, prev.longitude,
                curr.latitude, curr.longitude
            )

            if (crosses) {
                val crossingTs = curr.timestampMs
                
                // First crossing - always record it
                if (lastCrossingTs == null) {
                    crossings.add(crossingTs)
                    lastCrossingTs = crossingTs
                    maxDistanceFromStart = 0.0
                    continue
                }

                // Subsequent crossings - apply guards
                val timeSinceLastCrossing = crossingTs - lastCrossingTs

                // Guard 1: Minimum time between crossings
                if (timeSinceLastCrossing < MIN_LAP_TIME_MS) {
                    continue
                }

                // Guard 2: Must have traveled away from start before coming back
                if (maxDistanceFromStart < MIN_DISTANCE_FROM_START_M) {
                    continue
                }

                // Valid crossing
                crossings.add(crossingTs)
                lastCrossingTs = crossingTs
                maxDistanceFromStart = 0.0
            }
        }

        return crossings
    }

    /**
     * Builds lap objects from crossing timestamps.
     * 
     * A lap is defined as the time between two consecutive crossings.
     * The last incomplete lap (if session ended before re-crossing) is discarded.
     */
    private fun buildLaps(crossings: List<Long>): List<DetectedLap> {
        if (crossings.size < 2) return emptyList()

        val laps = mutableListOf<DetectedLap>()
        
        for (i in 1 until crossings.size) {
            val startTs = crossings[i - 1]
            val endTs = crossings[i]
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
