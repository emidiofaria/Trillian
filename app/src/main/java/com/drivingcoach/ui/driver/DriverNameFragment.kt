package com.drivingcoach.ui.driver

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.drivingcoach.R
import com.drivingcoach.databinding.FragmentDriverNameBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * First-run driver naming (SRS DR-02 … DR-05).
 *
 * V1 has no accounts, so this screen — not Login — is where a new driver enters the app. It
 * asks for one thing, remembers it, and gets out of the way. The name can be changed later
 * from Profile, so nothing here is a decision the driver is stuck with.
 *
 * Navigating away clears the back stack, so pressing back from Home exits the app rather
 * than returning to setup (SRS UI-05).
 */
@AndroidEntryPoint
class DriverNameFragment : Fragment() {

    private var _binding: FragmentDriverNameBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DriverNameViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDriverNameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.driverNameInput.doAfterTextChanged { text ->
            viewModel.onNameChanged(text?.toString().orEmpty())
        }

        binding.letsRaceButton.setOnClickListener {
            viewModel.onSubmit(binding.driverNameInput.text?.toString().orEmpty())
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect(::render)
                }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
            }
        }
    }

    private fun render(state: DriverNameViewModel.UiState) {
        binding.letsRaceButton.isEnabled = state.canSubmit && !state.isSaving
        binding.driverNameInputLayout.error = state.errorLabel?.let(::getString)

        // Reserving the error slot only while an error is showing keeps the button from
        // hopping down the screen the first time a name is rejected.
        binding.driverNameInputLayout.isErrorEnabled = state.errorLabel != null
    }

    private fun handleEvent(event: DriverNameViewModel.Event) {
        when (event) {
            DriverNameViewModel.Event.NavigateToHome -> {
                // Guard against a duplicate emission arriving after we have navigated.
                if (findNavController().currentDestination?.id != R.id.driverNameFragment) return
                findNavController().navigate(R.id.action_driver_name_to_home)
            }

            is DriverNameViewModel.Event.ShowError -> {
                Snackbar.make(
                    binding.root,
                    getString(event.messageLabel),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
