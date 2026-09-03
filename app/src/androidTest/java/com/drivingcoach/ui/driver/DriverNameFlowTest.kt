package com.drivingcoach.ui.driver

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.drivingcoach.R
import com.drivingcoach.di.DataStoreModule
import com.drivingcoach.di.SplashModule
import com.drivingcoach.testing.MutablePreferencesDataStore
import com.drivingcoach.testing.awaitStartupResolved
import com.drivingcoach.testing.awaitUntil
import com.drivingcoach.testing.currentDestinationId
import com.drivingcoach.ui.MainActivity
import com.drivingcoach.ui.splash.SplashTimings
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
 * L2 (ASPICE SWE.5) coverage for first-run driver naming — SRS DR-01 … DR-05.
 *
 * The headline case is [aSavedDriverGoesStraightToHomeOnRelaunch]. It is the regression
 * guard for the defect this flow replaced: the old "Skip Login (Demo Mode)" shortcut
 * navigated to Home without persisting anything, so every relaunch dropped the driver back
 * onto a Login screen that could not log anyone in.
 *
 * Uses the real [MainActivity] and a write-through in-memory DataStore, so the assertion is
 * about what a *later read* actually sees rather than what the screen believes it wrote.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DriverNameFlowTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object OnboardedWithoutProfileModule {

        /**
         * Onboarded, but not yet named — the state a driver is in the moment they finish
         * granting permissions. Shared across the whole test class so a name written by one
         * step is visible to the next.
         */
        @Provides
        @Singleton
        fun provideDataStore(): DataStore<Preferences> = MutablePreferencesDataStore(
            mutablePreferencesOf(booleanPreferencesKey("onboarding_complete") to true)
        )

        @Provides
        @Singleton
        fun provideSplashTimings(): SplashTimings = SplashTimings(
            minDisplayMs = 0L,
            // Pinned so the 4 s introduction hold is not charged to every test here.
            introDisplayMs = 0L,
            timeoutMs = SplashTimings.DEFAULT_TIMEOUT_MS,
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
        scenario = null
    }

    private fun launch(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { scenario = it }

    // --- First run (DR-02) ------------------------------------------------------------------

    @Test
    fun anOnboardedDriverWithoutAProfileIsAskedToName() {
        val active = launch()

        assertEquals(
            "An onboarded driver with no saved name should be asked for one",
            R.id.driverNameFragment,
            active.awaitStartupResolved()
        )

        onView(withId(R.id.driverNameInput)).check(matches(isDisplayed()))
    }

    /** DR-03: the way in stays shut until the name is usable. */
    @Test
    fun theCallToActionIsDisabledUntilTheNameIsValid() {
        val active = launch()
        active.awaitStartupResolved()

        onView(withId(R.id.letsRaceButton)).check(matches(isNotEnabled()))

        onView(withId(R.id.driverNameInput)).perform(replaceText("A"), closeSoftKeyboard())
        onView(withId(R.id.letsRaceButton)).check(matches(isNotEnabled()))

        onView(withId(R.id.driverNameInput)).perform(replaceText("Ayrton"), closeSoftKeyboard())
        onView(withId(R.id.letsRaceButton)).check(matches(isEnabled()))
    }

    @Test
    fun namingTheDriverOpensTheApp() {
        val active = launch()
        active.awaitStartupResolved()

        onView(withId(R.id.driverNameInput))
            .perform(replaceText("Ayrton Senna"), closeSoftKeyboard())
        onView(withId(R.id.letsRaceButton)).perform(click())

        awaitUntil("the driver to reach Home after naming") {
            active.currentDestinationId() == R.id.homeFragment
        }
    }

    /**
     * The regression guard. Naming happens on the first launch; the second launch must skip
     * it entirely and go straight to Home.
     */
    @Test
    fun aSavedDriverGoesStraightToHomeOnRelaunch() {
        val first = launch()
        first.awaitStartupResolved()

        onView(withId(R.id.driverNameInput))
            .perform(replaceText("Ayrton Senna"), closeSoftKeyboard())
        onView(withId(R.id.letsRaceButton)).perform(click())

        awaitUntil("the driver to reach Home after naming") {
            first.currentDestinationId() == R.id.homeFragment
        }
        first.close()

        val second = launch()

        assertEquals(
            "A driver who has already been named must not be asked again",
            R.id.homeFragment,
            second.awaitStartupResolved()
        )
    }

    /** DR-01, DR-06: the saved name is what Profile shows. */
    @Test
    fun theSavedNameIsShownOnTheProfileScreen() {
        val active = launch()
        active.awaitStartupResolved()

        onView(withId(R.id.driverNameInput))
            .perform(replaceText("Ayrton Senna"), closeSoftKeyboard())
        onView(withId(R.id.letsRaceButton)).perform(click())

        awaitUntil("the driver to reach Home after naming") {
            active.currentDestinationId() == R.id.homeFragment
        }

        active.onActivity { activity ->
            activity.findNavController(R.id.nav_host_fragment)
                .navigate(R.id.action_home_to_profile)
        }

        awaitUntil("the Profile screen to open") {
            active.currentDestinationId() == R.id.profileFragment
        }

        // The profile loads its name off the IO dispatcher, which Espresso cannot
        // idle on, so poll for the rendered text instead of asserting immediately.
        active.awaitViewText(R.id.userName, "Ayrton Senna")
        active.awaitViewText(R.id.avatarInitials, "AS")
    }

    /** DR-03: whitespace is trimmed on the way in, not carried into the profile. */
    @Test
    fun surroundingWhitespaceIsTrimmedFromTheSavedName() {
        val active = launch()
        active.awaitStartupResolved()

        onView(withId(R.id.driverNameInput))
            .perform(replaceText("   Ayrton   "), closeSoftKeyboard())
        onView(withId(R.id.letsRaceButton)).perform(click())

        awaitUntil("the driver to reach Home after naming") {
            active.currentDestinationId() == R.id.homeFragment
        }

        active.onActivity { activity ->
            activity.findNavController(R.id.nav_host_fragment)
                .navigate(R.id.action_home_to_profile)
        }

        awaitUntil("the Profile screen to open") {
            active.currentDestinationId() == R.id.profileFragment
        }

        active.awaitViewText(R.id.userName, "Ayrton")
    }

    /**
     * DR-08: the removed demo-mode shortcut must not come back. It is the control that
     * created the "app forgot me" defect — it navigated to Home while persisting nothing —
     * so its absence is worth asserting rather than assuming.
     */
    @Test
    fun theDemoModeShortcutIsGone() {
        val active = launch()
        active.awaitStartupResolved()

        active.onActivity { activity ->
            val identifier = activity.resources.getIdentifier(
                "skipLoginButton",
                "id",
                activity.packageName
            )
            assertEquals("the demo-mode shortcut must not be present", 0, identifier)
        }
    }

    /**
     * Waits for a [TextView] to render [expected]. Profile data is loaded asynchronously,
     * so a bare Espresso assertion races the first emission of the UI state.
     */
    private fun ActivityScenario<MainActivity>.awaitViewText(viewId: Int, expected: String) {
        var actual = ""
        awaitUntil("view $viewId to show \"$expected\"") {
            onActivity { activity ->
                actual = activity.findViewById<TextView>(viewId)?.text?.toString().orEmpty()
            }
            actual == expected
        }
    }

    private fun MainActivity.findNavController(hostId: Int) =
        (supportFragmentManager.findFragmentById(hostId)
            as androidx.navigation.fragment.NavHostFragment).navController
}
