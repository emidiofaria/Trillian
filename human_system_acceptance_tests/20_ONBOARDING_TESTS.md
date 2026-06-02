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
Settings → Apps → BMW Driving Coach → Storage → Clear Data
```

---

## Test Suite: ONB — Onboarding Flow

### ONB-01: First Launch Shows Onboarding

**Objective:** Verify onboarding appears on first launch only.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Clear app data (fresh install state) | Data cleared confirmation | ☐ |
| 2 | Launch BMW Driving Coach | Onboarding screen appears | ☐ |
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
