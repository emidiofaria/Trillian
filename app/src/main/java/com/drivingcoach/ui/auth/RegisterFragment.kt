package com.drivingcoach.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.drivingcoach.databinding.FragmentRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * TODO(V2): placeholder for Firebase Authentication — see SRS UM-01 … UM-06 (deferred).
 *
 * **Unreachable in V1.** Registration requires an identity provider, and V1 has none.
 * A driver identifies themselves locally via
 * [com.drivingcoach.ui.driver.DriverNameFragment] instead.
 *
 * Retained alongside [LoginFragment] so the V2 auth flow is restored rather than rebuilt.
 * When restoring, implement the 8-character password minimum (UM-03), the 2–100 character
 * display name bound (UM-04, already enforced by
 * [com.drivingcoach.data.profile.DriverProfileStore]) and the failure Snackbar (UM-06).
 */
@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    @Suppress("unused")
    private val viewModel: RegisterViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
