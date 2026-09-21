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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import javax.inject.Singleton

/**
 * L2 (ASPICE SWE.5) coverage for the fork a session now begins with.
 *
 * Before the track library there was one way to start: name the session, capture a
 * line, record. There are now two, and the branch that did not change is as much at
 * risk as the one that did - a driver at a circuit the app has never heard of must
 * still be able to capture a line, and that path is no longer the default one.
 *
 * The session name is carried by hand through both branches as a navigation
 * argument. Losing it does not fail: the session is simply recorded as "Unknown
 * Track", which looks like a naming quirk rather than a defect and is not
 * recoverable afterwards. That is what most of this class is watching for.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class, LocationModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SessionStartForkTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object SessionStartForkStartupModule {

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

    /**
     * The branch that existed before the library. A circuit the app has never seen
     * has to remain reachable, and reachable without first being offered a list of
     * circuits that are all somewhere else.
     */
    @Test
    fun theNewCircuitBranchStillReachesTrackSetup() {
        navigate(R.id.trackSetupFragment, bundleOf("trackName" to SESSION_NAME))

        assertEquals(
            "capturing a line by hand must survive the arrival of the library",
            R.id.trackSetupFragment, currentDestination()
        )
    }

    @Test
    fun theSelectTrackBranchReachesTheLibrary() {
        navigateToTrackList()

        assertEquals(R.id.trackListFragment, currentDestination())
        awaitUntil("the bundled circuit to be offered") { trackCount() >= 1 }
    }

    /**
     * The session name is typed once, on Home, and then carried by hand through
     * every screen of whichever branch the driver took. A drop anywhere along the
     * way is silent: the session is stored as "Unknown Track", which reads as a
     * naming quirk rather than a defect and cannot be corrected afterwards.
     */
    @Test
    fun theSessionNameSurvivesTheSelectTrackBranch() {
        navigateToTrackList()
        assertEquals(
            "the name should reach the list",
            SESSION_NAME, currentArgs()?.getString("sessionName")
        )

        openFirstCircuit()
        assertEquals(
            "and should still be there on the confirm screen, which is the last " +
                "chance to carry it into the session row",
            SESSION_NAME, currentArgs()?.getString("sessionName")
        )
    }

    @Test
    fun theSessionNameSurvivesTheNewCircuitBranch() {
        navigate(R.id.trackSetupFragment, bundleOf("trackName" to SESSION_NAME))

        assertEquals(
            "the branch that did not change must not have been broken by the one that did",
            SESSION_NAME, currentArgs()?.getString("trackName")
        )
    }

    /**
     * Choosing the wrong circuit from a list is an easy mistake and must be a cheap
     * one. Back from Confirm belongs on the list, not on Home: sending the driver
     * back to Home would make them retype the session name, which is the one piece
     * of the session that cannot be recovered later.
     */
    @Test
    fun backFromConfirmReturnsToTheListWithTheNameIntact() {
        navigateToTrackList()
        openFirstCircuit()

        scenario.onActivity { activity -> navController(activity).popBackStack() }

        awaitUntil("the list to become the current destination again") {
            currentDestination() == R.id.trackListFragment
        }
        assertEquals(
            "a driver who picked the wrong circuit should not have to retype the session name",
            SESSION_NAME, currentArgs()?.getString("sessionName")
        )
    }

    /**
     * The identifier the whole feature rests on. Everything downstream - the
     * surveyed start line, the heading prior that guards the first crossing, the
     * lap length behind LD-22 - is reached by looking this id up again after
     * recording stops. If it is not carried to the recording screen there is
     * nothing to look up, and the session records and detects laps exactly as an
     * uncatalogued one would, with no error anywhere.
     */
    @Test
    fun theChosenCircuitIdIsCarriedToTheConfirmScreen() {
        navigateToTrackList()
        openFirstCircuit()

        val trackId = currentArgs()?.getString("trackId")
        assertNotNull("the confirm screen must know which circuit it is confirming", trackId)
        assertTrue("and it must not be blank, which the recording screen reads as absent", trackId!!.isNotBlank())
        assertEquals("the bundled circuit is the one on the list", "baltar", trackId)
    }

    private fun navigate(destination: Int, args: android.os.Bundle) {
        scenario.onActivity { activity -> navController(activity).navigate(destination, args) }
        awaitUntil("destination $destination to become current") { currentDestination() == destination }
    }

    private fun navigateToTrackList() =
        navigate(R.id.trackListFragment, bundleOf("sessionName" to SESSION_NAME))

    private fun openFirstCircuit() {
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
    }

    private fun navController(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
            .navController

    private fun currentDestination(): Int? = scenario.currentDestinationId()

    private fun currentArgs(): android.os.Bundle? {
        var args: android.os.Bundle? = null
        scenario.onActivity { activity ->
            args = (
                activity.supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                )
                .childFragmentManager.fragments.firstOrNull()?.arguments
        }
        return args
    }

    private fun trackCount(): Int {
        var count = 0
        scenario.onActivity { activity ->
            count = activity.findViewById<RecyclerView>(R.id.trackList)?.adapter?.itemCount ?: 0
        }
        return count
    }

    private companion object {
        const val SESSION_NAME = "Thursday Club Night"
    }
}
