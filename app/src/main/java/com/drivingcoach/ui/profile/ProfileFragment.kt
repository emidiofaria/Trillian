package com.drivingcoach.ui.profile

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
import com.drivingcoach.R
import com.drivingcoach.databinding.FragmentProfileBinding
import com.drivingcoach.util.LapTimeFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        binding.signOutButton.setOnClickListener {
            showSignOutConfirmation()
        }
    }

    private fun showSignOutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out? Your local data will be cleared.")
            .setPositiveButton("Sign Out") { _, _ ->
                viewModel.signOut()
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

    private fun updateUI(state: ProfileUiState) {
        // Update profile info
        state.profile?.let { profile ->
            binding.avatarInitials.text = profile.initials
            binding.userName.text = profile.displayName
            binding.userEmail.text = profile.email
        }

        // Update stats
        binding.totalSessions.text = state.stats.totalSessions.toString()
        binding.totalLaps.text = state.stats.totalLaps.toString()
        binding.bestLapTime.text = state.stats.bestLapMs?.let { 
            formatShortLapTime(it)
        } ?: "—"
    }

    private fun formatShortLapTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val tenths = (ms % 1000) / 100
        return String.format("%d:%02d.%d", minutes, seconds, tenths)
    }

    private fun handleEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.NavigateToLogin -> {
                // Navigate to login and clear back stack
                findNavController().navigate(R.id.loginFragment, null,
                    androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .build()
                )
            }
            is ProfileEvent.ShowError -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
