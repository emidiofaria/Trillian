package com.drivingcoach.ui.session.tabs

/**
 * Represents a single data point for the speed chart.
 * X-axis: distance from lap start in meters
 * Y-axis: speed in km/h
 */
data class SpeedDataPoint(
    val distanceMeters: Float,
    val speedKmh: Float
)

/**
 * Processing mode for chart data.
 */
enum class ChartProcessingMode {
    /** Downsample to ~100 points per lap for fast rendering */
    FAST,
    /** Full resolution - all telemetry samples */
    DETAILED
}
