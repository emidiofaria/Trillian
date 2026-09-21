package com.drivingcoach.ui.tracklist

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import javax.inject.Singleton

/**
 * L2 (ASPICE SWE.5) coverage for what the Confirm screen tells the driver about a
 * circuit before they commit a session to it (TL-04, TL-06).
 *
 * This screen is the only thing standing between picking the wrong row in a list
 * and recording an entire session against the wrong start line, which cannot be
 * corrected afterwards. The facts it shows - length, corner count, expected lap
 * window - are there to be recognised rather than read, so a driver who picked
 * the wrong circuit sees that they did.
 *
 * The lap window is also the last remaining consumer of `slowestLapMs`. That field
 * used to be a cutoff that discarded detected laps; LD-22 now measures against the
 * surveyed lap length instead, and the envelope is display only. A field that is
 * only ever displayed needs a test that looks at the display, or nothing in the
 * suite is holding it.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class, LocationModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TrackConfirmDisplayTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object TrackConfirmDisplayStartupModule {

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
        navigateToConfirm()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun theCircuitIsNamedSoTheDriverCanSeeWhichOneTheyPicked() {
        awaitUntil("the circuit name to be shown") { text(R.id.trackName).isNotBlank() }

        // Compared case-insensitively: the style uppercases the label, and which casing the
        // designer chose is not what this test is defending.
        assertEquals(
            "Kartódromo de Baltar".lowercase(),
            text(R.id.trackName).lowercase()
        )
        assertTrue(
            "the location disambiguates circuits that share a name",
            text(R.id.trackLocation).contains("Baltar")
        )
    }

    /**
     * The start line is already set, and saying so is the point: this is the screen
     * that replaces capturing one, and a driver who has only ever used the capture
     * flow needs to be told that the step did not go missing.
     */
    @Test
    fun theStartLineIsDescribedAsAlreadySetWithItsWidth() {
        awaitUntil("the start line summary to be shown") {
            text(R.id.startLineSummary).isNotBlank()
        }

        val summary = text(R.id.startLineSummary)
        assertTrue("the driver should be told the line is already in place: '$summary'", summary.contains("Already set"))
        assertTrue(
            "and how wide it is, because a line too narrow is what incident 13 was: '$summary'",
            summary.contains("11.0 m") || summary.contains("m wide")
        )
    }

    /**
     * Length and corner count come from the survey, and the lap window from the
     * catalogue's envelope. Baltar is 1020 m, 13 corners, 40 s to 2 minutes.
     *
     * The window is the only place `slowestLapMs` is still used. If someone removes
     * the field on the grounds that LD-22 no longer reads it, this is what fails.
     */
    @Test
    fun theSurveyedFactsAndTheExpectedLapWindowAreShown() {
        awaitUntil("the circuit facts to be shown") { text(R.id.trackFacts).isNotBlank() }

        val facts = text(R.id.trackFacts)
        assertTrue("the surveyed length belongs here: '$facts'", facts.contains("1020 m"))
        assertTrue("as does the corner count: '$facts'", facts.contains("13 corners"))
        assertTrue(
            "and the lap window, which is the last remaining consumer of slowestLapMs: '$facts'",
            facts.contains("0:40.0") && facts.contains("2:00.0")
        )
    }

    private fun navigateToConfirm() {
        scenario.onActivity { activity ->
            navController(activity).navigate(
                R.id.trackConfirmFragment,
                bundleOf("sessionName" to "Display Test", "trackId" to "baltar")
            )
        }
        awaitUntil("the confirm screen to become the current destination") {
            scenario.currentDestinationId() == R.id.trackConfirmFragment
        }
    }

    private fun navController(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
            .navController

    private fun text(viewId: Int): String {
        var value = ""
        scenario.onActivity { activity ->
            value = activity.findViewById<android.widget.TextView>(viewId)?.text?.toString() ?: ""
        }
        return value
    }
}
