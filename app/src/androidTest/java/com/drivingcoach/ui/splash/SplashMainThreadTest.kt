package com.drivingcoach.ui.splash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.drivingcoach.R
import com.drivingcoach.di.DataStoreModule
import com.drivingcoach.di.SplashModule
import com.drivingcoach.testing.MainThreadResponsivenessProbe
import com.drivingcoach.testing.StallingPreferencesDataStore
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Singleton

/**
 * L2 (ASPICE SWE.5) coverage for **SRS UI-04**: startup state resolution runs off the main
 * thread and must never block the UI.
 *
 * This is the one startup requirement that genuinely cannot be verified at L1 — a unit test
 * can only assert which dispatcher a coroutine was handed, not that the UI thread stayed
 * alive.
 *
 * The detector is a [MainThreadResponsivenessProbe] sampling from a background thread while
 * the activity starts against a preferences store whose read never completes. **Espresso
 * interactions were tried first and rejected**: Espresso waits for the main looper to become
 * idle rather than failing, so a deliberate `runBlocking(60s)` on the main thread made the
 * Espresso-based test merely slow — it still passed. Latency sampling fails against that same
 * probe, which is what makes it a real regression guard for the `runBlocking` ANR that used
 * to live in `MainActivity.setupNavigation()`.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SplashMainThreadTest {

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
            timeoutMs = LONG_TIMEOUT_MS,
            warmUpTimeoutMs = SplashTimings.DEFAULT_WARM_UP_TIMEOUT_MS
        )
    }

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun mainThreadStaysResponsiveWhileStartupStateIsUnavailable() {
        val probe = MainThreadResponsivenessProbe()
        probe.start()
        try {
            // Launching is itself the risky moment: it kicks off the preferences read, and
            // it is exactly where the old `runBlocking` used to sit.
            scenario = ActivityScenario.launch(MainActivity::class.java)
            Thread.sleep(OBSERVATION_MS)
        } finally {
            probe.stop()
        }

        assertTrue(
            "SRS UI-04: main thread was unresponsive for ${probe.worstLatencyMs}ms during " +
                "startup (budget ${MAX_ACCEPTABLE_LATENCY_MS}ms). Startup work has most " +
                "likely moved back onto the main thread.",
            probe.worstLatencyMs < MAX_ACCEPTABLE_LATENCY_MS
        )
    }

    @Test
    fun loadingScreenRendersWhileStartupStateIsUnavailable() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.splashRoot)).check(matches(isDisplayed()))
        onView(withId(R.id.progressIndicator)).check(matches(isDisplayed()))
    }

    @Test
    fun stalledStartupHoldsTheLoadingScreenRatherThanNavigatingBlindly() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        Thread.sleep(OBSERVATION_MS)

        assertEquals(
            "A read that never returns must hold the loading screen until its timeout",
            R.id.splashFragment,
            scenario!!.currentDestinationId()
        )
    }

    private companion object {
        /** Far longer than the test, so a fallback navigation cannot race an assertion. */
        const val LONG_TIMEOUT_MS = 120_000L

        const val OBSERVATION_MS = 3_000L

        /**
         * Generous enough to absorb emulator jank and normal startup work, far below the
         * multi-second stalls that a main-thread storage read produces.
         */
        const val MAX_ACCEPTABLE_LATENCY_MS = 2_000L
    }
}
