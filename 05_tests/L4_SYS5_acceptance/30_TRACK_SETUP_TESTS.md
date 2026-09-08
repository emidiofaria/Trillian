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

### TS-00: GPS Warm-Up on Home

**Objective:** Verify the GPS readiness chip absorbs the cold time-to-first-fix before the user
reaches Track Setup, and that acquisition timings are recorded.

**Setup:** Force-stop the app and turn Location OFF then ON, so the next fix is genuinely cold.
Stand outdoors with clear sky view. Have a stopwatch ready.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Launch the app, start the stopwatch | Home appears | ☐ |
| 2 | Observe the chip below the tagline | Amber "Acquiring GPS…" | ☐ |
| 3 | Wait, watching the chip | Turns green "GPS ready"; note the stopwatch time | ☐ |
| 4 | Tap FAB, enter a name, open Track Setup | GPS status populates in **< 5 s** (not 30–60 s) | ☐ |
| 5 | Back out to Home, go to Profile → About | Last GPS acquisition timings shown | ☐ |
| 6 | Compare | About's time-to-accurate-fix ≈ your step 3 stopwatch reading | ☐ |

**Requirement Coverage:** TS-16, TS-17, TS-19

---

### TS-00b: Warm-Up Stops When It Should

**Objective:** Verify the receiver is not held open indefinitely.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Home, wait for green chip | "GPS ready" | ☐ |
| 2 | Press Home (background the app), wait 30 s | — | ☐ |
| 3 | Reopen the app | Chip restarts at amber, or goes green quickly | ☐ |
| 4 | Navigate Home → Track Setup → back to Home | Chip is still green; searching did **not** restart | ☐ |
| 5 | Leave the app open for 30+ minutes | Chip disappears (idle ceiling backstop) | ☐ |
| 6 | Navigate away and back | Chip reappears and searching restarts | ☐ |
| 7 | Deny/revoke location permission, relaunch | Chip stays hidden; no crash | ☐ |

> Step 4 is the Incident 12 guard. Leaving Home must **not** end the warm-up; only leaving the
> app, starting a recording, or the 30-minute backstop may.

**Requirement Coverage:** TS-18, NF-14

---

### TS-00c: Track Setup Survives an Interruption

**Objective:** Regression guard for the defect where location updates never resumed after the
screen switched off — the field symptom was "Acquiring GPS…" forever.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open Track Setup, wait for GPS status | Satellites/accuracy shown | ☐ |
| 2 | Press the power button (screen off), wait 20 s | — | ☐ |
| 3 | Unlock, return to Track Setup | GPS status resumes updating within seconds | ☐ |
| 4 | Switch to another app, wait 20 s, return | GPS status resumes updating | ☐ |
| 5 | Capture Point A | Capture succeeds with a fresh, live position | ☐ |

**Requirement Coverage:** TS-15

---

### TS-00d: The Warm-Up Survives the Walk to the Line (Incident 12)

**Objective:** The journey the feature exists to serve. This is the acceptance test whose
absence let the regression reach the track.

**Do this outdoors, from a genuinely cold start** — force-stop the app first, or the receiver
may still be warm from a previous run and the test proves nothing.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Force-stop the app, then open it in the paddock | Chip amber "Acquiring GPS…" | ☐ |
| 2 | Wait for green, noting roughly how long it took | "GPS ready" | ☐ |
| 3 | Tap **+** and walk to the start/finish line | — | ☐ |
| 4 | On arrival, look at the GPS status | Satellites/accuracy shown immediately — **not** "Acquiring GPS…" | ☐ |
| 5 | Check CAPTURE POINT A | Enabled within about a second of arriving | ☐ |
| 6 | Rotate the phone, then re-check | Still ready; no re-acquisition | ☐ |
| 7 | **Profile → About** after the session | One acquisition recorded, not two | ☐ |

**Fail condition:** any second wait for a fix after the chip already went green. Record the
observed wait in Notes — that number is the user-visible cost of this defect.

**Requirement Coverage:** TS-16, TS-17, TS-18, NF-15

---

### TS-00e: Stale Positions Cannot Become a Start Line

**Objective:** Verify the capture gate refuses a position that no longer says where the user
is, and that it waits rather than blocks (Incident 12, finding F4).

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Track Setup with good GPS, note CAPTURE is enabled | Enabled | ☐ |
| 2 | Lock the phone, walk 50+ m, unlock and return to the screen | Briefly shows "Getting a current GPS fix…" and CAPTURE is disabled | ☐ |
| 3 | Hold still with sky view | Status returns to normal and CAPTURE re-enables **on its own** within a few seconds | ☐ |
| 4 | Capture Point A, then compare against a phone map app | Point matches where you are standing, not where you came from | ☐ |
| 5 | Repeat step 2 but tap CAPTURE while it is disabled | Nothing is captured; no crash; no need to leave the screen | ☐ |

**Fail condition:** capture succeeding while the status reads "Getting a current GPS fix…", or
the state never clearing without leaving the screen.

**Requirement Coverage:** TS-20, TS-21, TS-22, TS-23

---

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
| TS-00 | GPS Warm-Up on Home | ☐ Pass ☐ Fail |
| TS-00b | Warm-Up Stops When It Should | ☐ Pass ☐ Fail |
| TS-00c | Track Setup Survives an Interruption | ☐ Pass ☐ Fail |
| TS-01 | Access Track Setup Screen | ☐ Pass ☐ Fail |
| TS-02 | GPS Status Display | ☐ Pass ☐ Fail |
| TS-03 | Capture Point A | ☐ Pass ☐ Fail |
| TS-04 | Capture Point B and Line Width | ☐ Pass ☐ Fail |
| TS-05 | Line Distance — Too Close | ☐ Pass ☐ Fail |
| TS-00d | Warm-Up Survives the Walk (Incident 12) | ☐ Pass ☐ Fail |
| TS-00e | Stale Positions Rejected at Capture | ☐ Pass ☐ Fail |
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

*Document ID: SAT-TS-001 | Version: 2.2 | Date: 2026-09-07*
*Updated: Added TS-00d (warm-up survives the Home → Track Setup walk) and TS-00e (stale fixes
rejected at capture) after Incident 12; corrected TS-00b, which asserted the defective
screen-scoped stop.*
