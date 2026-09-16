# Test Data

This directory contains test data files used by E2E and instrumented tests.

## Directory Structure

```
data/
├── tracks/           # Real track session recordings
├── edge-cases/       # Edge case and error scenarios
└── README.md         # This file
```

## File Format: JSONL Telemetry

Test data files use the same JSONL format as production telemetry.

### Header Line (first line)

```json
{"type":"header","version":"1.0","sessionId":123,"startLineLat1":48.135,"startLineLng1":11.582,"startLineLat2":48.135,"startLineLng2":11.583,"trackName":"Test Track"}
```

### Sample Lines (subsequent lines)

```json
{"ts":1700000000000,"lat":48.13520,"lng":11.58210,"spd":25.5,"hdg":90.0,"ax":0.1,"ay":-0.2,"az":9.8,"gx":0.01,"gy":0.02,"gz":0.00,"acc":3.5}
```

### Field Reference

| Field | Type | Unit | Description |
|-------|------|------|-------------|
| `ts` | Long | ms | Unix timestamp in milliseconds |
| `lat` | Double | degrees | Latitude |
| `lng` | Double | degrees | Longitude |
| `spd` | Float | m/s | Speed |
| `hdg` | Float | degrees | Heading (0-360) |
| `ax`, `ay`, `az` | Float | m/s² | Accelerometer X, Y, Z |
| `gx`, `gy`, `gz` | Float | rad/s | Gyroscope X, Y, Z |
| `acc` | Float | meters | GPS accuracy |

## Creating Test Data

### From Real Session

1. Record a session on track
2. Copy telemetry file from device:
   ```bash
   adb pull /data/data/io.github.emidiofaria.trillian/files/telemetry/session_X.jsonl
   ```
3. Anonymize if needed (remove user identifiers)
4. Place in appropriate folder

### Synthetic Generation

Use the test data generator (when implemented):
```bash
./05_tests/infra/scripts/generate-test-data.py --laps 3 --track spa
```

## Available Test Data

### tracks/

| File | Track | Laps | Notes |
|------|-------|------|-------|
| TBD | | | Add real recordings |

### edge-cases/

| File | Scenario | Notes |
|------|----------|-------|
| TBD | | Add edge case data |

---

*Add test data files as they become available.*
