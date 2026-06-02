# Track Setup Tests

> **Purpose:** Validate the start/finish line definition flow using map interaction. These tests verify the user can correctly define track boundaries before recording.

---

## Test Environment

| Item | Required State | Check |
|------|----------------|-------|
| Device | GPS enabled, location services ON | ☐ |
| Location | Outdoors or near window for GPS | ☐ |
| Network | Connected (for map tiles) | ☐ |
| User | Logged in | ☐ |

---

## Test Suite: TS — Track Setup Flow

### TS-01: Access Track Setup Screen

**Objective:** Verify Track Setup screen is accessible from Home.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Home screen | FAB visible (bottom right) | ☐ |
| 2 | Tap FAB (record button) | Track Setup screen opens | ☐ |
| 3 | Observe map | Map displayed with dark style | ☐ |
| 4 | Observe toolbar | Back button and CLEAR option | ☐ |
| 5 | Observe instruction card | Instruction text visible | ☐ |
| 6 | Instruction text reads | "Tap two points on the map..." | ☐ |
| 7 | START RECORDING button | Disabled initially | ☐ |

**Requirement Coverage:** TS-01, TS-02, TS-11, TS-13

---

### TS-02: Place Point A

**Objective:** Verify first map tap places Point A correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Track Setup screen | Map displayed | ☐ |
| 2 | Tap anywhere on map | Marker placed at tap location | ☐ |
| 3 | Marker color | BMW blue marker | ☐ |
| 4 | Marker label | "A" or distinguishing indicator | ☐ |
| 5 | Instruction updates | Indicates "tap second point" | ☐ |
| 6 | START RECORDING button | Still disabled | ☐ |

**Requirement Coverage:** TS-03

---

### TS-03: Place Point B and Draw Line

**Objective:** Verify second map tap places Point B and draws the line.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Point A already placed | One marker on map | ☐ |
| 2 | Tap second location on map | Second marker placed | ☐ |
| 3 | Marker label | "B" or distinguishing indicator | ☐ |
| 4 | Polyline drawn | BMW blue line between A and B | ☐ |
| 5 | Instruction updates | Indicates line is set | ☐ |
| 6 | START RECORDING button | Enabled (if distance valid) | ☐ |

**Requirement Coverage:** TS-04

---

### TS-04: Third Tap Does Nothing

**Objective:** Verify tapping after both points placed shows snackbar.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Both points A and B placed | Line visible | ☐ |
| 2 | Tap another location on map | No new marker added | ☐ |
| 3 | Snackbar appears | "Tap CLEAR to redraw" message | ☐ |
| 4 | Existing markers remain | A and B unchanged | ☐ |

**Requirement Coverage:** TS-05

---

### TS-05: Use My Location Button

**Objective:** Verify "USE MY LOCATION" sets Point A to current GPS position.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Fresh Track Setup (no points) | Empty map | ☐ |
| 2 | Tap "USE MY LOCATION" button | GPS location fetched | ☐ |
| 3 | Point A marker placed | At device's current location | ☐ |
| 4 | Map centers on location | Marker visible on screen | ☐ |
| 5 | Can still tap for Point B | Second tap works | ☐ |

**Requirement Coverage:** TS-06

---

### TS-06: Clear Button

**Objective:** Verify CLEAR button resets the track setup.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Place both Point A and Point B | Line visible | ☐ |
| 2 | Tap CLEAR in toolbar | Confirmation or immediate clear | ☐ |
| 3 | Both markers removed | Map empty | ☐ |
| 4 | Polyline removed | No line visible | ☐ |
| 5 | START RECORDING button | Disabled | ☐ |
| 6 | Can start over | Tap to place Point A | ☐ |

**Requirement Coverage:** TS-07

---

### TS-07: Line Distance Validation — Too Close

**Objective:** Verify points less than 2m apart are rejected.

**Setup:** Stand still and place both points at nearly identical location.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Tap "USE MY LOCATION" for Point A | Marker placed | ☐ |
| 2 | Zoom in maximum on map | Close view | ☐ |
| 3 | Tap very close to Point A for Point B | Second marker ~1m away | ☐ |
| 4 | Tap START RECORDING | Validation runs | ☐ |
| 5 | Error message appears | "Points are too close" | ☐ |
| 6 | Button disabled or returns to setup | Cannot proceed | ☐ |

**Requirement Coverage:** TS-08, TS-09

---

### TS-08: Line Distance Validation — Too Far

**Objective:** Verify points more than 200m apart are rejected.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Zoom out on map | Wide view | ☐ |
| 2 | Tap for Point A | Marker placed | ☐ |
| 3 | Tap for Point B ~300m away | Second marker placed | ☐ |
| 4 | Line drawn | Visible between distant points | ☐ |
| 5 | Tap START RECORDING | Validation runs | ☐ |
| 6 | Error message appears | "Line is too long — place points closer..." | ☐ |
| 7 | Button disabled or returns to setup | Cannot proceed | ☐ |

**Requirement Coverage:** TS-08, TS-10

---

### TS-09: Valid Line Distance (2m–200m)

**Objective:** Verify valid line distance allows recording to start.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Place Point A | Marker placed | ☐ |
| 2 | Place Point B ~10-50m away | Second marker placed | ☐ |
| 3 | Line drawn | Visible between points | ☐ |
| 4 | START RECORDING button | Enabled | ☐ |
| 5 | Tap START RECORDING | Validation passes | ☐ |
| 6 | Screen transitions | Recording screen appears | ☐ |

**Requirement Coverage:** TS-08, TS-11, TS-12

---

### TS-10: Start/Finish Coordinates Storage

**Objective:** Verify coordinates are stored in SessionEntity.

**Note:** This test requires database inspection or debug logging.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Define valid start/finish line | Line set | ☐ |
| 2 | Tap START RECORDING | Recording starts | ☐ |
| 3 | Immediately STOP | Session created | ☐ |
| 4 | Inspect session data | startLineLat1/Lng1 present | ☐ |
| 5 | Verify coordinates | startLineLat2/Lng2 present | ☐ |
| 6 | Values match map | Approximate to tapped locations | ☐ |

**Requirement Coverage:** TS-12

---

### TS-11: Track Name Entry

**Objective:** Verify user can enter track name before recording.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Define valid start/finish line | Line set | ☐ |
| 2 | Track name input visible | Text field or dialog | ☐ |
| 3 | Enter "Circuito de Braga" | Name accepted | ☐ |
| 4 | Tap START RECORDING | Recording starts | ☐ |
| 5 | After session, check Home | Track name displayed | ☐ |

**Requirement Coverage:** Related to session creation flow

---

### TS-12: Map Dark Style

**Objective:** Verify map uses BMW dark (Aubergine) style.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open Track Setup screen | Map loads | ☐ |
| 2 | Observe map background | Dark color scheme | ☐ |
| 3 | Roads visible | Visible on dark background | ☐ |
| 4 | Labels readable | White/light text on dark | ☐ |
| 5 | Overall aesthetic | Matches BMW luxury theme | ☐ |

**Requirement Coverage:** TS-02

---

## Test Suite: TS-EDGE — Edge Cases

### TS-EDGE-01: GPS Not Available

**Objective:** Verify behavior when GPS is disabled.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Disable device location services | Location OFF | ☐ |
| 2 | Open Track Setup screen | Map loads (cached tiles) | ☐ |
| 3 | Tap "USE MY LOCATION" | Error or prompt to enable GPS | ☐ |
| 4 | Manual tap still works | Can place markers on map | ☐ |
| 5 | Tap START RECORDING | Permission/GPS error shown | ☐ |

---

### TS-EDGE-02: No Network (Offline Map)

**Objective:** Verify behavior with no network connectivity.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Enable airplane mode | Network OFF | ☐ |
| 2 | Open Track Setup screen | Map may not load tiles | ☐ |
| 3 | If cached tiles exist | Some map visible | ☐ |
| 4 | Can still tap to place points | Markers placed | ☐ |
| 5 | GPS still works | Location available | ☐ |

---

### TS-EDGE-03: Back Navigation

**Objective:** Verify back button behavior on Track Setup.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open Track Setup | Screen displayed | ☐ |
| 2 | Place Point A only | One marker | ☐ |
| 3 | Press back button | Returns to Home | ☐ |
| 4 | Open Track Setup again | Fresh state (no markers) | ☐ |

---

### TS-EDGE-04: Screen Rotation

**Objective:** Verify state preserved on rotation.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Place Point A and Point B | Line visible | ☐ |
| 2 | Rotate device | Landscape mode | ☐ |
| 3 | Markers and line preserved | Still visible | ☐ |
| 4 | Rotate back | Portrait mode | ☐ |
| 5 | State unchanged | Same markers and line | ☐ |

---

## Track Setup Tests Summary

| Test ID | Test Name | Status |
|---------|-----------|--------|
| TS-01 | Access Track Setup Screen | ☐ Pass ☐ Fail |
| TS-02 | Place Point A | ☐ Pass ☐ Fail |
| TS-03 | Place Point B and Draw Line | ☐ Pass ☐ Fail |
| TS-04 | Third Tap Does Nothing | ☐ Pass ☐ Fail |
| TS-05 | Use My Location Button | ☐ Pass ☐ Fail |
| TS-06 | Clear Button | ☐ Pass ☐ Fail |
| TS-07 | Line Distance — Too Close | ☐ Pass ☐ Fail |
| TS-08 | Line Distance — Too Far | ☐ Pass ☐ Fail |
| TS-09 | Valid Line Distance | ☐ Pass ☐ Fail |
| TS-10 | Coordinates Storage | ☐ Pass ☐ Fail |
| TS-11 | Track Name Entry | ☐ Pass ☐ Fail |
| TS-12 | Map Dark Style | ☐ Pass ☐ Fail |
| TS-EDGE-01 | GPS Not Available | ☐ Pass ☐ Fail |
| TS-EDGE-02 | No Network (Offline Map) | ☐ Pass ☐ Fail |
| TS-EDGE-03 | Back Navigation | ☐ Pass ☐ Fail |
| TS-EDGE-04 | Screen Rotation | ☐ Pass ☐ Fail |

---

**Tester:** ___________________ **Date:** ___________________

**Notes:**
```




```

---

*Document ID: SAT-TS-001 | Version: 1.0 | Date: 2026-05-06*
