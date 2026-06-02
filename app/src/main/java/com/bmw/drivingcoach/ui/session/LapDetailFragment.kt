package com.bmw.drivingcoach.ui.session

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bmw.drivingcoach.databinding.FragmentLapDetailBinding
import com.bmw.drivingcoach.databinding.ItemSectorComparisonBinding
import com.bmw.drivingcoach.util.LapTimeFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LapDetailFragment : Fragment() {

    private var _binding: FragmentLapDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LapDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLapDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: LapDetailUiState) {
        binding.loadingIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        if (state.isLoading) return

        val selectedLap = state.selectedLap ?: return
        val bestLap = state.bestLap ?: return

        // Update toolbar title
        binding.toolbar.title = "Lap ${selectedLap.lapNumber} vs Best"

        // Update two-column header
        binding.selectedLapLabel.text = "LAP ${selectedLap.lapNumber}"
        binding.selectedLapTime.text = LapTimeFormatter.formatLapTime(selectedLap.durationMs)
        binding.bestLapTime.text = LapTimeFormatter.formatLapTime(bestLap.durationMs)

        // Update total delta
        binding.totalDelta.text = "${LapTimeFormatter.formatDelta(state.totalDeltaMs)} vs Best"
        binding.totalDelta.setTextColor(LapTimeFormatter.deltaColor(state.totalDeltaMs, requireContext()))

        // Update sector comparison rows
        updateSectorRow(
            ItemSectorComparisonBinding.bind(binding.sector1Row.root),
            state.sectorDeltas.getOrNull(0),
            "Sector 1",
            state.sectorDeltas.maxOfOrNull { it.selectedMs } ?: 0
        )
        updateSectorRow(
            ItemSectorComparisonBinding.bind(binding.sector2Row.root),
            state.sectorDeltas.getOrNull(1),
            "Sector 2",
            state.sectorDeltas.maxOfOrNull { it.selectedMs } ?: 0
        )
        updateSectorRow(
            ItemSectorComparisonBinding.bind(binding.sector3Row.root),
            state.sectorDeltas.getOrNull(2),
            "Sector 3",
            state.sectorDeltas.maxOfOrNull { it.selectedMs } ?: 0
        )
    }

    private fun updateSectorRow(
        rowBinding: ItemSectorComparisonBinding,
        sectorDelta: SectorDelta?,
        label: String,
        maxSectorTime: Long
    ) {
        if (sectorDelta == null) {
            rowBinding.root.visibility = View.GONE
            return
        }

        rowBinding.root.visibility = View.VISIBLE
        rowBinding.sectorLabel.text = label
        rowBinding.selectedTime.text = LapTimeFormatter.formatSectorTime(sectorDelta.selectedMs)
        rowBinding.deltaBadge.text = LapTimeFormatter.formatDelta(sectorDelta.deltaMs)
        rowBinding.deltaBadge.setTextColor(LapTimeFormatter.deltaColor(sectorDelta.deltaMs, requireContext()))

        // Update performance bar width based on relative performance
        // The best time (lowest) should have full width, slower times have proportionally less
        val performance = if (sectorDelta.selectedMs <= sectorDelta.bestMs) {
            1f // This is the best or better
        } else {
            // Calculate how close to best (1.0 = best, lower = worse)
            sectorDelta.bestMs.toFloat() / sectorDelta.selectedMs.toFloat()
        }
        
        // Set the fill width as a percentage of parent
        rowBinding.performanceBarFill.post {
            val parentWidth = rowBinding.performanceBarBg.width
            val fillWidth = (parentWidth * performance.coerceIn(0.3f, 1f)).toInt()
            rowBinding.performanceBarFill.layoutParams.width = fillWidth
            rowBinding.performanceBarFill.requestLayout()
        }
        
        // Color the bar based on delta
        val barColor = LapTimeFormatter.deltaColor(sectorDelta.deltaMs, requireContext())
        rowBinding.performanceBarFill.setBackgroundColor(barColor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
