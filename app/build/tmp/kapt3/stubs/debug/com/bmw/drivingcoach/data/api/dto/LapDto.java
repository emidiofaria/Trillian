package com.bmw.drivingcoach.data.api.dto;

import com.google.gson.annotations.SerializedName;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0002\b\u001f\b\u0086\b\u0018\u00002\u00020\u0001BS\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0007\u0012\u0006\u0010\t\u001a\u00020\u0007\u0012\b\u0010\n\u001a\u0004\u0018\u00010\u0007\u0012\b\u0010\u000b\u001a\u0004\u0018\u00010\u0007\u0012\b\u0010\f\u001a\u0004\u0018\u00010\u0007\u0012\u0006\u0010\r\u001a\u00020\u000e\u00a2\u0006\u0002\u0010\u000fJ\t\u0010\u001e\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u001f\u001a\u00020\u0005H\u00c6\u0003J\t\u0010 \u001a\u00020\u0007H\u00c6\u0003J\t\u0010!\u001a\u00020\u0007H\u00c6\u0003J\t\u0010\"\u001a\u00020\u0007H\u00c6\u0003J\u0010\u0010#\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0019J\u0010\u0010$\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0019J\u0010\u0010%\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0019J\t\u0010&\u001a\u00020\u000eH\u00c6\u0003Jn\u0010\'\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u00072\b\b\u0002\u0010\t\u001a\u00020\u00072\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\u00072\b\b\u0002\u0010\r\u001a\u00020\u000eH\u00c6\u0001\u00a2\u0006\u0002\u0010(J\u0013\u0010)\u001a\u00020\u000e2\b\u0010*\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010+\u001a\u00020\u0005H\u00d6\u0001J\t\u0010,\u001a\u00020\u0003H\u00d6\u0001R\u0016\u0010\t\u001a\u00020\u00078\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0016\u0010\b\u001a\u00020\u00078\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0011R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0016\u0010\r\u001a\u00020\u000e8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u0015R\u0016\u0010\u0004\u001a\u00020\u00058\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0017R\u001a\u0010\n\u001a\u0004\u0018\u00010\u00078\u0006X\u0087\u0004\u00a2\u0006\n\n\u0002\u0010\u001a\u001a\u0004\b\u0018\u0010\u0019R\u001a\u0010\u000b\u001a\u0004\u0018\u00010\u00078\u0006X\u0087\u0004\u00a2\u0006\n\n\u0002\u0010\u001a\u001a\u0004\b\u001b\u0010\u0019R\u001a\u0010\f\u001a\u0004\u0018\u00010\u00078\u0006X\u0087\u0004\u00a2\u0006\n\n\u0002\u0010\u001a\u001a\u0004\b\u001c\u0010\u0019R\u0016\u0010\u0006\u001a\u00020\u00078\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u0011\u00a8\u0006-"}, d2 = {"Lcom/bmw/drivingcoach/data/api/dto/LapDto;", "", "id", "", "lapNumber", "", "startTs", "", "endTs", "durationMs", "sector1Ms", "sector2Ms", "sector3Ms", "isBestLap", "", "(Ljava/lang/String;IJJJLjava/lang/Long;Ljava/lang/Long;Ljava/lang/Long;Z)V", "getDurationMs", "()J", "getEndTs", "getId", "()Ljava/lang/String;", "()Z", "getLapNumber", "()I", "getSector1Ms", "()Ljava/lang/Long;", "Ljava/lang/Long;", "getSector2Ms", "getSector3Ms", "getStartTs", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "(Ljava/lang/String;IJJJLjava/lang/Long;Ljava/lang/Long;Ljava/lang/Long;Z)Lcom/bmw/drivingcoach/data/api/dto/LapDto;", "equals", "other", "hashCode", "toString", "app_debug"})
public final class LapDto {
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String id = null;
    @com.google.gson.annotations.SerializedName(value = "lap_number")
    private final int lapNumber = 0;
    @com.google.gson.annotations.SerializedName(value = "start_ts")
    private final long startTs = 0L;
    @com.google.gson.annotations.SerializedName(value = "end_ts")
    private final long endTs = 0L;
    @com.google.gson.annotations.SerializedName(value = "duration_ms")
    private final long durationMs = 0L;
    @com.google.gson.annotations.SerializedName(value = "sector_1_ms")
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Long sector1Ms = null;
    @com.google.gson.annotations.SerializedName(value = "sector_2_ms")
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Long sector2Ms = null;
    @com.google.gson.annotations.SerializedName(value = "sector_3_ms")
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Long sector3Ms = null;
    @com.google.gson.annotations.SerializedName(value = "is_best_lap")
    private final boolean isBestLap = false;
    
    public LapDto(@org.jetbrains.annotations.NotNull()
    java.lang.String id, int lapNumber, long startTs, long endTs, long durationMs, @org.jetbrains.annotations.Nullable()
    java.lang.Long sector1Ms, @org.jetbrains.annotations.Nullable()
    java.lang.Long sector2Ms, @org.jetbrains.annotations.Nullable()
    java.lang.Long sector3Ms, boolean isBestLap) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getId() {
        return null;
    }
    
    public final int getLapNumber() {
        return 0;
    }
    
    public final long getStartTs() {
        return 0L;
    }
    
    public final long getEndTs() {
        return 0L;
    }
    
    public final long getDurationMs() {
        return 0L;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long getSector1Ms() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long getSector2Ms() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long getSector3Ms() {
        return null;
    }
    
    public final boolean isBestLap() {
        return false;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component1() {
        return null;
    }
    
    public final int component2() {
        return 0;
    }
    
    public final long component3() {
        return 0L;
    }
    
    public final long component4() {
        return 0L;
    }
    
    public final long component5() {
        return 0L;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long component6() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long component7() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long component8() {
        return null;
    }
    
    public final boolean component9() {
        return false;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.bmw.drivingcoach.data.api.dto.LapDto copy(@org.jetbrains.annotations.NotNull()
    java.lang.String id, int lapNumber, long startTs, long endTs, long durationMs, @org.jetbrains.annotations.Nullable()
    java.lang.Long sector1Ms, @org.jetbrains.annotations.Nullable()
    java.lang.Long sector2Ms, @org.jetbrains.annotations.Nullable()
    java.lang.Long sector3Ms, boolean isBestLap) {
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