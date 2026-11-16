package com.drivingcoach.ui.rewards

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.data.repository.ProgressionRepository
import com.drivingcoach.data.repository.SessionRewards
import com.drivingcoach.progression.LevelDef
import com.drivingcoach.progression.TrophyType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One celebration shown over the Rewards screen, in order (SRS PG-19). */
sealed class RewardPopup {
    data class Trophy(val type: TrophyType) : RewardPopup()
    data class LevelUp(val level: LevelDef) : RewardPopup()
}

sealed class RewardsUiState {
    object Loading : RewardsUiState()

    /** Progression could not be read; the screen still offers Continue. */
    object Unavailable : RewardsUiState()

    data class Ready(
        val rewards: SessionRewards,
        val popups: List<RewardPopup>,
        /** How many pop-ups the driver has already dismissed; survives rotation. */
        val popupsShown: Int
    ) : RewardsUiState() {
        val currentPopup: RewardPopup? get() = popups.getOrNull(popupsShown)
    }
}

@HiltViewModel
class RewardsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val progressionRepository: ProgressionRepository
) : ViewModel() {

    val sessionId: Long = savedStateHandle.get<Long>(ARG_SESSION_ID) ?: -1L

    private val _uiState = MutableStateFlow<RewardsUiState>(RewardsUiState.Loading)
    val uiState: StateFlow<RewardsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val rewards = try {
                progressionRepository.rewards(sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not read rewards for session $sessionId", e)
                null
            }
            _uiState.value = if (rewards == null) {
                RewardsUiState.Unavailable
            } else {
                RewardsUiState.Ready(rewards, popupsFor(rewards), savedStateHandle[KEY_POPUPS_SHOWN] ?: 0)
            }
        }
    }

    fun onPopupDismissed() {
        val state = _uiState.value as? RewardsUiState.Ready ?: return
        if (state.currentPopup == null) return
        val shown = state.popupsShown + 1
        savedStateHandle[KEY_POPUPS_SHOWN] = shown
        _uiState.value = state.copy(popupsShown = shown)
    }

    companion object {
        private const val TAG = "RewardsViewModel"
        const val ARG_SESSION_ID = "sessionId"
        private const val KEY_POPUPS_SHOWN = "popupsShown"

        /** Trophies first, in cabinet order, then the level up they may have caused. */
        fun popupsFor(rewards: SessionRewards): List<RewardPopup> =
            rewards.newTrophies.map { RewardPopup.Trophy(it) } +
                if (rewards.levelUp) listOf(RewardPopup.LevelUp(rewards.after.progress.current)) else emptyList()
    }
}
