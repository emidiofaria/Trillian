package com.bmw.drivingcoach.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bmw.drivingcoach.R
import com.bmw.drivingcoach.databinding.FragmentOnboardingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingFragment : Fragment() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val requiredPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        
        // Check if all permissions are now granted (user may have granted in settings)
        if (areAllPermissionsGranted()) {
            completeOnboarding()
        }
    }

    private fun setupClickListeners() {
        binding.grantButton.setOnClickListener {
            requestPermissions()
        }

        binding.settingsButton.setOnClickListener {
            openAppSettings()
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val deniedPermissions = permissions.filterValues { !it }.keys
        
        if (deniedPermissions.isEmpty()) {
            // All permissions granted
            completeOnboarding()
        } else {
            // Some permissions denied
            showDeniedState(deniedPermissions)
        }
        
        updatePermissionStatus()
    }

    private fun showDeniedState(deniedPermissions: Set<String>) {
        binding.deniedContainer.visibility = View.VISIBLE
        binding.settingsButton.visibility = View.VISIBLE
        
        val deniedNames = deniedPermissions.map { permission ->
            when (permission) {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION -> "Location"
                Manifest.permission.ACTIVITY_RECOGNITION -> "Activity Recognition"
                else -> permission.substringAfterLast(".")
            }
        }.distinct()
        
        binding.deniedList.text = deniedNames.joinToString(", ")
    }

    private fun updatePermissionStatus() {
        val locationGranted = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val activityGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isPermissionGranted(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            true
        }

        binding.locationStatus.apply {
            visibility = View.VISIBLE
            setImageResource(if (locationGranted) R.drawable.ic_check else R.drawable.ic_close)
        }

        binding.activityStatus.apply {
            visibility = View.VISIBLE
            setImageResource(if (activityGranted) R.drawable.ic_check else R.drawable.ic_close)
        }

        // Storage doesn't need runtime permission on modern Android (using app-specific storage)
        binding.storageStatus.apply {
            visibility = View.VISIBLE
            setImageResource(R.drawable.ic_check)
        }

        // Update button text based on state
        if (areAllPermissionsGranted()) {
            binding.grantButton.text = "CONTINUE"
        } else {
            binding.grantButton.text = "GRANT PERMISSIONS"
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PermissionChecker.PERMISSION_GRANTED
    }

    private fun areAllPermissionsGranted(): Boolean {
        return requiredPermissions.all { isPermissionGranted(it) }
    }

    private fun completeOnboarding() {
        lifecycleScope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_ONBOARDING_COMPLETE] = true
            }
            
            // Navigate to home
            findNavController().navigate(R.id.action_onboarding_to_home)
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }
}
