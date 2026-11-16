package com.drivingcoach.progression

import com.drivingcoach.progression.ProgressionFormat.DrivenTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionFormatTest {

    @Test
    fun drivenKmRoundsDown() {
        assertEquals("24.9", ProgressionFormat.drivenKm(24.96))
        assertEquals("124.6", ProgressionFormat.drivenKm(124.6))
        assertEquals("0.0", ProgressionFormat.drivenKm(0.0))
    }

    @Test
    fun remainingKmRoundsUp() {
        assertEquals("0.1", ProgressionFormat.remainingKm(0.04))
        assertEquals("15.0", ProgressionFormat.remainingKm(15.0))
    }

    @Test
    fun thresholdIsWholeKm() = assertEquals("450", ProgressionFormat.thresholdKm(450.0))

    @Test
    fun secondsHaveThreeDecimals() {
        assertEquals("0.800s", ProgressionFormat.seconds(800L))
        assertEquals("1.820s", ProgressionFormat.seconds(1_820.4))
    }

    @Test
    fun percentHasTwoDecimals() {
        assertEquals("2.10", ProgressionFormat.percent(2.1))
        assertEquals("0.75", ProgressionFormat.percent(0.75))
    }

    // ---------------------------------------------------------------- Hours driven (DP-11)

    @Test
    fun underAnHourIsWholeMinutesRoundedDown() {
        assertEquals(DrivenTime.Minutes(0), ProgressionFormat.drivenTime(0))
        assertEquals(DrivenTime.Minutes(0), ProgressionFormat.drivenTime(59_999))
        assertEquals(DrivenTime.Minutes(1), ProgressionFormat.drivenTime(60_000))
        assertEquals(DrivenTime.Minutes(59), ProgressionFormat.drivenTime(60 * 60_000L - 1))
    }

    @Test
    fun anHourOrMoreIsHoursWithOneDecimalRoundedDown() {
        assertEquals(DrivenTime.Hours("1.0"), ProgressionFormat.drivenTime(60 * 60_000L))
        // 2 h 25 min = 2.416 h.
        assertEquals(DrivenTime.Hours("2.4"), ProgressionFormat.drivenTime(8_700_000))
        assertEquals(DrivenTime.Hours("2.4"), ProgressionFormat.drivenTime(9_000_000 - 1))
        assertEquals(DrivenTime.Hours("2.5"), ProgressionFormat.drivenTime(9_000_000))
        assertEquals(DrivenTime.Hours("125.0"), ProgressionFormat.drivenTime(125 * 3_600_000L))
    }
}
