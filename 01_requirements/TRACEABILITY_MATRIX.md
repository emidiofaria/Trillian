# Traceability Matrix

Maps requirements from [DrivingCoach_SRS_v1.md](DrivingCoach_SRS_v1.md) to test cases.

> **This document is now the human narrative; the machine truth is
> [`05_tests/coverage-map.tsv`](../05_tests/coverage-map.tsv).** Every claim in that file is
> resolved against the actual test results each time the HTML report is generated, so a claim
> naming a test that did not run, or no longer exists, is reported rather than counted. If the
> two disagree, the report is right and this table is stale — see section 3 and 4 of any
> `TEST_REPORT.html`. The percentages below are hand-maintained and should be read as
> intent, not evidence.

**Last Updated:** 2026-09-29 (updated by trillian-docs-sync — LD-23 added and covered by `MergedLapCaveatTest`, LD count 22 → 23; Cabo do Mundo's fast-lap envelope corrected to 50 s; L1 grew 329 → 343 tests)

---

## Coverage Summary

| Category | Requirements | Covered | Coverage |
|----------|--------------|---------|----------|
| User Management (UM) | 19 | 0 | *deferred to V2 — 17 of 19 out of scope for V1* |
| Driver Profile (DR) | 8 | 8 | 100% ✅ |
| Onboarding (ON) | 8 | 0 | 0% ❌ |
| Track Setup (TS) | 20 | 12 | 60% ⚠️ |
| Track Library (TL) | 18 | 16 | 89% ⚠️ |
| Session Recording (SR) | 11 | 4 | 36% ⚠️ |
| Telemetry Capture (TC) | 12 | 4 | 33% ⚠️ |
| Telemetry Upload (TU) | 13 | 4 | 31% ⚠️ |
| Lap Detection (LD) | 23 | 9 | 39% ⚠️ |
| Lap Comparison (LC) | 15 | 0 | 0% ❌ |
| Session Analysis (AS) | 18 | 18 | 100% ✅ |
| AI Coaching (AI) | 14 | 0 | 0% ❌ |
| Offline Coaching (OC) | 10 | 10 | 100% ✅ |
| Driver Progression (DP) | 6 | 0 | 0% ⚠️ |
| Session Management (SM) | 10 | 4 | 40% ⚠️ |
| Share (SH) | 6 | 0 | 0% ⚠️ |
| Startup & Branding (UI) | 9 | 9 | 100% ✅ |
| Non-Functional (NF) | 13 | 1 | 8% ⚠️ |
| Security (SEC) | 9 | 0 | 0% ⚠️ |
| **TOTAL** | **~243** | **~98** | **~40%** |

> ℹ️ **Resolved — the `@Ignore`d L2 classes were deleted (2026-09-16).** Eight rows used to
> cite `EndToEndTest`, `RecordingFragmentTest`, `TrackSetupFragmentTest` or
> `TelemetryForegroundServiceTest`. All four carried a class-level `@Ignore`, so those rows
> showed ✅ while executing **nothing**. Rather than revive them, the classes were deleted and
> the eight claims removed, because 14 of their 29 tests were `isDisplayed()` assertions that
> could not fail when the app was broken. **TS-02, TS-03, TS-05, TS-06, TS-12, SR-04, SR-05
> and SR-09 now have no automated coverage** and are marked as such in the SRS — SR-09
> (recording survives backgrounding) is core V1 behaviour and is the most significant gap.
> The original lesson stands: Gradle reports `BUILD SUCCESSFUL` for a fully skipped class, so
> always parse `app/build/outputs/androidTest-results/**/*.xml` rather than trusting the exit
> code. Deleted tests are recoverable from git at `0216475`.

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
| **Driver Profile** | | | | |
| DR-01 | Driver Name screen shown after onboarding | L1 + L2 | `SplashViewModelTest`, `DriverNameFlowTest` | ✅ |
| DR-02 | Name 2–100 chars; action disabled while invalid | L1 + L2 | `DriverProfileStoreTest` (bounds 1/2/100/101), `DriverNameViewModelTest`, `DriverNameFlowTest` | ✅ |
| DR-03 | Name trimmed before persistence | L1 + L2 | `DriverProfileStoreTest`, `DriverNameFlowTest.surroundingWhitespaceIsTrimmedFromTheSavedName` | ✅ |
| DR-04 | `user_name` + `driver_profile_complete` written atomically | L1 | `DriverProfileStoreTest` (round-trip, flag independence) | ✅ |
| DR-05 | Relaunch goes straight to Home; destination resolution order | L1 + L2 | `SplashViewModelTest`, `DriverNameFlowTest.aSavedDriverGoesStraightToHomeOnRelaunch`, `StartupBackStackTest` | ✅ |
| DR-06 | Rename from Profile; never re-keys sessions | L1 + L2 | `ProfileViewModelTest` (asserts database untouched), `DriverNameFlowTest.theSavedNameIsShownOnTheProfileScreen` | ✅ |
| DR-07 | Clear User Data — confirm, wipe Room + files + prefs, return to Onboarding | L1 | `ProfileViewModelTest` (`inOrder`: paths read before `clearAllTables()`) | ✅ |
| DR-08 | No auth or demo-mode affordance; "LET'S RACE!!" CTA | L2 | `DriverNameFlowTest.theDemoModeShortcutIsGone` | ✅ |
| **Track Setup** | | | | |
| TS-02 | UI elements displayed | — | *(none — test deleted 2026-09-16)* | ❌ |
| TS-03 | GPS status indicator | — | *(none — test deleted 2026-09-16)* | ❌ |
| TS-05 | Point A capture | — | *(none — test deleted 2026-09-16)* | ❌ |
| TS-06 | Point B disabled initially | — | *(none — test deleted 2026-09-16)* | ❌ |
| TS-08 | Haversine distance | L1 | `GeoUtilsTest` | ✅ |
| TS-12 | Start Recording disabled | — | *(none — test deleted 2026-09-16)* | ❌ |
| TS-15 | Fused updates; resubscribe after stop/start | L2 | `TrackSetupResubscribeTest` (executing, not `@Ignore`d) | ✅ |
| TS-16 | Warm-up starts when Home becomes visible | L1 + L2 | `LocationWarmUpTest`, `HomeViewModelTest`, `HomeGpsWarmUpTest` | ✅ |
| TS-17 | Warm-up is silent on Home; readiness persists across the Home → Track Setup handover | L2 | `HomeGpsWarmUpTest`, `WarmUpHandoverTest` | ✅ |
| TS-18 | Warm-up stops on background / 3 min idle ceiling | L1 + L2 | `LocationWarmUpTest`, `HomeViewModelTest`, `HomeGpsWarmUpTest` | ✅ |
| TS-19 | Time-to-first-fix metrics recorded and shown on About | L1 | `LocationWarmUpTest` | ✅ |
| TS-20 | Warm-up exposes readiness only, never a position | L1 | `LocationWarmUpTest` | ✅ |
| **Track Library** | | | | |
| TL-01 | Bundled read-only circuit catalogue | L1 | `BundledTrackCatalogTest` | ✅ |
| TL-02 | SELECT TRACK / NEW CIRCUIT fork after naming | L2 | `SessionStartForkTest`, `TrackLibraryGateTest` | ✅ |
| TL-03 | One list of bundled and saved circuits | L2 | `TrackLibraryGateTest`, `SessionStartForkTest`, `TrackRepositoryTest` | ✅ |
| TL-04 | Catalogue entry carries the priors the detector needs | L1 + L2 | `BundledTrackCatalogTest`, `TrackRepositoryTest` (centreline fidelity) | ✅ |
| TL-05 | Provenance recorded per dataset, not per circuit | L1 | `BundledTrackCatalogTest` | ✅ |
| TL-06 | Confirmation screen + GPS readiness gate | L2 | `TrackConfirmDisplayTest`, `TrackLibraryGateTest` | ✅ |
| TL-07 | Start line re-resolved by id, never passed as a nav float | L2 | `TrackPriorsHandoffTest` | ✅ |
| TL-08 | Session persists the circuit id it recorded against | L2 | `TrackPriorsHandoffTest` | ✅ |
| TL-09 | Offer to save a captured line as a circuit | L2 | `SaveCapturedCircuitTest`, `TrackRepositoryTest` | ✅ |
| TL-10 | A circuit under 3 m is refused | L2 | `SaveCapturedCircuitTest`, `TrackRepositoryTest` | ✅ |
| TL-11 | Saved circuits renameable/deletable, bundled ones not | L2 | `TrackRepositoryTest`, `SaveCapturedCircuitTest` | ✅ |
| TL-12 | V1 list is plain and manual | — | *(none — absence of behaviour)* | ❌ |
| TL-13 | Recording may only touch last-used | L2 | `TrackRepositoryTest`, `TrackPriorsHandoffTest` | ✅ |
| TL-14 | Missing/corrupt asset behaves as an empty catalogue | — | *(none — see uncovered list)* | ❌ |
| TL-15 | Priors corroborated against a recorded session before shipping | L1 | `BaltarSurveyCorroborationTest`, `CaboDoMundoSurveyCorroborationTest` | ✅ |
| TL-16 | Bundled circuit must declare length and envelope | L1 | `BundledTrackCatalogTest` | ✅ |
| TL-17 | No requirement names a specific circuit | L1 | `CircuitEvidenceAnnexTest` | ✅ |
| TL-18 | Annex A and the catalogue match, both ways | L1 | `CircuitEvidenceAnnexTest` | ✅ |
| **Session Recording** | | | | |
| SR-04 | Elapsed time MM:SS.mmm | — | *(none — test deleted 2026-09-16)* | ❌ |
| SR-05 | GPS status indicator | — | *(none — claim was unfounded)* | ❌ |
| SR-09 | Foreground service | — | *(none — test deleted 2026-09-16)* | ❌ |
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
| LD-19 | Direction-of-travel filter on crossings | L1 | `LapDetectionIncident15Test` | ✅ |
| LD-20 | Catalogued heading is the reference | L1 + L2 | `LapDetectionIncident15Test`, `TrackPriorsEndToEndTest` (full replay through the production path) | ✅ |
| LD-21 | Catalogued fastest lap tightens the minimum gap | L1 | `LapDetectionIncident15Test` | ✅ |
| LD-22 | Wholly implausible lap sets discarded by surveyed distance | L1 | `LapDetectionIncident15Test` | ✅ |
| LD-23 | A lap count that may contain merged laps is qualified | L1 | `MergedLapCaveatTest` | ✅ |
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
| **Session Analysis** | | | | |
| AS-01 | ANALYSIS tab present, fourth, after CHART | L2 | `AnalysisTabTest.analysisTabIsPresentOnTheSessionResultScreen` | ✅ |
| AS-02 | Distance, duration, max/avg speed, best lap | L1 + L2 | `SessionAnalysisProcessorTest`, `AnalysisTabTest.analysisTabRendersStatsMapAndDerivedTables` | ✅ |
| AS-03 | Distance is cumulative haversine; average includes standing time | L1 | `SessionAnalysisProcessorTest` | ✅ |
| AS-04 | Track map drawn from GPS trace, no SDK/tiles/network | L1 + L2 | `TrackPathProjectionTest`, `AnalysisTabTest` | ✅ |
| AS-05 | Speed gradient, red braking, `T1..Tn`, start/finish marker | L1 | `TrackPathProjectionTest` (path + markers); rendering itself is L4 visual | ⚠️ |
| AS-06 | Lap chips come from §8 laps; laps never re-derived | L1 + L2 | `SessionAnalysisProcessorTest` (lap windows supplied, not computed), `AnalysisTabTest` | ✅ |
| AS-07 | Reference defaults to best lap; selection recomputes and is labelled | L2 | `AnalysisTabTest.pickingADifferentLapRebuildsTheReferenceView` | ✅ |
| AS-08 | Corners from yaw rate > 6 °/s for ≥ 1.5 s, with apex speed | L1 | `SessionAnalysisProcessorTest` | ✅ |
| AS-09 | Corner numbering by order of passage, caveat stated | L1 | `SessionAnalysisProcessorTest` (naming); caveat text is L4 visual | ✅ |
| AS-10 | Braking from GPS decel, with peak g and duration | L1 | `SessionAnalysisProcessorTest` | ✅ |
| AS-11 | Accelerometer explicitly not used, and said so | — | Static string + `SessionAnalysisProcessor` reads no IMU field | 📋 |
| AS-12 | Braking zone associated with the corner it precedes | L1 | `SessionAnalysisProcessorTest` | ✅ |
| AS-13 | Speed-vs-time graph spans the whole session | L1 + L2 | `SessionAnalysisProcessorTest`, `AnalysisTabTest` | ✅ |
| AS-14 | Same result at 1 Hz and 10 Hz | L1 | `SessionAnalysisRateInvarianceTest` | ✅ |
| AS-15 | No heading below 2 m travel; no corner below 10 km/h | L1 | `SessionAnalysisGuardsTest` | ✅ |
| AS-16 | Missing file and too-short session reported distinctly | L1 + L2 | `SessionAnalysisGuardsTest`, `AnalysisTabTest.missingTelemetryShowsTheEmptyStateInsteadOfCrashing` | ✅ |
| AS-17 | Unusable lap window falls back to whole session *and says so* | L1 | `SessionAnalysisGuardsTest.aLapWindowThatDoesNotOverlapTheTelemetryFallsBackToTheWholeSession` | ✅ |
| **Non-Functional** | | | | |
| NF-07 | 18K samples benchmark | L1 | `TelemetryFileWriterTest` | ✅ |

---

## Matrix — Uncovered Requirements (Priority)

| Req ID | Requirement Summary | Recommended Level | Priority |
|--------|---------------------|-------------------|----------|
| **User Management** *(deferred to V2)* | | | |
| UM-01→06 | Registration flow | L1 + L2 | ⏸️ V2 — not implemented in V1 |
| UM-07→15 | Login/session mgmt | L1 + L2 | ⏸️ V2 — not implemented in V1 |
| UM-16→17 | Sign out | — | ⏸️ V2 — superseded in V1 by DR-07 |
| UM-18, UM-19 | Profile stats and avatar initials | L1 + L2 | 🟡 Medium — partially exercised by `ProfileViewModelTest` and `DriverNameFlowTest` |
| **Onboarding** | | | |
| ON-01→03, ON-05, ON-06, ON-08 | Permission flow | L2 | 🔴 High |
| ON-04, ON-07 | Hand-off to Driver Name / straight to Home | L1 + L2 | ✅ Covered via DR-01 and DR-05 |
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
| TL | ✅ | ✅ | ✅ | ❌ | ✅ |
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
