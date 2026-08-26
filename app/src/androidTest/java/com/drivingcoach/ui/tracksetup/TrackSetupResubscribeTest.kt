package com.drivingcoach.ui.tracksetup

import android.Manifest
import androidx.core.os.bundleOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.drivingcoach.R
import com.drivingcoach.data.location.ApplicationScope
import com.drivingcoach.data.location.LocationUpdates
import com.drivingcoach.data.location.WarmUpTimings
import com.drivingcoach.di.DataStoreModule
import com.drivingcoach.di.IoDispatcher
import com.drivingcoach.di.LocationModule
import com.drivingcoach.di.SplashModule
import com.drivingcoach.testing.ScriptedLocationUpdates
import com.drivingcoach.testing.SeededPreferencesDataStore
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * L2 (ASPICE SWE.5) regression guard for **SRS TS-15**.
 *
 * Track Setup used to subscribe to location exactly once, at view creation, while tearing
 * the subscription down in `onStop()`. Any screen-off, notification pull or app switch —
 * all of which are likely while walking to the track edge — therefore killed location
 * updates permanently, leaving "Acquiring GPS..." on screen forever rather than for the
 * duration of a cold fix.
 *
 * The assertion that matters is [locationUpdatesResumeAfterTheScreenStops]: without the
 * fix, `subscribeCount` stays at 1 and the screen never recovers.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class, LocationModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TrackSetupResubscribeTest {

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
        @ApplicationScope
        fun provideApplicationScope(
            @IoDispatcher dispatcher: CoroutineDispatcher
        ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Inject
    lateinit var locationUpdates: ScriptedLocationUpdates

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        hiltRule.inject()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        assertEquals(R.id.homeFragment, scenario.awaitStartupResolved())
        navigateToTrackSetup()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun trackSetupSubscribesOnArrival() {
        awaitUntil("Track Setup to subscribe to location") {
            locationUpdates.activeSubscriptions >= 1
        }
    }

    @Test
    fun locationUpdatesResumeAfterTheScreenStops() {
        awaitUntil("Track Setup to subscribe to location") {
            locationUpdates.activeSubscriptions >= 1
        }
        val subscriptionsBefore = locationUpdates.subscribeCount

        // Screen-off / app switch while the user walks to the track edge.
        scenario.moveToState(Lifecycle.State.CREATED)
        awaitUntil("location updates to stop with the screen") {
            locationUpdates.activeSubscriptions == 0
        }

        scenario.moveToState(Lifecycle.State.RESUMED)

        awaitUntil("location updates to resume when the screen returns") {
            locationUpdates.subscribeCount > subscriptionsBefore &&
                locationUpdates.activeSubscriptions >= 1
        }
    }

    @Test
    fun captureBecomesAvailableOnceTheFixIsAccurateEnough() {
        awaitUntil("Track Setup to subscribe to location") {
            locationUpdates.activeSubscriptions >= 1
        }

        locationUpdates.emit(accuracyM = 30f)
        Thread.sleep(SETTLE_MS)
        assertTrue(
            "A 30 m fix must not unlock capture",
            !isCaptureEnabled()
        )

        locationUpdates.emit(accuracyM = 4f)

        awaitUntil("capture to unlock once the fix is accurate enough") { isCaptureEnabled() }
    }

    @Test
    fun captureStillWorksAfterAStopStartCycle() {
        awaitUntil("Track Setup to subscribe to location") {
            locationUpdates.activeSubscriptions >= 1
        }

        scenario.moveToState(Lifecycle.State.CREATED)
        awaitUntil("location updates to stop") { locationUpdates.activeSubscriptions == 0 }
        scenario.moveToState(Lifecycle.State.RESUMED)
        awaitUntil("location updates to resume") { locationUpdates.activeSubscriptions >= 1 }

        locationUpdates.emit(accuracyM = 4f)

        // The screen must be fully functional after the interruption, not merely subscribed.
        awaitUntil("capture to unlock after the interruption") { isCaptureEnabled() }
    }

    private fun navigateToTrackSetup() {
        scenario.onActivity { activity ->
            val navHost = activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            navHost.navController.navigate(
                R.id.trackSetupFragment,
                bundleOf("trackName" to "Instrumented Track")
            )
        }
        awaitUntil("Track Setup to become the current destination") {
            scenario.currentDestinationId() == R.id.trackSetupFragment
        }
    }

    private fun isCaptureEnabled(): Boolean {
        var enabled = false
        scenario.onActivity { activity ->
            enabled = activity.findViewById<android.view.View>(R.id.capturePointAButton)
                ?.isEnabled == true
        }
        return enabled
    }

    private companion object {
        /** Long enough for a fix to have been applied had it been going to unlock capture. */
        const val SETTLE_MS = 500L
    }
}
