package com.drivingcoach.ui.home

import android.view.View
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
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
import com.drivingcoach.testing.SeededPreferencesDataStore
import com.drivingcoach.testing.awaitStartupResolved
import com.drivingcoach.testing.awaitUntil
import com.drivingcoach.ui.MainActivity
import com.drivingcoach.ui.splash.SplashTimings
import com.google.android.material.appbar.AppBarLayout
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
 * L2 (ASPICE SWE.5) coverage for **SRS UI-06**: the Home brand hero collapses to a pinned
 * bar, and the Start Session control stays visible throughout.
 *
 * The collapse is driven through `AppBarLayout.setExpanded`, which routes through the very
 * same `OnOffsetChangedListener` that a finger scroll uses. A swipe gesture was rejected on
 * purpose: with an empty session list the scrolling content may be shorter than the
 * viewport, so a swipe would produce no offset change and the test would pass vacuously.
 * Reachability by scrolling is asserted separately via the collapsing scroll flags.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeHeroTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object SignedInStartupModule {

        @Provides
        @Singleton
        fun provideDataStore(): DataStore<Preferences> = SeededPreferencesDataStore(
            mutablePreferencesOf(
                booleanPreferencesKey("onboarding_complete") to true,
                stringPreferencesKey("jwt_token") to "instrumented-test-token"
            )
        )

        @Provides
        @Singleton
        fun provideSplashTimings(): SplashTimings = SplashTimings(
            minDisplayMs = 0L,
            // Pinned explicitly: inheriting the 4 s introduction hold would add
            // that cost to every test in this class.
            introDisplayMs = 0L,
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
        assertEquals(R.id.homeFragment, scenario.awaitStartupResolved())
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun expandedHeroShowsTheBrandAndHidesThePinnedBar() {
        onView(withId(R.id.heroWordmark)).check(matches(isDisplayed()))
        onView(withId(R.id.heroKicker)).check(matches(isDisplayed()))

        assertTrue("Expanded hero should be opaque", alphaOf(R.id.heroContent) > 0.9f)
        assertTrue("Pinned bar should be hidden", alphaOf(R.id.collapsedBrand) < 0.1f)
    }

    @Test
    fun collapsingTheHeroRevealsThePinnedBrandBar() {
        setExpanded(false)

        awaitUntil("the pinned brand bar to fade in as the hero collapses") {
            alphaOf(R.id.collapsedBrand) > 0.9f
        }

        assertTrue(
            "Expanded hero content should have faded out",
            alphaOf(R.id.heroContent) < 0.1f
        )
    }

    @Test
    fun startSessionRemainsVisibleAcrossTheWholeCollapse() {
        onView(withId(R.id.startSessionButton)).check(matches(isDisplayed()))

        setExpanded(false)
        awaitUntil("hero to finish collapsing") { alphaOf(R.id.collapsedBrand) > 0.9f }
        onView(withId(R.id.startSessionButton)).check(matches(isDisplayed()))

        setExpanded(true)
        awaitUntil("hero to finish expanding") { alphaOf(R.id.heroContent) > 0.9f }
        onView(withId(R.id.startSessionButton)).check(matches(isDisplayed()))
    }

    @Test
    fun heroIsConfiguredToCollapseOnScroll() {
        // Guards the reachability of the behaviour exercised above: without these flags the
        // hero could never collapse from a real finger scroll.
        var flags = 0
        scenario.onActivity { activity ->
            val collapsing = activity.findViewById<View>(R.id.collapsingToolbar)
            flags = (collapsing.layoutParams as AppBarLayout.LayoutParams).scrollFlags
        }

        assertTrue(
            "Hero must be scrollable",
            flags and AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL != 0
        )
        assertTrue(
            "Hero must collapse to a pinned bar rather than scrolling fully away",
            flags and AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED != 0
        )
    }

    private fun setExpanded(expanded: Boolean) {
        scenario.onActivity { activity ->
            activity.findViewById<AppBarLayout>(R.id.appBarLayout)
                .setExpanded(expanded, false)
        }
    }

    private fun alphaOf(viewId: Int): Float {
        var alpha = -1f
        scenario.onActivity { activity ->
            alpha = activity.findViewById<View>(viewId).alpha
        }
        return alpha
    }
}
