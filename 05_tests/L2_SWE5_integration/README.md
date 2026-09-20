# L2_SWE5_integration — Integration Test

**ASPICE Process:** SWE.5 — Software Integration and Integration Test  
**Test Level:** Integration/Instrumented Tests  
**Execution Environment:** Android Device or Emulator

---

## Purpose

Verify integration between software components using real Android APIs, Room database, and system services.

---

## Test Location

**Actual test files are located at:**

```
app/src/androidTest/java/com/drivingcoach/
├── data/
│   └── local/
│       └── dao/          # Room DAO tests
├── testing/                                    # Shared L2 fixtures
│   ├── FakePreferencesDataStores.kt            # Stalling / seeded DataStore fakes
│   ├── NavigationTestExtensions.kt             # awaitUntil, currentDestinationId
│   ├── ScriptedLocationUpdates.kt              # Deterministic LocationUpdates fake
│   └── MainThreadResponsivenessProbe.kt        # Main-looper latency sampler
├── ui/
│   ├── splash/
│   │   ├── SplashScreenTest.kt                 # UI-01, UI-02, UI-10
│   │   ├── SplashIntroductionTest.kt           # UI-02 (first-run hold + returning user)
│   │   ├── SplashFallbackTest.kt               # UI-03
│   │   └── SplashMainThreadTest.kt             # UI-04
│   ├── about/
│   │   └── AboutScreenTest.kt                  # UI-11, UI-12
│   ├── tracksetup/
│   │   └── TrackSetupResubscribeTest.kt        # TS-15 (resubscribe regression guard)
│   ├── home/
│   │   ├── HomeHeroTest.kt                     # UI-06
│   │   └── HomeGpsChipTest.kt                  # TS-16, TS-17, TS-18
│   └── session/
│       └── SessionShareTest.kt                 # SH-07, SH-08, SH-10, SH-11
└── StartupBackStackTest.kt                     # UI-05
```

---

## ⚠️ Espresso Prerequisites (learned the hard way)

Two things will make an instrumented test **hang forever rather than fail** — the runner's
default per-test timeout is one year (`testTimeoutSeconds=31536000`):

1. **Never call `Intents.intending(anyIntent())` before the screen is launched.** It stubs
   the intent that starts `HiltTestActivity`, so the screen never appears. Call
   `Intents.init()` *after* launch and stub the narrowest matcher possible.
2. **Never wrap `onView`/`intended` in a retry loop.** They block internally until the UI
   thread is idle, so the wrapper's timeout never gets a chance to fire.

Animations must be off. `05_tests/infra/scripts/start-emulator.sh` now sets
`window_animation_scale`, `transition_animation_scale` and `animator_duration_scale` to `0`;
without this, any screen with an indeterminate `ProgressBar` deadlocks the suite.

See `atlas/failure-patterns.md` → **FP-TEST-HANG** and **FP-TEST-WORKMANAGER**.

---

## Location Is Injected, Not Real

`LocationUpdates` is a Hilt binding, so L2 classes that need GPS behaviour
`@UninstallModules(LocationModule::class)` and provide `ScriptedLocationUpdates` instead.
That fake emits real `android.location.Location` objects on demand and counts
`subscribeCount` / `activeSubscriptions`.

Those two counters are the whole point of `TrackSetupResubscribeTest`: a subscription that is
torn down on stop and never restored is invisible to any assertion about *content*, and only
shows up as a count that fails to increase. Never replace those assertions with "is the text
still there" checks — a permanently frozen screen passes those.

Anything that overrides `LocationModule` must also provide `WarmUpTimings` and the
`@ApplicationScope CoroutineScope`, since they live in the same module.

---

## ⚠️ `BUILD SUCCESSFUL` Is Not Evidence

Gradle reports success for a suite in which **every class is `@Ignore`d**. Four legacy
classes (`EndToEndTest`, `RecordingFragmentTest`, `TrackSetupFragmentTest`,
`TelemetryForegroundServiceTest`) were in exactly that state until 2026-09-16, when they were
deleted rather than revived. No `@Ignore` remains in `app/src` today, but the warning stands:
the exit code alone still proves nothing, because the next skipped class will be just as
invisible.

Always parse the JUnit XML after a run:

```bash
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
for f in glob.glob('app/build/outputs/androidTest-results/connected/**/*.xml', recursive=True):
    r = ET.parse(f).getroot()
    print(r.get('name'), 'tests=', r.get('tests'), 'failures=', r.get('failures'), 'skipped=', r.get('skipped'))
EOF
```

A class reported as `tests=1 skipped=1 name="null"` executed nothing.

---

## ⚠️ New `SplashTimings` Fields Propagate Silently

`SplashTimings` is constructed with **named arguments** in every L2 module that overrides
`SplashModule`. A field with a default therefore reaches all of them without any compile
error — and if that default is a *duration*, the result is a slower suite rather than a
failing one, which is exactly the kind of regression that gets ignored.

`introDisplayMs` (4000 ms) is the current example. Every module below pins it explicitly:

```
SplashScreenTest · SplashIntroductionTest · SplashReturningUserTest
SplashFallbackTest · SplashMainThreadTest · StartupBackStackTest · HomeHeroTest
```

`visibilityTimeoutMs` (2000 ms) is the newer one, and it behaves differently: it is only
*reached* when the platform splash never reports its exit. Under `ActivityScenario` the
report does arrive, so it normally costs nothing — but if it ever stops arriving, every
splash test pays 2 s and the suite gets slower rather than red. `W/SplashViewModel: handoff
never reported` in logcat is the direct confirmation.

**Any new L2 class that overrides `SplashModule` must pin `introDisplayMs` too.** To check for a leak,
compare per-test durations against the recorded baseline rather than trusting the total:

```bash
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
rows = []
for p in glob.glob('app/build/outputs/androidTest-results/connected/**/*.xml', recursive=True):
    for tc in ET.parse(p).getroot().iter('testcase'):
        rows.append((float(tc.get('time')), tc.get('classname').split('.')[-1], tc.get('name')))
for t, c, n in sorted(rows, reverse=True)[:10]:
    print(f"{t:7.2f}  {c} > {n}")
EOF
```

Baseline (API 30 emulator, 30 executed tests): total ~114 s, slowest test ~7.4 s. A class
that suddenly costs 4 s per test has inherited the introduction budget.

---

## Test Isolation Strategy

Startup dependencies are provided by narrow Hilt modules so a test can replace exactly one:

| Module | Provides | Replaced to |
|--------|----------|-------------|
| `SplashModule` | `SplashTimings` | Pin the loading screen, or force a short timeout |
| `DataStoreModule` | Preferences `DataStore` | Stall startup, or seed an onboarded user |
| `DispatcherModule` | `@IoDispatcher` | (available; not currently overridden) |

Each `@HiltAndroidTest` class declares its own nested `@Module`, so per-class values do not
collide. This is why `SplashFallbackTest` (short timeout) and `SplashMainThreadTest` (long
timeout) must be separate classes.

---

## How to Run

### Prerequisites
- Connected Android device OR running emulator
- USB debugging enabled (for physical device)

### Commands

```bash
# Ensure device/emulator is available
adb devices

# Run integration tests
./gradlew connectedDebugAndroidTest

# Or via infrastructure scripts
./05_tests/infra/scripts/run-instrumented.sh

# Or as part of full test run
./05_tests/infra/scripts/run-all-tests.sh --level L2
```

### Emulator Setup (if no device)

```bash
# One-time setup
./05_tests/infra/scripts/setup-emulator.sh

# Start emulator
./05_tests/infra/scripts/start-emulator.sh

# Run tests
./gradlew connectedDebugAndroidTest

# Stop emulator
./05_tests/infra/scripts/stop-emulator.sh
```

---

## What Gets Tested

| Component | Purpose |
|-----------|---------|
| Branded startup (`SplashFragment` + `SplashViewModel` + NavGraph) | SRS UI-01…UI-05 |
| Home brand hero (`AppBarLayout` collapse) | SRS UI-06 |
| Room DAOs | Database operations, queries |
| Services | TelemetryForegroundService, notifications |
| Content Providers | Data sharing between components |
| System Integration | GPS, sensors, WorkManager |

---

## Report Output

Results are included in the consolidated test report at:
```
05_tests/reports/TEST_REPORT_[timestamp].md
```

---

## ASPICE Traceability

| ASPICE Requirement | Implementation |
|--------------------|----------------|
| SWE.5.BP1 | Integration strategy → Component test design |
| SWE.5.BP2 | Integration test execution → `connectedDebugAndroidTest` |
| SWE.5.BP3 | Results analysis → Test report L2 section |
