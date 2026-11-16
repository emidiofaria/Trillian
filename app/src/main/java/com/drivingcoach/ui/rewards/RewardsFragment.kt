package com.drivingcoach.ui.rewards

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.drivingcoach.R
import com.drivingcoach.data.repository.SessionRewards
import com.drivingcoach.databinding.FragmentRewardsBinding
import com.drivingcoach.databinding.ItemLevelRequirementBinding
import com.drivingcoach.progression.OdometerMilestones
import com.drivingcoach.progression.ProgressionFormat
import com.drivingcoach.ui.progression.CareerViews
import com.drivingcoach.ui.progression.ProgressionText
import com.drivingcoach.util.LapTimeFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Rewards screen (SRS PG-19, PG-20), between Recording and Session Result.
 *
 * Continue (or Back) replaces it with Session Result, so Back from the result returns to
 * Home rather than replaying the celebration.
 */
@AndroidEntryPoint
class RewardsFragment : Fragment() {

    private var _binding: FragmentRewardsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RewardsViewModel by viewModels()

    private var contentBound = false
    private var popupOnScreen: RewardPopup? = null
    private var glowAnimator: ObjectAnimator? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRewardsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setAccessibilityHeading(binding.rewardsTitle, true)
        binding.continueButton.setOnClickListener { goToResult() }
        binding.popupDismiss.setOnClickListener { viewModel.onPopupDismissed() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (popupOnScreen != null) viewModel.onPopupDismissed() else goToResult()
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun render(state: RewardsUiState) {
        when (state) {
            RewardsUiState.Loading -> binding.rewardsLoading.visibility = View.VISIBLE
            RewardsUiState.Unavailable -> {
                binding.rewardsLoading.visibility = View.GONE
                binding.rewardsContent.visibility = View.GONE
            }
            is RewardsUiState.Ready -> {
                binding.rewardsLoading.visibility = View.GONE
                if (!contentBound) {
                    contentBound = true
                    bindContent(state.rewards)
                }
                showPopup(state.currentPopup)
            }
        }
    }

    private fun bindContent(rewards: SessionRewards) {
        val text = ProgressionText(requireContext())
        binding.rewardsContent.visibility = View.VISIBLE
        binding.rewardsTrack.text = rewards.trackName
        binding.rewardsKmBeforeAfter.text = getString(
            R.string.rewards_km_before_after,
            ProgressionFormat.drivenKm(rewards.before.lifetimeKm),
            ProgressionFormat.drivenKm(rewards.after.lifetimeKm)
        )

        binding.summaryDistance.text = getString(R.string.rewards_km_value, ProgressionFormat.drivenKm(rewards.distanceKm))
        binding.summaryLaps.text = rewards.lapCount.toString()
        binding.summaryBestLap.text = rewards.bestLapMs?.let { LapTimeFormatter.formatLapTime(it) }
            ?: getString(R.string.rewards_value_none)
        binding.summaryConsistency.text = rewards.cvPercent?.let { getString(R.string.rewards_cv_value, ProgressionFormat.percent(it)) }
            ?: getString(R.string.rewards_value_none)

        binding.rewardsTrophies.removeAllViews()
        rewards.newTrophies.forEach { type ->
            val row = ItemLevelRequirementBinding.inflate(layoutInflater, binding.rewardsTrophies, false)
            row.root.text = text.trophyName(type)
            row.root.setTextColor(ContextCompat.getColor(requireContext(), ProgressionText.metalColor(type)))
            row.root.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_trophy, 0, 0, 0)
            row.root.compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            row.root.compoundDrawableTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), ProgressionText.metalColor(type)))
            binding.rewardsTrophies.addView(row.root)
        }
        binding.rewardsNoTrophies.visibility = if (rewards.newTrophies.isEmpty()) View.VISIBLE else View.GONE

        binding.rewardsMedals.removeAllViews()
        rewards.newMedals.forEach { type ->
            val row = ItemLevelRequirementBinding.inflate(layoutInflater, binding.rewardsMedals, false)
            row.root.text = getString(R.string.rewards_medal_row, text.trophyName(type), text.howToWin(type))
            row.root.tag = type.name
            row.root.setTextColor(ContextCompat.getColor(requireContext(), ProgressionText.metalColor(type)))
            row.root.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_trophy, 0, 0, 0)
            row.root.compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            row.root.compoundDrawableTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), ProgressionText.metalColor(type)))
            binding.rewardsMedals.addView(row.root)
        }
        binding.rewardsNoMedals.visibility = if (rewards.newMedals.isEmpty()) View.VISIBLE else View.GONE

        binding.rewardsMilestones.removeAllViews()
        rewards.milestonesKm.forEach { km ->
            val row = ItemLevelRequirementBinding.inflate(layoutInflater, binding.rewardsMilestones, false)
            row.root.text = getString(R.string.rewards_milestone, km)
            binding.rewardsMilestones.addView(row.root)
        }
        val milestones = if (rewards.milestonesKm.isEmpty()) View.GONE else View.VISIBLE
        binding.rewardsMilestonesTitle.visibility = milestones
        binding.rewardsMilestones.visibility = milestones

        binding.rewardsRecentProgress.text = text.recentProgress(rewards.recentProgress)
        binding.rewardsNextGoal.nextGoalText.text = text.nextGoal(rewards.after.nextGoal)

        animateLevelProgress(rewards)
    }

    /**
     * The bar starts where the career stood before this session and moves to where it
     * stands now; across a level up it fills, then restarts in the new level's band.
     */
    private fun animateLevelProgress(rewards: SessionRewards) {
        val card = binding.rewardsLevelCard
        CareerViews.bindLevelCard(card, rewards.before.progress, showChecklist = true)
        viewLifecycleOwner.lifecycleScope.launch {
            delay(PROGRESS_START_DELAY_MS)
            if (rewards.levelUp) {
                card.levelKmProgress.setProgressCompat(CareerViews.progressValue(1.0), true)
                delay(PROGRESS_LEVEL_UP_PAUSE_MS)
                CareerViews.bindLevelCard(card, rewards.after.progress, showChecklist = true)
                card.levelKmProgress.setProgressCompat(0, false)
            }
            CareerViews.bindLevelCard(card, rewards.after.progress, showChecklist = true, animate = true)
        }
    }

    private fun showPopup(popup: RewardPopup?) {
        if (popup == popupOnScreen) return
        popupOnScreen = popup
        glowAnimator?.cancel()

        if (popup == null) {
            binding.popupOverlay.visibility = View.GONE
            return
        }

        val text = ProgressionText(requireContext())
        val colorRes: Int
        when (popup) {
            is RewardPopup.Trophy -> {
                colorRes = ProgressionText.metalColor(popup.type)
                binding.popupHeadline.text = getString(R.string.rewards_popup_trophy)
                binding.popupName.text = text.trophyWithMetal(popup.type)
                binding.popupDescription.text = text.howToWin(popup.type)
                binding.popupIcon.visibility = View.VISIBLE
                binding.popupLevelNumber.visibility = View.GONE
            }
            is RewardPopup.Milestone -> {
                colorRes = R.color.colorPrimary
                binding.popupHeadline.text = getString(R.string.rewards_popup_milestone)
                binding.popupName.text = getString(R.string.rewards_milestone, popup.km)
                binding.popupDescription.text = getString(R.string.rewards_milestone_description, OdometerMilestones.STEP_KM)
                binding.popupIcon.visibility = View.VISIBLE
                binding.popupLevelNumber.visibility = View.GONE
            }
            is RewardPopup.LevelUp -> {
                colorRes = R.color.colorPrimary
                binding.popupHeadline.text = getString(R.string.rewards_popup_level_up)
                binding.popupName.text = text.levelTitle(popup.level)
                binding.popupDescription.text = getString(R.string.progression_tier_label, text.tierName(popup.level.tier))
                binding.popupIcon.visibility = View.GONE
                binding.popupLevelNumber.visibility = View.VISIBLE
                binding.popupLevelNumber.text = getString(R.string.progression_level_badge, popup.level.number)
            }
        }
        val color = ContextCompat.getColor(requireContext(), colorRes)
        binding.popupHeadline.setTextColor(color)
        binding.popupMedal.backgroundTintList = ColorStateList.valueOf(color)
        binding.popupGlow.backgroundTintList = ColorStateList.valueOf(color)
        binding.popupCard.strokeColor = color
        binding.popupCard.contentDescription = getString(
            R.string.rewards_popup_cd, binding.popupHeadline.text, binding.popupName.text
        )

        binding.popupOverlay.visibility = View.VISIBLE
        animatePopupIn()
        // Haptic through the view, so no VIBRATE permission is needed (SRS PG-19).
        binding.popupCard.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.LONG_PRESS
        )
        binding.popupCard.announceForAccessibility(binding.popupCard.contentDescription)
    }

    private fun animatePopupIn() {
        val card = binding.popupCard
        card.scaleX = POPUP_START_SCALE
        card.scaleY = POPUP_START_SCALE
        card.alpha = 0f
        card.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(POPUP_IN_MS)
            .setInterpolator(OvershootInterpolator())
            .start()

        val glow = binding.popupGlow
        glow.alpha = 0f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(glow, View.SCALE_X, POPUP_START_SCALE, 1f),
                ObjectAnimator.ofFloat(glow, View.SCALE_Y, POPUP_START_SCALE, 1f)
            )
            duration = POPUP_IN_MS
            start()
        }
        // Finite on purpose: an endless animation would keep the UI thread from idling.
        glowAnimator = ObjectAnimator.ofFloat(glow, View.ALPHA, GLOW_MIN_ALPHA, 1f).apply {
            duration = GLOW_PULSE_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = GLOW_PULSES
            start()
        }
    }

    private fun goToResult() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.rewardsFragment) return
        navController.navigate(RewardsFragmentDirections.actionRewardsToSessionResult(viewModel.sessionId))
    }

    override fun onDestroyView() {
        glowAnimator?.cancel()
        glowAnimator = null
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val POPUP_START_SCALE = 0.6f
        const val POPUP_IN_MS = 450L
        const val GLOW_PULSE_MS = 700L
        const val GLOW_PULSES = 5
        const val GLOW_MIN_ALPHA = 0.35f
        const val PROGRESS_START_DELAY_MS = 400L
        const val PROGRESS_LEVEL_UP_PAUSE_MS = 900L
    }
}
