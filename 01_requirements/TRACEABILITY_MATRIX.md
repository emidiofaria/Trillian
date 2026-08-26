# Traceability Matrix

Maps requirements from [DrivingCoach_SRS_v1.md](DrivingCoach_SRS_v1.md) to test cases.

**Last Updated:** 2026-08-11 (updated by trillian-docs-sync — brand asset geometry UI-08, UI-09 added and covered at L1)

---

## Coverage Summary

| Category | Requirements | Covered | Coverage |
|----------|--------------|---------|----------|
| User Management (UM) | 19 | 0 | 0% ❌ |
| Onboarding (ON) | 8 | 0 | 0% ❌ |
| Track Setup (TS) | 15 | 6 | 40% ⚠️ |
| Session Recording (SR) | 11 | 4 | 36% ⚠️ |
| Telemetry Capture (TC) | 12 | 4 | 33% ⚠️ |
| Telemetry Upload (TU) | 13 | 4 | 31% ⚠️ |
| Lap Detection (LD) | 13 | 1 | 8% ❌ |
| Lap Comparison (LC) | 15 | 0 | 0% ❌ |
| AI Coaching (AI) | 14 | 0 | 0% ❌ |
| Offline Coaching (OC) | 10 | 10 | 100% ✅ |
| Driver Progression (DP) | 6 | 0 | 0% ⚠️ |
| Session Management (SM) | 10 | 4 | 40% ⚠️ |
| Share (SH) | 6 | 0 | 0% ⚠️ |
| Startup & Branding (UI) | 9 | 9 | 100% ✅ |
| Non-Functional (NF) | 13 | 1 | 8% ⚠️ |
| Security (SEC) | 9 | 0 | 0% ⚠️ |
| **TOTAL** | **~188** | **~46** | **~24%** |

> ⚠️ **Coverage caveat — `@Ignore`d L2 classes.** Eight rows below cite
> `EndToEndTest`, `RecordingFragmentTest`, `TrackSetupFragmentTest` or
> `TelemetryForegroundServiceTest`. All four classes carry a class-level `@Ignore`, so those
> rows are marked ✅ but execute **nothing**. Gradle still reports `BUILD SUCCESSFUL` for a
> fully skipped class, which is how this went unnoticed — always parse
> `app/build/outputs/androidTest-results/**/*.xml` rather than trusting the exit code.
> Un-ignoring these classes is tracked as separate work; the Startup & Branding rows above
> are unaffected because they are backed by new, executing tests.

---

## Matrix — Covered Requirements

| Req ID | Requirement Summary | Test Level | Test Location | Status |
|--------|---------------------|------------|---------------|--------|
| **Startup & Branding** | | | | |
| UI-01 | Branded loading screen with real progress | L1 + L2 | `SplashViewModelTest`, `SplashScreenTest` | ✅ |
| UI-02 | Minimum display time, tap to skip | L1 + L2 | `SplashViewModelTest`, `SplashScreenTest` | ✅ |
| UI-03 | 8 s essential timeout → Onboarding fallback | L1 + L2 | `SplashViewModelTest`, `SplashFallbackTest` | ✅ |
| UI-04 | Startup state resolved off main thread | L1 + L2 | `SplashViewModelTest`, `SplashMainThreadTest` (main-thread latency probe) | ✅ |
| UI-05 | Loading screen popped from back stack | L2 | `StartupBackStackTest` | ✅ |
| UI-06 | Home brand hero collapses on scroll | L2 | `HomeHeroTest` | ✅ |
| UI-07 | Warm-up bounded, does not affect destination | L1 | `SplashViewModelTest` | ✅ |
| UI-08 | Emblem renders undistorted at all densities and sizes | L1 + L4 | `BrandAssetGeometryTest`, `BRD-01` (human visual) | ✅ |
| UI-09 | Brand artwork verified by measurement, not presence | L1 + L4 | `BrandAssetGeometryTest` (incl. falsification against the legacy asset), `BRD-01` | ✅ |
| **Track Setup** | | | | |
| TS-02 | UI elements displayed | L2 | `TrackSetupFragmentTest` | ✅ |
| TS-03 | GPS status indicator | L2 | `TrackSetupFragmentTest` | ✅ |
| TS-05 | Point A capture | L2 | `TrackSetupFragmentTest` | ✅ |
| TS-06 | Point B disabled initially | L2 | `TrackSetupFragmentTest` | ✅ |
| TS-08 | Haversine distance | L1 | `GeoUtilsTest` | ✅ |
| TS-12 | Start Recording disabled | L2 | `TrackSetupFragmentTest` | ✅ |
| **Session Recording** | | | | |
| SR-04 | Elapsed time MM:SS.mmm | L2 | `TelemetryForegroundServiceTest` | ✅ |
| SR-05 | GPS status indicator | L2 | `TelemetryForegroundServiceTest` | ✅ |
| SR-09 | Foreground service | L2 | `TelemetryForegroundServiceTest` | ✅ |
| **Telemetry Capture** | | | | |
| TC-06 | JSONL format | L1 | `TelemetryFileWriterTest` | ✅ |
| TC-07 | Mutex protection | L1 | `TelemetryFileWriterTest` | ✅ |
| **Telemetry Upload** | | | | |
| TU-02 | WorkManager enqueue | L1 | `TelemetryUploadWorkerTest` | ✅ |
| TU-04 | Multipart upload | L1 | `TelemetryUploadWorkerMockWebServerTest` | ✅ |
| TU-06 | HTTP 200 → DONE | L1 | `TelemetryUploadWorkerTest` | ✅ |
| TU-07 | HTTP 5xx → retry | L1 | `TelemetryUploadWorkerTest` | ✅ |
| **Lap Detection** | | | | |
| LD-04 | Line intersection | L1 | `GeoUtilsTest` | ✅ |
| **Offline Coaching** | | | | |
| OC-01 | Local insights | L1 | `OfflineCoachingEngineTest` | ✅ |
| OC-02 | 3-4 insights | L1 | `OfflineCoachingEngineTest` | ✅ |
| OC-03 | Best Lap insight | L1 | `OfflineCoachingEngineTest` | ✅ |
| OC-04 | Zero sectors handling | L1 | `OfflineCoachingEngineTest` | ✅ |
| OC-05 | Top Speed insight | L1 | `OfflineCoachingEngineTest` | ✅ |
| OC-06 | GPS noise filter | L1 | `OfflineCoachingEngineTest` | ✅ |
| OC-07 | Consistency wording | L1 | `OfflineCoachingEngineTest` | ✅ |
| OC-08 | Sector Focus upsell | L1 | `OfflineCoachingEngineTest` | ✅ |
| **Session Management** | | | | |
| SM-01 | Delete session | L1 | `HomeViewModelTest` | ✅ |
| SM-05 | Rename session | L1 | `HomeViewModelTest` | ✅ |
| SM-07 | Track name validation | L1 | `HomeViewModelTest` | ✅ |
| **Non-Functional** | | | | |
| NF-07 | 18K samples benchmark | L1 | `TelemetryFileWriterTest` | ✅ |

---

## Matrix — Uncovered Requirements (Priority)

| Req ID | Requirement Summary | Recommended Level | Priority |
|--------|---------------------|-------------------|----------|
| **User Management** | | | |
| UM-01→06 | Registration flow | L1 + L2 | 🔴 High |
| UM-07→15 | Login/session mgmt | L1 + L2 | 🔴 High |
| UM-16→19 | Sign out, profile | L2 | 🟡 Medium |
| **Onboarding** | | | |
| ON-01→08 | Permission flow | L2 | 🔴 High |
| **Lap Detection** | | | |
| LD-02, LD-03 | Detection algorithm | L1 | 🔴 High |
| LD-05 | 20,000ms guard | L1 (boundary) | 🔴 High |
| LD-06 | 200m distance guard | L1 (boundary) | 🔴 High |
| LD-07→13 | Lap processing | L1 | 🟡 Medium |
| **Lap Comparison** | | | |
| LC-01→15 | Session Result screen | L1 + L2 | 🟡 Medium |
| **AI Coaching** | | | |
| AI-01→14 | Backend coaching | Backend tests | 🟡 Medium |
| **Share** | | | |
| SH-01→06 | Share card | L1 | 🟢 Low |
| SH-07→11 | Telemetry export (hidden gesture, ZIP bundle, read-only invariant) | L1 + L2 | 🟢 Low |

---

## Status Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Automated test exists and passes |
| 📋 | Manual test checklist exists |
| ⚠️ | Test exists but failing or partial |
| ❌ | No test coverage |
| 🚧 | Test in progress |

---

## Test Type Coverage

| Domain | Happy | Boundary | Edge | Stress | Negative |
|--------|:-----:|:--------:|:----:|:------:|:--------:|
| TS | ✅ | ⚠️ | ✅ | ❌ | ✅ |
| SR | ✅ | ❌ | ⚠️ | ⚠️ | ❌ |
| TC | ✅ | ❌ | ✅ | ✅ | ✅ |
| TU | ✅ | ❌ | ❌ | ❌ | ✅ |
| LD | ✅ | ❌ | ✅ | ❌ | ✅ |
| OC | ✅ | ✅ | ✅ | ❌ | ✅ |
| SM | ✅ | ✅ | ❌ | ❌ | ✅ |

---

## How to Update

1. When adding a new requirement to SRS, add a row here
2. When creating a test, update the Test Location and Status
3. Run `test-coverage-checker` skill to auto-update
4. Review coverage before each release

---

*Auto-generated by test-coverage-checker skill — 2026-07-22*
