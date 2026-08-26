# Onboarding Tests

> **Purpose:** Validate the first-launch experience including onboarding screens, permission requests, and user registration/login flows.

---

## Test Environment

| Item | Required State | Check |
|------|----------------|-------|
| App | Fresh install or data cleared | ☐ |
| Device | All app permissions revoked | ☐ |
| Network | Connected (WiFi or cellular) | ☐ |
| Backend | Running and accessible | ☐ |

**To reset app state:**
```
Settings → Apps → Driving Coach → Storage → Clear Data
```

---

## Test Suite: BRD — Branding & Visual Identity

### BRD-01: Helmet Emblem Renders Correctly at All Sizes

**Objective:** Verify the helmet emblem is undistorted and uncropped on every
surface it appears. Added after Incident 11, where a deformed emblem shipped
because automated tests only asserted the emblem was *present*, not that it was
*correct*. A human must look at it.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Cold start the app | System splash shows the helmet on a dark background | ☐ |
| 2 | Observe the helmet shape | Clearly reads as a racing helmet — not squashed, stretched or lopsided | ☐ |
| 3 | Observe the branded loading screen (132dp emblem) | Helmet sits centred inside the navy ring with visible clearance on all sides | ☐ |
| 4 | Reach the Home screen | Hero emblem (88dp) renders identically, just smaller | ☐ |
| 5 | Inspect the emblem edges against the dark background | No dark halo, fringe or jagged staircase around the outline | ☐ |
| 6 | Scroll Home down until the hero collapses | Small emblem (36dp) in the brand bar is still recognisable as a helmet | ☐ |
| 7 | Rotate the device / test on a second device of a different density | Emblem is crisp, not blurry or pixelated | ☐ |

**Requirement Coverage:** UI-08, UI-09

**Note:** Densities ship at 132/198/264/396/528 px (mdpi→xxxhdpi). Blurriness on
one specific device usually means that density bucket is missing — check with
`./gradlew :app:testDebugUnitTest --tests 'com.drivingcoach.brand.*'`.

---

### BRD-02: Engineering Manifesto Is Actually Readable

**Objective:** Verify the first-run introduction window does what it exists to do.
The manifesto is 14 words; at a normal reading pace it needs roughly 4 seconds,
and the returning-user budget of 1200 ms is about a third of that. Only a human
can judge whether it was *readable*, which is why this is an L4 test — the
automated tests can only prove the *budget* was applied.

**Watch for the two-screen trap.** Cold start shows the emblem alone on black first
(the platform splash, owned by the OS) and the branded loading screen second. The
budget belongs to the *second* screen. Twice the budget was anchored too early and
was partly spent behind the emblem, delivering ~2.3 s and then ~2.6 s of a nominal
4 s. If the manifesto feels short again, check *which* screen you were timing.

**Precondition:** App freshly installed (or storage cleared), so the launch
counter starts at zero.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Cold start the app for the first time | Two screens in succession: first the emblem alone on black (the platform splash), then the loading screen with the manifesto card | ☐ |
| 2 | Judge the **second** screen only | The loading screen — not the emblem-alone screen — is the one that holds; hint reads **"Tap to continue"** | ☐ |
| 3 | Read the manifesto card without hurrying | You finish reading it before the screen moves on | ☐ |
| 4 | Do **not** tap anything | The app proceeds on its own — a tap is never required | ☐ |
| 5 | Launch the app a 2nd and 3rd time | Same longer hold, same "Tap to continue" hint | ☐ |
| 6 | Launch the app a 4th time | Hold is noticeably shorter; hint now reads **"Tap to skip"** | ☐ |
| 7 | Launch a 5th time and tap the screen immediately | Loading screen disappears at once; app still lands on the correct screen | ☐ |
| 8 | Go to **Profile → About Trillian** | Same manifesto is shown, readable for as long as you like | ☐ |

**Requirement Coverage:** UI-02, UI-10, UI-11

**Note:** The hold is measured from the moment the loading screen becomes visible,
so the full budget is delivered on that screen regardless of how slow startup was.
If step 3 fails on real hardware the fix is to raise `introDisplayMs` in
`SplashTimings.kt` — the number now means what it says.

---

### BRD-03: About Screen Reports the Build

**Objective:** Verify the version a user would quote in a bug report is correct.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Sign in and open **Profile** | An **About Trillian** button is visible above Sign Out | ☐ |
| 2 | Tap **About Trillian** | About screen opens showing emblem, wordmark, tagline and manifesto | ☐ |
| 3 | Read the Version row | Shows name and code, e.g. `2.8 (208)` | ☐ |
| 4 | Compare with the installed APK filename | Filename `DrivingCoach-v2.8-debug.apk` agrees with the displayed version | ☐ |
| 5 | Compare with **Settings → Apps → Driving Coach** | Android reports the same version | ☐ |
| 6 | Press Back | Returns to Profile, not to the loading screen | ☐ |
| 7 | Set device font size to Largest and reopen About | Content scrolls; nothing is clipped or overlapping | ☐ |

**Requirement Coverage:** UI-11, UI-12

---

## Test Suite: ONB — Onboarding Flow

### ONB-01: First Launch Shows Onboarding

**Objective:** Verify onboarding appears on first launch only.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Clear app data (fresh install state) | Data cleared confirmation | ☐ |
| 2 | Launch Driving Coach | Onboarding screen appears | ☐ |
| 3 | Observe screen content | First page visible (Location) | ☐ |
| 4 | Observe navigation | Page indicator dots visible | ☐ |
| 5 | Observe button | "GRANT PERMISSIONS & START" visible | ☐ |

**Requirement Coverage:** ON-01, ON-02

---

### ONB-02: Onboarding Page Content

**Objective:** Verify all three onboarding pages contain correct explanatory content.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On onboarding, view page 1 | "Location" title/header | ☐ |
| 2 | Page 1 content | GPS tracking explanation text | ☐ |
| 3 | Page 1 icon | Location icon visible | ☐ |
| 4 | Swipe left to page 2 | Page transition animation | ☐ |
| 5 | Page 2 title | "Motion" or similar | ☐ |
| 6 | Page 2 content | Activity recognition explanation | ☐ |
| 7 | Swipe left to page 3 | Page transition animation | ☐ |
| 8 | Page 3 title | "Privacy" or similar | ☐ |
| 9 | Page 3 content | Data privacy statement | ☐ |
| 10 | Swipe right | Can navigate back | ☐ |

**Requirement Coverage:** ON-02

---

### ONB-03: Permission Grant — All Accepted

**Objective:** Verify app proceeds correctly when all permissions granted.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On onboarding, tap "GRANT PERMISSIONS" | Permission dialog appears | ☐ |
| 2 | Grant Location (Allow) | Dialog closes or next appears | ☐ |
| 3 | If "Allow all the time" option shown | Select "Allow all the time" | ☐ |
| 4 | Grant Activity Recognition (Allow) | Dialog closes | ☐ |
| 5 | All permissions granted | Login screen appears | ☐ |
| 6 | Force close and relaunch app | Login screen (not onboarding) | ☐ |

**Requirement Coverage:** ON-03, ON-04, ON-07

---

### ONB-04: Permission Grant — Location Denied

**Objective:** Verify app handles location permission denial correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Clear app data, relaunch | Onboarding appears | ☐ |
| 2 | Tap "GRANT PERMISSIONS" | Permission dialog | ☐ |
| 3 | Deny Location (Don't Allow) | Denial dialog/screen shown | ☐ |
| 4 | Dialog lists denied permissions | "Location" mentioned | ☐ |
| 5 | "OPEN SETTINGS" button visible | Button displayed | ☐ |
| 6 | Tap "OPEN SETTINGS" | System app settings opens | ☐ |
| 7 | In settings, enable Location | Permission granted | ☐ |
| 8 | Return to app | App state updated | ☐ |

**Requirement Coverage:** ON-05

---

### ONB-05: Permission Grant — Skip After Denial

**Objective:** Verify user can skip and proceed after permission denial.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Clear app data, relaunch | Onboarding appears | ☐ |
| 2 | Tap "GRANT PERMISSIONS" | Permission dialog | ☐ |
| 3 | Deny permissions | Denial dialog shown | ☐ |
| 4 | Tap "SKIP" (if available) | Proceeds to Login | ☐ |
| 5 | Relaunch app | Login screen (onboarding skipped) | ☐ |

**Requirement Coverage:** ON-06, ON-07

---

### ONB-06: Onboarding Persistence

**Objective:** Verify onboarding state persists in DataStore.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Complete onboarding flow | Login screen shown | ☐ |
| 2 | Force close app | App closed | ☐ |
| 3 | Relaunch app | Login screen (no onboarding) | ☐ |
| 4 | Restart device | Device restarted | ☐ |
| 5 | Launch app after restart | Login screen (no onboarding) | ☐ |

**Requirement Coverage:** ON-07, ON-08

---

## Test Suite: REG — Registration Flow

### REG-01: Registration Form Validation

**Objective:** Verify registration form validates input correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Login screen, tap "Register" link | Registration screen appears | ☐ |
| 2 | Leave all fields empty, tap Submit | Error: fields required | ☐ |
| 3 | Enter invalid email "test" | Email format error | ☐ |
| 4 | Enter valid email "test@example.com" | Email accepted | ☐ |
| 5 | Enter 7-char password "1234567" | Password too short error | ☐ |
| 6 | Enter 8-char password "12345678" | Password accepted | ☐ |
| 7 | Enter 1-char display name "A" | Name too short error | ☐ |
| 8 | Enter 2-char display name "AB" | Name accepted | ☐ |
| 9 | Enter 101-char display name | Name too long error | ☐ |
| 10 | Enter valid display name (2-100 chars) | Name accepted | ☐ |

**Requirement Coverage:** UM-01, UM-03, UM-04

---

### REG-02: Successful Registration

**Objective:** Verify new user can register successfully.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Registration screen | Form displayed | ☐ |
| 2 | Enter unique email "newuser@example.com" | Email entered | ☐ |
| 3 | Enter password "SecurePass123" | Password entered (masked) | ☐ |
| 4 | Toggle password visibility | Password shown/hidden | ☐ |
| 5 | Enter display name "Test Driver" | Name entered | ☐ |
| 6 | Tap "CREATE ACCOUNT" | Progress indicator shown | ☐ |
| 7 | Button disabled during request | Button not clickable | ☐ |
| 8 | Wait for completion | Home screen appears | ☐ |
| 9 | Back button behavior | Cannot go back to registration | ☐ |

**Requirement Coverage:** UM-01, UM-05, UM-10, UM-11

---

### REG-03: Duplicate Email Registration

**Objective:** Verify duplicate email is rejected.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Attempt registration with existing email | Form submitted | ☐ |
| 2 | Wait for response | Error snackbar appears | ☐ |
| 3 | Error message | "Email already in use" or similar | ☐ |
| 4 | App remains on Registration screen | Can retry with different email | ☐ |

**Requirement Coverage:** UM-06

---

## Test Suite: LOG — Login Flow

### LOG-01: Login Form Validation

**Objective:** Verify login form validates input correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Login screen | Form displayed | ☐ |
| 2 | Email field | inputType=textEmailAddress (@ key visible) | ☐ |
| 3 | Password field | inputType=textPassword (masked) | ☐ |
| 4 | Password visibility toggle | Toggle icon present | ☐ |
| 5 | Tap toggle | Password shown/hidden | ☐ |
| 6 | Leave fields empty, tap Login | Error indication | ☐ |

**Requirement Coverage:** UM-11

---

### LOG-02: Successful Login

**Objective:** Verify existing user can login successfully.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Enter valid email | Email entered | ☐ |
| 2 | Enter valid password | Password entered | ☐ |
| 3 | Tap "SIGN IN" | Progress indicator shown | ☐ |
| 4 | Button disabled during request | Button not clickable | ☐ |
| 5 | Wait for completion | Home screen appears | ☐ |
| 6 | Back button behavior | Cannot go back to login | ☐ |
| 7 | Force close and relaunch | Home screen (still logged in) | ☐ |

**Requirement Coverage:** UM-07, UM-08, UM-10

---

### LOG-03: Failed Login — Wrong Password

**Objective:** Verify wrong password is handled correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Enter valid email | Email entered | ☐ |
| 2 | Enter wrong password | Password entered | ☐ |
| 3 | Tap "SIGN IN" | Request sent | ☐ |
| 4 | Wait for response | Error snackbar appears | ☐ |
| 5 | Error message | "Invalid credentials" or similar | ☐ |
| 6 | Password field | NOT cleared (preserves input) | ☐ |
| 7 | App state | Remains on Login screen | ☐ |

**Requirement Coverage:** UM-09

---

### LOG-04: Failed Login — Non-existent User

**Objective:** Verify non-existent email is handled correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Enter non-existent email | Email entered | ☐ |
| 2 | Enter any password | Password entered | ☐ |
| 3 | Tap "SIGN IN" | Request sent | ☐ |
| 4 | Wait for response | Error snackbar appears | ☐ |
| 5 | Error message | Clear error (not revealing if email exists) | ☐ |

**Requirement Coverage:** UM-09

---

### LOG-05: Session Persistence

**Objective:** Verify login session persists correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Login successfully | Home screen shown | ☐ |
| 2 | Force close app | App closed | ☐ |
| 3 | Relaunch app | Home screen (still logged in) | ☐ |
| 4 | Restart device | Device restarted | ☐ |
| 5 | Launch app | Home screen (still logged in) | ☐ |

**Requirement Coverage:** UM-12, UM-13

---

## Onboarding Tests Summary

| Test ID | Test Name | Status |
|---------|-----------|--------|
| BRD-01 | Helmet Emblem Renders Correctly at All Sizes | ☐ Pass ☐ Fail |
| ONB-01 | First Launch Shows Onboarding | ☐ Pass ☐ Fail |
| ONB-02 | Onboarding Page Content | ☐ Pass ☐ Fail |
| ONB-03 | Permission Grant — All Accepted | ☐ Pass ☐ Fail |
| ONB-04 | Permission Grant — Location Denied | ☐ Pass ☐ Fail |
| ONB-05 | Permission Grant — Skip After Denial | ☐ Pass ☐ Fail |
| ONB-06 | Onboarding Persistence | ☐ Pass ☐ Fail |
| REG-01 | Registration Form Validation | ☐ Pass ☐ Fail |
| REG-02 | Successful Registration | ☐ Pass ☐ Fail |
| REG-03 | Duplicate Email Registration | ☐ Pass ☐ Fail |
| LOG-01 | Login Form Validation | ☐ Pass ☐ Fail |
| LOG-02 | Successful Login | ☐ Pass ☐ Fail |
| LOG-03 | Failed Login — Wrong Password | ☐ Pass ☐ Fail |
| LOG-04 | Failed Login — Non-existent User | ☐ Pass ☐ Fail |
| LOG-05 | Session Persistence | ☐ Pass ☐ Fail |

---

**Tester:** ___________________ **Date:** ___________________

**Notes:**
```




```

---

*Document ID: SAT-ONB-001 | Version: 1.0 | Date: 2026-05-06*
