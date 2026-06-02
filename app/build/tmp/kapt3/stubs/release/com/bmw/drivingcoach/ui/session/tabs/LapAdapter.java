package com.bmw.drivingcoach.ui.session.tabs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bmw.drivingcoach.R;
import com.bmw.drivingcoach.data.db.entity.LapEntity;
import com.bmw.drivingcoach.databinding.FragmentLapsBinding;
import com.bmw.drivingcoach.databinding.ItemLapCardBinding;
import com.bmw.drivingcoach.ui.session.SessionResultFragmentDirections;
import com.bmw.drivingcoach.ui.session.SessionResultViewModel;
import dagger.hilt.android.AndroidEntryPoint;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0002\u0018\u00002\u0012\u0012\u0004\u0012\u00020\u0002\u0012\b\u0012\u00060\u0003R\u00020\u00000\u0001:\u0001\u0013B\u0019\u0012\u0012\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u00060\u0005\u00a2\u0006\u0002\u0010\u0007J\u001c\u0010\t\u001a\u00020\u00062\n\u0010\n\u001a\u00060\u0003R\u00020\u00002\u0006\u0010\u000b\u001a\u00020\fH\u0016J\u001c\u0010\r\u001a\u00060\u0003R\u00020\u00002\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\fH\u0016J\u0010\u0010\u0011\u001a\u00020\u00062\b\u0010\u0012\u001a\u0004\u0018\u00010\u0002R\u0010\u0010\b\u001a\u0004\u0018\u00010\u0002X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u00060\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0014"}, d2 = {"Lcom/bmw/drivingcoach/ui/session/tabs/LapAdapter;", "Landroidx/recyclerview/widget/ListAdapter;", "Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "Lcom/bmw/drivingcoach/ui/session/tabs/LapAdapter$LapViewHolder;", "onLapClick", "Lkotlin/Function1;", "", "(Lkotlin/jvm/functions/Function1;)V", "bestLap", "onBindViewHolder", "holder", "position", "", "onCreateViewHolder", "parent", "Landroid/view/ViewGroup;", "viewType", "setBestLap", "lap", "LapViewHolder", "app_release"})
final class LapAdapter extends androidx.recyclerview.widget.ListAdapter<com.bmw.drivingcoach.data.db.entity.LapEntity, com.bmw.drivingcoach.ui.session.tabs.LapAdapter.LapViewHolder> {
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function1<com.bmw.drivingcoach.data.db.entity.LapEntity, kotlin.Unit> onLapClick = null;
    @org.jetbrains.annotations.Nullable()
    private com.bmw.drivingcoach.data.db.entity.LapEntity bestLap;
    
    public LapAdapter(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super com.bmw.drivingcoach.data.db.entity.LapEntity, kotlin.Unit> onLapClick) {
        super(null);
    }
    
    public final void setBestLap(@org.jetbrains.annotations.Nullable()
    com.bmw.drivingcoach.data.db.entity.LapEntity lap) {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public com.bmw.drivingcoach.ui.session.tabs.LapAdapter.LapViewHolder onCreateViewHolder(@org.jetbrains.annotations.NotNull()
    android.view.ViewGroup parent, int viewType) {
        return null;
    }
    
    @java.lang.Override()
    public void onBindViewHolder(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.ui.session.tabs.LapAdapter.LapViewHolder holder, int position) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\b\u0086\u0004\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0018\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\b\u0010\t\u001a\u0004\u0018\u00010\bJ\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0002J\u0010\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\u000f\u001a\u00020\rH\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0010"}, d2 = {"Lcom/bmw/drivingcoach/ui/session/tabs/LapAdapter$LapViewHolder;", "Landroidx/recyclerview/widget/RecyclerView$ViewHolder;", "binding", "Lcom/bmw/drivingcoach/databinding/ItemLapCardBinding;", "(Lcom/bmw/drivingcoach/ui/session/tabs/LapAdapter;Lcom/bmw/drivingcoach/databinding/ItemLapCardBinding;)V", "bind", "", "lap", "Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "bestLap", "formatLapTime", "", "durationMs", "", "formatSectorTime", "ms", "app_release"})
    public final class LapViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        @org.jetbrains.annotations.NotNull()
        private final com.bmw.drivingcoach.databinding.ItemLapCardBinding binding = null;
        
        public LapViewHolder(@org.jetbrains.annotations.NotNull()
        com.bmw.drivingcoach.databinding.ItemLapCardBinding binding) {
            super(null);
        }
        
        public final void bind(@org.jetbrains.annotations.NotNull()
        com.bmw.drivingcoach.data.db.entity.LapEntity lap, @org.jetbrains.annotations.Nullable()
        com.bmw.drivingcoach.data.db.entity.LapEntity bestLap) {
        }
        
        private final java.lang.String formatLapTime(long durationMs) {
            return null;
        }
        
        private final java.lang.String formatSectorTime(long ms) {
            return null;
        }
    }
}