package com.drivingcoach.data.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit coverage for the GPS warm-up state machine (SRS TS-16, TS-17, TS-18, TS-19, TS-20).
 *
 * The idle ceiling is three real minutes, so every timing assertion here runs on virtual
 * time. Without that this suite would either take minutes or, worse, be written with sleeps
 * and quietly become flaky.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationWarmUpTest {

    private val timings = WarmUpTimings(intervalMs = 1_000L, idleCeilingMs = 180_000L)

    /*
     * These tests use runCurrent() rather than advanceUntilIdle() on purpose:
     * advanceUntilIdle() would run the pending idle-ceiling delay to completion, silently
     * stopping the warm-up before any assertion about it could be made. Virtual time is
     * only advanced where a test is deliberately exercising the ceiling.
     */

    private fun TestScope.createWarmUp(
        locationUpdates: FakeLocationUpdates = FakeLocationUpdates(),
        dataStore: InMemoryPreferencesDataStore = InMemoryPreferencesDataStore()
    ): LocationWarmUp = LocationWarmUp(
        locationUpdates = locationUpdates,
        metricsStore = GpsAcquisitionMetricsStore(dataStore, StandardTestDispatcher(testScheduler)),
        timings = timings,
        // A scope on the test scheduler, not the test scope itself: the warm-up collects an
        // endless flow, which would otherwise keep runTest from ever completing.
        scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        now = { testScheduler.currentTime }
    )

    // --- Subscription lifecycle ---

    @Test
    fun `start subscribes and reports acquiring`() = runTest {
        val updates = FakeLocationUpdates()
        val warmUp = createWarmUp(updates)

        warmUp.start()
        runCurrent()

        assertEquals(GpsReadiness.Acquiring, warmUp.readiness.value)
        assertEquals(1, updates.subscribeCount)
        assertEquals(1, updates.activeSubscriptions)
        assertEquals(timings.intervalMs, updates.requestedIntervalMs)
    }

    @Test
    fun `start without permission does nothing`() = runTest {
        val updates = FakeLocationUpdates(permissionGranted = false)
        val warmUp = createWarmUp(updates)

        warmUp.start()
        runCurrent()

        assertEquals(GpsReadiness.Idle, warmUp.readiness.value)
        assertEquals(0, updates.subscribeCount)
    }

    @Test
    fun `repeated start does not resubscribe`() = runTest {
        val updates = FakeLocationUpdates()
        val warmUp = createWarmUp(updates)

        warmUp.start()
        runCurrent()
        warmUp.start()
        warmUp.start()
        runCurrent()

        // Navigating between screens must not throw away a fix already acquired.
        assertEquals(1, updates.subscribeCount)
    }

    @Test
    fun `stop releases the chip`() = runTest {
        val updates = FakeLocationUpdates()
        val warmUp = createWarmUp(updates)

        warmUp.start()
        runCurrent()
        warmUp.stop()
        runCurrent()

        assertEquals(GpsReadiness.Idle, warmUp.readiness.value)
        assertEquals(0, updates.activeSubscriptions)
    }

    @Test
    fun `stop before start is harmless`() = runTest {
        val updates = FakeLocationUpdates()
        val warmUp = createWarmUp(updates)

        warmUp.stop()
        runCurrent()

        assertEquals(GpsReadiness.Idle, warmUp.readiness.value)
        assertEquals(0, updates.subscribeCount)
    }

    // --- Readiness thresholds ---

    @Test
    fun `coarse fix stays acquiring`() = runTest {
        val updates = FakeLocationUpdates()
        val warmUp = createWarmUp(updates)

        warmUp.start()
        runCurrent()
        updates.emitFix(accuracyM = 25f)
        runCurrent()

        assertEquals(GpsReadiness.Acquiring, warmUp.readiness.value)
    }

    @Test
    fun `fix at the threshold is ready`() = runTest {
        val updates = FakeLocationUpdates()
        val warmUp = createWarmUp(updates)

        warmUp.start()
        runCurrent()
        updates.emitFix(accuracyM = GpsReadiness.READY_ACCURACY_M)
        runCurrent()

        assertEquals(
            GpsReadiness.Ready(GpsReadiness.READY_ACCURACY_M),
            warmUp.readiness.value
        )
    }

    @Test
    fun `readiness degrades again if accuracy worsens`() = runTest {
        val updates = FakeLocationUpdates()
        val warmUp = createWarmUp(updates)

        warmUp.start()
        runCurrent()
        updates.emitFix(accuracyM = 4f)
        runCurrent()
        assertEquals(GpsReadiness.Ready(4f), warmUp.readiness.value)

        // Walking under a grandstand must not leave a stale green indicator telling the
        // user the chip is still good enough to place a start line.
        updates.emitFix(accuracyM = 40f)
        runCurrent()

        assertEquals(GpsReadiness.Acquiring, warmUp.readiness.value)
    }

    // --- Idle ceiling ---

    @Test
    fun `idle ceiling releases the chip`() = runTest {
        val updates = FakeLocationUpdates()
        val warmUp = createWarmUp(updates)

        warmUp.start()
        runCurrent()
        advanceTimeBy(timings.idleCeilingMs + 1)
        runCurrent()

        assertEquals(GpsReadiness.Idle, warmUp.readiness.value)
        assertEquals(0, updates.activeSubscriptions)
    }

    @Test
    fun `warm-up survives right up to the ceiling`() = runTest {
        val updates = FakeLocationUpdates()
        val warmUp = createWarmUp(updates)

        warmUp.start()
        runCurrent()
        advanceTimeBy(timings.idleCeilingMs - 1)
        runCurrent()

        assertEquals(1, updates.activeSubscriptions)
    }

    @Test
    fun `start refreshes the ceiling`() = runTest {
        val updates = FakeLocationUpdates()
        val warmUp = createWarmUp(updates)

        warmUp.start()
        runCurrent()
        advanceTimeBy(timings.idleCeilingMs / 2)

        // Returning to Home restarts the clock without restarting the subscription.
        warmUp.start()
        advanceTimeBy(timings.idleCeilingMs / 2 + 1)
        runCurrent()

        assertEquals(1, updates.subscribeCount)
        assertEquals(1, updates.activeSubscriptions)
    }

    @Test
    fun `warm-up can be restarted after the ceiling fires`() = runTest {
        val updates = FakeLocationUpdates()
        val warmUp = createWarmUp(updates)

        warmUp.start()
        runCurrent()
        advanceTimeBy(timings.idleCeilingMs + 1)
        runCurrent()

        warmUp.start()
        runCurrent()

        assertEquals(2, updates.subscribeCount)
        assertEquals(GpsReadiness.Acquiring, warmUp.readiness.value)
    }

    // --- Acquisition metrics (SRS TS-19) ---

    @Test
    fun `first fix time is recorded`() = runTest {
        val updates = FakeLocationUpdates()
        val dataStore = InMemoryPreferencesDataStore()
        val metrics = GpsAcquisitionMetricsStore(dataStore, StandardTestDispatcher(testScheduler))
        val warmUp = LocationWarmUp(
            updates,
            metrics,
            timings,
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            { testScheduler.currentTime }
        )

        warmUp.start()
        runCurrent()
        advanceTimeBy(7_000)
        updates.emitFix(accuracyM = 30f)
        runCurrent()

        val recorded = metrics.lastAcquisition.first()
        assertNotNull(recorded)
        assertEquals(7_000L, recorded!!.timeToFirstFixMs)
        // 30 m is not accurate enough to place a line, so that milestone is still open.
        assertNull(recorded.timeToAccurateFixMs)
    }

    @Test
    fun `first fix and accurate fix are recorded separately`() = runTest {
        val updates = FakeLocationUpdates()
        val dataStore = InMemoryPreferencesDataStore()
        val metrics = GpsAcquisitionMetricsStore(dataStore, StandardTestDispatcher(testScheduler))
        val warmUp = LocationWarmUp(
            updates,
            metrics,
            timings,
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            { testScheduler.currentTime }
        )

        warmUp.start()
        runCurrent()
        advanceTimeBy(5_000)
        updates.emitFix(accuracyM = 30f)
        runCurrent()
        advanceTimeBy(20_000)
        updates.emitFix(accuracyM = 6f)
        runCurrent()

        val recorded = metrics.lastAcquisition.first()!!
        assertEquals(5_000L, recorded.timeToFirstFixMs)
        assertEquals(25_000L, recorded.timeToAccurateFixMs)
    }

    @Test
    fun `milestones are recorded once, not on every fix`() = runTest {
        val updates = FakeLocationUpdates()
        val dataStore = InMemoryPreferencesDataStore()
        val metrics = GpsAcquisitionMetricsStore(dataStore, StandardTestDispatcher(testScheduler))
        val warmUp = LocationWarmUp(
            updates,
            metrics,
            timings,
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            { testScheduler.currentTime }
        )

        warmUp.start()
        runCurrent()
        advanceTimeBy(4_000)
        updates.emitFix(accuracyM = 5f)
        runCurrent()

        // A 1 Hz stream must not turn into a 1 Hz write stream.
        advanceTimeBy(30_000)
        updates.emitFix(accuracyM = 4f)
        updates.emitFix(accuracyM = 3f)
        runCurrent()

        val recorded = metrics.lastAcquisition.first()!!
        assertEquals(4_000L, recorded.timeToFirstFixMs)
        assertEquals(4_000L, recorded.timeToAccurateFixMs)
    }

    @Test
    fun `a new warm-up measures from its own subscribe`() = runTest {
        val updates = FakeLocationUpdates()
        val dataStore = InMemoryPreferencesDataStore()
        val metrics = GpsAcquisitionMetricsStore(dataStore, StandardTestDispatcher(testScheduler))
        val warmUp = LocationWarmUp(
            updates,
            metrics,
            timings,
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            { testScheduler.currentTime }
        )

        warmUp.start()
        runCurrent()
        advanceTimeBy(9_000)
        updates.emitFix(accuracyM = 5f)
        runCurrent()

        warmUp.stop()
        advanceTimeBy(60_000)
        warmUp.start()
        runCurrent()
        advanceTimeBy(2_000)
        updates.emitFix(accuracyM = 5f)
        runCurrent()

        // Elapsed time is relative to the second subscribe, not the first — otherwise a
        // warm second launch would look worse than the cold one that preceded it.
        val recorded = metrics.lastAcquisition.first()!!
        assertEquals(2_000L, recorded.timeToFirstFixMs)
        assertEquals(2_000L, recorded.timeToAccurateFixMs)
    }

    @Test
    fun `no metrics are recorded before any fix arrives`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val metrics = GpsAcquisitionMetricsStore(dataStore, StandardTestDispatcher(testScheduler))
        val warmUp = LocationWarmUp(
            FakeLocationUpdates(),
            metrics,
            timings,
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            { testScheduler.currentTime }
        )

        warmUp.start()
        runCurrent()

        assertNull(metrics.lastAcquisition.first())
    }

    @Test
    fun `readiness is observable as a stream`() = runTest {
        val updates = FakeLocationUpdates()
        val warmUp = createWarmUp(updates)

        assertEquals(GpsReadiness.Idle, warmUp.readiness.first())

        warmUp.start()
        runCurrent()
        updates.emitFix(accuracyM = 3.5f)
        runCurrent()

        val ready = warmUp.readiness.first()
        assertTrue(ready is GpsReadiness.Ready)
        assertEquals(3.5f, (ready as GpsReadiness.Ready).accuracyM, 0.001f)
    }
}
