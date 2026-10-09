# Test Execution Instructions (Agent Reference)

> **Purpose:** Operational reference for automated agents executing tests  
> **Audience:** Copilot agents, CI scripts  
> **Last Updated:** 2026-07-22  
> **ASPICE Aligned:** Yes

---

## ASPICE Test Level Overview

| Level | Folder | ASPICE | Execution | Automation |
|-------|--------|--------|-----------|------------|
| L1 | `L1_SWE4_unit` | SWE.4 | JVM (always) | ✅ Full |
| L2 | `L2_SWE5_integration` | SWE.5 | Device/Emulator | ✅ With device |
| L3 | `L3_SWE6_qualification` | SWE.6 | Emulator + test data | ⚠️ Future |
| L4 | `L4_SYS5_acceptance` | SYS.5 | Human at track | ❌ Manual only |

---

## 1. Recommended: Use Infrastructure Scripts

The preferred method is to use the infrastructure scripts which handle environment detection, test execution, and report generation.

### 1.1 Run All Tests (Recommended)

```bash
cd /home/ctw00173_ubuntu/05_AI_DIY/Trillian

# Run L1 + L2 (if device available) + generate report
./05_tests/infra/scripts/run-all-tests.sh

# With emulator auto-start for L2
./05_tests/infra/scripts/run-all-tests.sh --start-emulator --stop-emulator
```

### 1.2 Run Specific Level

```bash
# L1 only (unit tests)
./05_tests/infra/scripts/run-all-tests.sh --level L1

# L2 only (integration tests, requires device)
./05_tests/infra/scripts/run-all-tests.sh --level L2 --start-emulator
```

### 1.3 Emulator Management

```bash
# One-time setup (installs emulator, system image, creates AVD)
./05_tests/infra/scripts/setup-emulator.sh

# Start emulator (headless)
./05_tests/infra/scripts/start-emulator.sh

# Stop emulator
./05_tests/infra/scripts/stop-emulator.sh
```

### 1.4 Report Generation

Each run of `run-all-tests.sh` writes both reports into its own directory:

```
05_tests/reports/RUN_<timestamp>/
├── TEST_REPORT.md      Markdown summary
└── TEST_REPORT.html    Full evidence and requirements coverage
```

To regenerate from results already on disk, without re-running the tests:

```bash
# Markdown
./05_tests/infra/scripts/generate-report.sh --output 05_tests/reports/

# HTML (deterministic; --source-date pins the timestamp for diffing)
python3 05_tests/infra/scripts/generate-html-report.py --output /tmp/report.html
```

The HTML report is the one to hand to somebody else: it states what ran, what
is declared but switched off, which requirements are claimed by which test, and
which are not covered at all. Read it before claiming a feature is tested.

Coverage is reported against **two** denominators — V1 requirements, and all
requirements including those deferred to V2 (`05_tests/scope-map.tsv`). Quote
both. Quoting only the V1 figure overstates completeness; quoting only the
overall figure penalises a scope decision that was made on purpose.

If the generator prints a scope problem, fix it rather than ignoring it:

```
[WARN] N problem(s) in the coverage or scope map
       OC-01 is marked V2-BACKEND but has a passing test ...
```

That message means a requirement is recorded as unbuilt while a test proves
otherwise. One of the two is wrong.

**Non-interactive runs:** pass `--no-prompt` so the release question is never
asked.

```bash
./05_tests/infra/scripts/run-all-tests.sh --level L1 --no-prompt
```

### 1.5 Packaging a Release

After a clean run the orchestrator offers to package a release. The same thing
can be done by hand:

```bash
./05_tests/infra/scripts/package-release.sh --slug session-analysis
```

This produces `releases/v<version>-<slug>/` containing the APK, the HTML test
report from the run, and `RELEASE_NOTES.md` generated from `git log`.

That is the `dev` shape, which is the default. `--target play` produces a signed
bundle instead, and `--target both` produces both artifacts in one directory;
both of those run the full Play preflight and will refuse to package against a
stale or missing report. See `docs/RELEASE.md`.

### 1.6 Self-tests for the report generator

The generator has its own tests. Run them after changing it:

```bash
python3 -m unittest discover -s 05_tests/infra/scripts -p 'test_*.py'
```

Expect 54 tests. Among them are guards on the header emblem: it must be square
(Incident 11 was this artwork deformed by a non-uniform scale), it must stay
under 16 KB so nobody drops the 528x528 master in and triples every report, and
it must be embedded as a `data:` URI rather than linked so the page still works
when copied into a release directory on its own.

---

## 2. Environment Detection (Manual Method)

If not using scripts, check environment before running tests.

### 2.1 Check Project Root

```bash
ls app/build.gradle.kts && echo "OK: Project root confirmed"
```

### 2.2 Check Android SDK

```bash
# Check SDK path
SDK_PATH=$(grep "sdk.dir" local.properties 2>/dev/null | cut -d'=' -f2)
[ -z "$SDK_PATH" ] && SDK_PATH="$HOME/android-sdk"
echo "SDK Path: $SDK_PATH"

# Verify ADB exists
ls "$SDK_PATH/platform-tools/adb" && echo "OK: ADB found"
```

### 2.3 Check Device/Emulator

```bash
$SDK_PATH/platform-tools/adb devices | grep -E "device$|emulator"
```

### 2.4 Decision Matrix

| SDK Found | Device Connected | Can Run |
|-----------|------------------|---------|
| ❌ No | — | L1 only |
| ✅ Yes | ❌ No | L1 only (or start emulator) |
| ✅ Yes | ✅ Yes | L1, L2, L3 |

---

## 3. L1_SWE4_unit — Unit Tests

**ASPICE:** SWE.4 — Software Unit Verification  
**Prerequisites:** None (runs on JVM)  
**Test Location:** `app/src/test/`

### 3.1 Run Command

```bash
cd /home/ctw00173_ubuntu/05_AI_DIY/Trillian
./gradlew testDebugUnitTest --quiet
```

**With verbose output:**
```bash
./gradlew testDebugUnitTest
```

**Run specific test class:**
```bash
./gradlew testDebugUnitTest --tests "com.drivingcoach.lap.LocalLapDetectorTest"
```

### 3.2 Verify Results

**Check exit code:**
- Exit code `0` = All tests passed
- Exit code `non-zero` = Tests failed

**Check test counts:**
```bash
grep -E 'tests=|failures=|errors=' app/build/test-results/testDebugUnitTest/*.xml
```

**Expected output (success):**
```
tests="N" skipped="0" failures="0" errors="0"
```

### 3.3 Report Location

```
app/build/reports/tests/testDebugUnitTest/index.html
app/build/test-results/testDebugUnitTest/*.xml
```

### 3.4 Common Issues

| Issue | Symptom | Fix |
|-------|---------|-----|
| Compilation error | Build fails before tests | Fix code first |
| Test timeout | Stuck on single test | Check for infinite loops |
| Missing dependencies | ClassNotFoundException | Run `./gradlew dependencies` |
| `Unresolved reference 'BufferedImage'` | Test uses `java.awt` / `javax.imageio` | Neither exists on the Android unit-test classpath. Use `com.drivingcoach.brand.ArgbBitmap` |

### 3.5 Brand Asset Gate

`com.drivingcoach.brand.BrandAssetGeometryTest` measures the helmet emblem
artwork on disk (UI-08, UI-09). Run it alone with:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.drivingcoach.brand.*'
```

It includes a **falsification test** that requires the gate to reject the known
deformed emblem from Incident 11. If you see:

```
gate accepted the known-deformed legacy emblem — the gate is not falsifiable
```

the assertions have been weakened into a no-op — fix the thresholds, do not
delete the test.

To regenerate the emblem density buckets or check artwork outside Gradle:

```bash
python3 -m venv /tmp/brandvenv && /tmp/brandvenv/bin/pip install pillow numpy
/tmp/brandvenv/bin/python 05_tests/infra/scripts/brand-asset.py check
/tmp/brandvenv/bin/python 05_tests/infra/scripts/brand-asset.py build docs/brand/helmet_source.png
```

`pillow` and `numpy` are deliberately **not** app build dependencies.

---

## 4. L2_SWE5_integration — Integration Tests

**ASPICE:** SWE.5 — Software Integration and Integration Test  
**Prerequisites:** Device or emulator connected via ADB  
**Test Location:** `app/src/androidTest/`

### 4.1 Pre-check

```bash
# Must have device connected (or use emulator scripts)
SDK_PATH=$(grep "sdk.dir" local.properties | cut -d'=' -f2)
[ -z "$SDK_PATH" ] && SDK_PATH="$HOME/android-sdk"
$SDK_PATH/platform-tools/adb devices | grep -E "device$|emulator"
```

**If no output:** Use emulator scripts or inform user:
```bash
# Start emulator automatically
./05_tests/infra/scripts/setup-emulator.sh
./05_tests/infra/scripts/start-emulator.sh
```

### Which AVD to use

Two exist. `AVD_NAME` selects one; the default is the API 30 device.

| AVD | API | Use for |
|-----|-----|---------|
| `Trillian_API36` | 36 (Android 16) | **Release evidence.** This is the level the app targets, so it is the only one that exercises edge-to-edge enforcement, predictive back, and the current foreground-service rules. |
| `DrivingCoach_Test` | 30 (Android 11) | The `minSdk` end of the supported range |

```bash
AVD_NAME=Trillian_API36 ./05_tests/infra/scripts/start-emulator.sh
```

⚠️ Per **NF-19**, L2 results supporting a Play release must come from the API 36
device. A green run on API 30 says nothing about behaviour the platform only
changes at the targeted level — see `FP-SILENT-NOOP-API` for a case where
exactly that gap hid a broken screen.

### 4.2 Run Command

```bash
cd /home/ctw00173_ubuntu/05_AI_DIY/Trillian

# Via infrastructure script (recommended)
./05_tests/infra/scripts/run-instrumented.sh --start-emulator

# Or directly via Gradle
./gradlew connectedDebugAndroidTest
```

**Run specific test class:**
```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.drivingcoach.ui.splash.SplashScreenTest
```

**Run specific test method:**
```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.drivingcoach.ui.splash.SplashScreenTest#brandIntroductionShowsOnColdStart
```

### 4.3 Verify Results

**Check exit code:**
- Exit code `0` = All tests passed
- Exit code `non-zero` = Tests failed

**Check test results:**
```bash
find app/build/outputs/androidTest-results -name "*.xml" -exec grep -l "failures" {} \;
```

### 4.4 Report Location

```
app/build/reports/androidTests/connected/index.html
app/build/outputs/androidTest-results/connected/*.xml
```

### 4.5 Common Issues

| Issue | Symptom | Fix |
|-------|---------|-----|
| No device | "No connected devices" | Use `./05_tests/infra/scripts/start-emulator.sh` |
| App not installed | InstallException | Run `./gradlew installDebug` first |
| Permission denied | SecurityException in test | Grant permissions on device |
| Device offline | "device offline" | Reconnect USB, restart ADB |

**Restart ADB if issues:**
```bash
$SDK_PATH/platform-tools/adb kill-server
$SDK_PATH/platform-tools/adb start-server
```

---

## 5. L3_SWE6_qualification — SW Qualification Tests

**ASPICE:** SWE.6 — Software Qualification Test  
**Prerequisites:** 
- Emulator connected
- Test data available in `05_tests/data/`
- GPS mock location enabled

**Status:** ⚠️ Not yet implemented (future)

### 5.1 Pre-check

```bash
# Check emulator is running
SDK_PATH=$(grep "sdk.dir" local.properties | cut -d'=' -f2)
[ -z "$SDK_PATH" ] && SDK_PATH="$HOME/android-sdk"
$SDK_PATH/platform-tools/adb devices | grep "emulator"

# Check test data exists
ls 05_tests/data/tracks/*.jsonl 2>/dev/null || echo "No track test data found"
```

### 5.2 Run Command (Future)

```bash
cd /home/ctw00173_ubuntu/05_AI_DIY/Trillian

# Run all E2E qualification tests (when implemented)
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.drivingcoach.e2e
```

### 5.3 GPS Data Loading

**Option A: Via ADB geo command**
```bash
# Set mock location (longitude first, then latitude)
$SDK_PATH/platform-tools/adb emu geo fix <longitude> <latitude>

# Example: Munich coordinates
$SDK_PATH/platform-tools/adb emu geo fix 11.582 48.135
```

**Option B: Via GPX file in emulator**
1. Convert JSONL to GPX (when script available)
2. Load in emulator Extended Controls

### 5.4 Current Status

| Item | Status | Notes |
|------|--------|-------|
| Test implementation | ⚠️ Future | E2E tests not yet written |
| Test data | ⚠️ Placeholder | JSONL files need creation |
| GPS simulation | ⚠️ Manual | Automated playback not implemented |

---

## 6. L4_SYS5_acceptance — System Qualification Tests

**ASPICE:** SYS.5 — System Qualification Test  
**Prerequisites:** Human tester at track with physical device  
**Test Location:** `05_tests/L4_SYS5_acceptance/`

**Status:** ❌ Manual only (cannot be automated)

### 6.1 Test Checklists

See `05_tests/L4_SYS5_acceptance/` for:
- Pre-track validation tests
- On-track recording tests
- Session review tests
- Acceptance sign-off

---

## 7. Quick Reference

### Master Script (Recommended)

```bash
# Full test run with report
./05_tests/infra/scripts/run-all-tests.sh

# With emulator
./05_tests/infra/scripts/run-all-tests.sh --start-emulator --stop-emulator

# Specific level
./05_tests/infra/scripts/run-all-tests.sh --level L1
./05_tests/infra/scripts/run-all-tests.sh --level L2 --start-emulator
```

### Decision Tree

```
START: User requests test execution
  │
  ├─► RECOMMENDED: Use ./05_tests/infra/scripts/run-all-tests.sh
  │     (handles everything automatically, generates report)
  │
  ├─► OR MANUALLY:
  │     │
  │     ├─► ALWAYS: Run L1_SWE4_unit (Unit Tests)
  │     │     Command: ./gradlew testDebugUnitTest
  │     │     Check: Exit code 0 = pass
  │     │
  │     ├─► CHECK: Is device/emulator available?
  │     │     Command: adb devices | grep -E "device$|emulator"
  │     │     │
  │     │     ├─► NO DEVICE:
  │     │     │     Option A: Start emulator
  │     │     │       ./05_tests/infra/scripts/setup-emulator.sh
  │     │     │       ./05_tests/infra/scripts/start-emulator.sh
  │     │     │     Option B: Skip L2, report:
  │     │     │       "L1 passed. L2 skipped (no device)."
  │     │     │
  │     │     └─► DEVICE FOUND:
  │     │           Run L2_SWE5_integration:
  │     │             ./gradlew connectedDebugAndroidTest
  │     │
  │     └─► GENERATE REPORT:
  │           ./05_tests/infra/scripts/generate-report.sh
  │
  └─► END
```

### Copy-Paste Commands

```bash
# === RECOMMENDED: Full test run with report ===
cd /home/ctw00173_ubuntu/05_AI_DIY/Trillian
./05_tests/infra/scripts/run-all-tests.sh
```

```bash
# === MANUAL: L1 only ===
cd /home/ctw00173_ubuntu/05_AI_DIY/Trillian
./gradlew testDebugUnitTest --quiet && echo "✅ L1 passed" || echo "❌ L1 failed"
```

```bash
# === MANUAL: L1 + L2 (with emulator setup) ===
cd /home/ctw00173_ubuntu/05_AI_DIY/Trillian
./gradlew testDebugUnitTest --quiet && echo "✅ L1 passed"
./05_tests/infra/scripts/setup-emulator.sh
./05_tests/infra/scripts/start-emulator.sh
./gradlew connectedDebugAndroidTest --quiet && echo "✅ L2 passed" || echo "❌ L2 failed"
./05_tests/infra/scripts/stop-emulator.sh
./05_tests/infra/scripts/generate-report.sh
```

### Exit Code Reference

| Exit Code | Meaning |
|-----------|---------|
| 0 | All tests passed |
| 1 | Tests failed |
| Other | Build/configuration error |

---

## 8. Troubleshooting

### "SDK not found"

```bash
# Check local.properties exists
cat local.properties | grep sdk.dir

# If missing, create it:
echo "sdk.dir=/home/ctw00173_ubuntu/android-sdk" > local.properties
```

### "No connected devices"

```bash
# Check ADB
SDK_PATH=$(grep "sdk.dir" local.properties | cut -d'=' -f2)
$SDK_PATH/platform-tools/adb devices

# Restart ADB
$SDK_PATH/platform-tools/adb kill-server
$SDK_PATH/platform-tools/adb start-server
```

### "Tests not found"

```bash
# Verify test source sets exist
ls app/src/test/java
ls app/src/androidTest/java

# Clean and rebuild
./gradlew clean testDebugUnitTest
```

### Build fails before tests

```bash
# Compile only (no tests)
./gradlew compileDebugKotlin compileDebugAndroidTestKotlin

# Check for errors in output
```

---

## 9. Agent Behavior Summary

When asked to run tests:

1. **Preferred:** Use `./05_tests/infra/scripts/run-all-tests.sh`
2. **Always** run L1_SWE4_unit (unit tests)
3. **Check** for device/emulator before L2 — use emulator scripts if needed
4. **Generate** consolidated report via `generate-report.sh`
5. **Report** using ASPICE level names (L1, L2, L3, L4)

**Standard response format:**

```
📊 TEST RESULTS (ASPICE)

| Level | ASPICE | Status | Tests | Passed | Failed |
|-------|--------|--------|-------|--------|--------|
| L1_SWE4_unit | SWE.4 | ✅ PASS | 92 | 92 | 0 |
| L2_SWE5_integration | SWE.5 | ⏭️ NOT EXECUTED | — | — | — |
| L3_SWE6_qualification | SWE.6 | ⏭️ NOT EXECUTED | — | — | — |
| L4_SYS5_acceptance | SYS.5 | ⏭️ NOT EXECUTED | — | — | — |

Report: 05_tests/reports/TEST_REPORT_2026-07-22_18-30-00.md
```

**If user wants L2 but no device:**

```
L2 integration tests require a device or emulator.

Options:
1. I can set up and start an emulator:
   ./05_tests/infra/scripts/setup-emulator.sh
   ./05_tests/infra/scripts/start-emulator.sh
   
2. Or connect a physical device via USB.

Which would you prefer?
```

---

*This document is for agent consumption. For human documentation, see `Test_Strategy.md`.*  
*ASPICE aligned: 2026-07-22*
