package com.drivingcoach.ui.recording

import android.Manifest
import android.app.Application
import android.content.Context
import android.view.View
import androidx.core.os.bundleOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.drivingcoach.R
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.dao.TrophyDao
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.data.profile.LocalUser
import com.drivingcoach.data.repository.ProgressionRepository
import com.drivingcoach.data.repository.SessionRepository
import com.drivingcoach.data.track.TrackRepository
import com.drivingcoach.di.DataStoreModule
import com.drivingcoach.di.SplashModule
import com.drivingcoach.lap.LapDiagnosticsWriter
import com.drivingcoach.progression.LevelLadder
import com.drivingcoach.progression.TrophyType
import com.drivingcoach.service.TelemetryForegroundService
import com.drivingcoach.testing.MutablePreferencesDataStore
import com.drivingcoach.testing.ProgressionFaults
import com.drivingcoach.testing.awaitStartupResolved
import com.drivingcoach.testing.awaitUntil
import com.drivingcoach.testing.currentDestinationId
import com.drivingcoach.ui.MainActivity
import com.drivingcoach.ui.progression.ProgressionText
import com.drivingcoach.ui.rewards.RewardPopup
import com.drivingcoach.ui.rewards.RewardsViewModel
import com.drivingcoach.ui.splash.SplashTimings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * L2 (ASPICE SWE.5) end-to-end coverage for SRS PG-12: stopping a recording settles the
 * session's distance and trophies and opens the Rewards screen, whose Continue leads to the
 * Session Result.
 *
 * Drives the real app from Home: the Baltar replay of incident 15 (12 laps of 72-84 s,
 * about 13.7 km) is pointed at by the session, the Recording screen is opened on it, and
 * the Stop button is pressed. Nothing between the button and the Rewards screen is faked,
 * except in [awardFailureStillReachesSessionResult], where the trophy table refuses writes.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StopToRewardsEndToEndTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object SignedInStartupModule {

        @Provides
        @Singleton
        fun provideDataStore(): DataStore<Preferences> = MutablePreferencesDataStore(
            mutablePreferencesOf(
                booleanPreferencesKey("onboarding_complete") to true,
                stringPreferencesKey("user_name") to "Instrumented Driver",
                booleanPreferencesKey("driver_profile_complete") to true,
                // No history to recompute: only the stop is to award anything.
                ProgressionRepository.RULES_V2_APPLIED to true
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
    }

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var trackRepository: TrackRepository
    @Inject lateinit var sessionDao: SessionDao
    @Inject lateinit var trophyDao: TrophyDao
    @Inject lateinit var progression: ProgressionRepository
    @Inject lateinit var lapDiagnosticsWriter: LapDiagnosticsWriter

    private lateinit var telemetry: File
    private var scenario: ActivityScenario<MainActivity>? = null
    private var stopped = true
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hiltRule.inject()
        ProgressionFaults.reset()
        telemetry = copyFixtureOutOfAssets()
    }

    @After
    fun tearDown() {
        ProgressionFaults.reset()
        scenario?.close()
        // Only when a failure left it running: once stopped, a stop intent would create a
        // fresh service after the Hilt component is gone, and crash the process.
        if (!stopped) TelemetryForegroundService.stopRecording(context)
        lapDiagnosticsWriter.sidecarFor(telemetry).delete()
        telemetry.delete()
    }

    @Test
    fun stopOpensRewardsWithTheFinisherAndContinueReachesSessionResult() {
        val sessionId = baltarSession()
        stopRecordingOf(sessionId)

        val distanceM = runBlocking { sessionDao.getSessionByIdSync(sessionId)!!.distanceM }
        assertTrue("the replay measures about 13.7 km, was $distanceM", distanceM != null && distanceM in 12_000.0..15_500.0)
        val awarded = runBlocking { trophyDao.getForSession(sessionId).mapNotNull { TrophyType.fromName(it.type) } }
        assertTrue("12 laps over 5 km earn The Finisher: $awarded", TrophyType.FINISHER in awarded)
        assertFalse("a first visit cannot be a Pace Setter", TrophyType.PACE_SETTER in awarded)
        assertFalse("nor a Top Gun", TrophyType.TOP_GUN in awarded)

        val rewards = runBlocking { progression.rewards(sessionId)!! }
        assertEquals("0 → 13.7 km crosses the 8 km milestone", listOf(8), rewards.milestonesKm)
        assertTrue("8 km makes a Cadet", rewards.levelUp)
        val popups = RewardsViewModel.popupsFor(rewards)
        assertEquals(RewardPopup.Milestone(8), popups[popups.size - 2])
        assertEquals(RewardPopup.LevelUp(LevelLadder.levels[1]), popups.last())

        // The cabinet order puts The Finisher first.
        awaitUntil("the first pop-up") { overlayVisible() }
        onView(withId(R.id.popupName)).check(matches(withText(ProgressionText(context).trophyWithMetal(TrophyType.FINISHER))))
        onView(withId(R.id.popupHeadline)).check(matches(withText(R.string.rewards_popup_trophy)))
        dismissAllPopups(expected = popups.size)

        continueToSessionResult()
    }

    /**
     * PG-12: a failure while awarding must not strand the driver. The trophy write throws,
     * the transaction rolls back, and the driver still sees Rewards (with nothing new)
     * and then the Session Result.
     */
    @Test
    fun awardFailureStillReachesSessionResult() {
        ProgressionFaults.failTrophyWrites = true
        val sessionId = baltarSession()
        stopRecordingOf(sessionId)

        assertTrue("the fault was actually hit", ProgressionFaults.failures > 0)
        runBlocking {
            assertTrue("nothing awarded", trophyDao.getForSession(sessionId).isEmpty())
            assertNull("the distance update rolled back with the trophies", sessionDao.getSessionByIdSync(sessionId)!!.distanceM)
        }
        onView(withId(R.id.rewardsNoTrophies)).perform(scrollTo()).check(matches(isDisplayed()))
        assertFalse("no celebration for a failed award", overlayVisible())

        continueToSessionResult()
    }

    private fun baltarSession(): Long = runBlocking {
        val baltar = trackRepository.getTrack("baltar")!!
        sessionRepository.createSession(
            SessionEntity(
                userId = LocalUser.ID,
                trackName = baltar.name,
                startedAt = System.currentTimeMillis(),
                rawFilePath = telemetry.absolutePath,
                trackId = baltar.id,
                startLineLat1 = baltar.startLine.lat1,
                startLineLng1 = baltar.startLine.lng1,
                startLineLat2 = baltar.startLine.lat2,
                startLineLng2 = baltar.startLine.lng2
            )
        )
    }

    /** Home → Recording on [sessionId] → Stop, and wait for the Rewards screen. */
    private fun stopRecordingOf(sessionId: Long) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        assertEquals(R.id.homeFragment, scenario!!.awaitStartupResolved())
        scenario!!.onActivity { navController(it).navigate(R.id.action_home_to_recording, bundleOf("sessionId" to sessionId)) }
        stopped = false
        awaitUntil("recording to start", timeoutMs = 15_000L) {
            runCatching { onView(withId(R.id.stopRecordingButton)).check(matches(isEnabled())) }.isSuccess
        }

        onView(withId(R.id.stopRecordingButton)).perform(click())
        stopped = true

        awaitUntil("lap processing to finish and Rewards to open", timeoutMs = 90_000L) {
            scenario!!.currentDestinationId() == R.id.rewardsFragment
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun continueToSessionResult() {
        onView(withId(R.id.continueButton)).perform(click())
        awaitUntil("the Session Result") { scenario!!.currentDestinationId() == R.id.sessionResultFragment }
        var previous: Int? = null
        var stack = emptyList<Int>()
        scenario!!.onActivity { activity ->
            val nav = navController(activity)
            previous = nav.previousBackStackEntry?.destination?.id
            stack = nav.currentBackStack.value.map { it.destination.id }
        }
        assertEquals("Back from the result goes Home", R.id.homeFragment, previous)
        assertFalse("neither Recording nor Rewards is left behind: $stack",
            R.id.recordingFragment in stack || R.id.rewardsFragment in stack)
    }

    private fun dismissAllPopups(expected: Int) {
        var dismissed = 0
        while (overlayVisible() && dismissed <= expected + 1) {
            onView(withId(R.id.popupDismiss)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            dismissed++
        }
        assertFalse("the pop-ups should all close", overlayVisible())
        assertEquals("one pop-up per new level trophy, the milestone and the level up", expected, dismissed)
    }

    private fun overlayVisible(): Boolean {
        var visible = false
        scenario!!.onActivity { visible = it.findViewById<View>(R.id.popupOverlay)?.visibility == View.VISIBLE }
        return visible
    }

    private fun navController(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController

    private fun copyFixtureOutOfAssets(): File {
        val destination = File(context.cacheDir, "baltar2-rewards-replay.jsonl")
        InstrumentationRegistry.getInstrumentation().context.assets.open("baltar2/telemetry.jsonl").use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        assertTrue("the shared L1 fixture should have been packaged", destination.length() > 0)
        return destination
    }
}
