package com.drivingcoach.ui.tracksetup

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.drivingcoach.R
import com.drivingcoach.databinding.FragmentTrackSetupBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class TrackSetupFragment : Fragment() {

    private var _binding: FragmentTrackSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrackSetupViewModel by viewModels()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startLocationUpdates()
        } else {
            showPermissionDeniedMessage()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                currentLocation = location
                viewModel.updateGpsStatus(
                    accuracy = location.accuracy,
                    satelliteCount = if (location.extras?.containsKey("satellites") == true) {
                        location.extras?.getInt("satellites") ?: 0
                    } else {
                        // Estimate based on accuracy (emulator fallback)
                        when {
                            location.accuracy <= 5f -> 12
                            location.accuracy <= 10f -> 8
                            location.accuracy <= 20f -> 5
                            else -> 3
                        }
                    }
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupUI()
        observeViewModel()
        checkPermissionsAndStart()
    }

    private fun setupUI() {
        binding.capturePointAButton.setOnClickListener {
            currentLocation?.let { location ->
                viewModel.setPointA(location)
            }
        }

        binding.capturePointBButton.setOnClickListener {
            currentLocation?.let { location ->
                viewModel.setPointB(location)
            }
        }

        binding.clearButton.setOnClickListener {
            viewModel.clear()
        }

        binding.startRecordingButton.setOnClickListener {
            navigateToRecording()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: TrackSetupState) {
        // GPS status
        updateGpsStatus(state)

        // Point A
        binding.capturePointAButton.isEnabled = state.isGpsReady
        if (state.pointA != null) {
            binding.pointACoords.text = formatCoords(state.pointA)
            binding.pointACoords.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_primary)
            )
        } else {
            binding.pointACoords.text = "Not captured"
            binding.pointACoords.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.textSecondary)
            )
        }

        // Point B
        binding.capturePointBButton.isEnabled = state.isGpsReady && state.pointA != null
        if (state.pointB != null) {
            binding.pointBCoords.text = formatCoords(state.pointB)
            binding.pointBCoords.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_primary)
            )
        } else {
            binding.pointBCoords.text = "Not captured"
            binding.pointBCoords.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.textSecondary)
            )
        }

        // Distance
        if (state.pointA != null && state.pointB != null) {
            binding.distanceValue.text = String.format(Locale.US, "%.1fm", state.distance)
            binding.distanceHint.isVisible = !state.isValid
        } else {
            binding.distanceValue.text = "--"
            binding.distanceHint.isVisible = false
        }

        // Buttons
        binding.clearButton.isVisible = state.pointA != null
        binding.startRecordingButton.isEnabled = state.isValid
    }

    private fun updateGpsStatus(state: TrackSetupState) {
        val indicator = binding.gpsIndicator.background as? GradientDrawable

        if (state.isGpsReady) {
            indicator?.setColor(ContextCompat.getColor(requireContext(), R.color.colorSuccess))
            binding.gpsStatusText.text = String.format(
                Locale.US,
                "GPS: %d satellites, ±%.0fm",
                state.satelliteCount,
                state.gpsAccuracy
            )
            binding.gpsStatusText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.colorSuccess)
            )
        } else {
            indicator?.setColor(ContextCompat.getColor(requireContext(), R.color.colorError))
            binding.gpsStatusText.text = "Acquiring GPS..."
            binding.gpsStatusText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.colorError)
            )
        }
    }

    private fun formatCoords(latLng: LatLng): String {
        return String.format(Locale.US, "%.5f, %.5f", latLng.latitude, latLng.longitude)
    }

    private fun checkPermissionsAndStart() {
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) !=
                PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startLocationUpdates()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // 1 second interval
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setWaitForAccurateLocation(false)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun showPermissionDeniedMessage() {
        Snackbar.make(
            binding.root,
            "Location permission is required for track setup",
            Snackbar.LENGTH_LONG
        ).show()
        findNavController().popBackStack()
    }

    private fun navigateToRecording() {
        val coords = viewModel.getStartLineCoords() ?: return

        // Navigate to recording with start line coords
        val action = TrackSetupFragmentDirections
            .actionTrackSetupToRecording(
                sessionId = -1L, // Will be created by RecordingFragment
                startLineLat1 = coords.lat1.toFloat(),
                startLineLng1 = coords.lng1.toFloat(),
                startLineLat2 = coords.lat2.toFloat(),
                startLineLng2 = coords.lng2.toFloat()
            )
        findNavController().navigate(action)
    }

    override fun onStop() {
        super.onStop()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
