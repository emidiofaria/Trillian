package com.drivingcoach.ui.progression

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivingcoach.progression.LevelLadder
import com.drivingcoach.progression.NextGoal
import com.drivingcoach.progression.ProgressionGoals
import com.drivingcoach.progression.RecentProgress
import com.drivingcoach.progression.Requirement
import com.drivingcoach.progression.SessionFacts
import com.drivingcoach.progression.TrophyCounts
import com.drivingcoach.progression.TrophyHint
import com.drivingcoach.progression.TrophyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The words the progression screens show (SRS PG-15, PG-16, PG-18, PG-20, PG-23) and the
 * number style they share with the coaching text (OC-34): no dash directly before a number,
 * gaps in seconds to three decimals, and a '.' decimal separator whatever the device locale.
 *
 * Instrumented because [ProgressionText] reads the app's real string resources; there is no
 * Robolectric in this project.
 */
@RunWith(AndroidJUnit4::class)
class ProgressionTextTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val text = ProgressionText(context)

    @Test
    fun checklistLineShowsTickAndValue() {
        assertEquals("✅ 100 km (124.6 km driven)", text.requirementLine(Requirement.Distance(100.0, 124.68)))
        assertEquals("⬜ 100 km (99.9 km driven)", text.requirementLine(Requirement.Distance(100.0, 99.99)))
    }

    @Test
    fun checklistLineShowsMissingTrophyWithHowTo() {
        assertEquals(
            "⬜ 3 × The Finisher (Bronze) (you have 1) · drive at least 5 km and 5 laps in one session",
            text.requirementLine(Requirement.Trophies(TrophyType.FINISHER_BRONZE, 3, 1))
        )
        assertEquals(
            "✅ 1 × Personal Best (Silver) (you have 2) · " +
                "beat your best lap on a track you've driven before",
            text.requirementLine(Requirement.Trophies(TrophyType.PB_SILVER, 1, 2))
        )
    }

    @Test
    fun finisherHintSentence() {
        assertEquals(
            "Best session so far: 4.9 km / 7 laps · need 5 km and 5 laps",
            text.hint(TrophyHint.Finisher(4.999, 7))
        )
        assertEquals("Record a session to see how close you are.", text.hint(TrophyHint.Finisher(null, null)))
    }

    @Test
    fun consistencyHintUsesThreeDecimals() {
        assertEquals(
            "Your steadiest 5 laps varied by 3.457s · get under 3.000s for Consistency Bronze",
            text.hint(TrophyHint.Consistency(TrophyType.CONSISTENCY_BRONZE, 3456.7, 3000.0))
        )
        assertEquals(
            "Your steadiest 5 laps varied by 0.050s, already inside this tier. " +
                "A session that reaches a higher tier takes that one instead.",
            text.hint(TrophyHint.Consistency(TrophyType.CONSISTENCY_SILVER, 50.0, 1500.0))
        )
        // The checklist's how-to for a consistency tier carries its threshold the same way.
        assertTrue(text.howToWin(TrophyType.CONSISTENCY_GOLD).contains("under 0.750s of variation"))
    }

    @Test
    fun personalBestHintNamesTrackAndTime() {
        assertEquals("Your best at Baltar: 1:12.345 · beat it", text.hint(TrophyHint.PersonalBest("Baltar", 72_345L)))
        assertEquals(
            "Your best at Baltar: 1:12.345 · beat it Then keep 5 laps under 0.750s of variation in that same session.",
            text.hint(TrophyHint.TrackMaster(TrophyHint.PersonalBest("Baltar", 72_345L), 900.0))
        )
    }

    @Test
    fun nextGoalSentencesForKmTrophyAndMax() {
        // 25 km needed, 13.71 driven: 11.29 remaining rounds UP so the driver is never short.
        assertEquals("Drive 11.3 km more to reach Level 2 · Novice.", text.nextGoal(NextGoal.DriveKm(2, 25.0 - 13.71)))
        assertEquals(
            "Win 2 × Personal Best (Silver) to reach Level 5 · Expert. " +
                "Beat your best lap on a track you've driven before.",
            text.nextGoal(NextGoal.EarnTrophies(5, TrophyType.PB_SILVER, 2))
        )
        assertEquals("You've reached the top level. Keep chasing personal bests!", text.nextGoal(NextGoal.MaxLevel))

        // And the goal the ladder actually derives for a fresh driver reads the same way.
        val fresh = ProgressionGoals.nextGoal(LevelLadder.progress(0.0, TrophyCounts()))
        assertEquals("Drive 25.0 km more to reach Level 2 · Novice.", text.nextGoal(fresh))
    }

    @Test
    fun recentProgressFasterSlowerFirstVisit() {
        assertEquals(
            "You were 1.234s faster than your last session at Baltar!",
            text.recentProgress(RecentProgress.Faster("Baltar", 1_234L))
        )
        assertEquals(
            "You were 0.007s slower than your last session at Baltar. Next time!",
            text.recentProgress(RecentProgress.Slower("Baltar", 7L))
        )
        assertEquals(
            "First timed session at Baltar. Your best lap here is now the time to beat.",
            text.recentProgress(RecentProgress.FirstTime("Baltar"))
        )
    }

    @Test
    fun noDashBeforeAnyNumber() {
        val all = everyString(text)
        assertTrue("generated a meaningful sample", all.size > 80)
        val dashBeforeDigit = Regex("[-‐‑‒–—―−]\\s*\\d")
        val offenders = all.filter { dashBeforeDigit.containsMatchIn(it) }
        assertTrue("OC-34: a dash reads as a minus sign; offenders: $offenders", offenders.isEmpty())
    }

    @Test
    fun usesLocaleUsUnderGermanDefault() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val config = Configuration(context.resources.configuration).apply { setLocale(Locale.GERMANY) }
            val german = ProgressionText(context.createConfigurationContext(config))

            assertEquals(
                "Your steadiest 5 laps varied by 3.457s · get under 3.000s for Consistency Bronze",
                german.hint(TrophyHint.Consistency(TrophyType.CONSISTENCY_BRONZE, 3456.7, 3000.0))
            )
            assertEquals("✅ 100 km (1234.5 km driven)", german.requirementLine(Requirement.Distance(100.0, 1234.56)))
            assertEquals(
                "You were 1.234s faster than your last session at Baltar!",
                german.recentProgress(RecentProgress.Faster("Baltar", 1_234L))
            )
            val decimalComma = Regex("\\d,\\d")
            val offenders = everyString(german).filter { decimalComma.containsMatchIn(it) }
            assertTrue("no German decimal comma anywhere: $offenders", offenders.isEmpty())
        } finally {
            Locale.setDefault(previous)
        }
    }

    /** Every kind of string ProgressionText produces, with values chosen to carry decimals. */
    private fun everyString(t: ProgressionText): List<String> {
        val out = mutableListOf<String>()
        val kms = listOf(0.0, 0.05, 4.96, 24.99, 25.0, 61.37, 149.95, 350.0, 1234.56)
        val countsSet = listOf(
            TrophyCounts(),
            TrophyCounts(mapOf(TrophyType.FINISHER_BRONZE to 2, TrophyType.PB_SILVER to 1)),
            TrophyCounts(TrophyType.values().associateWith { 5 })
        )
        LevelLadder.levels.forEach { level ->
            out += t.levelName(level.number)
            out += t.tierName(level.tier)
            out += t.levelTitle(level)
            out += t.roadmapRequirements(level)
            out += t.levelContentDescription(level)
            kms.forEach { km -> out += t.kmLabel(km, level) }
        }
        kms.forEach { km -> out += t.kmLabel(km, null) }
        TrophyType.values().forEach { type ->
            out += t.trophyName(type)
            out += t.trophyWithMetal(type)
            out += t.howToWin(type)
        }
        kms.forEach { km ->
            countsSet.forEach { counts ->
                val progress = LevelLadder.progress(km, counts)
                progress.requirements.forEach { out += t.requirementLine(it) }
                out += t.nextGoal(ProgressionGoals.nextGoal(progress))
            }
        }
        out += t.nextGoal(NextGoal.MaxLevel)
        TrophyType.values().forEach { out += t.nextGoal(NextGoal.EarnTrophies(6, it, 3)) }

        val laps = listOf(61_234L, 60_987L, 62_001L, 61_500L, 60_750L, 61_999L)
        val histories = listOf(
            emptyList(),
            listOf(SessionFacts(1, 1_000L, "baltar", "Baltar", 4_321.0, laps.take(3))),
            listOf(
                SessionFacts(1, 1_000L, "baltar", "Baltar", 13_712.5, laps),
                SessionFacts(2, 2_000L, "baltar", "Baltar", 9_876.5, laps.map { it - 1_111L })
            )
        )
        histories.forEach { history -> ProgressionGoals.hints(history).values.forEach { out += t.hint(it)!! } }
        listOf(null, 0.0, 0.5, 749.9, 750.0, 1_499.5, 2_999.999, 3_000.0, 12_345.678).forEach { steadiest ->
            listOf(TrophyType.CONSISTENCY_BRONZE to 3000.0, TrophyType.CONSISTENCY_SILVER to 1500.0,
                TrophyType.CONSISTENCY_GOLD to 750.0).forEach { (type, threshold) ->
                out += t.hint(TrophyHint.Consistency(type, steadiest, threshold))!!
            }
        }
        listOf(1L, 7L, 999L, 1_234L, 61_000L).forEach { gap ->
            out += t.recentProgress(RecentProgress.Faster("Baltar", gap))
            out += t.recentProgress(RecentProgress.Slower("Baltar", gap))
        }
        out += t.recentProgress(RecentProgress.Equal("Baltar"))
        out += t.recentProgress(RecentProgress.FirstTime("Baltar"))
        out += t.recentProgress(RecentProgress.NoLaps)
        assertFalse(out.any { it.isBlank() })
        return out
    }
}
