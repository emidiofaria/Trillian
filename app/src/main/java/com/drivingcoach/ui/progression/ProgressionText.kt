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
            else -> R.string.progression_level_6
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
            TrophyType.FINISHER_BRONZE -> R.string.trophy_finisher_bronze
            TrophyType.PB_SILVER -> R.string.trophy_pb_silver
            TrophyType.TRACK_MASTER_GOLD -> R.string.trophy_track_master_gold
            TrophyType.CONSISTENCY_BRONZE -> R.string.trophy_consistency_bronze
            TrophyType.CONSISTENCY_SILVER -> R.string.trophy_consistency_silver
            TrophyType.CONSISTENCY_GOLD -> R.string.trophy_consistency_gold
        }
    )

    private fun metalName(metal: TrophyType.Metal): String = s(
        when (metal) {
            TrophyType.Metal.BRONZE -> R.string.trophy_metal_bronze
            TrophyType.Metal.SILVER -> R.string.trophy_metal_silver
            TrophyType.Metal.GOLD -> R.string.trophy_metal_gold
        }
    )

    /** "Personal Best (Silver)" for the level trophies, whose names do not say the metal. */
    fun trophyWithMetal(type: TrophyType): String =
        if (type.countsTowardLevels) s(R.string.trophy_requirement_name, trophyName(type), metalName(type.metal))
        else trophyName(type)

    fun howToWin(type: TrophyType): String = when (type) {
        TrophyType.FINISHER_BRONZE -> s(R.string.trophy_how_finisher)
        TrophyType.PB_SILVER -> s(R.string.trophy_how_pb)
        TrophyType.TRACK_MASTER_GOLD -> s(R.string.trophy_how_track_master)
        else -> s(R.string.trophy_how_consistency, ProgressionFormat.seconds(TrophyRules.thresholdMsFor(type)!!))
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
                trophyWithMetal(requirement.type), requirement.current,
                howToWin(requirement.type).decapitalised()
            )
        }
    }

    fun roadmapRequirements(level: LevelDef): String {
        val trophy = level.trophy ?: return s(R.string.progression_roadmap_start)
        return s(
            R.string.progression_roadmap_requirements,
            ProgressionFormat.thresholdKm(level.minKm), level.trophyCount, trophyWithMetal(trophy)
        )
    }

    fun nextGoal(goal: NextGoal): String = when (goal) {
        NextGoal.MaxLevel -> s(R.string.next_goal_max)
        is NextGoal.DriveKm -> s(
            R.string.next_goal_km, ProgressionFormat.remainingKm(goal.remainingKm),
            goal.nextLevel, levelName(goal.nextLevel)
        )
        is NextGoal.EarnTrophies -> s(
            R.string.next_goal_trophy, goal.remaining, trophyWithMetal(goal.type),
            goal.nextLevel, levelName(goal.nextLevel), howToWin(goal.type)
        )
    }

    fun hint(hint: TrophyHint?): String? = when (hint) {
        null -> null
        is TrophyHint.Finisher ->
            if (hint.bestDistanceKm == null || hint.bestLaps == null) s(R.string.trophy_hint_finisher_none)
            else s(R.string.trophy_hint_finisher, ProgressionFormat.drivenKm(hint.bestDistanceKm), hint.bestLaps)
        is TrophyHint.Consistency -> when {
            hint.steadiestMs == null -> s(R.string.trophy_hint_consistency_none)
            hint.steadiestMs < hint.thresholdMs ->
                s(R.string.trophy_hint_consistency_beyond, ProgressionFormat.seconds(hint.steadiestMs))
            else -> s(
                R.string.trophy_hint_consistency, ProgressionFormat.seconds(hint.steadiestMs),
                ProgressionFormat.seconds(hint.thresholdMs), trophyName(hint.type)
            )
        }
        is TrophyHint.PersonalBest ->
            if (hint.bestMs == null || hint.trackName == null) s(R.string.trophy_hint_pb_none)
            else s(R.string.trophy_hint_pb, hint.trackName, LapTimeFormatter.formatLapTime(hint.bestMs))
        is TrophyHint.TrackMaster -> s(R.string.trophy_hint_track_master, hint(hint.personalBest)!!)
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

    private fun String.decapitalised(): String = replaceFirstChar { it.lowercase() }.trimEnd('.')

    companion object {
        @ColorRes
        fun metalColor(type: TrophyType): Int = when (type.metal) {
            TrophyType.Metal.BRONZE -> R.color.colorBronze
            TrophyType.Metal.SILVER -> R.color.colorSilver
            TrophyType.Metal.GOLD -> R.color.colorGold
        }
    }
}
