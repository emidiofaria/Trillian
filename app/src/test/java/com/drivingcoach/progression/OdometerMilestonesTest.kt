package com.drivingcoach.progression

import org.junit.Assert.assertEquals
import org.junit.Test

/** L1 (SWE.4) tests for the 8 km odometer milestones (SRS PG-31). */
class OdometerMilestonesTest {

    @Test
    fun stepIsEightKm() = assertEquals(8, OdometerMilestones.STEP_KM)

    @Test
    fun crossingTheFirstBoundary() {
        assertEquals(listOf(8), OdometerMilestones.crossed(0.0, 8_000.0))
        assertEquals(listOf(8), OdometerMilestones.crossed(7_999.0, 8_000.0))
        assertEquals(emptyList<Int>(), OdometerMilestones.crossed(0.0, 7_999.9))
    }

    @Test
    fun aBoundaryIsCelebratedOnlyOnce() {
        // Starting exactly on 8 km: already celebrated by the session that reached it.
        assertEquals(emptyList<Int>(), OdometerMilestones.crossed(8_000.0, 15_999.0))
        assertEquals(listOf(16), OdometerMilestones.crossed(8_000.0, 16_000.0))
    }

    @Test
    fun oneLongSessionCrossesSeveral() {
        assertEquals(listOf(8, 16, 24), OdometerMilestones.crossed(7_000.0, 25_000.0))
    }

    @Test
    fun noDistanceNoMilestone() {
        assertEquals(emptyList<Int>(), OdometerMilestones.crossed(12_000.0, 12_000.0))
    }
}
