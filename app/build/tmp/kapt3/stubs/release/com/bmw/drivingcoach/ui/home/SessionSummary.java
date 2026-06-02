package com.bmw.drivingcoach.ui.home;

import android.content.Context;
import android.content.Intent;
import androidx.lifecycle.ViewModel;
import com.bmw.drivingcoach.data.db.dao.LapDao;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
import com.bmw.drivingcoach.data.db.entity.LapEntity;
import com.bmw.drivingcoach.data.db.entity.SessionEntity;
import com.bmw.drivingcoach.service.TelemetryForegroundService;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;
import kotlinx.coroutines.flow.SharedFlow;
import kotlinx.coroutines.flow.StateFlow;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.inject.Inject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0016\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B7\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0003\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\u0003\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\fJ\t\u0010\u0019\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u001a\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001b\u001a\u00020\u0003H\u00c6\u0003J\u0010\u0010\u001c\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010\u000eJ\t\u0010\u001d\u001a\u00020\tH\u00c6\u0003J\t\u0010\u001e\u001a\u00020\u000bH\u00c6\u0003JL\u0010\u001f\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00032\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\b\u001a\u00020\t2\b\b\u0002\u0010\n\u001a\u00020\u000bH\u00c6\u0001\u00a2\u0006\u0002\u0010 J\u0013\u0010!\u001a\u00020\"2\b\u0010#\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010$\u001a\u00020\tH\u00d6\u0001J\t\u0010%\u001a\u00020\u0005H\u00d6\u0001R\u0015\u0010\u0007\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010\u000f\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0011\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0013R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018\u00a8\u0006&"}, d2 = {"Lcom/bmw/drivingcoach/ui/home/SessionSummary;", "", "id", "", "trackName", "", "startedAt", "bestLapMs", "lapCount", "", "consistencyScore", "", "(JLjava/lang/String;JLjava/lang/Long;IF)V", "getBestLapMs", "()Ljava/lang/Long;", "Ljava/lang/Long;", "getConsistencyScore", "()F", "getId", "()J", "getLapCount", "()I", "getStartedAt", "getTrackName", "()Ljava/lang/String;", "component1", "component2", "component3", "component4", "component5", "component6", "copy", "(JLjava/lang/String;JLjava/lang/Long;IF)Lcom/bmw/drivingcoach/ui/home/SessionSummary;", "equals", "", "other", "hashCode", "toString", "app_release"})
public final class SessionSummary {
    private final long id = 0L;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String trackName = null;
    private final long startedAt = 0L;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Long bestLapMs = null;
    private final int lapCount = 0;
    private final float consistencyScore = 0.0F;
    
    public SessionSummary(long id, @org.jetbrains.annotations.NotNull()
    java.lang.String trackName, long startedAt, @org.jetbrains.annotations.Nullable()
    java.lang.Long bestLapMs, int lapCount, float consistencyScore) {
        super();
    }
    
    public final long getId() {
        return 0L;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getTrackName() {
        return null;
    }
    
    public final long getStartedAt() {
        return 0L;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long getBestLapMs() {
        return null;
    }
    
    public final int getLapCount() {
        return 0;
    }
    
    public final float getConsistencyScore() {
        return 0.0F;
    }
    
    public final long component1() {
        return 0L;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component2() {
        return null;
    }
    
    public final long component3() {
        return 0L;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long component4() {
        return null;
    }
    
    public final int component5() {
        return 0;
    }
    
    public final float component6() {
        return 0.0F;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.bmw.drivingcoach.ui.home.SessionSummary copy(long id, @org.jetbrains.annotations.NotNull()
    java.lang.String trackName, long startedAt, @org.jetbrains.annotations.Nullable()
    java.lang.Long bestLapMs, int lapCount, float consistencyScore) {
        return null;
    }
    
    @java.lang.Override()
    public boolean equals(@org.jetbrains.annotations.Nullable()
    java.lang.Object other) {
        return false;
    }
    
    @java.lang.Override()
    public int hashCode() {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public java.lang.String toString() {
        return null;
    }
}