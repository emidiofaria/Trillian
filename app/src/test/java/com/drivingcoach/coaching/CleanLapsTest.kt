package com.drivingcoach.coaching

import com.drivingcoach.data.db.entity.LapEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L1 (ASPICE SWE.4) coverage for the clean-lap rule shared by the coaching engine and
 * progression's credible best: SRS OC-33, PG-07.
 */
class CleanLapsTest {

    private fun laps(vararg durations: Long): List<LapEntity> = durations.mapIndexed { index, ms ->
        LapEntity(sessionId = 1L, lapNumber = index + 1, startTs = 0L, endTs = ms, durationMs = ms)
    }

    @Test
    fun medianOddAndEven() {
        assertEquals("odd count: the middle value, whatever the input order",
            62_000.0, CleanLaps.medianOf(listOf(70_000L, 60_000L, 62_000L)), 0.0)
        assertEquals("even count: the mean of the middle two",
            61_500.0, CleanLaps.medianOf(listOf(63_000L, 60_000L, 59_000L, 64_000L)), 0.0)
        assertEquals("a single value is its own median", 61_000.0, CleanLaps.medianOf(listOf(61_000L)), 0.0)
        // An out-lap drags the mean but not the median, which is why the median is the reference.
        assertEquals(60_000.0, CleanLaps.medianOf(listOf(60_000L, 59_000L, 167_000L, 61_000L, 60_000L)), 0.0)
    }

    @Test
    fun zeroDurationsIgnored() {
        // Two zero-length laps would pull an even median down to 30.5 s and reject every real lap.
        val clean = CleanLaps.of(laps(0L, 60_000L, 0L, 61_000L, 62_000L))
        assertEquals(listOf(60_000L, 61_000L, 62_000L), clean.map { it.durationMs })
        assertTrue(CleanLaps.of(laps(0L, 0L)).isEmpty())
        assertFalse("a zero median accepts nothing", CleanLaps.isRepresentative(0L, 0.0))
    }

    @Test
    fun fifteenPercentEdgeIsInclusive() {
        assertTrue(CleanLaps.isRepresentative(115_000L, 100_000.0))
        assertTrue(CleanLaps.isRepresentative(85_000L, 100_000.0))
        assertFalse(CleanLaps.isRepresentative(115_001L, 100_000.0))
        assertFalse(CleanLaps.isRepresentative(84_999L, 100_000.0))

        val kept = CleanLaps.of(laps(85_000L, 100_000L, 100_000L, 115_000L, 115_001L, 84_999L))
        assertEquals("median 100 s: exactly ±15 % stays, a millisecond beyond goes",
            listOf(85_000L, 100_000L, 100_000L, 115_000L), kept.map { it.durationMs })
    }

    @Test
    fun emptyListReturnsEmpty() {
        assertTrue(CleanLaps.of(emptyList()).isEmpty())
    }
}
