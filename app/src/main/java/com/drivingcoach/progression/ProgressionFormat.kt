package com.drivingcoach.progression

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Number formatting for progression text (SRS PG-23). Locale.US throughout so the
 * decimal separator never changes with the phone's language.
 */
object ProgressionFormat {

    /**
     * Distance driven, one decimal, rounded **down**: 24.96 km must not read "25.0" next
     * to an unticked "25 km" requirement.
     */
    fun drivenKm(km: Double): String = String.format(Locale.US, "%.1f", floor(km * 10.0 + 1e-9) / 10.0)

    /** Distance still to drive, rounded **up**, so "0.0 km more" is never shown while short. */
    fun remainingKm(km: Double): String = String.format(Locale.US, "%.1f", ceil(km * 10.0 - 1e-9) / 10.0)

    /** A level threshold: whole kilometres. */
    fun thresholdKm(km: Double): String = String.format(Locale.US, "%.0f", km)

    /** A time gap or spread in seconds, three decimals, as OfflineCoachingEngine does. */
    fun seconds(ms: Double): String = String.format(Locale.US, "%.3fs", ms / 1000.0)

    fun seconds(ms: Long): String = seconds(ms.toDouble())
}
