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

    /** A percentage with two decimals, e.g. a coefficient of variation "2.10". */
    fun percent(value: Double): String = String.format(Locale.US, "%.2f", value)

    /** A speed in km/h, one decimal. */
    fun speedKmh(kmh: Double): String = String.format(Locale.US, "%.1f", kmh)

    /**
     * Time on track for the Profile stats (SRS DP-11). Under an hour it is whole minutes,
     * rounded down; from an hour on it is hours with one decimal, also rounded down, so
     * 59 min 59 s reads "59 min" and never "1.0 h".
     */
    sealed class DrivenTime {
        data class Minutes(val minutes: Long) : DrivenTime()
        data class Hours(val hours: String) : DrivenTime()
    }

    fun drivenTime(totalMs: Long): DrivenTime {
        val ms = totalMs.coerceAtLeast(0L)
        val minutes = ms / 60_000L
        if (minutes < 60L) return DrivenTime.Minutes(minutes)
        val tenths = ms / 360_000L // whole tenths of an hour
        return DrivenTime.Hours(String.format(Locale.US, "%d.%d", tenths / 10, tenths % 10))
    }
}
