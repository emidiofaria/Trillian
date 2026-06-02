package com.bmw.drivingcoach.ui;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import com.bmw.drivingcoach.R;
import com.bmw.drivingcoach.data.api.AuthEvent;
import com.bmw.drivingcoach.data.api.AuthEventBus;
import com.bmw.drivingcoach.databinding.ActivityMainBinding;
import com.bmw.drivingcoach.ui.onboarding.OnboardingFragment;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000B\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0014\u001a\u00020\u0015H\u0002J\b\u0010\u0016\u001a\u00020\u0015H\u0002J\b\u0010\u0017\u001a\u00020\u0015H\u0002J\u0012\u0010\u0018\u001a\u00020\u00152\b\u0010\u0019\u001a\u0004\u0018\u00010\u001aH\u0014J\b\u0010\u001b\u001a\u00020\u001cH\u0016J\b\u0010\u001d\u001a\u00020\u0015H\u0002J\b\u0010\u001e\u001a\u00020\u0015H\u0002J\b\u0010\u001f\u001a\u00020\u0015H\u0002R\u001e\u0010\u0003\u001a\u00020\u00048\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u000e\u0010\t\u001a\u00020\nX\u0082.\u00a2\u0006\u0002\n\u0000R$\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\f8\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000e\u0010\u000f\"\u0004\b\u0010\u0010\u0011R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006 "}, d2 = {"Lcom/bmw/drivingcoach/ui/MainActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "authEventBus", "Lcom/bmw/drivingcoach/data/api/AuthEventBus;", "getAuthEventBus", "()Lcom/bmw/drivingcoach/data/api/AuthEventBus;", "setAuthEventBus", "(Lcom/bmw/drivingcoach/data/api/AuthEventBus;)V", "binding", "Lcom/bmw/drivingcoach/databinding/ActivityMainBinding;", "dataStore", "Landroidx/datastore/core/DataStore;", "Landroidx/datastore/preferences/core/Preferences;", "getDataStore", "()Landroidx/datastore/core/DataStore;", "setDataStore", "(Landroidx/datastore/core/DataStore;)V", "navController", "Landroidx/navigation/NavController;", "handleSessionExpired", "", "hideSystemUI", "observeAuthEvents", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onSupportNavigateUp", "", "setupEdgeToEdge", "setupNavigation", "showSystemUI", "app_debug"})
public final class MainActivity extends androidx.appcompat.app.AppCompatActivity {
    @javax.inject.Inject()
    public androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> dataStore;
    @javax.inject.Inject()
    public com.bmw.drivingcoach.data.api.AuthEventBus authEventBus;
    private com.bmw.drivingcoach.databinding.ActivityMainBinding binding;
    private androidx.navigation.NavController navController;
    
    public MainActivity() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> getDataStore() {
        return null;
    }
    
    public final void setDataStore(@org.jetbrains.annotations.NotNull()
    androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.bmw.drivingcoach.data.api.AuthEventBus getAuthEventBus() {
        return null;
    }
    
    public final void setAuthEventBus(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.api.AuthEventBus p0) {
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void setupEdgeToEdge() {
    }
    
    private final void setupNavigation() {
    }
    
    private final void observeAuthEvents() {
    }
    
    private final void handleSessionExpired() {
    }
    
    private final void hideSystemUI() {
    }
    
    private final void showSystemUI() {
    }
    
    @java.lang.Override()
    public boolean onSupportNavigateUp() {
        return false;
    }
}