package com.drivingcoach.ui.home

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.lifecycle.Lifecycle
import com.drivingcoach.R
import com.drivingcoach.data.location.LocationUpdates
import com.drivingcoach.data.location.WarmUpTimings
import com.drivingcoach.di.DataStoreModule
import com.drivingcoach.di.LocationModule
import com.drivingcoach.di.SplashModule
import com.drivingcoach.testing.ScriptedLocationUpdates
import com.drivingcoach.testing.SeededPreferencesDataStore
import com.drivingcoach.testing.awaitStartupResolved
import com.drivingcoach.testing.awaitUntil
import com.drivingcoach.ui.MainActivity
import com.drivingcoach.ui.splash.SplashTimings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Singleton

/**
 * L2 (ASPICE SWE.5) coverage for **SRS TS-16 and TS-18**: Home warms the GPS up before the
 * user reaches Track Setup, holds it across the walk to the start line, and releases it when
 * the app leaves the foreground.
 *
 * Home deliberately shows nothing while this happens. The readiness chip was removed because
 * a progress message the user cannot act on is not information; the acquisition itself is
 * unchanged, which is exactly what these tests exist to keep proving.
 *
 * Fixes are scripted rather than real. An emulator cannot produce a coarse-then-accurate
 * progression, and a test that waits for real satellites would be the flakiest in the suite.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class, LocationModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeGpsWarmUpTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object SignedInStartupModule {

        @Provides
        @Singleton
        fun provideDataStore(): DataStore<Preferences> = SeededPreferencesDataStore(
            mutablePreferencesOf(
                booleanPreferencesKey("onboarding_complete") to true,
                // V1 reaches Home via a saved local driver profile, not a token
                // (SRS DR-01 … DR-04). A seeded JWT no longer resolves past naming.
                stringPreferencesKey("user_name") to "Instrumented Driver",
                booleanPreferencesKey("driver_profile_complete") to true
            )
        )

        @Provides
        @Singleton
        fun provideSplashTimings(): SplashTimings = SplashTimings(
            minDisplayMs = 0L,
            introDisplayMs = 0L,
            timeoutMs = SplashTimings.DEFAULT_TIMEOUT_MS,
            warmUpTimeoutMs = SplashTimings.DEFAULT_WARM_UP_TIMEOUT_MS
        )

        @Provides
        @Singleton
        fun provideScriptedLocationUpdates(): ScriptedLocationUpdates = ScriptedLocationUpdates()

        @Provides
        @Singleton
        fun provideLocationUpdates(scripted: ScriptedLocationUpdates): LocationUpdates = scripted

        @Provides
        @Singleton
        fun provideWarmUpTimings(): WarmUpTimings = WarmUpTimings()

        @Provides
        @Singleton
        @com.drivingcoach.data.location.ApplicationScope
        fun provideApplicationScope(
            @com.drivingcoach.di.IoDispatcher dispatcher: CoroutineDispatcher
        ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @javax.inject.Inject
    lateinit var locationUpdates: ScriptedLocationUpdates

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
    fun homeSubscribesToLocationWithoutBeingAsked() {
        // The point of the feature: the cold fix is paid for here, not in front of a
        // disabled Capture button on Track Setup.
        awaitUntil("Home to start warming the GPS up") {
            locationUpdates.activeSubscriptions == 1
        }
    }

    /**
     * The chip is gone; the warm-up it reported is not.
     *
     * Asserting only that no GPS text is on screen would pass on a Home screen that failed to
     * inflate at all, and asserting only the subscription would not notice the message coming
     * back. Both halves together are the actual requirement: acquisition running, user not
     * told about it.
     */
    @Test
    fun warmUpRunsWithoutTellingTheUserAboutIt() {
        awaitUntil("warm-up to start") { locationUpdates.activeSubscriptions == 1 }

        // Accurate enough that the old chip would have read "GPS ready · ±4 m".
        locationUpdates.emit(accuracyM = 4f)

        val deadline = System.currentTimeMillis() + HELD_SETTLE_MS
        while (System.currentTimeMillis() < deadline) {
            assertEquals(
                "Hiding the readiness message must not stop the warm-up it described: the " +
                    "cold fix is still paid for on Home, not at the track edge (SRS TS-16).",
                1,
                locationUpdates.activeSubscriptions
            )
            val gpsText = visibleTexts().filter { it.contains("gps", ignoreCase = true) }
            assertTrue(
                "Home must not report GPS state to the user, but showed: $gpsText",
                gpsText.isEmpty()
            )
            Thread.sleep(50)
        }
    }

    /**
     * Rewritten for Incident 12. This test used to assert that Home going away released the
     * chip, which is precisely the defect: `moveToState(CREATED)` stops the Activity by
     * putting another one in front of it, exactly as navigating to Track Setup does. The old
     * assertion therefore certified the behaviour that threw the user's warm fix away at the
     * track edge.
     *
     * The chip must stay held while the app is still in front of the user.
     */
    @Test
    fun homeGoingAwayDoesNotReleaseTheChip() {
        awaitUntil("warm-up to start") { locationUpdates.activeSubscriptions == 1 }

        scenario.moveToState(Lifecycle.State.CREATED)

        val deadline = System.currentTimeMillis() + HELD_SETTLE_MS
        while (System.currentTimeMillis() < deadline) {
            assertEquals(
                "The GNSS chip was released because Home stopped, while the app was still in " +
                    "the foreground. That is Incident 12: the walk from the paddock to the " +
                    "start line is the one journey the warm-up exists to serve.",
                1,
                locationUpdates.activeSubscriptions
            )
            Thread.sleep(50)
        }
    }

    /**
     * The bound that replaces the screen-scoped one: high-accuracy location must not be held
     * while the user cannot see that it is held.
     *
     * The app is backgrounded by going to the launcher rather than by `moveToState(CREATED)`,
     * because the latter leaves the app in the foreground and so cannot express this.
     */
    @Test
    fun backgroundingTheAppReleasesTheChip() {
        awaitUntil("warm-up to start") { locationUpdates.activeSubscriptions == 1 }

        goToLauncher()

        awaitUntil("GPS to be released once the app is no longer in the foreground") {
            locationUpdates.activeSubscriptions == 0
        }
    }

    @Test
    fun returningToTheAppWarmsUpAgain() {
        awaitUntil("warm-up to start") { locationUpdates.activeSubscriptions == 1 }
        goToLauncher()
        awaitUntil("GPS to be released") { locationUpdates.activeSubscriptions == 0 }

        returnToTheApp()

        awaitUntil("warm-up to resume when the user comes back") {
            locationUpdates.activeSubscriptions == 1
        }
    }

    /** Backgrounds the whole app the way the home button does. */
    private fun goToLauncher() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Brings the existing task back the way tapping the launcher icon does.
     *
     * `ActivityScenario.moveToState(RESUMED)` cannot be used here: once the app is genuinely
     * in the background the scenario no longer drives it, and the call fails with the Activity
     * stuck in STOPPED.
     */
    private fun returnToTheApp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(requireNotNull(launch) { "no launch intent for the app under test" })
    }

    /** Every piece of text the user can actually see right now. */
    private fun visibleTexts(): List<String> {
        val texts = mutableListOf<String>()
        scenario.onActivity { activity ->
            collectVisibleTexts(activity.window.decorView, texts)
        }
        return texts
    }

    private fun collectVisibleTexts(view: View, into: MutableList<String>) {
        if (view.visibility != View.VISIBLE) return
        when (view) {
            is ViewGroup -> for (i in 0 until view.childCount) {
                collectVisibleTexts(view.getChildAt(i), into)
            }
            is TextView -> view.text?.toString()?.takeIf { it.isNotBlank() }?.let(into::add)
        }
    }

    private companion object {
        /** Long enough that a late, wrongly-scoped release would be caught rather than missed. */
        const val HELD_SETTLE_MS = 1_000L
    }
}
