package com.drivingcoach.ui.progression

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.drivingcoach.R
import com.drivingcoach.progression.LevelDef
import com.drivingcoach.progression.NextGoal
import com.drivingcoach.progression.ProgressionFormat
import com.drivingcoach.progression.RecentProgress
import com.drivingcoach.progression.Requirement
import com.drivingcoach.progression.Tier
import com.drivingcoach.progression.TrophyHint
import com.drivingcoach.progression.TrophyRules
import com.drivingcoach.progression.TrophyType
import com.drivingcoach.util.LapTimeFormatter

/** Turns progression values into the words shown on screen (SRS PG-23). */
class ProgressionText(private val context: Context) {

    private fun s(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    fun levelName(number: Int): String = s(
        when (number) {
            1 -> R.string.progression_level_1
            2 -> R.string.progression_level_2
            3 -> R.string.progression_level_3
            4 -> R.string.progression_level_4
            5 -> R.string.progression_level_5
            6 -> R.string.progression_level_6
            else -> R.string.progression_level_7
        }
    )

    fun tierName(tier: Tier): String = s(
        when (tier) {
            Tier.BEGINNER -> R.string.progression_tier_beginner
            Tier.MID -> R.string.progression_tier_mid
            Tier.PRO -> R.string.progression_tier_pro
        }
    )

    fun levelTitle(level: LevelDef): String = s(R.string.progression_level_title, level.number, levelName(level.number))

    fun trophyName(type: TrophyType): String = s(
        when (type) {
            TrophyType.FINISHER -> R.string.trophy_finisher
            TrophyType.PACE_SETTER -> R.string.trophy_pace_setter
            TrophyType.STEADY_HAND -> R.string.trophy_steady_hand
            TrophyType.METRONOME -> R.string.trophy_metronome
            TrophyType.PLATEAU_BREAKER -> R.string.trophy_plateau_breaker
            TrophyType.APEX_EXIT -> R.string.trophy_apex_exit
            TrophyType.SURGICAL -> R.string.trophy_surgical
            TrophyType.OPTIMAL_LAP -> R.string.trophy_optimal_lap
            TrophyType.PURPLE_SECTOR -> R.string.medal_purple_sector
            TrophyType.CARBON_COPY -> R.string.medal_carbon_copy
            TrophyType.TOP_GUN -> R.string.medal_top_gun
            TrophyType.CLEAN_AIR -> R.string.medal_clean_air
            TrophyType.LATE_CHARGER -> R.string.medal_late_charger
            TrophyType.FULL_STINT -> R.string.medal_full_stint
        }
    )

    fun metalName(metal: TrophyType.Metal): String = s(
        when (metal) {
            TrophyType.Metal.BRONZE -> R.string.trophy_metal_bronze
            TrophyType.Metal.SILVER -> R.string.trophy_metal_silver
            TrophyType.Metal.GOLD -> R.string.trophy_metal_gold
        }
    )

    /** "Bronze trophy": what a level asks for, since any trophy of that metal or better counts. */
    fun metalTrophy(metal: TrophyType.Metal): String = s(R.string.trophy_metal_trophy, metalName(metal))

    /** "Pace Setter (Bronze)" for level trophies; a medal's name alone. */
    fun trophyWithMetal(type: TrophyType): String {
        val metal = type.metal ?: return trophyName(type)
        return s(R.string.trophy_requirement_name, trophyName(type), metalName(metal))
    }

    /** "The Finisher, Pace Setter, Steady Hand": the trophies of exactly that metal. */
    fun trophiesOfMetal(metal: TrophyType.Metal): String =
        TrophyType.trophies.filter { it.metal == metal }
            .joinToString(s(R.string.trophy_examples_separator)) { trophyName(it) }

    fun howToWin(type: TrophyType): String = when (type) {
        TrophyType.FINISHER -> s(
            R.string.trophy_how_finisher,
            ProgressionFormat.thresholdKm(TrophyRules.FINISHER_MIN_DISTANCE_M / 1000.0), TrophyRules.FINISHER_MIN_VALID_LAPS
        )
        TrophyType.PACE_SETTER -> s(R.string.trophy_how_pace_setter)
        TrophyType.STEADY_HAND, TrophyType.METRONOME, TrophyType.SURGICAL ->
            s(R.string.trophy_how_consistency, threshold(TrophyRules.cvThresholdFor(type)!!))
        TrophyType.PLATEAU_BREAKER -> s(R.string.trophy_how_plateau_breaker, TrophyRules.PLATEAU_MIN_LAPS)
        TrophyType.APEX_EXIT -> s(R.string.trophy_how_apex_exit, threshold(TrophyRules.APEX_MAX_SPREAD_PERCENT))
        TrophyType.OPTIMAL_LAP -> s(
            R.string.trophy_how_optimal_lap,
            threshold(TrophyRules.OPTIMAL_MAX_GAP_PER_MILLE / 10.0), TrophyRules.OPTIMAL_MIN_LIFETIME_VALID_LAPS
        )
        TrophyType.PURPLE_SECTOR -> s(R.string.medal_how_purple_sector)
        TrophyType.CARBON_COPY -> s(R.string.medal_how_carbon_copy, threshold(TrophyRules.CARBON_COPY_MAX_GAP_BP / 100.0))
        TrophyType.TOP_GUN -> s(R.string.medal_how_top_gun, threshold(TrophyRules.TOP_GUN_TOLERANCE_KMH))
        TrophyType.CLEAN_AIR -> s(
            R.string.medal_how_clean_air, TrophyRules.CLEAN_AIR_MIN_RUN, threshold(TrophyRules.CLEAN_AIR_MAX_PERCENT)
        )
        TrophyType.LATE_CHARGER -> s(
            R.string.medal_how_late_charger, TrophyRules.LATE_CHARGER_LAST_LAPS, TrophyRules.LATE_CHARGER_MIN_VALID_LAPS
        )
        TrophyType.FULL_STINT -> s(
            R.string.medal_how_full_stint, ProgressionFormat.thresholdKm(TrophyRules.FULL_STINT_MIN_DISTANCE_M / 1000.0)
        )
    }

    /** "1.0", "0.75", "3.0": a rule threshold with no trailing noise but at least one decimal. */
    private fun threshold(value: Double): String {
        val two = String.format(java.util.Locale.US, "%.2f", value)
        return if (two.endsWith("0")) two.dropLast(1) else two
    }

    fun requirementLine(requirement: Requirement): String {
        val mark = s(if (requirement.met) R.string.progression_check_met else R.string.progression_check_unmet)
        return when (requirement) {
            is Requirement.Distance -> s(
                R.string.progression_req_km, mark,
                ProgressionFormat.thresholdKm(requirement.targetKm),
                ProgressionFormat.drivenKm(requirement.currentKm)
            )
            is Requirement.Trophies -> s(
                R.string.progression_req_trophy, mark, requirement.target,
                metalTrophy(requirement.metal), requirement.current, trophiesOfMetal(requirement.metal)
            )
        }
    }

    fun roadmapRequirements(level: LevelDef): String {
        if (level.minKm <= 0.0) return s(R.string.progression_roadmap_start)
        val metal = level.metal
            ?: return s(R.string.progression_roadmap_km_only, ProgressionFormat.thresholdKm(level.minKm))
        return s(
            R.string.progression_roadmap_requirements,
            ProgressionFormat.thresholdKm(level.minKm), level.trophyCount, metalTrophy(metal)
        )
    }

    fun nextGoal(goal: NextGoal): String = when (goal) {
        NextGoal.MaxLevel -> s(R.string.next_goal_max)
        is NextGoal.DriveKm -> s(
            R.string.next_goal_km, ProgressionFormat.remainingKm(goal.remainingKm),
            goal.nextLevel, levelName(goal.nextLevel)
        )
        is NextGoal.EarnTrophies -> s(
            R.string.next_goal_trophy, goal.remaining, metalTrophy(goal.metal),
            goal.nextLevel, levelName(goal.nextLevel), trophiesOfMetal(goal.metal)
        )
    }

    fun hint(hint: TrophyHint?): String? = when (hint) {
        null -> null
        is TrophyHint.Finisher ->
            if (hint.bestDistanceKm == null || hint.bestValidLaps == null) s(R.string.trophy_hint_finisher_none)
            else s(
                R.string.trophy_hint_finisher, ProgressionFormat.drivenKm(hint.bestDistanceKm), hint.bestValidLaps,
                ProgressionFormat.thresholdKm(TrophyRules.FINISHER_MIN_DISTANCE_M / 1000.0), TrophyRules.FINISHER_MIN_VALID_LAPS
            )
        is TrophyHint.Consistency -> when {
            hint.bestCvPercent == null -> s(R.string.trophy_hint_consistency_none)
            hint.bestCvPercent < hint.thresholdPercent ->
                s(R.string.trophy_hint_consistency_beyond, ProgressionFormat.percent(hint.bestCvPercent))
            else -> s(
                R.string.trophy_hint_consistency, ProgressionFormat.percent(hint.bestCvPercent),
                ProgressionFormat.percent(hint.thresholdPercent), trophyName(hint.type)
            )
        }
        is TrophyHint.PersonalBest ->
            if (hint.bestMs == null || hint.trackName == null) s(R.string.trophy_hint_pb_none)
            else s(R.string.trophy_hint_pb, hint.trackName, LapTimeFormatter.formatLapTime(hint.bestMs))
        is TrophyHint.Plateau ->
            if (hint.trackName == null || hint.lapsSincePb == null) s(R.string.trophy_hint_pb_none)
            else s(R.string.trophy_hint_plateau, hint.trackName, hint.lapsSincePb, hint.needed)
        is TrophyHint.Apex ->
            if (hint.bestSpreadPercent == null) s(R.string.trophy_hint_apex_none)
            else s(
                R.string.trophy_hint_apex, ProgressionFormat.percent(hint.bestSpreadPercent),
                ProgressionFormat.percent(hint.thresholdPercent)
            )
        is TrophyHint.Optimal -> when {
            hint.trackName == null -> s(R.string.trophy_hint_pb_none)
            hint.gapPercent == null ->
                s(R.string.trophy_hint_optimal_laps, hint.trackName, hint.validLaps, hint.neededLaps)
            else -> s(
                R.string.trophy_hint_optimal, hint.trackName, hint.validLaps, hint.neededLaps,
                ProgressionFormat.percent(hint.gapPercent), ProgressionFormat.percent(hint.thresholdPercent)
            )
        }
    }

    /** "2 h" style time on track for the Profile stats (SRS DP-11). */
    fun drivenTime(totalMs: Long): String = when (val t = ProgressionFormat.drivenTime(totalMs)) {
        is ProgressionFormat.DrivenTime.Minutes -> s(R.string.profile_driven_minutes, t.minutes)
        is ProgressionFormat.DrivenTime.Hours -> s(R.string.profile_driven_hours, t.hours)
    }

    fun recentProgress(progress: RecentProgress): String = when (progress) {
        RecentProgress.NoLaps -> s(R.string.rewards_progress_no_laps)
        is RecentProgress.FirstTime -> s(R.string.rewards_progress_first, progress.trackName)
        is RecentProgress.Faster -> s(R.string.rewards_progress_faster, ProgressionFormat.seconds(progress.gapMs), progress.trackName)
        is RecentProgress.Slower -> s(R.string.rewards_progress_slower, ProgressionFormat.seconds(progress.gapMs), progress.trackName)
        is RecentProgress.Equal -> s(R.string.rewards_progress_equal, progress.trackName)
    }

    fun kmLabel(lifetimeKm: Double, next: LevelDef?): String =
        if (next == null) s(R.string.progression_km_max, ProgressionFormat.drivenKm(lifetimeKm))
        else s(R.string.progression_km_progress, ProgressionFormat.drivenKm(lifetimeKm), ProgressionFormat.thresholdKm(next.minKm))

    fun levelContentDescription(level: LevelDef): String =
        s(R.string.progression_level_content_description, level.number, levelName(level.number), tierName(level.tier))

    companion object {
        @ColorRes
        fun metalColor(type: TrophyType): Int = when (type.metal) {
            TrophyType.Metal.BRONZE -> R.color.colorBronze
            TrophyType.Metal.SILVER -> R.color.colorSilver
            TrophyType.Metal.GOLD -> R.color.colorGold
            null -> R.color.colorMedalPurple
        }
    }
}
