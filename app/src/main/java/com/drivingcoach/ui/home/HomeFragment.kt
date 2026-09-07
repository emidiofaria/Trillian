package com.drivingcoach.ui.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.drivingcoach.R
import com.drivingcoach.data.location.GpsReadiness
import com.drivingcoach.databinding.FragmentHomeBinding
import com.drivingcoach.databinding.ItemSessionHistoryBinding
import com.drivingcoach.util.LapTimeFormatter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.abs

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    
    private var trackNameDialog: androidx.appcompat.app.AlertDialog? = null

    private val sessionsAdapter = SessionHistoryAdapter(
        onSessionClick = { sessionId -> viewModel.onSessionClick(sessionId) },
        onSessionLongClick = { session -> showSessionContextMenu(session) }
    )

    private companion object {
        /** Collapse ratio at which the pinned brand bar starts fading in. */
        const val COLLAPSE_FADE_START = 0.6f
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    /**
     * Warm-up starts with Home and is deliberately *not* stopped when Home stops.
     *
     * Binding it to this screen's visibility made the Home → Track Setup navigation its own
     * stop condition, so the fix the user had just waited for was discarded at the moment it
     * was needed (Incident 12). Releasing the chip is now owned by the task: the app leaving
     * the foreground (`DrivingCoachApp`), recording starting
     * (`TelemetryForegroundService`), or the idle ceiling (SRS TS-16, TS-18).
     */
    override fun onStart() {
        super.onStart()
        viewModel.startGpsWarmUp()
    }

    private fun setupUI() {
        // Setup RecyclerView
        binding.sessionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = sessionsAdapter
        }

        // Profile button
        binding.profileButton.setOnClickListener {
            viewModel.onProfileClick()
        }

        // Start new session
        binding.startSessionButton.setOnClickListener {
            showTrackNameDialog()
        }
        
        // Dismiss upload banner
        binding.dismissBannerButton.setOnClickListener {
            viewModel.dismissUploadBanner()
        }

        setupHeroCollapse()
    }

    /**
     * Cross-fades the pinned brand bar in as the hero collapses, so the two never
     * overlap visually (SRS UI-06).
     */
    private fun setupHeroCollapse() {
        binding.appBarLayout.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                val range = appBarLayout.totalScrollRange
                if (range == 0) return@OnOffsetChangedListener

                val collapseRatio = abs(verticalOffset).toFloat() / range
                binding.heroContent.alpha = (1f - collapseRatio * 1.6f).coerceIn(0f, 1f)
                binding.collapsedBrand.alpha =
                    ((collapseRatio - COLLAPSE_FADE_START) / (1f - COLLAPSE_FADE_START))
                        .coerceIn(0f, 1f)
            }
        )
    }

    private fun showTrackNameDialog() {
        // Dismiss any existing dialog to prevent leaks
        trackNameDialog?.dismiss()
        
        val editText = EditText(requireContext()).apply {
            hint = "e.g. Circuito de Braga"
            setPadding(64, 32, 64, 32)
            setTextColor(0xFFFFFFFF.toInt())  // White
            setHintTextColor(0xFF888888.toInt())  // Gray
        }

        trackNameDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Session")
            .setMessage("Enter the track name:")
            .setView(editText)
            .setPositiveButton("Start") { _, _ ->
                val trackName = editText.text.toString().trim()
                viewModel.startNewSession(trackName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSessionContextMenu(session: SessionSummary) {
        val options = arrayOf("Rename", "Delete")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(session.trackName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(session)
                    1 -> showDeleteConfirmation(session)
                }
            }
            .show()
    }

    private fun showDeleteConfirmation(session: SessionSummary) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Session")
            .setMessage("Delete \"${session.trackName}\" from ${HomeViewModel.formatDate(session.startedAt)}?\n\nThis will remove the session, all lap data, and coaching insights. This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSession(session.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog(session: SessionSummary) {
        val editText = EditText(requireContext()).apply {
            setText(session.trackName)
            setSelection(session.trackName.length)
            setPadding(64, 32, 64, 32)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            hint = "Track name"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename Session")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString()
                viewModel.renameSession(session.id, newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        updateUI(state)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        handleEvent(event)
                    }
                }
                launch {
                    viewModel.gpsReadiness.collect { readiness ->
                        updateGpsChip(readiness)
                    }
                }
            }
        }
    }

    /**
     * Turns the warm-up into something the user can act on: the point of the chip is that
     * they learn to walk out to the start line when it goes green, instead of discovering a
     * cold fix while standing at the track edge.
     *
     * Hidden while idle, which is also what a user without location permission sees — the
     * hero then looks exactly as it did before this feature.
     */
    private fun updateGpsChip(readiness: GpsReadiness) {
        val chip = binding.gpsReadinessChip

        when (readiness) {
            is GpsReadiness.Idle -> {
                chip.isVisible = false
                return
            }

            is GpsReadiness.Acquiring -> {
                chip.text = getString(R.string.gps_chip_acquiring)
                chip.contentDescription = getString(R.string.gps_chip_acquiring_description)
                chip.setChipColor(R.color.colorWarning)
            }

            is GpsReadiness.Ready -> {
                chip.text = getString(R.string.gps_chip_ready, readiness.accuracyM)
                chip.contentDescription =
                    getString(R.string.gps_chip_ready_description, readiness.accuracyM)
                chip.setChipColor(R.color.colorSuccess)
            }
        }

        chip.isVisible = true
    }

    private fun TextView.setChipColor(@ColorRes colorRes: Int) {
        val color = ContextCompat.getColor(requireContext(), colorRes)
        setTextColor(color)
        TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(color))
    }

    private fun updateUI(state: HomeUiState) {
        // Update hero card
        if (state.overallBestLap != null) {
            binding.bestLapTime.text = LapTimeFormatter.formatLapTime(state.overallBestLap.lapTimeMs)
            binding.bestLapInfo.text = "${state.overallBestLap.trackName} • ${HomeViewModel.formatDate(state.overallBestLap.date)}"
            binding.bestLapInfo.visibility = View.VISIBLE
        } else {
            binding.bestLapTime.text = "—"
            binding.bestLapInfo.visibility = View.GONE
        }

        // Update sessions list
        sessionsAdapter.submitList(state.sessions)

        // Show/hide empty state
        val isEmpty = state.sessions.isEmpty() && !state.isLoading
        binding.emptyStateContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.sessionsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        
        // Show/hide upload pending banner
        binding.uploadBanner.visibility = if (state.hasStaleUploads) View.VISIBLE else View.GONE
    }

    private fun handleEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.NavigateToRecording -> {
                val action = HomeFragmentDirections.actionHomeToRecording(event.sessionId)
                findNavController().navigate(action)
            }
            is HomeEvent.NavigateToSessionResult -> {
                val action = HomeFragmentDirections.actionHomeToSessionResult(event.sessionId)
                findNavController().navigate(action)
            }
            is HomeEvent.NavigateToTrackSetup -> {
                val action = HomeFragmentDirections.actionHomeToTrackSetup(event.trackName)
                findNavController().navigate(action)
            }
            is HomeEvent.NavigateToProfile -> {
                val action = HomeFragmentDirections.actionHomeToProfile()
                findNavController().navigate(action)
            }
            is HomeEvent.ShowError -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
            }
            is HomeEvent.ShowSessionDeleted -> {
                Snackbar.make(binding.root, "\"${event.trackName}\" deleted", Snackbar.LENGTH_SHORT).show()
            }
            is HomeEvent.ShowSessionRenamed -> {
                Snackbar.make(binding.root, "Renamed to \"${event.newName}\"", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        trackNameDialog?.dismiss()
        trackNameDialog = null
        _binding = null
    }
}

private class SessionHistoryAdapter(
    private val onSessionClick: (Long) -> Unit,
    private val onSessionLongClick: (SessionSummary) -> Unit
) : ListAdapter<SessionSummary, SessionHistoryAdapter.SessionViewHolder>(SessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemSessionHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SessionViewHolder(
        private val binding: ItemSessionHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onSessionClick(getItem(position).id)
                }
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onSessionLongClick(getItem(position))
                    true
                } else {
                    false
                }
            }
        }

        fun bind(session: SessionSummary) {
            binding.trackName.text = session.trackName
            binding.sessionDate.text = HomeViewModel.formatDate(session.startedAt)
            
            binding.bestLapTime.text = if (session.bestLapMs != null) {
                LapTimeFormatter.formatLapTime(session.bestLapMs)
            } else {
                "—"
            }
            
            binding.sessionStats.text = "${session.lapCount} laps • ${String.format("%.1f%%", session.consistencyScore)}"
        }
    }
}

private class SessionDiffCallback : DiffUtil.ItemCallback<SessionSummary>() {
    override fun areItemsTheSame(oldItem: SessionSummary, newItem: SessionSummary): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: SessionSummary, newItem: SessionSummary): Boolean {
        return oldItem == newItem
    }
}
