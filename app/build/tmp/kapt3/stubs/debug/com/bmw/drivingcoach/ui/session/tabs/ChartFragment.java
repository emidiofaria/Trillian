package com.bmw.drivingcoach.ui.session.tabs;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import com.bmw.drivingcoach.R;
import com.bmw.drivingcoach.data.db.entity.LapEntity;
import com.bmw.drivingcoach.databinding.FragmentChartBinding;
import com.bmw.drivingcoach.ui.session.SessionResultViewModel;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import dagger.hilt.android.AndroidEntryPoint;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000L\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\b\u0007\u0018\u0000 $2\u00020\u0001:\u0001$B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f2\u0006\u0010\u0011\u001a\u00020\u0012H\u0002J\b\u0010\u0013\u001a\u00020\u0014H\u0002J$\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u00182\b\u0010\u0019\u001a\u0004\u0018\u00010\u001a2\b\u0010\u001b\u001a\u0004\u0018\u00010\u001cH\u0016J\b\u0010\u001d\u001a\u00020\u0014H\u0016J\u001a\u0010\u001e\u001a\u00020\u00142\u0006\u0010\u001f\u001a\u00020\u00162\b\u0010\u001b\u001a\u0004\u0018\u00010\u001cH\u0016J\b\u0010 \u001a\u00020\u0014H\u0002J \u0010!\u001a\u00020\u00142\f\u0010\"\u001a\b\u0012\u0004\u0012\u00020\u00120\u000f2\b\u0010#\u001a\u0004\u0018\u00010\u0012H\u0002R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\u00020\u00048BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u001b\u0010\b\u001a\u00020\t8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\f\u0010\r\u001a\u0004\b\n\u0010\u000b\u00a8\u0006%"}, d2 = {"Lcom/bmw/drivingcoach/ui/session/tabs/ChartFragment;", "Landroidx/fragment/app/Fragment;", "()V", "_binding", "Lcom/bmw/drivingcoach/databinding/FragmentChartBinding;", "binding", "getBinding", "()Lcom/bmw/drivingcoach/databinding/FragmentChartBinding;", "parentViewModel", "Lcom/bmw/drivingcoach/ui/session/SessionResultViewModel;", "getParentViewModel", "()Lcom/bmw/drivingcoach/ui/session/SessionResultViewModel;", "parentViewModel$delegate", "Lkotlin/Lazy;", "generateSpeedEntries", "", "Lcom/github/mikephil/charting/data/Entry;", "lap", "Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "observeViewModel", "", "onCreateView", "Landroid/view/View;", "inflater", "Landroid/view/LayoutInflater;", "container", "Landroid/view/ViewGroup;", "savedInstanceState", "Landroid/os/Bundle;", "onDestroyView", "onViewCreated", "view", "setupChart", "updateChart", "laps", "bestLap", "Companion", "app_debug"})
public final class ChartFragment extends androidx.fragment.app.Fragment {
    @org.jetbrains.annotations.Nullable()
    private com.bmw.drivingcoach.databinding.FragmentChartBinding _binding;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy parentViewModel$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ARG_SESSION_ID = "sessionId";
    @org.jetbrains.annotations.NotNull()
    public static final com.bmw.drivingcoach.ui.session.tabs.ChartFragment.Companion Companion = null;
    
    public ChartFragment() {
        super();
    }
    
    private final com.bmw.drivingcoach.databinding.FragmentChartBinding getBinding() {
        return null;
    }
    
    private final com.bmw.drivingcoach.ui.session.SessionResultViewModel getParentViewModel() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public android.view.View onCreateView(@org.jetbrains.annotations.NotNull()
    android.view.LayoutInflater inflater, @org.jetbrains.annotations.Nullable()
    android.view.ViewGroup container, @org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
        return null;
    }
    
    @java.lang.Override()
    public void onViewCreated(@org.jetbrains.annotations.NotNull()
    android.view.View view, @org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void setupChart() {
    }
    
    private final void observeViewModel() {
    }
    
    private final void updateChart(java.util.List<com.bmw.drivingcoach.data.db.entity.LapEntity> laps, com.bmw.drivingcoach.data.db.entity.LapEntity bestLap) {
    }
    
    private final java.util.List<com.github.mikephil.charting.data.Entry> generateSpeedEntries(com.bmw.drivingcoach.data.db.entity.LapEntity lap) {
        return null;
    }
    
    @java.lang.Override()
    public void onDestroyView() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\t"}, d2 = {"Lcom/bmw/drivingcoach/ui/session/tabs/ChartFragment$Companion;", "", "()V", "ARG_SESSION_ID", "", "newInstance", "Lcom/bmw/drivingcoach/ui/session/tabs/ChartFragment;", "sessionId", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.bmw.drivingcoach.ui.session.tabs.ChartFragment newInstance(long sessionId) {
            return null;
        }
    }
}