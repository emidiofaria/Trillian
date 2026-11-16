package com.drivingcoach.progression

/** Where a level sits in the ladder's three bands (SRS PG-01). */
enum class Tier { BEGINNER, MID, PRO }

/**
 * One rung. A driver holds it when lifetime distance reaches [minKm] **and** they own at
 * least [trophyCount] of [trophy] — and every rung below is held too.
 */
data class LevelDef(
    val number: Int,
    val tier: Tier,
    val minKm: Double,
    val trophy: TrophyType?,
    val trophyCount: Int
)

/** Cumulative counts of each trophy type the driver owns. */
data class TrophyCounts(val counts: Map<TrophyType, Int> = emptyMap()) {
    operator fun get(type: TrophyType): Int = counts[type] ?: 0

    companion object {
        fun of(types: Iterable<TrophyType>): TrophyCounts =
            TrophyCounts(types.groupingBy { it }.eachCount())
    }
}

/** One line of the next-level checklist (SRS PG-15). */
sealed class Requirement {
    abstract val met: Boolean

    data class Distance(val targetKm: Double, val currentKm: Double) : Requirement() {
        override val met: Boolean get() = currentKm >= targetKm
    }

    data class Trophies(val type: TrophyType, val target: Int, val current: Int) : Requirement() {
        override val met: Boolean get() = current >= target
    }
}

data class LevelProgress(
    val current: LevelDef,
    /** Null at the top of the ladder. */
    val next: LevelDef?,
    val lifetimeKm: Double,
    /** What the next level needs, in display order; empty at the top. */
    val requirements: List<Requirement>,
    /**
     * Share of the distance between this level's and the next level's thresholds already
     * driven, 0..1; 1 at the top. Distance only: trophy requirements are binary and are
     * shown on the checklist, not smeared into a bar.
     */
    val kmFraction: Double
) {
    val isMaxLevel: Boolean get() = next == null
}

/**
 * The six-level ladder (SRS PG-01, PG-02). The level is never stored: it is recomputed
 * from lifetime distance and trophy counts, so it can never disagree with them.
 */
object LevelLadder {

    val levels: List<LevelDef> = listOf(
        LevelDef(1, Tier.BEGINNER, 0.0, null, 0),
        LevelDef(2, Tier.BEGINNER, 25.0, TrophyType.FINISHER_BRONZE, 1),
        LevelDef(3, Tier.MID, 50.0, TrophyType.FINISHER_BRONZE, 3),
        LevelDef(4, Tier.MID, 100.0, TrophyType.PB_SILVER, 1),
        LevelDef(5, Tier.PRO, 200.0, TrophyType.PB_SILVER, 3),
        LevelDef(6, Tier.PRO, 350.0, TrophyType.TRACK_MASTER_GOLD, 1)
    )

    val maxLevel: Int get() = levels.last().number

    fun isMet(level: LevelDef, lifetimeKm: Double, counts: TrophyCounts): Boolean =
        requirementsOf(level, lifetimeKm, counts).all { it.met }

    fun requirementsOf(level: LevelDef, lifetimeKm: Double, counts: TrophyCounts): List<Requirement> {
        val list = ArrayList<Requirement>(2)
        if (level.minKm > 0.0) list += Requirement.Distance(level.minKm, lifetimeKm)
        level.trophy?.let { list += Requirement.Trophies(it, level.trophyCount, counts[it]) }
        return list
    }

    /** Highest level whose own and every lower level's conditions hold. */
    fun levelFor(lifetimeKm: Double, counts: TrophyCounts): LevelDef {
        var reached = levels.first()
        for (level in levels.drop(1)) {
            if (!isMet(level, lifetimeKm, counts)) break
            reached = level
        }
        return reached
    }

    fun progress(lifetimeKm: Double, counts: TrophyCounts): LevelProgress {
        val current = levelFor(lifetimeKm, counts)
        val next = levels.firstOrNull { it.number == current.number + 1 }
            ?: return LevelProgress(current, null, lifetimeKm, emptyList(), 1.0)

        val span = next.minKm - current.minKm
        val fraction = if (span <= 0.0) 1.0 else ((lifetimeKm - current.minKm) / span).coerceIn(0.0, 1.0)
        return LevelProgress(current, next, lifetimeKm, requirementsOf(next, lifetimeKm, counts), fraction)
    }
}
