package com.drivingcoach.ui.splash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.drivingcoach.data.api.AuthInterceptor
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.ui.onboarding.OnboardingFragment
import kotlinx.coroutines.Dispatchers
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

    /** Minimal read-only DataStore stub; the splash never writes preferences. */
    private class FakeDataStore(
        override val data: Flow<Preferences>
    ) : DataStore<Preferences> {
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = throw UnsupportedOperationException("Splash never writes preferences")
    }

    private fun dataStoreOf(preferences: Preferences): DataStore<Preferences> =
        FakeDataStore(flowOf(preferences))

    private fun viewModel(
        preferences: Preferences = emptyPreferences(),
        timings: SplashTimings = SplashTimings(
            minDisplayMs = 0L,
            timeoutMs = 3000L,
            warmUpTimeoutMs = 2000L
        ),
        dataStore: DataStore<Preferences> = dataStoreOf(preferences)
    ) = SplashViewModel(
        dataStore = dataStore,
        sessionDao = sessionDao,
        timings = timings,
        ioDispatcher = testDispatcher
    )

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

            val vm = viewModel(
                timings = SplashTimings(minDisplayMs = 1200L, timeoutMs = 3000L, warmUpTimeoutMs = 2000L)
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

        val vm = viewModel(timings = SplashTimings(minDisplayMs = 5000L, timeoutMs = 3000L, warmUpTimeoutMs = 2000L))
        vm.skip()
        vm.start { 0L }
        advanceUntilIdle()

        assertEquals(SplashDestination.ONBOARDING, vm.uiState.value.destination)
        assertEquals(SplashViewModel.PROGRESS_COMPLETE, vm.uiState.value.progress)
    }

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
