package com.drivingcoach.ui.session.tabs

import com.drivingcoach.data.telemetry.TelemetryFileReader
import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.util.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Processes telemetry data into chart-ready speed vs distance data points.
 */
object TelemetryChartProcessor {

    /** Threshold for "large" session that triggers processing mode dialog */
    const val LARGE_SESSION_LAP_THRESHOLD = 10

    /** Target points per lap in FAST mode */
    private const val FAST_MODE_POINTS_PER_LAP = 100

    /**
     * Computes speed vs distance data points for a single lap.
     *
     * @param filePath Path to telemetry JSONL file
     * @param startTs Lap start timestamp (ms)
     * @param endTs Lap end timestamp (ms)
     * @param mode Processing mode (FAST = downsampled, DETAILED = full)
     * @return List of SpeedDataPoint with distance in meters and speed in km/h
     */
    suspend fun computeSpeedByDistance(
        filePath: String,
        startTs: Long,
        endTs: Long,
        mode: ChartProcessingMode = ChartProcessingMode.DETAILED
    ): List<SpeedDataPoint> = withContext(Dispatchers.Default) {
        val samples = TelemetryFileReader.readRange(filePath, startTs, endTs)
        if (samples.size < 2) return@withContext emptyList()

        // Apply downsampling if FAST mode
        val processedSamples = when (mode) {
            ChartProcessingMode.FAST -> downsample(samples, FAST_MODE_POINTS_PER_LAP)
            ChartProcessingMode.DETAILED -> samples
        }

        computeDistanceAndSpeed(processedSamples)
    }

    /**
     * Estimates total sample count for a session without loading all data.
     */
    suspend fun estimateTotalSamples(
        filePath: String,
        laps: List<Pair<Long, Long>>
    ): Int = withContext(Dispatchers.IO) {
        TelemetryFileReader.countSamples(filePath)
    }

    /**
     * Downsamples a list of samples to approximately targetCount points.
     * Uses uniform sampling to preserve shape of the speed curve.
     */
    private fun downsample(samples: List<TelemetrySample>, targetCount: Int): List<TelemetrySample> {
        if (samples.size <= targetCount) return samples

        val step = samples.size.toFloat() / targetCount
        return (0 until targetCount).map { i ->
            samples[(i * step).toInt().coerceAtMost(samples.lastIndex)]
        }
    }

    /**
     * Computes cumulative distance and converts speed to km/h.
     */
    private fun computeDistanceAndSpeed(samples: List<TelemetrySample>): List<SpeedDataPoint> {
        var cumulativeDistance = 0.0

        return samples.mapIndexed { index, sample ->
            if (index > 0) {
                val prev = samples[index - 1]
                cumulativeDistance += GeoUtils.haversineDistance(
                    prev.latitude, prev.longitude,
                    sample.latitude, sample.longitude
                )
            }

            SpeedDataPoint(
                distanceMeters = cumulativeDistance.toFloat(),
                speedKmh = sample.speedMs * 3.6f
            )
        }
    }
}
