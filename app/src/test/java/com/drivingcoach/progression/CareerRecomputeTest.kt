package com.drivingcoach.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** L1 (SWE.4) tests for the chronological re-award of every session (SRS PG-13). */
class CareerRecomputeTest {

    private fun session(id: Long, startedAt: Long, laps: List<Long>, track: String = "name:baltar", km: Double = 9.0) =
        SessionFacts(id, startedAt, track, "Baltar", km * 1000, laps.map { LapFacts(it) }, true)

    private val career = listOf(
        session(1, 1_000, listOf(62_000, 61_000, 61_500, 61_200, 61_800)),
        session(2, 2_000, listOf(60_500, 60_400, 60_600, 60_450, 60_550)),
        session(3, 3_000, listOf(70_000, 70_100), track = "name:leca", km = 2.0),
        session(4, 4_000, listOf(60_000, 60_010, 59_990, 60_000, 60_005, 59_995))
    )

    @Test
    fun everySessionIsAwardedAgainstOnlyItsPast() {
        val result = TrophyRules.recompute(career).toMap()
        assertEquals(listOf(1L, 2L, 3L, 4L), TrophyRules.recompute(career).map { it.first })
        // The first visit cannot be a Pace Setter; the second and fourth are.
        assertTrue(result.getValue(1).none { it.type == TrophyType.PACE_SETTER })
        assertTrue(result.getValue(2).any { it.type == TrophyType.PACE_SETTER })
        assertTrue(result.getValue(4).any { it.type == TrophyType.PACE_SETTER })
        // Each session equals a direct evaluation against its own past.
        for ((id, awards) in result) {
            val s = career.single { it.sessionId == id }
            assertEquals(TrophyRules.evaluate(s, career.filter { TrophyRules.isEarlier(it, s) }), awards)
        }
    }

    @Test
    fun recomputeIsIdempotent() {
        assertEquals(TrophyRules.recompute(career), TrophyRules.recompute(career))
    }

    @Test
    fun inputOrderDoesNotMatter() {
        assertEquals(TrophyRules.recompute(career), TrophyRules.recompute(career.reversed()))
        assertEquals(TrophyRules.recompute(career), TrophyRules.recompute(listOf(career[2], career[0], career[3], career[1])))
    }

    @Test
    fun aLaterSessionNeverChangesEarlierAwards() {
        val withoutLast = TrophyRules.recompute(career.dropLast(1))
        val withLast = TrophyRules.recompute(career)
        assertEquals(withoutLast, withLast.dropLast(1))
    }
}
