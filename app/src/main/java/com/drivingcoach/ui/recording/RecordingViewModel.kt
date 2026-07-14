package com.drivingcoach.ui.recording

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.data.repository.SessionRepository
import com.drivingcoach.data.telemetry.TelemetryFileWriter
import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.service.RecordingState
import com.drivingcoach.service.TelemetryForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * UI state for the recording screen.
 */
data class RecordingUiState(
    val elapsedMs: Long = 0L,
    val gpsLocked: Boolean = false,
    val isRecording: Boolean = false,
    val sessionId: Long = -1L,
    val lapCount: Int = 0,
    val isStopping: Boolean = false,
    val isProcessingLaps: Boolean = false,
    val isReadyToNavigate: Boolean = false,
    val lapDetectionMessage: String? = null,
    val error: String? = null
)

/**
 * Start line coordinates passed from TrackSetupFragment.
 */
data class StartLineCoords(
    val lat1: Double,
    val lng1: Double,
    val lat2: Double,
    val lng2: Double
) {
    fun isValid(): Boolean = lat1 != 0.0 || lng1 != 0.0 || lat2 != 0.0 || lng2 != 0.0
}

@HiltViewModel
class RecordingViewModel @Inject constructor(
    application: Application,
    private val sessionRepository: SessionRepository,
    private val lapDao: LapDao,
    private val localLapDetector: LocalLapDetector
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RecordingViewModel"
    }

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()
    
    private var serviceBinder: TelemetryForegroundService.TelemetryBinder? = null
    private var isBound = false
    private var hasProcessedLaps = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBinder = service as? TelemetryForegroundService.TelemetryBinder
            isBound = true
            
            // Collect service state and map to UI state
            serviceBinder?.let { binder ->
                viewModelScope.launch {
                    binder.getStateFlow().collectLatest { recordingState ->
                        handleServiceState(recordingState)
                    }
                }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
            isBound = false
        }
    }
    
    /**
     * Starts recording for the given session ID.
     * Binds to the service and sends start action.
     */
    fun startRecording(sessionId: Long) {
        val context = getApplication<Application>()
        
        // Start the foreground service
        TelemetryForegroundService.startRecording(context, sessionId)
        
        // Bind to the service
        bindToService()
        
        _uiState.value = _uiState.value.copy(
            sessionId = sessionId,
            isRecording = true
        )
    }

    /**
     * Creates a new session with the given start line coordinates and starts recording.
     * Used when navigating from TrackSetupFragment.
     */
    fun createSessionAndStartRecording(
        trackName: String,
        startLine: StartLineCoords?
    ) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val timestamp = System.currentTimeMillis()
                // Note: rawFilePath is a placeholder - actual file created by TelemetryFileWriter
                // Will be updated after session is created with correct session ID
                val rawFilePath = "" // Placeholder, updated below

                val session = SessionEntity(
                    userId = "default_user", // TODO: Get from auth/preferences
                    trackName = trackName,
                    startedAt = timestamp,
                    rawFilePath = rawFilePath,
                    startLineLat1 = startLine?.lat1,
                    startLineLng1 = startLine?.lng1,
                    startLineLat2 = startLine?.lat2,
                    startLineLng2 = startLine?.lng2
                )

                val newSessionId = sessionRepository.createSession(session)
                
                // Update rawFilePath to match TelemetryFileWriter's naming convention
                val actualFilePath = TelemetryFileWriter.getFilePathForSession(context, newSessionId)
                sessionRepository.updateRawFilePath(newSessionId, actualFilePath)
                
                startRecording(newSessionId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to create session: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Stops the current recording session.
     * Processes laps locally after the service stops.
     */
    fun stopRecording() {
        // Capture sessionId before stopping service (service may become unavailable)
        val sessionId = _uiState.value.sessionId
        
        val context = getApplication<Application>()
        TelemetryForegroundService.stopRecording(context)
        
        _uiState.value = _uiState.value.copy(isStopping = true)
        
        // Process laps directly - don't rely on service state flow
        // (service calls stopSelf() which breaks binding before we can observe Idle state)
        if (sessionId != -1L && !hasProcessedLaps) {
            hasProcessedLaps = true
            Log.d(TAG, "Triggering local lap processing for session $sessionId")
            processLapsLocally()
        }
    }
    
    /**
     * Binds to the TelemetryForegroundService.
     */
    fun bindToService() {
        if (!isBound) {
            val context = getApplication<Application>()
            val intent = Intent(context, TelemetryForegroundService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    /**
     * Unbinds from the service.
     */
    fun unbindFromService() {
        if (isBound) {
            try {
                val context = getApplication<Application>()
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                // Ignore if not bound
            }
            isBound = false
            serviceBinder = null
        }
    }
    
    /**
     * Handles service state changes during recording.
     * Note: Lap processing is triggered from stopRecording() directly,
     * not from here, because the service binding breaks before Idle is emitted.
     */
    private fun handleServiceState(state: RecordingState) {
        when (state) {
            is RecordingState.Idle -> {
                // Service has stopped - update UI state
                // Lap processing already triggered from stopRecording()
                if (!_uiState.value.isProcessingLaps) {
                    _uiState.value = _uiState.value.copy(
                        isRecording = false,
                        isStopping = false
                    )
                }
            }
            is RecordingState.Recording -> {
                _uiState.value = RecordingUiState(
                    elapsedMs = state.elapsedMs,
                    gpsLocked = state.gpsLocked,
                    isRecording = true,
                    sessionId = state.sessionId,
                    lapCount = state.lapCount,
                    isStopping = false
                )
            }
            is RecordingState.Stopping -> {
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    isStopping = true
                )
            }
            is RecordingState.Error -> {
                _uiState.value = RecordingUiState(
                    isRecording = false,
                    error = state.message
                )
            }
        }
    }

    /**
     * Processes laps locally after recording stops.
     * Reads the JSONL file, detects lap crossings, and saves to Room.
     */
    private fun processLapsLocally() {
        val sessionId = _uiState.value.sessionId
        if (sessionId == -1L) {
            Log.w(TAG, "Cannot process laps: invalid session ID")
            _uiState.value = _uiState.value.copy(
                isProcessingLaps = false,
                isReadyToNavigate = true
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isProcessingLaps = true,
            lapDetectionMessage = "Processing laps locally..."
        )

        viewModelScope.launch {
            try {
                // Get session from Room
                val session = withContext(Dispatchers.IO) {
                    sessionRepository.getSessionByIdSync(sessionId)
                }

                if (session == null) {
                    Log.w(TAG, "Session not found: $sessionId")
                    finishProcessing("Session not found", 0)
                    return@launch
                }

                // Check for start line
                val startLine = session.startLineLat1?.let { lat1 ->
                    session.startLineLng1?.let { lng1 ->
                        session.startLineLat2?.let { lat2 ->
                            session.startLineLng2?.let { lng2 ->
                                LocalLapDetector.StartLine(lat1, lng1, lat2, lng2)
                            }
                        }
                    }
                }

                if (startLine == null) {
                    Log.w(TAG, "No start line defined for session $sessionId")
                    finishProcessing("No start line defined", 0)
                    return@launch
                }

                // Detect laps
                val jsonlFile = File(session.rawFilePath)
                val result = withContext(Dispatchers.IO) {
                    localLapDetector.detectLaps(jsonlFile, startLine)
                }

                when (result) {
                    is LocalLapDetector.DetectionResult.Success -> {
                        // Save laps to Room
                        saveLapsToRoom(sessionId, result.laps)
                        finishProcessing("${result.laps.size} laps detected", result.laps.size)
                    }
                    is LocalLapDetector.DetectionResult.InsufficientLaps -> {
                        // Still save any laps detected, but warn user
                        if (result.lapCount > 0) {
                            val laps = listOf<LocalLapDetector.DetectedLap>() // No laps to save for comparison
                            finishProcessing(
                                "Only ${result.lapCount} lap detected. Minimum 2 laps needed for comparison.",
                                result.lapCount
                            )
                        } else {
                            finishProcessing("No laps detected. Complete at least 2 laps.", 0)
                        }
                    }
                    is LocalLapDetector.DetectionResult.NoStartLine -> {
                        finishProcessing(result.message, 0)
                    }
                    is LocalLapDetector.DetectionResult.Error -> {
                        Log.e(TAG, "Lap detection error: ${result.message}")
                        finishProcessing(result.message, 0)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing laps", e)
                finishProcessing("Error: ${e.message}", 0)
            }
        }
    }

    /**
     * Saves detected laps to Room database.
     */
    private suspend fun saveLapsToRoom(sessionId: Long, detectedLaps: List<LocalLapDetector.DetectedLap>) {
        if (detectedLaps.isEmpty()) return

        // Find best lap
        val bestLap = localLapDetector.findBestLap(detectedLaps)

        // Convert to LapEntity
        val lapEntities = detectedLaps.map { lap ->
            LapEntity(
                sessionId = sessionId,
                lapNumber = lap.lapNumber,
                startTs = lap.startTs,
                endTs = lap.endTs,
                durationMs = lap.durationMs,
                sector1Ms = 0L,  // No sectors in Phase 1
                sector2Ms = 0L,
                sector3Ms = 0L,
                isBestLap = lap == bestLap,
                isLocalOnly = true
            )
        }

        // Insert to Room
        withContext(Dispatchers.IO) {
            lapDao.insertLaps(lapEntities)
        }

        Log.d(TAG, "Saved ${lapEntities.size} laps to Room for session $sessionId")
    }

    /**
     * Completes lap processing and signals ready to navigate.
     */
    private fun finishProcessing(message: String, lapCount: Int) {
        _uiState.value = _uiState.value.copy(
            isProcessingLaps = false,
            lapDetectionMessage = message,
            lapCount = lapCount,
            isReadyToNavigate = true
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        unbindFromService()
    }
}

