# Test Infrastructure

This directory contains scripts and configuration for ASPICE-aligned test automation.

## ASPICE Test Levels

| Level | Folder | ASPICE | Execution |
|-------|--------|--------|-----------|
| L1 | `L1_SWE4_unit` | SWE.4 | JVM (local) |
| L2 | `L2_SWE5_integration` | SWE.5 | Device/Emulator |
| L3 | `L3_SWE6_qualification` | SWE.6 | Emulator + test data |
| L4 | `L4_SYS5_acceptance` | SYS.5 | Human at track |

## Directory Structure

```
infra/
├── scripts/
│   ├── setup-emulator.sh     # Install emulator, system image, create AVD
│   ├── start-emulator.sh     # Start headless emulator, wait for boot
│   ├── stop-emulator.sh      # Graceful emulator shutdown
│   ├── run-instrumented.sh   # Run L2 integration tests
│   ├── run-all-tests.sh      # Master orchestrator (L1 + L2 + report)
│   └── generate-report.sh    # Generate consolidated test report
├── config/
│   └── avd-config.ini        # AVD configuration documentation
└── README.md                 # This file
```

## Quick Start

### Run All Tests (Recommended)

```bash
# Run L1 unit tests + L2 integration (if device available) + generate report
./05_tests/infra/scripts/run-all-tests.sh

# With emulator auto-start
./05_tests/infra/scripts/run-all-tests.sh --start-emulator --stop-emulator
```

### Run Specific Level

```bash
# Unit tests only
./05_tests/infra/scripts/run-all-tests.sh --level L1

# Integration tests only (requires device)
./05_tests/infra/scripts/run-all-tests.sh --level L2 --start-emulator
```

## Prerequisites

- Android SDK installed (`$ANDROID_SDK_ROOT` or `$ANDROID_HOME` set)
- Java 17+
- KVM enabled (for fast emulation on Linux)

### Check KVM

```bash
# Should show /dev/kvm exists
ls -la /dev/kvm

# Check access
[ -r /dev/kvm ] && [ -w /dev/kvm ] && echo "KVM OK"
```

## Scripts Reference

### setup-emulator.sh

**Purpose:** Idempotent emulator setup (install + configure)

```bash
./05_tests/infra/scripts/setup-emulator.sh
```

**What it does:**
1. Checks KVM availability
2. Installs emulator package (if missing)
3. Installs system image: `android-30;google_apis;x86_64` (if missing)
4. Creates AVD: `DrivingCoach_Test` (if missing)

**Configuration:**
- API Level: 30 (Android 11)
- Device: Pixel 4
- ABI: x86_64

---

### start-emulator.sh

**Purpose:** Start emulator in headless mode

```bash
./05_tests/infra/scripts/start-emulator.sh [--timeout SECONDS]
```

**Options:**
- `--timeout`: Boot timeout in seconds (default: 120)

**What it does:**
1. Checks if emulator already running
2. Starts emulator with: `-no-window -no-audio -gpu swiftshader_indirect`
3. Waits for boot completion
4. Verifies ADB connection

---

### stop-emulator.sh

**Purpose:** Graceful emulator shutdown

```bash
./05_tests/infra/scripts/stop-emulator.sh [--force]
```

**Options:**
- `--force`: Force kill if graceful shutdown fails

---

### run-instrumented.sh

**Purpose:** Run L2 integration tests

```bash
./05_tests/infra/scripts/run-instrumented.sh [--start-emulator] [--stop-after]
```

**Options:**
- `--start-emulator`: Start emulator if no device connected
- `--stop-after`: Stop emulator after tests

---

### run-all-tests.sh

**Purpose:** Master test orchestrator

```bash
./05_tests/infra/scripts/run-all-tests.sh [OPTIONS]
```

**Options:**
- `--level LEVEL`: Run specific level (L1, L2, L3, all)
- `--start-emulator`: Start emulator for L2
- `--stop-emulator`: Stop emulator after tests
- `--skip-report`: Skip report generation

---

### generate-report.sh

**Purpose:** Generate consolidated test report

```bash
./05_tests/infra/scripts/generate-report.sh [--output DIR]
```

**Output:** `05_tests/reports/TEST_REPORT_YYYY-MM-DD_HH-MM-SS.md`

The report includes:
- Summary table with all test levels
- Pass/fail counts per level
- Failed test details
- "NOT EXECUTED" for levels not run
- Environment information

## Emulator Configuration

The AVD is configured for:
- **Headless execution** (no window, no audio)
- **GPS/sensors enabled** (for telemetry tests)
- **Software GPU** (swiftshader for CI compatibility)

See `config/avd-config.ini` for full configuration details.

## Troubleshooting

### Emulator won't start

```bash
# Check KVM permission
sudo chmod 666 /dev/kvm
# Or add user to kvm group
sudo adduser $USER kvm
```

### Tests not finding device

```bash
# List connected devices
adb devices

# Restart ADB
adb kill-server && adb start-server
```

### SDK not found

```bash
# Set SDK path
export ANDROID_SDK_ROOT=$HOME/android-sdk
```

### Boot timeout

```bash
# Increase timeout
./start-emulator.sh --timeout 180
```

---

*Infrastructure supports ASPICE test levels L1-L3. L4 is human-executed at track.*
