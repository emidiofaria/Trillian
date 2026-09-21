package com.drivingcoach.ui.tracklist

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
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
import androidx.navigation.fragment.navArgs
import com.drivingcoach.R
import com.drivingcoach.data.location.FixFreshness
import com.drivingcoach.data.location.LocationUpdates
import com.drivingcoach.data.location.LocationWarmUp
import com.drivingcoach.databinding.FragmentTrackConfirmBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * The last screen before recording on a known circuit.
 *
 * Its job is not decoration. Selecting a saved circuit skips Track Setup entirely,
 * and Track Setup is where the app refused to start a session on a cold or stale
 * GPS fix (SRS TS-04, TS-21, TS-22). Those gates live here for that path, so the
 * shortcut saves the walk to the start line without also removing the check that
 * the position is trustworthy.
 */
@AndroidEntryPoint
class TrackConfirmFragment : Fragment() {

    private var _binding: FragmentTrackConfirmBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrackConfirmViewModel by viewModels()
    private val args: TrackConfirmFragmentArgs by navArgs()

    @Inject
    lateinit var locationUpdates: LocationUpdates

    @Inject
    lateinit var locationWarmUp: LocationWarmUp

    private var permissionRequested = false
    private var collecting = false

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            collectLocationUpdates()
        } else {
            showPermissionDeniedMessage()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.load(args.trackId)

        binding.startRecordingButton.setOnClickListener {
            findNavController().navigate(
                TrackConfirmFragmentDirections.actionTrackConfirmToRecording(
                    sessionId = -1L,
                    trackName = args.sessionName,
                    trackId = args.trackId
                )
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest(::render)
            }
        }

        checkPermissionsAndStart()
    }

    override fun onStart() {
        super.onStart()
        locationWarmUp.start()
    }

    private fun render(state: TrackConfirmState) {
        binding.trackName.text = state.trackName.uppercase(Locale.getDefault())
        binding.trackLocation.text = state.location.orEmpty()
        binding.trackLocation.isVisible = !state.location.isNullOrBlank()
        binding.startLineSummary.text = state.startLineSummary
        binding.trackFacts.text = state.facts
        binding.trackFacts.isVisible = state.facts.isNotEmpty()
        binding.startRecordingButton.isEnabled = state.canStart

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
            binding.gpsStatusText.text = if (state.isAwaitingFreshFix) {
                "Getting a current GPS fix..."
            } else {
                "Acquiring GPS..."
            }
            binding.gpsStatusText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.colorError)
            )
        }
    }

    private fun checkPermissionsAndStart() {
        val missing = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) !=
                PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            collectLocationUpdates()
        } else if (!permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun collectLocationUpdates() {
        if (collecting) return
        collecting = true

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.markAwaitingFreshFix()
                locationUpdates.positionUpdates(LOCATION_INTERVAL_MS).collect(::onLocation)
            }
        }
    }

    private fun onLocation(location: Location) {
        if (!FixFreshness.isFresh(location)) {
            viewModel.markAwaitingFreshFix()
            return
        }
        viewModel.updateGpsStatus(location.accuracy, satelliteCountOf(location))
    }

    /** Fused location omits the satellite count on many devices; accuracy stands in (SRS TS-03). */
    private fun satelliteCountOf(location: Location): Int {
        val reported = location.extras?.takeIf { it.containsKey(SATELLITES_KEY) }
            ?.getInt(SATELLITES_KEY)
        if (reported != null && reported > 0) return reported

        return when {
            location.accuracy <= 5f -> 12
            location.accuracy <= 10f -> 8
            location.accuracy <= 20f -> 5
            else -> 3
        }
    }

    private fun showPermissionDeniedMessage() {
        Snackbar.make(
            binding.root,
            "Location permission is required to record a session",
            Snackbar.LENGTH_LONG
        ).show()
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        collecting = false
        _binding = null
    }

    private companion object {
        const val LOCATION_INTERVAL_MS = 1000L
        const val SATELLITES_KEY = "satellites"
    }
}
