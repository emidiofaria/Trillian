package com.drivingcoach.progression

import kotlin.math.floor

/**
 * The 8 km odometer milestones (SRS PG-31): a celebration on the Rewards screen whenever the
 * career odometer crosses a multiple of 8 km. Nothing is stored and nothing is unlocked; the
 * crossings are derived from the distance before and after the session, so they are the same
 * however many times the screen is opened.
 */
object OdometerMilestones {

    const val STEP_KM = 8

    /** Every multiple of [STEP_KM] in (beforeM, afterM], in km, ascending. */
    fun crossed(beforeM: Double, afterM: Double): List<Int> {
        if (afterM <= beforeM) return emptyList()
        val stepM = STEP_KM * 1000.0
        val first = floor(beforeM / stepM).toInt() + 1
        val last = floor(afterM / stepM).toInt()
        if (last < first) return emptyList()
        return (first..last).map { it * STEP_KM }
    }
}
