package com.bmw.drivingcoach.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.bmw.drivingcoach.data.db.entity.SessionEntity;
import kotlinx.coroutines.flow.Flow;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\t\n\u0002\b\b\n\u0002\u0010\u0002\n\u0002\b\n\bg\u0018\u00002\u00020\u0001J\u001c\u0010\u0002\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\u00040\u00032\u0006\u0010\u0006\u001a\u00020\u0007H\'J\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u00a7@\u00a2\u0006\u0002\u0010\tJ\u0018\u0010\n\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00050\u00032\u0006\u0010\u000b\u001a\u00020\fH\'J\u0018\u0010\r\u001a\u0004\u0018\u00010\u00052\u0006\u0010\u000b\u001a\u00020\fH\u00a7@\u00a2\u0006\u0002\u0010\u000eJ\u001c\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00050\u00042\u0006\u0010\u0010\u001a\u00020\fH\u00a7@\u00a2\u0006\u0002\u0010\u000eJ\u0016\u0010\u0011\u001a\u00020\f2\u0006\u0010\u0012\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0013J\u001e\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u0016\u001a\u00020\u0007H\u00a7@\u00a2\u0006\u0002\u0010\u0017J\u0016\u0010\u0018\u001a\u00020\u00152\u0006\u0010\u0012\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0013J\u001e\u0010\u0019\u001a\u00020\u00152\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u001a\u001a\u00020\fH\u00a7@\u00a2\u0006\u0002\u0010\u001bJ*\u0010\u001c\u001a\u00020\u00152\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u0016\u001a\u00020\u00072\n\b\u0002\u0010\u001d\u001a\u0004\u0018\u00010\u0007H\u00a7@\u00a2\u0006\u0002\u0010\u001e\u00a8\u0006\u001f"}, d2 = {"Lcom/bmw/drivingcoach/data/db/dao/SessionDao;", "", "getAllSessionsForUser", "Lkotlinx/coroutines/flow/Flow;", "", "Lcom/bmw/drivingcoach/data/db/entity/SessionEntity;", "userId", "", "getPendingUploadSessions", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getSessionById", "id", "", "getSessionByIdSync", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getStaleUploadSessions", "beforeTimestamp", "insertSession", "session", "(Lcom/bmw/drivingcoach/data/db/entity/SessionEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "updateProcessingStatus", "", "status", "(JLjava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "updateSession", "updateSessionEndTime", "endedAt", "(JJLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "updateUploadStatus", "remoteId", "(JLjava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
@androidx.room.Dao()
public abstract interface SessionDao {
    
    @androidx.room.Insert()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object insertSession(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.entity.SessionEntity session, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion);
    
    @androidx.room.Update()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object updateSession(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.entity.SessionEntity session, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM sessions WHERE id = :id")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<com.bmw.drivingcoach.data.db.entity.SessionEntity> getSessionById(long id);
    
    @androidx.room.Query(value = "SELECT * FROM sessions WHERE id = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getSessionByIdSync(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.bmw.drivingcoach.data.db.entity.SessionEntity> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM sessions WHERE userId = :userId ORDER BY startedAt DESC")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.util.List<com.bmw.drivingcoach.data.db.entity.SessionEntity>> getAllSessionsForUser(@org.jetbrains.annotations.NotNull()
    java.lang.String userId);
    
    @androidx.room.Query(value = "SELECT * FROM sessions WHERE uploadStatus = \'PENDING\' OR uploadStatus = \'FAILED\'")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getPendingUploadSessions(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.bmw.drivingcoach.data.db.entity.SessionEntity>> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM sessions WHERE uploadStatus = \'PENDING\' AND startedAt < :beforeTimestamp")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getStaleUploadSessions(long beforeTimestamp, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.bmw.drivingcoach.data.db.entity.SessionEntity>> $completion);
    
    @androidx.room.Query(value = "UPDATE sessions SET uploadStatus = :status, remoteSessionId = :remoteId WHERE id = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object updateUploadStatus(long id, @org.jetbrains.annotations.NotNull()
    java.lang.String status, @org.jetbrains.annotations.Nullable()
    java.lang.String remoteId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "UPDATE sessions SET processingStatus = :status WHERE id = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object updateProcessingStatus(long id, @org.jetbrains.annotations.NotNull()
    java.lang.String status, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "UPDATE sessions SET endedAt = :endedAt WHERE id = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object updateSessionEndTime(long id, long endedAt, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
    }
}