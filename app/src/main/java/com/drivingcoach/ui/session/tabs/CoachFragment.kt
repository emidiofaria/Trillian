package com.drivingcoach.ui.session.tabs

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
import com.drivingcoach.coaching.FundamentalsAnalyzer
import com.drivingcoach.coaching.FundamentalsAnalyzer.Tip
import com.drivingcoach.coaching.FundamentalsAnalyzer.Verdict
import com.drivingcoach.data.db.entity.CoachingInsightEntity
import com.drivingcoach.data.db.entity.CoachingPreference
import com.drivingcoach.data.track.CoachMap
import com.drivingcoach.databinding.FragmentCoachBinding
import com.drivingcoach.databinding.ItemCoachingInsightBinding
import com.drivingcoach.databinding.ItemFundamentalTipBinding
import com.drivingcoach.ui.session.SessionResultViewModel
import com.drivingcoach.ui.session.SessionUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CoachFragment : Fragment() {

    private var _binding: FragmentCoachBinding? = null
    private val binding get() = _binding!!

    private val parentViewModel: SessionResultViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private val mapViewModel: CoachTelemetryViewModel by viewModels()

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
        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnKeepLocal.setOnClickListener {
            parentViewModel.setCoachingPreference(CoachingPreference.LOCAL)
        }
        
        binding.btnViewAI.setOnClickListener {
            parentViewModel.setCoachingPreference(CoachingPreference.AI)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parentViewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mapViewModel.state.collect { mapState ->
                    updateSectorMap(mapState)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mapViewModel.fundamentals.collect { populateFundamentals(it) }
            }
        }
    }

    /**
     * Renders the four driving fundamentals (CF-01). Every tip is always shown; a verdict
     * changes the card only where the session could support one (CF-03 to CF-06).
     */
    private fun populateFundamentals(fundamentals: List<FundamentalsAnalyzer.Fundamental>) {
        val container = binding.fundamentalsContainer
        container.removeAllViews()
        val context = requireContext()

        fundamentals.forEach { fundamental ->
            val item = ItemFundamentalTipBinding.inflate(layoutInflater, container, false)
            val (title, body) = when (fundamental.tip) {
                Tip.FINAL_CORNER -> R.string.coach_tip_final_corner_title to R.string.coach_tip_final_corner_body
                Tip.BRAKING_REFERENCES -> R.string.coach_tip_braking_title to R.string.coach_tip_braking_body
                Tip.SLOW_IN_FAST_OUT -> R.string.coach_tip_slow_in_title to R.string.coach_tip_slow_in_body
                Tip.SMOOTH_STEERING -> R.string.coach_tip_steering_title to R.string.coach_tip_steering_body
            }
            item.tipTitle.setText(title)
            item.tipBody.setText(body)

            val status: String? = when (fundamental.verdict) {
                Verdict.GOOD -> when (fundamental.tip) {
                    Tip.FINAL_CORNER -> getString(R.string.coach_tip_final_corner_good)
                    Tip.BRAKING_REFERENCES -> getString(R.string.coach_tip_braking_good)
                    else -> null
                }
                Verdict.NEEDS_WORK -> when (fundamental.tip) {
                    Tip.FINAL_CORNER -> getString(R.string.coach_tip_final_corner_needs_work)
                    Tip.BRAKING_REFERENCES ->
                        getString(R.string.coach_tip_braking_needs_work, fundamental.focusSector ?: 1)
                    else -> null
                }
                Verdict.NOT_ASSESSED -> null
            }

            when {
                status != null && fundamental.verdict == Verdict.GOOD -> {
                    item.tipCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.fundamentalGoodSurface))
                    item.tipCard.strokeColor = ContextCompat.getColor(context, R.color.colorSuccess)
                    item.tipStatus.setTextColor(ContextCompat.getColor(context, R.color.colorSuccess))
                    item.tipCheck.visibility = View.VISIBLE
                }
                status != null -> {
                    item.tipCard.strokeColor = ContextCompat.getColor(context, R.color.colorWarning)
                    item.tipStatus.setTextColor(ContextCompat.getColor(context, R.color.colorWarning))
                }
            }
            if (status != null) {
                item.tipStatus.text = status
                item.tipStatus.visibility = View.VISIBLE
            }
            container.addView(item.root)
        }
    }

    /**
     * Shows the circuit with its sectors, or hides the card entirely.
     *
     * The map is never shown half-built: a shape the driver cannot recognise is worse
     * than no shape, because it invites them to read corners into GPS noise. When
     * [CoachMap] declines to produce one, the card disappears and the insights below are
     * left untouched - they are still true, they just lose their picture.
     */
    private fun updateSectorMap(state: CoachTelemetryViewModel.MapState) {
        when (state) {
            is CoachTelemetryViewModel.MapState.Ready -> {
                binding.sectorMapCard.visibility = View.VISIBLE
                binding.sectorMap.setDrawing(state.drawing)
                updateMapNote(state.drawing)
            }
            CoachTelemetryViewModel.MapState.Loading,
            CoachTelemetryViewModel.MapState.Unavailable -> {
                binding.sectorMapCard.visibility = View.GONE
                binding.sectorMap.setDrawing(null)
            }
        }
    }

    /**
     * Says where the drawn shape came from, when that changes what it can be trusted for.
     *
     * A surveyed circuit needs no note. A shape derived from the driver's own laps does,
     * because it follows the line they drove rather than the edges of the road, and a
     * shape from a single lap needs a stronger one still.
     */
    private fun updateMapNote(drawing: CoachMap.Drawing) {
        val note = when (drawing.provenance) {
            CoachMap.Provenance.SURVEYED_CENTRELINE -> null
            CoachMap.Provenance.DERIVED_FROM_LAPS -> R.string.coach_map_note_derived
            CoachMap.Provenance.SINGLE_LAP -> R.string.coach_map_note_single_lap
        }
        if (note == null) {
            binding.sectorMapNote.visibility = View.GONE
        } else {
            binding.sectorMapNote.setText(note)
            binding.sectorMapNote.visibility = View.VISIBLE
        }
    }

    private fun updateUI(state: SessionUiState) {
        // Update consistency score
        binding.consistencyScore.text = String.format("%.1f%%", state.consistencyScore)
        
        // Update session summary
        val lapCount = state.laps.size
        val bestLapTime = state.bestLap?.let { formatLapTime(it.durationMs) } ?: "N/A"
        binding.sessionSummary.text = "$lapCount laps completed\nBest lap: $bestLapTime"

        // Determine which insights to show based on preference
        val hasLocalInsights = state.insights.any { it.isLocalOnly }
        val hasAiInsights = state.insights.any { !it.isLocalOnly }
        val showingLocal = state.coachingPreference == CoachingPreference.LOCAL || !hasAiInsights
        
        // Update coaching type badge
        binding.coachingTypeBadge.text = if (showingLocal && hasLocalInsights) "OFFLINE INSIGHTS" else "AI COACHING"
        
        // Show AI available banner when:
        // - User is viewing local insights AND AI insights exist AND preference is LOCAL
        val showAiBanner = hasLocalInsights && hasAiInsights && state.coachingPreference == CoachingPreference.LOCAL
        binding.aiAvailableBanner.visibility = if (showAiBanner) View.VISIBLE else View.GONE
        
        // Show upsell card when:
        // - Only local insights exist (no AI yet) AND session is offline/pending upload
        val showUpsell = hasLocalInsights && !hasAiInsights && !state.isLoading
        binding.upsellCard.visibility = if (showUpsell) View.VISIBLE else View.GONE

        // Filter insights based on preference
        val insightsToShow = when {
            showingLocal -> state.insights.filter { it.isLocalOnly }
            else -> state.insights.filter { !it.isLocalOnly }
        }

        // Asked for once the session itself has loaded, because the file path and the
        // lap windows both arrive with it. Deliberately outside the branches below: a
        // session with no insights yet still has laps whose sector times may need
        // correcting (OC-31), and the LAPS tab is already showing them.
        if (!state.isLoading) {
            mapViewModel.load(
                sessionId = state.session?.id ?: 0L,
                telemetryFilePath = state.rawFilePath,
                trackId = state.session?.trackId,
                laps = state.laps
            )
        }

        // The fundamentals stand on their own, so they are shown whatever the insights
        // are doing - only hidden while the session itself is still loading.
        binding.fundamentalsSection.visibility = if (state.isLoading) View.GONE else View.VISIBLE

        // Handle loading/empty states
        when {
            state.isLoading -> {
                binding.loadingContainer.visibility = View.VISIBLE
                binding.insightsContainer.visibility = View.GONE
                binding.emptyStateText.visibility = View.GONE
                binding.headerCard.visibility = View.GONE
                binding.upsellCard.visibility = View.GONE
                binding.sectorCaveat.visibility = View.GONE
                binding.sectorMapCard.visibility = View.GONE
            }
            insightsToShow.isEmpty() && state.insights.isEmpty() -> {
                binding.loadingContainer.visibility = View.GONE
                binding.insightsContainer.visibility = View.GONE
                binding.emptyStateText.visibility = View.VISIBLE
                binding.headerCard.visibility = View.VISIBLE
                binding.sectorCaveat.visibility = View.GONE
                binding.sectorMapCard.visibility = View.GONE
            }
            else -> {
                binding.loadingContainer.visibility = View.GONE
                binding.insightsContainer.visibility = View.VISIBLE
                binding.emptyStateText.visibility = View.GONE
                binding.headerCard.visibility = View.VISIBLE
                populateInsights(insightsToShow)
                updateSectorCaveat(state)
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

    /**
     * Shows how sectors were derived, but only when the driver is actually being shown
     * something that rests on them.
     *
     * The caveat is tied to the laps carrying sector times rather than to the insight
     * text, because matching on wording would quietly stop working the first time an
     * insight is reworded - and it would fail by hiding the caveat, which is the
     * direction that costs the driver rather than merely looking wrong.
     */
    private fun updateSectorCaveat(state: SessionUiState) {
        val sectorsShown = state.laps.isNotEmpty() && state.laps.all {
            it.sector1Ms > 0 && it.sector2Ms > 0 && it.sector3Ms > 0
        }
        binding.sectorCaveat.visibility = if (sectorsShown) View.VISIBLE else View.GONE
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
