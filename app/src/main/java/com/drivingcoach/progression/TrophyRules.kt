package com.drivingcoach.progression

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Everything a session can win (SRS PG-05 … PG-09, PG-24 … PG-30).
 *
 * The first eight are **trophies**: they carry a metal and count towards levels. The rest
 * are **session medals**: they are stored and shown, but never unlock a level. Both live in
 * the `trophies` table, keyed by [name]; what a row *is* is derived here, in code, so no
 * schema change was needed when medals were introduced (v3.08).
 */
enum class TrophyType(val metal: Metal?, val category: Category) {
    FINISHER(Metal.BRONZE, Category.TROPHY),
    PACE_SETTER(Metal.BRONZE, Category.TROPHY),
    STEADY_HAND(Metal.BRONZE, Category.TROPHY),
    METRONOME(Metal.SILVER, Category.TROPHY),
    PLATEAU_BREAKER(Metal.SILVER, Category.TROPHY),
    APEX_EXIT(Metal.SILVER, Category.TROPHY),
    SURGICAL(Metal.GOLD, Category.TROPHY),
    OPTIMAL_LAP(Metal.GOLD, Category.TROPHY),

    PURPLE_SECTOR(null, Category.MEDAL),
    CARBON_COPY(null, Category.MEDAL),
    TOP_GUN(null, Category.MEDAL),
    CLEAN_AIR(null, Category.MEDAL),
    LATE_CHARGER(null, Category.MEDAL),
    FULL_STINT(null, Category.MEDAL);

    /** Declared lowest first: a higher [ordinal] is a higher tier (PG-02 upward compatibility). */
    enum class Metal { BRONZE, SILVER, GOLD }

    enum class Category { TROPHY, MEDAL }

    val countsTowardLevels: Boolean get() = category == Category.TROPHY
    val isMedal: Boolean get() = category == Category.MEDAL

    companion object {
        val trophies: List<TrophyType> = values().filter { it.category == Category.TROPHY }
        val medals: List<TrophyType> = values().filter { it.category == Category.MEDAL }

        /** Null for a name this build does not know, such as a pre-3.08 type before the recompute. */
        fun fromName(name: String): TrophyType? = values().firstOrNull { it.name == name }
    }
}

/** One lap as the rules see it. Sectors are 0 when unknown; [topSpeedKmh] is null without telemetry. */
data class LapFacts(
    val durationMs: Long,
    val sector1Ms: Long = 0L,
    val sector2Ms: Long = 0L,
    val sector3Ms: Long = 0L,
    val topSpeedKmh: Double? = null
) {
    /** All three sectors present and summing to the lap, as `SectorSplitter` writes them. */
    val hasSectors: Boolean
        get() = sector1Ms > 0L && sector2Ms > 0L && sector3Ms > 0L &&
            sector1Ms + sector2Ms + sector3Ms == durationMs
}

/** What one session contributes to the rules, with nothing Android about it. */
data class SessionFacts(
    val sessionId: Long,
    val startedAt: Long,
    val trackKey: String,
    val trackName: String,
    val distanceM: Double,
    /** Every recorded lap, in recorded order. */
    val laps: List<LapFacts>,
    /** False when the lap detector reported a possible merged lap (SRS LD-23). */
    val lapBoundariesTrusted: Boolean = true
) {
    val lapCount: Int get() = laps.size
    val lapDurationsMs: List<Long> get() = laps.map { it.durationMs }

    /** Median of the positive lap times; 0 when there are none. */
    val medianMs: Double by lazy {
        val positive = laps.map { it.durationMs }.filter { it > 0L }
        if (positive.isEmpty()) 0.0 else median(positive)
    }

    /** Per lap, in recorded order: inside 85 %–115 % of the session median (PG-24). */
    val validFlags: List<Boolean> by lazy { laps.map { ValidLaps.isValid(it.durationMs, medianMs) } }

    val validLaps: List<LapFacts> by lazy { laps.filterIndexed { i, _ -> validFlags[i] } }
    val validLapCount: Int get() = validLaps.size

    /** The fastest valid lap. A half lap from a split boundary is quick and invalid. */
    val credibleBestMs: Long? get() = validLaps.minOfOrNull { it.durationMs }

    /** Highest valid-lap top speed; null without telemetry (PG-27, PG-29). */
    val topSpeedKmh: Double? get() = validLaps.mapNotNull { it.topSpeedKmh }.maxOrNull()

    /** Lowest coefficient of variation over 5 consecutive valid laps, in percent (PG-25). */
    val bestCvPercent: Double? by lazy { SessionMetrics.bestCvPercent(this) }

    /** Fastest S1, S2, S3 over valid laps with sectors; null entries where none. */
    val bestSectorsMs: List<Long?>
        get() {
            val withSectors = validLaps.filter { it.hasSectors }
            return listOf(
                withSectors.minOfOrNull { it.sector1Ms },
                withSectors.minOfOrNull { it.sector2Ms },
                withSectors.minOfOrNull { it.sector3Ms }
            )
        }

    companion object {
        fun median(values: List<Long>): Double {
            val sorted = values.sorted()
            return if (sorted.size % 2 == 0) (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
            else sorted[sorted.size / 2].toDouble()
        }

        /** Laps with durations only, for callers and tests that have nothing else. */
        fun ofLapTimes(
            sessionId: Long,
            startedAt: Long,
            trackKey: String,
            trackName: String,
            distanceM: Double,
            lapDurationsMs: List<Long>,
            lapBoundariesTrusted: Boolean = true
        ) = SessionFacts(
            sessionId, startedAt, trackKey, trackName, distanceM,
            lapDurationsMs.map { LapFacts(it) }, lapBoundariesTrusted
        )
    }
}

/**
 * The valid-lap band (PG-24). Compared as `lap × 100` against `percent × median`, which is
 * exact for whole-millisecond laps, so 85.000 % and 115.000 % are reliably inside.
 */
object ValidLaps {
    const val MIN_PERCENT = 85.0
    const val MAX_PERCENT = 115.0

    fun isValid(durationMs: Long, medianMs: Double): Boolean {
        if (durationMs <= 0L || medianMs <= 0.0) return false
        val scaled = durationMs * 100.0
        return scaled >= MIN_PERCENT * medianMs && scaled <= MAX_PERCENT * medianMs
    }
}

data class AwardedTrophy(
    val type: TrophyType,
    /**
     * Evidence, by type: metres (Finisher, Full Stint); lap ms (Pace Setter, Plateau Breaker,
     * Optimal Lap); CV in hundredths of a percent (consistency); top speed in tenths of km/h
     * (Apex Exit, Top Gun); a 1/2/4 bitmask of improved sectors (Purple Sector); gap in ms
     * (Carbon Copy); run length in laps (Clean Air); lap number (Late Charger).
     */
    val value: Long
)

object TrackKey {
    /** Catalogue id when the session had one, else the name, normalised (SRS PG-06). */
    fun of(trackId: String?, trackName: String): String {
        val id = trackId?.trim()?.lowercase().orEmpty()
        return if (id.isNotEmpty()) "id:$id" else "name:${trackName.trim().lowercase()}"
    }
}

/** Measurements shared by the rules and the personal hints, so the two never disagree. */
object SessionMetrics {

    /** Indices of every run of [length] consecutive laps that are all valid. */
    fun validWindows(session: SessionFacts, length: Int): List<IntRange> {
        val flags = session.validFlags
        if (flags.size < length) return emptyList()
        return (0..flags.size - length)
            .filter { start -> (start until start + length).all { flags[it] } }
            .map { it until it + length }
    }

    fun cvPercent(durations: List<Long>): Double {
        val mean = durations.average()
        if (mean <= 0.0) return Double.MAX_VALUE
        return populationStdDev(durations) / mean * 100.0
    }

    fun bestCvPercent(session: SessionFacts): Double? =
        validWindows(session, TrophyRules.WINDOW)
            .minOfOrNull { range -> cvPercent(range.map { session.laps[it].durationMs }) }

    /**
     * The tightest Apex Exit attempt: over each 5-valid-lap window, the worst lap's shortfall
     * from the session top speed, in percent; the best window wins. Null without speeds.
     */
    fun bestApexSpreadPercent(session: SessionFacts): Double? {
        val top = session.topSpeedKmh ?: return null
        if (top <= 0.0) return null
        return validWindows(session, TrophyRules.WINDOW).mapNotNull { range ->
            val speeds = range.map { session.laps[it].topSpeedKmh }
            if (speeds.any { it == null }) null else speeds.maxOf { (top - it!!) / top * 100.0 }
        }.minOrNull()
    }

    fun populationStdDev(values: List<Long>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    /** Sessions on the same track that started before [session], trusted, oldest first. */
    fun earlierOnTrack(session: SessionFacts, history: List<SessionFacts>): List<SessionFacts> =
        history.filter {
            it.sessionId != session.sessionId && it.trackKey == session.trackKey &&
                it.lapBoundariesTrusted && TrophyRules.isEarlier(it, session)
        }.sortedWith(compareBy({ it.startedAt }, { it.sessionId }))

    /**
     * How many valid laps the track PB has stood for, up to the end of [earlier]: valid laps
     * driven after the first lap that set it. Null when there is no PB yet.
     */
    fun lapsSincePb(earlier: List<SessionFacts>): Int? {
        val pb = earlier.mapNotNull { it.credibleBestMs }.minOrNull() ?: return null
        var counting = false
        var count = 0
        for (s in earlier) {
            s.laps.forEachIndexed { i, lap ->
                if (!s.validFlags[i]) return@forEachIndexed
                if (counting) count++ else if (lap.durationMs == pb) counting = true
            }
        }
        return count
    }

    /** Theoretical best lap: the fastest S1 + S2 + S3 over valid laps with sectors (PG-26). */
    fun theoreticalBestMs(sessions: List<SessionFacts>): Long? {
        val bests = (0..2).map { idx -> sessions.mapNotNull { it.bestSectorsMs[idx] }.minOrNull() }
        if (bests.any { it == null }) return null
        return bests.sumOf { it!! }
    }
}

/**
 * The per-session rules. Pure and deterministic: a session's awards are a function of its own
 * laps and of the laps of earlier sessions on the same track only, which is what makes the
 * recompute (PG-13) give the same answer as awarding at the time would have.
 */
object TrophyRules {

    const val WINDOW = 5

    const val FINISHER_MIN_DISTANCE_M = 5_000.0
    const val FINISHER_MIN_VALID_LAPS = 5

    const val STEADY_HAND_CV = 3.0
    const val METRONOME_CV = 1.5
    const val SURGICAL_CV = 0.75

    const val PLATEAU_MIN_LAPS = 15
    const val APEX_MAX_SPREAD_PERCENT = 1.0
    const val OPTIMAL_MIN_LIFETIME_VALID_LAPS = 20
    /** 0.5 %, as parts per thousand so the comparison stays in whole milliseconds. */
    const val OPTIMAL_MAX_GAP_PER_MILLE = 5L

    /** 0.75 %, in hundredths of a percent. */
    const val CARBON_COPY_MAX_GAP_BP = 75L
    const val CLEAN_AIR_MAX_PERCENT = 5.0
    const val CLEAN_AIR_MIN_RUN = 5
    const val TOP_GUN_TOLERANCE_KMH = 0.5
    const val LATE_CHARGER_MIN_VALID_LAPS = 5
    const val LATE_CHARGER_LAST_LAPS = 3
    const val FULL_STINT_MIN_DISTANCE_M = 8_000.0

    fun evaluate(session: SessionFacts, history: List<SessionFacts>): List<AwardedTrophy> {
        val awarded = ArrayList<AwardedTrophy>()

        if (session.distanceM >= FINISHER_MIN_DISTANCE_M && session.validLapCount >= FINISHER_MIN_VALID_LAPS) {
            awarded += AwardedTrophy(TrophyType.FINISHER, session.distanceM.toLong())
        }

        // PG-10: lap boundaries that may be wrong cannot back anything lap-based.
        if (!session.lapBoundariesTrusted) return awarded

        val earlier = SessionMetrics.earlierOnTrack(session, history)

        // Pace Setter and Plateau Breaker.
        val best = session.credibleBestMs
        val previousBest = earlier.mapNotNull { it.credibleBestMs }.minOrNull()
        if (best != null && previousBest != null && best < previousBest) {
            awarded += AwardedTrophy(TrophyType.PACE_SETTER, best)
            val stood = lapsPbStood(session, earlier, previousBest)
            if (stood >= PLATEAU_MIN_LAPS) awarded += AwardedTrophy(TrophyType.PLATEAU_BREAKER, best)
        }

        // Consistency family: highest tier only.
        session.bestCvPercent?.let { cv ->
            consistencyTierFor(cv)?.let { awarded += AwardedTrophy(it, Math.round(cv * 100.0)) }
        }

        apexExit(session)?.let { awarded += it }
        optimalLap(session, earlier)?.let { awarded += it }

        // Medals.
        purpleSector(session, earlier)?.let { awarded += it }
        carbonCopy(session)?.let { awarded += it }
        topGun(session, earlier)?.let { awarded += it }
        cleanAir(session)?.let { awarded += it }
        lateCharger(session)?.let { awarded += it }
        if (session.distanceM >= FULL_STINT_MIN_DISTANCE_M) {
            awarded += AwardedTrophy(TrophyType.FULL_STINT, session.distanceM.toLong())
        }

        return awarded.sortedBy { it.type.ordinal }
    }

    /**
     * The whole career under the current rules, oldest session first (SRS PG-13). Each
     * session sees only the sessions before it, so the answer does not depend on the order
     * of [sessions] and running it twice gives the same rows: the recompute is idempotent.
     */
    fun recompute(sessions: List<SessionFacts>): List<Pair<Long, List<AwardedTrophy>>> =
        sessions.sortedWith(compareBy({ it.startedAt }, { it.sessionId }))
            .map { it.sessionId to evaluate(it, sessions) }

    fun isEarlier(a: SessionFacts, b: SessionFacts): Boolean =
        a.startedAt < b.startedAt || (a.startedAt == b.startedAt && a.sessionId < b.sessionId)

    /** Highest tier only: a session that qualifies for Surgical does not also take Metronome. */
    fun consistencyTierFor(cvPercent: Double): TrophyType? = when {
        cvPercent < SURGICAL_CV -> TrophyType.SURGICAL
        cvPercent < METRONOME_CV -> TrophyType.METRONOME
        cvPercent < STEADY_HAND_CV -> TrophyType.STEADY_HAND
        else -> null
    }

    fun cvThresholdFor(type: TrophyType): Double? = when (type) {
        TrophyType.STEADY_HAND -> STEADY_HAND_CV
        TrophyType.METRONOME -> METRONOME_CV
        TrophyType.SURGICAL -> SURGICAL_CV
        else -> null
    }

    /**
     * Valid laps the old PB stood for: after its first lap in earlier sessions, plus this
     * session's valid laps before the first one to beat it (PG-26).
     */
    fun lapsPbStood(session: SessionFacts, earlier: List<SessionFacts>, previousBestMs: Long): Int {
        val before = SessionMetrics.lapsSincePb(earlier) ?: 0
        var inSession = 0
        for (i in session.laps.indices) {
            if (!session.validFlags[i]) continue
            if (session.laps[i].durationMs < previousBestMs) break
            inSession++
        }
        return before + inSession
    }

    private fun apexExit(session: SessionFacts): AwardedTrophy? {
        val top = session.topSpeedKmh ?: return null
        val hit = SessionMetrics.validWindows(session, WINDOW).any { range ->
            range.all { i ->
                val speed = session.laps[i].topSpeedKmh
                speed != null && (top - speed) * 100.0 <= APEX_MAX_SPREAD_PERCENT * top
            }
        }
        return if (hit) AwardedTrophy(TrophyType.APEX_EXIT, Math.round(top * 10.0)) else null
    }

    private fun optimalLap(session: SessionFacts, earlier: List<SessionFacts>): AwardedTrophy? {
        val lifetimeValid = earlier.sumOf { it.validLapCount } + session.validLapCount
        if (lifetimeValid < OPTIMAL_MIN_LIFETIME_VALID_LAPS) return null
        val tbl = SessionMetrics.theoreticalBestMs(earlier + session) ?: return null
        val lap = session.validLaps.map { it.durationMs }
            .filter { (it - tbl) * 1000L <= OPTIMAL_MAX_GAP_PER_MILLE * tbl }
            .minOrNull() ?: return null
        return AwardedTrophy(TrophyType.OPTIMAL_LAP, lap)
    }

    private fun purpleSector(session: SessionFacts, earlier: List<SessionFacts>): AwardedTrophy? {
        val mine = session.bestSectorsMs
        var mask = 0L
        for (idx in 0..2) {
            val previous = earlier.mapNotNull { it.bestSectorsMs[idx] }.minOrNull() ?: continue
            val current = mine[idx] ?: continue
            if (current < previous) mask = mask or (1L shl idx)
        }
        return if (mask != 0L) AwardedTrophy(TrophyType.PURPLE_SECTOR, mask) else null
    }

    private fun carbonCopy(session: SessionFacts): AwardedTrophy? {
        val gaps = (0 until session.laps.size - 1).mapNotNull { i ->
            if (!session.validFlags[i] || !session.validFlags[i + 1]) return@mapNotNull null
            val a = session.laps[i].durationMs
            val b = session.laps[i + 1].durationMs
            val gap = abs(a - b)
            if (gap * 10_000L <= CARBON_COPY_MAX_GAP_BP * minOf(a, b)) gap else null
        }
        return gaps.minOrNull()?.let { AwardedTrophy(TrophyType.CARBON_COPY, it) }
    }

    private fun topGun(session: SessionFacts, earlier: List<SessionFacts>): AwardedTrophy? {
        val top = session.topSpeedKmh ?: return null
        val record = earlier.mapNotNull { it.topSpeedKmh }.maxOrNull() ?: return null
        return if (top >= record - TOP_GUN_TOLERANCE_KMH) AwardedTrophy(TrophyType.TOP_GUN, Math.round(top * 10.0))
        else null
    }

    private fun cleanAir(session: SessionFacts): AwardedTrophy? {
        val median = session.medianMs
        if (median <= 0.0) return null
        var run = 0
        var longest = 0
        session.laps.forEach { lap ->
            val inside = lap.durationMs > 0L && abs(lap.durationMs - median) * 100.0 <= CLEAN_AIR_MAX_PERCENT * median
            run = if (inside) run + 1 else 0
            longest = maxOf(longest, run)
        }
        return if (longest >= CLEAN_AIR_MIN_RUN) AwardedTrophy(TrophyType.CLEAN_AIR, longest.toLong()) else null
    }

    /**
     * The session's fastest valid lap (its first occurrence) is one of the last three valid
     * laps. Valid laps rather than recorded laps, so the in-lap does not hide a late charge,
     * and at least five of them, so a three-lap session cannot win it by definition.
     */
    private fun lateCharger(session: SessionFacts): AwardedTrophy? {
        val valid = session.laps.indices.filter { session.validFlags[it] }
        if (valid.size < LATE_CHARGER_MIN_VALID_LAPS) return null
        val fastest = valid.minByOrNull { session.laps[it].durationMs }!!
        val position = valid.indexOf(fastest)
        return if (position >= valid.size - LATE_CHARGER_LAST_LAPS) {
            AwardedTrophy(TrophyType.LATE_CHARGER, (fastest + 1).toLong())
        } else null
    }
}
