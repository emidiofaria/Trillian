# L3_SWE6_qualification — SW Qualification Test

**ASPICE Process:** SWE.6 — Software Qualification Test  
**Test Level:** End-to-End / System Tests  
**Execution Environment:** Emulator with Simulated Test Data

---

## Purpose

Verify that the integrated software meets software requirements by executing complete user workflows with simulated GPS/IMU data.

---

## Test Location

**Actual test files are located at:**

```
app/src/androidTest/java/com/drivingcoach/e2e/
├── RecordingFlowTest.kt      # Complete recording workflow
├── LapDetectionFlowTest.kt   # Lap detection accuracy
└── SessionReviewFlowTest.kt  # Post-session analysis
```

**Test data files:**

```
05_tests/data/
├── tracks/
│   ├── nurburgring_gp.jsonl    # GPS trace for Nürburgring GP
│   └── spa_francorchamps.jsonl  # GPS trace for Spa
└── edge-cases/
    ├── gps_dropout.jsonl        # GPS signal loss scenarios
    └── tunnel_passage.jsonl     # Tunnel/bridge scenarios
```

---

## How to Run

### Prerequisites
- Emulator with mock location support
- Test data files in `05_tests/data/`

### Commands

```bash
# Start emulator with mock location enabled
./05_tests/infra/scripts/start-emulator.sh

# Run E2E qualification tests
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.drivingcoach.e2e.*

# Or via infrastructure script
./05_tests/infra/scripts/run-all-tests.sh --level L3
```

---

## What Gets Tested

| Scenario | Verification |
|----------|--------------|
| Complete Recording | Start → Record → Stop → Save |
| Lap Detection | Correct lap count, timing accuracy |
| GPS Signal Loss | Recovery, data integrity |
| Session Review | Playback, statistics, export |

---

## Test Data Format

JSONL files with GPS/IMU samples:

```json
{"ts": 1234567890123, "lat": 50.3356, "lng": 6.9475, "alt": 320.5, "speed": 45.2, "bearing": 180.0}
{"ts": 1234567890223, "lat": 50.3357, "lng": 6.9476, "alt": 320.6, "speed": 46.1, "bearing": 181.5}
```

---

## Report Output

Results are included in the consolidated test report at:
```
05_tests/reports/TEST_REPORT_[timestamp].md
```

---

## Status

⚠️ **FUTURE IMPLEMENTATION**

E2E qualification tests and test data are planned but not yet implemented.

---

## ASPICE Traceability

| ASPICE Requirement | Implementation |
|--------------------|----------------|
| SWE.6.BP1 | SW qualification strategy → E2E test design |
| SWE.6.BP2 | SW qualification execution → `run-all-tests.sh --level L3` |
| SWE.6.BP3 | Results analysis → Test report L3 section |
