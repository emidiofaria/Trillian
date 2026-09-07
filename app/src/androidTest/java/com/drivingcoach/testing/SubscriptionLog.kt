package com.drivingcoach.testing

/**
 * A timeline of subscribe/unsubscribe events against a location source.
 *
 * ### Why a timeline rather than a counter
 *
 * Incident 12 was a subscription that was torn down and immediately re-created while the
 * user navigated Home → Track Setup. Any assertion sampled *after* that navigation sees one
 * live subscription and passes — which is exactly what `TrackSetupResubscribeTest` did, and
 * exactly why the defect reached human acceptance testing. The interesting event happens
 * *between* two observations, so the double has to remember it rather than be asked about
 * the present.
 *
 * [everReachedZero] is therefore the assertion that matters: it is false only if the GNSS
 * subscription was continuously held for the whole scenario.
 */
class SubscriptionLog {

    sealed interface Event {
        val atMs: Long

        data class Subscribed(override val atMs: Long, val active: Int) : Event
        data class Unsubscribed(override val atMs: Long, val active: Int) : Event
    }

    private val _events = mutableListOf<Event>()

    /** Snapshot of the timeline. Safe to read from the test thread. */
    val events: List<Event>
        @Synchronized get() = _events.toList()

    /** Total subscriptions ever opened. */
    @Volatile
    var subscribeCount = 0
        private set

    /** Subscriptions currently open. Non-zero means the GNSS chip is being paid for. */
    @Volatile
    var activeSubscriptions = 0
        private set

    /**
     * True once the number of live subscriptions has fallen to zero at any point after the
     * first subscription — i.e. the chip was released and any warmth was thrown away.
     */
    @Volatile
    var everReachedZero = false
        private set

    @Synchronized
    fun recordSubscribe(atMs: Long) {
        subscribeCount++
        activeSubscriptions++
        _events += Event.Subscribed(atMs, activeSubscriptions)
    }

    @Synchronized
    fun recordUnsubscribe(atMs: Long) {
        activeSubscriptions--
        if (activeSubscriptions <= 0) everReachedZero = true
        _events += Event.Unsubscribed(atMs, activeSubscriptions)
    }

    /**
     * Clears the timeline while leaving [activeSubscriptions] alone.
     *
     * Live subscriptions outlive a reset — they are held by the app, not by this log — so
     * zeroing the count here would make the next unsubscribe drive it negative and report a
     * release that never happened.
     */
    @Synchronized
    fun reset() {
        _events.clear()
        subscribeCount = 0
        everReachedZero = false
    }

    /** Renders the timeline for assertion failure messages. */
    @Synchronized
    fun describe(): String =
        if (_events.isEmpty()) {
            "no subscription events"
        } else {
            _events.joinToString(prefix = "\n  ", separator = "\n  ") { event ->
                when (event) {
                    is Event.Subscribed -> "+${event.atMs}ms subscribe   (active=${event.active})"
                    is Event.Unsubscribed -> "+${event.atMs}ms unsubscribe (active=${event.active})"
                }
            }
        }
}
