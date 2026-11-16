package com.drivingcoach.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L1 (SWE.4) tests for the v3.08 level trophies (SRS PG-05 … PG-10, PG-24 … PG-27).
 * Every threshold is tested on both sides.
 */
class TrophyRulesTest {

    private fun session(
        id: Long,
        laps: List<LapFacts>,
        distanceM: Double = 9_000.0,
        track: String = "name:baltar",
        trusted: Boolean = true
    ) = SessionFacts(id, startedAt = id * 1000, trackKey = track, trackName = "Baltar",
        distanceM = distanceM, laps = laps, lapBoundariesTrusted = trusted)

    private fun times(vararg ms: Long) = ms.map { LapFacts(it) }

    private fun types(session: SessionFacts, history: List<SessionFacts> = emptyList()) =
        TrophyRules.evaluate(session, history).map { it.type }.toSet()

    /** Five laps around 100 s whose population CV is `d × √0.8 / 1000` percent. */
    private fun cvLaps(d: Long) = times(100_000 - d, 100_000 + d, 100_000 - d, 100_000 + d, 100_000)

    private val ragged = times(60_000, 66_000, 58_000, 64_000, 61_000)

    // ---------------------------------------------------------------- valid laps (PG-24)

    @Test
    fun validBandIsInclusiveAt85And115Percent() {
        assertTrue(ValidLaps.isValid(85_000, 100_000.0))
        assertFalse(ValidLaps.isValid(84_999, 100_000.0))
        assertTrue(ValidLaps.isValid(115_000, 100_000.0))
        assertFalse(ValidLaps.isValid(115_001, 100_000.0))
    }

    @Test
    fun validLapsAreJudgedAgainstTheSessionMedian() {
        val s = session(1, times(100_000, 100_000, 100_000, 85_000, 115_000, 84_999, 115_001))
        assertEquals(100_000.0, s.medianMs, 0.0)
        assertEquals(listOf(true, true, true, true, true, false, false), s.validFlags)
        assertEquals(5, s.validLapCount)
    }

    // ---------------------------------------------------------------- The Finisher

    @Test
    fun finisherNeedsFiveKmAndFiveValidLaps() {
        assertTrue(TrophyType.FINISHER in types(session(1, ragged, 5_000.0)))
        assertTrue(TrophyType.FINISHER !in types(session(1, ragged, 4_999.0)))
        assertTrue(TrophyType.FINISHER !in types(session(1, ragged.take(4), 9_000.0)))
    }

    @Test
    fun finisherDoesNotCountInvalidLaps() {
        // Five laps, but the 30 s split half is outside the band: four valid.
        val laps = times(60_000, 61_000, 30_000, 60_500, 61_500)
        assertEquals(4, session(1, laps).validLapCount)
        assertTrue(TrophyType.FINISHER !in types(session(1, laps)))
    }

    // ---------------------------------------------------------------- Pace Setter

    @Test
    fun noPaceSetterOnFirstVisit() {
        assertTrue(TrophyType.PACE_SETTER !in types(session(1, ragged)))
    }

    @Test
    fun paceSetterBeatsEarlierSessionsOnSameTrackOnly() {
        val earlier = session(1, times(60_000, 61_000, 62_000))
        val elsewhere = session(2, times(50_000, 51_000), track = "name:other")
        val now = session(3, times(59_500, 61_000, 62_000))
        val awarded = TrophyRules.evaluate(now, listOf(earlier, elsewhere))
        assertEquals(59_500L, awarded.single { it.type == TrophyType.PACE_SETTER }.value)
    }

    @Test
    fun equalTimeIsNotAPaceSetter() {
        val earlier = session(1, times(60_000))
        assertTrue(TrophyType.PACE_SETTER !in types(session(2, times(60_000)), listOf(earlier)))
    }

    @Test
    fun laterSessionsAreNotHistory() {
        val later = session(5, times(70_000))
        assertTrue(TrophyType.PACE_SETTER !in types(session(2, times(60_000)), listOf(later)))
    }

    @Test
    fun splitHalfLapCannotFakePaceSetter() {
        val earlier = session(1, times(60_000, 61_000, 62_000))
        val now = session(2, times(61_000, 30_000, 62_000, 61_500))
        assertTrue(TrophyType.PACE_SETTER !in types(now, listOf(earlier)))
    }

    @Test
    fun untrustedHistoryIsNotAPersonalBest() {
        val suspect = session(1, times(55_000, 56_000, 55_500), trusted = false)
        val trusted = session(2, times(60_000, 61_000, 62_000))
        val now = session(3, times(59_000, 60_000, 60_500))
        assertTrue(TrophyType.PACE_SETTER in types(now, listOf(suspect, trusted)))
    }

    // ---------------------------------------------------------------- consistency family

    @Test
    fun cvIsPopulationStdDevOverMean() {
        assertEquals(0.0, SessionMetrics.cvPercent(listOf(60_000, 60_000)), 1e-12)
        // {98, 102} s: stdDev 2 s, mean 100 s.
        assertEquals(2.0, SessionMetrics.cvPercent(listOf(98_000, 102_000)), 1e-12)
    }

    @Test
    fun steadyHandBoundaryAt3Percent() {
        assertTrue(SessionMetrics.cvPercent(cvLaps(3_354).map { it.durationMs }) < 3.0)
        assertTrue(SessionMetrics.cvPercent(cvLaps(3_355).map { it.durationMs }) > 3.0)
        assertEquals(setOf(TrophyType.STEADY_HAND), types(session(1, cvLaps(3_354))).intersect(consistency))
        assertTrue(types(session(1, cvLaps(3_355))).intersect(consistency).isEmpty())
    }

    @Test
    fun metronomeBoundaryAt1Point5Percent() {
        assertTrue(SessionMetrics.cvPercent(cvLaps(1_677).map { it.durationMs }) < 1.5)
        assertTrue(SessionMetrics.cvPercent(cvLaps(1_678).map { it.durationMs }) > 1.5)
        assertEquals(setOf(TrophyType.METRONOME), types(session(1, cvLaps(1_677))).intersect(consistency))
        assertEquals(setOf(TrophyType.STEADY_HAND), types(session(1, cvLaps(1_678))).intersect(consistency))
    }

    @Test
    fun surgicalBoundaryAt0Point75Percent() {
        assertTrue(SessionMetrics.cvPercent(cvLaps(838).map { it.durationMs }) < 0.75)
        assertTrue(SessionMetrics.cvPercent(cvLaps(839).map { it.durationMs }) > 0.75)
        assertEquals(setOf(TrophyType.SURGICAL), types(session(1, cvLaps(838))).intersect(consistency))
        assertEquals(setOf(TrophyType.METRONOME), types(session(1, cvLaps(839))).intersect(consistency))
    }

    @Test
    fun onlyTheHighestConsistencyTierIsAwarded() {
        val awarded = TrophyRules.evaluate(session(1, times(60_000, 60_010, 59_990, 60_000, 60_000)), emptyList())
        assertEquals(listOf(TrophyType.SURGICAL), awarded.map { it.type }.filter { it in consistency })
        assertEquals(TrophyType.SURGICAL, TrophyRules.consistencyTierFor(0.5))
        assertEquals(TrophyType.METRONOME, TrophyRules.consistencyTierFor(0.75))
        assertEquals(TrophyType.STEADY_HAND, TrophyRules.consistencyTierFor(1.5))
        assertNull(TrophyRules.consistencyTierFor(3.0))
    }

    @Test
    fun cvUsesTheBestWindowOfFiveConsecutiveValidLaps() {
        // A wild lap first, then five metronomic ones.
        val laps = times(80_000, 60_000, 60_000, 60_000, 60_000, 60_000)
        assertEquals(0.0, session(1, laps).bestCvPercent!!, 1e-12)
        // An invalid lap in the middle breaks every window.
        val broken = times(60_000, 60_000, 30_000, 60_000, 60_000, 60_000, 60_000)
        assertNull(session(1, broken).bestCvPercent)
        assertNull(session(1, times(60_000, 60_000, 60_000, 60_000)).bestCvPercent)
    }

    @Test
    fun consistencyValueIsCvInHundredthsOfAPercent() {
        val awarded = TrophyRules.evaluate(session(1, times(98_000, 102_000, 98_000, 102_000, 100_000)), emptyList())
        // stdDev = √(16/5) s = 1.789 s over 100 s.
        assertEquals(179L, awarded.single { it.type == TrophyType.STEADY_HAND }.value)
    }

    // ---------------------------------------------------------------- Plateau Breaker

    /** The PB (60 s) is set on lap 1, then [after] valid laps follow it in the same session. */
    private fun pbThen(after: Int) = session(1, times(60_000) + List(after) { LapFacts(61_000) })

    @Test
    fun plateauBreakerNeedsThePbToHaveStoodFor15ValidLaps() {
        // 14 laps after the PB in session 1, then one more in session 2 before it falls: 15.
        val now = session(2, times(61_000, 59_000, 61_000, 61_000, 61_000))
        assertTrue(TrophyType.PLATEAU_BREAKER in types(now, listOf(pbThen(14))))
        assertTrue(TrophyType.PACE_SETTER in types(now, listOf(pbThen(14))))
        // 13 + 1 = 14: a Pace Setter but not a Plateau Breaker.
        assertTrue(TrophyType.PLATEAU_BREAKER !in types(now, listOf(pbThen(13))))
        assertTrue(TrophyType.PACE_SETTER in types(now, listOf(pbThen(13))))
    }

    @Test
    fun plateauCountsLapsInSessionsBetween() {
        val between = session(2, List(10) { LapFacts(62_000) })
        val now = session(3, times(59_000, 61_000, 61_000, 61_000, 61_000))
        // 5 after the PB + 10 in between = 15.
        assertEquals(15, TrophyRules.lapsPbStood(now, listOf(pbThen(5), between), 60_000))
        assertTrue(TrophyType.PLATEAU_BREAKER in types(now, listOf(pbThen(5), between)))
        assertTrue(TrophyType.PLATEAU_BREAKER !in types(now, listOf(pbThen(4), between)))
    }

    @Test
    fun plateauNeedsAPbToBreak() {
        val now = session(2, times(61_000, 61_500, 61_000, 61_000, 61_000))
        assertTrue(TrophyType.PLATEAU_BREAKER !in types(now, listOf(pbThen(20))))
    }

    @Test
    fun invalidLapsDoNotExtendThePlateau() {
        val earlier = session(1, times(60_000) + List(14) { LapFacts(61_000) } + times(90_000, 90_000))
        assertEquals(14, SessionMetrics.lapsSincePb(listOf(earlier)))
    }

    // ---------------------------------------------------------------- Apex Exit

    private fun speedLaps(vararg kmh: Double?) = kmh.map { LapFacts(60_000, topSpeedKmh = it) }

    @Test
    fun apexExitBoundaryAt1PercentOfSessionTop() {
        assertTrue(TrophyType.APEX_EXIT in types(session(1, speedLaps(200.0, 198.0, 198.0, 198.0, 198.0))))
        assertTrue(TrophyType.APEX_EXIT !in types(session(1, speedLaps(200.0, 198.0, 198.0, 198.0, 197.99))))
    }

    @Test
    fun apexExitNeedsFiveConsecutiveLapsWithSpeed() {
        assertTrue(TrophyType.APEX_EXIT !in types(session(1, speedLaps(200.0, 200.0, null, 200.0, 200.0, 200.0))))
        assertTrue(TrophyType.APEX_EXIT !in types(session(1, speedLaps(200.0, 200.0, 200.0, 200.0))))
        assertTrue(TrophyType.APEX_EXIT !in types(session(1, times(60_000, 60_000, 60_000, 60_000, 60_000))))
    }

    @Test
    fun apexExitWindowNeedNotContainTheTopLap() {
        val laps = speedLaps(150.0, 210.0, 150.0, 208.0, 208.0, 208.0, 208.0, 207.9)
        assertTrue(TrophyType.APEX_EXIT in types(session(1, laps)))
        assertEquals(1.0, SessionMetrics.bestApexSpreadPercent(session(1, laps))!!, 1e-9)
    }

    // ---------------------------------------------------------------- Optimal Lap

    /** Laps whose fastest sectors add up to 98.000 s; none of them is itself that quick. */
    private val sectorLaps = listOf(
        LapFacts(100_000, 33_000, 33_000, 34_000),
        LapFacts(100_000, 34_000, 32_000, 34_000),
        LapFacts(100_000, 33_500, 33_500, 33_000)
    )

    private fun optimalSession(id: Long, fillers: Int, lapMs: Long) =
        session(id, List(fillers) { sectorLaps[it % 3] } + LapFacts(lapMs))

    @Test
    fun theoreticalBestIsTheSumOfBestSectors() {
        assertEquals(98_000L, SessionMetrics.theoreticalBestMs(listOf(session(1, sectorLaps))))
        assertNull(SessionMetrics.theoreticalBestMs(listOf(session(1, times(100_000)))))
    }

    @Test
    fun optimalLapBoundaryAtHalfAPercentOfTheoreticalBest() {
        // 0.5 % of 98.000 s is 0.490 s.
        assertTrue(TrophyType.OPTIMAL_LAP in types(optimalSession(1, 19, 98_490)))
        assertTrue(TrophyType.OPTIMAL_LAP !in types(optimalSession(1, 19, 98_491)))
    }

    @Test
    fun optimalLapNeeds20LifetimeValidLapsOnTheTrack() {
        assertTrue(TrophyType.OPTIMAL_LAP !in types(optimalSession(1, 18, 98_100)))
        val earlier = session(1, List(9) { sectorLaps[it % 3] })
        assertTrue(TrophyType.OPTIMAL_LAP in types(optimalSession(2, 10, 98_100), listOf(earlier)))
        val shorter = session(1, List(8) { sectorLaps[it % 3] })
        assertTrue(TrophyType.OPTIMAL_LAP !in types(optimalSession(2, 10, 98_100), listOf(shorter)))
    }

    @Test
    fun sectorsThatDoNotAddUpAreIgnored() {
        assertFalse(LapFacts(100_000, 33_000, 33_000, 33_000).hasSectors)
        assertTrue(LapFacts(99_000, 33_000, 33_000, 33_000).hasSectors)
    }

    // ---------------------------------------------------------------- merged-lap caveat

    @Test
    fun untrustedSessionEarnsOnlyTheFinisher() {
        val steady = times(60_000, 60_010, 59_990, 60_000, 60_000)
        val earlier = session(1, times(61_000, 61_000, 61_000))
        val suspect = session(2, steady, trusted = false)
        assertEquals(listOf(TrophyType.FINISHER), TrophyRules.evaluate(suspect, listOf(earlier)).map { it.type })
    }

    @Test
    fun trophiesAndMedalsAreTwoCategories() {
        assertEquals(8, TrophyType.trophies.size)
        assertEquals(6, TrophyType.medals.size)
        assertTrue(TrophyType.trophies.all { it.countsTowardLevels && it.metal != null })
        assertTrue(TrophyType.medals.none { it.countsTowardLevels || it.metal != null })
        assertNull("pre-3.08 types are unknown", TrophyType.fromName("TRACK_MASTER_GOLD"))
        assertNull(TrophyType.fromName("CONSISTENCY_GOLD"))
        assertEquals(TrophyType.APEX_EXIT, TrophyType.fromName("APEX_EXIT"))
    }

    private val consistency = setOf(TrophyType.STEADY_HAND, TrophyType.METRONOME, TrophyType.SURGICAL)
}
