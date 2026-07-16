package com.drivingcoach.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.drivingcoach.databinding.FragmentHomeBinding
import com.drivingcoach.databinding.ItemSessionHistoryBinding
import com.drivingcoach.util.LapTimeFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    
    private var trackNameDialog: androidx.appcompat.app.AlertDialog? = null

    private val sessionsAdapter = SessionHistoryAdapter { sessionId ->
        viewModel.onSessionClick(sessionId)
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

        // FAB: Start new session
        binding.startSessionFab.setOnClickListener {
            showTrackNameDialog()
        }
        
        // Dismiss upload banner
        binding.dismissBannerButton.setOnClickListener {
            viewModel.dismissUploadBanner()
        }
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
            }
        }
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
    private val onSessionClick: (Long) -> Unit
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
