package com.drivingcoach.ui.session.tabs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.drivingcoach.R
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.databinding.FragmentChartBinding
import com.drivingcoach.ui.session.SessionResultViewModel
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChartFragment : Fragment() {

    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!

    private val parentViewModel: SessionResultViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    // Track if we've already loaded real data to avoid re-loading
    private var hasLoadedRealData = false
    private var selectedProcessingMode: ChartProcessingMode? = null

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

            // X axis - distance in meters
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = ContextCompat.getColor(requireContext(), R.color.colorOnSurfaceVariant)
                setDrawGridLines(true)
                gridColor = Color.argb(51, 255, 255, 255) // 20% opacity white
                axisLineColor = ContextCompat.getColor(requireContext(), R.color.colorOnSurfaceVariant)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}m"
                    }
                }
            }

            // Y axis - speed in km/h
            axisLeft.apply {
                textColor = ContextCompat.getColor(requireContext(), R.color.colorOnSurfaceVariant)
                setDrawGridLines(true)
                gridColor = Color.argb(51, 255, 255, 255) // 20% opacity white
                axisLineColor = ContextCompat.getColor(requireContext(), R.color.colorOnSurfaceVariant)
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()} km/h"
                    }
                }
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
                            // Try to load real telemetry data
                            if (!hasLoadedRealData && state.rawFilePath != null) {
                                loadRealSpeedData(state.rawFilePath, state.laps, state.bestLap)
                            } else if (state.rawFilePath == null) {
                                // No telemetry file - show empty state
                                binding.loadingContainer.visibility = View.GONE
                                binding.speedChart.visibility = View.GONE
                                binding.emptyStateText.text = "Telemetry data not available"
                                binding.emptyStateText.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadRealSpeedData(rawFilePath: String, laps: List<LapEntity>, bestLap: LapEntity?) {
        // Check if this is a large session
        val isLargeSession = laps.size > TelemetryChartProcessor.LARGE_SESSION_LAP_THRESHOLD

        if (isLargeSession && selectedProcessingMode == null) {
            // Show dialog for processing mode selection
            showProcessingModeDialog(rawFilePath, laps, bestLap)
        } else {
            // Use selected mode or default to DETAILED for small sessions
            val mode = selectedProcessingMode ?: ChartProcessingMode.DETAILED
            processAndDisplayChart(rawFilePath, laps, bestLap, mode, isLargeSession)
        }
    }

    private fun showProcessingModeDialog(rawFilePath: String, laps: List<LapEntity>, bestLap: LapEntity?) {
        AlertDialog.Builder(requireContext())
            .setTitle("Large Session Detected")
            .setMessage(
                "This session has ${laps.size} laps. " +
                "Offline processing is limited and may take some time.\n\n" +
                "Choose processing mode:"
            )
            .setPositiveButton("⚡ Fast") { _, _ ->
                selectedProcessingMode = ChartProcessingMode.FAST
                processAndDisplayChart(rawFilePath, laps, bestLap, ChartProcessingMode.FAST, true)
            }
            .setNegativeButton("📊 Detailed") { _, _ ->
                selectedProcessingMode = ChartProcessingMode.DETAILED
                processAndDisplayChart(rawFilePath, laps, bestLap, ChartProcessingMode.DETAILED, true)
            }
            .setCancelable(false)
            .show()
    }

    private fun processAndDisplayChart(
        rawFilePath: String,
        laps: List<LapEntity>,
        bestLap: LapEntity?,
        mode: ChartProcessingMode,
        isLargeSession: Boolean
    ) {
        binding.loadingContainer.visibility = View.VISIBLE
        binding.speedChart.visibility = View.GONE
        binding.emptyStateText.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val dataSets = mutableListOf<LineDataSet>()
                val brandBlue = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
                val greyColor = Color.argb(128, 176, 176, 176)

                laps.forEachIndexed { index, lap ->
                    val speedData = TelemetryChartProcessor.computeSpeedByDistance(
                        rawFilePath,
                        lap.startTs,
                        lap.endTs,
                        mode
                    )

                    if (speedData.isNotEmpty()) {
                        val entries = speedData.map { Entry(it.distanceMeters, it.speedKmh) }
                        val isBest = lap.id == bestLap?.id

                        val dataSet = LineDataSet(entries, if (isBest) "Best Lap" else "").apply {
                            color = if (isBest) brandBlue else greyColor
                            lineWidth = if (isBest) 3f else 1.5f
                            setDrawCircles(false)
                            setDrawValues(false)
                            this.mode = LineDataSet.Mode.CUBIC_BEZIER

                            if (!isBest) {
                                label = ""
                            }
                        }
                        dataSets.add(dataSet)
                    }
                }

                if (dataSets.isEmpty()) {
                    binding.loadingContainer.visibility = View.GONE
                    binding.speedChart.visibility = View.GONE
                    binding.emptyStateText.text = "No speed data available"
                    binding.emptyStateText.visibility = View.VISIBLE
                } else {
                    binding.speedChart.data = LineData(dataSets.toList())
                    binding.speedChart.invalidate()
                    binding.loadingContainer.visibility = View.GONE
                    binding.speedChart.visibility = View.VISIBLE
                    binding.emptyStateText.visibility = View.GONE
                    hasLoadedRealData = true
                }
            } catch (e: Exception) {
                binding.loadingContainer.visibility = View.GONE
                binding.speedChart.visibility = View.GONE
                binding.emptyStateText.text = "Error loading speed data"
                binding.emptyStateText.visibility = View.VISIBLE
            }
        }
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
