package com.drivingcoach

import android.app.Application
import android.os.DeadSystemException
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DrivingCoachApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // Lazy initialization ensures workerFactory is injected before first access
    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        setupUncaughtExceptionHandler()
    }
    
    /**
     * Sets up a global uncaught exception handler to gracefully handle
     * system-level exceptions like DeadSystemException.
     * 
     * DeadSystemException occurs when the Android system_server crashes.
     * There's nothing the app can do to recover, but we can log it gracefully
     * instead of showing an ugly crash report.
     */
    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isSystemDeathException(throwable)) {
                // System is dying — log gracefully and let the process terminate
                Log.e(TAG, "System crash detected (DeadSystemException). " +
                        "The Android system has crashed. App will restart when system recovers.", throwable)
                // Don't invoke default handler — just let the process die quietly
                // The system will restart everything anyway
            } else {
                // For all other exceptions, use the default handler (e.g., crash reporting)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    /**
     * Checks if the throwable is a DeadSystemException or caused by one.
     * DeadSystemException can be wrapped in other exceptions.
     */
    private fun isSystemDeathException(throwable: Throwable?): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is DeadSystemException) {
                return true
            }
            // Also check for the common wrapper case
            if (current.message?.contains("DeadSystemException") == true) {
                return true
            }
            current = current.cause
        }
        return false
    }
    
    companion object {
        private const val TAG = "DrivingCoachApp"
    }
}
