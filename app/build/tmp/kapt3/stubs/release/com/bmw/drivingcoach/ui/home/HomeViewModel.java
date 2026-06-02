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

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000t\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\t\n\u0002\b\u0004\b\u0007\u0018\u0000 ,2\u00020\u0001:\u0001,B!\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0001\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u0016\u0010\u0019\u001a\u00020\u001a2\f\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u001d0\u001cH\u0002J\u0006\u0010\u001e\u001a\u00020\u001fJ\u001e\u0010 \u001a\u0004\u0018\u00010!2\f\u0010\"\u001a\b\u0012\u0004\u0012\u00020#0\u001cH\u0082@\u00a2\u0006\u0002\u0010$J\b\u0010%\u001a\u00020\u001fH\u0002J\u0006\u0010&\u001a\u00020\u001fJ\u000e\u0010\'\u001a\u00020\u001f2\u0006\u0010(\u001a\u00020)J\u000e\u0010*\u001a\u00020\u001f2\u0006\u0010+\u001a\u00020\u0010R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u000e0\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082D\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u000b0\u0012\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\u000e0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018\u00a8\u0006-"}, d2 = {"Lcom/bmw/drivingcoach/ui/home/HomeViewModel;", "Landroidx/lifecycle/ViewModel;", "sessionDao", "Lcom/bmw/drivingcoach/data/db/dao/SessionDao;", "lapDao", "Lcom/bmw/drivingcoach/data/db/dao/LapDao;", "context", "Landroid/content/Context;", "(Lcom/bmw/drivingcoach/data/db/dao/SessionDao;Lcom/bmw/drivingcoach/data/db/dao/LapDao;Landroid/content/Context;)V", "_events", "Lkotlinx/coroutines/flow/MutableSharedFlow;", "Lcom/bmw/drivingcoach/ui/home/HomeEvent;", "_uiState", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/bmw/drivingcoach/ui/home/HomeUiState;", "currentUserId", "", "events", "Lkotlinx/coroutines/flow/SharedFlow;", "getEvents", "()Lkotlinx/coroutines/flow/SharedFlow;", "uiState", "Lkotlinx/coroutines/flow/StateFlow;", "getUiState", "()Lkotlinx/coroutines/flow/StateFlow;", "computeConsistencyScore", "", "laps", "", "Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "dismissUploadBanner", "", "findOverallBestLap", "Lcom/bmw/drivingcoach/ui/home/BestLapInfo;", "sessions", "Lcom/bmw/drivingcoach/data/db/entity/SessionEntity;", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "loadSessions", "onProfileClick", "onSessionClick", "sessionId", "", "startNewSession", "trackName", "Companion", "app_release"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class HomeViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.data.db.dao.SessionDao sessionDao = null;
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.data.db.dao.LapDao lapDao = null;
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String currentUserId = "demo_user";
    private static final long STALE_UPLOAD_THRESHOLD_MS = 300000L;
    @org.jetbrains.annotations.NotNull()
    private static final java.text.SimpleDateFormat DATE_FORMAT = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.bmw.drivingcoach.ui.home.HomeUiState> _uiState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.bmw.drivingcoach.ui.home.HomeUiState> uiState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableSharedFlow<com.bmw.drivingcoach.ui.home.HomeEvent> _events = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.SharedFlow<com.bmw.drivingcoach.ui.home.HomeEvent> events = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.bmw.drivingcoach.ui.home.HomeViewModel.Companion Companion = null;
    
    @javax.inject.Inject()
    public HomeViewModel(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.dao.SessionDao sessionDao, @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.dao.LapDao lapDao, @dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.bmw.drivingcoach.ui.home.HomeUiState> getUiState() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.SharedFlow<com.bmw.drivingcoach.ui.home.HomeEvent> getEvents() {
        return null;
    }
    
    private final void loadSessions() {
    }
    
    public final void dismissUploadBanner() {
    }
    
    private final java.lang.Object findOverallBestLap(java.util.List<com.bmw.drivingcoach.data.db.entity.SessionEntity> sessions, kotlin.coroutines.Continuation<? super com.bmw.drivingcoach.ui.home.BestLapInfo> $completion) {
        return null;
    }
    
    private final float computeConsistencyScore(java.util.List<com.bmw.drivingcoach.data.db.entity.LapEntity> laps) {
        return 0.0F;
    }
    
    public final void startNewSession(@org.jetbrains.annotations.NotNull()
    java.lang.String trackName) {
    }
    
    public final void onSessionClick(long sessionId) {
    }
    
    public final void onProfileClick() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\u0006R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\n"}, d2 = {"Lcom/bmw/drivingcoach/ui/home/HomeViewModel$Companion;", "", "()V", "DATE_FORMAT", "Ljava/text/SimpleDateFormat;", "STALE_UPLOAD_THRESHOLD_MS", "", "formatDate", "", "timestamp", "app_release"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String formatDate(long timestamp) {
            return null;
        }
    }
}