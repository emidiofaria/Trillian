# RCA: Emulator System Crash During Track Recording

## Incident Summary

During track data recording on the Android emulator, the system_server process became unresponsive and ultimately crashed, causing a `DeadSystemException` in the DrivingCoach app. The root cause was a cascade of ANRs in Google Play Services (GMS) processes that destabilized the emulator's system_server, not any defect in the DrivingCoach application code.

## Impact

- **Users Affected**: 1 (developer/tester on emulator)
- **Duration**: ~2 minutes (from first ANR at 13:26:28 to system restart at 13:28:38)
- **Data Loss**: Active recording session lost (telemetry not saved due to abrupt termination)
- **Severity**: LOW (emulator-specific, not a production issue)

## Timeline

| Time | Event | Evidence |
|------|-------|----------|
| 13:26:02 | GPS locked, app recording normally | Log: `TelemetryService: GPS locked` |
| 13:26:28 | **1st ANR** in `com.google.android.gms.persistent` | Log: `ANR in com.google.android.gms.persistent` (DispatchingService) |
| 13:26:49 | **2nd ANR** in `com.google.android.gms.persistent` | Log: `ANR in com.google.android.gms.persistent` (DeviceConnectionWatcherService) |
| 13:27:12 | **3rd ANR** in `com.google.android.gms.unstable` | Log: `ANR in com.google.android.gms.unstable` (DroidGuardService) |
| 13:28:24 | Binder transaction failure, system_server dies | Log: `Binder transaction failure: 6983857/29189/-3` |
| 13:28:24 | DrivingCoach receives `DeadSystemException` | Log: `FATAL EXCEPTION: main` / `DeadSystemException` |
| 13:28:24 | Process killed (SIGKILL) | Log: `Sending signal. PID: 17058 SIG: 9` |
| 13:28:38 | System restarted with new PIDs | Log: `SystemServerTimingAsync` with new PID 21177 |

## Signals Observed

### Present (Expected)
- `TelemetryService: GPS locked` — recording started successfully
- Regular EGL frame timing (~1000ms intervals) — app rendering normally throughout
- CPU pressure metrics normal (`avg10=7-15%`) — no CPU starvation

### Present (Unexpected)
- Multiple ANRs in Google Play Services processes
- `Binder transaction failure: 6983857/29189/-3` — system IPC failure
- `DeadSystemException` — entire Android runtime collapsed

### Absent (Expected but Missing)
- No DrivingCoach-specific errors before system death
- No memory pressure warnings (`avg10=0.00` in all ANR dumps)
- No I/O pressure warnings
- No app-level ANRs in DrivingCoach

## Systems Involved

| Component | Role in Incident | Reference |
|-----------|------------------|-----------|
| `system_server` | Crashed, causing system-wide failure | Android OS |
| `com.google.android.gms.persistent` | Source of first 2 ANRs | Google Play Services |
| `com.google.android.gms.unstable` | Source of 3rd ANR (DroidGuard) | Google Play Services |
| `TelemetryForegroundService` | Victim — killed when system died | components.md |
| Android Emulator | Runtime environment with known GMS stability issues | N/A |

## Evidence

### Primary Evidence

**1. ANR Chain in GMS (3 ANRs in 44 seconds)**
```
13:26:28 ANR in com.google.android.gms.persistent
         Reason: executing service DispatchingService
         
13:26:49 ANR in com.google.android.gms.persistent  
         Reason: executing service DeviceConnectionWatcherService
         
13:27:12 ANR in com.google.android.gms.unstable
         Reason: executing service DroidGuardService
```

**2. Binder Transaction Failure**
```
13:28:24 IPCThreadState E Binder transaction failure: 6983857/29189/-3
```
Error code `-3` indicates `DEAD_OBJECT` — the target process (system_server) was dead.

**3. DeadSystemException**
```
13:28:24 AndroidRuntime E FATAL EXCEPTION: main
    Process: com.drivingcoach, PID: 17058
    DeadSystemException: The system died; earlier logs will point to the root cause
```
This exception is thrown when an app tries to communicate with a dead system_server.

**4. DrivingCoach Was Operating Normally**
- GPS locked at 13:26:02
- EGL frame timing consistent throughout (no jank)
- CPU usage: 4.5% → 8.7% — normal for sensor capture + rendering
- No DrivingCoach errors before DeadSystemException

### Secondary Evidence

- CPU pressure metrics showed 7-15% CPU stall time — elevated for emulator but not critical
- system_server CPU usage spiked (9.1% → 24% → 20%) handling ANRs
- `com.google.android.gms.unstable` showed major page faults (1532 major faults) indicating memory pressure in GMS

### Negative Evidence

- **No DrivingCoach ANR** — app was responsive
- **No memory pressure** — `/proc/pressure/memory` shows `avg10=0.00`
- **No disk I/O pressure** — `/proc/pressure/io` shows `avg10=0.00` to `0.41`
- **No network errors** — no HTTP failures in logs
- **No TelemetryService errors** — no GPS loss, no write failures

## Hypotheses

### Primary Hypothesis
**Emulator GMS Instability Caused System Crash**

- Evidence for:
  - 3 ANRs in GMS processes in rapid succession (44 seconds)
  - DroidGuardService (security attestation) is known to be problematic in emulators
  - `com.google.android.gms.unstable` process name indicates sandbox isolation for unstable components
  - system_server CPU spiked handling ANR broadcasts
  - Major page faults (1532) in GMS suggest resource starvation
  - DeadSystemException explicitly states "earlier logs will point to the root cause" — pointing to the ANRs
  
- Evidence against:
  - None

- Confidence: **HIGH (95%)**

### Eliminated Hypotheses

| Hypothesis | Eliminated Because |
|------------|-------------------|
| DrivingCoach caused system crash | No app-level ANRs, normal CPU/memory usage, no errors before system death |
| Memory exhaustion | `/proc/pressure/memory` shows near-zero pressure |
| High sensor load | Sensor service CPU normal (5.3%), no sensor errors logged |
| Network timeout cascade | No network calls logged during incident window |

## Root Cause

**Root Cause**: Google Play Services instability on Android Emulator

The emulator's Google Play Services implementation experienced cascading ANRs in background services (DispatchingService → DeviceConnectionWatcherService → DroidGuardService). The repeated ANRs overwhelmed system_server, which is responsible for broadcasting ANR events and managing process lifecycle. Eventually system_server crashed or became unresponsive, triggering a system-wide restart.

**Trigger**: DroidGuardService ANR (security attestation service known to be problematic in emulated environments)

**Contributing Factors**:
1. Emulator limitations — GMS services behave less stably than on physical devices
2. High-frequency sensor capture (10Hz GPS + 200Hz IMU) may have stressed emulator's virtual HAL
3. DroidGuard security checks are particularly problematic on emulators lacking hardware attestation

## Confidence Level

**Overall Confidence**: HIGH (95%)

**Confidence Rationale**:
- Clear causal chain: GMS ANRs → system_server overload → system death → DeadSystemException
- DrivingCoach was explicitly a victim, not a cause (no errors, normal operation until system died)
- `DeadSystemException` documentation confirms this is a system-level failure

**Remaining Uncertainty**:
- Cannot determine exact trigger inside DroidGuardService without GMS source code
- Unknown if specific emulator version has known bugs

## Mitigation

### Immediate (User Recovery)
1. Restart the emulator — system has already recovered
2. Re-run the recording session — no action required in app

### Short-term (Prevent Recurrence)
1. Use a physical device for long recording sessions to avoid emulator GMS instability
2. Consider using an emulator image without Google Play Services for basic testing

### Permanent (Systemic Fix)
1. **No app changes required** — this is not a DrivingCoach defect
2. Consider adding `DeadSystemException` handling to gracefully inform user of system crash
3. Auto-save telemetry periodically (currently buffers in memory until explicit save)

## Prevention Recommendations

### Code Changes
- **Optional**: Implement periodic telemetry flush (every 30s) to minimize data loss on unexpected termination
- **Optional**: Catch `DeadSystemException` in critical paths and show user-friendly error message

### Monitoring
- No additional monitoring needed — this is an emulator environment issue

### Documentation
- Document that extended recording sessions should be tested on physical devices
- Note emulator GMS instability as a known limitation for telemetry-intensive testing

### Process
- For track recording QA, prioritize physical device testing over emulator

---

## Pattern Classification

**Pattern Match**: No match — Novel incident (Emulator-specific system crash)

This incident does not match any existing failure patterns in `failure-patterns.md` because it originates from the Android OS/emulator layer rather than the DrivingCoach application. Consider adding an "Emulator Stability Limitations" documentation section rather than a failure pattern.

---

## Appendix: Key Log Excerpts

### ANR #1 (13:26:28)
```
ANR in com.google.android.gms.persistent
PID: 20436
Reason: executing service com.google.android.gms/com.google.android.location.reporting.service.DispatchingService
```

### ANR #3 (13:27:12)
```
ANR in com.google.android.gms.unstable
PID: 20639
Reason: executing service com.google.android.gms/.droidguard.DroidGuardService
```

### System Death (13:28:24)
```
IPCThreadState E Binder transaction failure: 6983857/29189/-3
AndroidRuntime E FATAL EXCEPTION: main
    Process: com.drivingcoach, PID: 17058
    DeadSystemException: The system died; earlier logs will point to the root cause
Process I Sending signal. PID: 17058 SIG: 9
```
