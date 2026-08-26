package com.drivingcoach.testing

import android.location.Location
import com.drivingcoach.data.location.LocationUpdates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription

/**
 * A [LocationUpdates] driven by the test rather than by satellites.
 *
 * Emulators either have no fix at all or one pinned by `adb emu geo`, neither of which can
 * model "coarse fix now, accurate fix later" — the exact sequence the GPS chip and the
 * capture gate are built around. Scripting the stream also removes the single largest source
 * of flakiness from these tests.
 *
 * Tracks subscribe/unsubscribe counts because the behaviour under test on Track Setup is
 * precisely that updates are *resubscribed* after the screen stops.
 */
class ScriptedLocationUpdates(
    @Volatile var permissionGranted: Boolean = true
) : LocationUpdates {

    private val positions = MutableSharedFlow<Location>(extraBufferCapacity = 16)

    @Volatile
    var subscribeCount = 0
        private set

    @Volatile
    var activeSubscriptions = 0
        private set

    override fun hasPermission(): Boolean = permissionGranted

    override fun positionUpdates(intervalMs: Long): Flow<Location> = positions
        .onSubscription {
            subscribeCount++
            activeSubscriptions++
        }
        .onCompletion { activeSubscriptions-- }

    /** Pushes one fix. No-op if nothing is collecting. */
    fun emit(accuracyM: Float, latitude: Double = 48.1, longitude: Double = 11.5) {
        positions.tryEmit(
            Location("scripted").apply {
                this.latitude = latitude
                this.longitude = longitude
                this.accuracy = accuracyM
                this.time = System.currentTimeMillis()
            }
        )
    }
}
