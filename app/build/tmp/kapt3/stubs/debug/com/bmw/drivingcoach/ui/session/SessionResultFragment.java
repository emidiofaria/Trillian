package com.bmw.drivingcoach.ui.session;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import com.bmw.drivingcoach.R;
import com.bmw.drivingcoach.data.db.entity.ProcessingStatus;
import com.bmw.drivingcoach.databinding.FragmentSessionResultBinding;
import com.bmw.drivingcoach.util.ShareCardGenerator;
import com.google.android.material.tabs.TabLayoutMediator;
import dagger.hilt.android.AndroidEntryPoint;
import kotlinx.coroutines.Dispatchers;
import java.io.File;
import java.io.FileOutputStream;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u0011H\u0002J\b\u0010\u0012\u001a\u00020\u0013H\u0002J$\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u00172\b\u0010\u0018\u001a\u0004\u0018\u00010\u00192\b\u0010\u001a\u001a\u0004\u0018\u00010\u001bH\u0016J\b\u0010\u001c\u001a\u00020\u0013H\u0016J\u001a\u0010\u001d\u001a\u00020\u00132\u0006\u0010\u001e\u001a\u00020\u00152\b\u0010\u001a\u001a\u0004\u0018\u00010\u001bH\u0016J\u0010\u0010\u001f\u001a\u00020 2\u0006\u0010!\u001a\u00020\"H\u0002J\b\u0010#\u001a\u00020\u0013H\u0002J\b\u0010$\u001a\u00020\u0013H\u0002J\b\u0010%\u001a\u00020\u0013H\u0002J\b\u0010&\u001a\u00020\u0013H\u0002J\u0010\u0010\'\u001a\u00020\u00132\u0006\u0010(\u001a\u00020)H\u0002J\u0010\u0010*\u001a\u00020\u00132\u0006\u0010+\u001a\u00020,H\u0002J\u0010\u0010-\u001a\u00020\u00132\u0006\u0010(\u001a\u00020)H\u0002R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\u00020\u00048BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u001b\u0010\b\u001a\u00020\t8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\f\u0010\r\u001a\u0004\b\n\u0010\u000b\u00a8\u0006."}, d2 = {"Lcom/bmw/drivingcoach/ui/session/SessionResultFragment;", "Landroidx/fragment/app/Fragment;", "()V", "_binding", "Lcom/bmw/drivingcoach/databinding/FragmentSessionResultBinding;", "binding", "getBinding", "()Lcom/bmw/drivingcoach/databinding/FragmentSessionResultBinding;", "viewModel", "Lcom/bmw/drivingcoach/ui/session/SessionResultViewModel;", "getViewModel", "()Lcom/bmw/drivingcoach/ui/session/SessionResultViewModel;", "viewModel$delegate", "Lkotlin/Lazy;", "formatLapTime", "", "durationMs", "", "observeViewModel", "", "onCreateView", "Landroid/view/View;", "inflater", "Landroid/view/LayoutInflater;", "container", "Landroid/view/ViewGroup;", "savedInstanceState", "Landroid/os/Bundle;", "onDestroyView", "onViewCreated", "view", "saveBitmapToCache", "Ljava/io/File;", "bitmap", "Landroid/graphics/Bitmap;", "setupRetryButton", "setupToolbar", "setupViewPager", "shareSession", "updateProcessingCard", "state", "Lcom/bmw/drivingcoach/ui/session/SessionUiState;", "updateProcessingSteps", "status", "Lcom/bmw/drivingcoach/data/db/entity/ProcessingStatus;", "updateUI", "app_debug"})
public final class SessionResultFragment extends androidx.fragment.app.Fragment {
    @org.jetbrains.annotations.Nullable()
    private com.bmw.drivingcoach.databinding.FragmentSessionResultBinding _binding;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy viewModel$delegate = null;
    
    public SessionResultFragment() {
        super();
    }
    
    private final com.bmw.drivingcoach.databinding.FragmentSessionResultBinding getBinding() {
        return null;
    }
    
    private final com.bmw.drivingcoach.ui.session.SessionResultViewModel getViewModel() {
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
    
    private final void setupToolbar() {
    }
    
    private final void setupRetryButton() {
    }
    
    private final void shareSession() {
    }
    
    private final java.io.File saveBitmapToCache(android.graphics.Bitmap bitmap) {
        return null;
    }
    
    private final void setupViewPager() {
    }
    
    private final void observeViewModel() {
    }
    
    private final void updateUI(com.bmw.drivingcoach.ui.session.SessionUiState state) {
    }
    
    private final void updateProcessingCard(com.bmw.drivingcoach.ui.session.SessionUiState state) {
    }
    
    private final void updateProcessingSteps(com.bmw.drivingcoach.data.db.entity.ProcessingStatus status) {
    }
    
    private final java.lang.String formatLapTime(long durationMs) {
        return null;
    }
    
    @java.lang.Override()
    public void onDestroyView() {
    }
}