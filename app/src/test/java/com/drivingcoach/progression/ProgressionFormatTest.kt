package com.drivingcoach.progression

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
    fun thresholdIsWholeKm() = assertEquals("350", ProgressionFormat.thresholdKm(350.0))

    @Test
    fun secondsHaveThreeDecimals() {
        assertEquals("0.800s", ProgressionFormat.seconds(800L))
        assertEquals("1.820s", ProgressionFormat.seconds(1_820.4))
    }
}
