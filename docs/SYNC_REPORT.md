# Documentation Sync Report

Cumulative changelog of documentation synchronizations with codebase.

---

## [2026-08-06] L2 Integration Coverage for Branded Startup (SWE.5)

**Trigger:** SRS UI-01…UI-06 were nominally covered, but the L2 suite was a *false green* —
all four existing instrumented classes carry a class-level `@Ignore`, and Gradle reports
`BUILD SUCCESSFUL` for a fully skipped suite. UI-05 was manual-only and UI-06 had no
coverage at all.

**Outcome:** 5 new instrumented test classes (18 executing tests, 0 failures, 0 skipped),
enabled by a Hilt module split. Documentation realigned to reflect real, executing coverage.

### Summary

| Area | Change |
|------|--------|
| Production | `AppModule` split into `DataStoreModule`, `DispatcherModule`, `SplashModule` |
| Tests | +5 L2 classes (18 tests), +3 shared test fixtures |
| Requirements | UI-01…UI-06 promoted to L2; UI-05 ⚠️→✅, UI-06 ❌→✅ |
| Atlas | Test hooks, L2 verification table, strengthened regression guard |

### Key Finding — Espresso Cannot Detect a Blocked Main Thread

The first UI-04 design asserted "main thread is not blocked" by performing an Espresso click
during a stalled startup. To validate it, the original defect was deliberately reintroduced:

```kotlin
// SplashFragment.onViewCreated — TEMPORARY, for falsification only
runBlocking { delay(60_000) }
```

The Espresso-based test **still passed**. Espresso synchronises *with* the main looper — it
waits for idle rather than timing out — so blocking the main thread only makes it slower.

The replacement detector samples main-looper round-trip latency from a background thread:

```kotlin
// MainThreadResponsivenessProbe.kt
private const val SAMPLE_INTERVAL_MS = 50L
private const val PER_SAMPLE_TIMEOUT_MS = 15_000L
// posts a Runnable to Handler(Looper.getMainLooper()), records worstLatencyMs
```

Re-running the falsification with the probe in place produced:

```
main thread was unresponsive for 15000ms during startup (budget 2000ms)
```

The injected block was then fully reverted (`SplashFragment.kt` verified clean).
**Rule adopted:** any "must not block" assertion is untrusted until it has been falsified.

### Files Changed

```
 M app/src/main/java/com/drivingcoach/di/AppModule.kt                (-28 lines, now context only)
 A app/src/main/java/com/drivingcoach/di/DataStoreModule.kt
 A app/src/main/java/com/drivingcoach/di/DispatcherModule.kt
 A app/src/main/java/com/drivingcoach/di/SplashModule.kt
 A app/src/androidTest/java/com/drivingcoach/testing/FakePreferencesDataStores.kt
 A app/src/androidTest/java/com/drivingcoach/testing/NavigationTestExtensions.kt
 A app/src/androidTest/java/com/drivingcoach/testing/MainThreadResponsivenessProbe.kt
 A app/src/androidTest/java/com/drivingcoach/ui/splash/SplashScreenTest.kt        (6 tests, UI-01/02)
 A app/src/androidTest/java/com/drivingcoach/ui/splash/SplashFallbackTest.kt      (3 tests, UI-03)
 A app/src/androidTest/java/com/drivingcoach/ui/splash/SplashMainThreadTest.kt    (3 tests, UI-04)
 A app/src/androidTest/java/com/drivingcoach/StartupBackStackTest.kt              (2 tests, UI-05)
 A app/src/androidTest/java/com/drivingcoach/ui/home/HomeHeroTest.kt              (4 tests, UI-06)
 M 01_requirements/TRACEABILITY_MATRIX.md
 M SkunkOps/atlas/system.md
 M SkunkOps/atlas/components.md
 M SkunkOps/atlas/flows.md
 M SkunkOps/atlas/failure-patterns.md
 M 05_tests/L2_SWE5_integration/README.md
 M docs/SYNC_REPORT.md
```

### Documentation Updates

**`01_requirements/TRACEABILITY_MATRIX.md`**
- UI-01…UI-04: level `L1` → `L1 + L2`, test column now names the L2 class.
- UI-05: `Manual` / ⚠️ *No automated test* → `L2` / `StartupBackStackTest` / ✅.
- UI-06: ❌ *Not covered* → ✅ `HomeHeroTest`.
- Startup & Branding category: **71% ⚠️ → 100% ✅**; TOTAL **~42 → ~44 (~24%)**.
- New "Coverage caveat — `@Ignore`d L2 classes" callout above the matrix, naming the eight
  rows whose ✅ is backed by a skipped class and giving the XML-parsing verification command.

**`SkunkOps/atlas/system.md`**
- Repository-layout `di/` entry now enumerates the split modules and states *why*
  (per-binding `@UninstallModules` in tests).

**`SkunkOps/atlas/components.md`**
- Preferences DataStore component: key code area `di/AppModule.kt` → `di/DataStoreModule.kt`.
- Branded Startup component: new **Test Hooks** section — a goal→uninstall→substitute table
  plus the "do not use Espresso for UI-04" warning.
- New observable signal row: `MainThreadResponsivenessProbe.worstLatencyMs`.

**`SkunkOps/atlas/flows.md`**
- Timing-contract table: `SplashTimings` now injected via `SplashModule` (was `AppModule`).
- New **Automated Verification (L2)** section mapping each stage of the startup flow to its
  instrumented test and SRS ID.

**`SkunkOps/atlas/failure-patterns.md`**
- *DataStore ANR on Startup* → **Regression Guard** rewritten: L1 + L2 split, the 2000 ms
  latency budget, and the Espresso falsification evidence.
- Confidence rationale extended to "empirically falsified".

**`05_tests/L2_SWE5_integration/README.md`**
- Test-location tree updated with all new files and `@Ignore` markers on legacy classes.
- New **"`BUILD SUCCESSFUL` Is Not Evidence"** section with the JUnit-XML parsing snippet.
- New **Test Isolation Strategy** section explaining the module split and why the fallback
  and main-thread tests must live in separate classes.

**`docs/USER_MANUAL.md`** — *no change required.* This work added test coverage and
refactored DI only; no user-visible behaviour changed.

### Validation

| Level | Result |
|-------|--------|
| `assembleDebug` / `compileDebugKotlin` | ✅ PASS |
| `compileDebugAndroidTestKotlin` | ✅ PASS |
| L1 (SWE.4 unit) | ✅ **105 tests, 0 failures, 0 skipped** |
| L2 new (SWE.5) | ✅ **18 tests, 0 failures, 0 skipped** |
| L2 legacy | ⚠️ 4 classes still `@Ignore`d — out of scope, tracked separately |
| UI-04 detector falsification | ✅ Fails when the defect is reintroduced |

### Known Gaps / Follow-ups

- The 4 legacy `@Ignore`d L2 classes remain decorative; un-ignoring them is separate work.
- `MAX_ACCEPTABLE_LATENCY_MS = 2_000` is emulator-derived and may need tuning on slower CI.
- The three `Log.d` startup-timing statements are unconditional; consider gating behind
  `BuildConfig.DEBUG`.

---

## [2026-07-25] Startup Hardening — Device Verification & Timeout Redesign

**Codebase Version:** v2.8.1-brand-startup-fixes
**Trigger:** Attempt to execute L2 (SWE.5 integration) tests for the v2.8 branded startup work

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| SplashTimings.kt | ✅ Updated | Essential timeout 3000→8000 ms; new `warmUpTimeoutMs = 2000` |
| SplashViewModel.kt | ✅ Updated | Essential/optional split, ONBOARDING fallback, permanent timing logs |
| fragment_splash.xml | ✅ Updated | `fitsSystemWindows`; tagline↔manifesto constraint chain |
| themes.xml + ic_splash_emblem.xml | ✅ Added/Updated | System-splash icon no longer mask-cropped |
| strings.xml | ✅ Updated | Footer metrics refreshed (Tests 104, SRS 123) |
| SplashViewModelTest.kt | ✅ Updated | 13 tests; new warm-up isolation regression test |
| DrivingCoach_SRS_v1.md | ✅ Updated | UI-03 reworded; **new UI-07** (bounded non-essential warm-up) |
| TRACEABILITY_MATRIX.md | ✅ Updated | UI-05 corrected to manual-only; UI-07 added |
| flows.md | ✅ Updated | Split timeout contract, fallback rationale, logcat signals |
| components.md | ✅ Updated | Failure modes split, measured signals added |
| failure-patterns.md | ✅ Updated | Post-mitigation table + measured evidence table |
| USER_MANUAL.md | ✅ Updated | §2.2 "3 seconds" → 8 s ceiling + Onboarding fallback |

### Key Finding — L2 Is a False Green

`./gradlew connectedDebugAndroidTest` reports `BUILD SUCCESSFUL`, but the results XML shows
`tests=4 failures=0 skipped=4`. **All four L2 classes are class-level `@Ignore`d**
(`EndToEndTest`, `TelemetryForegroundServiceTest`, `RecordingFragmentTest`,
`TrackSetupFragmentTest`) for pre-existing reasons unrelated to this work. L2 therefore
provides **zero** real coverage today. Always parse the results XML, never trust the exit code.

Consequence: the earlier claim that UI-05 was "⚠️ Partial via EndToEndTest" was wrong and has
been corrected in the traceability matrix.

### Defects Found by Direct Device Verification

Because L2 gave no signal, verification was performed directly on `emulator-5554`.

| # | Defect | Root cause | Fix |
|---|--------|-----------|-----|
| 1 | Fresh install landed on **Login**, not Onboarding | 3 s global timeout fired on a *normal* cold start (`DataStore.data.first()` = 3 573 ms); fallback became the common path | Essential timeout → 8 s; warm-up split behind its own 2 s bound; fallback → ONBOARDING |
| 2 | Splash footer clipped by navigation bar | `MainActivity` applies only left/right insets by design; fragment must opt in | `fitsSystemWindows="true"` on `splashRoot` |
| 3 | Stale footer metrics string | Not refreshed after test/SRS growth | Updated to Tests 104 / SRS 123 |
| 4 | Android 12+ system splash icon hard-cropped | Adaptive-icon circular mask vs. full-bleed `ic_launcher_foreground` | New `ic_splash_emblem.xml` (`<inset>` 20%) |
| 5 | Tagline colliding with manifesto card | No constraint linking the two views | `Top_toBottomOf` + margins + `verticalBias=1.0` |

### Design Rationale — Why ONBOARDING, Not LOGIN

If the preferences read fails we do not know whether the user has onboarded. The costs are
asymmetric: routing an **already-onboarded** user through Onboarding is a recoverable
annoyance that still ends at Home; routing a **fresh** user to Login skips permission granting
entirely and leaves the app unable to record. ONBOARDING is therefore the strictly safer default.

### Design Rationale — Essential vs. Optional Work

The destination decision depends **only** on the preferences read. The Room warm-up is a pure
optimisation, so it now carries its own 2 s bound and is wrapped in `runCatching`: a slow or
broken database can never change where the user lands. Guarded by the L1 test
`slow database warm up does not change the destination`.

### Validation

| Level | Result |
|-------|--------|
| `compileDebugKotlin` / `compileDebugAndroidTestKotlin` / `assembleDebug` | ✅ PASS |
| L1 (SWE.4 unit) | ✅ **105 tests, 0 failures, 0 skipped** (clean run, emulator stopped) |
| L1 splash subset | ✅ 13 tests, 0 failures |
| L2 (SWE.5 integration) | ⚠️ Runs, but 4/4 classes `@Ignore`d — **no coverage** |
| Device verification (`emulator-5554`) | ✅ Splash renders correctly; destination = Onboarding on fresh install; footer clear of nav bar; Home hero matches mockup; **UI-05 confirmed** (Back from Home exits to launcher) |

Measured startup (Pixel 4 API 30 emulator): cold `datastore read 3 573 ms` →
`resolved in 4 458 ms`; warm `datastore read 1 878 ms`, `room warm-up 955 ms` →
`resolved=ONBOARDING in 2 889 ms`.

Note: `TelemetryFileWriterTest > benchmark 18000 samples writes in under 100ms` is
**load-sensitive**, not a regression — it fails only under CPU contention from a running
emulator and passes on a clean run.

### Open Items

- Four `@Ignore`d L2 classes make the entire L2 level decorative — recommend a follow-up to
  un-ignore at minimum `EndToEndTest`, which would give real automated UI-05 coverage.
- The 8 s essential budget is derived from emulator measurements; real-device figures are
  unknown and it may be tunable downward.
- The three `Log.d` startup timing statements are currently unconditional; decide whether to
  gate them behind `BuildConfig.DEBUG`.

---

## [2026-07-24] Branded Loading Screen & Home Brand Hero

**Codebase Version:** v2.8-brand-startup  
**Trigger:** TRILLIAN brand mockup implementation — splash loading screen + collapsing Home hero

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| system.md | ✅ Updated | +6/-3 (splash module, SplashScreen dep, ANR risk closed) |
| components.md | ✅ Updated | +141 lines (2 new components) |
| flows.md | ✅ Updated | +74/-16 (startup flow fully rewritten) |
| failure-patterns.md | ✅ Updated | +80/-45 (DataStore ANR marked MITIGATED) |
| DrivingCoach_SRS_v1.md | ✅ Updated | ON-01 reworded, +6 requirements (§3a) |
| TRACEABILITY_MATRIX.md | ✅ Updated | +6 rows, new UI category (83% covered) |
| USER_MANUAL.md | ✅ Updated | §2.2 new, §4.0 new, §2.3–2.5 renumbered |

### New Requirement IDs

- **UI-01** — Branded loading screen with progress tied to real initialisation work
- **UI-02** — Minimum display time (1.2 s) with tap-to-skip
- **UI-03** — 3 s initialisation timeout with Login fallback
- **UI-04** — Startup state resolved off the main thread
- **UI-05** — Loading screen removed from the back stack
- **UI-06** — Home brand hero collapses on scroll

**Modified:** ON-01 (reworded — start destination is now resolved asynchronously)

### Architectural Change

The `runBlocking` DataStore read at `MainActivity:68` — a **P1 documented ANR risk** in
`failure-patterns.md` — has been removed. Startup state now resolves in `SplashViewModel`
on an injected `@IoDispatcher`, bounded by `withTimeoutOrNull(3000)` with a Login fallback.
The pattern is now marked **✅ MITIGATED** and downgraded from P1 to Closed.

### Detailed Changes

#### 📁 SkunkOps/atlas/system.md

```diff
  ├── recording/
- │   └── session/
+ │   ├── session/
+ │   └── splash/               # Branded loading screen (startup resolution)

+ | Splash | AndroidX Core SplashScreen | 1.0.1 | `installSplashScreen()`, Android 12+ handoff |

- | **Blocking DataStore read** | MEDIUM | `runBlocking` in `MainActivity.setupNavigation()` | Move to suspending |
+ | ~~**Blocking DataStore read**~~ | ✅ RESOLVED v2.8 | Was `runBlocking` … | Replaced by async `SplashViewModel` |
```

#### 📁 SkunkOps/atlas/components.md

**Added Section: App Startup / Branded Loading Screen** (criticality HIGH) — key code areas,
dependencies, the 5-stage progress model, failure modes, observable signals.

**Added Section: Home Brand Hero (Collapsing Toolbar)** (criticality LOW) — layout structure,
scroll/alpha behaviour table, failure modes.

#### 📁 SkunkOps/atlas/flows.md

**Rewrote: Flow: App Startup & Navigation Resolution**

```diff
- → DataStore.data.first() [BLOCKING runBlocking]
- → NavController.setStartDestination()
+ → installSplashScreen()
+ → NavController starts at splashFragment
+ → SplashViewModel.start()
+   → withTimeoutOrNull(3000) { withContext(IO) { … } }
+   → awaitMinimumDisplay(1200)  [skippable]
+ → navigate(popUpTo splashFragment inclusive)
```

Added a **Timing Contract** table and rewrote Async Boundaries, Failure Points,
Retry/Recovery and Operational Signals.

#### 📁 SkunkOps/atlas/failure-patterns.md

```diff
- ## Pattern: DataStore ANR on Startup
+ ## Pattern: DataStore ANR on Startup — ✅ MITIGATED (v2.8)

- | DataStore ANR on Startup | MEDIUM | HIGH | HIGH | P1 |
+ | DataStore ANR on Startup | ~~MEDIUM~~ MITIGATED | HIGH | HIGH | ~~P1~~ Closed (v2.8) |
```

Added *Regression Signals*, *Current Behaviour (post-mitigation)*, a Permanent Fix status
table, and a *Regression Guard* pointing at `SplashViewModelTest`.

#### 📁 01_requirements/DrivingCoach_SRS_v1.md

**Added Requirements (new §3a — Application startup and branding):**

| ID | Requirement |
|----|-------------|
| UI-01 | The app SHALL display a branded loading screen on cold start with a progress indicator reflecting real initialisation work |
| UI-02 | The loading screen SHALL remain visible for a minimum of 1.2 s and SHALL be dismissible early by tapping |
| UI-03 | Startup initialisation SHALL be bounded by a 3 s timeout, after which the app SHALL navigate to Login |
| UI-04 | Startup state resolution SHALL NOT block the main thread |
| UI-05 | The loading screen SHALL be removed from the back stack on navigation |
| UI-06 | The Home screen SHALL present a brand hero that collapses as the user scrolls |

#### 📁 01_requirements/TRACEABILITY_MATRIX.md

```diff
+ | Startup & Branding (UI) | 6 | 5 | 83% ✅ |
- | **TOTAL** | **~179** | **~37** | **~21%** |
+ | **TOTAL** | **~185** | **~42** | **~23%** |
```

#### 📁 docs/USER_MANUAL.md

**Added Section 2.2 — The Loading Screen**: explains the progress bar reflects real work, the
step-by-step table, the ~1 s typical / 3 s maximum wait, tap-to-skip, and Back-exits-app.

**Added Section 4.0 — The Home Screen**: describes the collapsing brand hero, the persistent
profile button, and the content below it.

**Renumbered:** 2.2→2.3 (First Launch & Permissions), 2.3→2.4 (Creating Your Account),
2.4→2.5 (Logging In).

### Validation

| Level | ASPICE | Result |
|-------|--------|--------|
| L1 unit | SWE.4 | ✅ 104 tests, 0 failures (12 new in `SplashViewModelTest`) |
| Compile (main) | — | ✅ `compileDebugKotlin` |
| Compile (androidTest) | — | ✅ `compileDebugAndroidTestKotlin` |
| L2 instrumented | SWE.5 | ⏸️ Not run (requires emulator/KVM) |
| Visual verification | — | ⏸️ Not performed |

### Files Modified

```
M  SkunkOps/atlas/system.md                          (+6, -3)
M  SkunkOps/atlas/components.md                      (+141, -0)
M  SkunkOps/atlas/flows.md                           (+74, -16)
M  SkunkOps/atlas/failure-patterns.md                (+80, -45)
M  01_requirements/DrivingCoach_SRS_v1.md            (+16, -2)
M  01_requirements/TRACEABILITY_MATRIX.md            (+12, -3)
M  docs/USER_MANUAL.md                               (+49, -5)
M  app/build.gradle.kts                              (+1)
M  app/src/main/AndroidManifest.xml                  (+1, -1)
M  app/src/main/java/com/drivingcoach/di/AppModule.kt (+11)
M  app/src/main/java/com/drivingcoach/ui/MainActivity.kt (+3, -23)
M  app/src/main/java/com/drivingcoach/ui/home/HomeFragment.kt (+28, -4)
M  app/src/main/res/layout/fragment_home.xml         (+150, -41)
M  app/src/main/res/navigation/nav_graph.xml         (+26, -2)
M  app/src/main/res/values/{colors,dimens,strings,themes,type}.xml
M  app/src/androidTest/java/com/drivingcoach/EndToEndTest.kt (+2, -2)
A  app/src/main/java/com/drivingcoach/ui/splash/SplashViewModel.kt
A  app/src/main/java/com/drivingcoach/ui/splash/SplashFragment.kt
A  app/src/main/java/com/drivingcoach/ui/splash/SplashDestination.kt
A  app/src/main/java/com/drivingcoach/ui/splash/SplashTimings.kt
A  app/src/main/java/com/drivingcoach/di/IoDispatcher.kt
A  app/src/main/res/layout/fragment_splash.xml
A  app/src/main/res/drawable/{ic_helmet_emblem,bg_hero_ring,ic_dot}.xml
A  app/src/test/java/com/drivingcoach/ui/splash/SplashViewModelTest.kt
```

### Recommendations

- [ ] **Visual check needed** — `ic_helmet_emblem.xml` was hand-authored and has never been
      rendered; the emblem may need tuning
- [ ] **L2 run needed** — collapsing hero animation and splash→destination routing unverified
      on a device (requires emulator/KVM)
- [ ] Add L2 coverage for UI-06 (hero collapse) — currently uncovered
- [ ] DataStore corruption recovery remains open (falls back to empty preferences)
- [ ] Pre-existing: SRS AD-04 mandates Firebase Auth but the code uses JWT-in-DataStore —
      still unreconciled

---

## [2026-07-22] Test Documentation Sync to Agent Instructions

**Codebase Version:** v2.7-aspice-tests  
**Trigger:** Update agent instructions with ASPICE test infrastructure references

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| trillian-docs-sync.md | ✅ Updated | Path references to `05_tests/L4_SYS5_acceptance/` |
| SW_dev_agent.md | ✅ Updated | Enhanced Step 7 Validation with ASPICE details |
| RCA_agent.md | ✅ Updated | Added Step 8 Test-Based Verification |
| system.md (Atlas) | ✅ Updated | Added emulator configuration details |

### Detailed Changes

**trillian-docs-sync.md:**
- Updated acceptance test path: `human_system_acceptance_tests/` → `05_tests/L4_SYS5_acceptance/`
- Added full test infrastructure section with ASPICE levels (L1-L4)
- Updated file locations reference tree

**SW_dev_agent.md (Step 7 — Validation):**
- Added ASPICE test level reference table
- Added infrastructure script commands
- Added emulator management instructions
- Added KVM requirement documentation
- Added test report verification workflow
- Added minimum validation requirements matrix

**RCA_agent.md:**
- Added new Step 8: Test-Based Verification (optional)
- Added when-to-use decision table
- Added test verification output format

**system.md (Atlas):**
- Added emulator configuration block (API 30, Pixel 4, AVD name)
- Added KVM access requirement note

### Files Modified

```
M  .github/skills/trillian-docs-sync.md     (+18, -8)
M  .github/agents/SW_dev_agent.md           (+65, -25)
M  .github/agents/RCA_agent.md              (+35, -0)
M  SkunkOps/atlas/system.md                 (+5, -0)
```

---

## [2026-07-22] ASPICE Test Infrastructure

**Codebase Version:** v2.7-aspice-tests  
**Trigger:** User request for ASPICE-aligned test structure with emulator automation

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| 05_tests/ structure | ✅ Reorganized | ASPICE-aligned level folders (L1-L4) |
| infra/scripts/*.sh | ✅ Created | 6 automation scripts |
| Test_Strategy.md | ✅ Updated | ASPICE alignment section added |
| test_strategy_execution_instructions.md | ✅ Updated | Full rewrite with ASPICE naming |
| system.md (Atlas) | ✅ Updated | Testing Structure section updated |
| reports/README.md | ✅ Updated | Single-file report format |

### New Structure

```
05_tests/
├── L1_SWE4_unit/           # SWE.4 — Unit Verification
├── L2_SWE5_integration/    # SWE.5 — Integration Test
├── L3_SWE6_qualification/  # SWE.6 — SW Qualification Test
├── L4_SYS5_acceptance/     # SYS.5 — System Qualification Test
├── data/                   # Test data files
├── infra/
│   ├── scripts/
│   │   ├── setup-emulator.sh
│   │   ├── start-emulator.sh
│   │   ├── stop-emulator.sh
│   │   ├── run-instrumented.sh
│   │   ├── run-all-tests.sh
│   │   └── generate-report.sh
│   └── config/
│       └── avd-config.ini
└── reports/
    └── TEST_REPORT_*.md    # Single-file consolidated reports
```

### Scripts Created

| Script | Purpose |
|--------|---------|
| `setup-emulator.sh` | Install emulator, system image (API 30), create AVD |
| `start-emulator.sh` | Start headless emulator, wait for boot |
| `stop-emulator.sh` | Graceful emulator shutdown |
| `run-instrumented.sh` | Run L2 tests with optional emulator auto-start |
| `run-all-tests.sh` | Master orchestrator (L1 + L2 + report) |
| `generate-report.sh` | Generate consolidated markdown report |

### Report Format

Single file per execution: `TEST_REPORT_YYYY-MM-DD_HH-MM-SS.md`

Contains:
- ASPICE-aligned summary table
- Results per level (or "NOT EXECUTED")
- Failed test details
- Environment information

### ASPICE Traceability

| Folder | ASPICE | V-Model Alignment |
|--------|--------|-------------------|
| L1_SWE4_unit | SWE.4 | Implementation → Unit Verification |
| L2_SWE5_integration | SWE.5 | Design → Integration Test |
| L3_SWE6_qualification | SWE.6 | Architecture → SW Qualification |
| L4_SYS5_acceptance | SYS.5 | Requirements → System Qualification |

---

## [2026-07-22] Smooth Timer Display (100ms UI Updates)

**Codebase Version:** v2.6-smooth-timer  
**Trigger:** User feedback — recording timer display jumps inconsistently

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| TelemetryForegroundService.kt | ✅ Updated | +100ms UI state runnable, separated from notification |
| TelemetryForegroundServiceTest.kt | ✅ Updated | +1 test (elapsedMsUpdatesAtHighFrequency) |
| EndToEndTest.kt | ✅ Fixed | Pre-existing compile errors resolved |
| components.md | ✅ Updated | +Runtime Intervals section |

### Changes Made

#### TelemetryForegroundService.kt
```diff
+ private const val UI_STATE_UPDATE_INTERVAL_MS = 100L // 10 Hz for smooth timer display

+ // Fast UI state updates (100ms) for smooth timer display
+ private val uiStateUpdateRunnable = object : Runnable {
+     override fun run() {
+         updateElapsedTime()
+         handler.postDelayed(this, UI_STATE_UPDATE_INTERVAL_MS)
+     }
+ }

+ // Slower notification updates (1000ms) to save battery
  private val notificationUpdateRunnable = object : Runnable {
      override fun run() {
          updateNotification()
-         updateElapsedTime()
          checkGpsSignalLost()
          handler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL_MS)
      }
  }
```

#### components.md
```diff
+ ### Runtime Intervals
+ 
+ | Runnable | Interval | Purpose |
+ |----------|----------|---------|
+ | `uiStateUpdateRunnable` | 100ms (10 Hz) | Smooth timer display in RecordingFragment |
+ | `notificationUpdateRunnable` | 1000ms (1 Hz) | Notification bar + GPS signal check |
+ | `gpsLockTimeoutRunnable` | 5000ms (once) | GPS lock timeout detection |
+ | `periodicFlushRunnable` | 30000ms | Telemetry file flush for crash resilience |
+ 
+ **Design Note**: UI state updates are separated from notification updates...
```

### New Tests

| ID | Test | Purpose |
|----|------|---------|
| elapsedMsUpdatesAtHighFrequency | Verify ≥3 distinct elapsed samples in 500ms | Confirms 10Hz update rate |

### Bug Fixes

- **EndToEndTest.kt**: Fixed `rawFilePath = null` → `rawFilePath = ""` (non-null field)
- **EndToEndTest.kt**: Fixed `ProcessingStatus.NOT_STARTED` → `ProcessingStatus.PENDING`

### Files Modified

```
M  app/src/main/java/.../service/TelemetryForegroundService.kt  (+17, -2)
M  app/src/androidTest/java/.../service/TelemetryForegroundServiceTest.kt  (+53)
M  app/src/androidTest/java/com/drivingcoach/EndToEndTest.kt  (+2, -2)
M  SkunkOps/atlas/components.md  (+12)
```

### Design Rationale

- **Why 100ms?** Matches GPS capture rate (10 Hz), provides visually smooth millisecond counter
- **Why separate runnables?** Notification system calls are heavier; 1Hz is sufficient for notification bar
- **Performance impact:** Negligible — TextView.setText() is sub-millisecond

---

## [2026-07-22] Crash Resilience Improvements

**Codebase Version:** v2.5-crash-resilience  
**Trigger:** RCA finding — system crash during emulator recording (incident #10)

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| TelemetryFileWriter.kt | ✅ Updated | +1 method (flush) |
| TelemetryForegroundService.kt | ✅ Updated | +periodic flush runnable (30s) |
| DrivingCoachApp.kt | ✅ Updated | +DeadSystemException handler |
| TelemetryFileWriterIntegrationTest.kt | ✅ Updated | +2 flush tests |
| components.md | ✅ Updated | +Data Persistence section |
| failure-patterns.md | ✅ Updated | +DeadSystemException pattern |

### Changes Made

#### TelemetryFileWriter.kt
```kotlin
+ /**
+  * Flushes buffered data to disk without closing the writer.
+  * Call periodically to minimize data loss on unexpected termination.
+  */
+ suspend fun flush() = withContext(Dispatchers.IO) { ... }
```

#### TelemetryForegroundService.kt
```kotlin
+ private const val TELEMETRY_FLUSH_INTERVAL_MS = 30000L // 30s periodic flush
+ 
+ private val periodicFlushRunnable = object : Runnable {
+     override fun run() {
+         serviceScope.launch(Dispatchers.IO) { telemetryWriter?.flush() }
+         handler.postDelayed(this, TELEMETRY_FLUSH_INTERVAL_MS)
+     }
+ }
```

#### DrivingCoachApp.kt
```kotlin
+ private fun setupUncaughtExceptionHandler() {
+     Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
+         if (isSystemDeathException(throwable)) {
+             Log.e(TAG, "System crash detected (DeadSystemException)...")
+         } else {
+             defaultHandler?.uncaughtException(thread, throwable)
+         }
+     }
+ }
```

### New Tests

| Test | Purpose |
|------|---------|
| `periodicFlushPersistsDataBeforeClose` | Verifies flush persists data before close |
| `unflushedDataMayBeLostOnCrash` | Demonstrates why periodic flush matters |

### Atlas Updates

**components.md** — Added Data Persistence section to Telemetry Recording Service:
```markdown
+ ### Data Persistence
+ | Mechanism | Interval | Purpose |
+ |-----------|----------|---------|
+ | Sample write | On GPS fix (~100ms) | Append to buffer |
+ | Periodic flush | 30 seconds | Persist buffered data |
+ | Close flush | On stop | Final flush |
```

**failure-patterns.md** — Added DeadSystemException pattern:
- Symptoms, signals, causes, mitigation
- Links to RCA `10_RCA_emulator_system_server_crash.md`
- Documents app-level handling (graceful termination)

### Files Modified

```
M  app/src/main/java/.../data/telemetry/TelemetryFileWriter.kt     (+15)
M  app/src/main/java/.../service/TelemetryForegroundService.kt     (+14)
M  app/src/main/java/.../DrivingCoachApp.kt                        (+45)
M  app/src/test/java/.../TelemetryFileWriterIntegrationTest.kt     (+75)
M  SkunkOps/atlas/components.md                                     (+12)
M  SkunkOps/atlas/failure-patterns.md                               (+85)
```

### Related Incident

- `03_incidents/10_emulator_system_crash/10_RCA_emulator_system_server_crash.md`

---

## [2026-07-20] Session Management (Delete & Rename)

**Codebase Version:** v2.4-session-management  
**Trigger:** New feature — long-press context menu for session delete and rename

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| SessionDao.kt | ✅ Updated | +2 DAO methods (deleteById, updateTrackName) |
| HomeViewModel.kt | ✅ Updated | +2 methods, +2 events |
| HomeFragment.kt | ✅ Updated | +context menu, +dialogs |
| HomeViewModelTest.kt | ✅ Created | 6 new unit tests |
| components.md | ✅ Updated | +Session Management tables |
| SRS_v1.md | ✅ Updated | +10 requirements (SM-01 to SM-10) |
| USER_MANUAL.md | ✅ Updated | +Section 5.4 Managing Sessions |
| build.gradle.kts | ✅ Updated | +testOptions.returnDefaultValues |

### New Requirement IDs

| ID | Description |
|----|-------------|
| SM-01 | Delete via long-press context menu |
| SM-02 | CASCADE delete (session + laps + insights) |
| SM-03 | Delete telemetry JSONL file |
| SM-04 | Delete confirmation dialog |
| SM-05 | Rename via long-press context menu |
| SM-06 | Rename dialog pre-fills current name |
| SM-07 | Track name validation (1-100 chars) |
| SM-08 | Local-only operations |
| SM-09 | Delete success Snackbar |
| SM-10 | Rename success Snackbar |

### New DAO Methods

```kotlin
@Query("DELETE FROM sessions WHERE id = :sessionId")
suspend fun deleteById(sessionId: Long)

@Query("UPDATE sessions SET trackName = :trackName WHERE id = :sessionId")
suspend fun updateTrackName(sessionId: Long, trackName: String)
```

### New ViewModel Methods

```kotlin
fun deleteSession(sessionId: Long)  // Room delete + file delete + emit event
fun renameSession(sessionId: Long, newName: String)  // Validate + Room update + emit event
```

### New Events

```kotlin
data class ShowSessionDeleted(val trackName: String) : HomeEvent()
data class ShowSessionRenamed(val newName: String) : HomeEvent()
```

### New Unit Tests

| Test | Assertion |
|------|-----------|
| `deleteSession calls DAO deleteById` | verify(sessionDao).deleteById(42L) |
| `deleteSession handles missing session gracefully` | No crash when session null |
| `renameSession calls DAO updateTrackName with trimmed name` | verify(..., "New Track Name") |
| `renameSession rejects empty name` | verify(never()).updateTrackName() |
| `renameSession rejects name longer than 100 chars` | verify(never()).updateTrackName() |
| `renameSession accepts name with exactly 100 chars` | verify().updateTrackName() |

### Files Modified

```
M  app/src/main/java/.../data/db/dao/SessionDao.kt         (+6)
M  app/src/main/java/.../ui/home/HomeViewModel.kt          (+48)
M  app/src/main/java/.../ui/home/HomeFragment.kt           (+55)
A  app/src/test/java/.../ui/home/HomeViewModelTest.kt      (+165)
M  app/build.gradle.kts                                     (+5)
M  SkunkOps/atlas/components.md                            (+18)
M  01_requirements/DrivingCoach_SRS_v1.md                  (+15)
M  docs/USER_MANUAL.md                                     (+24)
```

---

## [2026-07-16] Track Name Navigation Fix

**Codebase Version:** v2.3-trackname-fix  
**Trigger:** RCA #08 track name lost in navigation

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| nav_graph.xml | ✅ Updated | +trackName args to 3 destinations |
| HomeFragment.kt | ✅ Updated | Pass trackName in navigation |
| TrackSetupFragment.kt | ✅ Updated | Receive + forward trackName |
| RecordingFragment.kt | ✅ Updated | Use args.trackName |
| 08_RCA_track_name_lost_in_navigation.md | ✅ Updated | Resolution status |

### Bug Fixed

| Before | After |
|--------|-------|
| All sessions named "Track Session" | Sessions use user-entered track name |

### Files Modified

```
M  app/src/main/res/navigation/nav_graph.xml         (+12)
M  app/src/main/java/.../home/HomeFragment.kt        (+1, -1)
M  app/src/main/java/.../tracksetup/TrackSetupFragment.kt (+4, -2)
M  app/src/main/java/.../recording/RecordingFragment.kt (+1, -1)
M  03_incidents/08_.../08_RCA_track_name_lost_in_navigation.md (+25)
```

---

## [2026-07-16] Coaching Bug Fix + Top Speed + Session Visibility

**Codebase Version:** v2.2-session-fix  
**Trigger:** RCA #06 invalid sector coaching + session visibility bug

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| SRS_v1.md | ✅ Updated | +10 requirements (OC-01 to OC-10), DP-01 clarified |
| components.md | ✅ Updated | Offline Coaching Engine fully rewritten |
| 06_RCA_invalid_sector_coaching.md | ✅ Updated | Resolution status added |

### New Requirement IDs

| ID | Description |
|----|-------------|
| OC-01 | Offline coaching generates local insights after lap detection |
| OC-02 | Produce 3-4 insights: Best Lap, Top Speed, Consistency, Sector Focus |
| OC-03 | Best Lap shows time delta vs average |
| OC-04 | Suppress sector detail when sector*Ms = 0 |
| OC-05 | Top Speed reads telemetry JSONL |
| OC-06 | GPS noise filter: reject >350 km/h |
| OC-07 | Consistency says "within" not "vary by" |
| OC-08 | Sector Focus shows "Coming Soon" upsell |
| OC-09 | Local insights stored with source="LOCAL" |
| OC-10 | Backend insights replace local insights |

### Bug Fixes

| Bug | Root Cause | Fix |
|-----|------------|-----|
| "0ms quicker in Sector 1" | No guard for zero sectors | `areSectorsAvailable()` check |
| Sessions not appearing | userId mismatch (`demo_user` vs `default_user`) | Removed userId filter |

### Files Modified

```
M  01_requirements/DrivingCoach_SRS_v1.md           (+18)
M  SkunkOps/atlas/components.md                    (+25, -15)
M  03_incidents/06_coaching_incidents/06_RCA_invalid_sector_coaching.md (+28)
```

---

## [2026-07-15] Real Speed Chart

**Codebase Version:** v1.9-real-speed-chart  
**Trigger:** Real telemetry speed chart implementation (distance-based X-axis)

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| components.md | ✅ Updated | +55 lines (TelemetryChartProcessor) |
| flows.md | ✅ Updated | +85 lines (Chart Data Loading flow) |
| SRS_v1.md | ✅ Updated | +5 requirements (LC-11 to LC-15) |
| USER_MANUAL.md | ✅ Updated | Section 5.3 rewritten |

### New Requirement IDs

| ID | Description |
|----|-------------|
| LC-11 | X-axis = distance in meters (haversine) |
| LC-12 | Real telemetry data from JSONL |
| LC-13 | Warning for >10 laps |
| LC-14 | Fast/Detailed processing dialog |
| LC-15 | Fast mode = ~100 points/lap |

### Files Modified

```
M  SkunkOps/atlas/components.md         (+55)
M  SkunkOps/atlas/flows.md              (+85)
M  01_requirements/SRS_v1.md            (+7, -2)
M  docs/USER_MANUAL.md                  (+22, -6)
```

---

## [2026-07-13] Local Lap Detection

**Codebase Version:** v1.7-local-lap-detection  
**Trigger:** Offline lap detection feature + incident fixes

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| system.md | ✅ Updated | +LocalLapDetector component |
| components.md | ✅ Updated | +LocalLapDetector spec |
| flows.md | ✅ Updated | +Local Lap Detection flow |
| SRS_v1.md | ✅ Updated | +9 requirements (LD-20 to LD-28, TC-06a) |
| USER_MANUAL.md | ✅ Updated | Offline mode, troubleshooting |

### New Requirement IDs

| ID | Description |
|----|-------------|
| LD-20 | Local detection runs immediately after stop |
| LD-21 | Same algorithm as server |
| LD-22 | 20,000ms minimum lap time |
| LD-23 | 50m minimum distance (kart-compatible) |
| LD-24 | `isLocalOnly=true` flag |
| LD-25 | Server results overwrite local |
| LD-26 | "No laps detected" message |
| LD-27 | "📶 Offline" indicator |
| LD-28 | Start line from header or entity |
| TC-06a | Telemetry file header format |

### Related Incidents

- `03_incidents/02_lap_detection_not_triggering/`
- `03_incidents/03_200m_threshold_too_large/`
- `03_incidents/04_telemetry_header_missing/`
- `03_incidents/05_status_banner_blocks_data/`

### Files Modified

```
M  SkunkOps/atlas/system.md
M  SkunkOps/atlas/components.md
M  SkunkOps/atlas/flows.md
M  01_requirements/DrivingCoach_SRS_v1.md
M  docs/USER_MANUAL.md
```

---
