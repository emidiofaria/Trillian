package com.drivingcoach.ui.tracksetup

import android.Manifest
import androidx.core.os.bundleOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import javax.inject.Singleton

/**
 * L2 (ASPICE SWE.5) guard for **finding F4 of Incident 12** and **SRS TS-21**.
 *
 * ### What this protects
 *
 * Fix A1 keeps the GNSS chip warm across the walk from the paddock to the start line. That is
 * the right trade, but it makes a second, quieter defect reachable: a fix acquired where the
 * user *was* is now much more likely to still be in hand where the user *is*. Nothing on the
 * capture path checked a fix's age, the screen never invalidated the last fix it saw, and the
 * ready flag never went back down once raised.
 *
 * The consequence is not a visible failure. Point A lands tens of metres from the actual
 * line, every lap in the session is offset by the same amount, and the times look entirely
 * plausible. That is worse than a crash: the user trusts the number.
 *
 * ### Wait, do not block
 *
 * The gate must express "not yet" and then clear itself the moment a fresh fix lands. Latching
 * CAPTURE off, or requiring the user to leave and come back, would replace a silent data error
 * with the exact frustration Incident 12 was raised about.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class, LocationModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StartLineFreshnessTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FreshnessStartupModule {

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
        navigateToTrackSetup()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    /**
     * The dangerous case, in the exact shape the fused client produces it: a well-formed,
     * accurate fix whose only fault is its age. Nothing about the coordinate looks wrong, so
     * only an explicit age check can refuse it.
     */
    @Test
    fun aStaleFixDoesNotUnlockStartLineCapture() {
        repeat(5) { locationUpdates.emitStale(accuracyM = 4f, ageMs = STALE_AGE_MS) }

        assertNeverEnabledWithin(STALE_SETTLE_MS)
    }

    /**
     * The other half of the guarantee, and the more important one for the user: refusing a
     * stale fix must delay capture, never withhold it. As soon as the receiver reports where
     * the user is actually standing, the button unlocks with no further interaction.
     */
    @Test
    fun captureUnlocksAsSoonAsAFreshFixArrives() {
        repeat(5) { locationUpdates.emitStale(accuracyM = 4f, ageMs = STALE_AGE_MS) }
        assertNeverEnabledWithin(STALE_SETTLE_MS)

        awaitUntil("CAPTURE to unlock once a fresh fix lands", timeoutMs = 5_000L) {
            locationUpdates.emit(accuracyM = 4f)
            isCaptureEnabled()
        }
    }

    private fun assertNeverEnabledWithin(durationMs: Long) {
        val deadline = System.currentTimeMillis() + durationMs
        while (System.currentTimeMillis() < deadline) {
            assertFalse(
                "A fix ${STALE_AGE_MS}ms old unlocked start-line capture. Point A would be " +
                    "recorded where the user was, not where they are, and every lap time in " +
                    "the session would carry the same silent offset (Incident 12, F4).",
                isCaptureEnabled()
            )
            Thread.sleep(50)
        }
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
        awaitUntil("Track Setup to subscribe to location") {
            locationUpdates.activeSubscriptions >= 1
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
        /** A plausible paddock-to-line walk. Far outside any defensible freshness window. */
        const val STALE_AGE_MS = 90_000L

        /** Long enough that a late unlock would be caught rather than missed. */
        const val STALE_SETTLE_MS = 1_000L
    }
}
