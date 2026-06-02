package com.bmw.drivingcoach.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bmw.drivingcoach.R
import com.bmw.drivingcoach.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        // Skip login button for demo/testing without backend
        binding.skipLoginButton.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_home)
        }
        
        // Regular login button
        binding.loginButton.setOnClickListener {
            // TODO: Implement actual login
        }
        
        // Register navigation
        binding.registerButton.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }
    }

    private fun observeViewModel() {
        // TODO: Observe login state
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
