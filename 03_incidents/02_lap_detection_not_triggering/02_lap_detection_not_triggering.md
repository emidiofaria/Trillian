# Bug Report: Lap Detection Not Triggering After Recording Stop

| Field | Value |
|-------|-------|
| **Application** | Driving Coach |
| **Package** | `com.drivingcoach` |
| **Severity** | Critical |
| **Priority** | High |
| **Status** | Resolved |
| **Reported Date** | 2026-07-14 |
| **Resolved Date** | 2026-07-14 |

---

## Summary

After completing multiple laps and stopping the recording, the app displays "No laps detected. Complete at least 2 laps." despite the user completing 3+ laps on the track. Local lap detection feature is implemented but never executes.

---

## Steps to Reproduce

1. Open the Driving Coach app
2. Set up a track with start/finish line (2 GPS points)
3. Start recording session
4. Complete 3 or more laps crossing the start line
5. Stop recording
6. **Result**: "No laps detected" message shown

---

## Expected Behavior

After stopping the recording, the app should:
1. Process the telemetry file locally
2. Detect lap crossings using the start line
3. Display lap times to the user

## Actual Behavior

- Recording stops successfully
- Telemetry file is created and saved
- Upload worker starts (TelemetryUploadWorker runs)
- **Lap detection never runs** - no LocalLapDetector logs appear
- User sees "No laps detected" message

---

## Diagnostic Evidence

### Logs Present
```
TelemetryService        I  Stopping recording for session: 1
TelemetryFileWriter     D  Closed telemetry writer for session 1
TelemetryService        I  Recording stopped and saved for session: 1
TelemetryUploadWorker   I  Starting upload for session: 1, attempt: 0
```

### Logs Absent (Should Have Appeared)
```
RecordingViewModel      D  Triggering local lap processing for session 1
LocalLapDetector        D  === LAP DETECTION START ===
LocalLapDetector        D  Detected X line crossings
```

---

## Environment

| Component | Details |
|-----------|---------|
| Android Version | 12+ (API 31+) |
| Device | Android Emulator |
| Affected Component | `RecordingViewModel.kt` |
| Related Components | `TelemetryForegroundService.kt`, `LocalLapDetector.kt` |

---

## Root Cause Summary

The `processLapsLocally()` method was designed to be triggered when the service emitted `RecordingState.Idle`, but the service calls `stopSelf()` immediately after emitting the state, which destroys the service binding before the ViewModel can observe it.

---

## Resolution

Changed the triggering mechanism to call `processLapsLocally()` directly from `stopRecording()` instead of relying on the service state flow observation.

---

## Attachments

- `02_RCA_lap_detection_not_triggering.md` - Full root cause analysis
