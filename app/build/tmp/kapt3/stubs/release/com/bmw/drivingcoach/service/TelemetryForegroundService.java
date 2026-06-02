package com.bmw.drivingcoach.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.bmw.drivingcoach.R;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
import com.bmw.drivingcoach.data.telemetry.TelemetryFileWriter;
import com.bmw.drivingcoach.data.telemetry.TelemetrySample;
import com.bmw.drivingcoach.ui.MainActivity;
import dagger.hilt.android.AndroidEntryPoint;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.flow.StateFlow;
import javax.inject.Inject;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u00b8\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\n\b\u0007\u0018\u0000 ^2\u00020\u00012\u00020\u00022\u00020\u0003:\u0002^_B\u0005\u00a2\u0006\u0002\u0010\u0004J\b\u00104\u001a\u000205H\u0002J\u0018\u00106\u001a\u0002072\u0006\u00108\u001a\u0002092\u0006\u0010:\u001a\u000209H\u0002J\b\u0010;\u001a\u000205H\u0002J\b\u0010<\u001a\u00020\u0015H\u0002J\u0006\u0010=\u001a\u000205J\u001a\u0010>\u001a\u0002052\b\u0010?\u001a\u0004\u0018\u00010\r2\u0006\u0010@\u001a\u00020\u001eH\u0016J\u0012\u0010A\u001a\u00020B2\b\u0010C\u001a\u0004\u0018\u00010DH\u0016J\b\u0010E\u001a\u000205H\u0016J\b\u0010F\u001a\u000205H\u0016J\u0010\u0010G\u001a\u0002052\u0006\u0010H\u001a\u00020IH\u0016J\u0010\u0010J\u001a\u0002052\u0006\u0010K\u001a\u000209H\u0016J\u0010\u0010L\u001a\u0002052\u0006\u0010K\u001a\u000209H\u0016J\u0010\u0010M\u001a\u0002052\u0006\u0010N\u001a\u00020OH\u0016J\"\u0010P\u001a\u00020\u001e2\b\u0010C\u001a\u0004\u0018\u00010D2\u0006\u0010Q\u001a\u00020\u001e2\u0006\u0010R\u001a\u00020\u001eH\u0016J$\u0010S\u001a\u0002052\b\u0010K\u001a\u0004\u0018\u0001092\u0006\u0010T\u001a\u00020\u001e2\b\u0010U\u001a\u0004\u0018\u00010VH\u0017J\u0010\u0010W\u001a\u0002052\u0006\u0010X\u001a\u00020\u0011H\u0003J\b\u0010Y\u001a\u000205H\u0002J\b\u0010Z\u001a\u000205H\u0002J\b\u0010[\u001a\u000205H\u0002J\b\u0010\\\u001a\u000205H\u0002J\b\u0010]\u001a\u000205H\u0002R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\f\u001a\u0004\u0018\u00010\rX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0012\u0010\u000e\u001a\u00060\u000fR\u00020\u0000X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0015X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0015X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001a\u001a\u0004\u0018\u00010\rX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001b\u001a\u00020\u001cX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001d\u001a\u00020\u001eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001f\u001a\u00020\u0011X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010 \u001a\u00020!X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\"\u001a\u00020\u0013X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010#\u001a\u00020\u0011X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010$\u001a\u00020%X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010&\u001a\u00020\'X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001e\u0010(\u001a\u00020)8\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b*\u0010+\"\u0004\b,\u0010-R\u0017\u0010.\u001a\b\u0012\u0004\u0012\u00020\u00070/\u00a2\u0006\b\n\u0000\u001a\u0004\b0\u00101R\u0010\u00102\u001a\u0004\u0018\u000103X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006`"}, d2 = {"Lcom/bmw/drivingcoach/service/TelemetryForegroundService;", "Landroid/app/Service;", "Landroid/location/LocationListener;", "Landroid/hardware/SensorEventListener;", "()V", "_state", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/bmw/drivingcoach/service/RecordingState;", "accelX", "", "accelY", "accelZ", "accelerometer", "Landroid/hardware/Sensor;", "binder", "Lcom/bmw/drivingcoach/service/TelemetryForegroundService$TelemetryBinder;", "currentSessionId", "", "gpsLockTimeoutRunnable", "Ljava/lang/Runnable;", "gpsLocked", "", "gpsSignalLost", "gyroX", "gyroY", "gyroZ", "gyroscope", "handler", "Landroid/os/Handler;", "lapCount", "", "lastGpsSampleTime", "locationManager", "Landroid/location/LocationManager;", "notificationUpdateRunnable", "recordingStartTime", "sensorManager", "Landroid/hardware/SensorManager;", "serviceScope", "Lkotlinx/coroutines/CoroutineScope;", "sessionDao", "Lcom/bmw/drivingcoach/data/db/dao/SessionDao;", "getSessionDao", "()Lcom/bmw/drivingcoach/data/db/dao/SessionDao;", "setSessionDao", "(Lcom/bmw/drivingcoach/data/db/dao/SessionDao;)V", "state", "Lkotlinx/coroutines/flow/StateFlow;", "getState", "()Lkotlinx/coroutines/flow/StateFlow;", "telemetryWriter", "Lcom/bmw/drivingcoach/data/telemetry/TelemetryFileWriter;", "checkGpsSignalLost", "", "createNotification", "Landroid/app/Notification;", "title", "", "content", "createNotificationChannel", "hasLocationPermission", "incrementLapCount", "onAccuracyChanged", "sensor", "accuracy", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "onCreate", "onDestroy", "onLocationChanged", "location", "Landroid/location/Location;", "onProviderDisabled", "provider", "onProviderEnabled", "onSensorChanged", "event", "Landroid/hardware/SensorEvent;", "onStartCommand", "flags", "startId", "onStatusChanged", "status", "extras", "Landroid/os/Bundle;", "startRecording", "sessionId", "stopRecording", "updateElapsedTime", "updateNotification", "updateNotificationGpsLost", "updateState", "Companion", "TelemetryBinder", "app_release"})
public final class TelemetryForegroundService extends android.app.Service implements android.location.LocationListener, android.hardware.SensorEventListener {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "TelemetryService";
    private static final int NOTIFICATION_ID = 1001;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String CHANNEL_ID = "bmw_recording";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String CHANNEL_NAME = "Recording";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_START_RECORDING = "com.bmw.drivingcoach.ACTION_START_RECORDING";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_STOP_RECORDING = "com.bmw.drivingcoach.ACTION_STOP_RECORDING";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_SESSION_ID = "session_id";
    private static final long GPS_MIN_TIME_MS = 100L;
    private static final float GPS_MIN_DISTANCE_M = 0.0F;
    private static final long GPS_LOCK_TIMEOUT_MS = 5000L;
    private static final long GPS_SIGNAL_LOST_TIMEOUT_MS = 10000L;
    private static final long NOTIFICATION_UPDATE_INTERVAL_MS = 1000L;
    @javax.inject.Inject()
    public com.bmw.drivingcoach.data.db.dao.SessionDao sessionDao;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope serviceScope = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.bmw.drivingcoach.service.RecordingState> _state = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.bmw.drivingcoach.service.RecordingState> state = null;
    @org.jetbrains.annotations.Nullable()
    private com.bmw.drivingcoach.data.telemetry.TelemetryFileWriter telemetryWriter;
    private long currentSessionId = -1L;
    private long recordingStartTime = 0L;
    private int lapCount = 0;
    private boolean gpsLocked = false;
    private boolean gpsSignalLost = false;
    private long lastGpsSampleTime = 0L;
    private float accelX = 0.0F;
    private float accelY = 0.0F;
    private float accelZ = 0.0F;
    private float gyroX = 0.0F;
    private float gyroY = 0.0F;
    private float gyroZ = 0.0F;
    private android.location.LocationManager locationManager;
    private android.hardware.SensorManager sensorManager;
    @org.jetbrains.annotations.Nullable()
    private android.hardware.Sensor accelerometer;
    @org.jetbrains.annotations.Nullable()
    private android.hardware.Sensor gyroscope;
    @org.jetbrains.annotations.NotNull()
    private final android.os.Handler handler = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.Runnable notificationUpdateRunnable = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.Runnable gpsLockTimeoutRunnable = null;
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.service.TelemetryForegroundService.TelemetryBinder binder = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.bmw.drivingcoach.service.TelemetryForegroundService.Companion Companion = null;
    
    public TelemetryForegroundService() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.bmw.drivingcoach.data.db.dao.SessionDao getSessionDao() {
        return null;
    }
    
    public final void setSessionDao(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.dao.SessionDao p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.bmw.drivingcoach.service.RecordingState> getState() {
        return null;
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    @java.lang.Override()
    public int onStartCommand(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public android.os.IBinder onBind(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent) {
        return null;
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    @android.annotation.SuppressLint(value = {"MissingPermission"})
    private final void startRecording(long sessionId) {
    }
    
    private final void stopRecording() {
    }
    
    private final void checkGpsSignalLost() {
    }
    
    private final void updateNotificationGpsLost() {
    }
    
    private final void updateState() {
    }
    
    private final void updateElapsedTime() {
    }
    
    public final void incrementLapCount() {
    }
    
    @java.lang.Override()
    public void onLocationChanged(@org.jetbrains.annotations.NotNull()
    android.location.Location location) {
    }
    
    @java.lang.Override()
    @java.lang.Deprecated()
    public void onStatusChanged(@org.jetbrains.annotations.Nullable()
    java.lang.String provider, int status, @org.jetbrains.annotations.Nullable()
    android.os.Bundle extras) {
    }
    
    @java.lang.Override()
    public void onProviderEnabled(@org.jetbrains.annotations.NotNull()
    java.lang.String provider) {
    }
    
    @java.lang.Override()
    public void onProviderDisabled(@org.jetbrains.annotations.NotNull()
    java.lang.String provider) {
    }
    
    @java.lang.Override()
    public void onSensorChanged(@org.jetbrains.annotations.NotNull()
    android.hardware.SensorEvent event) {
    }
    
    @java.lang.Override()
    public void onAccuracyChanged(@org.jetbrains.annotations.Nullable()
    android.hardware.Sensor sensor, int accuracy) {
    }
    
    private final boolean hasLocationPermission() {
        return false;
    }
    
    private final void createNotificationChannel() {
    }
    
    private final android.app.Notification createNotification(java.lang.String title, java.lang.String content) {
        return null;
    }
    
    private final void updateNotification() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00008\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\nJ\u000e\u0010\u0018\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u0016R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\nX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\nX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\nX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0019"}, d2 = {"Lcom/bmw/drivingcoach/service/TelemetryForegroundService$Companion;", "", "()V", "ACTION_START_RECORDING", "", "ACTION_STOP_RECORDING", "CHANNEL_ID", "CHANNEL_NAME", "EXTRA_SESSION_ID", "GPS_LOCK_TIMEOUT_MS", "", "GPS_MIN_DISTANCE_M", "", "GPS_MIN_TIME_MS", "GPS_SIGNAL_LOST_TIMEOUT_MS", "NOTIFICATION_ID", "", "NOTIFICATION_UPDATE_INTERVAL_MS", "TAG", "startRecording", "", "context", "Landroid/content/Context;", "sessionId", "stopRecording", "app_release"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final void startRecording(@org.jetbrains.annotations.NotNull()
        android.content.Context context, long sessionId) {
        }
        
        public final void stopRecording(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0004\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\u0003\u001a\u00020\u0004J\f\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u00a8\u0006\b"}, d2 = {"Lcom/bmw/drivingcoach/service/TelemetryForegroundService$TelemetryBinder;", "Landroid/os/Binder;", "(Lcom/bmw/drivingcoach/service/TelemetryForegroundService;)V", "getService", "Lcom/bmw/drivingcoach/service/TelemetryForegroundService;", "getStateFlow", "Lkotlinx/coroutines/flow/StateFlow;", "Lcom/bmw/drivingcoach/service/RecordingState;", "app_release"})
    public final class TelemetryBinder extends android.os.Binder {
        
        public TelemetryBinder() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.bmw.drivingcoach.service.TelemetryForegroundService getService() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final kotlinx.coroutines.flow.StateFlow<com.bmw.drivingcoach.service.RecordingState> getStateFlow() {
            return null;
        }
    }
}