package com.drivingcoach.ui.session.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.drivingcoach.R
import com.drivingcoach.data.db.entity.CoachingInsightEntity
import com.drivingcoach.databinding.FragmentCoachBinding
import com.drivingcoach.databinding.ItemCoachingInsightBinding
import com.drivingcoach.ui.session.SessionResultViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CoachFragment : Fragment() {

    private var _binding: FragmentCoachBinding? = null
    private val binding get() = _binding!!

    private val parentViewModel: SessionResultViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoachBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parentViewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: com.drivingcoach.ui.session.SessionUiState) {
        // Update consistency score
        binding.consistencyScore.text = String.format("%.1f%%", state.consistencyScore)
        
        // Update session summary
        val lapCount = state.laps.size
        val bestLapTime = state.bestLap?.let { formatLapTime(it.durationMs) } ?: "N/A"
        binding.sessionSummary.text = "$lapCount laps completed\nBest lap: $bestLapTime"

        // Handle loading/empty states
        when {
            state.isLoading -> {
                binding.loadingContainer.visibility = View.VISIBLE
                binding.insightsContainer.visibility = View.GONE
                binding.emptyStateText.visibility = View.GONE
                binding.headerCard.visibility = View.GONE
            }
            state.insights.isEmpty() -> {
                binding.loadingContainer.visibility = View.GONE
                binding.insightsContainer.visibility = View.GONE
                binding.emptyStateText.visibility = View.VISIBLE
                binding.headerCard.visibility = View.VISIBLE
            }
            else -> {
                binding.loadingContainer.visibility = View.GONE
                binding.insightsContainer.visibility = View.VISIBLE
                binding.emptyStateText.visibility = View.GONE
                binding.headerCard.visibility = View.VISIBLE
                populateInsights(state.insights)
            }
        }
    }

    private fun populateInsights(insights: List<CoachingInsightEntity>) {
        binding.insightsContainer.removeAllViews()
        
        insights.forEach { insight ->
            val insightBinding = ItemCoachingInsightBinding.inflate(
                layoutInflater,
                binding.insightsContainer,
                false
            )
            insightBinding.headline.text = insight.headline
            insightBinding.detail.text = insight.detail
            binding.insightsContainer.addView(insightBinding.root)
        }
    }

    private fun formatLapTime(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = durationMs % 1000
        return String.format("%d:%02d.%03d", minutes, seconds, millis)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SESSION_ID = "sessionId"

        fun newInstance(sessionId: Long): CoachFragment {
            return CoachFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SESSION_ID, sessionId)
                }
            }
        }
    }
}
