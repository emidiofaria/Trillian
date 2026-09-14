package com.drivingcoach.testing

import android.location.Location
import android.os.SystemClock
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
 *
 * ### Subscription continuity (Incident 12)
 *
 * Bookkeeping is delegated to a [SubscriptionLog] so tests can assert on what happened
 * *during* a scenario rather than on what is true at the end of it. A subscription that is
 * dropped and instantly re-created is indistinguishable from one that was never dropped if
 * you only count live subscriptions afterwards — which is what the original resubscribe test
 * did, and why Incident 12 reached human acceptance testing.
 * [SubscriptionLog.everReachedZero] is the assertion that tells the two apart.
 *
 * ### Acquisition latency
 *
 * Real GNSS does not answer the instant you subscribe, and a double that does cannot express
 * the cost of throwing a warm subscription away — the entire user-visible harm in Incident
 * 12. With [acquisitionLatencyMs] set, fixes pushed before the current subscription has been
 * alive that long are discarded exactly as a cold receiver would discard them, and counted in
 * [fixesDroppedWhileAcquiring]. A re-subscription pays the cost again, which is the point.
 *
 * Defaults to zero so existing tests keep their instantaneous behaviour.
 */
class ScriptedLocationUpdates(
    @Volatile var permissionGranted: Boolean = true,
    private val now: () -> Long = { SystemClock.elapsedRealtime() }
) : LocationUpdates {

    private val positions = MutableSharedFlow<Location>(extraBufferCapacity = 16)

    val log = SubscriptionLog()

    /**
     * How long the *receiver* must have been continuously subscribed before it can deliver
     * its first fix. Models cold time-to-first-fix.
     *
     * Scoped to the receiver rather than to an individual subscription, because that is how
     * GNSS actually behaves: a chip held warm by one subscriber answers a second subscriber
     * immediately. Modelling it per-subscription would make the fix for Incident 12 look
     * like it had failed, since Track Setup always opens a subscription of its own.
     */
    @Volatile
    var acquisitionLatencyMs: Long = 0L

    /** Fixes discarded because the receiver was still acquiring. */
    @Volatile
    var fixesDroppedWhileAcquiring = 0
        private set

    /** When the receiver last went from released to held. Zero means released. */
    @Volatile
    private var warmedAtMs: Long = 0L

    private val originMs: Long = now()

    val subscribeCount: Int get() = log.subscribeCount

    val activeSubscriptions: Int get() = log.activeSubscriptions

    /**
     * True if the GNSS subscription was held continuously across the whole scenario — the
     * property Incident 12 broke.
     */
    val subscriptionHeldContinuously: Boolean
        get() = log.subscribeCount > 0 && !log.everReachedZero

    override fun hasPermission(): Boolean = permissionGranted

    override fun positionUpdates(intervalMs: Long): Flow<Location> = positions
        .onSubscription {
            synchronized(this@ScriptedLocationUpdates) {
                if (log.activeSubscriptions == 0) warmedAtMs = now()
                log.recordSubscribe(now() - originMs)
            }
        }
        .onCompletion {
            synchronized(this@ScriptedLocationUpdates) {
                log.recordUnsubscribe(now() - originMs)
                if (log.activeSubscriptions <= 0) warmedAtMs = 0L
            }
        }

    /**
     * Pushes one fix. No-op if nothing is collecting, and discarded while the receiver is
     * still acquiring.
     */
    fun emit(accuracyM: Float, latitude: Double = 48.1, longitude: Double = 11.5) {
        if (acquisitionLatencyMs > 0L) {
            val warmedAt = warmedAtMs
            if (warmedAt == 0L || now() - warmedAt < acquisitionLatencyMs) {
                fixesDroppedWhileAcquiring++
                return
            }
        }

        positions.tryEmit(
            Location("scripted").apply {
                this.latitude = latitude
                this.longitude = longitude
                this.accuracy = accuracyM
                this.time = System.currentTimeMillis()
                this.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
        )
    }

    /**
     * Pushes a fix that is deliberately stale, for the start-line freshness gate (F4).
     *
     * The coordinate is plausible and the accuracy is good; only the age is wrong. That is
     * precisely what makes a stale fix dangerous — nothing about the value itself looks
     * suspicious, so only an explicit age check can reject it.
     */
    fun emitStale(
        accuracyM: Float,
        ageMs: Long,
        latitude: Double = 48.1,
        longitude: Double = 11.5
    ) {
        positions.tryEmit(
            Location("scripted").apply {
                this.latitude = latitude
                this.longitude = longitude
                this.accuracy = accuracyM
                this.time = System.currentTimeMillis() - ageMs
                this.elapsedRealtimeNanos =
                    SystemClock.elapsedRealtimeNanos() - (ageMs * 1_000_000L)
            }
        )
    }

    /** Timeline rendering for assertion failure messages. */
    fun describeTimeline(): String = log.describe()
}
