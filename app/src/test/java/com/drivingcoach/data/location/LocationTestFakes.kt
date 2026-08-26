package com.drivingcoach.data.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription

/**
 * Scripted [LocationUpdates] for JVM tests.
 *
 * Counts subscriptions as well as fixes, because two of the behaviours that matter most —
 * `start()` being idempotent and the idle ceiling actually releasing the chip — are only
 * observable as subscribe/unsubscribe events, not as state changes.
 */
class FakeLocationUpdates(
    var permissionGranted: Boolean = true
) : LocationUpdates {

    private val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)

    /** Total subscriptions ever made. */
    var subscribeCount = 0
        private set

    /** Subscriptions currently open. Non-zero means the GNSS chip is being paid for. */
    var activeSubscriptions = 0
        private set

    var requestedIntervalMs: Long? = null
        private set

    override fun hasPermission(): Boolean = permissionGranted

    /**
     * Unsupported on the JVM: `android.location.Location` is a stub there, so a fake fix
     * would report accuracy 0 and quietly make every readiness assertion meaningless. The
     * position path is covered by instrumented tests instead.
     */
    override fun positionUpdates(intervalMs: Long): Flow<android.location.Location> =
        throw UnsupportedOperationException("Position updates are instrumented-test only")

    override fun updates(intervalMs: Long): Flow<LocationFix> {
        requestedIntervalMs = intervalMs
        return fixes
            .onSubscription {
                subscribeCount++
                activeSubscriptions++
            }
            .onCompletion { activeSubscriptions-- }
    }

    /** Pushes a fix to whoever is collecting. No-op if nobody is. */
    fun emitFix(accuracyM: Float, timestampMs: Long = 0L) {
        fixes.tryEmit(LocationFix(accuracyM, timestampMs))
    }
}

/**
 * In-memory [DataStore] that actually retains what is written.
 *
 * The androidTest fakes are either stalling or read-only; asserting that acquisition
 * metrics are persisted needs a store that round-trips.
 */
class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences()
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
