package com.drivingcoach.ui.tracklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.data.track.Track
import com.drivingcoach.data.track.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackListItem(
    val id: String,
    val name: String,
    val details: String,
    val isBundled: Boolean
)

@HiltViewModel
class TrackListViewModel @Inject constructor(
    private val trackRepository: TrackRepository
) : ViewModel() {

    val tracks: StateFlow<List<TrackListItem>> = trackRepository.observeTracks()
        .map { tracks -> tracks.map(::toItem) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { trackRepository.rename(id, trimmed) }
    }

    fun delete(id: String) {
        viewModelScope.launch { trackRepository.delete(id) }
    }

    private fun toItem(track: Track): TrackListItem {
        // Whatever is known, in the order a driver would recognise the circuit by.
        // A saved circuit often has nothing but a line, and an empty second row reads
        // better than a row of placeholders.
        val details = listOfNotNull(
            track.location,
            track.lengthM?.let { "$it m" },
            track.cornerCount?.let { "$it corners" }
        ).joinToString(" · ")

        return TrackListItem(
            id = track.id,
            name = track.name,
            details = details,
            isBundled = track.isBundled
        )
    }
}
