package com.drivingcoach.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.entity.LapEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SectorDelta(
    val sectorNumber: Int,
    val selectedMs: Long,
    val bestMs: Long,
    val deltaMs: Long
)

data class LapDetailUiState(
    val isLoading: Boolean = true,
    val selectedLap: LapEntity? = null,
    val bestLap: LapEntity? = null,
    val sectorDeltas: List<SectorDelta> = emptyList(),
    val totalDeltaMs: Long = 0,
    val error: String? = null
)

@HiltViewModel
class LapDetailViewModel @Inject constructor(
    private val lapDao: LapDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val lapId: Long = savedStateHandle.get<Long>("lapId") ?: 0L

    private val _uiState = MutableStateFlow(LapDetailUiState())
    val uiState: StateFlow<LapDetailUiState> = _uiState.asStateFlow()

    init {
        loadLapDetails()
    }

    private fun loadLapDetails() {
        viewModelScope.launch {
            try {
                // Get the selected lap
                val selectedLap = findLapById(lapId)
                
                if (selectedLap == null) {
                    _uiState.value = LapDetailUiState(
                        isLoading = false,
                        error = "Lap not found"
                    )
                    return@launch
                }

                // Get all laps for this session to find the best
                val allLaps = lapDao.getLapsForSession(selectedLap.sessionId).first()
                val bestLap = allLaps.find { it.isBestLap } 
                    ?: allLaps.minByOrNull { it.durationMs }
                    ?: selectedLap

                // Calculate sector deltas
                val sectorDeltas = listOf(
                    SectorDelta(
                        sectorNumber = 1,
                        selectedMs = selectedLap.sector1Ms,
                        bestMs = bestLap.sector1Ms,
                        deltaMs = selectedLap.sector1Ms - bestLap.sector1Ms
                    ),
                    SectorDelta(
                        sectorNumber = 2,
                        selectedMs = selectedLap.sector2Ms,
                        bestMs = bestLap.sector2Ms,
                        deltaMs = selectedLap.sector2Ms - bestLap.sector2Ms
                    ),
                    SectorDelta(
                        sectorNumber = 3,
                        selectedMs = selectedLap.sector3Ms,
                        bestMs = bestLap.sector3Ms,
                        deltaMs = selectedLap.sector3Ms - bestLap.sector3Ms
                    )
                )

                val totalDeltaMs = selectedLap.durationMs - bestLap.durationMs

                _uiState.value = LapDetailUiState(
                    isLoading = false,
                    selectedLap = selectedLap,
                    bestLap = bestLap,
                    sectorDeltas = sectorDeltas,
                    totalDeltaMs = totalDeltaMs
                )
            } catch (e: Exception) {
                _uiState.value = LapDetailUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load lap details"
                )
            }
        }
    }

    private suspend fun findLapById(lapId: Long): LapEntity? {
        // We need to query by lap ID directly - add this method to LapDao
        return lapDao.getLapById(lapId)
    }
}
