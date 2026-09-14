package com.drivingcoach.ui.profile

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.drivingcoach.R
import com.drivingcoach.data.profile.DriverProfileStore
import com.drivingcoach.databinding.FragmentProfileBinding
import com.drivingcoach.util.LapTimeFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
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

        // The name is a control, not a label — make that discoverable (SRS DR-06).
        binding.userName.setOnClickListener { showRenameDialog() }
        binding.editNameButton.setOnClickListener { showRenameDialog() }

        binding.clearDataButton.setOnClickListener {
            showClearDataConfirmation()
        }

        binding.aboutButton.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_about)
        }
    }

    /**
     * Rename dialog, pre-filled with the current name (SRS DR-06).
     *
     * The confirm button is gated on the same [DriverProfileStore] validation the first-run
     * screen uses, so there is exactly one definition of an acceptable name in the app.
     */
    private fun showRenameDialog() {
        val current = viewModel.uiState.value.profile?.displayName.orEmpty()

        val input = TextInputEditText(requireContext()).apply {
            setText(current)
            setSelection(text?.length ?: 0)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PERSON_NAME or
                InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(DriverProfileStore.MAX_NAME_LENGTH))
        }

        val container = FrameLayout(requireContext()).apply {
            val margin = resources.getDimensionPixelSize(R.dimen.spacing_lg)
            setPadding(margin, margin / 2, margin, 0)
            addView(input)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.driver_name_edit_title)
            .setView(container)
            .setPositiveButton(R.string.driver_name_edit_save) { _, _ ->
                viewModel.renameDriver(input.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.driver_name_edit_cancel, null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            fun refresh() {
                val validation = viewModel.validateName(input.text?.toString().orEmpty())
                saveButton.isEnabled = validation is DriverProfileStore.NameValidation.Valid
            }

            input.doAfterTextChanged { refresh() }
            refresh()
        }

        dialog.show()
    }

    /**
     * Confirmation for the irreversible wipe (SRS DR-07).
     *
     * The dialog names exactly what is destroyed and states that nothing is recoverable.
     * With no backend there is no second copy, so vague wording here would cost drivers
     * their entire track history.
     */
    private fun showClearDataConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_data_title)
            .setMessage(R.string.clear_data_message)
            .setPositiveButton(R.string.clear_data_confirm) { _, _ ->
                viewModel.clearUserData()
            }
            .setNegativeButton(R.string.clear_data_cancel, null)
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
            is ProfileEvent.NavigateToOnboarding -> {
                // A cleared app is a first-run app: permissions and naming are both gone,
                // so the driver rejoins the flow at its start (SRS DR-07).
                findNavController().navigate(R.id.onboardingFragment, null,
                    androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .build()
                )
            }
            is ProfileEvent.ShowMessage -> {
                Snackbar.make(
                    binding.root,
                    getString(event.messageLabel),
                    Snackbar.LENGTH_SHORT
                ).show()
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
