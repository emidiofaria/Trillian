package com.drivingcoach.ui.progression

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.data.repository.ProgressionRepository
import com.drivingcoach.progression.CareerSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The driver's career for Home and Profile (SRS PG-15, PG-18).
 *
 * Opening either screen also triggers the one-off replay of sessions recorded before
 * progression existed (PG-13); the career flow re-emits as that replay writes.
 */
@HiltViewModel
class CareerViewModel @Inject constructor(
    progressionRepository: ProgressionRepository
) : ViewModel() {

    val career: StateFlow<CareerSnapshot?> = progressionRepository.career()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        viewModelScope.launch {
            try {
                progressionRepository.backfillIfNeeded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Progression history replay failed; it will retry next time", e)
            }
        }
    }

    private companion object {
        const val TAG = "CareerViewModel"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
