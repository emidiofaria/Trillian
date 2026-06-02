package com.bmw.drivingcoach.ui.recording;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import com.bmw.drivingcoach.R;
import com.bmw.drivingcoach.databinding.FragmentRecordingBinding;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000h\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0011\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0018\u0002\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u001c\u001a\u00020\u001dH\u0002J\u0010\u0010\u001e\u001a\u00020\u00132\u0006\u0010\u001f\u001a\u00020 H\u0002J\u0010\u0010!\u001a\u00020\u001d2\u0006\u0010\"\u001a\u00020 H\u0002J\b\u0010#\u001a\u00020\u001dH\u0002J$\u0010$\u001a\u00020%2\u0006\u0010&\u001a\u00020\'2\b\u0010(\u001a\u0004\u0018\u00010)2\b\u0010*\u001a\u0004\u0018\u00010+H\u0016J\b\u0010,\u001a\u00020\u001dH\u0016J\b\u0010-\u001a\u00020\u001dH\u0016J\b\u0010.\u001a\u00020\u001dH\u0016J\u001a\u0010/\u001a\u00020\u001d2\u0006\u00100\u001a\u00020%2\b\u0010*\u001a\u0004\u0018\u00010+H\u0016J\b\u00101\u001a\u00020\u001dH\u0002J\b\u00102\u001a\u00020\u001dH\u0002J\b\u00103\u001a\u00020\u001dH\u0002J\u0010\u00104\u001a\u00020\u001d2\u0006\u00105\u001a\u00020\u000fH\u0002J\u0010\u00106\u001a\u00020\u001d2\u0006\u00107\u001a\u000208H\u0002R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001b\u0010\u0005\u001a\u00020\u00068BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\t\u0010\n\u001a\u0004\b\u0007\u0010\bR\u0014\u0010\u000b\u001a\u00020\u00048BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\f\u0010\rR\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0010\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00130\u00120\u0011X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00130\u0012X\u0082\u0004\u00a2\u0006\u0004\n\u0002\u0010\u0015R\u001b\u0010\u0016\u001a\u00020\u00178BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u001a\u0010\u001b\u001a\u0004\b\u0018\u0010\u0019\u00a8\u00069"}, d2 = {"Lcom/bmw/drivingcoach/ui/recording/RecordingFragment;", "Landroidx/fragment/app/Fragment;", "()V", "_binding", "Lcom/bmw/drivingcoach/databinding/FragmentRecordingBinding;", "args", "Lcom/bmw/drivingcoach/ui/recording/RecordingFragmentArgs;", "getArgs", "()Lcom/bmw/drivingcoach/ui/recording/RecordingFragmentArgs;", "args$delegate", "Landroidx/navigation/NavArgsLazy;", "binding", "getBinding", "()Lcom/bmw/drivingcoach/databinding/FragmentRecordingBinding;", "hasNavigatedToResult", "", "permissionLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "", "", "requiredPermissions", "[Ljava/lang/String;", "viewModel", "Lcom/bmw/drivingcoach/ui/recording/RecordingViewModel;", "getViewModel", "()Lcom/bmw/drivingcoach/ui/recording/RecordingViewModel;", "viewModel$delegate", "Lkotlin/Lazy;", "checkPermissionsAndStart", "", "formatElapsedTime", "elapsedMs", "", "navigateToSessionResult", "sessionId", "observeViewModel", "onCreateView", "Landroid/view/View;", "inflater", "Landroid/view/LayoutInflater;", "container", "Landroid/view/ViewGroup;", "savedInstanceState", "Landroid/os/Bundle;", "onDestroyView", "onStart", "onStop", "onViewCreated", "view", "setupUI", "showPermissionDeniedMessage", "startRecordingIfReady", "updateGpsStatus", "locked", "updateUI", "state", "Lcom/bmw/drivingcoach/ui/recording/RecordingUiState;", "app_release"})
public final class RecordingFragment extends androidx.fragment.app.Fragment {
    @org.jetbrains.annotations.Nullable()
    private com.bmw.drivingcoach.databinding.FragmentRecordingBinding _binding;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy viewModel$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.navigation.NavArgsLazy args$delegate = null;
    private boolean hasNavigatedToResult = false;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String[] requiredPermissions = {"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"};
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<java.lang.String[]> permissionLauncher = null;
    
    public RecordingFragment() {
        super();
    }
    
    private final com.bmw.drivingcoach.databinding.FragmentRecordingBinding getBinding() {
        return null;
    }
    
    private final com.bmw.drivingcoach.ui.recording.RecordingViewModel getViewModel() {
        return null;
    }
    
    private final com.bmw.drivingcoach.ui.recording.RecordingFragmentArgs getArgs() {
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
    
    @java.lang.Override()
    public void onStart() {
    }
    
    @java.lang.Override()
    public void onStop() {
    }
    
    private final void setupUI() {
    }
    
    private final void observeViewModel() {
    }
    
    private final void updateUI(com.bmw.drivingcoach.ui.recording.RecordingUiState state) {
    }
    
    private final void updateGpsStatus(boolean locked) {
    }
    
    private final java.lang.String formatElapsedTime(long elapsedMs) {
        return null;
    }
    
    private final void checkPermissionsAndStart() {
    }
    
    private final void startRecordingIfReady() {
    }
    
    private final void showPermissionDeniedMessage() {
    }
    
    private final void navigateToSessionResult(long sessionId) {
    }
    
    @java.lang.Override()
    public void onDestroyView() {
    }
}