package com.drivingcoach.ui.session.tabs.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.data.db.entity.LapEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Holds the computed analysis for the ANALYSIS tab.
 *
 * Session and lap data are already loaded by
 * [com.drivingcoach.ui.session.SessionResultViewModel]; this view model exists
 * only so that the expensive part - parsing the whole telemetry file and running
 * the corner and braking detection over it - survives a configuration change and
 * is not repeated every time the tab is scrolled back into view.
 */
@HiltViewModel
class AnalysisViewModel @Inject constructor() : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val analysis: SessionAnalysis? = null,
        val hasTelemetryFile: Boolean = true
    )

    private data class Inputs(
        val filePath: String?,
        val lapIds: List<Long>,
        val startLine: Pair<Double, Double>?
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var inputs: Inputs? = null
    private var laps: List<LapEntity> = emptyList()
    private var selectedLapId: Long? = null
    private var job: Job? = null

    /**
     * Supplies the session data. Recomputes only when the file or the set of
     * laps actually changed, because the parent view model re-emits its state
     * on every poll while a session is still processing.
     */
    fun submit(filePath: String?, laps: List<LapEntity>, startLine: Pair<Double, Double>?) {
        val next = Inputs(filePath, laps.map { it.id }, startLine)
        if (next == inputs) return
        inputs = next
        this.laps = laps
        selectedLapId = laps.firstOrNull { it.isBestLap }?.id ?: laps.minByOrNull { it.durationMs }?.id
        recompute()
    }

    /** Changes the reference lap the corners and braking zones relate to. */
    fun selectLap(lapId: Long) {
        if (lapId == selectedLapId) return
        selectedLapId = lapId
        recompute()
    }

    private fun recompute() {
        val current = inputs ?: return
        job?.cancel()
        _uiState.value = UiState(isLoading = true)

        job = viewModelScope.launch {
            // "Has telemetry" has to mean the file is actually readable, not merely
            // that the session row remembers a path: a session whose file was
            // cleaned up still carries its original path, and reporting that as
            // present would tell the driver their lap was too short to analyse
            // when in fact the recording is gone.
            val path = current.filePath
            val hasFile = withContext(Dispatchers.IO) {
                !path.isNullOrBlank() && File(path).canRead()
            }
            val analysis = SessionAnalysisProcessor.analyze(
                filePath = current.filePath,
                laps = laps.map { it.toLapOption() },
                referenceLapId = selectedLapId,
                lapWindows = laps.associate { it.id to it.startTs..it.endTs },
                startLine = current.startLine
            )
            _uiState.value = UiState(
                isLoading = false,
                analysis = analysis,
                hasTelemetryFile = hasFile
            )
        }
    }
}

internal fun LapEntity.toLapOption(): LapOption = LapOption(
    lapId = id,
    lapNumber = lapNumber,
    durationMs = durationMs,
    isBestLap = isBestLap
)
