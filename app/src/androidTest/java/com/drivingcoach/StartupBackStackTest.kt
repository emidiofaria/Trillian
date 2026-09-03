package com.drivingcoach

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.drivingcoach.di.DataStoreModule
import com.drivingcoach.di.SplashModule
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Singleton

/**
 * L2 (ASPICE SWE.5) coverage for **SRS UI-05**: the loading screen must not linger on the
 * navigation back stack, so pressing back from the first functional screen exits the app.
 *
 * Uses the real [MainActivity] rather than a fragment test container. That is deliberate:
 * the requirement is about the navigation graph's `popUpTo` behaviour inside the real host,
 * which a fragment-in-container harness cannot express — and it sidesteps the
 * `HiltTestActivity` problems that block the older instrumented tests.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StartupBackStackTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object SignedInStartupModule {

        @Provides
        @Singleton
        fun provideDataStore(): DataStore<Preferences> = SeededPreferencesDataStore(
            mutablePreferencesOf(
                booleanPreferencesKey("onboarding_complete") to true,
                // V1 identity is a local driver profile, not a token. Seeding a JWT here
                // would no longer reach Home — see SRS DR-01 … DR-04.
                stringPreferencesKey("user_name") to "Instrumented Driver",
                booleanPreferencesKey("driver_profile_complete") to true
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
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun seededSessionLandsOnHome() {
        assertEquals(
            "An onboarded driver with a saved profile should resolve straight to Home",
            R.id.homeFragment,
            scenario.awaitStartupResolved()
        )
    }

    @Test
    fun loadingScreenIsPoppedOffTheBackStack() {
        val destination = scenario.awaitStartupResolved()
        assertNotEquals(R.id.splashFragment, destination)

        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        awaitUntil("the activity to finish instead of returning to the loading screen") {
            scenario.state == Lifecycle.State.DESTROYED
        }
    }
}
