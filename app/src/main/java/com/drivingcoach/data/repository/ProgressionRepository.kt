package com.drivingcoach.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
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
import com.drivingcoach.progression.AwardedTrophy
import com.drivingcoach.progression.CareerSnapshot
import com.drivingcoach.progression.LapFacts
import com.drivingcoach.progression.LapTopSpeeds
import com.drivingcoach.progression.OdometerMilestones
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

/** Everything the Rewards screen shows for one session (SRS PG-19, PG-20, PG-31). */
data class SessionRewards(
    val sessionId: Long,
    val trackName: String,
    val distanceKm: Double,
    val lapCount: Int,
    val bestLapMs: Long?,
    /** Lowest 5-valid-lap coefficient of variation, percent; null under five consecutive valid laps. */
    val cvPercent: Double?,
    /** Level trophies this session holds, lowest metal first. */
    val newTrophies: List<TrophyType>,
    /** Session medals this session holds (they never count towards levels). */
    val newMedals: List<TrophyType>,
    /** 8 km odometer milestones this session crossed, ascending km (celebration only). */
    val milestonesKm: List<Int>,
    /** Career as it stood without this session. */
    val before: CareerSnapshot,
    val after: CareerSnapshot,
    val recentProgress: RecentProgress
) {
    val levelUp: Boolean get() = after.progress.current.number > before.progress.current.number
}

/**
 * Distance, trophies, medals and the derived career (SRS §11.2).
 *
 * Writes are serialised by one mutex so the stop-time award and the recompute never
 * interleave. Every write is idempotent: rows are unique per session and type, and a
 * session's awards depend only on its own laps and those of earlier sessions.
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
     * Per-lap top speeds read from the trace, keyed by session and remembered with the lap
     * spans they were read for, so an edited lap list is reread rather than misread.
     */
    private val speedCache = ConcurrentHashMap<Long, Pair<List<LapTopSpeeds.Span>, List<Double?>>>()

    /**
     * Measures and awards a just-finished session. The distance is always remeasured
     * here, since a replay that ran while the session was still recording would have
     * stored a partial figure. Returns only the awards this call added.
     */
    suspend fun awardSession(sessionId: Long): List<TrophyType> = withContext(ioDispatcher) {
        writeLock.withLock {
            trustCache.remove(sessionId)
            speedCache.remove(sessionId)
            val session = sessionDao.getSessionByIdSync(sessionId) ?: return@withLock emptyList()
            award(session)
        }
    }

    /**
     * One-off recompute of the whole career under the v3.08 rules (SRS PG-13): every
     * existing trophy is deleted and every session re-awarded, oldest first, in one
     * transaction. Sessions recorded before progression existed are measured here.
     * The flag is set only after the transaction commits, so an interrupted recompute
     * simply runs again, and a second run would produce the same rows.
     */
    suspend fun recomputeIfNeeded() = withContext(ioDispatcher) {
        if (dataStore.data.first()[RULES_V2_APPLIED] == true) return@withContext
        writeLock.withLock {
            if (dataStore.data.first()[RULES_V2_APPLIED] == true) return@withLock
            recompute()
            dataStore.edit { it[RULES_V2_APPLIED] = true }
        }
    }

    private suspend fun recompute() {
        val sessions = sessionDao.getAllSessionsOldestFirst()
        val lapsBySession = lapDao.getAllLaps().groupBy { it.sessionId }

        val distances = sessions.associate { s ->
            s.id to (s.distanceM ?: measure(s, lapsBySession[s.id].orEmpty().size))
        }
        val facts = sessions.map { factsOf(it, lapsBySession[it.id].orEmpty(), distances.getValue(it.id)) }

        val now = System.currentTimeMillis()
        val byId = sessions.associateBy { it.id }
        val factsById = facts.associateBy { it.sessionId }
        val rows = TrophyRules.recompute(facts).flatMap { (id, awards) ->
            awards.map { entityOf(byId.getValue(id), factsById.getValue(id), it, now) }
        }

        database.withTransaction {
            trophyDao.deleteAll()
            sessions.filter { it.distanceM == null }.forEach { sessionDao.updateDistance(it.id, distances.getValue(it.id)) }
            trophyDao.insertAll(rows)
        }
    }

    fun career(): Flow<CareerSnapshot> =
        combine(sessionDao.getAllSessions(), lapDao.observeAllLaps(), trophyDao.observeAll()) { sessions, laps, trophies ->
            val lapsBySession = laps.groupBy { it.sessionId }
            val facts = sessions.map { factsOf(it, lapsBySession[it.id].orEmpty(), it.distanceM ?: 0.0) }
            ProgressionGoals.career(facts, trophies.mapNotNull(::recordOf))
        }.flowOn(ioDispatcher)

    suspend fun rewards(sessionId: Long): SessionRewards? = withContext(ioDispatcher) {
        val sessions = sessionDao.getAllSessionsOldestFirst()
        val session = sessions.firstOrNull { it.id == sessionId } ?: return@withContext null
        val lapsBySession = lapDao.getAllLaps().groupBy { it.sessionId }
        val trophies = trophyDao.getAll().mapNotNull(::recordOf)

        val allFacts = sessions.map { factsOf(it, lapsBySession[it.id].orEmpty(), it.distanceM ?: 0.0) }
        val facts = allFacts.first { it.sessionId == sessionId }
        val others = allFacts.filter { it.sessionId != sessionId }
        val mine = trophies.filter { it.sessionId == sessionId }.map { it.type }.sortedBy { it.ordinal }

        val beforeM = others.filter { TrophyRules.isEarlier(it, facts) }.sumOf { it.distanceM }

        SessionRewards(
            sessionId = sessionId,
            trackName = session.trackName,
            distanceKm = facts.distanceM / 1000.0,
            lapCount = facts.lapCount,
            bestLapMs = facts.credibleBestMs,
            cvPercent = facts.bestCvPercent,
            newTrophies = mine.filter { it.countsTowardLevels },
            newMedals = mine.filter { it.isMedal },
            milestonesKm = OdometerMilestones.crossed(beforeM, beforeM + facts.distanceM),
            before = ProgressionGoals.career(others, trophies.filter { it.sessionId != sessionId }),
            after = ProgressionGoals.career(allFacts, trophies),
            recentProgress = ProgressionGoals.recentProgress(facts, others)
        )
    }

    private suspend fun award(session: SessionEntity): List<TrophyType> {
        val laps = lapDao.getLapsForSessionSync(session.id)
        val distance = measure(session, laps.size)
        val facts = factsOf(session, laps, distance)

        val allLaps = lapDao.getAllLaps().groupBy { it.sessionId }
        val history = sessionDao.getAllSessionsOldestFirst()
            .filter { it.id != session.id }
            .map { factsOf(it, allLaps[it.id].orEmpty(), it.distanceM ?: 0.0) }

        val awarded = TrophyRules.evaluate(facts, history)
        val now = System.currentTimeMillis()
        val rowIds = database.withTransaction {
            sessionDao.updateDistance(session.id, distance)
            trophyDao.insertAll(awarded.map { entityOf(session, facts, it, now) })
        }
        return awarded.filterIndexed { index, _ -> rowIds.getOrNull(index) != -1L }.map { it.type }
    }

    private fun entityOf(session: SessionEntity, facts: SessionFacts, award: AwardedTrophy, now: Long) =
        TrophyEntity(
            sessionId = session.id,
            type = award.type.name,
            trackKey = facts.trackKey,
            value = award.value,
            awardedAt = session.endedAt ?: now
        )

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

    private fun topSpeedsFor(session: SessionEntity, ordered: List<LapEntity>): List<Double?> {
        if (ordered.isEmpty() || session.rawFilePath.isBlank()) return ordered.map { null }
        val spans = ordered.map { LapTopSpeeds.Span(it.startTs, it.endTs) }
        speedCache[session.id]?.let { (cachedSpans, speeds) -> if (cachedSpans == spans) return speeds }
        val speeds = runCatching { LapTopSpeeds.ofTelemetryFile(File(session.rawFilePath), spans) }.getOrNull()
            ?: return ordered.map { null }
        speedCache[session.id] = spans to speeds
        return speeds
    }

    private fun factsOf(session: SessionEntity, laps: List<LapEntity>, distanceM: Double): SessionFacts {
        val ordered = laps.sortedBy { it.lapNumber }
        val speeds = topSpeedsFor(session, ordered)
        return SessionFacts(
            sessionId = session.id,
            startedAt = session.startedAt,
            trackKey = TrackKey.of(session.trackId, session.trackName),
            trackName = session.trackName,
            distanceM = distanceM,
            laps = ordered.mapIndexed { i, lap ->
                LapFacts(lap.durationMs, lap.sector1Ms, lap.sector2Ms, lap.sector3Ms, speeds.getOrNull(i))
            },
            lapBoundariesTrusted = trustedFor(session)
        )
    }

    private fun recordOf(entity: TrophyEntity): TrophyRecord? =
        TrophyType.fromName(entity.type)?.let {
            TrophyRecord(entity.sessionId, it, entity.trackKey, entity.value, entity.awardedAt)
        }

    companion object {
        /** Set once the v3.08 recompute has committed (SRS PG-13). Replaces the v3.07 backfill flag. */
        val RULES_V2_APPLIED = booleanPreferencesKey("progression_rules_v2_applied")
    }
}
