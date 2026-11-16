package com.drivingcoach.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.drivingcoach.progression.TrophyType.Metal.BRONZE
import com.drivingcoach.progression.TrophyType.Metal.GOLD
import com.drivingcoach.progression.TrophyType.Metal.SILVER

/** L1 (SWE.4) tests for the seven-level ladder and its dual gate (SRS PG-01, PG-02). */
class LevelLadderTest {

    /** Counts made from one representative trophy per metal. */
    private fun counts(bronze: Int = 0, silver: Int = 0, gold: Int = 0, medals: Int = 0) = TrophyCounts(mapOf(
        TrophyType.FINISHER to bronze, TrophyType.METRONOME to silver, TrophyType.SURGICAL to gold,
        TrophyType.FULL_STINT to medals))

    private fun level(km: Double, c: TrophyCounts) = LevelLadder.levelFor(km, c).number

    @Test
    fun ladderDefinition() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), LevelLadder.levels.map { it.number })
        assertEquals(listOf(0.0, 8.0, 24.0, 56.0, 120.0, 240.0, 450.0), LevelLadder.levels.map { it.minKm })
        assertEquals(listOf(null, null, BRONZE, BRONZE, SILVER, SILVER, GOLD), LevelLadder.levels.map { it.metal })
        assertEquals(listOf(0, 0, 1, 3, 1, 3, 1), LevelLadder.levels.map { it.trophyCount })
        assertEquals(7, LevelLadder.maxLevel)
    }

    @Test
    fun rookieAtStart() = assertEquals(1, level(0.0, counts()))

    @Test
    fun distanceOnlyLevelsAtTheirThreshold() {
        assertEquals(1, level(7.999, counts()))
        assertEquals(2, level(8.0, counts()))
        assertEquals(2, level(23.999, counts(bronze = 1)))
    }

    @Test
    fun eachLevelOnBothSidesOfItsDistanceGate() {
        val all = counts(bronze = 3, silver = 3, gold = 1)
        assertEquals(3, level(24.0, counts(bronze = 1)))
        assertEquals(3, level(55.999, all))
        assertEquals(4, level(56.0, counts(bronze = 3)))
        assertEquals(4, level(119.999, all))
        assertEquals(5, level(120.0, counts(bronze = 2, silver = 1)))
        assertEquals(5, level(239.999, all))
        assertEquals(6, level(240.0, counts(bronze = 0, silver = 3)))
        assertEquals(6, level(449.999, all))
        assertEquals(7, level(450.0, all))
    }

    @Test
    fun eachLevelOnBothSidesOfItsTrophyGate() {
        assertEquals(2, level(500.0, counts()))
        assertEquals(3, level(500.0, counts(bronze = 1)))
        assertEquals(3, level(500.0, counts(bronze = 2)))
        assertEquals(4, level(500.0, counts(bronze = 3)))
        assertEquals(5, level(500.0, counts(bronze = 2, silver = 1)))
        assertEquals(5, level(500.0, counts(bronze = 1, silver = 2)))
        assertEquals(6, level(500.0, counts(silver = 3)))
        assertEquals(7, level(500.0, counts(silver = 2, gold = 1)))
    }

    @Test
    fun higherMetalCountsTowardLowerRequirements() {
        // One Silver stands in for the Bronze of Level 3.
        assertEquals(3, level(30.0, counts(silver = 1)))
        // Gold + Silver + Bronze are three Bronze-or-better: Level 4.
        assertEquals(4, level(60.0, counts(bronze = 1, silver = 1, gold = 1)))
        // Three Golds are three Silvers.
        assertEquals(6, level(300.0, counts(gold = 3)))
        assertEquals(3, TrophyCounts(mapOf(TrophyType.SURGICAL to 3)).atLeast(TrophyType.Metal.SILVER))
        assertEquals(0, TrophyCounts(mapOf(TrophyType.FINISHER to 3)).atLeast(TrophyType.Metal.SILVER))
    }

    @Test
    fun eachTrophyCountsOnceNotOncePerRequirement() {
        // 450 km and one Gold: the Gold covers the single Bronze of Level 3, but Level 4
        // needs three trophies and there is still only one.
        assertEquals(3, level(450.0, counts(gold = 1)))
    }

    @Test
    fun medalsNeverCount() {
        assertEquals(2, level(500.0, counts(medals = 50)))
        assertEquals(0, counts(medals = 5).levelTrophies)
        assertEquals(4, counts(bronze = 1, silver = 2, gold = 1, medals = 9).levelTrophies)
    }

    @Test
    fun kmMetButTrophyMissingShowsWhatIsMissing() {
        val progress = LevelLadder.progress(400.0, counts(bronze = 2))
        assertEquals(3, progress.current.number)
        val trophy = progress.requirements.filterIsInstance<Requirement.Trophies>().single()
        assertFalse(trophy.met)
        assertEquals(TrophyType.Metal.BRONZE, trophy.metal)
        assertEquals(3, trophy.target)
        assertEquals(2, trophy.current)
        assertTrue(progress.requirements.filterIsInstance<Requirement.Distance>().single().met)
    }

    @Test
    fun distanceOnlyNextLevelHasOneRequirement() {
        val progress = LevelLadder.progress(3.0, counts())
        assertEquals(listOf(Requirement.Distance(8.0, 3.0)), progress.requirements)
    }

    @Test
    fun progressFractionWithinBand() {
        val p = LevelLadder.progress(40.0, counts(bronze = 1))
        assertEquals(3, p.current.number)
        assertEquals(0.5, p.kmFraction, 1e-9)
        assertEquals(1.0, LevelLadder.progress(400.0, counts()).kmFraction, 1e-9)
    }

    @Test
    fun levelSevenHasNoNextAndFullBar() {
        val top = LevelLadder.progress(450.0, counts(bronze = 3, silver = 3, gold = 1))
        assertEquals(7, top.current.number)
        assertTrue(top.isMaxLevel)
        assertNull(top.next)
        assertTrue(top.requirements.isEmpty())
        assertEquals(1.0, top.kmFraction, 0.0)
    }
}
