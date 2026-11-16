package com.drivingcoach.testing

import com.drivingcoach.data.db.dao.TrophyDao
import com.drivingcoach.data.db.entity.TrophyEntity

/**
 * Switches for making progression fail on purpose in instrumented tests (SRS PG-12).
 *
 * [com.drivingcoach.data.repository.ProgressionRepository] is a concrete class, so it
 * cannot be swapped for a fake; the fault is injected one layer down instead, at the
 * trophy write every award goes through. A test that sets a switch must reset it in
 * `@After`: Hilt rebuilds the component per test, but this object outlives it.
 */
object ProgressionFaults {
    @Volatile
    var failTrophyWrites: Boolean = false

    /** How many writes were refused, so a test can prove its fault was actually reached. */
    @Volatile
    var failures: Int = 0

    fun reset() {
        failTrophyWrites = false
        failures = 0
    }
}

/** The real DAO, except that writes throw while [ProgressionFaults.failTrophyWrites] is set. */
class FaultInjectingTrophyDao(private val real: TrophyDao) : TrophyDao by real {

    override suspend fun insertAll(trophies: List<TrophyEntity>): List<Long> {
        if (ProgressionFaults.failTrophyWrites) {
            ProgressionFaults.failures++
            throw IllegalStateException("Injected trophy write failure (ProgressionFaults)")
        }
        return real.insertAll(trophies)
    }
}
