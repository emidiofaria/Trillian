package com.drivingcoach.ui.splash

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.drivingcoach.R
import com.drivingcoach.di.SplashModule
import com.drivingcoach.testing.awaitUntil
import com.drivingcoach.testing.currentDestinationId
import com.drivingcoach.ui.MainActivity
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.hamcrest.Matchers.containsStringIgnoringCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Singleton

/**
 * L2 (ASPICE SWE.5) coverage for the branded loading screen.
 *
 * Covers:
 * - **SRS UI-01** — every brand element inflates and is visible, and the progress indicator
 *   is determinate and reflects real initialisation progress.
 * - **SRS UI-02** — tapping the screen cuts the brand hold short.
 * - **SRS UI-10** — the tap affordance is visible on screen, not only announced to
 *   accessibility services.
 *
 * The minimum display time is pinned far longer than any assertion needs, so the loading
 * screen is *deterministically* on screen while the test runs. Racing the real 1200 ms
 * budget with sleeps would be flaky on a loaded emulator; pinning it also makes the skip
 * test unambiguous, because without a working tap the screen would still be there.
 */
@LargeTest
@UninstallModules(SplashModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SplashScreenTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object PinnedSplashTimingsModule {

        @Provides
        @Singleton
        fun provideSplashTimings(): SplashTimings = SplashTimings(
            minDisplayMs = PINNED_MIN_DISPLAY_MS,
            // Pinned explicitly: inheriting the 4 s introduction hold would add
            // that cost to every test in this class.
            introDisplayMs = PINNED_MIN_DISPLAY_MS,
            timeoutMs = SplashTimings.DEFAULT_TIMEOUT_MS,
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

    // --- SRS UI-01: brand elements and determinate progress ------------------------------

    @Test
    fun loadingScreenDisplaysEveryBrandElement() {
        onView(withId(R.id.emblemContainer)).check(matches(isDisplayed()))
        onView(withId(R.id.wordmark)).check(matches(isDisplayed()))
        onView(withId(R.id.kicker)).check(matches(isDisplayed()))
        onView(withId(R.id.tagline)).check(matches(isDisplayed()))
        onView(withId(R.id.manifestoCard)).check(matches(isDisplayed()))
        onView(withId(R.id.manifestoTitle)).check(matches(isDisplayed()))
        onView(withId(R.id.manifestoBody)).check(matches(isDisplayed()))
        onView(withId(R.id.progressIndicator)).check(matches(isDisplayed()))
    }

    @Test
    fun tapAffordanceIsVisibleNotJustAnAccessibilityLabel() {
        // Regression guard: the hint previously existed only as a contentDescription on the
        // root, so a sighted user had no indication the screen could be tapped at all.
        onView(withId(R.id.splashHint)).check(matches(isCompletelyDisplayed()))
        onView(withId(R.id.splashHint)).check(matches(withText(containsStringIgnoringCase("tap"))))
    }

    @Test
    fun footerMetricsAreNotClippedByTheSystemBars() {
        // Regression guard: the footer was previously drawn underneath the navigation bar
        // because the fragment did not consume bottom window insets.
        onView(withId(R.id.footerMetrics)).check(matches(isCompletelyDisplayed()))
    }

    @Test
    fun progressIndicatorIsDeterminateAndAdvances() {
        awaitUntil("progress indicator to report real initialisation progress") {
            readProgress() > 0
        }

        var indeterminate = true
        scenario.onActivity { activity ->
            indeterminate = activity
                .findViewById<LinearProgressIndicator>(R.id.progressIndicator)
                .isIndeterminate
        }

        assertFalse(
            "SRS UI-01 requires a determinate indicator reflecting actual progress",
            indeterminate
        )
        assertTrue("Progress should be within 1..100", readProgress() in 1..100)
    }

    @Test
    fun progressLabelMatchesTheIndicatorValue() {
        awaitUntil("progress to advance past zero") { readProgress() > 0 }

        val progress = readProgress()
        onView(withId(R.id.progressLabel)).check(matches(withText("$progress%")))
    }

    // --- SRS UI-02: minimum hold, and tap to skip it -------------------------------------

    @Test
    fun loadingScreenRemainsWhileTheBrandHoldIsPending() {
        Thread.sleep(HOLD_OBSERVATION_MS)

        assertEquals(
            "Splash should still be showing while the minimum display time is pending",
            R.id.splashFragment,
            scenario.currentDestinationId()
        )
    }

    @Test
    fun tappingTheLoadingScreenSkipsTheBrandHold() {
        onView(withId(R.id.splashRoot)).check(matches(isDisplayed()))

        onView(withId(R.id.splashRoot)).perform(click())

        awaitUntil("splash to be dismissed after the skip tap", timeoutMs = 10_000L) {
            scenario.currentDestinationId() != R.id.splashFragment
        }
    }

    private fun readProgress(): Int {
        var progress = 0
        scenario.onActivity { activity ->
            progress = activity
                .findViewById<LinearProgressIndicator>(R.id.progressIndicator)
                .progress
        }
        return progress
    }

    private companion object {
        /** Long enough that the loading screen cannot disappear mid-assertion. */
        const val PINNED_MIN_DISPLAY_MS = 60_000L

        /** Comfortably beyond the real 1200 ms budget, far below the pinned hold. */
        const val HOLD_OBSERVATION_MS = 3_000L
    }
}
