# Driving Coach — Test Strategy

> **Version:** 2.0  
> **Last Updated:** 2026-07-22  
> **Status:** Active

This document describes the test structure, test levels, and execution procedures for the Driving Coach Android application.

**ASPICE Alignment:** Test levels follow Automotive SPICE (ASPICE) process framework.

---

## Table of Contents

1. [ASPICE Test Level Alignment](#1-aspice-test-level-alignment)
2. [Test Levels Overview](#2-test-levels-overview)
3. [Directory Structure](#3-directory-structure)
4. [Running Tests](#4-running-tests)
5. [Test Data](#5-test-data)
6. [Test Infrastructure](#6-test-infrastructure)
7. [Test Reports](#7-test-reports)
8. [Traceability](#8-traceability)

---

## 1. ASPICE Test Level Alignment

| Folder | ASPICE Process | Description | Execution |
|--------|----------------|-------------|-----------|
| `L1_SWE4_unit` | SWE.4 | Unit Verification | JVM (local machine) |
| `L2_SWE5_integration` | SWE.5 | Integration Test | Device/Emulator |
| `L3_SWE6_qualification` | SWE.6 | SW Qualification Test | Emulator + test data |
| `L4_SYS5_acceptance` | SYS.5 | System Qualification Test | Human at track |

### V-Model Alignment

```
Requirements (SRS)  ←────────────────────→  L4_SYS5_acceptance (SYS.5)
        │                                           ↑
        ▼                                           │
Architecture        ←────────────────────→  L3_SWE6_qualification (SWE.6)
        │                                           ↑
        ▼                                           │
Detailed Design     ←────────────────────→  L2_SWE5_integration (SWE.5)
        │                                           ↑
        ▼                                           │
Implementation      ←────────────────────→  L1_SWE4_unit (SWE.4)
```

---

## 2. Test Levels Overview

| Level | Folder | ASPICE | Location | Runner | Purpose |
|-------|--------|--------|----------|--------|---------|
| **L1** | `L1_SWE4_unit` | SWE.4 | `app/src/test/` | JVM | Fast, isolated logic tests |
| **L2** | `L2_SWE5_integration` | SWE.5 | `app/src/androidTest/` | Device/Emulator | Service, Room DB, UI components |
| **L3** | `L3_SWE6_qualification` | SWE.6 | `app/src/androidTest/e2e/` | Emulator + Test Data | Full user flow with GPS/IMU replay |
| **L4** | `L4_SYS5_acceptance` | SYS.5 | `05_tests/L4_SYS5_acceptance/` | Manual | End-user acceptance validation |

### L1_SWE4_unit — Unit Verification

**Purpose:** Validate pure business logic without Android framework dependencies.

**What's tested:**
- `LocalLapDetector` — Lap crossing detection algorithm
- `OfflineCoachingEngine` — Coaching insight generation
- `GeoUtils` — Geographic calculations
- `TelemetryFileWriter` — JSONL file formatting
- Repository and ViewModel logic (with mocks)

**Characteristics:**
- Run on JVM (no device needed)
- Fast execution (~10 seconds for full suite)
- No external dependencies

### L2_SWE5_integration — Integration Test

**Purpose:** Validate Android-specific components that require the framework.

**What's tested:**
- `TelemetryForegroundService` — Service lifecycle, state emissions
- Room database operations
- Fragment navigation
- UI component rendering

**Characteristics:**
- Requires device or emulator
- Slower execution (~2-5 minutes)
- Tests real Android behavior

### L3_SWE6_qualification — SW Qualification Test

**Purpose:** Validate complete user flows with simulated GPS/IMU data.

**What's tested:**
- Full recording session with replayed telemetry
- Lap detection from simulated track data
- Upload and processing flow
- Error recovery scenarios

**Characteristics:**
- Requires emulator with GPS simulation
- Uses test data from `05_tests/data/`
- Longest execution time (~10-15 minutes)

### L4_SYS5_acceptance — System Qualification Test

**Purpose:** Manual validation of user-facing behavior before release.

**What's tested:**
- Complete user journeys
- Edge cases difficult to automate
- Visual/UX verification
- Real-world track testing

**Characteristics:**
- Manual execution by human tester
- Checklist-based verification
- Requires physical device for GPS tests

---

## 3. Directory Structure

```
Trillian/
├── 01_requirements/
│   ├── DrivingCoach_SRS_v1.md        # Requirements specification
│   └── TRACEABILITY_MATRIX.md        # Req-to-test mapping
│
├── 05_tests/
│   ├── Test_Strategy.md              # This document
│   ├── test_strategy_execution_instructions.md  # Agent execution guide
│   ├── L1_SWE4_unit/                 # SWE.4 reference folder
│   │   └── README.md                 # → app/src/test/
│   ├── L2_SWE5_integration/          # SWE.5 reference folder
│   │   └── README.md                 # → app/src/androidTest/
│   ├── L3_SWE6_qualification/        # SWE.6 reference folder
│   │   └── README.md                 # → E2E tests (future)
│   ├── L4_SYS5_acceptance/           # SYS.5 human acceptance tests
│   │   └── *.md                      # Test checklists
│   ├── data/                         # Test data files
│   │   ├── tracks/                   # Real track recordings
│   │   └── edge-cases/               # Edge case scenarios
│   ├── infra/                        # Test infrastructure
│   │   ├── scripts/                  # Automation scripts
│   │   ├── config/                   # AVD configuration
│   │   └── README.md                 # Setup instructions
│   └── reports/                      # Test execution reports
│       └── TEST_REPORT_*.md          # Consolidated reports
│
├── app/src/
│   ├── test/                         # L1: Unit tests (actual code)
│   │   └── java/com/drivingcoach/
│   └── androidTest/                  # L2 & L3: Instrumented + E2E
│       └── java/com/drivingcoach/
│           ├── service/              # Service tests
│           ├── ui/                   # UI tests
│           └── e2e/                  # E2E tests
```

---

## 4. Running Tests

### Recommended: Use Master Orchestrator

```bash
# Run all automated tests and generate report
./05_tests/infra/scripts/run-all-tests.sh

# With emulator auto-start for L2
./05_tests/infra/scripts/run-all-tests.sh --start-emulator --stop-emulator

# Run specific level
./05_tests/infra/scripts/run-all-tests.sh --level L1
./05_tests/infra/scripts/run-all-tests.sh --level L2 --start-emulator
```

### L1_SWE4_unit — Unit Tests

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run specific test class
./gradlew testDebugUnitTest --tests "com.drivingcoach.lap.LocalLapDetectorTest"

# Run with coverage report
./gradlew testDebugUnitTestCoverage
```

**Output:** `app/build/reports/tests/testDebugUnitTest/index.html`

### L2_SWE5_integration — Integration Tests

```bash
# Ensure device/emulator is connected
adb devices

# Run all instrumented tests
./gradlew connectedDebugAndroidTest

# Run specific test class
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.drivingcoach.service.TelemetryForegroundServiceTest
```

**Output:** `app/build/reports/androidTests/connected/index.html`

### L3_SWE6_qualification — E2E Tests

```bash
# Setup and start emulator
./05_tests/infra/scripts/setup-emulator.sh
./05_tests/infra/scripts/start-emulator.sh

# Run E2E qualification tests (future)
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.drivingcoach.e2e.*
```

### L4_SYS5_acceptance — Human Acceptance Tests

1. Open checklist from `05_tests/L4_SYS5_acceptance/`
2. Execute each test case manually at track
3. Record pass/fail status
4. Submit completed checklist for sign-off

---

## 5. Test Data

Test data is stored in `05_tests/data/` and used by E2E tests.

### Data Formats

| Format | Extension | Purpose |
|--------|-----------|---------|
| JSONL | `.jsonl` | Telemetry samples (GPS + IMU) |
| GPX | `.gpx` | GPS track for emulator replay |

### Available Test Data

| File | Description | Laps | Duration |
|------|-------------|------|----------|
| `tracks/spa_2laps.jsonl` | Spa circuit, clean run | 2 | 4 min |
| `tracks/nurburgring_5laps.jsonl` | Nordschleife, varied pace | 5 | 45 min |
| `edge-cases/gps_dropout.jsonl` | 10-second GPS signal loss | 2 | 5 min |
| `edge-cases/short_session.jsonl` | Only 1 lap (edge case) | 1 | 2 min |

See `05_tests/data/README.md` for data format specification.

---

## 6. Test Infrastructure

### Emulator Configuration

Configuration in `05_tests/infra/config/`:

| Setting | Value |
|---------|-------|
| AVD Name | `DrivingCoach_Test` |
| API Level | 30 (Android 11) |
| Device | Pixel 4 |
| ABI | x86_64 |
| GPS | Enabled (mock location) |

### Scripts

| Script | Purpose |
|--------|---------|
| `setup-emulator.sh` | Install emulator, system image, create AVD |
| `start-emulator.sh` | Start headless emulator, wait for boot |
| `stop-emulator.sh` | Graceful emulator shutdown |
| `run-instrumented.sh` | Run L2 integration tests |
| `run-all-tests.sh` | Master orchestrator (L1 + L2 + report) |
| `generate-report.sh` | Generate consolidated test report |

See `05_tests/infra/README.md` for detailed setup and usage instructions.

---

## 7. Test Reports

Test execution reports are stored in `05_tests/reports/`.

### Report Format

A single consolidated report is generated per test execution:

```
reports/
├── TEST_REPORT_2026-07-22_18-30-00.md
├── TEST_REPORT_2026-07-21_14-15-00.md
└── ...
```

Each report contains:
- Summary table with all ASPICE test levels
- Pass/fail counts per level
- Failed test details
- "NOT EXECUTED" for levels not run
- Environment information

### Generating Reports

```bash
# Run tests and generate report automatically
./05_tests/infra/scripts/run-all-tests.sh

# Or generate report manually after tests
./05_tests/infra/scripts/generate-report.sh
```

---

## 8. Traceability

The **Traceability Matrix** maps requirements (from SRS) to test cases.

**Location:** `01_requirements/TRACEABILITY_MATRIX.md`

### Coverage Goals

| Test Level | ASPICE | Target Coverage |
|------------|--------|-----------------|
| L1_SWE4_unit | SWE.4 | 80%+ code coverage on business logic |
| L2_SWE5_integration | SWE.5 | All critical Android components |
| L3_SWE6_qualification | SWE.6 | Happy path + top 5 error scenarios |
| L4_SYS5_acceptance | SYS.5 | 100% of SRS requirements |

---

## Quick Reference

| Action | Command |
|--------|---------|
| Run all tests + report | `./05_tests/infra/scripts/run-all-tests.sh` |
| Run L1 unit tests | `./05_tests/infra/scripts/run-all-tests.sh --level L1` |
| Run L2 with emulator | `./05_tests/infra/scripts/run-all-tests.sh --level L2 --start-emulator` |
| Setup emulator | `./05_tests/infra/scripts/setup-emulator.sh` |
| Start emulator | `./05_tests/infra/scripts/start-emulator.sh` |
| Stop emulator | `./05_tests/infra/scripts/stop-emulator.sh` |
| Generate report only | `./05_tests/infra/scripts/generate-report.sh` |
| View latest report | `ls -t 05_tests/reports/TEST_REPORT_*.md | head -1` |

---

*Last updated: 2026-07-22 | Version 2.0 — ASPICE alignment*
