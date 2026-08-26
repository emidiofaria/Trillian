package com.drivingcoach.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play Services implementation of [LocationUpdates].
 *
 * Uses [Priority.PRIORITY_HIGH_ACCURACY] because the whole point of the warm-up is to make
 * the *GNSS* chip acquire ephemeris. Balanced priority would be answered from wifi and cell
 * towers, which is cheaper but leaves the satellite fix exactly as cold as before.
 *
 * Holds the application context only; it is a process singleton and must not retain an
 * activity.
 */
@Singleton
class FusedLocationUpdates @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationUpdates {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun positionUpdates(intervalMs: Long): Flow<Location> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs)
            // Emit coarse early fixes instead of withholding them: the warm-up wants to show
            // "acquiring" progress, and accuracy is judged downstream against READY_ACCURACY_M.
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                trySend(location)
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose { client.removeLocationUpdates(callback) }
    }
}
