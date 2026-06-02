# Pre-Track Day Tests

> **Purpose:** Validate the complete system before going to the track. These tests ensure end-to-end connectivity and basic functionality work in a controlled environment.

---

## Test Environment

| Item | Required State | Check |
|------|----------------|-------|
| Backend | Running (local or Azure) | ☐ |
| Database | Clean or seeded with test data | ☐ |
| Android device | Connected to same network as backend | ☐ |
| APK installed | Latest build | ☐ |
| Firebase Auth | Configured and working | ☐ |

---

## Test Suite: PRE — Pre-Track Validation

### PRE-01: Backend Health Check

**Objective:** Verify backend is running and accessible.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | From device, open browser | Browser opens | ☐ |
| 2 | Navigate to `{API_BASE_URL}/health` | Page loads | ☐ |
| 3 | Observe response | `{"status":"healthy"}` displayed | ☐ |

**Requirement Coverage:** BE-01, NF-03

---

### PRE-02: Firebase Authentication Test

**Objective:** Verify Firebase Auth integration works end-to-end.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open BMW Driving Coach app | App launches | ☐ |
| 2 | Navigate to Login screen | Login form displayed | ☐ |
| 3 | Enter invalid email format | Email field shows error | ☐ |
| 4 | Enter valid email, short password (<8 chars) | Password error shown | ☐ |
| 5 | Tap "REGISTER" link | Register screen appears | ☐ |
| 6 | Fill registration: email, password, display name | All fields accepted | ☐ |
| 7 | Tap "CREATE ACCOUNT" | Progress indicator shown | ☐ |
| 8 | Wait for registration | Home screen appears | ☐ |
| 9 | Open Profile, tap "SIGN OUT" | Returns to Login screen | ☐ |
| 10 | Login with registered credentials | Home screen appears | ☐ |

**Requirement Coverage:** UM-01 to UM-11, UM-16, UM-17

---

### PRE-03: Permission Flow Test

**Objective:** Verify permission request and handling works correctly.

**Precondition:** Fresh install or cleared app data.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Launch app (fresh install) | Onboarding screen appears | ☐ |
| 2 | View page 1 (Location) | Location explanation text | ☐ |
| 3 | Swipe to page 2 (Motion) | Motion explanation text | ☐ |
| 4 | Swipe to page 3 (Privacy) | Privacy explanation text | ☐ |
| 5 | Tap "GRANT PERMISSIONS & START" | System permission dialogs appear | ☐ |
| 6 | Grant Location permission | Dialog accepts | ☐ |
| 7 | Grant Activity Recognition permission | Dialog accepts | ☐ |
| 8 | All permissions granted | Login screen appears | ☐ |

**Test denial flow:**

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 9 | (Fresh install) Deny Location permission | Dialog with denied permissions shown | ☐ |
| 10 | Tap "OPEN SETTINGS" | App settings opened | ☐ |
| 11 | Grant permission in settings | Returns to app | ☐ |

**Requirement Coverage:** ON-01 to ON-08

---

### PRE-04: Home Screen Load Test

**Objective:** Verify Home screen loads correctly with or without existing sessions.

**Test A: No sessions (fresh user)**

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Login as new user | Home screen appears | ☐ |
| 2 | Observe hero card | "Record your first session..." message | ☐ |
| 3 | Observe session list | Empty or "No sessions yet" | ☐ |
| 4 | Observe FAB | Blue FAB with record icon visible | ☐ |
| 5 | Tap profile icon (top right) | Profile screen opens | ☐ |

**Test B: With sessions (seeded user)**

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 6 | Login as user with test sessions | Home screen appears | ☐ |
| 7 | Observe hero card | Best lap time displayed | ☐ |
| 8 | Observe session list | Session cards visible | ☐ |
| 9 | Scroll session list | Scrolls smoothly | ☐ |
| 10 | Session cards show | Track name, date, best lap, lap count | ☐ |

**Requirement Coverage:** DP-01 to DP-06, NF-08

---

### PRE-05: Start Session Flow Test (Indoor/Simulated)

**Objective:** Verify session start flow works without actual GPS movement.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Home screen, tap FAB | Track setup dialog/screen appears | ☐ |
| 2 | Enter track name "Test Track" | Name accepted | ☐ |
| 3 | Tap START | Recording screen appears | ☐ |
| 4 | Observe elapsed time | Timer starts (00:00.000 → 00:01.xxx) | ☐ |
| 5 | Observe GPS status | Shows "ACQUIRING GPS..." (red) | ☐ |
| 6 | Move near window/outdoors | GPS status changes to "GPS LOCKED" (green) | ☐ |
| 7 | Observe notification bar | "Recording — MM:SS" notification | ☐ |
| 8 | Tap STOP RECORDING | Recording stops | ☐ |
| 9 | Observe transition | Session Result screen appears | ☐ |

**Requirement Coverage:** SR-01 to SR-11, TC-01 to TC-05

---

### PRE-06: Session Upload Test

**Objective:** Verify telemetry upload works.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | After stopping recording | Session Result screen shown | ☐ |
| 2 | Observe processing card | "Processing your session..." visible | ☐ |
| 3 | Observe upload step | "Uploading ✓" after completion | ☐ |
| 4 | Navigate to Home | Session appears in list | ☐ |
| 5 | Session card shows | Upload status chip | ☐ |

**Test offline upload:**

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 6 | Enable airplane mode | Network disconnected | ☐ |
| 7 | Start and stop a session | Session saved locally | ☐ |
| 8 | Observe status | "PENDING" upload status | ☐ |
| 9 | Disable airplane mode | Network restored | ☐ |
| 10 | Wait for WorkManager | Upload completes automatically | ☐ |

**Requirement Coverage:** TU-01 to TU-09, TU-12, TU-13

---

### PRE-07: Session Result Tabs Test

**Objective:** Verify all three result tabs render correctly.

**Precondition:** Completed session with detected laps (use seeded data or simulated GPS track).

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open Session Result for completed session | Result screen appears | ☐ |
| 2 | Default tab is LAPS | Lap list visible | ☐ |
| 3 | Scroll lap list | All laps shown | ☐ |
| 4 | Best lap has gold border | Gold accent visible | ☐ |
| 5 | Delta badges colored | Green/red appropriately | ☐ |
| 6 | Tap COACH tab | Coach tab content shown | ☐ |
| 7 | Consistency score displayed | Percentage shown | ☐ |
| 8 | Coaching insight cards | Headline + detail visible | ☐ |
| 9 | Tap CHART tab | Chart tab content shown | ☐ |
| 10 | Speed chart rendered | Lines visible, no crash | ☐ |
| 11 | Best lap highlighted | Blue line distinguishable | ☐ |

**Requirement Coverage:** LC-01 to LC-10, AI-11, AI-13, DP-05

---

### PRE-08: Share Functionality Test

**Objective:** Verify share card generation and sharing works.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open Session Result for completed session | Result screen appears | ☐ |
| 2 | Tap share icon (toolbar) | Processing indicator (brief) | ☐ |
| 3 | Share sheet opens | Android share sheet appears | ☐ |
| 4 | Select "Save to device" or similar | Image saves | ☐ |
| 5 | Open saved image | 1080×1080 image displayed | ☐ |
| 6 | Image contains | BMW branding, lap time, track name, date | ☐ |

**Requirement Coverage:** SH-01 to SH-05

---

### PRE-09: Profile and Sign Out Test

**Objective:** Verify profile displays correctly and sign out clears data.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | From Home, tap profile icon | Profile screen opens | ☐ |
| 2 | Avatar shows | Initials on blue background | ☐ |
| 3 | Display name shown | Correct name from registration | ☐ |
| 4 | Email shown | Correct email address | ☐ |
| 5 | Stats row shows | Total sessions, laps, best time | ☐ |
| 6 | Tap "SIGN OUT" | Confirmation or immediate logout | ☐ |
| 7 | After sign out | Login screen appears | ☐ |
| 8 | Sign in as different user | Different profile data | ☐ |

**Requirement Coverage:** UM-16 to UM-19

---

### PRE-10: Error Handling Test

**Objective:** Verify error states are handled gracefully.

**Test A: Network error on login**

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Enable airplane mode | Network off | ☐ |
| 2 | Attempt login | Error snackbar shown | ☐ |
| 3 | App does not crash | Still responsive | ☐ |

**Test B: Backend unavailable**

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 4 | Stop backend server | Backend offline | ☐ |
| 5 | Attempt login (logged in user) | Network error shown | ☐ |
| 6 | App continues working offline | Local data accessible | ☐ |

**Test C: Invalid credentials**

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 7 | Enter wrong password | "Invalid credentials" error | ☐ |
| 8 | Password field not cleared | Previous input preserved | ☐ |

**Requirement Coverage:** UM-06, UM-09, UM-14, NF-03, NF-09

---

## Pre-Track Checklist Summary

| Test ID | Test Name | Status |
|---------|-----------|--------|
| PRE-01 | Backend Health Check | ☐ Pass ☐ Fail |
| PRE-02 | Firebase Authentication | ☐ Pass ☐ Fail |
| PRE-03 | Permission Flow | ☐ Pass ☐ Fail |
| PRE-04 | Home Screen Load | ☐ Pass ☐ Fail |
| PRE-05 | Start Session Flow | ☐ Pass ☐ Fail |
| PRE-06 | Session Upload | ☐ Pass ☐ Fail |
| PRE-07 | Session Result Tabs | ☐ Pass ☐ Fail |
| PRE-08 | Share Functionality | ☐ Pass ☐ Fail |
| PRE-09 | Profile and Sign Out | ☐ Pass ☐ Fail |
| PRE-10 | Error Handling | ☐ Pass ☐ Fail |

---

## Go/No-Go Decision

| Criteria | Met? |
|----------|------|
| All PRE-01 to PRE-10 tests pass | ☐ |
| No critical bugs found | ☐ |
| Backend accessible from track location (cellular) | ☐ |
| Device battery >80% | ☐ |
| Backup device available (optional) | ☐ |

**Decision:** ☐ GO to track / ☐ NO-GO (fix issues first)

---

**Tester:** ___________________ **Date:** ___________________

**Notes:**
```




```

---

*Document ID: SAT-PRE-001 | Version: 1.0 | Date: 2026-05-06*
