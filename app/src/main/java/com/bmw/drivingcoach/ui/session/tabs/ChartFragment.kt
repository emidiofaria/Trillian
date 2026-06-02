package com.bmw.drivingcoach.ui.session.tabs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bmw.drivingcoach.R
import com.bmw.drivingcoach.data.db.entity.LapEntity
import com.bmw.drivingcoach.databinding.FragmentChartBinding
import com.bmw.drivingcoach.ui.session.SessionResultViewModel
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChartFragment : Fragment() {

    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!

    private val parentViewModel: SessionResultViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        observeViewModel()
    }

    private fun setupChart() {
        binding.speedChart.apply {
            // Basic setup
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorBackground))

            // X axis
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = ContextCompat.getColor(requireContext(), R.color.colorOnSurfaceVariant)
                setDrawGridLines(true)
                gridColor = Color.argb(51, 255, 255, 255) // 20% opacity white
                axisLineColor = ContextCompat.getColor(requireContext(), R.color.colorOnSurfaceVariant)
            }

            // Y axis
            axisLeft.apply {
                textColor = ContextCompat.getColor(requireContext(), R.color.colorOnSurfaceVariant)
                setDrawGridLines(true)
                gridColor = Color.argb(51, 255, 255, 255) // 20% opacity white
                axisLineColor = ContextCompat.getColor(requireContext(), R.color.colorOnSurfaceVariant)
                axisMinimum = 0f
            }
            axisRight.isEnabled = false

            // Legend
            legend.apply {
                isEnabled = true
                textColor = ContextCompat.getColor(requireContext(), R.color.colorOnSurface)
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(true)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parentViewModel.uiState.collect { state ->
                    when {
                        state.isLoading -> {
                            binding.loadingContainer.visibility = View.VISIBLE
                            binding.speedChart.visibility = View.GONE
                            binding.emptyStateText.visibility = View.GONE
                        }
                        state.laps.isEmpty() -> {
                            binding.loadingContainer.visibility = View.GONE
                            binding.speedChart.visibility = View.GONE
                            binding.emptyStateText.visibility = View.VISIBLE
                        }
                        else -> {
                            binding.loadingContainer.visibility = View.GONE
                            binding.speedChart.visibility = View.VISIBLE
                            binding.emptyStateText.visibility = View.GONE
                            updateChart(state.laps, state.bestLap)
                        }
                    }
                }
            }
        }
    }

    private fun updateChart(laps: List<LapEntity>, bestLap: LapEntity?) {
        val dataSets = mutableListOf<LineDataSet>()
        val bmwBlue = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
        val greyColor = Color.argb(128, 176, 176, 176) // Semi-transparent grey

        laps.forEachIndexed { index, lap ->
            // Generate simulated speed data based on sector times
            // In a real app, this would come from telemetry samples
            val entries = generateSpeedEntries(lap)
            
            val isBest = lap.id == bestLap?.id
            val dataSet = LineDataSet(entries, if (isBest) "Best Lap" else "").apply {
                color = if (isBest) bmwBlue else greyColor
                lineWidth = if (isBest) 3f else 1.5f
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                
                if (!isBest) {
                    // Don't show labels for non-best laps
                    label = ""
                }
            }
            dataSets.add(dataSet)
        }

        // Add a grey reference entry for legend
        if (laps.size > 1) {
            val greyLegendSet = LineDataSet(listOf(Entry(0f, 0f)), "Other Laps").apply {
                color = greyColor
                lineWidth = 1.5f
                setDrawCircles(false)
                setDrawValues(false)
            }
            // We'll handle legend differently since we can't easily add a dummy set
        }

        binding.speedChart.data = LineData(dataSets.toList())
        binding.speedChart.invalidate()
    }

    private fun generateSpeedEntries(lap: LapEntity): List<Entry> {
        // Generate synthetic speed curve based on lap duration and sector times
        // In a real implementation, this would use actual telemetry samples
        val entries = mutableListOf<Entry>()
        val totalDuration = lap.durationMs.toFloat()
        val numPoints = 50

        for (i in 0 until numPoints) {
            val progress = i.toFloat() / numPoints
            val time = progress * totalDuration / 1000f // Time in seconds
            
            // Simulate speed variation: faster in straights, slower in corners
            // Using sine wave pattern to simulate track layout
            val baseSpeed = 120f // Base speed in km/h
            val variation = 40f
            val speed = baseSpeed + variation * kotlin.math.sin(progress * 6 * kotlin.math.PI).toFloat()
            
            entries.add(Entry(time, speed.coerceIn(60f, 180f)))
        }
        
        return entries
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SESSION_ID = "sessionId"

        fun newInstance(sessionId: Long): ChartFragment {
            return ChartFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SESSION_ID, sessionId)
                }
            }
        }
    }
}
