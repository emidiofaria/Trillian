package com.drivingcoach.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.drivingcoach.R
import com.drivingcoach.data.api.AuthEvent
import com.drivingcoach.data.api.AuthEventBus
import com.drivingcoach.data.location.WarmUpForegroundBinder
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

    @Inject
    lateinit var warmUpForegroundBinder: WarmUpForegroundBinder

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

        // Ties the GPS warm-up's release to the app leaving the foreground rather than to any
        // one screen stopping (Incident 12). Bound here, before this Activity is started, so
        // the very first onActivityStarted is counted. Idempotent across recreation.
        warmUpForegroundBinder.bind(application)
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

    // Recording runs full-screen: at a circuit the driver needs the largest
    // possible readout and no accidental taps on system bars.
    //
    // This used to set window.decorView.systemUiVisibility with SYSTEM_UI_FLAG_*.
    // Those flags are deprecated since API 30 and, once edge-to-edge became
    // mandatory in Android 16 (targetSdk 36), the platform ignores them outright.
    // The call still compiled and still ran; it simply stopped doing anything,
    // which would have taken the Recording screen out of full-screen on exactly
    // the devices the app is being published for.
    private fun hideSystemUI() {
        WindowCompat.getInsetsController(window, binding.root).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun showSystemUI() {
        WindowCompat.getInsetsController(window, binding.root)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
