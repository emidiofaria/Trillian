package com.bmw.drivingcoach.ui.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmw.drivingcoach.data.db.dao.LapDao
import com.bmw.drivingcoach.data.db.dao.SessionDao
import com.bmw.drivingcoach.data.db.entity.LapEntity
import com.bmw.drivingcoach.data.db.entity.SessionEntity
import com.bmw.drivingcoach.service.TelemetryForegroundService
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
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val lapDao: LapDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // TODO: Replace with actual user ID from auth
    private val currentUserId = "demo_user"
    
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
                sessionDao.getAllSessionsForUser(currentUserId).collect { sessions ->
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
}
