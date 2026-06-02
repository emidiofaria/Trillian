package com.bmw.drivingcoach.data.telemetry

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
