# Human System Acceptance Tests

> **BMW Driving Coach — System Acceptance Test Documentation**

This directory contains comprehensive test documentation for validating the BMW Driving Coach application through human testing at a real track day.

---

## Document Overview

### Setup Documentation

| Document | Purpose |
|----------|---------|
| [01_ENVIRONMENT_SETUP.md](01_ENVIRONMENT_SETUP.md) | Prerequisites, tool installation, cloud service setup |
| [02_BACKEND_DEPLOYMENT.md](02_BACKEND_DEPLOYMENT.md) | Backend local and Azure deployment guide |
| [03_ANDROID_BUILD.md](03_ANDROID_BUILD.md) | APK build, signing, and device installation |

### Test Checklists

| Document | Tests | Scope |
|----------|-------|-------|
| [10_PRE_TRACK_DAY_TESTS.md](10_PRE_TRACK_DAY_TESTS.md) | 10 | Full system validation before track day |
| [20_ONBOARDING_TESTS.md](20_ONBOARDING_TESTS.md) | 14 | First launch, permissions, registration, login |
| [30_TRACK_SETUP_TESTS.md](30_TRACK_SETUP_TESTS.md) | 16 | Start/finish line definition on map |
| [40_SESSION_RECORDING_TESTS.md](40_SESSION_RECORDING_TESTS.md) | 16 | GPS capture, foreground service, telemetry |
| [50_LAP_DETECTION_TESTS.md](50_LAP_DETECTION_TESTS.md) | 14 | Server-side lap detection validation |
| [60_SESSION_RESULTS_TESTS.md](60_SESSION_RESULTS_TESTS.md) | 17 | Laps, Coach, Chart tabs |
| [70_AI_COACHING_TESTS.md](70_AI_COACHING_TESTS.md) | 14 | AI coaching feedback quality |
| [80_SHARE_AND_PROFILE_TESTS.md](80_SHARE_AND_PROFILE_TESTS.md) | 16 | Share card, profile, sign out |
| [90_ERROR_HANDLING_TESTS.md](90_ERROR_HANDLING_TESTS.md) | 18 | Network errors, GPS loss, edge cases |
| [99_ACCEPTANCE_SIGNOFF.md](99_ACCEPTANCE_SIGNOFF.md) | — | Final release approval checklist |

**Total Test Cases: 135**

---

## Quick Start Guide

### 1. Environment Setup (1-2 hours)
1. Complete [01_ENVIRONMENT_SETUP.md](01_ENVIRONMENT_SETUP.md)
2. Verify all tools are installed

### 2. Deploy Backend (30 minutes)
1. Follow [02_BACKEND_DEPLOYMENT.md](02_BACKEND_DEPLOYMENT.md)
2. Run health check to verify

### 3. Build Android App (20 minutes)
1. Follow [03_ANDROID_BUILD.md](03_ANDROID_BUILD.md)
2. Install APK on test device

### 4. Pre-Track Validation (1 hour)
1. Execute all tests in [10_PRE_TRACK_DAY_TESTS.md](10_PRE_TRACK_DAY_TESTS.md)
2. Verify GO/NO-GO decision

### 5. Track Day Testing (Track session)
1. Execute tests 20-90 at the track
2. Record all results

### 6. Acceptance Signoff
1. Complete [99_ACCEPTANCE_SIGNOFF.md](99_ACCEPTANCE_SIGNOFF.md)
2. Obtain required signatures

---

## Test Execution Order

```
┌─────────────────────────────────────────────────────────────┐
│                    BEFORE TRACK DAY                         │
├─────────────────────────────────────────────────────────────┤
│ 01_ENVIRONMENT_SETUP → 02_BACKEND → 03_ANDROID              │
│                           ↓                                 │
│                   10_PRE_TRACK_DAY                          │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                      AT THE TRACK                           │
├─────────────────────────────────────────────────────────────┤
│ 20_ONBOARDING → 30_TRACK_SETUP → 40_SESSION_RECORDING       │
│                           ↓                                 │
│ 50_LAP_DETECTION → 60_SESSION_RESULTS → 70_AI_COACHING      │
│                           ↓                                 │
│         80_SHARE_PROFILE → 90_ERROR_HANDLING                │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                    AFTER TRACK DAY                          │
├─────────────────────────────────────────────────────────────┤
│                   99_ACCEPTANCE_SIGNOFF                     │
└─────────────────────────────────────────────────────────────┘
```

---

## Requirements Traceability

These tests cover all 117 requirements from `BMW_DrivingCoach_SRS_v1.md`:

| SRS Section | Requirement Count | Coverage |
|-------------|------------------|----------|
| User Management (UM) | 19 | ✓ |
| Onboarding (ON) | 8 | ✓ |
| Track Setup (TS) | 13 | ✓ |
| Session Recording (SR) | 11 | ✓ |
| Telemetry Capture (TC) | 12 | ✓ |
| Telemetry Upload (TU) | 13 | ✓ |
| Lap Detection (LD) | 12 | ✓ |
| Lap Comparison (LC) | 10 | ✓ |
| AI Coaching (AI) | 14 | ✓ |
| Driver Progression (DP) | 6 | ✓ |
| Share (SH) | 6 | ✓ |
| Backend API (BE) | 15 | ✓ |
| Non-Functional (NF) | 13 | ✓ |
| Security (SEC) | 9 | ✓ |

---

## Test Device Requirements

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| Android Version | 8.0 (API 26) | 12+ (API 31+) |
| Screen Size | 5 inch | 6+ inch |
| GPS | Required | A-GPS with GLONASS |
| Storage | 500 MB free | 2 GB free |
| RAM | 2 GB | 4 GB |

---

## Track Day Checklist

### What to Bring
- [ ] Android test device (charged 100%)
- [ ] USB cable and car charger
- [ ] Phone mount for vehicle
- [ ] Printed test checklists (or tablet)
- [ ] Pen for marking results
- [ ] Backup device (optional)

### Device Preparation
- [ ] APK installed and tested
- [ ] User account created and logged in
- [ ] Permissions granted (Location, Activity)
- [ ] Battery saver OFF
- [ ] Do Not Disturb enabled
- [ ] Screen timeout set to 10+ minutes

### Network Preparation
- [ ] Verify cellular connectivity at track
- [ ] Test backend health endpoint from track
- [ ] Have WiFi hotspot as backup

---

## Contact Information

| Role | Name | Contact |
|------|------|---------|
| QA Lead | | |
| Dev Lead | | |
| DevOps | | |

---

*Document version: 1.0 | Created: 2026-05-06*
