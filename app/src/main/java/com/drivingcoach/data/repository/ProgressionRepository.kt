package com.drivingcoach.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
import com.drivingcoach.coaching.CleanLaps
import com.drivingcoach.data.db.DrivingCoachDatabase
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.dao.TrophyDao
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.data.db.entity.TrophyEntity
import com.drivingcoach.data.track.TrackRepository
import com.drivingcoach.di.IoDispatcher
import com.drivingcoach.lap.LapDiagnosticsWriter
import com.drivingcoach.lap.MergedLapCaveat
import com.drivingcoach.progression.CareerSnapshot
import com.drivingcoach.progression.ProgressionGoals
import com.drivingcoach.progression.RecentProgress
import com.drivingcoach.progression.SessionDistance
import com.drivingcoach.progression.SessionFacts
import com.drivingcoach.progression.TrackKey
import com.drivingcoach.progression.TrophyRecord
import com.drivingcoach.progression.TrophyRules
import com.drivingcoach.progression.TrophyType
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Everything the Rewards screen shows for one session (SRS PG-19, PG-20). */
data class SessionRewards(
    val sessionId: Long,
    val trackName: String,
    val distanceKm: Double,
    val lapCount: Int,
    val bestLapMs: Long?,
    /** Population stdDev of the session's clean laps, ms; null under two clean laps. */
    val consistencyStdDevMs: Double?,
    val newTrophies: List<TrophyType>,
    /** Career as it stood without this session. */
    val before: CareerSnapshot,
    val after: CareerSnapshot,
    val recentProgress: RecentProgress
) {
    val levelUp: Boolean get() = after.progress.current.number > before.progress.current.number
}

/**
 * Distance, trophies and the derived career (SRS §11.2).
 *
 * Writes are serialised by one mutex so the stop-time award and the history replay
 * never interleave. Every write is idempotent: trophies are unique per session and type,
 * and a session's trophies depend only on its own laps and those of earlier sessions.
 */
@Singleton
class ProgressionRepository @Inject constructor(
    private val database: DrivingCoachDatabase,
    private val sessionDao: SessionDao,
    private val lapDao: LapDao,
    private val trophyDao: TrophyDao,
    private val trackRepository: TrackRepository,
    private val lapDiagnosticsWriter: LapDiagnosticsWriter,
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val writeLock = Mutex()

    /** Whether a session's lap diagnostics carry the merged-lap caveat; read once per session. */
    private val trustCache = ConcurrentHashMap<Long, Boolean>()

    /**
     * Measures and awards a just-finished session. The distance is always remeasured
     * here, since a replay that ran while the session was still recording would have
     * stored a partial figure. Returns only the trophies this call added.
     */
    suspend fun awardSession(sessionId: Long): List<TrophyType> = withContext(ioDispatcher) {
        writeLock.withLock {
            trustCache.remove(sessionId)
            val session = sessionDao.getSessionByIdSync(sessionId) ?: return@withLock emptyList()
            award(session, remeasure = true)
        }
    }

    /**
     * One-off silent replay of every session recorded before progression existed,
     * oldest first (SRS PG-13). The flag is set only after the last session, so an
     * interrupted replay simply runs again.
     */
    suspend fun backfillIfNeeded() = withContext(ioDispatcher) {
        if (dataStore.data.first()[BACKFILL_DONE] == true) return@withContext
        writeLock.withLock {
            if (dataStore.data.first()[BACKFILL_DONE] == true) return@withLock
            for (session in sessionDao.getAllSessionsOldestFirst()) {
                award(session, remeasure = false)
            }
            dataStore.edit { it[BACKFILL_DONE] = true }
        }
    }

    fun career(): Flow<CareerSnapshot> =
        combine(sessionDao.getAllSessions(), lapDao.observeAllLaps(), trophyDao.observeAll()) { sessions, laps, trophies ->
            val lapsBySession = laps.groupBy { it.sessionId }
            val facts = sessions.map { factsOf(it, lapsBySession[it.id].orEmpty(), it.distanceM ?: 0.0, trustedFor(it)) }
            ProgressionGoals.career(facts, trophies.mapNotNull(::recordOf))
        }.flowOn(ioDispatcher)

    suspend fun rewards(sessionId: Long): SessionRewards? = withContext(ioDispatcher) {
        val sessions = sessionDao.getAllSessionsOldestFirst()
        val session = sessions.firstOrNull { it.id == sessionId } ?: return@withContext null
        val lapsBySession = lapDao.getAllLaps().groupBy { it.sessionId }
        val trophies = trophyDao.getAll().mapNotNull(::recordOf)

        val allFacts = sessions.map { factsOf(it, lapsBySession[it.id].orEmpty(), it.distanceM ?: 0.0, trustedFor(it)) }
        val facts = allFacts.first { it.sessionId == sessionId }
        val others = allFacts.filter { it.sessionId != sessionId }
        val clean = CleanLaps.of(lapsBySession[sessionId].orEmpty()).map { it.durationMs }

        SessionRewards(
            sessionId = sessionId,
            trackName = session.trackName,
            distanceKm = facts.distanceM / 1000.0,
            lapCount = facts.lapCount,
            bestLapMs = facts.credibleBestMs,
            consistencyStdDevMs = if (clean.size >= 2) TrophyRules.populationStdDev(clean) else null,
            newTrophies = trophies.filter { it.sessionId == sessionId }.map { it.type }.sortedBy { it.ordinal },
            before = ProgressionGoals.career(others, trophies.filter { it.sessionId != sessionId }),
            after = ProgressionGoals.career(allFacts, trophies),
            recentProgress = ProgressionGoals.recentProgress(facts, others)
        )
    }

    private suspend fun award(session: SessionEntity, remeasure: Boolean): List<TrophyType> {
        val laps = lapDao.getLapsForSessionSync(session.id)
        val distance = if (remeasure || session.distanceM == null) measure(session, laps.size) else session.distanceM
        val facts = factsOf(session, laps, distance, trustedFor(session))

        val allLaps = lapDao.getAllLaps().groupBy { it.sessionId }
        val history = sessionDao.getAllSessionsOldestFirst()
            .filter { it.id != session.id }
            .map { factsOf(it, allLaps[it.id].orEmpty(), it.distanceM ?: 0.0, trusted = true) }

        val awarded = TrophyRules.evaluate(facts, history)
        val now = System.currentTimeMillis()
        val rowIds = database.withTransaction {
            sessionDao.updateDistance(session.id, distance)
            trophyDao.insertAll(awarded.map {
                TrophyEntity(
                    sessionId = session.id,
                    type = it.type.name,
                    trackKey = facts.trackKey,
                    value = it.value,
                    awardedAt = session.endedAt ?: now
                )
            })
        }
        return awarded.filterIndexed { index, _ -> rowIds.getOrNull(index) != -1L }.map { it.type }
    }

    private suspend fun measure(session: SessionEntity, lapCount: Int): Double {
        val fromTrace = session.rawFilePath.takeIf { it.isNotBlank() }
            ?.let { runCatching { SessionDistance.ofTelemetryFile(File(it)) }.getOrNull() }
        if (fromTrace != null) return fromTrace
        val length = session.trackId?.let { runCatching { trackRepository.getTrack(it) }.getOrNull()?.lengthM }
        return SessionDistance.fallback(lapCount, length)
    }

    private fun trustedFor(session: SessionEntity): Boolean = trustCache.getOrPut(session.id) {
        if (session.rawFilePath.isBlank()) return@getOrPut true
        val diagnostics = runCatching { lapDiagnosticsWriter.read(File(session.rawFilePath)) }.getOrNull()
        MergedLapCaveat.of(diagnostics) == null
    }

    private fun factsOf(session: SessionEntity, laps: List<LapEntity>, distanceM: Double, trusted: Boolean) =
        SessionFacts(
            sessionId = session.id,
            startedAt = session.startedAt,
            trackKey = TrackKey.of(session.trackId, session.trackName),
            trackName = session.trackName,
            distanceM = distanceM,
            lapDurationsMs = laps.sortedBy { it.lapNumber }.map { it.durationMs },
            lapBoundariesTrusted = trusted
        )

    private fun recordOf(entity: TrophyEntity): TrophyRecord? =
        TrophyType.fromName(entity.type)?.let {
            TrophyRecord(entity.sessionId, it, entity.trackKey, entity.value, entity.awardedAt)
        }

    companion object {
        val BACKFILL_DONE = booleanPreferencesKey("progression_backfill_v1_done")
    }
}
