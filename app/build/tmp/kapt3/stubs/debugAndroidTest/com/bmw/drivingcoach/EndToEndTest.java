package com.bmw.drivingcoach;

import android.content.Intent;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.recyclerview.widget.RecyclerView;
import com.bmw.drivingcoach.data.api.AuthInterceptor;
import com.bmw.drivingcoach.data.db.BMWDatabase;
import com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity;
import com.bmw.drivingcoach.data.db.entity.LapEntity;
import com.bmw.drivingcoach.data.db.entity.ProcessingStatus;
import com.bmw.drivingcoach.data.db.entity.SessionEntity;
import com.bmw.drivingcoach.ui.MainActivity;
import com.bmw.drivingcoach.ui.onboarding.OnboardingFragment;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import javax.inject.Inject;

@dagger.hilt.android.testing.HiltAndroidTest()
@org.junit.runner.RunWith(value = androidx.test.ext.junit.runners.AndroidJUnit4.class)
@androidx.test.filters.LargeTest()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000B\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0006\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u001d\u001a\u00020\u001eH\u0007J\b\u0010\u001f\u001a\u00020\u001eH\u0007J\b\u0010 \u001a\u00020\u001eH\u0007J\b\u0010!\u001a\u00020\u001eH\u0007J\b\u0010\"\u001a\u00020\u001eH\u0007J\b\u0010#\u001a\u00020\u001eH\u0007R$\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u00048\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0006\u0010\u0007\"\u0004\b\b\u0010\tR\u001e\u0010\n\u001a\u00020\u000b8\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u0013\u0010\u0010\u001a\u00020\u00118G\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u001e\u0010\u0014\u001a\u00020\u00158\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0016\u0010\u0017\"\u0004\b\u0018\u0010\u0019R\u0014\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u001c0\u001bX\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006$"}, d2 = {"Lcom/bmw/drivingcoach/EndToEndTest;", "", "()V", "dataStore", "Landroidx/datastore/core/DataStore;", "Landroidx/datastore/preferences/core/Preferences;", "getDataStore", "()Landroidx/datastore/core/DataStore;", "setDataStore", "(Landroidx/datastore/core/DataStore;)V", "database", "Lcom/bmw/drivingcoach/data/db/BMWDatabase;", "getDatabase", "()Lcom/bmw/drivingcoach/data/db/BMWDatabase;", "setDatabase", "(Lcom/bmw/drivingcoach/data/db/BMWDatabase;)V", "hiltRule", "Ldagger/hilt/android/testing/HiltAndroidRule;", "getHiltRule", "()Ldagger/hilt/android/testing/HiltAndroidRule;", "mockWebServer", "Lokhttp3/mockwebserver/MockWebServer;", "getMockWebServer", "()Lokhttp3/mockwebserver/MockWebServer;", "setMockWebServer", "(Lokhttp3/mockwebserver/MockWebServer;)V", "scenario", "Landroidx/test/core/app/ActivityScenario;", "Lcom/bmw/drivingcoach/ui/MainActivity;", "setup", "", "teardown", "testFullSessionFlow", "testLapComparisonDeltaColors", "testShareCard", "testSignOut", "app_debugAndroidTest"})
public final class EndToEndTest {
    @org.jetbrains.annotations.NotNull()
    private final dagger.hilt.android.testing.HiltAndroidRule hiltRule = null;
    @javax.inject.Inject()
    public com.bmw.drivingcoach.data.db.BMWDatabase database;
    @javax.inject.Inject()
    public okhttp3.mockwebserver.MockWebServer mockWebServer;
    @javax.inject.Inject()
    public androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> dataStore;
    private androidx.test.core.app.ActivityScenario<com.bmw.drivingcoach.ui.MainActivity> scenario;
    
    public EndToEndTest() {
        super();
    }
    
    @org.junit.Rule(order = 0)
    @org.jetbrains.annotations.NotNull()
    public final dagger.hilt.android.testing.HiltAndroidRule getHiltRule() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.bmw.drivingcoach.data.db.BMWDatabase getDatabase() {
        return null;
    }
    
    public final void setDatabase(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.BMWDatabase p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final okhttp3.mockwebserver.MockWebServer getMockWebServer() {
        return null;
    }
    
    public final void setMockWebServer(@org.jetbrains.annotations.NotNull()
    okhttp3.mockwebserver.MockWebServer p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> getDataStore() {
        return null;
    }
    
    public final void setDataStore(@org.jetbrains.annotations.NotNull()
    androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> p0) {
    }
    
    @org.junit.Before()
    public final void setup() {
    }
    
    @org.junit.After()
    public final void teardown() {
    }
    
    @org.junit.Test()
    public final void testFullSessionFlow() {
    }
    
    @org.junit.Test()
    public final void testLapComparisonDeltaColors() {
    }
    
    @org.junit.Test()
    public final void testShareCard() {
    }
    
    @org.junit.Test()
    public final void testSignOut() {
    }
}