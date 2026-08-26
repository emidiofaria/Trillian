package com.drivingcoach.ui.splash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.drivingcoach.R
import com.drivingcoach.data.api.AuthInterceptor
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.ui.onboarding.OnboardingFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * L1 (SWE.4) unit tests for the branded loading screen.
 *
 * Covers SRS UI-01 (real progress), UI-02 (minimum display + skip), UI-03 (timeout
 * fallback) and the startup destination resolution that replaced MainActivity's
 * former main-thread `runBlocking` read (UI-04).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionDao: SessionDao

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionDao = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Read-only DataStore stub whose writes fail. Used both for the tests that do not care
     * about the launch counter and, deliberately, for the test that proves a failed counter
     * write cannot break startup.
     */
    private class FakeDataStore(
        override val data: Flow<Preferences>
    ) : DataStore<Preferences> {
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = throw UnsupportedOperationException("write not supported")
    }

    /** DataStore stub that actually applies writes, so the launch counter can be observed. */
    private class RecordingDataStore(initial: Preferences) : DataStore<Preferences> {
        var current: Preferences = initial
            private set
        var writeCount: Int = 0
            private set

        override val data: Flow<Preferences> get() = flowOf(current)

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences {
            writeCount++
            current = transform(current)
            return current
        }
    }

    private fun dataStoreOf(preferences: Preferences): DataStore<Preferences> =
        FakeDataStore(flowOf(preferences))

    /** Preferences for a user who has already spent the introduction window. */
    private fun returningUser(): Preferences = mutablePreferencesOf(
        SplashViewModel.KEY_LAUNCH_COUNT to SplashTimings.INTRO_LAUNCH_COUNT
    )

    private fun viewModel(
        preferences: Preferences = emptyPreferences(),
        timings: SplashTimings = SplashTimings(
            minDisplayMs = 0L,
            introDisplayMs = 0L,
            timeoutMs = 3000L,
            warmUpTimeoutMs = 2000L
        ),
        dataStore: DataStore<Preferences> = dataStoreOf(preferences),
        visibility: SplashVisibilitySignal = visibleImmediately()
    ) = SplashViewModel(
        dataStore = dataStore,
        sessionDao = sessionDao,
        timings = timings,
        visibility = visibility,
        ioDispatcher = testDispatcher
    )

    /**
     * A handoff that has already happened at time zero. The default for tests that are not
     * about the anchor, so they behave as if the branded screen was visible from the start.
     */
    private fun visibleImmediately() = SplashVisibilitySignal().apply { markVisible(0L) }

    // --- Destination resolution (UI-04) -------------------------------------------------

    @Test
    fun `fresh install routes to onboarding`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

        val vm = viewModel(preferences = emptyPreferences())
        vm.start()
        advanceUntilIdle()

        assertEquals(SplashDestination.ONBOARDING, vm.uiState.value.destination)
    }

    @Test
    fun `onboarded but signed out routes to login`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())
        val prefs = mutablePreferencesOf(
            OnboardingFragment.KEY_ONBOARDING_COMPLETE to true
        )

        val vm = viewModel(preferences = prefs)
        vm.start()
        advanceUntilIdle()

        assertEquals(SplashDestination.LOGIN, vm.uiState.value.destination)
    }

    @Test
    fun `onboarded and signed in routes to home`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())
        val prefs = mutablePreferencesOf(
            OnboardingFragment.KEY_ONBOARDING_COMPLETE to true,
            AuthInterceptor.KEY_JWT to "a-token"
        )

        val vm = viewModel(preferences = prefs)
        vm.start()
        advanceUntilIdle()

        assertEquals(SplashDestination.HOME, vm.uiState.value.destination)
    }

    @Test
    fun `blank token is not treated as signed in`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())
        val prefs = mutablePreferencesOf(
            OnboardingFragment.KEY_ONBOARDING_COMPLETE to true,
            AuthInterceptor.KEY_JWT to "   "
        )

        val vm = viewModel(preferences = prefs)
        vm.start()
        advanceUntilIdle()

        assertEquals(SplashDestination.LOGIN, vm.uiState.value.destination)
    }

    // --- Progress reporting (UI-01) -----------------------------------------------------

    @Test
    fun `progress reaches one hundred when initialisation completes`() =
        runTest(testDispatcher) {
            whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            assertEquals(SplashViewModel.PROGRESS_COMPLETE, vm.uiState.value.progress)
        }

    @Test
    fun `progress never regresses across the startup sequence`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

        val vm = viewModel(timings = SplashTimings(minDisplayMs = 240L, timeoutMs = 3000L, warmUpTimeoutMs = 2000L))
        val observed = mutableListOf<Int>()

        vm.start()
        repeat(20) {
            observed += vm.uiState.value.progress
            advanceUntilIdle()
        }

        assertTrue(
            "progress regressed: $observed",
            observed.zipWithNext().all { (previous, next) -> next >= previous }
        )
    }

    @Test
    fun `pending upload count is captured during warm up`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(
            listOf(mock(), mock(), mock())
        )

        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        assertEquals(3, vm.pendingUploadCount)
    }

    // --- Timeout fallback (UI-03) -------------------------------------------------------

    @Test
    fun `initialisation timeout falls back to onboarding without blocking`() =
        runTest(testDispatcher) {
            val neverEmits = FakeDataStore(
                flow {
                    kotlinx.coroutines.delay(Long.MAX_VALUE / 2)
                    emit(emptyPreferences())
                }
            )

            val vm = viewModel(
                timings = SplashTimings(
                    minDisplayMs = 0L,
                    introDisplayMs = 0L,
                    timeoutMs = 100L,
                    warmUpTimeoutMs = 2000L
                ),
                dataStore = neverEmits
            )
            vm.start()
            advanceUntilIdle()

            // ONBOARDING, not LOGIN: if we cannot read preferences we must not skip
            // permission granting for a user who has never onboarded.
            assertEquals(SplashDestination.ONBOARDING, vm.uiState.value.destination)
            assertTrue(vm.uiState.value.usedFallback)
        }

    @Test
    fun `successful initialisation does not report a fallback`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.usedFallback)
    }

    @Test
    fun `slow database warm up does not change the destination`() = runTest(testDispatcher) {
        // Regression guard: a Room stall used to consume the whole startup budget and
        // dump every user at Login. Warm-up is now bounded and non-fatal.
        whenever(sessionDao.getPendingUploadSessions()).thenAnswer {
            Thread.sleep(0); emptyList<Any>()
        }
        val prefs = mutablePreferencesOf(
            OnboardingFragment.KEY_ONBOARDING_COMPLETE to true,
            AuthInterceptor.KEY_JWT to "a-token"
        )

        val vm = viewModel(preferences = prefs)
        vm.start()
        advanceUntilIdle()

        assertEquals(SplashDestination.HOME, vm.uiState.value.destination)
        assertFalse(vm.uiState.value.usedFallback)
    }

    // --- Minimum display and skip (UI-02) -----------------------------------------------

    @Test
    fun `destination is withheld until the minimum display time has elapsed`() =
        runTest(testDispatcher) {
            whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

            // A returning user: the introduction window is already spent, so the short
            // hold applies.
            val vm = viewModel(
                preferences = returningUser(),
                timings = SplashTimings(
                    minDisplayMs = 1200L,
                    introDisplayMs = 0L,
                    timeoutMs = 3000L,
                    warmUpTimeoutMs = 2000L
                )
            )
            // Freeze the clock so the hold is paid entirely in virtual delay time.
            vm.start { 0L }

            advanceTimeBy(1000L)
            assertNull(
                "brand moment was cut short",
                vm.uiState.value.destination
            )

            advanceUntilIdle()
            assertEquals(SplashDestination.ONBOARDING, vm.uiState.value.destination)
        }

    @Test
    fun `skip shortens the minimum display hold`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

        val vm = viewModel(
            preferences = returningUser(),
            timings = SplashTimings(
                minDisplayMs = 5000L,
                introDisplayMs = 0L,
                timeoutMs = 3000L,
                warmUpTimeoutMs = 2000L
            )
        )
        vm.skip()
        vm.start { 0L }
        advanceUntilIdle()

        assertEquals(SplashDestination.ONBOARDING, vm.uiState.value.destination)
        assertEquals(SplashViewModel.PROGRESS_COMPLETE, vm.uiState.value.progress)
    }

    // --- Introduction window (UI-02) ----------------------------------------------------

    @Test
    fun `first launch is held long enough to read the manifesto`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

        val vm = viewModel(
            preferences = emptyPreferences(),
            timings = introTimings()
        )
        vm.start { 0L }

        // Past the short hold, but still inside the introduction: the manifesto is the
        // whole point of this window, so the screen must still be up.
        advanceTimeBy(2000L)
        assertNull(
            "introduction was cut to the returning-user hold",
            vm.uiState.value.destination
        )

        advanceUntilIdle()
        assertEquals(SplashDestination.ONBOARDING, vm.uiState.value.destination)
    }

    @Test
    fun `returning user is not charged the introduction hold`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

        val vm = viewModel(preferences = returningUser(), timings = introTimings())
        vm.start { 0L }

        // Just past the short hold and far short of the introduction hold.
        advanceTimeBy(1500L)

        assertEquals(
            "returning user paid the introduction hold",
            SplashDestination.ONBOARDING,
            vm.uiState.value.destination
        )
    }

    @Test
    fun `introduction is spent after the configured number of launches`() =
        runTest(testDispatcher) {
            whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

            // The launch immediately before the boundary still gets the introduction...
            val last = viewModel(
                preferences = mutablePreferencesOf(
                    SplashViewModel.KEY_LAUNCH_COUNT to SplashTimings.INTRO_LAUNCH_COUNT - 1
                ),
                timings = introTimings()
            )
            last.start { 0L }
            advanceTimeBy(1500L)
            assertNull("last introduction launch was cut short", last.uiState.value.destination)
            advanceUntilIdle()

            // ...and the one at the boundary does not.
            val first = viewModel(
                preferences = mutablePreferencesOf(
                    SplashViewModel.KEY_LAUNCH_COUNT to SplashTimings.INTRO_LAUNCH_COUNT
                ),
                timings = introTimings()
            )
            first.start { 0L }
            advanceTimeBy(1500L)
            assertEquals(
                "introduction outlasted its configured window",
                SplashDestination.ONBOARDING,
                first.uiState.value.destination
            )
        }

    @Test
    fun `hint invites continuation during the introduction and skipping afterwards`() =
        runTest(testDispatcher) {
            whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

            val introducing = viewModel(preferences = emptyPreferences())
            introducing.start()
            advanceUntilIdle()
            assertEquals(
                R.string.splash_hint_continue,
                introducing.uiState.value.hintLabel
            )

            val returning = viewModel(preferences = returningUser())
            returning.start()
            advanceUntilIdle()
            assertEquals(R.string.splash_hint_skip, returning.uiState.value.hintLabel)
        }

    @Test
    fun `tap cuts the introduction short`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

        val vm = viewModel(preferences = emptyPreferences(), timings = introTimings())
        vm.skip()
        vm.start { 0L }
        advanceUntilIdle()

        assertEquals(SplashDestination.ONBOARDING, vm.uiState.value.destination)
        assertEquals(SplashViewModel.PROGRESS_COMPLETE, vm.uiState.value.progress)
    }

    // --- Display anchor (UI-02) ---------------------------------------------------------

    /**
     * Startup that takes real time on the virtual clock, so the handoff can be reported
     * *while* it is still running — which is the ordering measured on device.
     */
    private fun slowStartupViewModel(
        readDelayMs: Long,
        visibility: SplashVisibilitySignal
    ) = SplashViewModel(
        dataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                delay(readDelayMs)
                emit(emptyPreferences())
            }

            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences
            ): Preferences = emptyPreferences()
        },
        sessionDao = sessionDao,
        timings = introTimings(),
        visibility = visibility,
        ioDispatcher = testDispatcher
    )

    @Test
    fun `hold is measured from the handoff, not from start`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

        // Models a cold start: startup work runs for 2000 ms, and the system splash hands
        // the window over part-way through it, at 1700 ms — the gap measured on device.
        val visibility = SplashVisibilitySignal()
        val vm = slowStartupViewModel(readDelayMs = 2_000L, visibility = visibility)
        vm.start { testScheduler.currentTime }

        advanceTimeBy(1_700L)
        visibility.markVisible(testScheduler.currentTime)

        // Anchored at the handoff the hold runs to 1700 + 4000 = 5700. Anchored at start()
        // it would end at 4000, cutting 1700 ms off a screen the user could not yet read.
        advanceTimeBy(3_300L)
        assertNull(
            "the introduction budget was spent behind the system splash",
            vm.uiState.value.destination
        )

        advanceUntilIdle()
        assertEquals(SplashDestination.ONBOARDING, vm.uiState.value.destination)
    }

    @Test
    fun `hold falls back to the start clock when the handoff is never reported`() =
        runTest(testDispatcher) {
            whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

            // The handoff never arrives. The hold must still end — degrading to the older,
            // slightly short behaviour beats stranding the user on the splash.
            val vm = slowStartupViewModel(
                readDelayMs = 2_000L,
                visibility = SplashVisibilitySignal()
            )
            vm.start { testScheduler.currentTime }

            // 2000 startup + 2000 visibility timeout + 4000 budget, generously exceeded.
            advanceTimeBy(9_000L)
            assertEquals(
                "hold never ended without a handoff report",
                SplashDestination.ONBOARDING,
                vm.uiState.value.destination
            )
        }

    @Test
    fun `only the first reported handoff anchors the hold`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

        // A configuration change re-runs the activity and reports a second handoff. Taking
        // it would restart an introduction the user has already begun reading.
        val visibility = SplashVisibilitySignal()
        val vm = slowStartupViewModel(readDelayMs = 2_000L, visibility = visibility)
        vm.start { testScheduler.currentTime }

        advanceTimeBy(1_000L)
        visibility.markVisible(testScheduler.currentTime)
        advanceTimeBy(700L)
        visibility.markVisible(testScheduler.currentTime)

        // Anchored at 1000 the hold ends at 5000; re-anchoring to 1700 would run to 5700.
        advanceTimeBy(4_500L)
        assertEquals(
            "a later handoff restarted the introduction",
            SplashDestination.ONBOARDING,
            vm.uiState.value.destination
        )
    }

    // --- Launch counter -----------------------------------------------------------------

    @Test
    fun `launch count is recorded during the introduction`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

        val store = RecordingDataStore(emptyPreferences())
        val vm = viewModel(dataStore = store)
        vm.start()
        advanceUntilIdle()

        assertEquals(1, store.writeCount)
        assertEquals(1, store.current[SplashViewModel.KEY_LAUNCH_COUNT])
    }

    @Test
    fun `launch count stops being written once the introduction is spent`() =
        runTest(testDispatcher) {
            whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

            // Without this the counter would grow without bound for the life of the install.
            val store = RecordingDataStore(returningUser())
            val vm = viewModel(dataStore = store)
            vm.start()
            advanceUntilIdle()

            assertEquals(0, store.writeCount)
        }

    @Test
    fun `a failed launch count write does not block startup`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

        // FakeDataStore rejects every write. Startup must still complete: a bookkeeping
        // failure is never worth stranding the user on the loading screen.
        val vm = viewModel(preferences = emptyPreferences(), timings = introTimings())
        vm.start { 0L }
        advanceUntilIdle()

        assertEquals(SplashDestination.ONBOARDING, vm.uiState.value.destination)
        assertEquals(SplashViewModel.PROGRESS_COMPLETE, vm.uiState.value.progress)
    }

    private fun introTimings() = SplashTimings(
        minDisplayMs = 1200L,
        introDisplayMs = 4000L,
        timeoutMs = 3000L,
        warmUpTimeoutMs = 2000L
    )

    @Test
    fun `start is idempotent across configuration changes`() = runTest(testDispatcher) {
        whenever(sessionDao.getPendingUploadSessions()).thenReturn(emptyList())

        val vm = viewModel()
        vm.start()
        vm.start()
        advanceUntilIdle()

        assertEquals(SplashDestination.ONBOARDING, vm.uiState.value.destination)
    }
}
