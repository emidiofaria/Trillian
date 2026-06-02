package com.bmw.drivingcoach.ui.recording;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.lifecycle.AndroidViewModel;
import com.bmw.drivingcoach.service.RecordingState;
import com.bmw.drivingcoach.service.TelemetryForegroundService;
import dagger.hilt.android.lifecycle.HiltViewModel;
import kotlinx.coroutines.flow.StateFlow;
import javax.inject.Inject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\u0003\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0006\u0010\u0013\u001a\u00020\u0014J\u0010\u0010\u0015\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u0017H\u0002J\b\u0010\u0018\u001a\u00020\u0014H\u0014J\u000e\u0010\u0019\u001a\u00020\u00142\u0006\u0010\u001a\u001a\u00020\u001bJ\u0006\u0010\u001c\u001a\u00020\u0014J\u0006\u0010\u001d\u001a\u00020\u0014R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\n\u001a\b\u0018\u00010\u000bR\u00020\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00070\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012\u00a8\u0006\u001e"}, d2 = {"Lcom/bmw/drivingcoach/ui/recording/RecordingViewModel;", "Landroidx/lifecycle/AndroidViewModel;", "application", "Landroid/app/Application;", "(Landroid/app/Application;)V", "_uiState", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/bmw/drivingcoach/ui/recording/RecordingUiState;", "isBound", "", "serviceBinder", "Lcom/bmw/drivingcoach/service/TelemetryForegroundService$TelemetryBinder;", "Lcom/bmw/drivingcoach/service/TelemetryForegroundService;", "serviceConnection", "Landroid/content/ServiceConnection;", "uiState", "Lkotlinx/coroutines/flow/StateFlow;", "getUiState", "()Lkotlinx/coroutines/flow/StateFlow;", "bindToService", "", "mapServiceStateToUiState", "state", "Lcom/bmw/drivingcoach/service/RecordingState;", "onCleared", "startRecording", "sessionId", "", "stopRecording", "unbindFromService", "app_debug"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class RecordingViewModel extends androidx.lifecycle.AndroidViewModel {
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.bmw.drivingcoach.ui.recording.RecordingUiState> _uiState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.bmw.drivingcoach.ui.recording.RecordingUiState> uiState = null;
    @org.jetbrains.annotations.Nullable()
    private com.bmw.drivingcoach.service.TelemetryForegroundService.TelemetryBinder serviceBinder;
    private boolean isBound = false;
    @org.jetbrains.annotations.NotNull()
    private final android.content.ServiceConnection serviceConnection = null;
    
    @javax.inject.Inject()
    public RecordingViewModel(@org.jetbrains.annotations.NotNull()
    android.app.Application application) {
        super(null);
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.bmw.drivingcoach.ui.recording.RecordingUiState> getUiState() {
        return null;
    }
    
    /**
     * Starts recording for the given session ID.
     * Binds to the service and sends start action.
     */
    public final void startRecording(long sessionId) {
    }
    
    /**
     * Stops the current recording session.
     */
    public final void stopRecording() {
    }
    
    /**
     * Binds to the TelemetryForegroundService.
     */
    public final void bindToService() {
    }
    
    /**
     * Unbinds from the service.
     */
    public final void unbindFromService() {
    }
    
    private final void mapServiceStateToUiState(com.bmw.drivingcoach.service.RecordingState state) {
    }
    
    @java.lang.Override()
    protected void onCleared() {
    }
}