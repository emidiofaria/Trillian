package com.drivingcoach.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.drivingcoach.BuildConfig
import com.drivingcoach.R
import com.drivingcoach.data.location.GpsAcquisition
import com.drivingcoach.data.location.GpsAcquisitionMetricsStore
import com.drivingcoach.databinding.FragmentAboutBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Permanent home for the brand identity and build identity (SRS UI-11, UI-12).
 *
 * The engineering manifesto also appears on the loading screen, but that screen is
 * transient by design — it must not be the only place this text can be read. Both surfaces
 * bind the *same* string resources so they cannot drift apart.
 *
 * The version is read from [BuildConfig] rather than hardcoded, so it derives from the same
 * single source of truth that names the APK.
 */
@AndroidEntryPoint
class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var gpsMetrics: GpsAcquisitionMetricsStore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        binding.versionValue.text = getString(
            R.string.about_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )

        observeGpsMetrics()
    }

    /**
     * Surfaces how long the last GPS acquisition actually took (SRS TS-19). Without this the
     * only evidence lives in logcat, which is useless at the one place the number is
     * produced: a racetrack, with no laptop.
     */
    private fun observeGpsMetrics() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                gpsMetrics.lastAcquisition.collect(::renderGpsMetrics)
            }
        }
    }

    private fun renderGpsMetrics(acquisition: GpsAcquisition?) {
        binding.gpsValue.text = if (acquisition == null) {
            getString(R.string.about_gps_never)
        } else {
            getString(
                R.string.about_gps_format,
                formatDuration(acquisition.timeToFirstFixMs),
                formatDuration(acquisition.timeToAccurateFixMs)
            )
        }
    }

    /** A null milestone means the warm-up ended before that accuracy was ever reached. */
    private fun formatDuration(durationMs: Long?): String = durationMs
        ?.let { getString(R.string.about_gps_seconds_format, it / 1000f) }
        ?: getString(R.string.about_gps_pending)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
