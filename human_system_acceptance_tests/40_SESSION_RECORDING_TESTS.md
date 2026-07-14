# Session Recording Tests

> **Purpose:** Validate the live recording functionality including GPS capture, foreground service behavior, screen state, and background operation. **These tests should be performed at an actual track or parking lot with space to drive.**

---

## Test Environment

| Item | Required State | Check |
|------|----------------|-------|
| Location | Outdoors with clear GPS sky view | ☐ |
| Vehicle | Safe vehicle for testing (parking lot OK) | ☐ |
| Device | Mounted securely in vehicle | ☐ |
| Battery | >50% charged | ☐ |
| Network | Connected (WiFi or cellular) | ☐ |

**Safety Note:** Ensure a passenger operates the device, or test in stationary mode for UI validation.

---

## Test Suite: REC — Recording Flow

### REC-01: Start Recording Screen

**Objective:** Verify Recording screen appears after start and displays correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Complete track setup (valid line) | Line set | ☐ |
| 2 | Tap START RECORDING | Screen transitions | ☐ |
| 3 | Recording screen appears | Full screen, dark background | ☐ |
| 4 | App logo/branding visible | Top of screen | ☐ |
| 5 | Elapsed time displayed | Large MM:SS.mmm format | ☐ |
| 6 | GPS status indicator visible | Below time | ☐ |
| 7 | STOP button visible | Large red button at bottom | ☐ |

**Requirement Coverage:** SR-01, SR-04, SR-05, layout requirements

---

### REC-02: Elapsed Time Counter

**Objective:** Verify elapsed time updates correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording | Timer at 00:00.000 | ☐ |
| 2 | Wait 5 seconds | Timer shows ~00:05.xxx | ☐ |
| 3 | Timer format | MM:SS.mmm (e.g., 00:05.234) | ☐ |
| 4 | Updates smoothly | ~1 second intervals | ☐ |
| 5 | Wait 1 minute | Timer shows ~01:00.xxx | ☐ |
| 6 | Wait 10 minutes | Timer shows ~10:00.xxx | ☐ |

**Requirement Coverage:** SR-04

---

### REC-03: GPS Status Indicator

**Objective:** Verify GPS status indicator shows correct state.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording (outdoors) | Status indicator visible | ☐ |
| 2 | Wait for GPS lock | Status changes to "GPS LOCKED" | ☐ |
| 3 | Status indicator color | GREEN dot/icon when locked | ☐ |
| 4 | Move to indoor/garage | Wait for signal loss | ☐ |
| 5 | After ~10s without fix | Status shows "ACQUIRING GPS..." | ☐ |
| 6 | Status indicator color | RED dot/icon when acquiring | ☐ |
| 7 | Return outdoors | Status returns to "GPS LOCKED" | ☐ |

**Requirement Coverage:** SR-05, TC-09, TC-10

---

### REC-04: REC Badge Animation

**Objective:** Verify blinking REC badge during recording.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording | REC badge visible | ☐ |
| 2 | Observe badge | Red pill/badge shape | ☐ |
| 3 | Animation | Pulsing/blinking alpha (1→0→1) | ☐ |
| 4 | Animation timing | ~1 second repeat cycle | ☐ |
| 5 | Badge persists | Continues throughout recording | ☐ |

**Requirement Coverage:** SR-06

---

### REC-05: Screen Stays On

**Objective:** Verify screen does not turn off during recording.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Check device screen timeout | Note setting (e.g., 30s) | ☐ |
| 2 | Start recording | Recording screen shown | ☐ |
| 3 | Wait beyond screen timeout | Do not touch device | ☐ |
| 4 | Observe screen | Screen remains ON | ☐ |
| 5 | Wait 2 minutes | Screen still ON | ☐ |
| 6 | Stop recording | Session ends | ☐ |
| 7 | Wait on result screen | Screen timeout resumes (may dim) | ☐ |

**Requirement Coverage:** SR-07

---

### REC-06: Stop Recording

**Objective:** Verify stop recording flow.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | During active recording | Recording screen shown | ☐ |
| 2 | Tap STOP RECORDING button | Button responds | ☐ |
| 3 | Confirmation (if any) | Confirm or immediate stop | ☐ |
| 4 | Recording stops | Timer stops | ☐ |
| 5 | Screen transitions | Session Result screen appears | ☐ |
| 6 | Session data saved | Session appears in history | ☐ |

**Requirement Coverage:** SR-02, SR-03

---

### REC-07: Permission Denied Handling

**Objective:** Verify recording fails gracefully without location permission.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Revoke Location permission | Settings → Apps → Permissions | ☐ |
| 2 | Try to start recording | Permission request shown | ☐ |
| 3 | Deny permission | Denied | ☐ |
| 4 | Error message | "Location permission is required" | ☐ |
| 5 | Service behavior | Does not start or stops itself | ☐ |
| 6 | Returns to previous screen | Not stuck on Recording | ☐ |

**Requirement Coverage:** SR-08

---

## Test Suite: SVC — Foreground Service

### SVC-01: Foreground Service Notification

**Objective:** Verify foreground service notification appears and updates.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording | Recording begins | ☐ |
| 2 | Pull down notification shade | Notification visible | ☐ |
| 3 | Notification title | "Driving Coach" | ☐ |
| 4 | Notification content | "Recording — MM:SS" | ☐ |
| 5 | Observe notification | Time updates (~every second) | ☐ |
| 6 | Notification icon | App icon visible | ☐ |
| 7 | Cannot dismiss | Swipe doesn't remove it | ☐ |
| 8 | Stop recording | Notification disappears | ☐ |

**Requirement Coverage:** SR-09, SR-10

---

### SVC-02: GPS Signal Lost Notification

**Objective:** Verify notification updates when GPS signal is lost.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording outdoors | GPS locked | ☐ |
| 2 | Move to location with no GPS | Parking garage, indoors | ☐ |
| 3 | Wait 10+ seconds | Signal lost detected | ☐ |
| 4 | Check notification | "GPS signal lost — move to open sky" | ☐ |
| 5 | Return to GPS coverage | Clear sky view | ☐ |
| 6 | Check notification | Returns to normal "Recording" | ☐ |

**Requirement Coverage:** TC-09, TC-10

---

### SVC-03: Background Recording

**Objective:** Verify recording continues when app is backgrounded.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording | Recording active | ☐ |
| 2 | Press Home button | App goes to background | ☐ |
| 3 | Check notification | Still shows "Recording" | ☐ |
| 4 | Wait 30 seconds | In background | ☐ |
| 5 | Open another app | Navigate away | ☐ |
| 6 | Return to Driving Coach | Open from notification or recent | ☐ |
| 7 | Elapsed time | Continued counting (not reset) | ☐ |
| 8 | GPS data collected | Telemetry file grows | ☐ |

**Requirement Coverage:** SR-09

---

### SVC-04: Recording Survives Process Kill

**Objective:** Verify recording survives aggressive process management.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording | Recording active | ☐ |
| 2 | Press Home | App backgrounded | ☐ |
| 3 | Open many other apps | Trigger memory pressure | ☐ |
| 4 | Check notification | Still recording | ☐ |
| 5 | Return to app | Recording still active | ☐ |

---

## Test Suite: TEL — Telemetry Capture

### TEL-01: GPS Data Quality at Track

**Objective:** Verify GPS captures correctly at track speeds.

**Environment:** Actual driving on track or parking lot.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording at track start/finish | GPS locked | ☐ |
| 2 | Drive one slow lap (~30 km/h) | Complete lap | ☐ |
| 3 | Drive normal laps (track pace) | Multiple laps | ☐ |
| 4 | Observe GPS indicator | Stays GREEN throughout | ☐ |
| 5 | Stop recording | Session saved | ☐ |
| 6 | Check processing status | Backend detects laps | ☐ |

**Requirement Coverage:** TC-01, TC-02, LD-02

---

### TEL-02: 10 Hz GPS Sample Rate

**Objective:** Verify GPS samples at approximately 10 Hz.

**Note:** May require backend log inspection.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Record 60-second session | Telemetry captured | ☐ |
| 2 | Stop and upload | File uploaded | ☐ |
| 3 | Inspect telemetry file (if accessible) | ~600 samples | ☐ |
| 4 | Or check backend logs | "Loaded X samples" ≈ duration×10 | ☐ |

**Requirement Coverage:** TC-01

---

### TEL-03: IMU Data Capture

**Objective:** Verify accelerometer and gyroscope data is captured.

**Note:** Requires file inspection or backend validation.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Record session with vehicle movement | Acceleration changes | ☐ |
| 2 | Include braking, turns | Various IMU events | ☐ |
| 3 | Stop and upload | File uploaded | ☐ |
| 4 | Inspect sample data | accelX/Y/Z values present | ☐ |
| 5 | Non-zero values | IMU captured motion | ☐ |

**Requirement Coverage:** TC-03, TC-04, TC-05

---

### TEL-04: GPS Accuracy Warning

**Objective:** Verify app warns when GPS accuracy is poor.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording in poor GPS area | Near buildings, trees | ☐ |
| 2 | Observe GPS indicator | May show warning color/state | ☐ |
| 3 | Accuracy < 10m expected | Best quality | ☐ |
| 4 | Accuracy > 10m | Visual warning shown | ☐ |

**Requirement Coverage:** TC-11

---

### TEL-05: Long Session Stability (30 minutes)

**Objective:** Verify app handles 30-minute recording without issues.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording | Recording active | ☐ |
| 2 | Record for 30 minutes | Continuous driving | ☐ |
| 3 | Monitor during session | No crashes, ANRs | ☐ |
| 4 | Check battery drain | <15% consumption | ☐ |
| 5 | Stop recording | Session saves successfully | ☐ |
| 6 | Upload completes | Within 30 seconds | ☐ |
| 7 | Sample count | ~18,000 samples | ☐ |

**Requirement Coverage:** NF-04, NF-06, NF-07

---

## Recording Tests Summary

| Test ID | Test Name | Status |
|---------|-----------|--------|
| REC-01 | Start Recording Screen | ☐ Pass ☐ Fail |
| REC-02 | Elapsed Time Counter | ☐ Pass ☐ Fail |
| REC-03 | GPS Status Indicator | ☐ Pass ☐ Fail |
| REC-04 | REC Badge Animation | ☐ Pass ☐ Fail |
| REC-05 | Screen Stays On | ☐ Pass ☐ Fail |
| REC-06 | Stop Recording | ☐ Pass ☐ Fail |
| REC-07 | Permission Denied Handling | ☐ Pass ☐ Fail |
| SVC-01 | Foreground Service Notification | ☐ Pass ☐ Fail |
| SVC-02 | GPS Signal Lost Notification | ☐ Pass ☐ Fail |
| SVC-03 | Background Recording | ☐ Pass ☐ Fail |
| SVC-04 | Recording Survives Process Kill | ☐ Pass ☐ Fail |
| TEL-01 | GPS Data Quality at Track | ☐ Pass ☐ Fail |
| TEL-02 | 10 Hz GPS Sample Rate | ☐ Pass ☐ Fail |
| TEL-03 | IMU Data Capture | ☐ Pass ☐ Fail |
| TEL-04 | GPS Accuracy Warning | ☐ Pass ☐ Fail |
| TEL-05 | Long Session Stability | ☐ Pass ☐ Fail |

---

**Tester:** ___________________ **Date:** ___________________

**Track/Location:** ___________________ 

**Weather Conditions:** ___________________ 

**Notes:**
```




```

---

*Document ID: SAT-REC-001 | Version: 1.0 | Date: 2026-05-06*
