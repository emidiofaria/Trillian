package com.drivingcoach.ui.profile

import android.content.Context
import android.view.View
import android.widget.LinearLayout
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
import com.drivingcoach.progression.TrophyType
import com.drivingcoach.testing.ProgressionSeed
import com.drivingcoach.testing.awaitUntil
import com.drivingcoach.testing.launchFragmentInHiltContainer
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
 * L2 (ASPICE SWE.5) coverage for the Career section of Profile: SRS PG-15, PG-16, PG-17.
 *
 * One steady session at a new track: 14.4 km, The Finisher and Consistency Gold. That is
 * a Rookie who has the Bronze for Novice but not yet the distance.
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

    private lateinit var seed: ProgressionSeed
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var root: View? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        seed = ProgressionSeed(sessionDao, lapDao, File(context.filesDir, "telemetry"))
        runBlocking {
            val id = seed.session(startedAt = 1_700_000_000_000L, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
            progression.awardSession(id)
        }
        launchFragmentInHiltContainer<ProfileFragment> { root = requireView() }
        awaitUntil("the career to load") {
            runCatching {
                onView(withId(R.id.levelName)).check(matches(withText(context.getString(R.string.progression_level_title, 1, "Rookie"))))
            }.isSuccess
        }
    }

    @After
    fun tearDown() = seed.cleanUp()

    @Test
    fun levelCardShowsTheNextLevelChecklist() {
        onView(withId(R.id.levelName)).perform(scrollTo()).check(matches(isDisplayed()))
        val rows = childTexts(R.id.levelRequirements)
        assertEquals("Novice needs a distance and a Bronze", 2, rows.size)
        assertTrue("distance not met yet: ${rows[0]}", rows[0].startsWith("⬜") && rows[0].contains("25 km"))
        assertTrue("the Finisher is already in hand: ${rows[1]}", rows[1].startsWith("✅"))
    }

    @Test
    fun roadmapListsAllSixLevels() {
        assertEquals(6, childCount(R.id.levelRoadmap))
    }

    @Test
    fun cabinetShowsEarnedAndLockedTrophies() {
        assertEquals(6, childCount(R.id.trophyCabinet))

        val text = ProgressionText(context)
        var finisherCount = ""
        var pbHowToWinShown = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val cabinet = root!!.findViewById<LinearLayout>(R.id.trophyCabinet)
            val finisher = cabinet.findViewWithTag<View>(TrophyType.FINISHER_BRONZE.name)
            finisherCount = finisher.findViewById<android.widget.TextView>(R.id.trophyCount).text.toString()
            val pb = cabinet.findViewWithTag<View>(TrophyType.PB_SILVER.name)
            pbHowToWinShown = pb.findViewById<View>(R.id.trophyHowToWin).visibility == View.VISIBLE
        }
        assertEquals(context.getString(R.string.trophy_count, 1), finisherCount)
        assertTrue("an unearned trophy explains how to win it", pbHowToWinShown)
        onView(withText(text.trophyName(TrophyType.CONSISTENCY_GOLD))).perform(scrollTo()).check(matches(isDisplayed()))
    }

    /**
     * PG-14 with DR-07: Clear data wipes the sessions, so the career is recomputed from
     * nothing. 14.4 km and two trophies before; Rookie, 0 km and a locked cabinet after.
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
        assertEquals("precondition", 2, earnedTiles().size)

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
