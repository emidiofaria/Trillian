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

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000B\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0007\u0018\u0000 \u001e2\u00020\u0001:\u0001\u001eB\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0010\u001a\u00020\u0011H\u0002J$\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u00152\b\u0010\u0016\u001a\u0004\u0018\u00010\u00172\b\u0010\u0018\u001a\u0004\u0018\u00010\u0019H\u0016J\b\u0010\u001a\u001a\u00020\u0011H\u0016J\u001a\u0010\u001b\u001a\u00020\u00112\u0006\u0010\u001c\u001a\u00020\u00132\b\u0010\u0018\u001a\u0004\u0018\u00010\u0019H\u0016J\b\u0010\u001d\u001a\u00020\u0011H\u0002R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\u00020\u00048BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\b\u0010\tR\u001b\u0010\n\u001a\u00020\u000b8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u000e\u0010\u000f\u001a\u0004\b\f\u0010\r\u00a8\u0006\u001f"}, d2 = {"Lcom/bmw/drivingcoach/ui/session/tabs/LapsFragment;", "Landroidx/fragment/app/Fragment;", "()V", "_binding", "Lcom/bmw/drivingcoach/databinding/FragmentLapsBinding;", "adapter", "Lcom/bmw/drivingcoach/ui/session/tabs/LapAdapter;", "binding", "getBinding", "()Lcom/bmw/drivingcoach/databinding/FragmentLapsBinding;", "parentViewModel", "Lcom/bmw/drivingcoach/ui/session/SessionResultViewModel;", "getParentViewModel", "()Lcom/bmw/drivingcoach/ui/session/SessionResultViewModel;", "parentViewModel$delegate", "Lkotlin/Lazy;", "observeViewModel", "", "onCreateView", "Landroid/view/View;", "inflater", "Landroid/view/LayoutInflater;", "container", "Landroid/view/ViewGroup;", "savedInstanceState", "Landroid/os/Bundle;", "onDestroyView", "onViewCreated", "view", "setupRecyclerView", "Companion", "app_release"})
public final class LapsFragment extends androidx.fragment.app.Fragment {
    @org.jetbrains.annotations.Nullable()
    private com.bmw.drivingcoach.databinding.FragmentLapsBinding _binding;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy parentViewModel$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.ui.session.tabs.LapAdapter adapter = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ARG_SESSION_ID = "sessionId";
    @org.jetbrains.annotations.NotNull()
    public static final com.bmw.drivingcoach.ui.session.tabs.LapsFragment.Companion Companion = null;
    
    public LapsFragment() {
        super();
    }
    
    private final com.bmw.drivingcoach.databinding.FragmentLapsBinding getBinding() {
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
    
    private final void setupRecyclerView() {
    }
    
    private final void observeViewModel() {
    }
    
    @java.lang.Override()
    public void onDestroyView() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\t"}, d2 = {"Lcom/bmw/drivingcoach/ui/session/tabs/LapsFragment$Companion;", "", "()V", "ARG_SESSION_ID", "", "newInstance", "Lcom/bmw/drivingcoach/ui/session/tabs/LapsFragment;", "sessionId", "", "app_release"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.bmw.drivingcoach.ui.session.tabs.LapsFragment newInstance(long sessionId) {
            return null;
        }
    }
}