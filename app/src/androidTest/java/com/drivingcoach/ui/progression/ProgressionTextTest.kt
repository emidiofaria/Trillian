package com.drivingcoach.ui.progression

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivingcoach.progression.LapFacts
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
 * The words the progression screens show (SRS PG-15, PG-16, PG-18, PG-20, PG-23, DP-11) and the
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
    fun checklistLineShowsMissingMetalWithExamples() {
        assertEquals(
            "⬜ 3 × Bronze trophy or better (you have 1) · The Finisher, Pace Setter, Steady Hand",
            text.requirementLine(Requirement.Trophies(TrophyType.Metal.BRONZE, 3, 1))
        )
        assertEquals(
            "✅ 1 × Silver trophy or better (you have 2) · Metronome, Plateau Breaker, Apex Exit",
            text.requirementLine(Requirement.Trophies(TrophyType.Metal.SILVER, 1, 2))
        )
    }

    @Test
    fun roadmapNamesSevenLevelsAndTheirGates() {
        assertEquals(
            listOf("Rookie", "Cadet", "Novice", "Challenger", "Competitor", "Expert", "Track Legend"),
            LevelLadder.levels.map { text.levelName(it.number) }
        )
        assertEquals(
            listOf(
                "Where every driver starts", "8 km",
                "24 km · 1 × Bronze trophy or better", "56 km · 3 × Bronze trophy or better",
                "120 km · 1 × Silver trophy or better", "240 km · 3 × Silver trophy or better",
                "450 km · 1 × Gold trophy or better"
            ),
            LevelLadder.levels.map { text.roadmapRequirements(it) }
        )
    }

    @Test
    fun howToWinCarriesEveryThresholdFromTheRules() {
        assertEquals("Drive at least 5 km with 5 valid laps in one session.", text.howToWin(TrophyType.FINISHER))
        assertTrue(text.howToWin(TrophyType.STEADY_HAND).contains("CV under 3.0%"))
        assertTrue(text.howToWin(TrophyType.METRONOME).contains("CV under 1.5%"))
        assertTrue(text.howToWin(TrophyType.SURGICAL).contains("CV under 0.75%"))
        assertTrue(text.howToWin(TrophyType.PLATEAU_BREAKER).contains("at least 15 valid laps"))
        assertTrue(text.howToWin(TrophyType.APEX_EXIT).contains("within 1.0%"))
        assertTrue(text.howToWin(TrophyType.OPTIMAL_LAP).contains("within 0.5%"))
        assertTrue(text.howToWin(TrophyType.OPTIMAL_LAP).contains("20 valid laps"))
        assertTrue(text.howToWin(TrophyType.CARBON_COPY).contains("within 0.75%"))
        assertTrue(text.howToWin(TrophyType.TOP_GUN).contains("within 0.5 km/h"))
        assertTrue(text.howToWin(TrophyType.CLEAN_AIR).contains("5 consecutive laps each within 5.0%"))
        assertTrue(text.howToWin(TrophyType.LATE_CHARGER).contains("last 3 valid laps"))
        assertEquals("Drive at least 8 km in one session.", text.howToWin(TrophyType.FULL_STINT))
    }

    @Test
    fun trophyNamesCarryTheirMetalAndMedalsDoNot() {
        assertEquals("Apex Exit (Silver)", text.trophyWithMetal(TrophyType.APEX_EXIT))
        assertEquals("Surgical (Gold)", text.trophyWithMetal(TrophyType.SURGICAL))
        assertEquals("Top Gun", text.trophyWithMetal(TrophyType.TOP_GUN))
    }

    @Test
    fun finisherHintSentence() {
        assertEquals(
            "Best session so far: 4.9 km / 7 valid laps · need 5 km and 5 valid laps",
            text.hint(TrophyHint.Finisher(4.999, 7))
        )
        assertEquals("Record a session to see how close you are.", text.hint(TrophyHint.Finisher(null, null)))
    }

    @Test
    fun consistencyHintQuotesBestCvAgainstTheTier() {
        assertEquals(
            "Your best CV 2.10% · need under 1.50% for Metronome",
            text.hint(TrophyHint.Consistency(TrophyType.METRONOME, 2.1, 1.5))
        )
        assertEquals(
            "Your best CV 0.50% is already inside this tier. A session that reaches a higher tier takes that one instead.",
            text.hint(TrophyHint.Consistency(TrophyType.STEADY_HAND, 0.5, 3.0))
        )
        assertEquals(
            "Drive 5 consecutive valid laps in one session to get a CV reading.",
            text.hint(TrophyHint.Consistency(TrophyType.SURGICAL, null, 0.75))
        )
    }

    @Test
    fun personalBestPlateauApexAndOptimalHints() {
        assertEquals("Your best at Baltar: 1:12.345 · beat it", text.hint(TrophyHint.PersonalBest("Baltar", 72_345L)))
        assertEquals(
            "Your best at Baltar has stood for 9 valid laps · need 15, then beat it",
            text.hint(TrophyHint.Plateau("Baltar", 9, 15))
        )
        assertEquals(
            "Your tightest 5 lap top speed spread: 1.25% · need 1.00% or less",
            text.hint(TrophyHint.Apex(1.25, 1.0))
        )
        assertEquals(
            "Baltar: 12 of 20 valid laps · best lap 0.84% over your theoretical best, need 0.50% or less",
            text.hint(TrophyHint.Optimal("Baltar", 12, 20, 0.84, 0.5))
        )
    }

    @Test
    fun drivenTimeIsMinutesThenHours() {
        assertEquals("0 min", text.drivenTime(0L))
        assertEquals("59 min", text.drivenTime(3_599_999L))
        assertEquals("1.0 h", text.drivenTime(3_600_000L))
        assertEquals("2.4 h", text.drivenTime(8_700_000L))
    }

    @Test
    fun nextGoalSentencesForKmTrophyAndMax() {
        // 8 km needed, 3.71 driven: 4.29 remaining rounds UP so the driver is never short.
        assertEquals("Drive 4.3 km more to reach Level 2 · Cadet.", text.nextGoal(NextGoal.DriveKm(2, 8.0 - 3.71)))
        assertEquals(
            "Win 2 × Silver trophy or better to reach Level 6 · Expert. Try Metronome, Plateau Breaker, Apex Exit.",
            text.nextGoal(NextGoal.EarnTrophies(6, TrophyType.Metal.SILVER, 2))
        )
        assertEquals("You've reached the top level. Keep chasing trophies and medals!", text.nextGoal(NextGoal.MaxLevel))

        val fresh = ProgressionGoals.nextGoal(LevelLadder.progress(0.0, TrophyCounts()))
        assertEquals("Drive 8.0 km more to reach Level 2 · Cadet.", text.nextGoal(fresh))
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
                "Your best CV 3.46% · need under 3.00% for Steady Hand",
                german.hint(TrophyHint.Consistency(TrophyType.STEADY_HAND, 3.4567, 3.0))
            )
            assertEquals("2.4 h", german.drivenTime(8_700_000L))
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
        val kms = listOf(0.0, 0.05, 4.96, 7.99, 23.99, 24.0, 61.37, 149.95, 449.95, 1234.56)
        val countsSet = listOf(
            TrophyCounts(),
            TrophyCounts(mapOf(TrophyType.FINISHER to 2, TrophyType.METRONOME to 1)),
            TrophyCounts(mapOf(TrophyType.OPTIMAL_LAP to 1, TrophyType.TOP_GUN to 4)),
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
        TrophyType.Metal.values().forEach { out += t.nextGoal(NextGoal.EarnTrophies(6, it, 3)) }

        val laps = listOf(61_234L, 60_987L, 62_001L, 61_500L, 60_750L, 61_999L)
        fun facts(id: Long, km: Double, times: List<Long>, speed: Double?) = SessionFacts(
            id, id * 1_000L, "baltar", "Baltar", km,
            times.mapIndexed { i, ms -> LapFacts(ms, ms / 3, ms / 3, ms - 2 * (ms / 3) - 7, speed?.minus(i * 0.37)) }, true
        )
        val histories = listOf(
            emptyList(),
            listOf(facts(1, 4_321.0, laps.take(3), null)),
            listOf(facts(1, 13_712.5, laps, 141.37), facts(2, 9_876.5, laps.map { it - 1_111L }, 139.91))
        )
        histories.forEach { history -> ProgressionGoals.hints(history).values.forEach { out += t.hint(it)!! } }
        listOf(null, 0.0, 0.5, 0.749, 0.75, 1.4999, 2.999, 3.0, 12.345).forEach { cv ->
            listOf(TrophyType.STEADY_HAND to 3.0, TrophyType.METRONOME to 1.5, TrophyType.SURGICAL to 0.75)
                .forEach { (type, threshold) -> out += t.hint(TrophyHint.Consistency(type, cv, threshold))!! }
        }
        listOf(0L, 59_999L, 3_600_000L, 8_700_000L, 123_456_789L).forEach { out += t.drivenTime(it) }
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
