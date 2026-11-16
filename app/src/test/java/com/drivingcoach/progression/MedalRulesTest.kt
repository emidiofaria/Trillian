package com.drivingcoach.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** L1 (SWE.4) tests for the session medals (SRS PG-28, PG-29, PG-30). Both sides of every threshold. */
class MedalRulesTest {

    private fun session(
        id: Long,
        laps: List<LapFacts>,
        distanceM: Double = 4_000.0,
        track: String = "name:baltar",
        trusted: Boolean = true
    ) = SessionFacts(id, id * 1000, track, "Baltar", distanceM, laps, trusted)

    private fun times(vararg ms: Long) = ms.map { LapFacts(it) }

    private fun types(session: SessionFacts, history: List<SessionFacts> = emptyList()) =
        TrophyRules.evaluate(session, history).map { it.type }.toSet()

    // ---------------------------------------------------------------- Purple Sector

    @Test
    fun purpleSectorIsANewAllTimeBestSector() {
        val earlier = session(1, listOf(LapFacts(100_000, 33_000, 32_000, 35_000)))
        val now = session(2, listOf(LapFacts(100_000, 33_001, 31_999, 35_000)))
        val medal = TrophyRules.evaluate(now, listOf(earlier)).single { it.type == TrophyType.PURPLE_SECTOR }
        assertEquals("sector 2 only", 2L, medal.value)
    }

    @Test
    fun equalSectorIsNotPurple() {
        val earlier = session(1, listOf(LapFacts(100_000, 33_000, 32_000, 35_000)))
        val now = session(2, listOf(LapFacts(100_000, 33_000, 32_000, 35_000)))
        assertTrue(TrophyType.PURPLE_SECTOR !in types(now, listOf(earlier)))
    }

    @Test
    fun noPurpleSectorOnFirstVisit() {
        assertTrue(TrophyType.PURPLE_SECTOR !in types(session(1, listOf(LapFacts(100_000, 33_000, 32_000, 35_000)))))
    }

    // ---------------------------------------------------------------- Carbon Copy

    @Test
    fun carbonCopyBoundaryAt0Point75Percent() {
        // 0.75 % of 100.000 s is 0.750 s.
        assertTrue(TrophyType.CARBON_COPY in types(session(1, times(100_000, 100_750, 103_000, 106_000, 109_000))))
        assertTrue(TrophyType.CARBON_COPY !in types(session(1, times(100_000, 100_751, 103_000, 106_000, 109_000))))
    }

    @Test
    fun carbonCopyNeedsConsecutiveValidLaps() {
        // Equal laps two apart do not count.
        assertTrue(TrophyType.CARBON_COPY !in types(session(1, times(100_000, 103_000, 100_000, 106_000))))
        // An invalid lap is never a copy.
        assertTrue(TrophyType.CARBON_COPY !in types(session(1, times(100_000, 103_000, 106_000, 30_000, 30_000))))
    }

    // ---------------------------------------------------------------- Top Gun

    private fun fast(id: Long, kmh: Double, trusted: Boolean = true) =
        session(id, List(3) { LapFacts(60_000, topSpeedKmh = kmh) }, trusted = trusted)

    @Test
    fun topGunBoundaryAtHalfAKmhBelowTheTrackRecord() {
        val record = fast(1, 200.0)
        assertTrue(TrophyType.TOP_GUN in types(fast(2, 199.5), listOf(record)))
        assertTrue(TrophyType.TOP_GUN !in types(fast(2, 199.49), listOf(record)))
        assertTrue(TrophyType.TOP_GUN in types(fast(2, 201.0), listOf(record)))
    }

    @Test
    fun noTopGunOnFirstVisitOrOverAnotherTrack() {
        assertTrue(TrophyType.TOP_GUN !in types(fast(1, 250.0)))
        val elsewhere = SessionFacts(1, 1000, "name:other", "Other", 4_000.0,
            List(3) { LapFacts(60_000, topSpeedKmh = 150.0) }, true)
        assertTrue(TrophyType.TOP_GUN !in types(fast(2, 250.0), listOf(elsewhere)))
    }

    @Test
    fun untrustedSessionIsNotATopSpeedRecord() {
        assertTrue(TrophyType.TOP_GUN !in types(fast(2, 199.0), listOf(fast(1, 150.0, trusted = false))))
    }

    // ---------------------------------------------------------------- Clean Air

    @Test
    fun cleanAirBoundaryAt5PercentOfTheMedian() {
        assertTrue(TrophyType.CLEAN_AIR in types(session(1, times(100_000, 100_000, 105_000, 100_000, 100_000))))
        assertTrue(TrophyType.CLEAN_AIR !in types(session(1, times(100_000, 100_000, 105_001, 100_000, 100_000))))
        assertTrue(TrophyType.CLEAN_AIR in types(session(1, times(100_000, 95_000, 100_000, 100_000, 100_000))))
        assertTrue(TrophyType.CLEAN_AIR !in types(session(1, times(100_000, 94_999, 100_000, 100_000, 100_000))))
    }

    @Test
    fun cleanAirNeedsFiveInARow() {
        assertTrue(TrophyType.CLEAN_AIR !in types(session(1, times(100_000, 100_000, 100_000, 100_000))))
        val awarded = TrophyRules.evaluate(session(1, times(120_000) + List(7) { LapFacts(100_000) }), emptyList())
        assertEquals(7L, awarded.single { it.type == TrophyType.CLEAN_AIR }.value)
    }

    // ---------------------------------------------------------------- Late Charger

    @Test
    fun lateChargerIsTheFastestLapInTheLastThree() {
        assertTrue(TrophyType.LATE_CHARGER in types(session(1, times(101_000, 102_000, 103_000, 104_000, 100_000))))
        assertTrue(TrophyType.LATE_CHARGER in types(session(1, times(101_000, 102_000, 100_000, 104_000, 103_000))))
        assertTrue(TrophyType.LATE_CHARGER !in types(session(1, times(101_000, 100_000, 102_000, 104_000, 103_000))))
    }

    @Test
    fun lateChargerNeedsFiveValidLaps() {
        assertTrue(TrophyType.LATE_CHARGER !in types(session(1, times(101_000, 102_000, 103_000, 100_000))))
    }

    @Test
    fun anInLapDoesNotHideALateCharge() {
        // The slow, invalid in-lap is not one of "the last three".
        val laps = times(101_000, 102_000, 103_000, 104_000, 100_000, 150_000)
        assertTrue(TrophyType.LATE_CHARGER in types(session(1, laps)))
    }

    // ---------------------------------------------------------------- Full Stint

    @Test
    fun fullStintBoundaryAt8Km() {
        assertTrue(TrophyType.FULL_STINT in types(session(1, times(60_000), 8_000.0)))
        assertTrue(TrophyType.FULL_STINT !in types(session(1, times(60_000), 7_999.9)))
    }

    // ---------------------------------------------------------------- shared rules

    @Test
    fun untrustedSessionEarnsNoMedals() {
        val laps = times(100_000, 100_000, 100_000, 100_000, 99_000)
        assertTrue(types(session(1, laps, 9_000.0, trusted = false)).none { it.isMedal })
    }

    @Test
    fun medalsNeverCountTowardLevels() {
        val counts = TrophyCounts.of(TrophyType.medals + TrophyType.medals)
        assertEquals(0, counts.levelTrophies)
        assertEquals(2, LevelLadder.levelFor(30.0, counts).number)
    }
}
