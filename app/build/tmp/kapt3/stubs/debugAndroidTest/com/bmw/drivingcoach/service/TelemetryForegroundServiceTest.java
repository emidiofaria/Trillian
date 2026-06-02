package com.bmw.drivingcoach.service;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.TimeoutException;

/**
 * Instrumented tests for TelemetryForegroundService.
 *
 * These tests require a device or emulator with location permissions granted.
 * Run with: ./gradlew connectedAndroidTest
 */
@org.junit.runner.RunWith(value = androidx.test.ext.junit.runners.AndroidJUnit4.class)
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0006\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\t\u001a\u00020\nH\u0007J\b\u0010\u000b\u001a\u00020\nH\u0007J\b\u0010\f\u001a\u00020\nH\u0007J\b\u0010\r\u001a\u00020\nH\u0007J\b\u0010\u000e\u001a\u00020\nH\u0007J\b\u0010\u000f\u001a\u00020\nH\u0007R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u0013\u0010\u0005\u001a\u00020\u00068G\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006\u0010"}, d2 = {"Lcom/bmw/drivingcoach/service/TelemetryForegroundServiceTest;", "", "()V", "context", "Landroid/content/Context;", "serviceRule", "Landroidx/test/rule/ServiceTestRule;", "getServiceRule", "()Landroidx/test/rule/ServiceTestRule;", "elapsedMsIncreasesOverTime", "", "initialStateIsIdle", "serviceReturnsValidBinder", "serviceStartsAndPostsRecordingStateWithin2Seconds", "setUp", "stoppingServiceTransitionsToIdleState", "app_debugAndroidTest"})
public final class TelemetryForegroundServiceTest {
    @org.jetbrains.annotations.NotNull()
    private final androidx.test.rule.ServiceTestRule serviceRule = null;
    private android.content.Context context;
    
    public TelemetryForegroundServiceTest() {
        super();
    }
    
    @org.junit.Rule()
    @org.jetbrains.annotations.NotNull()
    public final androidx.test.rule.ServiceTestRule getServiceRule() {
        return null;
    }
    
    @org.junit.Before()
    public final void setUp() {
    }
    
    @org.junit.Test()
    public final void serviceStartsAndPostsRecordingStateWithin2Seconds() {
    }
    
    @org.junit.Test()
    public final void stoppingServiceTransitionsToIdleState() {
    }
    
    @org.junit.Test()
    public final void elapsedMsIncreasesOverTime() {
    }
    
    @org.junit.Test()
    public final void serviceReturnsValidBinder() {
    }
    
    @org.junit.Test()
    public final void initialStateIsIdle() {
    }
}