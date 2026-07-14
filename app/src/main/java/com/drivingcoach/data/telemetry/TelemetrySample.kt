package com.drivingcoach.data.telemetry

/**
 * Header line written at the start of a telemetry JSONL file.
 * Contains session metadata including start line coordinates for lap detection.
 */
data class TelemetryHeader(
    val type: String = "header",
    val sessionId: Long,
    val startLine: StartLineData? = null,
    val trackName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Start line coordinates embedded in telemetry header.
 */
data class StartLineData(
    val lat1: Double,
    val lng1: Double,
    val lat2: Double,
    val lng2: Double
)

/**
 * Represents a single telemetry sample captured during a driving session.
 * This is NOT a Room entity - it is serialized to JSONL files for efficient storage.
 */
data class TelemetrySample(
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val speedMs: Float,
    val headingDeg: Float,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val gpsAccuracyM: Float
) {
    companion object {
        /**
         * Creates a sample with current timestamp.
         */
        fun now(
            latitude: Double,
            longitude: Double,
            speedMs: Float,
            headingDeg: Float,
            accelX: Float,
            accelY: Float,
            accelZ: Float,
            gyroX: Float,
            gyroY: Float,
            gyroZ: Float,
            gpsAccuracyM: Float
        ): TelemetrySample = TelemetrySample(
            timestampMs = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            speedMs = speedMs,
            headingDeg = headingDeg,
            accelX = accelX,
            accelY = accelY,
            accelZ = accelZ,
            gyroX = gyroX,
            gyroY = gyroY,
            gyroZ = gyroZ,
            gpsAccuracyM = gpsAccuracyM
        )
    }
}
