package com.drivingcoach.ui.progression

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.drivingcoach.R
import com.drivingcoach.databinding.ItemLevelRequirementBinding
import com.drivingcoach.databinding.ItemLevelRoadmapBinding
import com.drivingcoach.databinding.ItemTrophyTileBinding
import com.drivingcoach.databinding.ViewLevelCardBinding
import com.drivingcoach.progression.CabinetEntry
import com.drivingcoach.progression.CareerSnapshot
import com.drivingcoach.progression.LevelLadder
import com.drivingcoach.progression.LevelProgress
import com.drivingcoach.progression.TrophyHint
import com.drivingcoach.progression.ValidLaps
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Binds the career views shared by Home, Profile and Rewards (SRS PG-15 … PG-18). */
object CareerViews {

    fun bindLevelCard(card: ViewLevelCardBinding, progress: LevelProgress, showChecklist: Boolean, animate: Boolean = false) {
        val text = ProgressionText(card.root.context)
        val level = progress.current
        card.levelBadge.text = card.root.context.getString(R.string.progression_level_badge, level.number)
        card.levelName.text = text.levelTitle(level)
        card.levelTier.text = card.root.context.getString(R.string.progression_tier_label, text.tierName(level.tier))
        card.levelHeader.contentDescription = text.levelContentDescription(level)
        card.levelKmProgress.setProgressCompat(progressValue(progress.kmFraction), animate)
        card.levelKmLabel.text = text.kmLabel(progress.lifetimeKm, progress.next)

        val next = progress.next
        val checklist = showChecklist && next != null
        card.nextLevelHeader.visibility = if (showChecklist) View.VISIBLE else View.GONE
        card.levelRequirements.visibility = if (checklist) View.VISIBLE else View.GONE
        card.nextLevelHeader.text = if (next != null) {
            card.root.context.getString(R.string.progression_next_level_header, next.number, text.levelName(next.number))
        } else {
            card.root.context.getString(R.string.progression_max_level_reached)
        }

        card.levelRequirements.removeAllViews()
        if (checklist) {
            val inflater = LayoutInflater.from(card.root.context)
            progress.requirements.forEach { requirement ->
                val row = ItemLevelRequirementBinding.inflate(inflater, card.levelRequirements, false)
                row.root.text = text.requirementLine(requirement)
                card.levelRequirements.addView(row.root)
            }
        }
    }

    fun progressValue(fraction: Double): Int = (fraction.coerceIn(0.0, 1.0) * 1000).roundToInt()

    fun bindRoadmap(container: LinearLayout, progress: LevelProgress) {
        val context = container.context
        val text = ProgressionText(context)
        val inflater = LayoutInflater.from(context)
        container.removeAllViews()
        LevelLadder.levels.forEach { level ->
            val row = ItemLevelRoadmapBinding.inflate(inflater, container, false)
            val name = text.levelName(level.number)
            val requirements = text.roadmapRequirements(level)
            row.roadmapTitle.text = text.levelTitle(level)
            row.roadmapRequirements.text = requirements
            val current = progress.current.number
            when {
                level.number < current -> {
                    row.roadmapStatus.text = context.getString(R.string.progression_roadmap_done)
                    row.roadmapStatus.setTextColor(ContextCompat.getColor(context, R.color.colorSuccess))
                    row.root.contentDescription = context.getString(R.string.progression_roadmap_cd_done, level.number, name)
                }
                level.number == current -> {
                    row.roadmapStatus.text = context.getString(R.string.progression_roadmap_current)
                    row.roadmapStatus.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
                    row.roadmapTitle.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
                    row.root.setBackgroundColor(ContextCompat.getColor(context, R.color.colorSurfaceVariant))
                    row.root.contentDescription = context.getString(R.string.progression_roadmap_cd_current, level.number, name)
                }
                else -> {
                    row.roadmapStatus.text = context.getString(R.string.progression_roadmap_locked)
                    row.root.alpha = LOCKED_ALPHA
                    row.root.contentDescription =
                        context.getString(R.string.progression_roadmap_cd_locked, level.number, name, requirements)
                }
            }
            container.addView(row.root)
        }
    }

    /**
     * The eight level trophies, then the six session medals under their own header (SRS
     * PG-17, PG-28). Medals never count towards levels, so they are kept visibly apart.
     */
    fun bindCabinet(container: LinearLayout, career: CareerSnapshot) {
        val context = container.context
        container.removeAllViews()
        container.addView(caption(context, context.getString(
            R.string.progression_valid_lap_note, ValidLaps.MIN_PERCENT.toInt(), ValidLaps.MAX_PERCENT.toInt()
        ), header = false))
        career.cabinet.filter { it.type.countsTowardLevels }.forEach { container.addView(tile(container, career, it)) }
        container.addView(caption(context, context.getString(R.string.progression_medals_title), header = true).apply {
            tag = MEDALS_HEADER_TAG
        })
        container.addView(caption(context, context.getString(R.string.progression_medals_note), header = false))
        career.cabinet.filter { it.type.isMedal }.forEach { container.addView(tile(container, career, it)) }
    }

    private fun tile(container: LinearLayout, career: CareerSnapshot, entry: CabinetEntry): View {
        val context = container.context
        val text = ProgressionText(context)
        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
        val tile = ItemTrophyTileBinding.inflate(LayoutInflater.from(context), container, false)
        val name = text.trophyName(entry.type)
        val medal = entry.type.isMedal
        tile.root.tag = entry.type.name
        tile.trophyName.text = if (medal) name else text.trophyWithMetal(entry.type)
        tile.trophyMedal.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, if (entry.earned) ProgressionText.metalColor(entry.type) else R.color.colorMedalLocked)
        )
        if (entry.earned) {
            tile.trophyCount.text = context.getString(R.string.trophy_count, entry.count)
            tile.trophyCount.setTextColor(ContextCompat.getColor(context, ProgressionText.metalColor(entry.type)))
            tile.trophyStatus.text = context.getString(
                R.string.trophy_last_earned,
                entry.lastAwardedAt?.let { dateFormat.format(Date(it)) }.orEmpty(),
                entry.lastTrackName.orEmpty()
            )
            tile.trophyHowToWin.visibility = View.GONE
            tile.trophyHint.visibility = View.GONE
            tile.root.contentDescription = context.getString(
                if (medal) R.string.medal_tile_cd_earned else R.string.trophy_tile_cd_earned, name, entry.count
            )
        } else {
            tile.trophyCount.visibility = View.GONE
            tile.trophyStatus.text = context.getString(R.string.trophy_not_earned)
            tile.trophyName.alpha = LOCKED_ALPHA
            tile.trophyHowToWin.text = context.getString(R.string.trophy_how_to_win, text.howToWin(entry.type))
            val hint: TrophyHint? = career.hints[entry.type]
            setOrHide(tile.trophyHint, text.hint(hint))
            tile.root.contentDescription = context.getString(
                if (medal) R.string.medal_tile_cd_locked else R.string.trophy_tile_cd_locked, name
            )
        }
        return tile.root
    }

    private fun caption(context: Context, value: String, header: Boolean): TextView =
        TextView(context).apply {
            TextViewCompat.setTextAppearance(
                this, if (header) R.style.TextAppearance_DrivingCoach_Caption else R.style.TextAppearance_DrivingCoach_Body_Secondary
            )
            if (header) setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
            text = value
            val top = context.resources.getDimensionPixelSize(if (header) R.dimen.spacing_lg else R.dimen.spacing_xs)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = top }
        }

    private fun setOrHide(view: TextView, value: String?) {
        view.text = value
        view.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private const val LOCKED_ALPHA = 0.45f
    const val MEDALS_HEADER_TAG = "cabinet_medals_header"
}
