package com.drivingcoach.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L1 (ASPICE SWE.4) guard for **finding F4 of Incident 12** and **SRS TS-21**.
 *
 * Start-line capture had no freshness check anywhere on its path: the fused client may answer
 * a new subscription with a cached fix, the screen held the last fix it ever saw, and the
 * readiness flag never went back down. A position recorded in the paddock could therefore
 * become Point A at the track edge, silently offsetting every lap time of the session — the
 * kind of error that is never noticed because the numbers still look like lap times.
 *
 * The arithmetic lives here, apart from the fragment, because `android.location.Location` is
 * a stub on the JVM and a rule this consequential should be provable without a device.
 */
class FixFreshnessTest {

    @Test
    fun `a fix taken now is fresh`() {
        val now = 10_000_000_000L
        assertTrue(FixFreshness.isFresh(fixElapsedRealtimeNanos = now, nowElapsedRealtimeNanos = now))
    }

    @Test
    fun `a fix just inside the age limit is still fresh`() {
        val now = 10_000_000_000L
        val fix = now - millisToNanos(FixFreshness.MAX_FIX_AGE_MS - 1)

        assertTrue(FixFreshness.isFresh(fix, now))
    }

    @Test
    fun `a fix past the age limit is rejected`() {
        val now = 10_000_000_000L
        val fix = now - millisToNanos(FixFreshness.MAX_FIX_AGE_MS + 1)

        assertFalse(FixFreshness.isFresh(fix, now))
    }

    /**
     * The scenario from the incident: the user acquires in the paddock, walks to the line,
     * and the screen is still holding the paddock position.
     */
    @Test
    fun `a fix from the paddock walk is rejected at the line`() {
        val now = 600_000_000_000L
        val paddockFix = now - millisToNanos(90_000)

        assertFalse(FixFreshness.isFresh(paddockFix, now))
        assertEquals(90_000L, FixFreshness.ageMs(paddockFix, now))
    }

    /**
     * Some devices, and every stubbed [android.location.Location], leave the monotonic
     * timestamp unset. Refusing to capture there would block the user outright, which is a
     * worse failure than the one being prevented — the accuracy gate still applies. So an
     * unknown age is treated as fresh, deliberately and visibly.
     */
    @Test
    fun `an unknown timestamp is treated as fresh rather than blocking capture`() {
        assertTrue(FixFreshness.isFresh(fixElapsedRealtimeNanos = 0L, nowElapsedRealtimeNanos = 600_000_000_000L))
    }

    /**
     * A fix stamped in the future can only come from a clock that moved under us. Reading it
     * as a negative age and rejecting it would strand the user; it is accepted, and the
     * accuracy gate remains the backstop.
     */
    @Test
    fun `a fix stamped in the future is not rejected`() {
        val now = 10_000_000_000L

        assertTrue(FixFreshness.isFresh(now + millisToNanos(5_000), now))
    }

    private fun millisToNanos(ms: Long): Long = ms * 1_000_000L
}
