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

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000b\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0007\b\u0007\u0018\u00002\u00020\u0001B/\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\fJ\u0016\u0010\u001a\u001a\u00020\u001b2\f\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u001e0\u001dH\u0002J\b\u0010\u001f\u001a\u00020 H\u0002J\b\u0010!\u001a\u00020 H\u0014J\u000e\u0010\"\u001a\u00020 H\u0082@\u00a2\u0006\u0002\u0010#J\u0006\u0010$\u001a\u00020 J\b\u0010%\u001a\u00020 H\u0002J\b\u0010&\u001a\u00020 H\u0002R\u0014\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000f0\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0010\u001a\u0004\u0018\u00010\u0011X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0011\u0010\u0012\u001a\u00020\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0017\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\u000f0\u0017\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0019R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\'"}, d2 = {"Lcom/bmw/drivingcoach/ui/session/SessionResultViewModel;", "Landroidx/lifecycle/ViewModel;", "sessionDao", "Lcom/bmw/drivingcoach/data/db/dao/SessionDao;", "lapDao", "Lcom/bmw/drivingcoach/data/db/dao/LapDao;", "coachingInsightDao", "Lcom/bmw/drivingcoach/data/db/dao/CoachingInsightDao;", "workManager", "Landroidx/work/WorkManager;", "savedStateHandle", "Landroidx/lifecycle/SavedStateHandle;", "(Lcom/bmw/drivingcoach/data/db/dao/SessionDao;Lcom/bmw/drivingcoach/data/db/dao/LapDao;Lcom/bmw/drivingcoach/data/db/dao/CoachingInsightDao;Landroidx/work/WorkManager;Landroidx/lifecycle/SavedStateHandle;)V", "_uiState", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/bmw/drivingcoach/ui/session/SessionUiState;", "pollingJob", "Lkotlinx/coroutines/Job;", "sessionId", "", "getSessionId", "()J", "uiState", "Lkotlinx/coroutines/flow/StateFlow;", "getUiState", "()Lkotlinx/coroutines/flow/StateFlow;", "computeConsistencyScore", "", "laps", "", "Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "loadSession", "", "onCleared", "refreshSession", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "retryAnalysis", "startPolling", "stopPolling", "app_release"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class SessionResultViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.data.db.dao.SessionDao sessionDao = null;
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.data.db.dao.LapDao lapDao = null;
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.data.db.dao.CoachingInsightDao coachingInsightDao = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.work.WorkManager workManager = null;
    private final long sessionId = 0L;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.bmw.drivingcoach.ui.session.SessionUiState> _uiState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.bmw.drivingcoach.ui.session.SessionUiState> uiState = null;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job pollingJob;
    
    @javax.inject.Inject()
    public SessionResultViewModel(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.dao.SessionDao sessionDao, @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.dao.LapDao lapDao, @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.dao.CoachingInsightDao coachingInsightDao, @org.jetbrains.annotations.NotNull()
    androidx.work.WorkManager workManager, @org.jetbrains.annotations.NotNull()
    androidx.lifecycle.SavedStateHandle savedStateHandle) {
        super();
    }
    
    public final long getSessionId() {
        return 0L;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.bmw.drivingcoach.ui.session.SessionUiState> getUiState() {
        return null;
    }
    
    private final void loadSession() {
    }
    
    private final void startPolling() {
    }
    
    private final void stopPolling() {
    }
    
    private final java.lang.Object refreshSession(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    public final void retryAnalysis() {
    }
    
    private final float computeConsistencyScore(java.util.List<com.bmw.drivingcoach.data.db.entity.LapEntity> laps) {
        return 0.0F;
    }
    
    @java.lang.Override()
    protected void onCleared() {
    }
}