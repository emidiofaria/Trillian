package com.drivingcoach.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.drivingcoach.R
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.telemetry.TelemetryFileWriter
import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TelemetryForegroundService : Service(), LocationListener, SensorEventListener {

    companion object {
        private const val TAG = "TelemetryService"
        
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "drivingcoach_recording"
        private const val CHANNEL_NAME = "Recording"
        
        const val ACTION_START_RECORDING = "com.drivingcoach.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.drivingcoach.ACTION_STOP_RECORDING"
        const val EXTRA_SESSION_ID = "session_id"
        
        private const val GPS_MIN_TIME_MS = 100L // 10 Hz
        private const val GPS_MIN_DISTANCE_M = 0f
        private const val GPS_LOCK_TIMEOUT_MS = 5000L
        private const val GPS_SIGNAL_LOST_TIMEOUT_MS = 10000L
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1000L

        fun startRecording(context: Context, sessionId: Long) {
            val intent = Intent(context, TelemetryForegroundService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            context.startForegroundService(intent)
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, TelemetryForegroundService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var sessionDao: SessionDao
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()
    
    private var telemetryWriter: TelemetryFileWriter? = null
    private var currentSessionId: Long = -1
    private var recordingStartTime: Long = 0
    private var lapCount: Int = 0
    private var gpsLocked: Boolean = false
    private var gpsSignalLost: Boolean = false
    private var lastGpsSampleTime: Long = 0
    
    // IMU buffers (latest values)
    private var accelX: Float = 0f
    private var accelY: Float = 0f
    private var accelZ: Float = 0f
    private var gyroX: Float = 0f
    private var gyroY: Float = 0f
    private var gyroZ: Float = 0f
    
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val notificationUpdateRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            updateElapsedTime()
            checkGpsSignalLost()
            handler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL_MS)
        }
    }
    
    private val gpsLockTimeoutRunnable = Runnable {
        if (!gpsLocked) {
            Log.w(TAG, "GPS lock timeout - no fix within ${GPS_LOCK_TIMEOUT_MS}ms")
            updateState()
        }
    }
    
    private val binder = TelemetryBinder()
    
    inner class TelemetryBinder : Binder() {
        fun getService(): TelemetryForegroundService = this@TelemetryForegroundService
        fun getStateFlow(): StateFlow<RecordingState> = state
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
                if (sessionId != -1L) {
                    startRecording(sessionId)
                } else {
                    Log.e(TAG, "Invalid session ID")
                    stopSelf()
                }
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
            else -> {
                // Service started without action - start foreground but stay idle
                startForeground(NOTIFICATION_ID, createNotification("Idle", "Ready to record"))
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        handler.removeCallbacks(notificationUpdateRunnable)
        handler.removeCallbacks(gpsLockTimeoutRunnable)
        serviceScope.cancel()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(sessionId: Long) {
        if (_state.value is RecordingState.Recording) {
            Log.w(TAG, "Already recording, ignoring start request")
            return
        }
        
        // Check GPS permission
        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission denied")
            _state.value = RecordingState.Error("Location permission required")
            stopSelf()
            return
        }
        
        Log.i(TAG, "Starting recording for session: $sessionId")
        
        currentSessionId = sessionId
        recordingStartTime = System.currentTimeMillis()
        lastGpsSampleTime = System.currentTimeMillis()
        lapCount = 0
        gpsLocked = false
        gpsSignalLost = false
        
        // Initialize telemetry writer
        telemetryWriter = TelemetryFileWriter(this, sessionId)
        
        // Start foreground with recording notification
        startForeground(NOTIFICATION_ID, createNotification("Recording", "00:00"))
        
        // Register location listener
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                GPS_MIN_TIME_MS,
                GPS_MIN_DISTANCE_M,
                this,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request location updates", e)
            _state.value = RecordingState.Error("GPS unavailable: ${e.message}")
            stopSelf()
            return
        }
        
        // Register sensor listeners with fallback for permission denial
        accelerometer?.let { sensor ->
            try {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            } catch (e: SecurityException) {
                Log.w(TAG, "High sampling rate denied, falling back to SENSOR_DELAY_GAME", e)
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        gyroscope?.let { sensor ->
            try {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            } catch (e: SecurityException) {
                Log.w(TAG, "High sampling rate denied for gyroscope, falling back to SENSOR_DELAY_GAME", e)
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        
        // Set GPS lock timeout
        handler.postDelayed(gpsLockTimeoutRunnable, GPS_LOCK_TIMEOUT_MS)
        
        // Start notification updates
        handler.post(notificationUpdateRunnable)
        
        // Update state
        updateState()
    }
    
    private fun stopRecording() {
        if (_state.value !is RecordingState.Recording) {
            Log.w(TAG, "Not recording, ignoring stop request")
            stopSelf()
            return
        }
        
        Log.i(TAG, "Stopping recording for session: $currentSessionId")
        _state.value = RecordingState.Stopping
        
        // Stop updates
        handler.removeCallbacks(notificationUpdateRunnable)
        handler.removeCallbacks(gpsLockTimeoutRunnable)
        
        // Unregister listeners
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
        
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering sensor listener", e)
        }
        
        // Close writer and update database
        serviceScope.launch(Dispatchers.IO) {
            try {
                telemetryWriter?.close()
                telemetryWriter = null
                
                // Update session end time
                sessionDao.updateSessionEndTime(currentSessionId, System.currentTimeMillis())
                
                // Enqueue TelemetryUploadWorker
                androidx.work.WorkManager.getInstance(this@TelemetryForegroundService)
                    .enqueue(com.drivingcoach.data.worker.TelemetryUploadWorker.buildRequest(currentSessionId))
                
                Log.i(TAG, "Recording stopped and saved for session: $currentSessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing recording", e)
            } finally {
                _state.value = RecordingState.Idle
                currentSessionId = -1
                stopSelf()
            }
        }
    }
    
    private fun checkGpsSignalLost() {
        if (!gpsLocked) return
        
        val timeSinceLastSample = System.currentTimeMillis() - lastGpsSampleTime
        val wasSignalLost = gpsSignalLost
        gpsSignalLost = timeSinceLastSample > GPS_SIGNAL_LOST_TIMEOUT_MS
        
        if (gpsSignalLost && !wasSignalLost) {
            Log.w(TAG, "GPS signal lost - no sample for ${timeSinceLastSample}ms")
            updateNotificationGpsLost()
        } else if (!gpsSignalLost && wasSignalLost) {
            Log.i(TAG, "GPS signal recovered")
        }
        
        updateState()
    }
    
    private fun updateNotificationGpsLost() {
        val notification = createNotification(
            "Recording",
            "GPS signal lost — move to open sky"
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun updateState() {
        if (currentSessionId != -1L) {
            val elapsed = System.currentTimeMillis() - recordingStartTime
            _state.value = RecordingState.Recording(
                sessionId = currentSessionId,
                elapsedMs = elapsed,
                lapCount = lapCount,
                gpsLocked = gpsLocked && !gpsSignalLost
            )
        }
    }
    
    private fun updateElapsedTime() {
        val current = _state.value
        if (current is RecordingState.Recording) {
            val elapsed = System.currentTimeMillis() - recordingStartTime
            _state.value = current.copy(
                elapsedMs = elapsed,
                gpsLocked = gpsLocked && !gpsSignalLost
            )
        }
    }
    
    fun incrementLapCount() {
        lapCount++
        updateState()
    }

    // LocationListener implementation
    override fun onLocationChanged(location: Location) {
        try {
            lastGpsSampleTime = System.currentTimeMillis()
            
            if (!gpsLocked) {
                gpsLocked = true
                handler.removeCallbacks(gpsLockTimeoutRunnable)
                Log.i(TAG, "GPS locked")
                updateState()
            }
            
            if (gpsSignalLost) {
                gpsSignalLost = false
                Log.i(TAG, "GPS signal recovered")
                updateState()
            }
            
            val sample = TelemetrySample(
                timestampMs = location.time,
                latitude = location.latitude,
                longitude = location.longitude,
                speedMs = if (location.hasSpeed()) location.speed else 0f,
                headingDeg = if (location.hasBearing()) location.bearing else 0f,
                accelX = accelX,
                accelY = accelY,
                accelZ = accelZ,
                gyroX = gyroX,
                gyroY = gyroY,
                gyroZ = gyroZ,
                gpsAccuracyM = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
            )
            
            serviceScope.launch(Dispatchers.IO) {
                try {
                    telemetryWriter?.writeSample(sample)
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing telemetry sample", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing location", e)
        }
    }

    @Deprecated("Deprecated in API")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
        Log.d(TAG, "GPS status changed: provider=$provider, status=$status")
    }

    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "GPS provider enabled: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.w(TAG, "GPS provider disabled: $provider")
    }

    // SensorEventListener implementation
    override fun onSensorChanged(event: SensorEvent) {
        try {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accelX = event.values[0]
                    accelY = event.values[1]
                    accelZ = event.values[2]
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroX = event.values[0]
                    gyroY = event.values[1]
                    gyroZ = event.values[2]
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing sensor event", e)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: ${sensor?.name}, accuracy=$accuracy")
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Recording telemetry data"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Driving Coach — $title")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun updateNotification() {
        val elapsed = System.currentTimeMillis() - recordingStartTime
        val minutes = (elapsed / 60000).toInt()
        val seconds = ((elapsed % 60000) / 1000).toInt()
        val timeText = String.format("%02d:%02d", minutes, seconds)
        
        val content = if (gpsSignalLost) {
            "GPS signal lost — move to open sky"
        } else {
            val gpsStatus = if (gpsLocked) "GPS ✓" else "GPS..."
            "$timeText • $gpsStatus"
        }
        
        val notification = createNotification("Recording", content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
