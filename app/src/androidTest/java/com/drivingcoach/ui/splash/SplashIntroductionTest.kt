package com.drivingcoach.ui.splash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.drivingcoach.R
import com.drivingcoach.di.DataStoreModule
import com.drivingcoach.di.SplashModule
import com.drivingcoach.testing.SeededPreferencesDataStore
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Singleton

/**
 * L2 (ASPICE SWE.5) coverage for the first-run introduction window.
 *
 * Covers **SRS UI-02** — the engineering manifesto is held on screen long enough to be read
 * on the first few launches, then the screen gets out of the way on every launch after that.
 *
 * This is the behaviour the unit tests verify in isolation; asserting it again here proves
 * the launch counter is actually wired through the *real* DataStore-backed startup path and
 * survives Hilt injection, which is where the two halves could silently disagree.
 *
 * The budgets are pinned to exaggerated values so the assertions cannot race a loaded
 * emulator: the introduction hold is effectively infinite, the returning-user hold is zero.
 * A regression in either direction therefore fails, rather than merely being slow.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SplashIntroductionTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    // --- SRS UI-02: first launches hold long enough to read ------------------------------

    @Test
    fun firstLaunchHoldsTheManifestoOnScreen() {
        launch()

        Thread.sleep(OBSERVATION_MS)

        assertEquals(
            "A first-launch user must still be able to read the manifesto after " +
                "${OBSERVATION_MS}ms — this is the whole point of the introduction window",
            R.id.splashFragment,
            scenario.currentDestinationId()
        )
        onView(withId(R.id.manifestoBody)).check(matches(withText(R.string.manifesto_body)))
    }

    @Test
    fun firstLaunchInvitesTheUserToContinueRatherThanToSkip() {
        launch()

        // "Tap to skip" would be the wrong instruction while we are deliberately holding
        // the screen: it frames the hold as an obstacle instead of as the content.
        onView(withId(R.id.splashHint)).check(matches(withText(R.string.splash_hint_continue)))
    }

    private companion object {
        /**
         * Comfortably beyond the real 1200 ms returning-user budget, so a regression that
         * dropped the introduction window would fail here.
         */
        const val OBSERVATION_MS = 3_000L
    }

    /**
     * Timings for a user who has never launched the app: seeded preferences carry no launch
     * count, so the introduction budget applies.
     */
    @Module
    @InstallIn(SingletonComponent::class)
    object FirstLaunchModule {

        @Provides
        @Singleton
        fun provideDataStore(): DataStore<Preferences> = SeededPreferencesDataStore()

        @Provides
        @Singleton
        fun provideSplashTimings(): SplashTimings = SplashTimings(
            minDisplayMs = 0L,
            introDisplayMs = PINNED_INTRO_MS,
            timeoutMs = SplashTimings.DEFAULT_TIMEOUT_MS,
            warmUpTimeoutMs = SplashTimings.DEFAULT_WARM_UP_TIMEOUT_MS
        )
    }
}

/**
 * The returning-user half of [SplashIntroductionTest].
 *
 * Split into its own class because the launch count is supplied by a `@Singleton` Hilt
 * module, which cannot vary per test method within a class.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SplashReturningUserTest {

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

    // --- SRS UI-02: the introduction window is spent, so get out of the way ---------------

    @Test
    fun returningUserIsNotHeldByTheIntroductionWindow() {
        awaitUntil(
            "splash to release a returning user without waiting for the introduction hold",
            timeoutMs = RELEASE_TIMEOUT_MS
        ) {
            scenario.currentDestinationId() != R.id.splashFragment
        }
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object ReturningUserModule {

        @Provides
        @Singleton
        fun provideDataStore(): DataStore<Preferences> = SeededPreferencesDataStore(
            preferencesOf(SplashViewModel.KEY_LAUNCH_COUNT to SplashTimings.INTRO_LAUNCH_COUNT)
        )

        @Provides
        @Singleton
        fun provideSplashTimings(): SplashTimings = SplashTimings(
            minDisplayMs = 0L,
            // Effectively infinite. If the returning-user path ever inherited this budget
            // the test below would time out instead of passing slowly.
            introDisplayMs = PINNED_INTRO_MS,
            timeoutMs = SplashTimings.DEFAULT_TIMEOUT_MS,
            warmUpTimeoutMs = SplashTimings.DEFAULT_WARM_UP_TIMEOUT_MS
        )
    }

    private companion object {
        const val RELEASE_TIMEOUT_MS = 10_000L
    }
}

/** Long enough that the introduction hold cannot expire during any assertion. */
private const val PINNED_INTRO_MS = 60_000L

/*
 * Deliberately NOT tested at L2: the *arithmetic* of the display anchor — that the budget
 * is measured from the system splash's exit rather than from startup.
 *
 * An L2 assertion would have to observe a 4 s window through Espresso, which waits for the
 * main thread to fall idle before it looks. On a loaded emulator that wait can outlast the
 * window, so the test fails without a defect. A first attempt did exactly that: green in the
 * suite, red in isolation.
 *
 * The arithmetic is instead gated at L1 by SplashViewModelTest, which controls the clock and
 * is mutation-verified — reverting the anchor to the startup clock fails it. The end-to-end
 * accuracy was measured on device: the reported handoff landed within ~17 ms of the
 * platform's own "Displayed" first-frame marker across repeated cold starts.
 */
