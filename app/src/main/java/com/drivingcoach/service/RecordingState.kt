package com.drivingcoach.service

/**
 * Represents the current state of the telemetry recording service.
 */
sealed class RecordingState {
    
    /**
     * Service is idle, not recording.
     */
    data object Idle : RecordingState()
    
    /**
     * Service is actively recording telemetry data.
     */
    data class Recording(
        val sessionId: Long,
        val elapsedMs: Long,
        val lapCount: Int,
        val gpsLocked: Boolean
    ) : RecordingState()
    
    /**
     * Service is in the process of stopping (saving data, cleaning up).
     */
    data object Stopping : RecordingState()
    
    /**
     * An error occurred that prevents recording.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : RecordingState()
}
