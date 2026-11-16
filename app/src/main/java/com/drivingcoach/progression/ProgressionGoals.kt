package com.drivingcoach.progression

/** A trophy as persisted, reduced to what the career view needs. */
data class TrophyRecord(
    val sessionId: Long,
    val type: TrophyType,
    val trackKey: String,
    val value: Long,
    val awardedAt: Long
)

/** One tile of the trophy cabinet (SRS PG-17). */
data class CabinetEntry(
    val type: TrophyType,
    val count: Int,
    val lastAwardedAt: Long?,
    val lastTrackName: String?
) {
    val earned: Boolean get() = count > 0
}

/** The single most reachable step towards the next level (SRS PG-18). */
sealed class NextGoal {
    object MaxLevel : NextGoal()
    data class DriveKm(val nextLevel: Int, val remainingKm: Double) : NextGoal()
    data class EarnTrophies(val nextLevel: Int, val type: TrophyType, val remaining: Int) : NextGoal()
}

/**
 * How close the driver already is to a trophy, from their own history (SRS PG-16).
 * Each variant carries numbers only; wording lives in string resources.
 */
sealed class TrophyHint {
    /** Best attempt so far, or null fields when no session has been recorded. */
    data class Finisher(val bestDistanceKm: Double?, val bestLaps: Int?) : TrophyHint()

    /** Steadiest five-lap run so far, against [type]'s threshold. Null when never five laps. */
    data class Consistency(val type: TrophyType, val steadiestMs: Double?, val thresholdMs: Double) : TrophyHint()

    /** The driver's best at the track they drove most recently; null when no laps yet. */
    data class PersonalBest(val trackName: String?, val bestMs: Long?) : TrophyHint()

    data class TrackMaster(val personalBest: PersonalBest, val steadiestMs: Double?) : TrophyHint()
}

data class CareerSnapshot(
    val lifetimeKm: Double,
    val progress: LevelProgress,
    val counts: TrophyCounts,
    val cabinet: List<CabinetEntry>,
    val nextGoal: NextGoal,
    val hints: Map<TrophyType, TrophyHint>
)

/** How this session's best compares with the previous session on the same track (PG-20). */
sealed class RecentProgress {
    object NoLaps : RecentProgress()
    data class FirstTime(val trackName: String) : RecentProgress()
    data class Faster(val trackName: String, val gapMs: Long) : RecentProgress()
    data class Slower(val trackName: String, val gapMs: Long) : RecentProgress()
    data class Equal(val trackName: String) : RecentProgress()
}

object ProgressionGoals {

    fun career(sessions: List<SessionFacts>, trophies: List<TrophyRecord>): CareerSnapshot {
        val lifetimeKm = sessions.sumOf { it.distanceM } / 1000.0
        val counts = TrophyCounts.of(trophies.filter { it.type.countsTowardLevels }.map { it.type })
        val progress = LevelLadder.progress(lifetimeKm, counts)
        return CareerSnapshot(
            lifetimeKm = lifetimeKm,
            progress = progress,
            counts = TrophyCounts.of(trophies.map { it.type }),
            cabinet = cabinet(sessions, trophies),
            nextGoal = nextGoal(progress),
            hints = hints(sessions)
        )
    }

    /**
     * Distance first when it is missing, otherwise the trophy: a driver who already has the
     * kilometres is told which trophy is missing, and one who has not is told how far is
     * left, because every session adds distance while a trophy needs a particular one.
     */
    fun nextGoal(progress: LevelProgress): NextGoal {
        val next = progress.next ?: return NextGoal.MaxLevel
        val distance = progress.requirements.filterIsInstance<Requirement.Distance>().firstOrNull()
        val trophy = progress.requirements.filterIsInstance<Requirement.Trophies>().firstOrNull()
        return when {
            distance != null && !distance.met ->
                NextGoal.DriveKm(next.number, distance.targetKm - distance.currentKm)
            trophy != null && !trophy.met ->
                NextGoal.EarnTrophies(next.number, trophy.type, trophy.target - trophy.current)
            // Unreachable while levelFor is consistent with requirementsOf; kept total.
            else -> NextGoal.DriveKm(next.number, 0.0)
        }
    }

    fun cabinet(sessions: List<SessionFacts>, trophies: List<TrophyRecord>): List<CabinetEntry> {
        val namesById = sessions.associate { it.sessionId to it.trackName }
        return TrophyType.values().map { type ->
            val ofType = trophies.filter { it.type == type }
            val last = ofType.maxWithOrNull(compareBy<TrophyRecord>({ it.awardedAt }, { it.sessionId }))
            CabinetEntry(type, ofType.size, last?.awardedAt, last?.let { namesById[it.sessionId] })
        }
    }

    fun hints(sessions: List<SessionFacts>): Map<TrophyType, TrophyHint> {
        val ordered = sessions.sortedWith(compareBy({ it.startedAt }, { it.sessionId }))

        val bestAttempt = ordered.maxWithOrNull(compareBy({ finisherScore(it) }, { it.startedAt }, { it.sessionId }))
        val finisher = TrophyHint.Finisher(bestAttempt?.distanceM?.div(1000.0), bestAttempt?.lapCount)

        val steadiest = ordered.filter { it.lapBoundariesTrusted }
            .mapNotNull { TrophyRules.steadiestWindowStdDevMs(it.lapDurationsMs) }
            .minOrNull()

        val pb = personalBestHint(ordered)

        return mapOf(
            TrophyType.FINISHER_BRONZE to finisher,
            TrophyType.PB_SILVER to pb,
            TrophyType.TRACK_MASTER_GOLD to TrophyHint.TrackMaster(pb, steadiest),
            TrophyType.CONSISTENCY_BRONZE to
                TrophyHint.Consistency(TrophyType.CONSISTENCY_BRONZE, steadiest, TrophyRules.CONSISTENCY_BRONZE_MS),
            TrophyType.CONSISTENCY_SILVER to
                TrophyHint.Consistency(TrophyType.CONSISTENCY_SILVER, steadiest, TrophyRules.CONSISTENCY_SILVER_MS),
            TrophyType.CONSISTENCY_GOLD to
                TrophyHint.Consistency(TrophyType.CONSISTENCY_GOLD, steadiest, TrophyRules.CONSISTENCY_GOLD_MS)
        )
    }

    /** Each condition counts up to half; a session meeting both scores 2. */
    private fun finisherScore(session: SessionFacts): Double =
        (session.distanceM / TrophyRules.FINISHER_MIN_DISTANCE_M).coerceAtMost(1.0) +
            (session.lapCount.toDouble() / TrophyRules.FINISHER_MIN_LAPS).coerceAtMost(1.0)

    /** The best credible lap at the most recently driven track that has one. */
    private fun personalBestHint(ordered: List<SessionFacts>): TrophyHint.PersonalBest {
        val latest = ordered.lastOrNull { it.credibleBestMs != null }
            ?: return TrophyHint.PersonalBest(null, null)
        val best = ordered.filter { it.trackKey == latest.trackKey }.mapNotNull { it.credibleBestMs }.minOrNull()
        return TrophyHint.PersonalBest(latest.trackName, best)
    }

    /** Compares with the latest earlier session on the same track that has a credible best. */
    fun recentProgress(session: SessionFacts, history: List<SessionFacts>): RecentProgress {
        val best = session.credibleBestMs ?: return RecentProgress.NoLaps
        val previous = history
            .filter { it.trackKey == session.trackKey && TrophyRules.isEarlier(it, session) }
            .filter { it.credibleBestMs != null }
            .maxWithOrNull(compareBy({ it.startedAt }, { it.sessionId }))
            ?: return RecentProgress.FirstTime(session.trackName)
        val gap = previous.credibleBestMs!! - best
        return when {
            gap > 0 -> RecentProgress.Faster(session.trackName, gap)
            gap < 0 -> RecentProgress.Slower(session.trackName, -gap)
            else -> RecentProgress.Equal(session.trackName)
        }
    }
}
