package com.drivingcoach.progression

import com.drivingcoach.coaching.CleanLaps
import kotlin.math.sqrt

/** The six trophies a session can win (SRS PG-05 … PG-09). */
enum class TrophyType(val metal: Metal, val countsTowardLevels: Boolean) {
    FINISHER_BRONZE(Metal.BRONZE, true),
    PB_SILVER(Metal.SILVER, true),
    TRACK_MASTER_GOLD(Metal.GOLD, true),
    CONSISTENCY_BRONZE(Metal.BRONZE, false),
    CONSISTENCY_SILVER(Metal.SILVER, false),
    CONSISTENCY_GOLD(Metal.GOLD, false);

    enum class Metal { BRONZE, SILVER, GOLD }

    companion object {
        fun fromName(name: String): TrophyType? = values().firstOrNull { it.name == name }
    }
}

/** What one session contributes to the trophy rules, with nothing Android about it. */
data class SessionFacts(
    val sessionId: Long,
    val startedAt: Long,
    val trackKey: String,
    val trackName: String,
    val distanceM: Double,
    /** Every recorded lap's duration, in recorded order. */
    val lapDurationsMs: List<Long>,
    /** False when the lap detector reported a possible merged lap (SRS LD-23). */
    val lapBoundariesTrusted: Boolean = true
) {
    val lapCount: Int get() = lapDurationsMs.size

    /**
     * The fastest lap that is within ±15 % of the session median (SRS OC-17 / CF-07).
     * A half-lap from a split boundary is quick and wrong; this is what stops it
     * standing as a personal best or as the time a later session must beat.
     */
    val credibleBestMs: Long?
        get() {
            val durations = lapDurationsMs.filter { it > 0L }
            if (durations.isEmpty()) return null
            val median = CleanLaps.medianOf(durations)
            return durations.filter { CleanLaps.isRepresentative(it, median) }.minOrNull()
        }
}

data class AwardedTrophy(
    val type: TrophyType,
    /** Lap time in ms (PB, Track Master), stdDev in ms (consistency), metres (Finisher). */
    val value: Long
)

object TrackKey {
    /** Catalogue id when the session had one, else the name, normalised (SRS PG-06). */
    fun of(trackId: String?, trackName: String): String {
        val id = trackId?.trim()?.lowercase().orEmpty()
        return if (id.isNotEmpty()) "id:$id" else "name:${trackName.trim().lowercase()}"
    }
}

/**
 * The per-session trophy rules. Pure and deterministic: a session's trophies are a
 * function of its own laps and of the laps of earlier sessions on the same track only,
 * which is what makes the one-off history replay (PG-13) give the same answer as
 * awarding at the time would have.
 */
object TrophyRules {

    const val FINISHER_MIN_DISTANCE_M = 5_000.0
    const val FINISHER_MIN_LAPS = 5
    const val CONSISTENCY_WINDOW = 5
    const val CONSISTENCY_BRONZE_MS = 3_000.0
    const val CONSISTENCY_SILVER_MS = 1_500.0
    const val CONSISTENCY_GOLD_MS = 750.0

    fun evaluate(session: SessionFacts, history: List<SessionFacts>): List<AwardedTrophy> =
        evaluate(session, previousBestOnTrack(session, history))

    fun evaluate(session: SessionFacts, previousBestMs: Long?): List<AwardedTrophy> {
        val awarded = ArrayList<AwardedTrophy>()

        if (session.distanceM >= FINISHER_MIN_DISTANCE_M && session.lapCount >= FINISHER_MIN_LAPS) {
            awarded += AwardedTrophy(TrophyType.FINISHER_BRONZE, session.distanceM.toLong())
        }

        if (!session.lapBoundariesTrusted) return awarded

        val best = session.credibleBestMs
        val isPb = best != null && previousBestMs != null && best < previousBestMs
        if (isPb) awarded += AwardedTrophy(TrophyType.PB_SILVER, best!!)

        val steadiest = steadiestWindowStdDevMs(session.lapDurationsMs)
        val consistency = steadiest?.let { consistencyTierFor(it) }
        if (consistency != null) awarded += AwardedTrophy(consistency, Math.round(steadiest))

        if (isPb && consistency == TrophyType.CONSISTENCY_GOLD) {
            awarded += AwardedTrophy(TrophyType.TRACK_MASTER_GOLD, best!!)
        }
        return awarded
    }

    /**
     * The best credible lap from every session on the same track that started before
     * this one. Null when there is none, which is what withholds a PB on a first visit.
     */
    fun previousBestOnTrack(session: SessionFacts, history: List<SessionFacts>): Long? =
        history.asSequence()
            .filter { it.trackKey == session.trackKey && isEarlier(it, session) }
            .mapNotNull { it.credibleBestMs }
            .minOrNull()

    fun isEarlier(a: SessionFacts, b: SessionFacts): Boolean =
        a.startedAt < b.startedAt || (a.startedAt == b.startedAt && a.sessionId < b.sessionId)

    /** Highest tier only: a session that qualifies for Gold does not also take Silver. */
    fun consistencyTierFor(stdDevMs: Double): TrophyType? = when {
        stdDevMs < CONSISTENCY_GOLD_MS -> TrophyType.CONSISTENCY_GOLD
        stdDevMs < CONSISTENCY_SILVER_MS -> TrophyType.CONSISTENCY_SILVER
        stdDevMs < CONSISTENCY_BRONZE_MS -> TrophyType.CONSISTENCY_BRONZE
        else -> null
    }

    fun thresholdMsFor(type: TrophyType): Double? = when (type) {
        TrophyType.CONSISTENCY_BRONZE -> CONSISTENCY_BRONZE_MS
        TrophyType.CONSISTENCY_SILVER -> CONSISTENCY_SILVER_MS
        TrophyType.CONSISTENCY_GOLD -> CONSISTENCY_GOLD_MS
        else -> null
    }

    /**
     * Lowest population standard deviation over any [CONSISTENCY_WINDOW] consecutive
     * laps, in recorded order. Population (divide by n) to match the Consistency insight
     * in `OfflineCoachingEngine`, so the two never disagree about the same laps.
     */
    fun steadiestWindowStdDevMs(lapDurationsMs: List<Long>): Double? {
        if (lapDurationsMs.size < CONSISTENCY_WINDOW) return null
        return lapDurationsMs.windowed(CONSISTENCY_WINDOW).minOf { populationStdDev(it) }
    }

    fun populationStdDev(values: List<Long>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}
