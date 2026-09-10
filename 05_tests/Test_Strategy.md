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
| `generate-report.sh` | Generate the Markdown test report |
| `generate-html-report.py` | Generate the HTML test report (evidence + coverage) |
| `test_generate_html_report.py` | Unit tests for the HTML generator |
| `package-release.sh` | Package an APK with its test report and release notes |

See `05_tests/infra/README.md` for detailed setup and usage instructions.

---

## 7. Test Reports

Every run writes its own directory under `05_tests/reports/`, so a report is
always tied to the run that produced it and nothing is overwritten:

```
reports/
└── RUN_20260910_005338/
    ├── TEST_REPORT.md      Markdown, for reading in the terminal or a diff
    └── TEST_REPORT.html    Self-contained HTML, for handing to someone else
```

### The HTML report

`generate-html-report.py` produces one self-contained page — no JavaScript, no
external stylesheets, nothing fetched from a network — that answers a single
question for a reader who has never seen this project: *how is this software
tested, and how much of it is actually covered?*

Sections, in order:

| # | Section | What it shows |
|---|---------|---------------|
| 1 | Results by test level | Declared vs executed per ASPICE level |
| 2 | How we test | What each level can and cannot prove |
| 3 | Failures | Every failure in full |
| 4 | Every automated test | The complete inventory |
| 5 | Manual acceptance tests | L4, with a note that a human must run them |
| 6 | Requirements coverage | Every SRS requirement, expandable to the tests that claim it |
| 7 | Gaps and caveats | Switched-off classes, unbacked claims, uncovered requirements |

The order is evidence before interpretation. Failures come third because on a red
run nothing else matters and they must not sit below 245 requirement rows. The
test inventory and the manual checks come before coverage so that a claim is read
against tests the reader has already seen, rather than taken on trust. The
document ends on the gaps, and then on a reminder that a human still has to run
the L4 checks before the build is trusted on track.

Everything in it comes from machine-readable evidence. No prose is parsed
anywhere, because a report that guesses at a document's meaning can overstate
coverage without anyone noticing:

| Input | Supplies |
|-------|----------|
| JUnit XML | What ran, what passed, how long it took |
| Kotlin source (`@Test` counts) | How many tests are *declared* |
| `05_tests/coverage-map.tsv` | Which test claims which requirement |
| `05_tests/scope-map.tsv` | Which requirements V1 deliberately does not build |
| `01_requirements/DrivingCoach_SRS_v1.md` | The full requirement list — the denominator |
| `05_tests/L4_SYS5_acceptance/*.md` | The manual checks only a human can discharge |

**Why declared counts come from the source, not the XML.** Gradle reports a
class annotated `@Ignore` as a *single* skipped entry, whatever the number of
tests inside it. Counting the XML alone therefore hides them. Reading the source
is what makes the difference visible: at the time of writing, 29 L2 tests are
declared and never run.

### The coverage map

`05_tests/coverage-map.tsv` is the machine-readable claim ledger:

```
requirement<TAB>test<TAB>note
UI-01	SplashScreenTest	Brand introduction shows on cold start
SR-04	TelemetryForegroundServiceTest	Foreground notification
LD-04	GeoUtilsTest	Line intersection
AS-03	L4:ANA-02	Track map matches the real circuit
```

The level is deliberately *not* stored: the report derives it from which results
file the class appears in, so the file cannot claim the wrong level. Each claim
is resolved against the actual run:

| Mark | Meaning |
|------|---------|
| `PASS` | The cited test ran and passed |
| `FAILED` | The cited test ran and failed |
| `SKIPPED` | The test exists but did not run — the claim is not backed by evidence |
| `MISSING` | The cited test was not found — the claim has rotted |
| `MANUAL` | An L4 claim; only a human at a circuit can discharge it |

`TRACEABILITY_MATRIX.md` remains the human narrative. The TSV is what the report
believes; drift between the two is detected loudly rather than prevented.

### The scope map

`05_tests/scope-map.tsv` records what V1 deliberately does not build. Without it
the report says *"177 requirements have no automated test"* and lists them flat,
putting `BE-01` — a route on a server V1 never deploys — next to `LD-02`, which
ships on phones today and is genuinely untested. A reader cannot tell a scope
decision from neglect, so they assume the worst about everything.

```
scope <TAB> target <TAB> reason
V2-BACKEND	@User management	No authentication provider in V1 (SRS AD-04)
V1	UM-18	The Profile screen ships in V1, reading counts from Room
V2-BACKEND	AI-02	Server-side Anthropic API call
```

`target` is either an SRS section (`@Backend API`) or a single requirement ID.
A requirement line always wins over a section line, so an exception stays
visible on its own line instead of being buried in a sweep.

**The rule for deciding is the subject of the sentence.** *"The backend shall…"*
is deferred; *"The app shall…"* is not, even when it mentions upload — the
client half of a network feature is testable against a fake server, and
TU-04, TU-06 and TU-07 already pass that way. Marking them deferred would hide
work that is done.

The report then prints **both** denominators, never one: 68 of 190 V1
requirements, and 68 of 245 counting the 55 deferred. Either figure alone
misleads — the first flatters V1 by ignoring what was never built, the second
punishes it for a deliberate decision.

Three guards stop the file becoming a place to hide inconvenient requirements:

| Guard | Behaviour |
|-------|-----------|
| Contradiction | A deferred requirement with a *passing* test is an error: either the note is stale or it was built after all |
| Unknown target | A section or ID that is not in the SRS is an error |
| No silent removal | Deferred requirements keep their row, their reason and their own count in section 4 |

A deferred requirement may still carry an L4 manual claim; only a passing
automated test is contradictory.

### Generating reports

```bash
# Run tests; both reports are written into a fresh RUN_ directory
./05_tests/infra/scripts/run-all-tests.sh

# Regenerate the HTML from results already on disk
python3 05_tests/infra/scripts/generate-html-report.py --output /tmp/report.html

# Two runs of the generator produce byte-identical output
python3 05_tests/infra/scripts/generate-html-report.py --source-date "2026-01-01"
```

### Releases

After a clean run, the orchestrator offers to package a local release
(suppress with `--no-prompt`). The result groups the build with the evidence
for it, so the two cannot be separated:

```
releases/v2.95-session-analysis/
├── DrivingCoach-v2.95-session-analysis.apk
├── TEST_REPORT.html      the tests that were run against this APK
├── TEST_REPORT.md
└── RELEASE_NOTES.md      the commits since the previous release
```

```bash
./05_tests/infra/scripts/package-release.sh --slug session-analysis
```

If no test report can be found, the release still gets made but a
`TEST_REPORT_MISSING.txt` is written into the directory saying so. An APK with
no evidence beside it is an APK nobody has checked, and that fact is recorded
rather than left to be assumed.

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
