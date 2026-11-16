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
import com.drivingcoach.data.db.entity.TrophyEntity
import com.drivingcoach.data.track.SaveTrackResult
import com.drivingcoach.data.track.TrackRepository
import com.drivingcoach.lap.LapDiagnosticsWriter
import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.progression.TrophyType
import com.drivingcoach.testing.ProgressionSeed
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * L2 (ASPICE SWE.5) coverage for awarding against Room: SRS PG-03, PG-11, PG-12, PG-13, PG-14, PG-22,
 * PG-28 … PG-31.
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
    @Inject lateinit var trackRepository: TrackRepository
    @Inject lateinit var lapDiagnosticsWriter: LapDiagnosticsWriter

    private lateinit var seed: ProgressionSeed
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hiltRule.inject()
        seed = ProgressionSeed(sessionDao, lapDao, File(context.filesDir, "telemetry"))
    }

    @After
    fun tearDown() {
        seed.files.forEach { lapDiagnosticsWriter.sidecarFor(it).delete() }
        seed.cleanUp()
    }

    @Test
    fun awardingTwiceStoresEachTrophyOnce() = runBlocking {
        val id = seed.session(startedAt = START, lapTimesMs = ProgressionSeed.STEADY_MINUTES)

        val first = progression.awardSession(id)
        val second = progression.awardSession(id)

        assertEquals(FIRST_VISIT, first.toSet())
        assertTrue("nothing is new the second time", second.isEmpty())
        assertEquals(FIRST_VISIT.size, trophyDao.getForSession(id).size)
        assertEquals(14_400.0, sessionDao.getSessionByIdSync(id)!!.distanceM!!, 50.0)
    }

    @Test
    fun theFirstVisitToATrackHasNoComparisonAwards() = runBlocking {
        val id = seed.session(startedAt = START, lapTimesMs = ProgressionSeed.STEADY_QUICKER)
        val awarded = progression.awardSession(id)
        listOf(TrophyType.PACE_SETTER, TrophyType.PLATEAU_BREAKER, TrophyType.TOP_GUN, TrophyType.PURPLE_SECTOR)
            .forEach { assertTrue("$it needs an earlier visit", it !in awarded) }
    }

    @Test
    fun aSecondVisitEarnsPaceSetterAndTopGun() = runBlocking {
        progression.awardSession(seed.session(startedAt = START, lapTimesMs = ProgressionSeed.STEADY_MINUTES))
        val second = seed.session(startedAt = START + DAY, lapTimesMs = ProgressionSeed.STEADY_QUICKER)
        assertEquals(SECOND_VISIT, progression.awardSession(second).toSet())
    }

    @Test
    fun careerSumsDistanceAndCountsOnlyLevelTrophies() = runBlocking {
        val first = seed.session(startedAt = START, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
        progression.awardSession(first)
        val second = seed.session(startedAt = START + DAY, lapTimesMs = ProgressionSeed.STEADY_QUICKER)
        progression.awardSession(second)

        val career = progression.career().first()
        assertEquals("14.4 km plus 13.9 km", 28.3, career.lifetimeKm, 0.1)
        assertEquals("24 km and a Bronze make a Novice; 56 km are needed for more", 3, career.progress.current.number)
        assertEquals(7, career.counts.levelTrophies)
        assertEquals("medals are kept but never count", 2, career.counts[TrophyType.FULL_STINT])
    }

    @Test
    fun rewardsSeparateTrophiesMedalsAndMilestones() = runBlocking {
        val first = seed.session(startedAt = START, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
        progression.awardSession(first)
        val second = seed.session(startedAt = START + DAY, lapTimesMs = ProgressionSeed.STEADY_QUICKER)
        progression.awardSession(second)

        val one = progression.rewards(first)!!
        assertEquals(FIRST_VISIT.filter { it.countsTowardLevels }.toSet(), one.newTrophies.toSet())
        assertEquals(FIRST_VISIT.filter { it.isMedal }.toSet(), one.newMedals.toSet())
        assertEquals("0 → 14.4 km crosses 8 km", listOf(8), one.milestonesKm)
        assertEquals(0.0, one.cvPercent!!, 0.0)

        val two = progression.rewards(second)!!
        assertEquals("14.4 → 28.3 km crosses 16 and 24 km", listOf(16, 24), two.milestonesKm)
        assertTrue(TrophyType.TOP_GUN in two.newMedals)
        assertTrue("24 km and a Bronze: Level 3", two.levelUp)
        assertEquals(3, two.after.progress.current.number)
    }

    /**
     * The v3.08 recompute (PG-13): rows written under the v3.07 rules are deleted and every
     * session re-awarded in the order it was driven, not the order it was stored — the later
     * session is inserted first here, and only a chronological replay makes it the Pace Setter.
     */
    @Test
    fun recomputeReplacesOldTrophiesChronologicallyAndOnce() = runBlocking {
        val later = seed.session(startedAt = START + DAY, lapTimesMs = ProgressionSeed.STEADY_QUICKER)
        val earlier = seed.session(startedAt = START, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
        val noLaps = seed.session(startedAt = START + 2 * DAY, lapTimesMs = emptyList())
        trophyDao.insertAll(
            listOf("FINISHER_BRONZE", "CONSISTENCY_GOLD").map { old(earlier, it) } +
                listOf("FINISHER_BRONZE", "PB_SILVER", "TRACK_MASTER_GOLD").map { old(later, it) }
        )
        assertEquals("old rows are unknown, so they count for nothing", 0,
            progression.career().first().counts.levelTrophies)
        dataStore.edit { it.remove(ProgressionRepository.RULES_V2_APPLIED) }

        progression.recomputeIfNeeded()

        assertEquals(FIRST_VISIT, typesOf(earlier))
        assertEquals(SECOND_VISIT, typesOf(later))
        assertTrue("no pre-3.08 row survives", trophyDao.getAll().all { TrophyType.fromName(it.type) != null })
        assertTrue("a session without laps gets a distance only", trophyDao.getForSession(noLaps).isEmpty())
        assertNotNull(sessionDao.getSessionByIdSync(noLaps)!!.distanceM)
        assertEquals(true, dataStore.data.first()[ProgressionRepository.RULES_V2_APPLIED])

        // Forcing it again rewrites exactly the same rows.
        val rows = trophyDao.getAll().map { Triple(it.sessionId, it.type, it.value) }.toSet()
        dataStore.edit { it.remove(ProgressionRepository.RULES_V2_APPLIED) }
        progression.recomputeIfNeeded()
        assertEquals(rows, trophyDao.getAll().map { Triple(it.sessionId, it.type, it.value) }.toSet())

        // Once done, a new unawarded session is left to award-at-stop.
        val afterReplay = seed.session(startedAt = START + 3 * DAY, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
        progression.recomputeIfNeeded()
        assertTrue(trophyDao.getForSession(afterReplay).isEmpty())
    }

    /** Baltar is catalogued at 1020 m, so six laps without a trace are 6.12 km (PG-04). */
    @Test
    fun missingTelemetryFallsBackToTrackLength() = runBlocking {
        val id = seed.session(
            startedAt = START, lapTimesMs = ProgressionSeed.STEADY_MINUTES,
            trackName = "Baltar", trackId = "baltar", withTelemetry = false
        )
        assertTrue("precondition: no trace on disk", !seed.telemetryFileFor(START).exists())

        val awarded = progression.awardSession(id)

        assertEquals(6 * 1_020.0, sessionDao.getSessionByIdSync(id)!!.distanceM!!, 0.0)
        assertTrue("6.12 km and six valid laps is a Finisher", TrophyType.FINISHER in awarded)
        assertTrue("no trace, no per-lap speed, no Apex Exit", TrophyType.APEX_EXIT !in awarded)
        assertTrue("6.12 km is short of a Full Stint", TrophyType.FULL_STINT !in awarded)
        assertEquals(6.12, progression.career().first().lifetimeKm, 1e-9)
    }

    /** A circuit the driver captured has no declared length, so no trace means 0 km. */
    @Test
    fun customTrackWithoutLengthIsZeroKm() = runBlocking {
        val saved = trackRepository.saveCapturedTrack("Car park", 41.0, -8.0, 41.0, -8.0003)
        val track = (saved as SaveTrackResult.Success).track
        assertNull("precondition: a captured circuit has no length", track.lengthM)

        val id = seed.session(
            startedAt = START, lapTimesMs = ProgressionSeed.STEADY_MINUTES,
            trackName = track.name, trackId = track.id, withTelemetry = false
        )
        val awarded = progression.awardSession(id)

        assertEquals(0.0, sessionDao.getSessionByIdSync(id)!!.distanceM!!, 0.0)
        assertTrue("no distance, no Finisher, however many laps", TrophyType.FINISHER !in awarded)
        assertTrue("the laps still earn consistency", TrophyType.SURGICAL in awarded)
        assertEquals(0.0, progression.career().first().lifetimeKm, 0.0)
    }

    /**
     * A sidecar recording a crossing refused at 30 s into a 60 s lap, against a 32 s floor,
     * is the LD-23 evidence of a lost boundary: only The Finisher survives (PG-10), and no
     * medal. The control session's sidecar refuses a crossing 2 s after the accepted one,
     * the routine same-pass duplicate, and keeps everything.
     */
    @Test
    fun sidecarMergedCaveatLeavesOnlyFinisher() = runBlocking {
        val suspect = seed.session(startedAt = START, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
        writeSidecar(seed.telemetryFileFor(START), suspect, START, rejectedAfterMs = 30_000L)
        val control = seed.session(startedAt = START + DAY, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
        writeSidecar(seed.telemetryFileFor(START + DAY), control, START + DAY, rejectedAfterMs = 2_000L)

        assertEquals(listOf(TrophyType.FINISHER), progression.awardSession(suspect))
        assertTrue(progression.awardSession(control).containsAll(listOf(TrophyType.FINISHER, TrophyType.SURGICAL)))
        val rewards = progression.rewards(suspect)!!
        assertEquals(listOf(TrophyType.FINISHER), rewards.newTrophies)
        assertTrue(rewards.newMedals.isEmpty())
    }

    @Test
    fun deletingSessionRemovesItsTrophies() = runBlocking {
        val first = seed.session(startedAt = START, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
        progression.awardSession(first)
        val second = seed.session(startedAt = START + DAY, lapTimesMs = ProgressionSeed.STEADY_QUICKER)
        progression.awardSession(second)
        assertTrue(trophyDao.getForSession(first).isNotEmpty())

        sessionDao.deleteById(first)

        assertTrue("CASCADE removes the deleted session's trophies", trophyDao.getForSession(first).isEmpty())
        assertTrue(trophyDao.getAll().all { it.sessionId == second })
        assertEquals(SECOND_VISIT.size, trophyDao.getForSession(second).size)
        val career = progression.career().first()
        assertEquals("only the remaining 13.9 km", 13.9, career.lifetimeKm, 0.05)
        assertEquals("the deleted Finisher no longer counts", 1, career.counts[TrophyType.FINISHER])
        assertEquals(1, career.counts[TrophyType.SURGICAL])
    }

    private suspend fun typesOf(sessionId: Long) =
        trophyDao.getForSession(sessionId).map { TrophyType.valueOf(it.type) }.toSet()

    private fun old(sessionId: Long, type: String) =
        TrophyEntity(sessionId = sessionId, type = type, trackKey = "name:test ring", value = 0L, awardedAt = 0L)

    private fun writeSidecar(telemetry: File, sessionId: Long, startedAt: Long, rejectedAfterMs: Long) {
        val accepted = List(7) { LocalLapDetector.Crossing(startedAt + it * 60_000L, 0.0, 0.0) }
        val diagnostics = LocalLapDetector.DetectionDiagnostics(
            sampleCount = 360, durationMs = 360_000L, observedSampleRateHz = 1.0,
            startLineLengthM = 12.0, startLineBearingDeg = 90.0, angleBetweenLineAndTravelDeg = 90.0,
            acceptedCrossings = accepted,
            rejectedCrossings = listOf(
                LocalLapDetector.RejectedCrossing(
                    startedAt + 60_000L + rejectedAfterMs, LocalLapDetector.RejectionReason.TOO_SOON, "injected"
                )
            ),
            lapCount = 6, minLapTimeMs = 32_000L
        )
        val written = lapDiagnosticsWriter.write(
            telemetry, sessionId,
            LocalLapDetector.DetectionOutcome(LocalLapDetector.DetectionResult.Success(emptyList()), diagnostics)
        )
        assertNotNull("sidecar written", written)
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val DAY = 86_400_000L

        /** [ProgressionSeed.STEADY_MINUTES] on a first visit. */
        val FIRST_VISIT = setOf(
            TrophyType.FINISHER, TrophyType.SURGICAL, TrophyType.APEX_EXIT,
            TrophyType.CARBON_COPY, TrophyType.CLEAN_AIR, TrophyType.FULL_STINT
        )

        /** [ProgressionSeed.STEADY_QUICKER] after it. */
        val SECOND_VISIT = FIRST_VISIT + TrophyType.PACE_SETTER + TrophyType.TOP_GUN
    }
}
