package com.drivingcoach.ui.rewards

import android.content.Context
import android.view.View
import androidx.core.os.bundleOf
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
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
import com.drivingcoach.progression.ProgressionFormat
import com.drivingcoach.progression.TrophyType
import com.drivingcoach.testing.ProgressionSeed
import com.drivingcoach.testing.awaitUntil
import com.drivingcoach.testing.launchFragmentInHiltContainer
import com.drivingcoach.ui.progression.ProgressionText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * L2 (ASPICE SWE.5) coverage for the Rewards screen: SRS PG-12, PG-19, PG-20.
 *
 * The second visit to a track beats the first by two seconds with six identical laps,
 * which earns The Finisher, Personal Best, Consistency Gold and Track Master, and
 * crosses 25 km with a Bronze already in hand, so Novice is reached too.
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RewardsScreenTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var sessionDao: SessionDao
    @Inject lateinit var lapDao: LapDao
    @Inject lateinit var progression: ProgressionRepository

    private lateinit var seed: ProgressionSeed
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val text get() = ProgressionText(context)

    @Before
    fun setUp() {
        hiltRule.inject()
        seed = ProgressionSeed(sessionDao, lapDao, File(context.filesDir, "telemetry"))
    }

    @After
    fun tearDown() = seed.cleanUp()

    @Test
    fun trophiesPopOneAtATimeThenLevelUpThenTheSummary() {
        val sessionId = seedSecondVisit()
        val rewards = runBlocking { progression.rewards(sessionId)!! }
        val awarded = rewards.newTrophies
        assertEquals(
            setOf(
                TrophyType.FINISHER_BRONZE, TrophyType.PB_SILVER,
                TrophyType.CONSISTENCY_GOLD, TrophyType.TRACK_MASTER_GOLD
            ),
            awarded.toSet()
        )

        launchRewards(sessionId)

        awarded.forEach { type ->
            awaitText(R.id.popupName, text.trophyName(type))
            onView(withId(R.id.popupHeadline)).check(matches(withText(R.string.rewards_popup_trophy)))
            onView(withId(R.id.popupDismiss)).perform(click())
        }
        awaitText(R.id.popupHeadline, context.getString(R.string.rewards_popup_level_up))
        onView(withId(R.id.popupName)).check(matches(withText(context.getString(R.string.progression_level_title, 2, "Novice"))))
        onView(withId(R.id.popupDismiss)).perform(click())
        awaitOverlayGone()

        assertEquals("six 58 s laps at 40 m/s", 13.9, rewards.distanceKm, 0.05)
        onView(withId(R.id.summaryLaps)).perform(scrollTo()).check(matches(withText("6")))
        val km = { value: Double -> ProgressionFormat.drivenKm(value) }
        onView(withId(R.id.summaryDistance)).check(matches(withText("${km(rewards.distanceKm)} km")))
        onView(withId(R.id.summaryBestLap)).check(matches(withText("0:58.000")))
        onView(withId(R.id.summaryConsistency)).check(matches(withText("0.000s")))
        onView(withId(R.id.rewardsKmBeforeAfter)).check(matches(withText("${km(rewards.before.lifetimeKm)} km → ${km(rewards.after.lifetimeKm)} km")))
        onView(withId(R.id.rewardsRecentProgress)).perform(scrollTo()).check(
            matches(withText(context.getString(R.string.rewards_progress_faster, "2.000s", ProgressionSeed.TRACK)))
        )
    }

    @Test
    fun continueReplacesRewardsWithTheSessionResult() {
        val sessionId = seedSecondVisit()
        val navController = launchRewards(sessionId)

        // Back dismisses pop-ups one by one, as the button does.
        awaitUntil("the first pop-up") { overlayVisible() }
        repeat(POPUPS) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        awaitOverlayGone()

        onView(withId(R.id.continueButton)).perform(click())
        assertEquals(R.id.sessionResultFragment, navController.currentDestination?.id)
        assertNotEquals(
            "Back from the result must not return to the celebration",
            R.id.rewardsFragment,
            navController.previousBackStackEntry?.destination?.id
        )
    }

    @Test
    fun aSessionWithoutTrophiesSaysSoAndStillContinues() {
        val sessionId = runBlocking {
            seed.session(startedAt = START, lapTimesMs = listOf(60_000L, 61_000L)).also {
                progression.awardSession(it)
            }
        }
        val navController = launchRewards(sessionId)

        onView(withId(R.id.rewardsNoTrophies)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.rewardsRecentProgress)).perform(scrollTo()).check(
            matches(withText(context.getString(R.string.rewards_progress_first, ProgressionSeed.TRACK)))
        )
        onView(withId(R.id.continueButton)).perform(click())
        assertEquals(R.id.sessionResultFragment, navController.currentDestination?.id)
    }

    /**
     * PG-19: system Back on the Rewards screen behaves like Continue. Rewards is reached
     * from Recording, so a Back that simply popped would land on a finished recording.
     */
    @Test
    fun backActsLikeContinue() {
        val sessionId = seedSecondVisit()
        val args = bundleOf("sessionId" to sessionId)
        val navController = TestNavHostController(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.homeFragment)
            navController.navigate(R.id.action_home_to_recording, args)
            navController.navigate(R.id.action_recording_to_rewards, args)
        }
        assertEquals(R.id.rewardsFragment, navController.currentDestination?.id)
        launchFragmentInHiltContainer<RewardsFragment>(args) {
            Navigation.setViewNavController(requireView(), navController)
            this@RewardsScreenTest.activity = requireActivity()
        }

        awaitUntil("the first pop-up") { overlayVisible() }
        repeat(POPUPS) {
            onView(withId(R.id.popupDismiss)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        awaitOverlayGone()
        assertEquals("dismissing pop-ups does not leave", R.id.rewardsFragment, navController.currentDestination?.id)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity!!.onBackPressedDispatcher.onBackPressed()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(R.id.sessionResultFragment, navController.currentDestination?.id)
        val stack = navController.currentBackStack.value.map { it.destination.id }
        assertFalse("never back to Recording: $stack", R.id.recordingFragment in stack)
        assertFalse("Rewards is gone from the stack: $stack", R.id.rewardsFragment in stack)
        assertEquals(R.id.homeFragment, navController.previousBackStackEntry?.destination?.id)
    }

    private var activity: androidx.fragment.app.FragmentActivity? = null

    private fun seedSecondVisit(): Long = runBlocking {
        val first = seed.session(startedAt = START, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
        progression.awardSession(first)
        seed.session(startedAt = START + DAY, lapTimesMs = ProgressionSeed.STEADY_QUICKER).also {
            progression.awardSession(it)
        }
    }

    private fun launchRewards(sessionId: Long): TestNavHostController {
        val navController = TestNavHostController(context)
        val args = bundleOf("sessionId" to sessionId)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.rewardsFragment, args)
        }
        launchFragmentInHiltContainer<RewardsFragment>(args) {
            Navigation.setViewNavController(requireView(), navController)
            this@RewardsScreenTest.activity = requireActivity()
        }
        return navController
    }

    private fun overlayVisible(): Boolean {
        var visible = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            visible = activity?.findViewById<View>(R.id.popupOverlay)?.visibility == View.VISIBLE
        }
        return visible
    }

    private fun awaitOverlayGone() = awaitUntil("the pop-ups to be dismissed") { !overlayVisible() }

    private fun awaitText(id: Int, expected: String) = awaitUntil("view showing \"$expected\"") {
        runCatching { onView(withId(id)).check(matches(withText(expected))) }.isSuccess
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val DAY = 86_400_000L
        const val POPUPS = 5
    }
}
