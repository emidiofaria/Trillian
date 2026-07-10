package com.drivingcoach.ui.recording

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.data.repository.SessionRepository
import com.drivingcoach.service.RecordingState
import com.drivingcoach.service.TelemetryForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
    private val sessionRepository: SessionRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()
    
    private var serviceBinder: TelemetryForegroundService.TelemetryBinder? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBinder = service as? TelemetryForegroundService.TelemetryBinder
            isBound = true
            
            // Collect service state and map to UI state
            serviceBinder?.let { binder ->
                viewModelScope.launch {
                    binder.getStateFlow().collectLatest { recordingState ->
                        mapServiceStateToUiState(recordingState)
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
                val rawFilePath = File(
                    context.filesDir,
                    "telemetry_${timestamp}.jsonl"
                ).absolutePath

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
     */
    fun stopRecording() {
        val context = getApplication<Application>()
        TelemetryForegroundService.stopRecording(context)
        
        _uiState.value = _uiState.value.copy(isStopping = true)
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
    
    private fun mapServiceStateToUiState(state: RecordingState) {
        _uiState.value = when (state) {
            is RecordingState.Idle -> RecordingUiState(
                isRecording = false,
                isStopping = false
            )
            is RecordingState.Recording -> RecordingUiState(
                elapsedMs = state.elapsedMs,
                gpsLocked = state.gpsLocked,
                isRecording = true,
                sessionId = state.sessionId,
                lapCount = state.lapCount,
                isStopping = false
            )
            is RecordingState.Stopping -> RecordingUiState(
                isRecording = false,
                isStopping = true,
                sessionId = _uiState.value.sessionId
            )
            is RecordingState.Error -> RecordingUiState(
                isRecording = false,
                error = state.message
            )
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        unbindFromService()
    }
}

