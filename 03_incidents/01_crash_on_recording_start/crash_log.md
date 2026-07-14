# Crash Log

## Exception

```
java.lang.RuntimeException: Unable to start service com.drivingcoach.service.TelemetryForegroundService@d90acc8 with Intent { act=com.drivingcoach.ACTION_START_RECORDING cmp=com.drivingcoach/.service.TelemetryForegroundService (has extras) }:
java.lang.SecurityException: To use the sampling rate of 0 microseconds, app needs to declare the normal permission HIGH_SAMPLING_RATE_SENSORS.
```

## Stack Trace

```
at android.app.ActivityThread.handleServiceArgs(ActivityThread.java:5139)
at android.app.ActivityThread.-$$Nest$mhandleServiceArgs(Unknown Source:0)
at android.app.ActivityThread$H.handleMessage(ActivityThread.java:2476)
at android.os.Handler.dispatchMessage(Handler.java:106)
at android.os.Looper.loopOnce(Looper.java:222)
at android.os.Looper.loop(Looper.java:314)
at android.app.ActivityThread.main(ActivityThread.java:8788)
at java.lang.reflect.Method.invoke(Native Method)
at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:569)
at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1090)
```

## Caused by

```
java.lang.SecurityException: To use the sampling rate of 0 microseconds, app needs to declare the normal permission HIGH_SAMPLING_RATE_SENSORS.
at android.hardware.SystemSensorManager$BaseEventQueue.enableSensor(SystemSensorManager.java:956)
at android.hardware.SystemSensorManager$BaseEventQueue.addSensor(SystemSensorManager.java:876)
at android.hardware.SystemSensorManager.registerListenerImpl(SystemSensorManager.java:326)
at android.hardware.SensorManager.registerListener(SensorManager.java:855)
at android.hardware.SensorManager.registerListener(SensorManager.java:762)
at com.drivingcoach.service.TelemetryForegroundService.startRecording(TelemetryForegroundService.kt:226)
at com.drivingcoach.service.TelemetryForegroundService.onStartCommand(TelemetryForegroundService.kt:150)
at android.app.ActivityThread.handleServiceArgs(ActivityThread.java:5121)
... 9 more
```
