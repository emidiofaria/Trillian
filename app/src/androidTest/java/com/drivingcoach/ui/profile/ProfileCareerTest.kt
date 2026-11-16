package com.drivingcoach.ui.profile

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.drivingcoach.R
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.repository.ProgressionRepository
import com.drivingcoach.progression.LevelLadder
import com.drivingcoach.progression.ProgressionFormat
import com.drivingcoach.progression.TrophyType
import com.drivingcoach.testing.ProgressionSeed
import com.drivingcoach.testing.awaitUntil
import com.drivingcoach.testing.launchFragmentInHiltContainer
import com.drivingcoach.ui.progression.CareerViews
import com.drivingcoach.ui.progression.ProgressionText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * L2 (ASPICE SWE.5) coverage for the Career section of Profile (SRS PG-15, PG-16, PG-17) and
 * the Profile stats row (DP-11).
 *
 * One steady session at a new track, recorded under the recorder's user id: 14.4 km in six
 * minutes; The Finisher, Surgical and Apex Exit; the Carbon Copy, Clean Air and Full Stint
 * medals. That is a Level 2 Cadet who has the Bronze for Novice but not yet the 24 km.
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProfileCareerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var sessionDao: SessionDao
    @Inject lateinit var lapDao: LapDao
    @Inject lateinit var progression: ProgressionRepository

    // Production DataStore (not replaced in tests): Clear data empties it, so the driver's
    // onboarding and replay flags are snapshotted here and restored for later test classes.
    @Inject lateinit var dataStore: DataStore<Preferences>
    private var savedPrefs: Preferences? = null

    private lateinit var seed: ProgressionSeed
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var root: View? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        savedPrefs = runBlocking { dataStore.data.first() }
        seed = ProgressionSeed(sessionDao, lapDao, File(context.filesDir, "telemetry"))
        runBlocking {
            val id = seed.session(startedAt = 1_700_000_000_000L, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
            progression.awardSession(id)
        }
        launchFragmentInHiltContainer<ProfileFragment> { root = requireView() }
        awaitUntil("the career to load") {
            runCatching {
                onView(withId(R.id.levelName)).check(matches(withText(context.getString(R.string.progression_level_title, 2, "Cadet"))))
            }.isSuccess
        }
    }

    @After
    fun tearDown() {
        seed.cleanUp()
        savedPrefs?.let { saved -> runBlocking { dataStore.updateData { saved.toMutablePreferences() } } }
    }

    @Test
    fun levelCardShowsTheNextLevelChecklist() {
        onView(withId(R.id.levelName)).perform(scrollTo()).check(matches(isDisplayed()))
        val rows = childTexts(R.id.levelRequirements)
        assertEquals("Novice needs a distance and a Bronze", 2, rows.size)
        assertTrue("distance not met yet: ${rows[0]}", rows[0].startsWith("⬜") && rows[0].contains("24 km"))
        assertTrue("the Finisher is already in hand: ${rows[1]}", rows[1].startsWith("✅"))
    }

    @Test
    fun roadmapListsAllSevenLevels() {
        assertEquals(7, childCount(R.id.levelRoadmap))
        val rows = allTexts(R.id.levelRoadmap)
        val text = ProgressionText(context)
        (1..7).forEach { n -> assertTrue("roadmap names Level $n: $rows", rows.contains(text.levelName(n))) }
        assertTrue("Level 7 asks for a Gold: $rows", rows.contains("450 km"))
    }

    /**
     * DP-11 with the v3.07 user-id fix: the session was written under the id the recorder
     * uses, and the Profile shows its distance, time and level trophies. Under v3.07 the
     * Profile queried another id and showed zeros.
     */
    @Test
    fun statsShowDrivenKmHoursAndTrophies() {
        val km = runBlocking { progression.career().first().lifetimeKm }
        val expectedKm = ProgressionFormat.drivenKm(km)
        awaitUntil("the stats row to load") {
            runCatching { onView(withId(R.id.statDrivenKm)).check(matches(withText(expectedKm))) }.isSuccess
        }
        onView(withId(R.id.statHoursDriven)).check(matches(withText(ProgressionText(context).drivenTime(6 * 60_000L))))
        onView(withId(R.id.statHoursDriven)).check(matches(withText("6 min")))
        onView(withId(R.id.statTrophies)).check(matches(withText("3")))
    }

    @Test
    fun cabinetShowsEightTrophiesThenSixMedals() {
        assertEquals(
            "8 trophies, then the medals header, then 6 medals",
            TrophyType.trophies.map { it.name } + CareerViews.MEDALS_HEADER_TAG + TrophyType.medals.map { it.name },
            childTags(R.id.trophyCabinet)
        )

        var finisherCount = ""
        var paceHowToWin = ""
        var steadyHint = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val cabinet = root!!.findViewById<LinearLayout>(R.id.trophyCabinet)
            val finisher = cabinet.findViewWithTag<View>(TrophyType.FINISHER.name)
            finisherCount = finisher.findViewById<android.widget.TextView>(R.id.trophyCount).text.toString()
            val pace = cabinet.findViewWithTag<View>(TrophyType.PACE_SETTER.name)
            paceHowToWin = pace.findViewById<android.widget.TextView>(R.id.trophyHowToWin)
                .takeIf { it.visibility == View.VISIBLE }?.text?.toString().orEmpty()
            val plateau = cabinet.findViewWithTag<View>(TrophyType.PLATEAU_BREAKER.name)
            steadyHint = plateau.findViewById<android.widget.TextView>(R.id.trophyHint)
                .takeIf { it.visibility == View.VISIBLE }?.text?.toString().orEmpty()
        }
        assertEquals(context.getString(R.string.trophy_count, 1), finisherCount)
        assertTrue("an unearned trophy explains how to win it", paceHowToWin.isNotBlank())
        assertTrue("and gives a personal hint: $steadyHint", steadyHint.contains("5") && steadyHint.contains("15"))
        assertEquals(
            listOf(TrophyType.FINISHER, TrophyType.SURGICAL, TrophyType.APEX_EXIT,
                TrophyType.CARBON_COPY, TrophyType.CLEAN_AIR, TrophyType.FULL_STINT).map { it.name }.sorted(),
            earnedTiles().sorted()
        )
        onView(withText(ProgressionText(context).trophyName(TrophyType.FULL_STINT))).perform(scrollTo()).check(matches(isDisplayed()))
    }

    /**
     * PG-14 with DR-07: Clear data wipes the sessions, so the career is recomputed from
     * nothing. 14.4 km, three trophies and three medals before; Rookie, 0 km and a locked
     * cabinet after.
     */
    @Test
    fun clearDataResetsCareerToRookie() {
        val navController = TestNavHostController(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.profileFragment)
            Navigation.setViewNavController(root!!, navController)
        }
        val text = ProgressionText(context)
        val zeroKm = text.kmLabel(0.0, LevelLadder.levels[1])
        assertTrue("precondition: the seeded distance shows", levelKmLabel() != zeroKm)
        assertEquals("precondition", 6, earnedTiles().size)

        onView(withId(R.id.clearDataButton)).perform(click())
        onView(withText(R.string.clear_data_confirm)).inRoot(isDialog()).perform(click())

        awaitUntil("the wipe to finish and leave for onboarding") {
            var destination: Int? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync { destination = navController.currentDestination?.id }
            destination == R.id.onboardingFragment
        }
        awaitUntil("the career to re-emit at zero") { levelKmLabel() == zeroKm }

        onView(withId(R.id.levelName)).check(matches(withText(context.getString(R.string.progression_level_title, 1, "Rookie"))))
        assertEquals("every trophy locked again", emptyList<String>(), earnedTiles())
        val rows = childTexts(R.id.levelRequirements)
        assertTrue("no Finisher in hand any more: $rows", rows.all { it.startsWith("⬜") })
        runBlocking {
            assertEquals(0.0, progression.career().first().lifetimeKm, 0.0)
            assertTrue(sessionDao.getAllSessionsOldestFirst().isEmpty())
        }
    }

    private fun levelKmLabel(): String {
        var label = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            label = root!!.findViewById<android.widget.TextView>(R.id.levelKmLabel).text.toString()
        }
        return label
    }

    /** Tags of cabinet tiles that show a count rather than "Not yet earned". */
    private fun earnedTiles(): List<String> {
        val earned = mutableListOf<String>()
        val notEarned = context.getString(R.string.trophy_not_earned)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val cabinet = root!!.findViewById<LinearLayout>(R.id.trophyCabinet)
            TrophyType.values().forEach { type ->
                val tile = cabinet.findViewWithTag<View>(type.name)
                val status = tile.findViewById<android.widget.TextView>(R.id.trophyStatus).text.toString()
                val countShown = tile.findViewById<View>(R.id.trophyCount).visibility == View.VISIBLE
                if (countShown || status != notEarned) earned += type.name
            }
        }
        return earned
    }

    /** Every TextView text under [id], depth first, joined by newlines. */
    private fun allTexts(id: Int): String {
        val texts = mutableListOf<String>()
        fun walk(view: View) {
            if (view is android.widget.TextView) texts += view.text.toString()
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { walk(root!!.findViewById(id)) }
        return texts.joinToString("\n")
    }

    private fun childTags(id: Int): List<String> {
        val tags = mutableListOf<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val group = root!!.findViewById<LinearLayout>(id)
            for (i in 0 until group.childCount) (group.getChildAt(i).tag as? String)?.let { tags += it }
        }
        return tags
    }

    private fun childCount(id: Int): Int {
        var count = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            count = root!!.findViewById<LinearLayout>(id).childCount
        }
        return count
    }

    private fun childTexts(id: Int): List<String> {
        val texts = mutableListOf<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val group = root!!.findViewById<LinearLayout>(id)
            for (i in 0 until group.childCount) {
                (group.getChildAt(i) as? android.widget.TextView)?.let { texts += it.text.toString() }
            }
        }
        return texts
    }
}
