package com.drivingcoach.ui.splash

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
import com.drivingcoach.databinding.FragmentSplashBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Branded loading screen shown on cold start (SRS UI-01 … UI-05).
 *
 * Owns the startup destination decision that previously lived in `MainActivity` behind a
 * `runBlocking` DataStore read. Every navigation out of here pops the splash off the back
 * stack, so pressing back from the first functional screen exits the app (SRS UI-05).
 */
@AndroidEntryPoint
class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SplashViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.splashRoot.setOnClickListener { viewModel.skip() }

        observeState()
        viewModel.start()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressIndicator.setProgressCompat(state.progress, true)
                    binding.progressLabel.text =
                        getString(R.string.splash_progress_format, state.progress)
                    binding.progressLabel.contentDescription =
                        getString(state.stepLabel)

                    state.destination?.let(::navigateTo)
                }
            }
        }
    }

    private fun navigateTo(destination: SplashDestination) {
        // Guard against a second emission arriving after we have already navigated.
        if (findNavController().currentDestination?.id != R.id.splashFragment) return

        val action = when (destination) {
            SplashDestination.ONBOARDING -> R.id.action_splash_to_onboarding
            SplashDestination.LOGIN -> R.id.action_splash_to_login
            SplashDestination.HOME -> R.id.action_splash_to_home
        }
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
