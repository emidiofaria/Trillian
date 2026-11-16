package com.drivingcoach.ui.home

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.drivingcoach.R
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.dao.TrophyDao
import com.drivingcoach.data.repository.ProgressionRepository
import com.drivingcoach.di.DataStoreModule
import com.drivingcoach.di.SplashModule
import com.drivingcoach.progression.LevelLadder
import com.drivingcoach.progression.ProgressionFormat
import com.drivingcoach.progression.TrophyType
import com.drivingcoach.testing.MutablePreferencesDataStore
import com.drivingcoach.testing.ProgressionSeed
import com.drivingcoach.testing.awaitStartupResolved
import com.drivingcoach.testing.awaitUntil
import com.drivingcoach.testing.currentDestinationId
import com.drivingcoach.ui.MainActivity
import com.drivingcoach.ui.progression.ProgressionText
import com.drivingcoach.ui.splash.SplashTimings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * L2 (ASPICE SWE.5) coverage for the Home level card and next goal (SRS PG-18) and the
 * one-off replay of earlier sessions that opening Home triggers (PG-13).
 *
 * The preferences are in memory and writable, so the replay's done-flag can be observed
 * and each test decides whether the replay has already happened.
 */
@LargeTest
@UninstallModules(DataStoreModule::class, SplashModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeLevelCardTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object SignedInStartupModule {

        @Provides
        @Singleton
        fun provideDataStore(): DataStore<Preferences> = MutablePreferencesDataStore(
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
    }

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var sessionDao: SessionDao
    @Inject lateinit var lapDao: LapDao
    @Inject lateinit var trophyDao: TrophyDao
    @Inject lateinit var progression: ProgressionRepository
    @Inject lateinit var dataStore: DataStore<Preferences>

    private lateinit var seed: ProgressionSeed
    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val text get() = ProgressionText(context)

    @Before
    fun setUp() {
        hiltRule.inject()
        seed = ProgressionSeed(sessionDao, lapDao, File(context.filesDir, "telemetry"))
    }

    @After
    fun tearDown() {
        scenario?.close()
        seed.cleanUp()
    }

    /**
     * About 14.4 + 13.9 km and two Finishers: Novice, with 50 km the next thing missing.
     * The figures come from the measured career, since great-circle distance on the seeded
     * trace is a little under speed × time.
     */
    @Test
    fun showsLevelAndNextGoal() {
        runBlocking {
            markReplayDone()
            progression.awardSession(seed.session(START, ProgressionSeed.STEADY_MINUTES))
            progression.awardSession(seed.session(START + DAY, ProgressionSeed.STEADY_QUICKER))
        }
        val km = runBlocking { progression.career().first().lifetimeKm }
        assertEquals("precondition", 28.3, km, 0.1)
        launchHome()

        awaitText(R.id.levelName, R.id.homeLevelCard, context.getString(R.string.progression_level_title, 2, "Novice"))
        onCard(R.id.levelBadge).check(matches(withText(context.getString(R.string.progression_level_badge, 2))))
        onCard(R.id.levelKmLabel).check(
            matches(withText("${ProgressionFormat.drivenKm(km)} km driven · next level at 50 km"))
        )
        onView(allOf(withId(R.id.nextGoalText), isDescendantOfA(withId(R.id.homeNextGoal))))
            .check(matches(withText("Drive ${ProgressionFormat.remainingKm(50.0 - km)} km more to reach Level 3 · Challenger.")))
    }

    @Test
    fun tappingLevelCardOpensProfile() {
        runBlocking { markReplayDone() }
        launchHome()
        awaitText(R.id.levelName, R.id.homeLevelCard, context.getString(R.string.progression_level_title, 1, "Rookie"))

        onView(withId(R.id.homeLevelCard)).perform(click())

        awaitUntil("Profile to open") { scenario!!.currentDestinationId() == R.id.profileFragment }
    }

    /**
     * PG-13: a session recorded before progression existed is replayed the first time Home
     * opens, and only then. A session that appears after the replay is left alone by the
     * next launch; it would be awarded when its own recording finishes, not by a replay.
     */
    @Test
    fun openingHomeRunsHistoryReplayOnce() {
        val earlier = runBlocking { seed.session(START, ProgressionSeed.STEADY_MINUTES) }
        runBlocking {
            assertTrue("precondition: never awarded", trophyDao.getAll().isEmpty())
            assertTrue("precondition: replay pending", dataStore.data.first()[ProgressionRepository.BACKFILL_DONE] != true)
        }

        launchHome()
        awaitUntil("the replay to mark itself done") {
            runBlocking { dataStore.data.first()[ProgressionRepository.BACKFILL_DONE] == true }
        }
        runBlocking {
            assertEquals(
                setOf(TrophyType.FINISHER_BRONZE, TrophyType.CONSISTENCY_GOLD),
                trophyDao.getForSession(earlier).map { TrophyType.fromName(it.type) }.toSet()
            )
        }
        val km = runBlocking { progression.career().first().lifetimeKm }
        assertEquals("the replay measured the earlier trace", 14.4, km, 0.05)
        val label = text.kmLabel(km, LevelLadder.levels[1])
        awaitText(R.id.levelKmLabel, R.id.homeLevelCard, label)
        scenario!!.close()

        val later = runBlocking { seed.session(START + DAY, ProgressionSeed.STEADY_QUICKER) }
        launchHome()
        awaitText(R.id.levelKmLabel, R.id.homeLevelCard, label)
        // Give a second replay, were it to run, time to write: it would take milliseconds.
        Thread.sleep(SETTLE_MS)

        runBlocking {
            assertTrue("no replay of the later session", trophyDao.getForSession(later).isEmpty())
            assertEquals("the earlier session is not awarded twice", 2, trophyDao.getAll().size)
            assertEquals(null, sessionDao.getSessionByIdSync(later)!!.distanceM)
        }
    }

    private suspend fun markReplayDone() {
        dataStore.edit { it[ProgressionRepository.BACKFILL_DONE] = true }
    }

    private fun launchHome() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        assertEquals(R.id.homeFragment, scenario!!.awaitStartupResolved())
    }

    private fun onCard(id: Int) = onView(allOf(withId(id), isDescendantOfA(withId(R.id.homeLevelCard))))

    private fun awaitText(id: Int, parent: Int, expected: String) = awaitUntil("Home showing \"$expected\"") {
        runCatching {
            onView(allOf(withId(id), isDescendantOfA(withId(parent)))).check(matches(withText(expected)))
        }.isSuccess
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val DAY = 86_400_000L
        const val SETTLE_MS = 1_500L
    }
}
