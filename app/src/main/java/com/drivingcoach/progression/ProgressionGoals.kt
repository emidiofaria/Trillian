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
    /** [remaining] more trophies of [metal] or better (PG-02 upward compatibility). */
    data class EarnTrophies(val nextLevel: Int, val metal: TrophyType.Metal, val remaining: Int) : NextGoal()
}

/**
 * How close the driver already is to a trophy, from their own history (SRS PG-16).
 * Each variant carries numbers only; wording lives in string resources.
 */
sealed class TrophyHint {
    /** Best attempt so far, or null fields when no session has been recorded. */
    data class Finisher(val bestDistanceKm: Double?, val bestValidLaps: Int?) : TrophyHint()

    /** Lowest 5-lap CV so far, in percent, against [type]'s threshold. Null when never five valid laps. */
    data class Consistency(val type: TrophyType, val bestCvPercent: Double?, val thresholdPercent: Double) : TrophyHint()

    /** The driver's best at the track they drove most recently; null when no laps yet. */
    data class PersonalBest(val trackName: String?, val bestMs: Long?) : TrophyHint()

    /** Valid laps the PB at the latest track has stood for, against [needed]. */
    data class Plateau(val trackName: String?, val lapsSincePb: Int?, val needed: Int) : TrophyHint()

    /** Tightest five-lap top-speed spread so far, percent, against [thresholdPercent]. */
    data class Apex(val bestSpreadPercent: Double?, val thresholdPercent: Double) : TrophyHint()

    /**
     * At the latest track: lifetime valid laps against [neededLaps], and how far the best real
     * lap is above the theoretical best, in percent (null without sector data).
     */
    data class Optimal(
        val trackName: String?,
        val validLaps: Int,
        val neededLaps: Int,
        val gapPercent: Double?,
        val thresholdPercent: Double
    ) : TrophyHint()
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
                NextGoal.EarnTrophies(next.number, trophy.metal, trophy.target - trophy.current)
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
        val trusted = ordered.filter { it.lapBoundariesTrusted }

        val bestAttempt = ordered.maxWithOrNull(compareBy({ finisherScore(it) }, { it.startedAt }, { it.sessionId }))
        val finisher = TrophyHint.Finisher(bestAttempt?.distanceM?.div(1000.0), bestAttempt?.validLapCount)

        val bestCv = trusted.mapNotNull { it.bestCvPercent }.minOrNull()
        val bestSpread = trusted.mapNotNull { SessionMetrics.bestApexSpreadPercent(it) }.minOrNull()

        val pb = personalBestHint(trusted)
        val latestTrack = trusted.lastOrNull { it.credibleBestMs != null }
        val onLatest = latestTrack?.let { l -> trusted.filter { it.trackKey == l.trackKey } }.orEmpty()

        val plateau = TrophyHint.Plateau(
            latestTrack?.trackName,
            if (latestTrack == null) null else SessionMetrics.lapsSincePb(onLatest),
            TrophyRules.PLATEAU_MIN_LAPS
        )

        val tbl = SessionMetrics.theoreticalBestMs(onLatest)
        val bestReal = onLatest.mapNotNull { it.credibleBestMs }.minOrNull()
        val optimal = TrophyHint.Optimal(
            trackName = latestTrack?.trackName,
            validLaps = onLatest.sumOf { it.validLapCount },
            neededLaps = TrophyRules.OPTIMAL_MIN_LIFETIME_VALID_LAPS,
            gapPercent = if (tbl != null && tbl > 0 && bestReal != null) (bestReal - tbl) * 100.0 / tbl else null,
            thresholdPercent = TrophyRules.OPTIMAL_MAX_GAP_PER_MILLE / 10.0
        )

        return mapOf(
            TrophyType.FINISHER to finisher,
            TrophyType.PACE_SETTER to pb,
            TrophyType.STEADY_HAND to TrophyHint.Consistency(TrophyType.STEADY_HAND, bestCv, TrophyRules.STEADY_HAND_CV),
            TrophyType.METRONOME to TrophyHint.Consistency(TrophyType.METRONOME, bestCv, TrophyRules.METRONOME_CV),
            TrophyType.SURGICAL to TrophyHint.Consistency(TrophyType.SURGICAL, bestCv, TrophyRules.SURGICAL_CV),
            TrophyType.PLATEAU_BREAKER to plateau,
            TrophyType.APEX_EXIT to TrophyHint.Apex(bestSpread, TrophyRules.APEX_MAX_SPREAD_PERCENT),
            TrophyType.OPTIMAL_LAP to optimal
        )
    }

    /** Each condition counts up to half; a session meeting both scores 2. */
    private fun finisherScore(session: SessionFacts): Double =
        (session.distanceM / TrophyRules.FINISHER_MIN_DISTANCE_M).coerceAtMost(1.0) +
            (session.validLapCount.toDouble() / TrophyRules.FINISHER_MIN_VALID_LAPS).coerceAtMost(1.0)

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
