package com.drivingcoach.ui.about

import android.content.Context
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.drivingcoach.BuildConfig
import com.drivingcoach.R
import com.drivingcoach.testing.launchFragmentInHiltContainer
import com.drivingcoach.ui.profile.ProfileFragment
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * L2 (ASPICE SWE.5) coverage for the About screen.
 *
 * Covers:
 * - **SRS UI-11** — the engineering manifesto has a permanent, readable home. The loading
 *   screen also shows it, but that surface is transient by design; if this screen stopped
 *   rendering the manifesto the text would be effectively unreadable in the product.
 * - **SRS UI-12** — the build identity is visible in-app, and is *derived* from the same
 *   source of truth that names the APK rather than being typed in by hand.
 *
 * The reachability test is the important one: [AboutFragment] is only ever opened through a
 * navigation action, and a missing or misdirected action fails at runtime, not at compile
 * time. Asserting the destination id after a real click is what makes that falsifiable.
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    // --- SRS UI-11: the manifesto has a permanent home -----------------------------------

    @Test
    fun aboutScreenShowsTheBrandIdentityAndManifesto() {
        launchFragmentInHiltContainer<AboutFragment>()

        onView(withId(R.id.wordmark)).check(matches(isDisplayed()))
        onView(withId(R.id.kicker)).check(matches(isDisplayed()))
        onView(withId(R.id.tagline)).check(matches(isDisplayed()))

        onView(withId(R.id.manifestoTitle))
            .perform(scrollTo())
            .check(matches(withText(context.getString(R.string.manifesto_title))))
        onView(withId(R.id.manifestoBody))
            .perform(scrollTo())
            .check(matches(withText(context.getString(R.string.manifesto_body))))
    }

    // --- SRS UI-12: build identity is visible and derived --------------------------------

    @Test
    fun aboutScreenReportsTheBuildIdentityFromBuildConfig() {
        launchFragmentInHiltContainer<AboutFragment>()

        val expected = context.getString(
            R.string.about_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )

        onView(withId(R.id.versionLabel)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.versionValue)).perform(scrollTo()).check(matches(withText(expected)))
    }

    @Test
    fun reportedVersionMatchesTheInstalledPackage() {
        // Guards the failure that motivated the versioning work: an APK whose filename and
        // whose reported version disagreed. BuildConfig and the installed manifest must be
        // the same number, otherwise a bug report cites a build that does not exist.
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)

        assertEquals(
            "BuildConfig.VERSION_NAME must match the installed package",
            BuildConfig.VERSION_NAME,
            installed.versionName
        )
    }

    // --- Reachability: Profile -> About --------------------------------------------------

    @Test
    fun aboutIsReachableFromTheProfileScreen() {
        val navController = TestNavHostController(context)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.profileFragment)
        }

        launchFragmentInHiltContainer<ProfileFragment> {
            Navigation.setViewNavController(requireView(), navController)
        }

        // No scrollTo(): the Profile screen is a ConstraintLayout and both action buttons are
        // anchored to the bottom of the parent, so they are always on screen.
        onView(withId(R.id.aboutButton)).perform(click())

        assertEquals(
            "Tapping About on the Profile screen must open the About destination",
            R.id.aboutFragment,
            navController.currentDestination?.id
        )
    }
}
