package com.drivingcoach.ui.tracklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.data.track.Track
import com.drivingcoach.data.track.TrackRepository
import com.drivingcoach.ui.tracksetup.TrackSetupState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class TrackConfirmState(
    val trackName: String = "",
    val location: String? = null,
    val startLineSummary: String = "",
    val facts: String = "",
    val gpsAccuracy: Float = Float.MAX_VALUE,
    val satelliteCount: Int = 0,
    val isGpsReady: Boolean = false,
    val isAwaitingFreshFix: Boolean = false,
    val trackLoaded: Boolean = false
) {
    /**
     * Recording starts only once the circuit resolved and the receiver is warm.
     *
     * Choosing a saved circuit skips Track Setup, and Track Setup was where the
     * readiness gate lived. Without this the driver would reach the track screen
     * faster and start a session on a cold fix, which is a worse trade than the
     * one the shortcut was meant to make.
     */
    val canStart: Boolean get() = trackLoaded && isGpsReady
}

@HiltViewModel
class TrackConfirmViewModel @Inject constructor(
    private val trackRepository: TrackRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TrackConfirmState())
    val state: StateFlow<TrackConfirmState> = _state.asStateFlow()

    private var track: Track? = null

    fun load(trackId: String) {
        if (track != null) return
        viewModelScope.launch {
            val loaded = trackRepository.getTrack(trackId) ?: return@launch
            track = loaded
            _state.update { current ->
                current.copy(
                    trackName = loaded.name,
                    location = loaded.location,
                    startLineSummary = String.format(
                        Locale.US, "Already set · %.1f m wide", loaded.startLineLengthM()
                    ),
                    facts = factsFor(loaded),
                    trackLoaded = true
                )
            }
        }
    }

    fun updateGpsStatus(accuracy: Float, satelliteCount: Int) {
        _state.update { current ->
            current.copy(
                gpsAccuracy = accuracy,
                satelliteCount = satelliteCount,
                isGpsReady = accuracy <= TrackSetupState.MAX_GPS_ACCURACY_M && satelliteCount >= 4,
                isAwaitingFreshFix = false
            )
        }
    }

    /** Same wait, not block, as on Track Setup: a current fix clears it by itself. */
    fun markAwaitingFreshFix() {
        _state.update { it.copy(isGpsReady = false, isAwaitingFreshFix = true) }
    }

    private fun factsFor(track: Track): String = listOfNotNull(
        track.lengthM?.let { "$it m" },
        track.cornerCount?.let { "$it corners" },
        lapRange(track)
    ).joinToString(" · ")

    private fun lapRange(track: Track): String? {
        val fastest = track.fastestLapMs ?: return null
        val slowest = track.slowestLapMs ?: return null
        return "laps around ${formatLap(fastest)}–${formatLap(slowest)}"
    }

    private fun formatLap(ms: Long): String =
        String.format(Locale.US, "%d:%04.1f", ms / 60_000, (ms % 60_000) / 1000.0)
}
