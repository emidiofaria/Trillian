package com.drivingcoach.ui.splash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.drivingcoach.R
import com.drivingcoach.di.DataStoreModule
import com.drivingcoach.di.SplashModule
import com.drivingcoach.testing.StallingPreferencesDataStore
import com.drivingcoach.testing.awaitUntil
import com.drivingcoach.testing.currentDestinationId
import com.drivingcoach.ui.MainActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Singleton

/**
 * L2 (ASPICE SWE.5) coverage for degraded startup.
 *
 * Covers **SRS UI-03** — when essential startup state cannot be read, the app proceeds to
 * Onboarding rather than blocking, and specifically *not* to Login.
 *
 * The timeout is pinned short so the fallback fires quickly. Main-thread responsiveness
 * during the same stall is asserted separately in [SplashMainThreadTest], which needs a
 * long timeout so its interactions cannot race the fallback navigation.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SplashFallbackTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object StalledStartupModule {

        @Provides
        @Singleton
        fun provideDataStore(): DataStore<Preferences> = StallingPreferencesDataStore()

        @Provides
        @Singleton
        fun provideSplashTimings(): SplashTimings = SplashTimings(
            minDisplayMs = 0L,
            // Pinned explicitly: inheriting the 4 s introduction hold would add
            // that cost to every test in this class.
            introDisplayMs = 0L,
            timeoutMs = SHORT_TIMEOUT_MS,
            warmUpTimeoutMs = SplashTimings.DEFAULT_WARM_UP_TIMEOUT_MS
        )
    }

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        hiltRule.inject()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    // --- SRS UI-03: bounded startup with a safe fallback ---------------------------------

    @Test
    fun unreadableStartupStateFallsBackToOnboarding() {
        awaitUntil("startup to give up and navigate away from the splash") {
            scenario.currentDestinationId() != R.id.splashFragment
        }

        assertEquals(
            "SRS UI-03: an unreadable preferences store must land the user in Onboarding",
            R.id.onboardingFragment,
            scenario.currentDestinationId()
        )
    }

    @Test
    fun unreadableStartupStateNeverFallsBackToLogin() {
        awaitUntil("startup to resolve a destination") {
            scenario.currentDestinationId() != R.id.splashFragment
        }

        // Login would skip permission granting entirely, leaving a fresh install unable to
        // record. Onboarding is idempotent, so it is the strictly safer default.
        assertNotEquals(
            "Falling back to Login can leave a fresh install unable to record",
            R.id.loginFragment,
            scenario.currentDestinationId()
        )
    }

    @Test
    fun startupNeverStrandsTheUserOnTheLoadingScreen() {
        awaitUntil(
            message = "the loading screen to be dismissed within its timeout budget",
            timeoutMs = SHORT_TIMEOUT_MS * 4
        ) {
            scenario.currentDestinationId() != R.id.splashFragment
        }
    }

    private companion object {
        /**
         * Deliberately short. The production ceiling is 8 s; the requirement under test is
         * "the timeout fires and yields a safe destination", not the specific number.
         */
        const val SHORT_TIMEOUT_MS = 2_000L
    }
}
