package com.drivingcoach.ui.home

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.data.location.GpsReadiness
import com.drivingcoach.data.location.LocationWarmUp
import com.drivingcoach.service.TelemetryForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.sqrt

data class SessionSummary(
    val id: Long,
    val trackName: String,
    val startedAt: Long,
    val bestLapMs: Long?,
    val lapCount: Int,
    val consistencyScore: Float
)

data class BestLapInfo(
    val lapTimeMs: Long,
    val trackName: String,
    val date: Long
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val sessions: List<SessionSummary> = emptyList(),
    val overallBestLap: BestLapInfo? = null,
    val hasStaleUploads: Boolean = false,
    val error: String? = null
)

sealed class HomeEvent {
    data class NavigateToRecording(val sessionId: Long) : HomeEvent()
    data class NavigateToSessionResult(val sessionId: Long) : HomeEvent()
    data class NavigateToTrackSetup(val trackName: String) : HomeEvent()
    object NavigateToProfile : HomeEvent()
    data class ShowError(val message: String) : HomeEvent()
    data class ShowSessionDeleted(val trackName: String) : HomeEvent()
    data class ShowSessionRenamed(val newName: String) : HomeEvent()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val lapDao: LapDao,
    private val locationWarmUp: LocationWarmUp,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /**
     * GPS readiness for the hero chip (SRS TS-16, TS-17).
     *
     * Home is the last screen where the user is still busy — reading lap times, deciding
     * which track to set up — so it is the last chance to pay the cold time-to-first-fix
     * somewhere other than in front of a disabled Capture button.
     */
    val gpsReadiness: StateFlow<GpsReadiness> = locationWarmUp.readiness

    /** Called when Home becomes visible. Idempotent; also refreshes the idle ceiling. */
    fun startGpsWarmUp() = locationWarmUp.start()

    /** Called when Home stops. Releases the chip so backgrounding never keeps GPS alive. */
    fun stopGpsWarmUp() = locationWarmUp.stop()

    // Single-user MVP: no userId filtering needed
    
    companion object {
        private const val STALE_UPLOAD_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        
        fun formatDate(timestamp: Long): String {
            return DATE_FORMAT.format(Date(timestamp))
        }
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    init {
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            try {
                sessionDao.getAllSessions().collect { sessions ->
                    val sessionSummaries = sessions.map { session ->
                        val laps = lapDao.getLapsForSession(session.id).first()
                        val bestLap = laps.minByOrNull { it.durationMs }
                        val consistencyScore = computeConsistencyScore(laps)
                        
                        SessionSummary(
                            id = session.id,
                            trackName = session.trackName,
                            startedAt = session.startedAt,
                            bestLapMs = bestLap?.durationMs,
                            lapCount = laps.size,
                            consistencyScore = consistencyScore
                        )
                    }

                    // Find overall best lap across all sessions
                    val overallBestLap = findOverallBestLap(sessions)
                    
                    // Check for stale uploads (pending for more than 5 minutes)
                    val staleThreshold = System.currentTimeMillis() - STALE_UPLOAD_THRESHOLD_MS
                    val staleUploads = sessionDao.getStaleUploadSessions(staleThreshold)

                    _uiState.value = HomeUiState(
                        isLoading = false,
                        sessions = sessionSummaries,
                        overallBestLap = overallBestLap,
                        hasStaleUploads = staleUploads.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load sessions"
                )
            }
        }
    }
    
    fun dismissUploadBanner() {
        _uiState.value = _uiState.value.copy(hasStaleUploads = false)
    }

    private suspend fun findOverallBestLap(sessions: List<SessionEntity>): BestLapInfo? {
        var bestLap: LapEntity? = null
        var bestSession: SessionEntity? = null

        for (session in sessions) {
            val laps = lapDao.getLapsForSession(session.id).first()
            val sessionBest = laps.minByOrNull { it.durationMs }
            if (sessionBest != null && (bestLap == null || sessionBest.durationMs < bestLap.durationMs)) {
                bestLap = sessionBest
                bestSession = session
            }
        }

        return if (bestLap != null && bestSession != null) {
            BestLapInfo(
                lapTimeMs = bestLap.durationMs,
                trackName = bestSession.trackName,
                date = bestSession.startedAt
            )
        } else null
    }

    private fun computeConsistencyScore(laps: List<LapEntity>): Float {
        if (laps.size < 2) return 100f
        
        val durations = laps.map { it.durationMs.toDouble() }
        val mean = durations.average()
        val variance = durations.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        
        val cv = stdDev / mean
        return ((1 - cv) * 100).coerceIn(0.0, 100.0).toFloat()
    }

    fun startNewSession(trackName: String) {
        viewModelScope.launch {
            // Navigate to track setup instead of directly starting recording
            _events.emit(HomeEvent.NavigateToTrackSetup(trackName.ifBlank { "Unknown Track" }))
        }
    }

    fun onSessionClick(sessionId: Long) {
        viewModelScope.launch {
            _events.emit(HomeEvent.NavigateToSessionResult(sessionId))
        }
    }

    fun onProfileClick() {
        viewModelScope.launch {
            _events.emit(HomeEvent.NavigateToProfile)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            try {
                val session = sessionDao.getSessionByIdSync(sessionId)
                val trackName = session?.trackName ?: "Session"
                
                // Delete telemetry file if it exists
                session?.rawFilePath?.let { path ->
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            file.delete()
                            Log.d("HomeViewModel", "Deleted telemetry file: $path")
                        }
                    } catch (e: Exception) {
                        Log.w("HomeViewModel", "Failed to delete telemetry file: $path", e)
                    }
                }
                
                // Delete from Room (CASCADE will remove laps and coaching insights)
                sessionDao.deleteById(sessionId)
                Log.d("HomeViewModel", "Deleted session: $sessionId")
                
                _events.emit(HomeEvent.ShowSessionDeleted(trackName))
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to delete session: $sessionId", e)
                _events.emit(HomeEvent.ShowError("Failed to delete session"))
            }
        }
    }

    fun renameSession(sessionId: Long, newName: String) {
        viewModelScope.launch {
            try {
                val trimmedName = newName.trim()
                if (trimmedName.isEmpty() || trimmedName.length > 100) {
                    _events.emit(HomeEvent.ShowError("Track name must be 1-100 characters"))
                    return@launch
                }
                
                sessionDao.updateTrackName(sessionId, trimmedName)
                Log.d("HomeViewModel", "Renamed session $sessionId to: $trimmedName")
                
                _events.emit(HomeEvent.ShowSessionRenamed(trimmedName))
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to rename session: $sessionId", e)
                _events.emit(HomeEvent.ShowError("Failed to rename session"))
            }
        }
    }
}
