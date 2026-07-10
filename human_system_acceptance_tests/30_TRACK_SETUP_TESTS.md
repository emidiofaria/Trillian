# Track Setup Tests

> **Purpose:** Validate the start/finish line definition flow using two-point GPS capture. These tests verify the user can correctly define track boundaries before recording.

---

## Test Environment

| Item | Required State | Check |
|------|----------------|-------|
| Device | GPS enabled, location services ON | ☐ |
| Location | Outdoors with clear sky view (track day) | ☐ |
| Network | Not required for track setup | ☐ |
| User | Logged in | ☐ |
| Position | Standing at the start/finish line of the track | ☐ |

---

## Test Suite: TS — Track Setup Flow

### TS-01: Access Track Setup Screen

**Objective:** Verify Track Setup screen is accessible from Home.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Home screen | FAB visible (bottom right) | ☐ |
| 2 | Tap FAB (record button) | New Session dialog appears | ☐ |
| 3 | Enter track name | Track name accepted | ☐ |
| 4 | Tap "Start" | Track Setup screen opens | ☐ |
| 5 | Observe instructions | "SET START/FINISH LINE" title | ☐ |
| 6 | Instruction text reads | "Walk to each edge of the track..." | ☐ |
| 7 | GPS status indicator visible | Shows satellites and accuracy | ☐ |
| 8 | START RECORDING button | Disabled initially | ☐ |

**Requirement Coverage:** TS-01, TS-02, TS-12, TS-14

---

### TS-02: GPS Status Display

**Objective:** Verify GPS status indicator shows accuracy information.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Track Setup screen | GPS status container visible | ☐ |
| 2 | Wait for GPS fix | Status updates | ☐ |
| 3 | GPS indicator dot | Green when ≤10m accuracy | ☐ |
| 4 | GPS indicator dot | Amber when >10m accuracy | ☐ |
| 5 | Status text shows | "GPS: X satellites, ±Ym" format | ☐ |

**Requirement Coverage:** TS-03, TS-04

---

### TS-03: Capture Point A

**Objective:** Verify Point A capture at track edge.

**Setup:** Walk to the LEFT edge of the track at the start/finish line.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Track Setup screen | Point A card displayed | ☐ |
| 2 | Point A label shows | "POINT A (Left Edge)" | ☐ |
| 3 | Point A coords show | "Not captured" | ☐ |
| 4 | CAPTURE button for Point A | Enabled | ☐ |
| 5 | Tap CAPTURE (Point A) | Current GPS location captured | ☐ |
| 6 | Point A coords update | Shows "48.xxxxx, 11.xxxxx" format | ☐ |
| 7 | Point B CAPTURE button | Now enabled | ☐ |
| 8 | CLEAR button appears | Visible | ☐ |
| 9 | START RECORDING button | Still disabled | ☐ |

**Requirement Coverage:** TS-05, TS-06, TS-11

---

### TS-04: Capture Point B and Line Width

**Objective:** Verify Point B capture and distance calculation.

**Setup:** Walk to the RIGHT edge of the track at the start/finish line (opposite side from Point A).

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Point A already captured | Coords displayed | ☐ |
| 2 | Point B CAPTURE button | Enabled | ☐ |
| 3 | Tap CAPTURE (Point B) | Current GPS location captured | ☐ |
| 4 | Point B coords update | Shows "48.xxxxx, 11.xxxxx" format | ☐ |
| 5 | LINE WIDTH label shows | "LINE WIDTH" | ☐ |
| 6 | LINE WIDTH value updates | Shows distance in metres (e.g., "8.2m") | ☐ |
| 7 | If distance ≥3m | START RECORDING enabled | ☐ |

**Requirement Coverage:** TS-07, TS-08, TS-12

---

### TS-05: Line Distance Validation — Too Close

**Objective:** Verify points less than 3m apart are rejected.

**Setup:** Stand still and capture both points without moving.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Capture Point A | Coords captured | ☐ |
| 2 | Without moving, capture Point B | Same approximate location | ☐ |
| 3 | LINE WIDTH shows | <3m value (e.g., "0.5m") | ☐ |
| 4 | Hint text appears | "Minimum 3m required" | ☐ |
| 5 | START RECORDING button | Disabled | ☐ |
| 6 | Tap CLEAR | Points reset | ☐ |
| 7 | Re-capture correctly | Walk to opposite edges | ☐ |

**Requirement Coverage:** TS-09, TS-10

---

### TS-06: Clear Button

**Objective:** Verify CLEAR button resets captured points.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Capture Point A | Coords displayed | ☐ |
| 2 | CLEAR button visible | Shows "CLEAR" | ☐ |
| 3 | Tap CLEAR | Both points reset | ☐ |
| 4 | Point A coords | "Not captured" | ☐ |
| 5 | Point B coords | "Not captured" | ☐ |
| 6 | LINE WIDTH | "--" | ☐ |
| 7 | Point B CAPTURE | Disabled | ☐ |
| 8 | CLEAR button | Hidden | ☐ |
| 9 | START RECORDING | Disabled | ☐ |

**Requirement Coverage:** TS-11

---

### TS-07: Valid Line Distance and Start Recording

**Objective:** Verify valid line allows recording to start.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Walk to left track edge | Position at left | ☐ |
| 2 | Capture Point A | Coords displayed | ☐ |
| 3 | Walk to right track edge | Cross ~5-15m | ☐ |
| 4 | Capture Point B | Coords displayed | ☐ |
| 5 | LINE WIDTH shows | Value between 3-50m | ☐ |
| 6 | START RECORDING button | Enabled | ☐ |
| 7 | Tap START RECORDING | Navigation occurs | ☐ |
| 8 | Recording screen appears | Elapsed time displayed | ☐ |

**Requirement Coverage:** TS-12, TS-13

---

### TS-08: Start/Finish Coordinates Storage

**Objective:** Verify coordinates are stored in SessionEntity.

**Note:** This test requires database inspection or debug logging.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Define valid start/finish line | Both points captured | ☐ |
| 2 | Tap START RECORDING | Recording starts | ☐ |
| 3 | Immediately STOP | Session created | ☐ |
| 4 | Inspect session data | startLineLat1/Lng1 present | ☐ |
| 5 | Verify coordinates | startLineLat2/Lng2 present | ☐ |
| 6 | Values match capture | Close to captured GPS coords | ☐ |

**Requirement Coverage:** TS-13

---

## Test Suite: TS-EDGE — Edge Cases

### TS-EDGE-01: GPS Not Available

**Objective:** Verify behavior when GPS is disabled.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Disable device location services | Location OFF | ☐ |
| 2 | Open Track Setup screen | Screen loads | ☐ |
| 3 | GPS status shows | "Waiting for GPS..." or similar | ☐ |
| 4 | GPS indicator | Amber (not ready) | ☐ |
| 5 | CAPTURE buttons | May work but low accuracy warning | ☐ |
| 6 | Enable GPS | Status updates to show accuracy | ☐ |

---

### TS-EDGE-02: Poor GPS Accuracy

**Objective:** Verify behavior with poor GPS signal.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Go indoors (poor GPS) | Limited satellite visibility | ☐ |
| 2 | Open Track Setup screen | Screen loads | ☐ |
| 3 | GPS indicator | Amber (>10m accuracy) | ☐ |
| 4 | GPS status shows | Accuracy >10m (e.g., "±25m") | ☐ |
| 5 | CAPTURE still works | Can capture but may be inaccurate | ☐ |

---

### TS-EDGE-03: Back Navigation

**Objective:** Verify back button behavior on Track Setup.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open Track Setup | Screen displayed | ☐ |
| 2 | Capture Point A | One point captured | ☐ |
| 3 | Press back button | Returns to Home | ☐ |
| 4 | Open Track Setup again | Fresh state (no points) | ☐ |

---

### TS-EDGE-04: Screen Rotation

**Objective:** Verify state preserved on rotation.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Capture Point A | Coords displayed | ☐ |
| 2 | Rotate device | Landscape mode | ☐ |
| 3 | Point A still captured | Coords preserved | ☐ |
| 4 | Capture Point B | In landscape | ☐ |
| 5 | Rotate back | Portrait mode | ☐ |
| 6 | Both points preserved | LINE WIDTH still shown | ☐ |

---

### TS-EDGE-05: Re-capture Point A After Point B

**Objective:** Verify recapturing requires clearing first.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Capture Point A | Coords displayed | ☐ |
| 2 | Capture Point B | Both captured | ☐ |
| 3 | Point A CAPTURE button | Shows captured state | ☐ |
| 4 | To re-capture | Must tap CLEAR first | ☐ |
| 5 | Tap CLEAR | Both points reset | ☐ |
| 6 | Can start over | Fresh capture | ☐ |

---

## Track Setup Tests Summary

| Test ID | Test Name | Status |
|---------|-----------|--------|
| TS-01 | Access Track Setup Screen | ☐ Pass ☐ Fail |
| TS-02 | GPS Status Display | ☐ Pass ☐ Fail |
| TS-03 | Capture Point A | ☐ Pass ☐ Fail |
| TS-04 | Capture Point B and Line Width | ☐ Pass ☐ Fail |
| TS-05 | Line Distance — Too Close | ☐ Pass ☐ Fail |
| TS-06 | Clear Button | ☐ Pass ☐ Fail |
| TS-07 | Valid Line Distance | ☐ Pass ☐ Fail |
| TS-08 | Coordinates Storage | ☐ Pass ☐ Fail |
| TS-EDGE-01 | GPS Not Available | ☐ Pass ☐ Fail |
| TS-EDGE-02 | Poor GPS Accuracy | ☐ Pass ☐ Fail |
| TS-EDGE-03 | Back Navigation | ☐ Pass ☐ Fail |
| TS-EDGE-04 | Screen Rotation | ☐ Pass ☐ Fail |
| TS-EDGE-05 | Re-capture After Both | ☐ Pass ☐ Fail |

---

**Tester:** ___________________ **Date:** ___________________

**Notes:**
```




```

---

*Document ID: SAT-TS-001 | Version: 2.0 | Date: 2026-07-10*
*Updated: Replaced Google Maps tap-based line drawing with two-point GPS capture workflow*
