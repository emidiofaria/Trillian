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
 *
 * ### Subscription continuity (Incident 12)
 *
 * [everReachedZero] records whether the chip was ever released *during* a scenario, not just
 * whether it is held at the end of one. A subscription torn down and instantly re-created
 * leaves `activeSubscriptions == 1` behind it, so counting live subscriptions afterwards
 * cannot detect the defect that caused Incident 12.
 *
 * ### Acquisition latency
 *
 * With [acquisitionLatencyMs] set, fixes pushed before the current subscription has been
 * alive that long are discarded, as a cold receiver would discard them. This is what lets a
 * test express the *cost* of dropping a warm subscription rather than merely the fact of it.
 * Drive it from `runTest`'s virtual clock by passing `now = { testScheduler.currentTime }`.
 */
class FakeLocationUpdates(
    var permissionGranted: Boolean = true,
    private val now: () -> Long = { 0L }
) : LocationUpdates {

    private val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)

    /** Total subscriptions ever made. */
    var subscribeCount = 0
        private set

    /** Subscriptions currently open. Non-zero means the GNSS chip is being paid for. */
    var activeSubscriptions = 0
        private set

    /** True once the chip has been released at any point after first being acquired. */
    var everReachedZero = false
        private set

    /**
     * How long the *receiver* must have been continuously subscribed before it can deliver a
     * fix. Scoped to the receiver rather than to an individual subscription, because a chip
     * held warm by one subscriber answers a second subscriber immediately.
     */
    var acquisitionLatencyMs: Long = 0L

    /** Fixes discarded because the receiver was still acquiring. */
    var fixesDroppedWhileAcquiring = 0
        private set

    private var warmedAtMs: Long = 0L

    var requestedIntervalMs: Long? = null
        private set

    /**
     * True if the GNSS subscription was held continuously for the whole scenario — the
     * property Incident 12 broke.
     */
    val subscriptionHeldContinuously: Boolean
        get() = subscribeCount > 0 && !everReachedZero

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
                if (activeSubscriptions == 0) warmedAtMs = now()
                subscribeCount++
                activeSubscriptions++
            }
            .onCompletion {
                activeSubscriptions--
                if (activeSubscriptions <= 0) {
                    everReachedZero = true
                    warmedAtMs = 0L
                }
            }
    }

    /**
     * Pushes a fix to whoever is collecting. No-op if nobody is, and discarded while the
     * receiver is still acquiring.
     */
    fun emitFix(accuracyM: Float, timestampMs: Long = 0L) {
        if (acquisitionLatencyMs > 0L &&
            (warmedAtMs == 0L || now() - warmedAtMs < acquisitionLatencyMs)
        ) {
            fixesDroppedWhileAcquiring++
            return
        }
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
