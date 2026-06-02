package com.bmw.drivingcoach.ui.session;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.bmw.drivingcoach.data.db.dao.LapDao;
import com.bmw.drivingcoach.data.db.entity.LapEntity;
import dagger.hilt.android.lifecycle.HiltViewModel;
import kotlinx.coroutines.flow.StateFlow;
import javax.inject.Inject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u0017\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0018\u0010\u0012\u001a\u0004\u0018\u00010\u00132\u0006\u0010\n\u001a\u00020\u000bH\u0082@\u00a2\u0006\u0002\u0010\u0014J\b\u0010\u0015\u001a\u00020\u0016H\u0002R\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0017\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\t0\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011\u00a8\u0006\u0017"}, d2 = {"Lcom/bmw/drivingcoach/ui/session/LapDetailViewModel;", "Landroidx/lifecycle/ViewModel;", "lapDao", "Lcom/bmw/drivingcoach/data/db/dao/LapDao;", "savedStateHandle", "Landroidx/lifecycle/SavedStateHandle;", "(Lcom/bmw/drivingcoach/data/db/dao/LapDao;Landroidx/lifecycle/SavedStateHandle;)V", "_uiState", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/bmw/drivingcoach/ui/session/LapDetailUiState;", "lapId", "", "getLapId", "()J", "uiState", "Lkotlinx/coroutines/flow/StateFlow;", "getUiState", "()Lkotlinx/coroutines/flow/StateFlow;", "findLapById", "Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "loadLapDetails", "", "app_debug"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class LapDetailViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.data.db.dao.LapDao lapDao = null;
    private final long lapId = 0L;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.bmw.drivingcoach.ui.session.LapDetailUiState> _uiState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.bmw.drivingcoach.ui.session.LapDetailUiState> uiState = null;
    
    @javax.inject.Inject()
    public LapDetailViewModel(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.dao.LapDao lapDao, @org.jetbrains.annotations.NotNull()
    androidx.lifecycle.SavedStateHandle savedStateHandle) {
        super();
    }
    
    public final long getLapId() {
        return 0L;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.bmw.drivingcoach.ui.session.LapDetailUiState> getUiState() {
        return null;
    }
    
    private final void loadLapDetails() {
    }
    
    private final java.lang.Object findLapById(long lapId, kotlin.coroutines.Continuation<? super com.bmw.drivingcoach.data.db.entity.LapEntity> $completion) {
        return null;
    }
}