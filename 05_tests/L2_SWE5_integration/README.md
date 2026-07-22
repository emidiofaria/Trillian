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
│   └── TelemetryForegroundServiceTest.kt
└── EndToEndTest.kt       # Component integration tests
```

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
