package com.bmw.drivingcoach.ui.onboarding;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.fragment.app.Fragment;
import com.bmw.drivingcoach.R;
import com.bmw.drivingcoach.databinding.FragmentOnboardingBinding;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000h\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0010\u0011\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010$\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010\"\n\u0002\b\u0003\b\u0007\u0018\u0000 22\u00020\u0001:\u00012B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0016\u001a\u00020\u0017H\u0002J\b\u0010\u0018\u001a\u00020\u0019H\u0002J\u001c\u0010\u001a\u001a\u00020\u00192\u0012\u0010\u001b\u001a\u000e\u0012\u0004\u0012\u00020\u0012\u0012\u0004\u0012\u00020\u00170\u001cH\u0002J\u0010\u0010\u001d\u001a\u00020\u00172\u0006\u0010\u001e\u001a\u00020\u0012H\u0002J$\u0010\u001f\u001a\u00020 2\u0006\u0010!\u001a\u00020\"2\b\u0010#\u001a\u0004\u0018\u00010$2\b\u0010%\u001a\u0004\u0018\u00010&H\u0016J\b\u0010\'\u001a\u00020\u0019H\u0016J\b\u0010(\u001a\u00020\u0019H\u0016J\u001a\u0010)\u001a\u00020\u00192\u0006\u0010*\u001a\u00020 2\b\u0010%\u001a\u0004\u0018\u00010&H\u0016J\b\u0010+\u001a\u00020\u0019H\u0002J\b\u0010,\u001a\u00020\u0019H\u0002J\b\u0010-\u001a\u00020\u0019H\u0002J\u0016\u0010.\u001a\u00020\u00192\f\u0010/\u001a\b\u0012\u0004\u0012\u00020\u001200H\u0002J\b\u00101\u001a\u00020\u0019H\u0002R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\u00020\u00048BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R$\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\t8\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000eR\u001a\u0010\u000f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00120\u00110\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00120\u00118BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0014\u0010\u0015\u00a8\u00063"}, d2 = {"Lcom/bmw/drivingcoach/ui/onboarding/OnboardingFragment;", "Landroidx/fragment/app/Fragment;", "()V", "_binding", "Lcom/bmw/drivingcoach/databinding/FragmentOnboardingBinding;", "binding", "getBinding", "()Lcom/bmw/drivingcoach/databinding/FragmentOnboardingBinding;", "dataStore", "Landroidx/datastore/core/DataStore;", "Landroidx/datastore/preferences/core/Preferences;", "getDataStore", "()Landroidx/datastore/core/DataStore;", "setDataStore", "(Landroidx/datastore/core/DataStore;)V", "permissionLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "", "", "requiredPermissions", "getRequiredPermissions", "()[Ljava/lang/String;", "areAllPermissionsGranted", "", "completeOnboarding", "", "handlePermissionResults", "permissions", "", "isPermissionGranted", "permission", "onCreateView", "Landroid/view/View;", "inflater", "Landroid/view/LayoutInflater;", "container", "Landroid/view/ViewGroup;", "savedInstanceState", "Landroid/os/Bundle;", "onDestroyView", "onResume", "onViewCreated", "view", "openAppSettings", "requestPermissions", "setupClickListeners", "showDeniedState", "deniedPermissions", "", "updatePermissionStatus", "Companion", "app_release"})
public final class OnboardingFragment extends androidx.fragment.app.Fragment {
    @javax.inject.Inject()
    public androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> dataStore;
    @org.jetbrains.annotations.Nullable()
    private com.bmw.drivingcoach.databinding.FragmentOnboardingBinding _binding;
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<java.lang.String[]> permissionLauncher = null;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.datastore.preferences.core.Preferences.Key<java.lang.Boolean> KEY_ONBOARDING_COMPLETE = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.bmw.drivingcoach.ui.onboarding.OnboardingFragment.Companion Companion = null;
    
    public OnboardingFragment() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> getDataStore() {
        return null;
    }
    
    public final void setDataStore(@org.jetbrains.annotations.NotNull()
    androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> p0) {
    }
    
    private final com.bmw.drivingcoach.databinding.FragmentOnboardingBinding getBinding() {
        return null;
    }
    
    private final java.lang.String[] getRequiredPermissions() {
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
    public void onResume() {
    }
    
    private final void setupClickListeners() {
    }
    
    private final void requestPermissions() {
    }
    
    private final void handlePermissionResults(java.util.Map<java.lang.String, java.lang.Boolean> permissions) {
    }
    
    private final void showDeniedState(java.util.Set<java.lang.String> deniedPermissions) {
    }
    
    private final void updatePermissionStatus() {
    }
    
    private final boolean isPermissionGranted(java.lang.String permission) {
        return false;
    }
    
    private final boolean areAllPermissionsGranted() {
        return false;
    }
    
    private final void completeOnboarding() {
    }
    
    private final void openAppSettings() {
    }
    
    @java.lang.Override()
    public void onDestroyView() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u0017\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\b"}, d2 = {"Lcom/bmw/drivingcoach/ui/onboarding/OnboardingFragment$Companion;", "", "()V", "KEY_ONBOARDING_COMPLETE", "Landroidx/datastore/preferences/core/Preferences$Key;", "", "getKEY_ONBOARDING_COMPLETE", "()Landroidx/datastore/preferences/core/Preferences$Key;", "app_release"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final androidx.datastore.preferences.core.Preferences.Key<java.lang.Boolean> getKEY_ONBOARDING_COMPLETE() {
            return null;
        }
    }
}