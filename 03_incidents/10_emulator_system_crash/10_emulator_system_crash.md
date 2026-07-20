# Incident: Emulator System Crash During Track Recording

**Date Reported**: 2026-07-22  
**Severity**: TBD  
**Status**: Open  

---

## Description

While collecting track day data on the Android emulator, the phone restarted unexpectedly.

## Reproduction Context

1. User was running DrivingCoach app on Android emulator
2. App was in recording mode collecting track data
3. Emulator restarted without warning

## Observations from Log

**Timeline** (from log file):
- `13:26:02` — GPS locked, app running normally
- `13:26:28` — First ANR in `com.google.android.gms.persistent`
- `13:26:49` — Second ANR in `com.google.android.gms.persistent`
- `13:27:12` — ANR in `com.google.android.gms.unstable`
- `13:28:24` — Binder transaction failure detected
- `13:28:24` — FATAL EXCEPTION: `DeadSystemException: The system died`
- `13:28:24` — Process killed (SIG: 9)
- `13:28:38` — System restarted with new PIDs

**Key Log Entries**:
```
2026-07-22 13:28:24.756 IPCThreadState com.drivingcoach E Binder transaction failure: 6983857/29189/-3
2026-07-22 13:28:24.758 AndroidRuntime com.drivingcoach E FATAL EXCEPTION: main
    Process: com.drivingcoach, PID: 17058
    DeadSystemException: The system died; earlier logs will point to the root cause
2026-07-22 13:28:24.767 Process com.drivingcoach I Sending signal. PID: 17058 SIG: 9
```

## Evidence

- [Android_log_error.txt](./Android_log_error.txt) — Full Android logcat from the session

## Notes

- Multiple ANRs occurred in Google Play Services before the crash
- The app was receiving GPS updates normally until the system died
