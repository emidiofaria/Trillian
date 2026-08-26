package com.drivingcoach.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.drivingcoach.BuildConfig
import com.drivingcoach.R
import com.drivingcoach.databinding.FragmentAboutBinding

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
class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
