package com.bmw.drivingcoach.util;

import android.content.Context;
import androidx.core.content.ContextCompat;
import com.bmw.drivingcoach.R;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bJ\u000e\u0010\t\u001a\u00020\n2\u0006\u0010\u0005\u001a\u00020\u0006J\u000e\u0010\u000b\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\u0006J\u000e\u0010\r\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\u0006J\u000e\u0010\u000e\u001a\u00020\n2\u0006\u0010\u0005\u001a\u00020\u0006\u00a8\u0006\u000f"}, d2 = {"Lcom/bmw/drivingcoach/util/LapTimeFormatter;", "", "()V", "deltaColor", "", "deltaMs", "", "context", "Landroid/content/Context;", "formatDelta", "", "formatLapTime", "ms", "formatSectorTime", "formatShortDelta", "app_debug"})
public final class LapTimeFormatter {
    @org.jetbrains.annotations.NotNull()
    public static final com.bmw.drivingcoach.util.LapTimeFormatter INSTANCE = null;
    
    private LapTimeFormatter() {
        super();
    }
    
    /**
     * Formats milliseconds to lap time string: M:SS.mmm (e.g. '1:23.456')
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String formatLapTime(long ms) {
        return null;
    }
    
    /**
     * Formats sector time in seconds: SS.s (e.g. '32.4')
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String formatSectorTime(long ms) {
        return null;
    }
    
    /**
     * Formats delta time with sign: '+0.456s' or '-0.123s', always 3 decimal places
     * Positive delta = slower (worse), Negative delta = faster (better)
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String formatDelta(long deltaMs) {
        return null;
    }
    
    /**
     * Formats short delta (for sector comparison): '+0.3' or '-0.1'
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String formatShortDelta(long deltaMs) {
        return null;
    }
    
    /**
     * Returns the appropriate color for a delta value.
     * Negative delta (faster) = green (positive/good)
     * Positive delta (slower) = red (negative/bad)
     * Zero = neutral (surface variant)
     */
    public final int deltaColor(long deltaMs, @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return 0;
    }
}