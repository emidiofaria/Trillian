package com.bmw.drivingcoach.util

import android.content.Context
import androidx.core.content.ContextCompat
import com.bmw.drivingcoach.R

object LapTimeFormatter {

    /**
     * Formats milliseconds to lap time string: M:SS.mmm (e.g. '1:23.456')
     */
    fun formatLapTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = ms % 1000
        return String.format("%d:%02d.%03d", minutes, seconds, millis)
    }

    /**
     * Formats sector time in seconds: SS.s (e.g. '32.4')
     */
    fun formatSectorTime(ms: Long): String {
        val seconds = ms / 1000.0
        return String.format("%.1f", seconds)
    }

    /**
     * Formats delta time with sign: '+0.456s' or '-0.123s', always 3 decimal places
     * Positive delta = slower (worse), Negative delta = faster (better)
     */
    fun formatDelta(deltaMs: Long): String {
        val seconds = deltaMs / 1000.0
        return if (deltaMs >= 0) {
            String.format("+%.3fs", seconds)
        } else {
            String.format("%.3fs", seconds)
        }
    }

    /**
     * Formats short delta (for sector comparison): '+0.3' or '-0.1'
     */
    fun formatShortDelta(deltaMs: Long): String {
        val seconds = deltaMs / 1000.0
        return if (deltaMs >= 0) {
            String.format("+%.1f", seconds)
        } else {
            String.format("%.1f", seconds)
        }
    }

    /**
     * Returns the appropriate color for a delta value.
     * Negative delta (faster) = green (positive/good)
     * Positive delta (slower) = red (negative/bad)
     * Zero = neutral (surface variant)
     */
    fun deltaColor(deltaMs: Long, context: Context): Int {
        return when {
            deltaMs < 0 -> ContextCompat.getColor(context, R.color.colorDeltaPositive) // Faster = green
            deltaMs > 0 -> ContextCompat.getColor(context, R.color.colorDeltaNegative) // Slower = red
            else -> ContextCompat.getColor(context, R.color.colorOnSurfaceVariant)
        }
    }
}
