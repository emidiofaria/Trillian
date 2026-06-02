package com.bmw.drivingcoach.ui.session;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.bmw.drivingcoach.data.db.dao.LapDao;
import com.bmw.drivingcoach.data.db.entity.LapEntity;
import dagger.hilt.android.lifecycle.HiltViewModel;
import kotlinx.coroutines.flow.StateFlow;
import javax.inject.Inject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00008\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0015\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001BM\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u0012\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u0012\u000e\b\u0002\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b\u0012\b\b\u0002\u0010\n\u001a\u00020\u000b\u0012\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\r\u00a2\u0006\u0002\u0010\u000eJ\t\u0010\u0019\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\u001a\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\u000b\u0010\u001b\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\u000f\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\t0\bH\u00c6\u0003J\t\u0010\u001d\u001a\u00020\u000bH\u00c6\u0003J\u000b\u0010\u001e\u001a\u0004\u0018\u00010\rH\u00c6\u0003JQ\u0010\u001f\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u00052\u000e\b\u0002\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b2\b\b\u0002\u0010\n\u001a\u00020\u000b2\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\rH\u00c6\u0001J\u0013\u0010 \u001a\u00020\u00032\b\u0010!\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\"\u001a\u00020#H\u00d6\u0001J\t\u0010$\u001a\u00020\rH\u00d6\u0001R\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0013\u0010\f\u001a\u0004\u0018\u00010\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0002\u0010\u0013R\u0017\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0010R\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018\u00a8\u0006%"}, d2 = {"Lcom/bmw/drivingcoach/ui/session/LapDetailUiState;", "", "isLoading", "", "selectedLap", "Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "bestLap", "sectorDeltas", "", "Lcom/bmw/drivingcoach/ui/session/SectorDelta;", "totalDeltaMs", "", "error", "", "(ZLcom/bmw/drivingcoach/data/db/entity/LapEntity;Lcom/bmw/drivingcoach/data/db/entity/LapEntity;Ljava/util/List;JLjava/lang/String;)V", "getBestLap", "()Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "getError", "()Ljava/lang/String;", "()Z", "getSectorDeltas", "()Ljava/util/List;", "getSelectedLap", "getTotalDeltaMs", "()J", "component1", "component2", "component3", "component4", "component5", "component6", "copy", "equals", "other", "hashCode", "", "toString", "app_release"})
public final class LapDetailUiState {
    private final boolean isLoading = false;
    @org.jetbrains.annotations.Nullable()
    private final com.bmw.drivingcoach.data.db.entity.LapEntity selectedLap = null;
    @org.jetbrains.annotations.Nullable()
    private final com.bmw.drivingcoach.data.db.entity.LapEntity bestLap = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.bmw.drivingcoach.ui.session.SectorDelta> sectorDeltas = null;
    private final long totalDeltaMs = 0L;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String error = null;
    
    public LapDetailUiState(boolean isLoading, @org.jetbrains.annotations.Nullable()
    com.bmw.drivingcoach.data.db.entity.LapEntity selectedLap, @org.jetbrains.annotations.Nullable()
    com.bmw.drivingcoach.data.db.entity.LapEntity bestLap, @org.jetbrains.annotations.NotNull()
    java.util.List<com.bmw.drivingcoach.ui.session.SectorDelta> sectorDeltas, long totalDeltaMs, @org.jetbrains.annotations.Nullable()
    java.lang.String error) {
        super();
    }
    
    public final boolean isLoading() {
        return false;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.bmw.drivingcoach.data.db.entity.LapEntity getSelectedLap() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.bmw.drivingcoach.data.db.entity.LapEntity getBestLap() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.bmw.drivingcoach.ui.session.SectorDelta> getSectorDeltas() {
        return null;
    }
    
    public final long getTotalDeltaMs() {
        return 0L;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getError() {
        return null;
    }
    
    public LapDetailUiState() {
        super();
    }
    
    public final boolean component1() {
        return false;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.bmw.drivingcoach.data.db.entity.LapEntity component2() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.bmw.drivingcoach.data.db.entity.LapEntity component3() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.bmw.drivingcoach.ui.session.SectorDelta> component4() {
        return null;
    }
    
    public final long component5() {
        return 0L;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component6() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.bmw.drivingcoach.ui.session.LapDetailUiState copy(boolean isLoading, @org.jetbrains.annotations.Nullable()
    com.bmw.drivingcoach.data.db.entity.LapEntity selectedLap, @org.jetbrains.annotations.Nullable()
    com.bmw.drivingcoach.data.db.entity.LapEntity bestLap, @org.jetbrains.annotations.NotNull()
    java.util.List<com.bmw.drivingcoach.ui.session.SectorDelta> sectorDeltas, long totalDeltaMs, @org.jetbrains.annotations.Nullable()
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