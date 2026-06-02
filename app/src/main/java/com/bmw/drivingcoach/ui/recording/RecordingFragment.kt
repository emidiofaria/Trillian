package com.bmw.drivingcoach.ui.recording

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bmw.drivingcoach.R
import com.bmw.drivingcoach.databinding.FragmentRecordingBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecordingFragment : Fragment() {

    private var _binding: FragmentRecordingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecordingViewModel by viewModels()
    private val args: RecordingFragmentArgs by navArgs()
    
    private var hasNavigatedToResult = false
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startRecordingIfReady()
        } else {
            showPermissionDeniedMessage()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        observeViewModel()
        checkPermissionsAndStart()
    }
    
    override fun onStart() {
        super.onStart()
        viewModel.bindToService()
    }
    
    override fun onStop() {
        super.onStop()
        viewModel.unbindFromService()
    }

    private fun setupUI() {
        binding.stopRecordingButton.setOnClickListener {
            viewModel.stopRecording()
        }
        
        // Initial state
        binding.elapsedTime.text = formatElapsedTime(0L)
        updateGpsStatus(false)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    updateUI(state)
                }
            }
        }
    }
    
    private fun updateUI(state: RecordingUiState) {
        // Update elapsed time
        binding.elapsedTime.text = formatElapsedTime(state.elapsedMs)
        
        // Update GPS status
        updateGpsStatus(state.gpsLocked)
        
        // Update recording indicator
        if (state.isRecording) {
            binding.recordingStatusText.text = "RECORDING"
            binding.recordingStatusText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.colorSuccess)
            )
        } else if (state.isStopping) {
            binding.recordingStatusText.text = "STOPPING..."
            binding.recordingStatusText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.colorOnSurfaceVariant)
            )
        }
        
        // Handle error
        state.error?.let { error ->
            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
        
        // Navigate to result when stopping completes
        if (state.isStopping && !hasNavigatedToResult) {
            hasNavigatedToResult = true
            navigateToSessionResult(state.sessionId)
        }
    }
    
    private fun updateGpsStatus(locked: Boolean) {
        val indicator = binding.gpsIndicator.background as? GradientDrawable
        
        if (locked) {
            indicator?.setColor(ContextCompat.getColor(requireContext(), R.color.colorSuccess))
            binding.gpsStatusText.text = "GPS LOCKED"
            binding.gpsStatusText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.colorSuccess)
            )
        } else {
            indicator?.setColor(ContextCompat.getColor(requireContext(), R.color.colorError))
            binding.gpsStatusText.text = "ACQUIRING GPS..."
            binding.gpsStatusText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.colorError)
            )
        }
    }
    
    private fun formatElapsedTime(elapsedMs: Long): String {
        val minutes = (elapsedMs / 60000).toInt()
        val seconds = ((elapsedMs % 60000) / 1000).toInt()
        val millis = ((elapsedMs % 1000)).toInt()
        return String.format("%02d:%02d.%03d", minutes, seconds, millis)
    }
    
    private fun checkPermissionsAndStart() {
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) != 
                PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isEmpty()) {
            startRecordingIfReady()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    
    private fun startRecordingIfReady() {
        val sessionId = args.sessionId
        if (sessionId != -1L) {
            viewModel.startRecording(sessionId)
        } else {
            Snackbar.make(binding.root, "Invalid session", Snackbar.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }
    
    private fun showPermissionDeniedMessage() {
        Snackbar.make(
            binding.root,
            "Location permission is required to record",
            Snackbar.LENGTH_LONG
        ).show()
        findNavController().popBackStack()
    }
    
    private fun navigateToSessionResult(sessionId: Long) {
        if (sessionId != -1L) {
            val action = RecordingFragmentDirections
                .actionRecordingToSessionResult(sessionId)
            findNavController().navigate(action)
        } else {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

