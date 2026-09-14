package com.drivingcoach.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.drivingcoach.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * TODO(V2): placeholder for Firebase Authentication — see SRS UM-01 … UM-17 (deferred).
 *
 * **Unreachable in V1, and deliberately so.** V1 ships without a backend, so there is
 * nothing to authenticate against. Local identity is handled by
 * [com.drivingcoach.ui.driver.DriverNameFragment] instead, and the startup path
 * ([com.drivingcoach.ui.splash.SplashViewModel]) never resolves here.
 *
 * This screen is retained rather than deleted because the V2 work is "fill in the form and
 * restore the navigation edges", not "rebuild the auth flow". Its email and password fields
 * were removed: while they were on screen they looked functional but were wired to a
 * `TODO`, and a control that silently does nothing is worse than no control at all.
 *
 * The former `⚡ Skip Login (Demo Mode)` shortcut lived here too. It navigated to Home
 * without persisting anything, which is why every relaunch dropped the driver back onto this
 * screen — the defect that motivated the move to a persisted local profile.
 *
 * When restoring this screen in V2, reinstate:
 *  - email/password inputs per SRS UM-11 (`textEmailAddress`, `textPassword` + toggle)
 *  - in-flight button disable and progress indicator per UM-10
 *  - failure Snackbar that preserves the password field per UM-09
 */
@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    @Suppress("unused")
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
