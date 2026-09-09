package com.drivingcoach.ui.tracksetup

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.core.os.bundleOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.drivingcoach.R
import com.drivingcoach.data.location.ApplicationScope
import com.drivingcoach.data.location.GpsReadiness
import com.drivingcoach.data.location.LocationUpdates
import com.drivingcoach.data.location.LocationWarmUp
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
 * L2 (ASPICE SWE.5) regression guard for **Incident 12** and **SRS TS-16 / TS-18**.
 *
 * ### What broke
 *
 * The GPS warm-up exists so the 30–60 s cold time-to-first-fix elapses in the paddock rather
 * than at the track edge. Its lifetime, however, was bound to the *visibility of the Home
 * screen*: `HomeFragment.onStop()` called `stop()`, which cancelled the job and called
 * `removeLocationUpdates`. The single navigation the feature exists to serve — Home → Track
 * Setup — was therefore also its stop condition. The user watched the badge turn green, walked
 * to the line, and was met with "Acquiring GPS…" and a disabled CAPTURE button anyway.
 *
 * ### Why the existing tests did not catch it
 *
 * `TrackSetupResubscribeTest` performs this exact navigation in `setUp()` and then asserts
 * `activeSubscriptions >= 1` — a predicate that holds whether or not the warm-up survived,
 * because Track Setup immediately opens a subscription of its own. The interesting event
 * happens *between* two observations, so [ScriptedLocationUpdates] now keeps a timeline and
 * this test asserts on continuity rather than on a sample.
 *
 * [gpsSubscriptionSurvivesTheHandoverFromHomeToTrackSetup] is the assertion that matters: it
 * must FAIL before the fix and PASS after it.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class, LocationModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WarmUpHandoverTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object HandoverStartupModule {

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

    @Inject
    lateinit var warmUp: LocationWarmUp

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

    /**
     * The core Incident 12 assertion.
     *
     * The user waits out the cold fix on Home, sees green, and walks to the line. The chip
     * must still be held when Track Setup opens. Sampling `activeSubscriptions` afterwards
     * cannot see the defect — only the timeline can.
     */
    @Test
    fun gpsSubscriptionSurvivesTheHandoverFromHomeToTrackSetup() {
        awaitWarmUpReady()

        navigateToTrackSetup()
        awaitUntil("Track Setup to subscribe to location") {
            locationUpdates.activeSubscriptions >= 1
        }

        assertTrue(
            "The GNSS subscription was released while navigating Home → Track Setup, so the " +
                "warm-up the user waited for was thrown away at the worst possible moment " +
                "(Incident 12). Subscription timeline:${locationUpdates.describeTimeline()}",
            locationUpdates.subscriptionHeldContinuously
        )
    }

    /**
     * The same defect expressed as the user experiences it.
     *
     * With a cold receiver modelled at [ACQUISITION_LATENCY_MS], a fix arriving immediately
     * after Track Setup opens is only delivered if the chip stayed warm across the handover.
     * If the subscription was dropped, the receiver is re-acquiring and the fix is discarded —
     * which is exactly the disabled CAPTURE button reported from the track.
     */
    @Test
    fun captureIsAvailableOnArrivalWhenGpsWasAlreadyReady() {
        locationUpdates.acquisitionLatencyMs = ACQUISITION_LATENCY_MS
        awaitWarmUpReady(timeoutMs = ACQUISITION_LATENCY_MS + 10_000L)

        navigateToTrackSetup()
        awaitUntil("Track Setup to subscribe to location") {
            locationUpdates.activeSubscriptions >= 1
        }

        // The fix the user is standing in: good accuracy, delivered straight away.
        locationUpdates.emit(accuracyM = 4f)

        awaitUntil(
            "CAPTURE to be enabled on arrival without a second acquisition wait " +
                "(Incident 12). Subscription timeline:${locationUpdates.describeTimeline()}",
            timeoutMs = ARRIVAL_BUDGET_MS
        ) { isCaptureEnabled() }
    }

    /**
     * The privacy guarantee that replaces the old screen-scoped stop: the receiver must not
     * be held while the app is not in the foreground.
     *
     * The app is backgrounded by launching the home screen rather than by
     * `ActivityScenario.moveToState(CREATED)`. That helper stops the Activity by launching an
     * empty Activity *on top of it*, which leaves the app in the foreground — so it exercises
     * the previous, screen-scoped bug rather than this guarantee. Going to the launcher is the
     * thing the user actually does when they pocket the phone.
     */
    @Test
    fun warmUpStopsWhenTheAppLeavesTheForeground() {
        awaitWarmUpReady()

        goToLauncher()

        awaitUntil("GNSS to be released once the app is no longer in the foreground") {
            locationUpdates.activeSubscriptions == 0
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
     * Guards the mitigation for risk R2. A configuration change tears the Activity down and
     * rebuilds it; if warm-up were bound to that, rotating the phone while walking to the
     * line would silently reintroduce Incident 12 in a form that is far harder to spot.
     */
    @Test
    fun warmUpSurvivesAConfigurationChange() {
        awaitWarmUpReady()
        locationUpdates.log.reset()

        scenario.recreate()
        awaitUntil("Home to come back after recreation") {
            scenario.currentDestinationId() == R.id.homeFragment
        }

        assertTrue(
            "Rotating the phone released the GNSS chip. Subscription timeline:" +
                locationUpdates.describeTimeline(),
            !locationUpdates.log.everReachedZero
        )
    }

    /**
     * Drives the warm-up to [GpsReadiness.Ready] the way satellites would: by repeating fixes
     * until one lands. Repetition matters once a latency is modelled, because early fixes are
     * discarded while the receiver is still acquiring.
     */
    private fun awaitWarmUpReady(timeoutMs: Long = 10_000L) {
        awaitUntil("warm-up to reach GPS ready on Home", timeoutMs = timeoutMs) {
            locationUpdates.emit(accuracyM = 4f)
            warmUp.readiness.value is GpsReadiness.Ready
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
        /**
         * Stands in for a cold time-to-first-fix. Short enough to keep the suite quick, long
         * enough that a re-acquisition cannot be mistaken for an uninterrupted warm chip.
         */
        const val ACQUISITION_LATENCY_MS = 2_000L

        /**
         * How long CAPTURE may take to unlock after arriving at the line. Deliberately well
         * under [ACQUISITION_LATENCY_MS]: if the chip had to re-acquire, this budget expires
         * first and the test fails, which is the whole point.
         */
        const val ARRIVAL_BUDGET_MS = 1_000L
    }
}
