package com.bmw.drivingcoach.data.repository;

import com.bmw.drivingcoach.data.api.ApiService;
import com.bmw.drivingcoach.data.api.dto.SessionDetailDto;
import com.bmw.drivingcoach.data.api.dto.SessionDto;
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao;
import com.bmw.drivingcoach.data.db.dao.LapDao;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
import com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity;
import com.bmw.drivingcoach.data.db.entity.LapEntity;
import com.bmw.drivingcoach.data.db.entity.SessionEntity;
import kotlinx.coroutines.flow.Flow;
import okhttp3.MultipartBody;
import java.io.File;
import javax.inject.Inject;
import javax.inject.Singleton;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000p\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u000e\b\u0007\u0018\u00002\u00020\u0001B\'\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nJ\u0016\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eH\u0086@\u00a2\u0006\u0002\u0010\u000fJ\u001c\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00120\u00112\u0006\u0010\u0013\u001a\u00020\u0014H\u0086@\u00a2\u0006\u0002\u0010\u0015J\u001a\u0010\u0016\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00180\u00170\u0011H\u0086@\u00a2\u0006\u0002\u0010\u0019J\u001a\u0010\u001a\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000e0\u00170\u001b2\u0006\u0010\u001c\u001a\u00020\u0014J\u001a\u0010\u001d\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u001e0\u00170\u001b2\u0006\u0010\u001f\u001a\u00020\fJ\u001a\u0010 \u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020!0\u00170\u001b2\u0006\u0010\u001f\u001a\u00020\fJ\u0016\u0010\"\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u000e0\u001b2\u0006\u0010\u001f\u001a\u00020\fJ\u001c\u0010#\u001a\u00020$2\f\u0010%\u001a\b\u0012\u0004\u0012\u00020\u001e0\u0017H\u0086@\u00a2\u0006\u0002\u0010&J\u001c\u0010\'\u001a\u00020$2\f\u0010(\u001a\b\u0012\u0004\u0012\u00020!0\u0017H\u0086@\u00a2\u0006\u0002\u0010&J\u0016\u0010)\u001a\u00020$2\u0006\u0010*\u001a\u00020\u0012H\u0082@\u00a2\u0006\u0002\u0010+J\u001e\u0010,\u001a\u00020$2\u0006\u0010\u001f\u001a\u00020\f2\u0006\u0010-\u001a\u00020\fH\u0086@\u00a2\u0006\u0002\u0010.J$\u0010/\u001a\b\u0012\u0004\u0012\u00020\u00140\u00112\u0006\u0010\r\u001a\u00020\u000e2\u0006\u00100\u001a\u00020\u0014H\u0086@\u00a2\u0006\u0002\u00101R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u00062"}, d2 = {"Lcom/bmw/drivingcoach/data/repository/SessionRepository;", "", "apiService", "Lcom/bmw/drivingcoach/data/api/ApiService;", "sessionDao", "Lcom/bmw/drivingcoach/data/db/dao/SessionDao;", "lapDao", "Lcom/bmw/drivingcoach/data/db/dao/LapDao;", "coachingInsightDao", "Lcom/bmw/drivingcoach/data/db/dao/CoachingInsightDao;", "(Lcom/bmw/drivingcoach/data/api/ApiService;Lcom/bmw/drivingcoach/data/db/dao/SessionDao;Lcom/bmw/drivingcoach/data/db/dao/LapDao;Lcom/bmw/drivingcoach/data/db/dao/CoachingInsightDao;)V", "createSession", "", "session", "Lcom/bmw/drivingcoach/data/db/entity/SessionEntity;", "(Lcom/bmw/drivingcoach/data/db/entity/SessionEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "fetchSessionDetail", "Lcom/bmw/drivingcoach/data/repository/SessionResult;", "Lcom/bmw/drivingcoach/data/api/dto/SessionDetailDto;", "remoteId", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "fetchSessions", "", "Lcom/bmw/drivingcoach/data/api/dto/SessionDto;", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAllSessionsForUser", "Lkotlinx/coroutines/flow/Flow;", "userId", "getInsightsForSession", "Lcom/bmw/drivingcoach/data/db/entity/CoachingInsightEntity;", "sessionId", "getLapsForSession", "Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "getSessionById", "insertInsights", "", "insights", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "insertLaps", "laps", "syncSessionDetail", "detail", "(Lcom/bmw/drivingcoach/data/api/dto/SessionDetailDto;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "updateSessionEndTime", "endedAt", "(JJLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "uploadSession", "filePath", "(Lcom/bmw/drivingcoach/data/db/entity/SessionEntity;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
public final class SessionRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.data.api.ApiService apiService = null;
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.data.db.dao.SessionDao sessionDao = null;
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.data.db.dao.LapDao lapDao = null;
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.data.db.dao.CoachingInsightDao coachingInsightDao = null;
    
    @javax.inject.Inject()
    public SessionRepository(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.api.ApiService apiService, @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.dao.SessionDao sessionDao, @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.dao.LapDao lapDao, @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.dao.CoachingInsightDao coachingInsightDao) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.bmw.drivingcoach.data.db.entity.SessionEntity> getSessionById(long sessionId) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<java.util.List<com.bmw.drivingcoach.data.db.entity.LapEntity>> getLapsForSession(long sessionId) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<java.util.List<com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity>> getInsightsForSession(long sessionId) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<java.util.List<com.bmw.drivingcoach.data.db.entity.SessionEntity>> getAllSessionsForUser(@org.jetbrains.annotations.NotNull()
    java.lang.String userId) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object fetchSessions(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.bmw.drivingcoach.data.repository.SessionResult<? extends java.util.List<com.bmw.drivingcoach.data.api.dto.SessionDto>>> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object fetchSessionDetail(@org.jetbrains.annotations.NotNull()
    java.lang.String remoteId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.bmw.drivingcoach.data.repository.SessionResult<com.bmw.drivingcoach.data.api.dto.SessionDetailDto>> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object uploadSession(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.entity.SessionEntity session, @org.jetbrains.annotations.NotNull()
    java.lang.String filePath, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.bmw.drivingcoach.data.repository.SessionResult<java.lang.String>> $completion) {
        return null;
    }
    
    private final java.lang.Object syncSessionDetail(com.bmw.drivingcoach.data.api.dto.SessionDetailDto detail, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object createSession(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.entity.SessionEntity session, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object updateSessionEndTime(long sessionId, long endedAt, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object insertLaps(@org.jetbrains.annotations.NotNull()
    java.util.List<com.bmw.drivingcoach.data.db.entity.LapEntity> laps, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object insertInsights(@org.jetbrains.annotations.NotNull()
    java.util.List<com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity> insights, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
}