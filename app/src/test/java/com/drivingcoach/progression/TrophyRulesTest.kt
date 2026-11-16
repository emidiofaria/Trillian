package com.drivingcoach.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrophyRulesTest {

    private fun session(
        id: Long,
        laps: List<Long>,
        distanceM: Double = 6_000.0,
        track: String = "name:baltar",
        trusted: Boolean = true
    ) = SessionFacts(id, startedAt = id * 1000, trackKey = track, trackName = "Baltar",
        distanceM = distanceM, lapDurationsMs = laps, lapBoundariesTrusted = trusted)

    private fun types(session: SessionFacts, history: List<SessionFacts> = emptyList()) =
        TrophyRules.evaluate(session, history).map { it.type }.toSet()

    private val ragged = listOf(60_000L, 66_000L, 58_000L, 64_000L, 61_000L)

    @Test
    fun finisherNeedsFiveKmAndFiveLaps() {
        assertTrue(TrophyType.FINISHER_BRONZE in types(session(1, ragged, 5_000.0)))
        assertTrue(TrophyType.FINISHER_BRONZE !in types(session(1, ragged, 4_999.0)))
        assertTrue(TrophyType.FINISHER_BRONZE !in types(session(1, ragged.take(4), 9_000.0)))
    }

    @Test
    fun noPersonalBestOnFirstVisit() {
        assertTrue(TrophyType.PB_SILVER !in types(session(1, ragged)))
    }

    @Test
    fun personalBestBeatsEarlierSessionsOnSameTrackOnly() {
        val earlier = session(1, listOf(60_000L, 61_000L, 62_000L))
        val elsewhere = session(2, listOf(50_000L, 51_000L), track = "name:other")
        val now = session(3, listOf(59_500L, 61_000L, 62_000L))
        val awarded = TrophyRules.evaluate(now, listOf(earlier, elsewhere))
        assertEquals(59_500L, awarded.single { it.type == TrophyType.PB_SILVER }.value)
    }

    @Test
    fun equalTimeIsNotAPersonalBest() {
        val earlier = session(1, listOf(60_000L))
        assertTrue(TrophyType.PB_SILVER !in types(session(2, listOf(60_000L)), listOf(earlier)))
    }

    @Test
    fun laterSessionsAreNotHistory() {
        val later = session(5, listOf(70_000L))
        assertTrue(TrophyType.PB_SILVER !in types(session(2, listOf(60_000L)), listOf(later)))
    }

    @Test
    fun splitHalfLapCannotFakePersonalBest() {
        val earlier = session(1, listOf(60_000L, 61_000L, 62_000L))
        // A 30 s "lap" from a split boundary is far outside ±15 % of the median.
        val now = session(2, listOf(30_000L, 61_000L, 62_000L, 63_000L))
        assertTrue(TrophyType.PB_SILVER !in types(now, listOf(earlier)))
        assertEquals(61_000L, now.credibleBestMs)
    }

    @Test
    fun consistencyAwardsHighestTierOnly() {
        val gold = listOf(60_000L, 60_500L, 59_500L, 60_200L, 59_800L)
        val awarded = types(session(1, gold))
        assertTrue(TrophyType.CONSISTENCY_GOLD in awarded)
        assertTrue(TrophyType.CONSISTENCY_SILVER !in awarded)
        assertTrue(TrophyType.CONSISTENCY_BRONZE !in awarded)
    }

    @Test
    fun consistencyTiers() {
        assertEquals(TrophyType.CONSISTENCY_GOLD, TrophyRules.consistencyTierFor(749.0))
        assertEquals(TrophyType.CONSISTENCY_SILVER, TrophyRules.consistencyTierFor(750.0))
        assertEquals(TrophyType.CONSISTENCY_SILVER, TrophyRules.consistencyTierFor(1_499.0))
        assertEquals(TrophyType.CONSISTENCY_BRONZE, TrophyRules.consistencyTierFor(1_500.0))
        assertEquals(TrophyType.CONSISTENCY_BRONZE, TrophyRules.consistencyTierFor(2_999.0))
        assertNull(TrophyRules.consistencyTierFor(3_000.0))
    }

    @Test
    fun consistencyUsesSteadiestFiveConsecutiveLaps() {
        // Wild opening lap, then five steady laps.
        val laps = listOf(90_000L, 60_000L, 60_100L, 59_900L, 60_000L, 60_050L)
        val steadiest = TrophyRules.steadiestWindowStdDevMs(laps)!!
        assertTrue(steadiest < 100.0)
        assertNull(TrophyRules.steadiestWindowStdDevMs(laps.take(4)))
    }

    @Test
    fun stdDevIsPopulation() {
        assertEquals(2.0, TrophyRules.populationStdDev(listOf(2L, 4L, 4L, 4L, 5L, 5L, 7L, 9L)), 1e-9)
    }

    @Test
    fun trackMasterIsPersonalBestPlusConsistencyGold() {
        val earlier = session(1, listOf(61_000L, 62_000L, 63_000L))
        val steadyPb = session(2, listOf(60_000L, 60_500L, 59_500L, 60_200L, 59_800L))
        val awarded = types(steadyPb, listOf(earlier))
        assertTrue(TrophyType.TRACK_MASTER_GOLD in awarded)
        assertTrue(TrophyType.PB_SILVER in awarded)
        assertTrue(TrophyType.CONSISTENCY_GOLD in awarded)

        val steadyFirstVisit = types(session(3, steadyPb.lapDurationsMs, track = "name:new"))
        assertTrue(TrophyType.TRACK_MASTER_GOLD !in steadyFirstVisit)

        val pbButRagged = types(session(4, listOf(58_000L, 62_000L, 60_000L, 65_000L, 59_000L)), listOf(earlier))
        assertTrue(TrophyType.PB_SILVER in pbButRagged)
        assertTrue(TrophyType.TRACK_MASTER_GOLD !in pbButRagged)
    }

    @Test
    fun mergedLapCaveatKeepsOnlyFinisher() {
        val earlier = session(1, listOf(61_000L, 62_000L))
        val suspect = session(2, listOf(60_000L, 60_500L, 59_500L, 60_200L, 59_800L), trusted = false)
        assertEquals(setOf(TrophyType.FINISHER_BRONZE), types(suspect, listOf(earlier)))
    }

    @Test
    fun trackKeyPrefersIdAndNormalisesName() {
        assertEquals("id:baltar", TrackKey.of(" Baltar ", "whatever"))
        assertEquals("name:baltar 2", TrackKey.of(null, "  Baltar 2 "))
        assertEquals(TrackKey.of("", "BALTAR"), TrackKey.of(null, "baltar"))
    }

    @Test
    fun onlyMainTrophiesCountTowardLevels() {
        val counting = TrophyType.values().filter { it.countsTowardLevels }.toSet()
        assertEquals(setOf(TrophyType.FINISHER_BRONZE, TrophyType.PB_SILVER, TrophyType.TRACK_MASTER_GOLD), counting)
    }

    /**
     * Five laps whose deviations from a 60 s mean are ±1.5σ, ±0.5σ and 0, so the
     * population stdDev is exactly σ (2·2.25σ² + 2·0.25σ² = 5σ²) with every term exact
     * in floating point. The tiers are "under" a threshold, so σ itself must not qualify.
     */
    private fun lapsWithStdDev(sigmaMs: Long): List<Long> {
        val big = sigmaMs * 3 / 2
        val small = sigmaMs / 2
        return listOf(60_000L + big, 60_000L - small, 60_000L, 60_000L + small, 60_000L - big)
    }

    @Test
    fun spreadExactlyAtThresholdIsNotAwarded() {
        listOf(3_000L, 1_500L, 750L).forEach { sigma ->
            assertEquals(sigma.toDouble(), TrophyRules.populationStdDev(lapsWithStdDev(sigma)), 0.0)
        }
        val atBronze = types(session(1, lapsWithStdDev(3_000L)))
        assertTrue("exactly 3.000 s earns no consistency trophy: $atBronze",
            atBronze.none { !it.countsTowardLevels })
        assertEquals(setOf(TrophyType.CONSISTENCY_BRONZE) + TrophyType.FINISHER_BRONZE,
            types(session(1, lapsWithStdDev(1_500L))))
        assertEquals(setOf(TrophyType.CONSISTENCY_SILVER) + TrophyType.FINISHER_BRONZE,
            types(session(1, lapsWithStdDev(750L))))
        // One millisecond under each threshold reaches the tier, so the boundary is the only cause.
        assertEquals(TrophyType.CONSISTENCY_BRONZE, TrophyRules.consistencyTierFor(
            TrophyRules.populationStdDev(lapsWithStdDev(3_000L)) - 1.0))
    }

    @Test
    fun exactlyFiveKmAndFiveLapsEarnsFinisher() {
        val awarded = TrophyRules.evaluate(session(1, ragged, distanceM = 5_000.0), emptyList())
        val finisher = awarded.single { it.type == TrophyType.FINISHER_BRONZE }
        assertEquals("the Finisher records the distance in metres", 5_000L, finisher.value)
        assertTrue(TrophyType.FINISHER_BRONZE !in types(session(1, ragged.take(4), distanceM = 5_000.0)))
    }

    @Test
    fun fourLapsEarnNoConsistency() {
        val four = List(4) { 60_000L }
        assertNull(TrophyRules.steadiestWindowStdDevMs(four))
        assertTrue(types(session(1, four)).isEmpty())
        // The same identical laps, one more of them, are Gold: only the count withheld it.
        assertTrue(TrophyType.CONSISTENCY_GOLD in types(session(1, four + 60_000L)))
    }

    @Test
    fun zeroOrOneLapAwardsNothingAndDoesNotCrash() {
        val none = session(1, emptyList(), distanceM = 12_000.0)
        assertNull(none.credibleBestMs)
        assertNull(TrophyRules.steadiestWindowStdDevMs(none.lapDurationsMs))
        assertTrue(TrophyRules.evaluate(none, emptyList()).isEmpty())
        assertTrue(TrophyRules.evaluate(none, listOf(session(0, ragged))).isEmpty())

        val one = session(2, listOf(61_000L), distanceM = 12_000.0)
        assertEquals(61_000L, one.credibleBestMs)
        assertTrue(TrophyRules.evaluate(one, emptyList()).isEmpty())
    }
}
