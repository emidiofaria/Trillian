package com.drivingcoach.ui.session.tabs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.coaching.SectorRepair
import com.drivingcoach.data.telemetry.TelemetryFileReader
import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.data.track.CoachMap
import com.drivingcoach.data.track.SessionOutline
import com.drivingcoach.data.track.TrackRepository
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
 * Does the Coach tab's telemetry-derived work: the sector map, and the repair of
 * sector times measured before the ruler was anchored at the start/finish line.
 *
 * ### Why this is a separate view model
 *
 * The Coach tab has until now been a pure reader of text: insights are generated once
 * when the session is saved and stored in `coaching_insights`, and the fragment renders
 * strings. The map is the first thing on that tab that needs telemetry, which means
 * file I/O and a few hundred milliseconds of geometry.
 *
 * Keeping it here rather than in `SessionResultViewModel` means the LAPS and CHART tabs
 * do not pay for work they never show, and the insights still appear immediately while
 * the map arrives behind them.
 *
 * ### Why the sector repair rides along here
 *
 * [SectorRepair] needs the session's whole telemetry, which this view model has just
 * read in order to draw. Running it anywhere else would mean reading the same file a
 * second time on every session open, purely to keep a class name accurate. The name was
 * changed instead.
 *
 * The repair writes corrected sector times back to the database, so the LAPS tab - which
 * observes the same Room query - corrects itself without being told. That matters more
 * than it sounds: a repair that only affected the Coach tab would leave two tabs of the
 * same screen quoting different numbers for the same lap.
 *
 * ### Why the map is not stored
 *
 * It could be computed once at save time and persisted beside the insights, which would
 * remove this load entirely. It is derived data, though, and persisting it would add a
 * schema migration and a second source of truth that could drift from the telemetry it
 * came from. Telemetry only disappears when the whole session is deleted (SM-03), so
 * there is no case where the insights outlive the data the map needs.
 */
@HiltViewModel
class CoachTelemetryViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val sectorRepair: SectorRepair
) : ViewModel() {

    sealed interface MapState {
        /** Nothing requested yet, or telemetry is being read. */
        data object Loading : MapState

        /** There was nothing honest to draw. The map is hidden; insights are not. */
        data object Unavailable : MapState

        data class Ready(val drawing: CoachMap.Drawing) : MapState
    }

    private val _state = MutableStateFlow<MapState>(MapState.Loading)
    val state: StateFlow<MapState> = _state.asStateFlow()

    private var job: Job? = null
    private var loadedFor: Key? = null

    /** What a drawing depends on. Re-reading the file for the same inputs is wasted work. */
    private data class Key(val filePath: String?, val trackId: String?, val lapCount: Int)

    fun load(sessionId: Long, telemetryFilePath: String?, trackId: String?, laps: List<LapEntity>) {
        val key = Key(telemetryFilePath, trackId, laps.size)
        if (key == loadedFor) return
        loadedFor = key

        if (telemetryFilePath.isNullOrBlank() || laps.isEmpty()) {
            _state.value = MapState.Unavailable
            return
        }

        job?.cancel()
        job = viewModelScope.launch {
            _state.value = MapState.Loading

            val windows = laps
                .sortedBy { it.lapNumber }
                .map { SessionOutline.LapWindow(it.startTs, it.endTs) }

            // A catalogue circuit has a surveyed centreline, which is a better picture
            // than anything derivable from GPS. A circuit the driver captured has none,
            // and null is the ordinary case rather than an error.
            val centreline = trackId?.let { trackRepository.getTrack(it)?.centreline }

            val samples = withContext(Dispatchers.IO) {
                TelemetryFileReader.readAll(telemetryFilePath)
            }

            val drawing = withContext(Dispatchers.Default) {
                CoachMap.build(samples, windows, centreline)
            }

            // Drawn first. The map is what the driver is waiting for, and the repair
            // only writes to a database that will push its own update when it does.
            _state.value = drawing?.let { MapState.Ready(it) } ?: MapState.Unavailable

            repairSectors(sessionId, laps, samples, telemetryFilePath)
        }
    }

    /**
     * Corrects sector times measured before the ruler was anchored at the line.
     *
     * Failure here is swallowed deliberately. The repair is a correction to data the
     * driver is already looking at, and a session that will not open because its old
     * numbers could not be refreshed would be a far worse outcome than one showing the
     * numbers it has always shown.
     */
    private suspend fun repairSectors(
        sessionId: Long,
        laps: List<LapEntity>,
        samples: List<TelemetrySample>,
        telemetryFilePath: String
    ) {
        try {
            val outcome = withContext(Dispatchers.IO) {
                sectorRepair.repair(sessionId, laps, samples, File(telemetryFilePath))
            }
            if (outcome.changedAnything) {
                Log.d(TAG, "Session $sessionId: $outcome")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sector repair failed for session $sessionId", e)
        }
    }

    private companion object {
        private const val TAG = "CoachTelemetryVM"
    }
}
