package com.drivingcoach.ui.home

import android.view.View
import android.widget.TextView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ActivityScenario
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
 * L2 (ASPICE SWE.5) coverage for **SRS TS-16 to TS-18**: Home warms the GPS up before the
 * user reaches Track Setup, shows how close it is, and releases it when the screen stops.
 *
 * Fixes are scripted rather than real. An emulator cannot produce the coarse-then-accurate
 * progression the readiness chip exists to display, and a test that waits for real
 * satellites would be the flakiest in the suite.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class, LocationModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeGpsChipTest {

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

    @Test
    fun chipReportsAcquiringUntilAFixIsAccurateEnough() {
        awaitUntil("warm-up to start") { locationUpdates.activeSubscriptions == 1 }

        locationUpdates.emit(accuracyM = 35f)

        awaitUntil("the chip to appear while acquiring") { chipIsVisible() }
        assertTrue(
            "A 35 m fix must not be advertised as ready",
            chipText().contains("acquiring", ignoreCase = true)
        )
    }

    @Test
    fun chipReportsReadyOnceTheFixIsUsable() {
        awaitUntil("warm-up to start") { locationUpdates.activeSubscriptions == 1 }

        locationUpdates.emit(accuracyM = 4f)

        awaitUntil("the chip to report readiness") {
            chipIsVisible() && chipText().contains("ready", ignoreCase = true)
        }
        assertTrue("Accuracy should be shown", chipText().contains("4"))
    }

    @Test
    fun readinessDegradesIfAccuracyWorsens() {
        awaitUntil("warm-up to start") { locationUpdates.activeSubscriptions == 1 }

        locationUpdates.emit(accuracyM = 4f)
        awaitUntil("the chip to report readiness") {
            chipText().contains("ready", ignoreCase = true)
        }

        locationUpdates.emit(accuracyM = 45f)

        // A stale green chip would send the user out to the track edge with a fix that
        // cannot legally place a start line.
        awaitUntil("the chip to fall back to acquiring") {
            chipText().contains("acquiring", ignoreCase = true)
        }
    }

    @Test
    fun backgroundingHomeReleasesTheChip() {
        awaitUntil("warm-up to start") { locationUpdates.activeSubscriptions == 1 }

        scenario.moveToState(Lifecycle.State.CREATED)

        // High-accuracy GPS must never be left running behind a backgrounded app.
        awaitUntil("GPS to be released when Home stops") {
            locationUpdates.activeSubscriptions == 0
        }
    }

    @Test
    fun returningToHomeWarmsUpAgain() {
        awaitUntil("warm-up to start") { locationUpdates.activeSubscriptions == 1 }
        scenario.moveToState(Lifecycle.State.CREATED)
        awaitUntil("GPS to be released") { locationUpdates.activeSubscriptions == 0 }

        scenario.moveToState(Lifecycle.State.RESUMED)

        awaitUntil("warm-up to resume when Home comes back") {
            locationUpdates.activeSubscriptions == 1
        }
    }

    private fun chipIsVisible(): Boolean {
        var visible = false
        scenario.onActivity { activity ->
            visible = activity.findViewById<View>(R.id.gpsReadinessChip)?.visibility == View.VISIBLE
        }
        return visible
    }

    private fun chipText(): String {
        var text = ""
        scenario.onActivity { activity ->
            text = activity.findViewById<TextView>(R.id.gpsReadinessChip)?.text?.toString() ?: ""
        }
        return text
    }
}
