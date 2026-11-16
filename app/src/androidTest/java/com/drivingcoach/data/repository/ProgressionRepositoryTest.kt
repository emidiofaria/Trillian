package com.drivingcoach.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.dao.TrophyDao
import com.drivingcoach.progression.TrophyType
import com.drivingcoach.testing.ProgressionSeed
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * L2 (ASPICE SWE.5) coverage for awarding against Room: SRS PG-03, PG-11, PG-12, PG-13, PG-14, PG-22.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProgressionRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var sessionDao: SessionDao
    @Inject lateinit var lapDao: LapDao
    @Inject lateinit var trophyDao: TrophyDao
    @Inject lateinit var progression: ProgressionRepository
    @Inject lateinit var dataStore: DataStore<Preferences>

    private lateinit var seed: ProgressionSeed
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hiltRule.inject()
        seed = ProgressionSeed(sessionDao, lapDao, File(context.filesDir, "telemetry"))
    }

    @After
    fun tearDown() = seed.cleanUp()

    @Test
    fun awardingTwiceStoresEachTrophyOnce() = runBlocking {
        val id = seed.session(startedAt = START, lapTimesMs = ProgressionSeed.STEADY_MINUTES)

        val first = progression.awardSession(id)
        val second = progression.awardSession(id)

        assertEquals(setOf(TrophyType.FINISHER_BRONZE, TrophyType.CONSISTENCY_GOLD), first.toSet())
        assertTrue("nothing is new the second time", second.isEmpty())
        assertEquals(2, trophyDao.getForSession(id).size)
        assertEquals(14_400.0, sessionDao.getSessionByIdSync(id)!!.distanceM!!, 50.0)
    }

    @Test
    fun theFirstVisitToATrackIsNeverAPersonalBest() = runBlocking {
        val id = seed.session(startedAt = START, lapTimesMs = ProgressionSeed.STEADY_QUICKER)
        val awarded = progression.awardSession(id)
        assertTrue(TrophyType.PB_SILVER !in awarded)
        assertTrue(TrophyType.TRACK_MASTER_GOLD !in awarded)
    }

    @Test
    fun careerSumsDistanceAndCountsOnlyMainTrophies() = runBlocking {
        val first = seed.session(startedAt = START, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
        progression.awardSession(first)
        val second = seed.session(startedAt = START + DAY, lapTimesMs = ProgressionSeed.STEADY_QUICKER)
        progression.awardSession(second)

        val career = progression.career().first()
        assertEquals("14.4 km plus 13.9 km", 28.3, career.lifetimeKm, 0.1)
        assertEquals("25 km and a Bronze make a Novice", 2, career.progress.current.number)
    }

    /**
     * The replay must follow the order sessions were driven, not the order they were
     * stored: the later session is inserted first here, and only a chronological replay
     * gives it the Personal Best.
     */
    @Test
    fun historyReplayIsSilentChronologicalAndOneOff() = runBlocking {
        val later = seed.session(startedAt = START + DAY, lapTimesMs = ProgressionSeed.STEADY_QUICKER)
        val earlier = seed.session(startedAt = START, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
        val noLaps = seed.session(startedAt = START + 2 * DAY, lapTimesMs = emptyList())
        dataStore.edit { it.remove(ProgressionRepository.BACKFILL_DONE) }

        progression.backfillIfNeeded()

        assertEquals(
            setOf(TrophyType.FINISHER_BRONZE, TrophyType.CONSISTENCY_GOLD),
            trophyDao.getForSession(earlier).map { TrophyType.valueOf(it.type) }.toSet()
        )
        assertEquals(
            setOf(
                TrophyType.FINISHER_BRONZE, TrophyType.PB_SILVER,
                TrophyType.CONSISTENCY_GOLD, TrophyType.TRACK_MASTER_GOLD
            ),
            trophyDao.getForSession(later).map { TrophyType.valueOf(it.type) }.toSet()
        )
        assertTrue("a session without laps gets a distance only", trophyDao.getForSession(noLaps).isEmpty())
        assertNotNull(sessionDao.getSessionByIdSync(noLaps)!!.distanceM)
        assertEquals(true, dataStore.data.first()[ProgressionRepository.BACKFILL_DONE])

        // Once done, a new unawarded session is left to award-at-stop.
        val afterReplay = seed.session(startedAt = START + 3 * DAY, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
        progression.backfillIfNeeded()
        assertTrue(trophyDao.getForSession(afterReplay).isEmpty())
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val DAY = 86_400_000L
    }
}
