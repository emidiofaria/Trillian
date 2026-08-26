package com.drivingcoach.ui

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.drivingcoach.R
import com.drivingcoach.data.api.AuthEvent
import com.drivingcoach.data.api.AuthEventBus
import com.drivingcoach.databinding.ActivityMainBinding
import com.drivingcoach.ui.splash.SplashVisibilitySignal
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var authEventBus: AuthEventBus

    @Inject
    lateinit var splashVisibility: SplashVisibilitySignal

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hands the Android 12+ system splash over to our branded screen without a
        // second flash of the launcher icon.
        val systemSplash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // SRS UI-02: the exit listener fires at the one instant that matters — when the
        // system splash is removed and the branded screen becomes visible. It is set here,
        // after super.onCreate(), because installSplashScreen() must run before it and Hilt
        // has not injected the field until then. The splash cannot exit before the first
        // frame, which is well after onCreate returns, so the listener is never set late.
        systemSplash.setOnExitAnimationListener { provider ->
            splashVisibility.markVisible()
            // Mandatory: the system splash stays up forever if the provider is not removed.
            provider.remove()
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
        setupNavigation()
        observeAuthEvents()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = insets.left,
                right = insets.right
            )
            windowInsets
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Start destination is always the splash screen; it resolves onboarding and auth
        // state off the main thread and navigates onward (SRS UI-04).
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.recordingFragment -> hideSystemUI()
                else -> showSystemUI()
            }
        }
    }
    
    private fun observeAuthEvents() {
        lifecycleScope.launch {
            authEventBus.events.collect { event ->
                when (event) {
                    is AuthEvent.SessionExpired -> {
                        handleSessionExpired()
                    }
                }
            }
        }
    }
    
    private fun handleSessionExpired() {
        // Show snackbar
        Snackbar.make(
            binding.root,
            "Session expired — please log in again",
            Snackbar.LENGTH_LONG
        ).show()
        
        // Navigate to login, clearing back stack
        navController.navigate(R.id.loginFragment) {
            popUpTo(R.id.nav_graph) {
                inclusive = true
            }
        }
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    private fun showSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
