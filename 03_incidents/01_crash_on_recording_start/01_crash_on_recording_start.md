# Bug Report: App Crash on Recording Start

| Field | Value |
|-------|-------|
| **Application** | Driving Coach |
| **Package** | `com.drivingcoach` |
| **Severity** | Critical |
| **Priority** | High |
| **Status** | Open |
| **Reported Date** | 2026-05-27 |

---

## Summary

The Driving Coach app crashes immediately when attempting to start a telemetry recording session. The crash is caused by a missing Android permission declaration in the manifest.

---

## Steps to Reproduce

1. Open the Driving Coach app
2. Start a recording session (triggers `ACTION_START_RECORDING`)
3. **Result**: App crashes with `SecurityException`

---

## Expected Behavior

The app should successfully start the telemetry recording using device sensors without crashing.

## Actual Behavior

The app crashes with a `java.lang.SecurityException` when the `TelemetryForegroundService` attempts to register a sensor listener with a sampling rate of 0 microseconds.

---

The app is attempting to use a sensor sampling rate of **0 microseconds** (maximum rate), but the required permission `HIGH_SAMPLING_RATE_SENSORS` is not declared in `AndroidManifest.xml`.

Starting from **Android 12 (API 31)**, apps must declare the `HIGH_SAMPLING_RATE_SENSORS` permission to access sensor data at rates faster than 200Hz.

---

## Exception Details

```
java.lang.SecurityException: To use the sampling rate of 0 microseconds, 
app needs to declare the normal permission HIGH_SAMPLING_RATE_SENSORS.
```

### Stack Trace

```
at android.hardware.SystemSensorManager$BaseEventQueue.enableSensor(SystemSensorManager.java:956)
at android.hardware.SystemSensorManager$BaseEventQueue.addSensor(SystemSensorManager.java:876)
at android.hardware.SystemSensorManager.registerListenerImpl(SystemSensorManager.java:326)
at android.hardware.SensorManager.registerListener(SensorManager.java:855)
at android.hardware.SensorManager.registerListener(SensorManager.java:762)
at com.drivingcoach.service.TelemetryForegroundService.startRecording(TelemetryForegroundService.kt:226)
at com.drivingcoach.service.TelemetryForegroundService.onStartCommand(TelemetryForegroundService.kt:150)
```
---

## Environment

| Component | Details |
|-----------|---------|
| Android Version | 12+ (API 31+) |
| Affected Component | `TelemetryForegroundService.kt` |
| Crash Location | Line 226 (`startRecording` method) |

---

## Attachments

- `crash_log.md` - Full stack trace extracted from device screenshots
