package com.bmw.drivingcoach.ui.recording;

import androidx.navigation.Navigation;
import androidx.navigation.testing.TestNavHostController;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.bmw.drivingcoach.R;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Espresso tests for RecordingFragment.
 *
 * Note: These tests require Hilt test dependencies and a test runner.
 * Some tests are marked as integration tests that require the full app context.
 */
@dagger.hilt.android.testing.HiltAndroidTest()
@org.junit.runner.RunWith(value = androidx.test.ext.junit.runners.AndroidJUnit4.class)
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\b\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u000b\u001a\u00020\fH\u0007J\b\u0010\r\u001a\u00020\fH\u0007J\b\u0010\u000e\u001a\u00020\fH\u0007J\b\u0010\u000f\u001a\u00020\fH\u0007J\b\u0010\u0010\u001a\u00020\fH\u0007J\b\u0010\u0011\u001a\u00020\fH\u0007J\b\u0010\u0012\u001a\u00020\fH\u0007J\b\u0010\u0013\u001a\u00020\fH\u0007R\u001c\u0010\u0003\u001a\u00020\u00048GX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u000e\u0010\t\u001a\u00020\nX\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0014"}, d2 = {"Lcom/bmw/drivingcoach/ui/recording/RecordingFragmentTest;", "", "()V", "hiltRule", "Ldagger/hilt/android/testing/HiltAndroidRule;", "getHiltRule", "()Ldagger/hilt/android/testing/HiltAndroidRule;", "setHiltRule", "(Ldagger/hilt/android/testing/HiltAndroidRule;)V", "navController", "Landroidx/navigation/testing/TestNavHostController;", "bmwLogo_isDisplayed", "", "elapsedTimeText_isDisplayed", "gpsStatus_isDisplayed", "recordingIndicator_isDisplayed", "sessionTimeLabel_isDisplayed", "setup", "stopButton_isVisibleAndClickable", "stopButton_triggersNavigation_whenClicked", "app_debugAndroidTest"})
public final class RecordingFragmentTest {
    @org.jetbrains.annotations.NotNull()
    private dagger.hilt.android.testing.HiltAndroidRule hiltRule;
    private androidx.navigation.testing.TestNavHostController navController;
    
    public RecordingFragmentTest() {
        super();
    }
    
    @org.junit.Rule()
    @org.jetbrains.annotations.NotNull()
    public final dagger.hilt.android.testing.HiltAndroidRule getHiltRule() {
        return null;
    }
    
    public final void setHiltRule(@org.jetbrains.annotations.NotNull()
    dagger.hilt.android.testing.HiltAndroidRule p0) {
    }
    
    @org.junit.Before()
    public final void setup() {
    }
    
    @org.junit.Test()
    public final void elapsedTimeText_isDisplayed() {
    }
    
    @org.junit.Test()
    public final void stopButton_isVisibleAndClickable() {
    }
    
    @org.junit.Test()
    public final void gpsStatus_isDisplayed() {
    }
    
    @org.junit.Test()
    public final void recordingIndicator_isDisplayed() {
    }
    
    @org.junit.Test()
    public final void stopButton_triggersNavigation_whenClicked() {
    }
    
    @org.junit.Test()
    public final void bmwLogo_isDisplayed() {
    }
    
    @org.junit.Test()
    public final void sessionTimeLabel_isDisplayed() {
    }
}