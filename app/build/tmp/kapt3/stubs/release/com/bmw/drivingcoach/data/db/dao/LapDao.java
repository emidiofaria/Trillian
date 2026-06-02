package com.bmw.drivingcoach.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.bmw.drivingcoach.data.db.entity.LapEntity;
import kotlinx.coroutines.flow.Flow;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\b\b\bg\u0018\u00002\u00020\u0001J\u0016\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u0018\u0010\u0007\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\t0\b2\u0006\u0010\u0004\u001a\u00020\u0005H\'J\u0018\u0010\n\u001a\u0004\u0018\u00010\t2\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u0018\u0010\u000b\u001a\u0004\u0018\u00010\t2\u0006\u0010\f\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u001c\u0010\r\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\t0\u000e0\b2\u0006\u0010\u0004\u001a\u00020\u0005H\'J\u0016\u0010\u000f\u001a\u00020\u00052\u0006\u0010\u0010\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\u0011J\u001c\u0010\u0012\u001a\u00020\u00032\f\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\t0\u000eH\u00a7@\u00a2\u0006\u0002\u0010\u0014J\u0016\u0010\u0015\u001a\u00020\u00032\u0006\u0010\f\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006\u00a8\u0006\u0016"}, d2 = {"Lcom/bmw/drivingcoach/data/db/dao/LapDao;", "", "clearBestLap", "", "sessionId", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getBestLap", "Lkotlinx/coroutines/flow/Flow;", "Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "getFastestLap", "getLapById", "lapId", "getLapsForSession", "", "insertLap", "lap", "(Lcom/bmw/drivingcoach/data/db/entity/LapEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "insertLaps", "laps", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "setBestLap", "app_release"})
@androidx.room.Dao()
public abstract interface LapDao {
    
    @androidx.room.Insert()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object insertLaps(@org.jetbrains.annotations.NotNull()
    java.util.List<com.bmw.drivingcoach.data.db.entity.LapEntity> laps, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Insert()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object insertLap(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.entity.LapEntity lap, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM laps WHERE id = :lapId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getLapById(long lapId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.bmw.drivingcoach.data.db.entity.LapEntity> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM laps WHERE sessionId = :sessionId ORDER BY lapNumber ASC")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.util.List<com.bmw.drivingcoach.data.db.entity.LapEntity>> getLapsForSession(long sessionId);
    
    @androidx.room.Query(value = "SELECT * FROM laps WHERE sessionId = :sessionId AND isBestLap = 1 LIMIT 1")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<com.bmw.drivingcoach.data.db.entity.LapEntity> getBestLap(long sessionId);
    
    @androidx.room.Query(value = "UPDATE laps SET isBestLap = 0 WHERE sessionId = :sessionId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object clearBestLap(long sessionId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "UPDATE laps SET isBestLap = 1 WHERE id = :lapId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object setBestLap(long lapId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM laps WHERE sessionId = :sessionId ORDER BY durationMs ASC LIMIT 1")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getFastestLap(long sessionId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.bmw.drivingcoach.data.db.entity.LapEntity> $completion);
}