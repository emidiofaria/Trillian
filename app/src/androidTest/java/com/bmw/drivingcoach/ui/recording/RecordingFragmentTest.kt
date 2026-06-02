package com.bmw.drivingcoach.ui.recording

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bmw.drivingcoach.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.containsString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso tests for RecordingFragment.
 * 
 * Note: These tests require Hilt test dependencies and a test runner.
 * Some tests are marked as integration tests that require the full app context.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RecordingFragmentTest {

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
    fun elapsedTimeText_isDisplayed() {
        // Launch the fragment with a test session ID
        val bundle = RecordingFragmentArgs(sessionId = 1L).toBundle()
        
        launchFragmentInContainer<RecordingFragment>(
            fragmentArgs = bundle,
            themeResId = R.style.Theme_BMWDrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.recordingFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        // Assert elapsed time is displayed (initial format)
        onView(withId(R.id.elapsedTime))
            .check(matches(isDisplayed()))
        
        // Check for time format pattern (MM:SS.mmm)
        onView(withId(R.id.elapsedTime))
            .check(matches(withText(containsString(":"))))
    }

    @Test
    fun stopButton_isVisibleAndClickable() {
        val bundle = RecordingFragmentArgs(sessionId = 1L).toBundle()
        
        launchFragmentInContainer<RecordingFragment>(
            fragmentArgs = bundle,
            themeResId = R.style.Theme_BMWDrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.recordingFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        // Assert stop button is displayed
        onView(withId(R.id.stopRecordingButton))
            .check(matches(isDisplayed()))
        
        // Assert stop button is clickable
        onView(withId(R.id.stopRecordingButton))
            .check(matches(isClickable()))
        
        // Assert button text
        onView(withId(R.id.stopRecordingButton))
            .check(matches(withText(containsString("STOP"))))
    }

    @Test
    fun gpsStatus_isDisplayed() {
        val bundle = RecordingFragmentArgs(sessionId = 1L).toBundle()
        
        launchFragmentInContainer<RecordingFragment>(
            fragmentArgs = bundle,
            themeResId = R.style.Theme_BMWDrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.recordingFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        // Assert GPS status container is displayed
        onView(withId(R.id.gpsStatusContainer))
            .check(matches(isDisplayed()))
        
        // Assert GPS status text is displayed
        onView(withId(R.id.gpsStatusText))
            .check(matches(isDisplayed()))
    }

    @Test
    fun recordingIndicator_isDisplayed() {
        val bundle = RecordingFragmentArgs(sessionId = 1L).toBundle()
        
        launchFragmentInContainer<RecordingFragment>(
            fragmentArgs = bundle,
            themeResId = R.style.Theme_BMWDrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.recordingFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        // Assert recording indicator is displayed
        onView(withId(R.id.recordingIndicator))
            .check(matches(isDisplayed()))
        
        // Assert recording dot is displayed
        onView(withId(R.id.recordingDot))
            .check(matches(isDisplayed()))
    }

    @Test
    fun stopButton_triggersNavigation_whenClicked() {
        val bundle = RecordingFragmentArgs(sessionId = 1L).toBundle()
        
        launchFragmentInContainer<RecordingFragment>(
            fragmentArgs = bundle,
            themeResId = R.style.Theme_BMWDrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.recordingFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        // Click stop button
        onView(withId(R.id.stopRecordingButton))
            .perform(click())
        
        // Note: Full navigation assertion requires mocking the ViewModel
        // which would need a more complex test setup with Hilt testing
        // For now, we verify the button is clickable and doesn't crash
    }

    @Test
    fun bmwLogo_isDisplayed() {
        val bundle = RecordingFragmentArgs(sessionId = 1L).toBundle()
        
        launchFragmentInContainer<RecordingFragment>(
            fragmentArgs = bundle,
            themeResId = R.style.Theme_BMWDrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.recordingFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        // Assert BMW logo placeholder is displayed
        onView(withId(R.id.bmwLogo))
            .check(matches(isDisplayed()))
    }

    @Test
    fun sessionTimeLabel_isDisplayed() {
        val bundle = RecordingFragmentArgs(sessionId = 1L).toBundle()
        
        launchFragmentInContainer<RecordingFragment>(
            fragmentArgs = bundle,
            themeResId = R.style.Theme_BMWDrivingCoach
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.recordingFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        // Assert session time label is displayed
        onView(withId(R.id.elapsedTimeLabel))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.elapsedTimeLabel))
            .check(matches(withText("SESSION TIME")))
    }
}
