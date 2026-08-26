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
├── service/
│   └── TelemetryForegroundServiceTest.kt      (@Ignore)
├── testing/                                    # Shared L2 fixtures
│   ├── FakePreferencesDataStores.kt            # Stalling / seeded DataStore fakes
│   ├── NavigationTestExtensions.kt             # awaitUntil, currentDestinationId
│   └── MainThreadResponsivenessProbe.kt        # Main-looper latency sampler
├── ui/
│   ├── splash/
│   │   ├── SplashScreenTest.kt                 # UI-01, UI-02
│   │   ├── SplashFallbackTest.kt               # UI-03
│   │   └── SplashMainThreadTest.kt             # UI-04
│   └── home/
│       └── HomeHeroTest.kt                     # UI-06
├── StartupBackStackTest.kt                     # UI-05
└── EndToEndTest.kt                             (@Ignore)
```

---

## ⚠️ `BUILD SUCCESSFUL` Is Not Evidence

Gradle reports success for a suite in which **every class is `@Ignore`d**. Four legacy
classes (`EndToEndTest`, `RecordingFragmentTest`, `TrackSetupFragmentTest`,
`TelemetryForegroundServiceTest`) are currently in that state, so the exit code alone proves
nothing.

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
