package com.drivingcoach.ui.tracklist

import android.Manifest
import androidx.core.os.bundleOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import javax.inject.Singleton

/**
 * L2 (ASPICE SWE.5) coverage for the track library, and specifically for the gate
 * that the library could easily have removed by accident.
 *
 * Picking a known circuit skips Track Setup. Track Setup is where the app refused
 * to begin recording until the receiver was reporting a usable position, so the
 * shortcut walks straight past three requirements (SRS TS-04, TS-21, TS-22)
 * unless the Confirm screen carries them. A session started on a cold fix does
 * not fail visibly - it produces lap times that look plausible and are not - so
 * this is checked in the real navigation host rather than argued about in review.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class, LocationModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TrackLibraryGateTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object TrackLibraryStartupModule {

        @Provides
        @Singleton
        fun provideDataStore(): DataStore<Preferences> = SeededPreferencesDataStore(
            mutablePreferencesOf(
                booleanPreferencesKey("onboarding_complete") to true,
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
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun theBundledCircuitIsOfferedWithoutCapturingALine() {
        navigateToTrackList()

        awaitUntil("the bundled circuit to appear in the list") { trackCount() >= 1 }
    }

    /**
     * The gate itself. Recording must not be reachable from a circuit selection
     * until the receiver reports a position worth recording against.
     */
    @Test
    fun startRecordingStaysLockedUntilGpsIsReady() {
        navigateToTrackConfirm()

        assertFalse(
            "START RECORDING was available before any fix arrived. Selecting a saved " +
                "circuit would then start a session on a cold receiver, which is the " +
                "check Track Setup used to perform (SRS TS-04, TS-21, TS-22).",
            isStartEnabled()
        )

        repeat(5) { locationUpdates.emitStale(accuracyM = 4f, ageMs = STALE_AGE_MS) }
        Thread.sleep(SETTLE_MS)
        assertFalse(
            "A fix ${STALE_AGE_MS}ms old unlocked recording. It describes where the " +
                "driver was, not where they are (Incident 12, F4).",
            isStartEnabled()
        )

        awaitUntil("START RECORDING to unlock once a fresh fix lands", timeoutMs = 5_000L) {
            locationUpdates.emit(accuracyM = 4f)
            isStartEnabled()
        }
    }

    /** Selecting a known circuit must not route the driver through line capture. */
    @Test
    fun choosingACircuitSkipsTrackSetup() {
        navigateToTrackConfirm()

        assertTrue(
            "selecting a circuit should lead to confirmation, not to capturing a line",
            currentDestination() == R.id.trackConfirmFragment
        )
    }

    private fun navigateToTrackList() {
        scenario.onActivity { activity ->
            navController(activity).navigate(
                R.id.trackListFragment,
                bundleOf("sessionName" to "Instrumented Session")
            )
        }
        awaitUntil("the track list to become the current destination") {
            currentDestination() == R.id.trackListFragment
        }
    }

    private fun navigateToTrackConfirm() {
        navigateToTrackList()
        awaitUntil("the bundled circuit to appear in the list") { trackCount() >= 1 }

        scenario.onActivity { activity ->
            activity.findViewById<RecyclerView>(R.id.trackList)
                ?.findViewHolderForAdapterPosition(0)
                ?.itemView
                ?.performClick()
        }

        awaitUntil("the confirm screen to become the current destination") {
            currentDestination() == R.id.trackConfirmFragment
        }
        awaitUntil("the confirm screen to subscribe to location") {
            locationUpdates.activeSubscriptions >= 1
        }
    }

    private fun navController(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
            .navController

    private fun currentDestination(): Int? = scenario.currentDestinationId()

    private fun trackCount(): Int {
        var count = 0
        scenario.onActivity { activity ->
            count = activity.findViewById<RecyclerView>(R.id.trackList)?.adapter?.itemCount ?: 0
        }
        return count
    }

    private fun isStartEnabled(): Boolean {
        var enabled = false
        scenario.onActivity { activity ->
            enabled = activity.findViewById<android.view.View>(R.id.startRecordingButton)
                ?.isEnabled == true
        }
        return enabled
    }

    private companion object {
        const val STALE_AGE_MS = 90_000L
        const val SETTLE_MS = 500L
    }
}
