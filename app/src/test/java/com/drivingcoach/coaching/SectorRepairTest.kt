package com.drivingcoach.coaching

import com.drivingcoach.data.db.dao.CoachingInsightDao
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.entity.CoachingInsightEntity
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.telemetry.TelemetryFileReader
import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.lap.LapDiagnosticsWriter
import com.drivingcoach.lap.LapReplayHarness
import com.drivingcoach.lap.LocalLapDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [SectorRepair] rewrites data the driver has already seen, and it does so on a read
 * path, so the tests that matter are the ones about restraint rather than the ones
 * about arithmetic.
 *
 * Three of them guard a specific way this could do harm: writing when nothing changed,
 * deleting insights the driver paid for, and replacing coaching text with nothing. The
 * insight text is refreshed on every open (OC-32); without the merged-lap signal the
 * dream lap is built only from laps near the median, so a merged lap cannot inflate it
 * (OC-33).
 */
class SectorRepairTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var lapDao: FakeLapDao
    private lateinit var insightDao: FakeInsightDao
    private lateinit var diagnosticsWriter: LapDiagnosticsWriter
    private lateinit var repair: SectorRepair

    @Before
    fun setUp() {
        lapDao = FakeLapDao()
        insightDao = FakeInsightDao()
        diagnosticsWriter = LapDiagnosticsWriter()
        repair = SectorRepair(lapDao, insightDao, diagnosticsWriter)
    }

    // ==================== Correcting ====================

    @Test
    fun `sectors measured the old way are rewritten`() = runBlocking {
        val session = replay("s_mamede")
        // Shift every stored sector by half a second, as the unanchored ruler did.
        val stale = session.laps.map {
            it.copy(sector1Ms = it.sector1Ms + 500L, sector2Ms = it.sector2Ms - 500L)
        }

        val outcome = repair.repair(SESSION_ID, stale, session.samples, session.telemetryFile)

        assertEquals("every lap was wrong, so every lap should have moved",
            stale.size, outcome.lapsCorrected)
        assertEquals(stale.size, lapDao.sectorWrites.size)

        session.laps.forEach { original ->
            val written = lapDao.sectorWrites.getValue(original.id)
            assertEquals(
                "lap ${original.lapNumber} should have been restored to the measured value",
                Triple(original.sector1Ms, original.sector2Ms, original.sector3Ms),
                written
            )
        }
    }

    @Test
    fun `a session already measured correctly is not written to`() = runBlocking {
        val session = replay("s_mamede")

        val outcome = repair.repair(SESSION_ID, session.laps, session.samples, session.telemetryFile)

        assertEquals(0, outcome.lapsCorrected)
        assertFalse(outcome.changedAnything)
        assertTrue(
            "recomputation is deterministic, so a correct session must cause no writes",
            lapDao.sectorWrites.isEmpty()
        )
    }

    @Test
    fun `repairing twice writes only once`() = runBlocking {
        val session = replay("s_mamede")
        val stale = session.laps.map { it.copy(sector1Ms = it.sector1Ms + 900L) }

        repair.repair(SESSION_ID, stale, session.samples, session.telemetryFile)
        val writesAfterFirst = lapDao.sectorWrites.size
        lapDao.sectorWrites.clear()

        // Second pass sees the corrected values the first pass produced.
        val corrected = stale.map {
            val w = session.laps.first { l -> l.id == it.id }
            it.copy(sector1Ms = w.sector1Ms, sector2Ms = w.sector2Ms, sector3Ms = w.sector3Ms)
        }
        val second = repair.repair(SESSION_ID, corrected, session.samples, session.telemetryFile)

        assertTrue("the first pass should have corrected something", writesAfterFirst > 0)
        assertEquals("the repair must be idempotent", 0, second.lapsCorrected)
        assertTrue(lapDao.sectorWrites.isEmpty())
    }

    // ==================== Refusing to act ====================

    @Test
    fun `a session with no telemetry is left exactly as it was`() = runBlocking {
        val session = replay("s_mamede")
        val stale = session.laps.map { it.copy(sector1Ms = it.sector1Ms + 5_000L) }

        val outcome = repair.repair(SESSION_ID, stale, emptyList(), session.telemetryFile)

        assertEquals(0, outcome.lapsCorrected)
        assertTrue(lapDao.sectorWrites.isEmpty())
        assertTrue(insightDao.inserted.isEmpty())
    }

    @Test
    fun `a session with no laps does nothing`() = runBlocking {
        val session = replay("s_mamede")

        val outcome = repair.repair(SESSION_ID, emptyList(), session.samples, session.telemetryFile)

        assertEquals(0, outcome.lapsCorrected)
        assertTrue(lapDao.sectorWrites.isEmpty())
    }

    // ==================== Refreshing the insight text (OC-32) ====================

    @Test
    fun `a session recorded before the current insights gets them on open`() = runBlocking {
        val session = replay("cabo_do_mundo")
        // No sidecar: the session predates detection diagnostics. Its stored text is the
        // old engine's, with the Sector Diagnostic echoing the Best Lap.
        insightDao.inserted.add(localInsight("Focus on Sector 2", "You lose 1126ms here on average."))

        val outcome = repair.repair(SESSION_ID, session.laps, session.samples, session.telemetryFile)

        assertTrue("old wording must be replaced with today's", outcome.insightsRegenerated)
        val headlines = insightDao.inserted.map { it.headline }
        assertFalse(insightDao.inserted.any { it.detail == "You lose 1126ms here on average." })
        assertTrue(
            "without a sidecar the dream lap is still offered, built from laps near the median",
            headlines.any { it.contains("Dream Lap") }
        )
        assertTrue(insightDao.inserted.all { it.isLocalOnly })
    }

    @Test
    fun `insights already up to date are not rewritten`() = runBlocking {
        val session = replay("s_mamede")

        repair.repair(SESSION_ID, session.laps, session.samples, session.telemetryFile)
        insightDao.deletedLocalFor.clear()
        val second = repair.repair(SESSION_ID, session.laps, session.samples, session.telemetryFile)

        assertFalse("identical text must cause no writes on every open", second.insightsRegenerated)
        assertTrue(insightDao.deletedLocalFor.isEmpty())
    }

    @Test
    fun `stored insights are kept when the laps no longer support any`() = runBlocking {
        val session = replay("s_mamede")
        val existing = localInsight("Best Lap", "kept")
        insightDao.inserted.add(existing)

        val outcome = repair.repair(SESSION_ID, session.laps.take(1), session.samples, session.telemetryFile)

        assertFalse(outcome.insightsRegenerated)
        assertEquals(listOf(existing), insightDao.inserted)
    }

    @Test
    fun `with diagnostics the insights are regenerated`() = runBlocking {
        val session = replay("s_mamede")
        diagnosticsWriter.write(session.telemetryFile, SESSION_ID, session.outcome)
        val stale = session.laps.map { it.copy(sector1Ms = it.sector1Ms + 700L) }

        val outcome = repair.repair(SESSION_ID, stale, session.samples, session.telemetryFile)

        assertTrue(outcome.lapsCorrected > 0)
        assertTrue(outcome.insightsRegenerated)
        assertTrue(insightDao.inserted.isNotEmpty())
        assertTrue(
            "regenerated insights must be marked local",
            insightDao.inserted.all { it.isLocalOnly }
        )
    }

    @Test
    fun `regeneration removes only the locally generated insights`() = runBlocking {
        val session = replay("s_mamede")
        diagnosticsWriter.write(session.telemetryFile, SESSION_ID, session.outcome)
        val stale = session.laps.map { it.copy(sector1Ms = it.sector1Ms + 700L) }

        repair.repair(SESSION_ID, stale, session.samples, session.telemetryFile)

        assertEquals(
            "insights the driver paid for cannot be regenerated locally, so a local " +
                "recalculation must never take them with it (OC-09, OC-10)",
            listOf(SESSION_ID), insightDao.deletedLocalFor
        )
        assertTrue(
            "the indiscriminate delete must not have been used",
            insightDao.deletedAllFor.isEmpty()
        )
    }

    @Test
    fun `an unreadable sidecar is treated as no sidecar rather than as no caveat`() = runBlocking {
        val session = replay("s_mamede")
        diagnosticsWriter.sidecarFor(session.telemetryFile).writeText("{ this is not json")
        val stale = session.laps.map { it.copy(sector1Ms = it.sector1Ms + 700L) }

        repair.repair(SESSION_ID, stale, session.samples, session.telemetryFile)
        val withGarbage = insightDao.inserted.map { it.headline to it.detail }

        // The same session with no sidecar at all, in a fresh store.
        diagnosticsWriter.sidecarFor(session.telemetryFile).delete()
        insightDao = FakeInsightDao()
        SectorRepair(FakeLapDao(), insightDao, diagnosticsWriter)
            .repair(SESSION_ID, stale, session.samples, session.telemetryFile)

        assertEquals(
            "a sidecar that will not parse tells us nothing, and nothing is not 'no caveat'",
            insightDao.inserted.map { it.headline to it.detail }, withGarbage
        )
    }

    @Test
    fun `a null telemetry file still refreshes, without a top speed`() = runBlocking {
        val session = replay("s_mamede")
        val stale = session.laps.map { it.copy(sector1Ms = it.sector1Ms + 700L) }

        val outcome = repair.repair(SESSION_ID, stale, session.samples, telemetryFile = null)

        assertTrue(outcome.lapsCorrected > 0)
        assertTrue(outcome.insightsRegenerated)
        assertTrue(insightDao.inserted.none { it.headline.contains("Top Speed") })
    }

    // ==================== Helpers ====================

    private class Replay(
        val samples: List<TelemetrySample>,
        val laps: List<LapEntity>,
        val telemetryFile: File,
        val outcome: LocalLapDetector.DetectionOutcome
    )

    private fun replay(name: String): Replay {
        val fixture = LapReplayHarness.load(name, tempFolder.newFolder())
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(
            fixture.telemetryFile, fixture.startLine, LocalLapDetector.TrackPriors.NONE
        )
        val laps = (outcome.result as? LocalLapDetector.DetectionResult.Success)?.laps
            ?: error("$name should detect laps")
        val samples = runBlocking {
            TelemetryFileReader.readAll(fixture.telemetryFile.absolutePath)
        }
        // The detector reports laps; the database stores them. Converting here mirrors
        // what RecordingViewModel does at save time, ids included.
        return Replay(
            samples = samples,
            laps = laps.mapIndexed { i, lap ->
                LapEntity(
                    id = (i + 1).toLong(),
                    sessionId = SESSION_ID,
                    lapNumber = lap.lapNumber,
                    startTs = lap.startTs,
                    endTs = lap.endTs,
                    durationMs = lap.durationMs,
                    sector1Ms = lap.sector1Ms,
                    sector2Ms = lap.sector2Ms,
                    sector3Ms = lap.sector3Ms
                )
            },
            telemetryFile = fixture.telemetryFile,
            outcome = outcome
        )
    }

    private class FakeLapDao : LapDao {
        val sectorWrites = LinkedHashMap<Long, Triple<Long, Long, Long>>()

        override suspend fun updateSectors(lapId: Long, sector1Ms: Long, sector2Ms: Long, sector3Ms: Long) {
            sectorWrites[lapId] = Triple(sector1Ms, sector2Ms, sector3Ms)
        }

        override suspend fun insertLaps(laps: List<LapEntity>) = Unit
        override suspend fun insertLap(lap: LapEntity): Long = 0L
        override suspend fun getLapById(lapId: Long): LapEntity? = null
        override fun getLapsForSession(sessionId: Long): Flow<List<LapEntity>> = flowOf(emptyList())
        override fun getBestLap(sessionId: Long): Flow<LapEntity?> = flowOf(null)
        override suspend fun clearBestLap(sessionId: Long) = Unit
        override suspend fun setBestLap(lapId: Long) = Unit
        override suspend fun getFastestLap(sessionId: Long): LapEntity? = null
    }

    private class FakeInsightDao : CoachingInsightDao {
        val inserted = ArrayList<CoachingInsightEntity>()
        val deletedLocalFor = ArrayList<Long>()
        val deletedAllFor = ArrayList<Long>()

        override suspend fun insertInsights(insights: List<CoachingInsightEntity>) {
            inserted.addAll(insights)
        }

        override suspend fun insertInsight(insight: CoachingInsightEntity): Long {
            inserted.add(insight)
            return inserted.size.toLong()
        }

        override fun getInsightsForSession(sessionId: Long): Flow<List<CoachingInsightEntity>> =
            flowOf(inserted.filter { it.sessionId == sessionId })

        override suspend fun deleteInsightsForSession(sessionId: Long) {
            deletedAllFor.add(sessionId)
        }

        override suspend fun deleteLocalInsightsForSession(sessionId: Long) {
            deletedLocalFor.add(sessionId)
            inserted.removeAll { it.sessionId == sessionId && it.isLocalOnly }
        }

        override suspend fun getInsightsForSessionOnce(sessionId: Long): List<CoachingInsightEntity> =
            inserted.filter { it.sessionId == sessionId }
    }

    private fun localInsight(headline: String, detail: String) = CoachingInsightEntity(
        sessionId = SESSION_ID, headline = headline, detail = detail,
        generatedAt = 0L, isLocalOnly = true
    )

    private companion object {
        const val SESSION_ID = 42L
    }
}
