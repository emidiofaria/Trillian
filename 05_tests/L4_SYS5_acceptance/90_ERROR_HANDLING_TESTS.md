# Error Handling Tests

> **Purpose:** Validate error states, edge cases, network failures, and recovery mechanisms throughout the application.

---

## Test Environment

| Item | Required State | Check |
|------|----------------|-------|
| Device | Airplane mode accessible | ☐ |
| Backend | Can be stopped/started | ☐ |
| Network | WiFi and cellular available | ☐ |

---

## Test Suite: NET — Network Error Handling

### NET-01: No Network on Login

**Objective:** Verify login gracefully handles no network.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Enable airplane mode | Network off | ☐ |
| 2 | Open app (not logged in) | Login screen shown | ☐ |
| 3 | Enter valid credentials | Form filled | ☐ |
| 4 | Tap LOGIN | Request attempted | ☐ |
| 5 | Error shown | Network error snackbar | ☐ |
| 6 | App stable | No crash, still responsive | ☐ |
| 7 | Disable airplane mode | Network restored | ☐ |
| 8 | Retry login | Success | ☐ |

**Requirement Coverage:** NF-03

---

### NET-02: No Network on Registration

**Objective:** Verify registration handles no network.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Enable airplane mode | Network off | ☐ |
| 2 | Attempt registration | Form submitted | ☐ |
| 3 | Error shown | Network error message | ☐ |
| 4 | Form data preserved | Fields not cleared | ☐ |

---

### NET-03: Network Loss During Session Upload

**Objective:** Verify upload recovers from network loss.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Complete recording | Session created | ☐ |
| 2 | Immediately enable airplane mode | Network lost | ☐ |
| 3 | Upload status | PENDING or FAILED | ☐ |
| 4 | Home screen banner | May show upload pending | ☐ |
| 5 | Disable airplane mode | Network restored | ☐ |
| 6 | Wait for WorkManager retry | Auto retry | ☐ |
| 7 | Upload completes | Status = DONE | ☐ |

**Requirement Coverage:** TU-02, TU-03, TU-12

---

### NET-04: Backend Unavailable

**Objective:** Verify app handles backend being down.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Stop backend server | Server offline | ☐ |
| 2 | Try to login (cached user) | May work offline | ☐ |
| 3 | Try to upload session | Fails with retry | ☐ |
| 4 | App continues offline | Local data accessible | ☐ |
| 5 | Start backend server | Server back online | ☐ |
| 6 | Operations resume | Uploads complete | ☐ |

---

### NET-05: Slow Network Handling

**Objective:** Verify app handles slow connections gracefully.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Use poor cellular connection | Slow network | ☐ |
| 2 | Attempt login | May take longer | ☐ |
| 3 | Loading indicator shown | User feedback | ☐ |
| 4 | No timeout too quickly | Allows time for slow conn | ☐ |
| 5 | Eventually succeeds or fails | Clean result | ☐ |

**Requirement Coverage:** NF-09

---

## Test Suite: AUTH — Authentication Errors

### AUTH-01: Session Expired Handling

**Objective:** Verify 401 response triggers logout flow.

**Note:** May require backend manipulation to expire token.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Login successfully | Home screen | ☐ |
| 2 | Invalidate token server-side | Token expired | ☐ |
| 3 | Make API request | Request sent | ☐ |
| 4 | Backend returns 401 | Unauthorized | ☐ |
| 5 | Snackbar appears | "Session expired — please sign in again" | ☐ |
| 6 | Auto navigate to Login | Login screen shown | ☐ |
| 7 | Back stack cleared | Cannot go back | ☐ |

**Requirement Coverage:** UM-14

---

### AUTH-02: Token Refresh Transparency

**Objective:** Verify Firebase token refresh is automatic.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Login successfully | Token obtained | ☐ |
| 2 | Use app for extended period | >1 hour | ☐ |
| 3 | Make API requests | Requests sent | ☐ |
| 4 | Token refreshed automatically | No user action needed | ☐ |
| 5 | No session expired errors | Seamless experience | ☐ |

**Requirement Coverage:** UM-13

---

## Test Suite: GPS — GPS Error Handling

### GPS-01: GPS Not Available

**Objective:** Verify handling when GPS hardware unavailable.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Disable location services entirely | System settings | ☐ |
| 2 | Attempt to start recording | Recording starts | ☐ |
| 3 | GPS indicator | Shows "ACQUIRING GPS" (red) | ☐ |
| 4 | After 10+ seconds | Notification update | ☐ |
| 5 | App stable | No crash | ☐ |

**Requirement Coverage:** SR-11

---

### GPS-02: GPS Signal Lost During Recording

**Objective:** Verify notification updates on GPS loss.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording outdoors | GPS locked | ☐ |
| 2 | Move to GPS-blocked area | Indoors, garage | ☐ |
| 3 | Wait 10+ seconds | Signal lost detected | ☐ |
| 4 | Check notification | "GPS signal lost — move to open sky" | ☐ |
| 5 | Recording continues | Timer still running | ☐ |
| 6 | Return to GPS area | Open sky | ☐ |
| 7 | Notification restores | Normal recording text | ☐ |
| 8 | GPS indicator | Returns to GREEN | ☐ |

**Requirement Coverage:** TC-09, TC-10

---

### GPS-03: Poor GPS Accuracy

**Objective:** Verify warning when GPS accuracy degrades.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording in poor GPS area | Near tall buildings | ☐ |
| 2 | Observe GPS indicator | May show warning | ☐ |
| 3 | Accuracy > 10m | Visual indication | ☐ |
| 4 | Move to open area | Better accuracy | ☐ |
| 5 | Warning clears | Normal state | ☐ |

**Requirement Coverage:** TC-11

---

## Test Suite: DATA — Data Error Handling

### DATA-01: Empty Session History

**Objective:** Verify Home handles no sessions.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Login as new user | No sessions | ☐ |
| 2 | Home screen loads | No crash | ☐ |
| 3 | Session list | Empty or "No sessions" message | ☐ |
| 4 | Hero card | "Record your first session..." | ☐ |
| 5 | FAB still works | Can start recording | ☐ |

**Requirement Coverage:** DP-04

---

### DATA-02: Processing Failed Session

**Objective:** Verify handling of failed processing.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Create session with insufficient laps | 0 or 1 lap | ☐ |
| 2 | Wait for processing | Status updates | ☐ |
| 3 | Status becomes FAILED | Processing error | ☐ |
| 4 | Session Result screen | Error indication | ☐ |
| 5 | COACH tab | Retry button visible | ☐ |
| 6 | Error message clear | User understands issue | ☐ |
| 7 | Can still view partial data | If available | ☐ |

**Requirement Coverage:** LD-08, AI-14

---

### DATA-03: Telemetry File Missing

**Objective:** Verify handling when JSONL file is missing.

**Note:** Requires file system manipulation.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Complete recording | File created | ☐ |
| 2 | Delete JSONL file manually | File removed | ☐ |
| 3 | Upload worker runs | Attempts upload | ☐ |
| 4 | Worker returns failure | No retry | ☐ |
| 5 | Status = FAILED | Upload failed | ☐ |

**Requirement Coverage:** TU-09

---

### DATA-04: Stale Upload Banner

**Objective:** Verify pending upload banner appears.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Complete recording | Session created | ☐ |
| 2 | Block upload (airplane mode) | Upload pending | ☐ |
| 3 | Wait 5+ minutes | Time passes | ☐ |
| 4 | Go to Home screen | Banner visible | ☐ |
| 5 | Banner message | "Session upload pending — connect to Wi-Fi" | ☐ |
| 6 | Banner dismissible | Can close it | ☐ |
| 7 | Restore network | Upload completes | ☐ |
| 8 | Banner disappears | Clean state | ☐ |

**Requirement Coverage:** TU-12

---

## Test Suite: EDGE — Edge Cases

### EDGE-01: Screen Rotation

**Objective:** Verify rotation preserves state.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On any screen with data | State loaded | ☐ |
| 2 | Rotate to landscape | Orientation change | ☐ |
| 3 | Data preserved | Same content | ☐ |
| 4 | Rotate back | Portrait mode | ☐ |
| 5 | No crash or data loss | Stable | ☐ |

---

### EDGE-02: Process Death Recovery

**Objective:** Verify app recovers from process death.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open session result | Data displayed | ☐ |
| 2 | Background app | Home button | ☐ |
| 3 | Open many other apps | Trigger memory pressure | ☐ |
| 4 | Return to app | May restart from scratch | ☐ |
| 5 | Login still works | Session persisted | ☐ |
| 6 | Data accessible | Can navigate to content | ☐ |

---

### EDGE-03: Low Storage

**Objective:** Verify handling when device storage is low.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Fill device storage | <100 MB free | ☐ |
| 2 | Attempt recording | Recording starts | ☐ |
| 3 | If write fails | Error shown | ☐ |
| 4 | App doesn't crash | Graceful handling | ☐ |
| 5 | Free up storage | Space available | ☐ |
| 6 | Recording works | Normal operation | ☐ |

---

### EDGE-04: Battery Saver Mode

**Objective:** Verify behavior with battery saver enabled.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Enable battery saver | System setting | ☐ |
| 2 | Start recording | Recording active | ☐ |
| 3 | GPS may be affected | May be less accurate | ☐ |
| 4 | App still functions | Core features work | ☐ |
| 5 | Recommend disabling | If issues occur | ☐ |

---

## Error Handling Tests Summary

| Test ID | Test Name | Status |
|---------|-----------|--------|
| NET-01 | No Network on Login | ☐ Pass ☐ Fail |
| NET-02 | No Network on Registration | ☐ Pass ☐ Fail |
| NET-03 | Network Loss During Session Upload | ☐ Pass ☐ Fail |
| NET-04 | Backend Unavailable | ☐ Pass ☐ Fail |
| NET-05 | Slow Network Handling | ☐ Pass ☐ Fail |
| AUTH-01 | Session Expired Handling | ☐ Pass ☐ Fail |
| AUTH-02 | Token Refresh Transparency | ☐ Pass ☐ Fail |
| GPS-01 | GPS Not Available | ☐ Pass ☐ Fail |
| GPS-02 | GPS Signal Lost During Recording | ☐ Pass ☐ Fail |
| GPS-03 | Poor GPS Accuracy | ☐ Pass ☐ Fail |
| DATA-01 | Empty Session History | ☐ Pass ☐ Fail |
| DATA-02 | Processing Failed Session | ☐ Pass ☐ Fail |
| DATA-03 | Telemetry File Missing | ☐ Pass ☐ Fail |
| DATA-04 | Stale Upload Banner | ☐ Pass ☐ Fail |
| EDGE-01 | Screen Rotation | ☐ Pass ☐ Fail |
| EDGE-02 | Process Death Recovery | ☐ Pass ☐ Fail |
| EDGE-03 | Low Storage | ☐ Pass ☐ Fail |
| EDGE-04 | Battery Saver Mode | ☐ Pass ☐ Fail |

---

**Tester:** ___________________ **Date:** ___________________

**Notes:**
```




```

---

*Document ID: SAT-ERR-001 | Version: 1.0 | Date: 2026-05-06*
