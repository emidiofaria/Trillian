package com.drivingcoach.ui.tracksetup

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.util.GeoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State representing the track setup screen.
 */
data class TrackSetupState(
    val pointA: LatLng? = null,
    val pointB: LatLng? = null,
    val distance: Double = 0.0,
    val isValid: Boolean = false,
    val gpsAccuracy: Float = Float.MAX_VALUE,
    val satelliteCount: Int = 0,
    val isGpsReady: Boolean = false,
    /**
     * Set when the only fixes on hand are too old to say where the user is standing
     * (Incident 12, F4). Distinct from "not ready yet" so the screen can tell the user the
     * truth: the receiver is working, the position simply is not current.
     */
    val isAwaitingFreshFix: Boolean = false
) {
    companion object {
        const val MIN_LINE_DISTANCE_M = 3.0
        const val MAX_GPS_ACCURACY_M = 10.0f
    }
}

/**
 * Simple lat/lng holder.
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double
)

/**
 * ViewModel for track setup screen.
 * Manages capturing two GPS points to define a start/finish line.
 */
@HiltViewModel
class TrackSetupViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(TrackSetupState())
    val state: StateFlow<TrackSetupState> = _state.asStateFlow()

    /**
     * Updates GPS status from location updates.
     */
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

    /**
     * Withdraws capture while the held position is too old to be trusted (Incident 12, F4).
     *
     * This is a *wait*, not a block: [updateGpsStatus] clears it the moment a current fix
     * arrives, with no action from the user. Latching it off would trade a silent data error
     * for the frustration the incident was raised about.
     */
    fun markAwaitingFreshFix() {
        _state.update { current ->
            if (!current.isGpsReady && current.isAwaitingFreshFix) current
            else current.copy(isGpsReady = false, isAwaitingFreshFix = true)
        }
    }

    /**
     * Captures Point A from current GPS location.
     */
    fun setPointA(location: Location) {
        viewModelScope.launch {
            _state.update { current ->
                val pointA = LatLng(location.latitude, location.longitude)
                current.copy(
                    pointA = pointA,
                    // Clear Point B if A is re-captured
                    pointB = null,
                    distance = 0.0,
                    isValid = false
                )
            }
        }
    }

    /**
     * Captures Point B from current GPS location and calculates distance.
     */
    fun setPointB(location: Location) {
        viewModelScope.launch {
            val currentState = _state.value
            val pointA = currentState.pointA ?: return@launch

            val pointB = LatLng(location.latitude, location.longitude)
            val distance = GeoUtils.haversineDistance(
                pointA.latitude, pointA.longitude,
                pointB.latitude, pointB.longitude
            )

            _state.update {
                it.copy(
                    pointB = pointB,
                    distance = distance,
                    isValid = distance >= TrackSetupState.MIN_LINE_DISTANCE_M
                )
            }
        }
    }

    /**
     * Clears both captured points.
     */
    fun clear() {
        _state.update {
            it.copy(
                pointA = null,
                pointB = null,
                distance = 0.0,
                isValid = false
            )
        }
    }

    /**
     * Returns the start line coordinates if valid.
     */
    fun getStartLineCoords(): StartLineCoords? {
        val current = _state.value
        if (!current.isValid) return null

        val pointA = current.pointA ?: return null
        val pointB = current.pointB ?: return null

        return StartLineCoords(
            lat1 = pointA.latitude,
            lng1 = pointA.longitude,
            lat2 = pointB.latitude,
            lng2 = pointB.longitude
        )
    }
}

/**
 * Start line coordinates to pass to recording session.
 */
data class StartLineCoords(
    val lat1: Double,
    val lng1: Double,
    val lat2: Double,
    val lng2: Double
)
