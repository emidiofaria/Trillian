# Share and Profile Tests

> **Purpose:** Validate the share card generation, Android share flow, profile screen, and sign out functionality.

---

## Test Environment

| Item | Required State | Check |
|------|----------------|-------|
| Session | Completed with laps and coaching | ☐ |
| User | Logged in with known profile | ☐ |
| Device | Has share targets (gallery, messaging) | ☐ |

---

## Test Suite: SHARE — Share Card Feature

### SHARE-01: Share Icon Visibility

**Objective:** Verify share icon is accessible in Session Result.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open Session Result screen | Screen displayed | ☐ |
| 2 | Look at toolbar | Share icon visible | ☐ |
| 3 | Icon position | Top-right area | ☐ |
| 4 | Icon recognizable | Share symbol | ☐ |

**Requirement Coverage:** SH-01

---

### SHARE-02: Share Card Generation

**Objective:** Verify share card bitmap is generated correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Session Result, tap share icon | Processing begins | ☐ |
| 2 | Brief loading (if visible) | Progress indicator | ☐ |
| 3 | Android share sheet opens | Share targets shown | ☐ |
| 4 | Select "Save to device" or gallery | Image saved | ☐ |
| 5 | Open saved image | Image viewer opens | ☐ |
| 6 | Image dimensions | 1080 × 1080 px | ☐ |

**Requirement Coverage:** SH-02

---

### SHARE-03: Share Card Content

**Objective:** Verify share card contains required elements.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | View saved share card image | Image displayed | ☐ |
| 2 | Driving Coach branding | Text visible at top | ☐ |
| 3 | Best lap time | Large, centered, brand blue | ☐ |
| 4 | "BEST LAP" label | Visible near time | ☐ |
| 5 | Track name | Displayed | ☐ |
| 6 | Session date | Displayed | ☐ |
| 7 | Consistency score | "CONSISTENCY XX%" | ☐ |
| 8 | brand blue bottom border | 4dp line visible | ☐ |
| 9 | Dark background | #0D0D0D or similar | ☐ |

**Requirement Coverage:** SH-03

---

### SHARE-04: Share Card Typography

**Objective:** Verify typography matches specification.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Branding text | ~28sp bold, white | ☐ |
| 2 | Best lap time | ~72sp bold, monospace, brand blue | ☐ |
| 3 | Track name | ~22sp, white | ☐ |
| 4 | Date | ~14sp, grey | ☐ |
| 5 | Consistency | ~14sp, white | ☐ |
| 6 | Overall legibility | Clear on dark background | ☐ |

---

### SHARE-05: Share to Different Targets

**Objective:** Verify share works with various apps.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Tap share icon | Share sheet opens | ☐ |
| 2 | Share to messaging app | Image attaches | ☐ |
| 3 | Share to email | Image attaches | ☐ |
| 4 | Share to social media (if installed) | Image posts | ☐ |
| 5 | Cancel share | Returns to app cleanly | ☐ |

**Requirement Coverage:** SH-04

---

### SHARE-06: FileProvider Configuration

**Objective:** Verify FileProvider is configured correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Tap share icon | Share starts | ☐ |
| 2 | No security exception | Clean execution | ☐ |
| 3 | Image accessible by target app | Can open/view | ☐ |
| 4 | Cache directory used | Not external storage | ☐ |

**Requirement Coverage:** SH-05

---

### SHARE-07: Telemetry Export Gesture

**Objective:** Verify the hidden developer export is reachable and does not disturb the tap path.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open a session recorded on this device | Session Result screen shown | ☐ |
| 2 | **Tap** the share icon | Image share card appears (not the ZIP) | ☐ |
| 3 | Dismiss the share sheet | Returns to Session Result | ☐ |
| 4 | **Press and hold** the share icon | "Preparing export…" appears, then a share sheet | ☐ |
| 5 | Observe during the long-press | **No "Share" tooltip appears** | ☐ |

**Requirement Coverage:** SH-07

---

### SHARE-08: Telemetry Bundle Contents

**Objective:** Verify the exported ZIP is complete and usable for debugging.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Long-press share, send the file to yourself | `.zip` received | ☐ |
| 2 | Open the archive | Contains exactly `telemetry.jsonl` and `session.json` | ☐ |
| 3 | Open `telemetry.jsonl` | Same GPS samples as the recorded session | ☐ |
| 4 | Open `session.json` | Track name, start line, lap times, app version/build, phone model, SDK level all present | ☐ |
| 5 | Check the file name | Starts with `telemetry_`, contains a sanitised track name and a date | ☐ |
| 6 | Share the same session again | MIME type is `application/zip`; export succeeds again | ☐ |

**Requirement Coverage:** SH-08, SH-09

---

### SHARE-09: Export Failure Is Visible

**Objective:** Verify a failed export is reported rather than silently ignored.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open a session whose telemetry file no longer exists (e.g. an older session) | Session Result screen shown | ☐ |
| 2 | Long-press the share icon | Snackbar: "Telemetry file not found" | ☐ |
| 3 | Observe | No share sheet opens, app does not crash | ☐ |
| 4 | Tap share (card path) on a normal session | Still works | ☐ |

**Requirement Coverage:** SH-10

---

### SHARE-10: Sharing Does Not Alter Recorded Data

**Objective:** Verify sharing is strictly read-only — the safety property of the feature.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Note the session's lap count, lap times and best lap | Recorded on paper | ☐ |
| 2 | Long-press share and complete an export | Share sheet opens | ☐ |
| 3 | Return to the session | Lap count, lap times and best lap **unchanged** | ☐ |
| 4 | Repeat the export 3 times | Succeeds every time; data still unchanged | ☐ |
| 5 | Reopen the app and check the session list | Session still present, unmodified | ☐ |
| 6 | Wait 24 h (or change device date), then reopen and share | Old cached exports pruned; recorded sessions untouched | ☐ |

**Requirement Coverage:** SH-11

---

## Test Suite: PROF — Profile Screen

### PROF-01: Profile Screen Access

**Objective:** Verify Profile screen is accessible from Home.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Home screen | Screen displayed | ☐ |
| 2 | Find profile icon | Top-right area | ☐ |
| 3 | Tap profile icon | Profile screen opens | ☐ |
| 4 | Toolbar title | "PROFILE" | ☐ |
| 5 | Back button | Returns to Home | ☐ |

---

### PROF-02: Avatar Display

**Objective:** Verify avatar shows initials correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Profile screen | Avatar visible | ☐ |
| 2 | Avatar shape | Circle | ☐ |
| 3 | Avatar background | brand blue (#1C69D4) | ☐ |
| 4 | Initials displayed | First 2 letters of name | ☐ |
| 5 | Name "John Doe" | Shows "JD" | ☐ |
| 6 | Name "Alice" | Shows "AL" | ☐ |
| 7 | Initials color | White | ☐ |

**Requirement Coverage:** UM-19

---

### PROF-03: User Information Display

**Objective:** Verify user info is displayed correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Display name | Shown in H2 style | ☐ |
| 2 | Display name value | Matches registered name | ☐ |
| 3 | Email address | Shown in Body style | ☐ |
| 4 | Email color | Muted (colorOnSurfaceVariant) | ☐ |
| 5 | Email value | Matches registered email | ☐ |

**Requirement Coverage:** UM-18

---

### PROF-04: Statistics Row

**Objective:** Verify aggregate statistics display.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Find stats row/cards | Below profile info | ☐ |
| 2 | Total sessions | Count displayed | ☐ |
| 3 | Sessions value | Matches session count | ☐ |
| 4 | Total laps | Count displayed | ☐ |
| 5 | Laps value | Sum of all session laps | ☐ |
| 6 | Best lap time | Time displayed | ☐ |
| 7 | Best lap value | Fastest across all sessions | ☐ |
| 8 | Metric card style | Consistent formatting | ☐ |

**Requirement Coverage:** UM-18

---

### PROF-05: Statistics Accuracy

**Objective:** Verify statistics match actual data.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Note total sessions in Profile | Value X | ☐ |
| 2 | Go to Home, count session cards | Same count X | ☐ |
| 3 | Note total laps in Profile | Value Y | ☐ |
| 4 | Sum laps from all sessions | Same count Y | ☐ |
| 5 | Note best lap in Profile | Time Z | ☐ |
| 6 | Find best lap in session history | Same time Z | ☐ |

---

## Test Suite: SIGN — Sign Out Flow

### SIGN-01: Sign Out Button Visibility

**Objective:** Verify sign out button is accessible.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Profile screen | Screen displayed | ☐ |
| 2 | Scroll to bottom if needed | Full content | ☐ |
| 3 | Sign out button visible | Button present | ☐ |
| 4 | Button style | Outlined, red text | ☐ |
| 5 | Button text | "SIGN OUT" | ☐ |

**Requirement Coverage:** UM-16

---

### SIGN-02: Sign Out Execution

**Objective:** Verify sign out completes correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Tap "SIGN OUT" button | Button responds | ☐ |
| 2 | Confirmation (if any) | Confirm or immediate | ☐ |
| 3 | Firebase signOut called | Auth state cleared | ☐ |
| 4 | Navigation occurs | Login screen appears | ☐ |
| 5 | Back stack cleared | Cannot go back | ☐ |
| 6 | Press back button | Exits app or stays on Login | ☐ |

**Requirement Coverage:** UM-16, UM-17

---

### SIGN-03: Local Data Cleared on Sign Out

**Objective:** Verify Room DB is cleared on sign out.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Before sign out | Note session count | ☐ |
| 2 | Sign out | Complete | ☐ |
| 3 | Sign in as same user | Login successful | ☐ |
| 4 | Check session count | May be 0 (cleared) or synced | ☐ |
| 5 | Sign in as different user | Login successful | ☐ |
| 6 | Check session count | Different user's data | ☐ |
| 7 | No cross-user data | Only own sessions visible | ☐ |

**Requirement Coverage:** UM-17

---

### SIGN-04: Sign Out and Re-login

**Objective:** Verify can sign out and back in cleanly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Sign out | Login screen shown | ☐ |
| 2 | Login with same credentials | Login form submitted | ☐ |
| 3 | Login succeeds | Home screen shown | ☐ |
| 4 | Profile shows correct user | Same user info | ☐ |
| 5 | Sessions may reload from server | Data synced | ☐ |

---

### SIGN-05: Sign Out During Recording

**Objective:** Verify sign out is blocked during recording.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start a recording | Recording active | ☐ |
| 2 | Background app (Home button) | App backgrounded | ☐ |
| 3 | Cannot access Profile easily | Recording screen locked | ☐ |
| 4 | Or sign out blocked | Error/warning shown | ☐ |
| 5 | Stop recording first | Clean state | ☐ |
| 6 | Then sign out works | Normal flow | ☐ |

---

## Share and Profile Tests Summary

| Test ID | Test Name | Status |
|---------|-----------|--------|
| SHARE-01 | Share Icon Visibility | ☐ Pass ☐ Fail |
| SHARE-02 | Share Card Generation | ☐ Pass ☐ Fail |
| SHARE-03 | Share Card Content | ☐ Pass ☐ Fail |
| SHARE-04 | Share Card Typography | ☐ Pass ☐ Fail |
| SHARE-05 | Share to Different Targets | ☐ Pass ☐ Fail |
| SHARE-06 | FileProvider Configuration | ☐ Pass ☐ Fail |
| PROF-01 | Profile Screen Access | ☐ Pass ☐ Fail |
| PROF-02 | Avatar Display | ☐ Pass ☐ Fail |
| PROF-03 | User Information Display | ☐ Pass ☐ Fail |
| PROF-04 | Statistics Row | ☐ Pass ☐ Fail |
| PROF-05 | Statistics Accuracy | ☐ Pass ☐ Fail |
| SIGN-01 | Sign Out Button Visibility | ☐ Pass ☐ Fail |
| SIGN-02 | Sign Out Execution | ☐ Pass ☐ Fail |
| SIGN-03 | Local Data Cleared on Sign Out | ☐ Pass ☐ Fail |
| SIGN-04 | Sign Out and Re-login | ☐ Pass ☐ Fail |
| SIGN-05 | Sign Out During Recording | ☐ Pass ☐ Fail |

---

**Tester:** ___________________ **Date:** ___________________

**Notes:**
```




```

---

*Document ID: SAT-SHARE-001 | Version: 1.0 | Date: 2026-05-06*
