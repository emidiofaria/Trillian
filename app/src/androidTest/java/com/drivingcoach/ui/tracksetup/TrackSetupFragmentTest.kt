package com.drivingcoach.ui.tracksetup

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drivingcoach.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso tests for TrackSetupFragment.
 * 
 * Tests the two-point GPS capture workflow for defining the start/finish line.
 * 
 * NOTE: These tests require proper Hilt fragment container setup.
 * Currently ignored due to launchFragmentInContainer incompatibility with @AndroidEntryPoint fragments.
 * TODO: Migrate to launchFragmentInHiltContainer with proper HiltTestActivity setup.
 */
@Ignore("Fragment uses @AndroidEntryPoint - requires launchFragmentInHiltContainer migration")
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TrackSetupFragmentTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private lateinit var navController: TestNavHostController

    @Before
    fun setup() {
        hiltRule.inject()
        
        navController = TestNavHostController(
            ApplicationProvider.getApplicationContext()
        )
    }

    @Test
    fun instructionsTitle_isDisplayed() {
        launchFragmentInContainer<TrackSetupFragment>(
            themeResId = R.style.Theme_DrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.trackSetupFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        onView(withId(R.id.instructionsTitle))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.instructionsTitle))
            .check(matches(withText(containsString("START/FINISH LINE"))))
    }

    @Test
    fun instructionsBody_isDisplayed() {
        launchFragmentInContainer<TrackSetupFragment>(
            themeResId = R.style.Theme_DrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.trackSetupFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        onView(withId(R.id.instructionsBody))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.instructionsBody))
            .check(matches(withText(containsString("Walk to each edge"))))
    }

    @Test
    fun gpsStatusContainer_isDisplayed() {
        launchFragmentInContainer<TrackSetupFragment>(
            themeResId = R.style.Theme_DrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.trackSetupFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        onView(withId(R.id.gpsStatusContainer))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.gpsStatusText))
            .check(matches(isDisplayed()))
    }

    @Test
    fun pointACard_isDisplayed() {
        launchFragmentInContainer<TrackSetupFragment>(
            themeResId = R.style.Theme_DrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.trackSetupFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        onView(withId(R.id.pointACard))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.pointALabel))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.pointALabel))
            .check(matches(withText(containsString("POINT A"))))
        
        onView(withId(R.id.pointACoords))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.pointACoords))
            .check(matches(withText("Not captured")))
    }

    @Test
    fun pointBCard_isDisplayed() {
        launchFragmentInContainer<TrackSetupFragment>(
            themeResId = R.style.Theme_DrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.trackSetupFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        onView(withId(R.id.pointBCard))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.pointBLabel))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.pointBLabel))
            .check(matches(withText(containsString("POINT B"))))
        
        onView(withId(R.id.pointBCoords))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.pointBCoords))
            .check(matches(withText("Not captured")))
    }

    @Test
    fun capturePointAButton_isDisplayedAndClickable() {
        launchFragmentInContainer<TrackSetupFragment>(
            themeResId = R.style.Theme_DrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.trackSetupFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        onView(withId(R.id.capturePointAButton))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.capturePointAButton))
            .check(matches(isClickable()))
        
        onView(withId(R.id.capturePointAButton))
            .check(matches(withText("CAPTURE")))
    }

    @Test
    fun capturePointBButton_isInitiallyDisabled() {
        launchFragmentInContainer<TrackSetupFragment>(
            themeResId = R.style.Theme_DrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.trackSetupFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        onView(withId(R.id.capturePointBButton))
            .check(matches(isDisplayed()))
        
        // Point B button should be disabled until Point A is captured
        onView(withId(R.id.capturePointBButton))
            .check(matches(isNotEnabled()))
    }

    @Test
    fun distanceDisplay_isDisplayed() {
        launchFragmentInContainer<TrackSetupFragment>(
            themeResId = R.style.Theme_DrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.trackSetupFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        onView(withId(R.id.distanceLabel))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.distanceLabel))
            .check(matches(withText("LINE WIDTH")))
        
        onView(withId(R.id.distanceValue))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.distanceValue))
            .check(matches(withText("--")))
    }

    @Test
    fun startRecordingButton_isInitiallyDisabled() {
        launchFragmentInContainer<TrackSetupFragment>(
            themeResId = R.style.Theme_DrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.trackSetupFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        onView(withId(R.id.startRecordingButton))
            .check(matches(isDisplayed()))
        
        // Should be disabled until valid line is captured (both points with ≥3m distance)
        onView(withId(R.id.startRecordingButton))
            .check(matches(isNotEnabled()))
        
        onView(withId(R.id.startRecordingButton))
            .check(matches(withText("START RECORDING")))
    }

    @Test
    fun appLogo_isDisplayed() {
        launchFragmentInContainer<TrackSetupFragment>(
            themeResId = R.style.Theme_DrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.trackSetupFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        onView(withId(R.id.appLogo))
            .check(matches(isDisplayed()))
    }

    @Test
    fun clearButton_isInitiallyHidden() {
        launchFragmentInContainer<TrackSetupFragment>(
            themeResId = R.style.Theme_DrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.trackSetupFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        // Clear button should be hidden until at least one point is captured
        onView(withId(R.id.clearButton))
            .check(matches(not(isDisplayed())))
    }
}
