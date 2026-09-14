package com.drivingcoach.ui.tracksetup

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
import com.drivingcoach.databinding.FragmentTrackSetupBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class TrackSetupFragment : Fragment() {

    private var _binding: FragmentTrackSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrackSetupViewModel by viewModels()
    
    private val args: TrackSetupFragmentArgs by navArgs()

    @Inject
    lateinit var locationUpdates: LocationUpdates

    @Inject
    lateinit var locationWarmUp: LocationWarmUp

    private var currentLocation: Location? = null

    /**
     * The permission prompt is asked at most once per screen visit. Without this, a denial
     * would re-prompt every time the screen restarts — including the restart that follows
     * the denial dialog itself — trapping the user in a loop.
     */
    private var permissionRequested = false

    /** Guards against two collectors being started for the same view. */
    private var collecting = false

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
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
        _binding = FragmentTrackSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        observeViewModel()
        checkPermissionsAndStart()
    }

    /**
     * Keeps the warm-up running while the user is at the line.
     *
     * Idempotent, so this costs nothing when the user arrived from Home with a fix already
     * acquired — the common case. It matters when they did not: returning to Track Setup
     * after process death, or arriving without passing through Home, would otherwise leave
     * the chip cold with nothing extending the bound (Incident 12).
     */
    override fun onStart() {
        super.onStart()
        locationWarmUp.start()
    }

    private fun setupUI() {
        binding.capturePointAButton.setOnClickListener {
            captureWithFreshFix { location -> viewModel.setPointA(location) }
        }

        binding.capturePointBButton.setOnClickListener {
            captureWithFreshFix { location -> viewModel.setPointB(location) }
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
            binding.gpsStatusText.text = if (state.isAwaitingFreshFix) {
                // Distinct from "Acquiring": the receiver is working, the position it is
                // holding simply is not current (Incident 12, F4).
                "Getting a current GPS fix..."
            } else {
                "Acquiring GPS..."
            }
            binding.gpsStatusText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.colorError)
            )
        }
    }

    /**
     * Last line of defence for the start line (Incident 12, F4).
     *
     * [onLocation] already rejects old fixes as they arrive, but updates can also simply
     * *stop* — a lost signal leaves the last good fix in hand and the button enabled. Checking
     * again at the moment of capture closes that window, and it is the only check the user's
     * lap times ultimately depend on.
     */
    private fun captureWithFreshFix(capture: (Location) -> Unit) {
        val location = currentLocation
        if (location == null || !FixFreshness.isFresh(location)) {
            currentLocation = null
            viewModel.markAwaitingFreshFix()
            Snackbar.make(
                binding.root,
                "Waiting for a current GPS fix — hold still a moment",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        capture(location)
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
            collectLocationUpdates()
        } else if (!permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    /**
     * Collects fixes for as long as the screen is STARTED.
     *
     * [repeatOnLifecycle] restarts the collection on every return to STARTED, which is the
     * whole point: subscribing once at view creation meant a screen-off, notification pull
     * or app switch tore the updates down for good, leaving "Acquiring GPS..." on screen
     * forever rather than for 45 seconds (SRS TS-15).
     *
     * Guarded so the permission callback and [onViewCreated] cannot both start a collector.
     */
    private fun collectLocationUpdates() {
        if (collecting) return
        collecting = true

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Whatever was on screen before the screen went away describes where the user
                // was then, not now. Discarding it on every return is what stops a position
                // held across a pocketed phone from being captured as a start line.
                currentLocation = null
                viewModel.markAwaitingFreshFix()

                locationUpdates.positionUpdates(LOCATION_INTERVAL_MS).collect(::onLocation)
            }
        }
    }

    /**
     * Accepts a fix only if it describes where the user is *now* (Incident 12, F4).
     *
     * The fused client may answer a fresh subscription with a cached position, and the
     * warm-up now legitimately holds the chip across the walk from the paddock, so an old
     * fix reaching this screen is an ordinary event rather than an exotic one. Captured as
     * Point A it would offset every lap time in the session by the same amount, and the
     * times would still look like lap times — a wrong number that looks right.
     *
     * A rejected fix is dropped rather than displayed, and capture is withdrawn until a
     * current one lands. See [TrackSetupViewModel.markAwaitingFreshFix] for why that is a
     * wait and not a block.
     */
    private fun onLocation(location: Location) {
        if (!FixFreshness.isFresh(location)) {
            currentLocation = null
            viewModel.markAwaitingFreshFix()
            return
        }

        currentLocation = location
        viewModel.updateGpsStatus(
            accuracy = location.accuracy,
            satelliteCount = satelliteCountOf(location)
        )
    }

    /**
     * Fused location does not carry a satellite count on every device, and emulators never
     * do, so accuracy stands in for it (SRS TS-03). The displayed count is indicative; the
     * capture gate is driven by accuracy, which is always present.
     */
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
            "Location permission is required for track setup",
            Snackbar.LENGTH_LONG
        ).show()
        findNavController().popBackStack()
    }

    private fun navigateToRecording() {
        val coords = viewModel.getStartLineCoords() ?: return

        // Navigate to recording with start line coords and track name
        val action = TrackSetupFragmentDirections
            .actionTrackSetupToRecording(
                sessionId = -1L, // Will be created by RecordingFragment
                trackName = args.trackName,
                startLineLat1 = coords.lat1.toFloat(),
                startLineLng1 = coords.lng1.toFloat(),
                startLineLat2 = coords.lat2.toFloat(),
                startLineLng2 = coords.lng2.toFloat()
            )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // The collector is scoped to the view lifecycle, so it is already gone; the flag has
        // to follow it or a recreated view would never resubscribe.
        collecting = false
        _binding = null
    }

    private companion object {
        const val LOCATION_INTERVAL_MS = 1000L
        const val SATELLITES_KEY = "satellites"
    }
}
