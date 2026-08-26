package com.drivingcoach.data.location

import android.location.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The slice of location services the app needs, behind a seam.
 *
 * Exists so [LocationWarmUp] — which owns timing, thresholds and the idle ceiling — can be
 * exercised as plain JVM unit tests, and so the Track Setup screen can be driven by scripted
 * fixes in instrumented tests instead of by real satellites on an emulator.
 */
interface LocationUpdates {

    /**
     * Whether fine location has been granted. Checked before subscribing so a missing
     * permission degrades to doing nothing rather than throwing.
     */
    fun hasPermission(): Boolean

    /**
     * Cold flow of full fixes, coordinates included, for the one screen that must *capture*
     * a position. Subscribing starts location updates; cancelling the collector stops them,
     * so callers manage the GNSS bill purely through coroutine scope.
     */
    fun positionUpdates(intervalMs: Long): Flow<Location>

    /**
     * The same stream reduced to what readiness is decided from.
     *
     * Separate from [positionUpdates] so the warm-up never handles a coordinate it could
     * accidentally leak — see [GpsReadiness] — and so its logic stays testable off-device,
     * where `android.location.Location` is only a stub.
     */
    fun updates(intervalMs: Long): Flow<LocationFix> =
        positionUpdates(intervalMs).map { LocationFix(it.accuracy, System.currentTimeMillis()) }
}

/**
 * Only what readiness is decided from. Coordinates are deliberately absent — see
 * [GpsReadiness] for why the warm-up must never be able to hand out a capturable position.
 */
data class LocationFix(
    val accuracyM: Float,
    val timestampMs: Long
)
