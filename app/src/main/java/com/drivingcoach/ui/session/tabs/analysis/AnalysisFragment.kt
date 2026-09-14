package com.drivingcoach.ui.session.tabs.analysis

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
import com.drivingcoach.R
import com.drivingcoach.databinding.FragmentAnalysisBinding
import com.drivingcoach.databinding.ItemBrakingRowBinding
import com.drivingcoach.databinding.ItemCornerRowBinding
import com.drivingcoach.ui.session.SessionResultViewModel
import com.drivingcoach.util.LapTimeFormatter
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The ANALYSIS tab of the Session Result screen: session statistics, an offline
 * track map, a reference-lap selector, corner and braking-zone tables, and the
 * session speed trace.
 *
 * The tab is read-only and derived entirely from data that already exists: laps
 * come from the database (detected by `LocalLapDetector`) and everything else is
 * computed from the session's telemetry file by [SessionAnalysisProcessor].
 */
@AndroidEntryPoint
class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!

    private val parentViewModel: SessionResultViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )
    private val viewModel: AnalysisViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.trackMap.emptyText = getString(R.string.analysis_map_empty)
        setupSpeedChart()
        observeSession()
        observeAnalysis()
    }

    private fun observeSession() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parentViewModel.uiState.collect { state ->
                    if (state.isLoading) return@collect
                    val session = state.session
                    val startLine = if (
                        session?.startLineLat1 != null && session.startLineLng1 != null &&
                        session.startLineLat2 != null && session.startLineLng2 != null
                    ) {
                        (session.startLineLat1 + session.startLineLat2) / 2 to
                                (session.startLineLng1 + session.startLineLng2) / 2
                    } else {
                        null
                    }
                    viewModel.submit(state.rawFilePath, state.laps, startLine)
                }
            }
        }
    }

    private fun observeAnalysis() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val analysis = state.analysis
                    when {
                        state.isLoading -> showLoading()
                        analysis == null || !analysis.hasData -> showEmpty(
                            if (state.hasTelemetryFile) {
                                getString(R.string.analysis_empty_too_short)
                            } else {
                                getString(R.string.analysis_empty_no_telemetry)
                            }
                        )
                        else -> showAnalysis(analysis)
                    }
                }
            }
        }
    }

    private fun showLoading() {
        binding.loadingContainer.visibility = View.VISIBLE
        binding.emptyStateText.visibility = View.GONE
        binding.contentContainer.visibility = View.GONE
    }

    private fun showEmpty(message: String) {
        binding.loadingContainer.visibility = View.GONE
        binding.emptyStateText.text = message
        binding.emptyStateText.visibility = View.VISIBLE
        binding.contentContainer.visibility = View.GONE
    }

    private fun showAnalysis(analysis: SessionAnalysis) {
        binding.loadingContainer.visibility = View.GONE
        binding.emptyStateText.visibility = View.GONE
        binding.contentContainer.visibility = View.VISIBLE

        bindStats(analysis)
        bindMap(analysis)
        bindLapChips(analysis)
        bindCorners(analysis)
        bindBrakingZones(analysis)
        bindSpeedChart(analysis)
    }

    private fun bindStats(analysis: SessionAnalysis) {
        val stats = analysis.stats
        binding.statDistance.text = String.format(Locale.US, "%.2f km", stats.distanceM / 1000.0)
        binding.statDuration.text = formatDuration(stats.durationMs)
        binding.statMaxSpeed.text = String.format(Locale.US, "%.1f km/h", stats.maxSpeedKmh)
        binding.statAvgSpeed.text = String.format(Locale.US, "%.1f km/h", stats.avgSpeedKmh)
        binding.statBestLap.text = stats.bestLapMs?.let { LapTimeFormatter.formatLapTime(it) } ?: "—"
    }

    private fun bindMap(analysis: SessionAnalysis) {
        binding.trackMap.setTrackPath(analysis.path)
        binding.mapReferenceLabel.text = if (analysis.referenceIsWholeSession) {
            getString(R.string.analysis_reference_whole_session)
        } else {
            getString(R.string.analysis_reference_lap, analysis.referenceLapLabel.orEmpty())
        }
    }

    private fun bindLapChips(analysis: SessionAnalysis) {
        val group = binding.lapChipGroup
        group.removeAllViews()
        binding.lapSelectorCard.visibility =
            if (analysis.lapOptions.isEmpty()) View.GONE else View.VISIBLE

        analysis.lapOptions.forEach { lap ->
            val chip = Chip(requireContext()).apply {
                text = getString(
                    R.string.analysis_lap_chip,
                    lap.lapNumber,
                    LapTimeFormatter.formatLapTime(lap.durationMs)
                )
                isCheckable = true
                isChecked = lap.lapId == analysis.referenceLapId
                if (lap.isBestLap) {
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.colorGold))
                }
                setOnClickListener { viewModel.selectLap(lap.lapId) }
            }
            group.addView(chip)
        }
    }

    private fun bindCorners(analysis: SessionAnalysis) {
        val container = binding.cornerContainer
        container.removeAllViews()
        binding.cornerEmpty.visibility =
            if (analysis.corners.isEmpty()) View.VISIBLE else View.GONE

        // Corner times are shown relative to the start of the reference window,
        // which is where the drawn outline begins.
        val referenceStart = analysis.path.points.firstOrNull()?.timestampMs
        analysis.corners.forEach { corner ->
            val row = ItemCornerRowBinding.inflate(layoutInflater, container, false)
            row.cornerName.text = corner.name
            row.cornerApex.text = getString(
                R.string.analysis_corner_apex,
                String.format(Locale.US, "%.1f", corner.apexSpeedKmh)
            )
            val direction = if (corner.direction == TurnDirection.RIGHT) {
                getString(R.string.analysis_turn_right)
            } else {
                getString(R.string.analysis_turn_left)
            }
            val elapsedS = ((corner.apexMs - (referenceStart ?: corner.apexMs)) / 1000.0)
            row.cornerDetail.text = getString(
                R.string.analysis_corner_detail,
                direction,
                String.format(Locale.US, "%.0f", abs(corner.turnDeg)),
                String.format(Locale.US, "%.1f", elapsedS)
            )
            container.addView(row.root)
        }
    }

    private fun bindBrakingZones(analysis: SessionAnalysis) {
        val container = binding.brakingContainer
        container.removeAllViews()
        binding.brakingEmpty.visibility =
            if (analysis.brakingZones.isEmpty()) View.VISIBLE else View.GONE

        analysis.brakingZones.forEach { zone ->
            val row = ItemBrakingRowBinding.inflate(layoutInflater, container, false)
            row.brakingSpeeds.text = getString(
                R.string.analysis_braking_speeds,
                String.format(Locale.US, "%.1f", zone.entrySpeedKmh),
                String.format(Locale.US, "%.1f", zone.exitSpeedKmh)
            )
            row.brakingDetail.text = getString(
                R.string.analysis_braking_detail,
                String.format(Locale.US, "%.1f", zone.speedDropKmh),
                String.format(Locale.US, "%.2f", zone.peakG),
                String.format(Locale.US, "%.1f", zone.durationMs / 1000.0)
            )
            row.brakingCorner.text = zone.entersCorner?.let {
                getString(R.string.analysis_braking_into, it)
            } ?: ""
            container.addView(row.root)
        }
    }

    private fun setupSpeedChart() {
        binding.sessionSpeedChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            // Inside a NestedScrollView: gestures belong to the scroll container,
            // otherwise the chart swallows vertical drags and the tab feels stuck.
            setTouchEnabled(false)
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorSurface))
            setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.colorOnSurfaceVariant))

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = ContextCompat.getColor(requireContext(), R.color.colorOnSurfaceVariant)
                setDrawGridLines(false)
                axisLineColor = ContextCompat.getColor(requireContext(), R.color.colorOutline)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = "${value.toInt()}s"
                }
            }
            axisLeft.apply {
                textColor = ContextCompat.getColor(requireContext(), R.color.colorOnSurfaceVariant)
                setDrawGridLines(true)
                gridColor = Color.argb(51, 255, 255, 255)
                axisLineColor = ContextCompat.getColor(requireContext(), R.color.colorOutline)
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
        }
    }

    private fun bindSpeedChart(analysis: SessionAnalysis) {
        val entries = analysis.speedByTime.map { Entry(it.elapsedS, it.speedKmh) }
        if (entries.isEmpty()) {
            binding.sessionSpeedChart.clear()
            return
        }
        val dataSet = LineDataSet(entries, "").apply {
            color = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
            fillAlpha = 40
            mode = LineDataSet.Mode.LINEAR
        }
        binding.sessionSpeedChart.data = LineData(dataSet)
        binding.sessionSpeedChart.invalidate()
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000.0).roundToLong()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SESSION_ID = "sessionId"

        fun newInstance(sessionId: Long): AnalysisFragment = AnalysisFragment().apply {
            arguments = Bundle().apply { putLong(ARG_SESSION_ID, sessionId) }
        }
    }
}
