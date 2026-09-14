# Incident: "No laps detected" After Three Completed Laps — Track Session

**Date Reported**: 2026-09-13  
**Reported By**: Developer / User test session  
**Severity**: **HIGH** — Core product feature (lap timing and coaching) failed to produce any laps for a valid 3-lap session  
**Status**: Reported / Under Investigation  
**Component**: `LocalLapDetector` (on-device lap detection) / Track Setup start-line capture  
**Build Under Test**: `versionName "2.95"`, `versionCode 295`  
**Device**: ZTE Blade A53+, Android SDK 31  
**Session**: ID 10, Track name: `Inês 3 - no lap detection`, recorded 2026-09-13  

---

## Original Report (verbatim)

> incident description, after a test session 3 laps, app did not detect any lap.  
> telemetry is in this directory.  
> on analysis tab circuit graphs is correct and data seems to be correct but laps were not detected.

---

## Description

A driver recorded a session driving 3 laps on a kart circuit. The recording completed normally without application crashes or data logging interruptions.

Upon session completion:
- The Analysis tab displayed the circuit track map and trajectory graphs correctly, confirming complete GPS telemetry capture.
- However, the app reported: **"No laps detected. Complete at least 2 laps."**
- No lap times were displayed and no coaching insights were generated.

The telemetry stream is complete and healthy, containing all 4 passes across the start/finish straight bounding the 3 driven laps.

---

## Observed Behaviour

| Observation | Value |
|-------------|-------|
| UI message shown | `No laps detected. Complete at least 2 laps.` |
| Laps stored in session | `[]` (empty — see `session.json`) |
| `processingStatus` | `PENDING` |
| `uploadStatus` | `FAILED` (offline session) |
| Analysis tab graph | Circuit map / trajectory rendered correctly |
| Coaching insights | None generated (requires detected laps) |

### Expected Behaviour

The session should have detected **3 completed laps** (from 4 passes of the start/finish straight), with corresponding lap times, sector splits, and offline coaching insights.

---

## Evidence — Attached Telemetry

The session files exported from the device are stored in:
`telemetry_no_lap_detection_20260913-2045/`

| File | Description |
|------|-------------|
| `session.json` | Session metadata, device info, captured start line coordinates, and empty `laps` array |
| `telemetry.jsonl` | 84 KB stream containing 1 header line and 351 GPS/sensor telemetry samples |

### Session Summary (from `telemetry.jsonl`)

| Metric | Value |
|--------|-------|
| Telemetry samples | 351 (plus 1 header line) |
| Duration | ~351 s (~5.8 minutes) |
| Sample rate | Nominally 1 Hz |
| GPS bounding box | Lat `[41.199573, 41.201591]`, Lng `[-8.612559, -8.608922]` |
| GPS accuracy | Mean ~8.5 m |

### Start Line Captured

From `session.json` and telemetry header:
```json
"startLine": {
  "lat1": 41.200443267822266, "lng1": -8.611432075500488,
  "lat2": 41.20037078857422, "lng2": -8.611417770385742
}
```
- **Midpoint**: `(41.200407, -8.611425)`
- **Length**: ~8.15 m
- **Bearing**: ~171.6°

### Passes Across the Start Straight

The vehicle traversed the start/finish straight 4 times in the session with consistent headings and speeds:

| Pass | Sample Index | Approximate Time | Heading | Speed | Notes |
|------|--------------|------------------|---------|-------|-------|
| 1 | 39–41 | ~39 s | ~281.8° | ~7.3 m/s | Out-lap completion |
| 2 | 144–146 | ~144 s | ~283.3° | ~12.4 m/s | Lap 1 completion (~105 s) |
| 3 | 230–232 | ~230 s | ~282.6° | ~12.1 m/s | Lap 2 completion (~86 s) |
| 4 | 324–326 | ~324 s | ~283.7° | ~12.5 m/s | Lap 3 completion (~94 s) |

---

## Impact

| Aspect | Assessment |
|--------|------------|
| Data loss | **None** — Telemetry is fully intact |
| Feature loss | **Total for this session** — No lap times, no comparisons, no coaching insights |
| User experience | High friction — Driver completed 3 laps but app reported 0 laps |

---

## Prior Related Incidents

| Incident | Title | Summary |
|----------|-------|---------|
| 02 | Lap detection not triggering | Service teardown race condition |
| 03 | 200m threshold too large | `MIN_DISTANCE_FROM_START_M` reduced from 200 m to 50 m |
| 09 | No laps after 4 laps | Unconfirmed telemetry failure |
| 13 | Start line parallel to direction of travel | Midpoint perpendicular plane detection introduced in v2.94 |
