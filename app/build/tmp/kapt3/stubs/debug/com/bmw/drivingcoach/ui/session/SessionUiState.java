package com.bmw.drivingcoach.ui.session;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.work.WorkManager;
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao;
import com.bmw.drivingcoach.data.db.dao.LapDao;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
import com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity;
import com.bmw.drivingcoach.data.db.entity.LapEntity;
import com.bmw.drivingcoach.data.db.entity.ProcessingStatus;
import com.bmw.drivingcoach.data.db.entity.SessionEntity;
import com.bmw.drivingcoach.data.worker.TelemetryUploadWorker;
import dagger.hilt.android.lifecycle.HiltViewModel;
import kotlinx.coroutines.flow.StateFlow;
import javax.inject.Inject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u001b\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001Bg\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u0012\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\u000e\b\u0002\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u0007\u0012\u000e\b\u0002\u0010\t\u001a\b\u0012\u0004\u0012\u00020\n0\u0007\u0012\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\b\u0012\b\b\u0002\u0010\f\u001a\u00020\r\u0012\b\b\u0002\u0010\u000e\u001a\u00020\u000f\u0012\n\b\u0002\u0010\u0010\u001a\u0004\u0018\u00010\u0011\u00a2\u0006\u0002\u0010\u0012J\t\u0010!\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\"\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\u000f\u0010#\u001a\b\u0012\u0004\u0012\u00020\b0\u0007H\u00c6\u0003J\u000f\u0010$\u001a\b\u0012\u0004\u0012\u00020\n0\u0007H\u00c6\u0003J\u000b\u0010%\u001a\u0004\u0018\u00010\bH\u00c6\u0003J\t\u0010&\u001a\u00020\rH\u00c6\u0003J\t\u0010\'\u001a\u00020\u000fH\u00c6\u0003J\u000b\u0010(\u001a\u0004\u0018\u00010\u0011H\u00c6\u0003Jk\u0010)\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u00052\u000e\b\u0002\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u00072\u000e\b\u0002\u0010\t\u001a\b\u0012\u0004\u0012\u00020\n0\u00072\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\b2\b\b\u0002\u0010\f\u001a\u00020\r2\b\b\u0002\u0010\u000e\u001a\u00020\u000f2\n\b\u0002\u0010\u0010\u001a\u0004\u0018\u00010\u0011H\u00c6\u0001J\u0013\u0010*\u001a\u00020\u00032\b\u0010+\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010,\u001a\u00020-H\u00d6\u0001J\t\u0010.\u001a\u00020\u0011H\u00d6\u0001R\u0013\u0010\u000b\u001a\u0004\u0018\u00010\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\f\u001a\u00020\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0013\u0010\u0010\u001a\u0004\u0018\u00010\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0017\u0010\t\u001a\b\u0012\u0004\u0012\u00020\n0\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0002\u0010\u001bR\u0017\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u001aR\u0011\u0010\u000e\u001a\u00020\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001eR\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010 \u00a8\u0006/"}, d2 = {"Lcom/bmw/drivingcoach/ui/session/SessionUiState;", "", "isLoading", "", "session", "Lcom/bmw/drivingcoach/data/db/entity/SessionEntity;", "laps", "", "Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "insights", "Lcom/bmw/drivingcoach/data/db/entity/CoachingInsightEntity;", "bestLap", "consistencyScore", "", "processingStatus", "Lcom/bmw/drivingcoach/data/db/entity/ProcessingStatus;", "error", "", "(ZLcom/bmw/drivingcoach/data/db/entity/SessionEntity;Ljava/util/List;Ljava/util/List;Lcom/bmw/drivingcoach/data/db/entity/LapEntity;FLcom/bmw/drivingcoach/data/db/entity/ProcessingStatus;Ljava/lang/String;)V", "getBestLap", "()Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "getConsistencyScore", "()F", "getError", "()Ljava/lang/String;", "getInsights", "()Ljava/util/List;", "()Z", "getLaps", "getProcessingStatus", "()Lcom/bmw/drivingcoach/data/db/entity/ProcessingStatus;", "getSession", "()Lcom/bmw/drivingcoach/data/db/entity/SessionEntity;", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "copy", "equals", "other", "hashCode", "", "toString", "app_debug"})
public final class SessionUiState {
    private final boolean isLoading = false;
    @org.jetbrains.annotations.Nullable()
    private final com.bmw.drivingcoach.data.db.entity.SessionEntity session = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.bmw.drivingcoach.data.db.entity.LapEntity> laps = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity> insights = null;
    @org.jetbrains.annotations.Nullable()
    private final com.bmw.drivingcoach.data.db.entity.LapEntity bestLap = null;
    private final float consistencyScore = 0.0F;
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.data.db.entity.ProcessingStatus processingStatus = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String error = null;
    
    public SessionUiState(boolean isLoading, @org.jetbrains.annotations.Nullable()
    com.bmw.drivingcoach.data.db.entity.SessionEntity session, @org.jetbrains.annotations.NotNull()
    java.util.List<com.bmw.drivingcoach.data.db.entity.LapEntity> laps, @org.jetbrains.annotations.NotNull()
    java.util.List<com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity> insights, @org.jetbrains.annotations.Nullable()
    com.bmw.drivingcoach.data.db.entity.LapEntity bestLap, float consistencyScore, @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.entity.ProcessingStatus processingStatus, @org.jetbrains.annotations.Nullable()
    java.lang.String error) {
        super();
    }
    
    public final boolean isLoading() {
        return false;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.bmw.drivingcoach.data.db.entity.SessionEntity getSession() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.bmw.drivingcoach.data.db.entity.LapEntity> getLaps() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity> getInsights() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.bmw.drivingcoach.data.db.entity.LapEntity getBestLap() {
        return null;
    }
    
    public final float getConsistencyScore() {
        return 0.0F;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.bmw.drivingcoach.data.db.entity.ProcessingStatus getProcessingStatus() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getError() {
        return null;
    }
    
    public SessionUiState() {
        super();
    }
    
    public final boolean component1() {
        return false;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.bmw.drivingcoach.data.db.entity.SessionEntity component2() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.bmw.drivingcoach.data.db.entity.LapEntity> component3() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity> component4() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.bmw.drivingcoach.data.db.entity.LapEntity component5() {
        return null;
    }
    
    public final float component6() {
        return 0.0F;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.bmw.drivingcoach.data.db.entity.ProcessingStatus component7() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component8() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.bmw.drivingcoach.ui.session.SessionUiState copy(boolean isLoading, @org.jetbrains.annotations.Nullable()
    com.bmw.drivingcoach.data.db.entity.SessionEntity session, @org.jetbrains.annotations.NotNull()
    java.util.List<com.bmw.drivingcoach.data.db.entity.LapEntity> laps, @org.jetbrains.annotations.NotNull()
    java.util.List<com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity> insights, @org.jetbrains.annotations.Nullable()
    com.bmw.drivingcoach.data.db.entity.LapEntity bestLap, float consistencyScore, @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.entity.ProcessingStatus processingStatus, @org.jetbrains.annotations.Nullable()
    java.lang.String error) {
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