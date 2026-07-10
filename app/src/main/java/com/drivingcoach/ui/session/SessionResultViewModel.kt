package com.drivingcoach.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.drivingcoach.data.db.dao.CoachingInsightDao
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.entity.CoachingInsightEntity
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.ProcessingStatus
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.data.worker.TelemetryUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.sqrt

data class SessionUiState(
    val isLoading: Boolean = true,
    val session: SessionEntity? = null,
    val laps: List<LapEntity> = emptyList(),
    val insights: List<CoachingInsightEntity> = emptyList(),
    val bestLap: LapEntity? = null,
    val consistencyScore: Float = 0f,
    val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,
    val error: String? = null
)

@HiltViewModel
class SessionResultViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val lapDao: LapDao,
    private val coachingInsightDao: CoachingInsightDao,
    private val workManager: WorkManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    
    private var pollingJob: Job? = null

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            try {
                combine(
                    sessionDao.getSessionById(sessionId),
                    lapDao.getLapsForSession(sessionId),
                    coachingInsightDao.getInsightsForSession(sessionId)
                ) { session, laps, insights ->
                    val bestLap = laps.find { it.isBestLap } ?: laps.minByOrNull { it.durationMs }
                    val consistencyScore = computeConsistencyScore(laps)
                    val processingStatus = session?.processingStatus?.let { 
                        try { ProcessingStatus.valueOf(it) } catch (e: Exception) { ProcessingStatus.PENDING }
                    } ?: ProcessingStatus.PENDING
                    
                    SessionUiState(
                        isLoading = false,
                        session = session,
                        laps = laps.sortedBy { it.lapNumber },
                        insights = insights,
                        bestLap = bestLap,
                        consistencyScore = consistencyScore,
                        processingStatus = processingStatus
                    )
                }.collect { state ->
                    _uiState.value = state
                    
                    // Start or stop polling based on processing status
                    when (state.processingStatus) {
                        ProcessingStatus.PENDING,
                        ProcessingStatus.UPLOADING,
                        ProcessingStatus.DETECTING_LAPS,
                        ProcessingStatus.GENERATING_COACHING -> startPolling()
                        ProcessingStatus.COMPLETE,
                        ProcessingStatus.FAILED -> stopPolling()
                    }
                }
            } catch (e: Exception) {
                _uiState.value = SessionUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load session"
                )
            }
        }
    }
    
    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(5000) // Poll every 5 seconds
                refreshSession()
            }
        }
    }
    
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
    
    private suspend fun refreshSession() {
        // The combine flow will automatically pick up changes from Room
        // This is just a placeholder if we need to sync from remote
    }
    
    fun retryAnalysis() {
        viewModelScope.launch {
            try {
                // Reset processing status to PENDING
                sessionDao.updateProcessingStatus(sessionId, ProcessingStatus.PENDING.name)
                
                // Re-enqueue the upload worker
                workManager.enqueue(TelemetryUploadWorker.buildRequest(sessionId))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to retry: ${e.message}"
                )
            }
        }
    }

    private fun computeConsistencyScore(laps: List<LapEntity>): Float {
        if (laps.size < 2) return 100f
        
        val durations = laps.map { it.durationMs.toDouble() }
        val mean = durations.average()
        val variance = durations.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        
        // Consistency = (1 - coefficient of variation) * 100
        // Clamped between 0 and 100
        val cv = stdDev / mean
        return ((1 - cv) * 100).coerceIn(0.0, 100.0).toFloat()
    }
    
    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
