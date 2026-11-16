package com.drivingcoach.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelLadderTest {

    private fun counts(bronze: Int = 0, silver: Int = 0, gold: Int = 0) = TrophyCounts(mapOf(
        TrophyType.FINISHER_BRONZE to bronze, TrophyType.PB_SILVER to silver, TrophyType.TRACK_MASTER_GOLD to gold))

    private fun level(km: Double, c: TrophyCounts) = LevelLadder.levelFor(km, c).number

    @Test
    fun rookieAtStart() = assertEquals(1, level(0.0, counts()))

    @Test
    fun eachLevelAtItsThresholds() {
        assertEquals(2, level(25.0, counts(bronze = 1)))
        assertEquals(3, level(50.0, counts(bronze = 3)))
        assertEquals(4, level(100.0, counts(bronze = 3, silver = 1)))
        assertEquals(5, level(200.0, counts(bronze = 3, silver = 3)))
        assertEquals(6, level(350.0, counts(bronze = 3, silver = 3, gold = 1)))
    }

    @Test
    fun kmMetButTrophyMissingStaysLower() {
        assertEquals(1, level(400.0, counts()))
        assertEquals(2, level(400.0, counts(bronze = 2)))
        val progress = LevelLadder.progress(400.0, counts(bronze = 2))
        val trophy = progress.requirements.filterIsInstance<Requirement.Trophies>().single()
        assertFalse(trophy.met)
        assertEquals(2, trophy.current)
        assertTrue(progress.requirements.filterIsInstance<Requirement.Distance>().single().met)
    }

    @Test
    fun ladderIsSequential() {
        // Plenty of km, silver and gold, but no bronze: level 2 is not held, so nothing above is.
        assertEquals(1, level(500.0, counts(silver = 5, gold = 2)))
        // Gold without three silvers stops at 4.
        assertEquals(4, level(500.0, counts(bronze = 3, silver = 1, gold = 1)))
    }

    @Test
    fun trophyMetButKmMissingStaysLower() {
        assertEquals(1, level(24.9, counts(bronze = 5, silver = 5, gold = 5)))
    }

    @Test
    fun progressFractionWithinBand() {
        val p = LevelLadder.progress(75.0, counts(bronze = 3))
        assertEquals(3, p.current.number)
        assertEquals(4, p.next!!.number)
        assertEquals(0.5, p.kmFraction, 1e-9)
    }

    @Test
    fun progressFractionClampsWhenKmBeyondNextThreshold() {
        assertEquals(1.0, LevelLadder.progress(400.0, counts()).kmFraction, 1e-9)
    }

    @Test
    fun maxLevelHasNoNext() {
        val p = LevelLadder.progress(400.0, counts(bronze = 3, silver = 3, gold = 1))
        assertTrue(p.isMaxLevel)
        assertNull(p.next)
        assertTrue(p.requirements.isEmpty())
    }

    @Test
    fun ladderDefinition() {
        assertEquals(listOf(0.0, 25.0, 50.0, 100.0, 200.0, 350.0), LevelLadder.levels.map { it.minKm })
        assertEquals(listOf(Tier.BEGINNER, Tier.BEGINNER, Tier.MID, Tier.MID, Tier.PRO, Tier.PRO),
            LevelLadder.levels.map { it.tier })
    }
}
