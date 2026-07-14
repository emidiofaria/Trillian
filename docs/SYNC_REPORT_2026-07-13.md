# Documentation Sync Report

**Date:** 2026-07-13  
**Session:** Local Lap Detection Implementation  
**Triggered By:** Session incident fixes and feature implementation

---

## Summary

This sync updates documentation to reflect the new **local (offline) lap detection** feature and related fixes implemented during debugging session.

### Features Documented

1. **Local Lap Detection** — Immediate lap time display without network connectivity
2. **Telemetry File Header** — Self-contained session metadata in JSONL files
3. **Dismissible Status Bar** — Compact upload status UI that doesn't block lap data
4. **50m Detection Threshold** — Kart-track compatible distance threshold (was 200m)

---

## Artifacts Updated

### 1. SkunkOps/atlas/system.md

**Change:** Added `LocalLapDetector` component and updated `TelemetryFileWriter` description

```diff
| Responsibility | Component |
|----------------|-----------|
| GPS/IMU capture at 10 Hz | `TelemetryForegroundService` |
-| Telemetry file persistence | `TelemetryFileWriter` (JSONL) |
+| Telemetry file persistence | `TelemetryFileWriter` (JSONL with header) |
+| Local (offline) lap detection | `LocalLapDetector` |
| Session state management | Room database + `SessionRepository` |
```

---

### 2. SkunkOps/atlas/components.md

**Change:** Added new component section for `LocalLapDetector`

**New Section:**
```markdown
## Component: Local Lap Detector

### Purpose
Detects laps locally (offline) from a JSONL telemetry file...

### Configuration Constants
| Constant | Value | Purpose |
|----------|-------|---------|
| `MIN_LAP_TIME_MS` | 20,000 | Prevents GPS jitter false positives |
| `MIN_DISTANCE_FROM_START_M` | 50 | Kart-track compatible threshold |
| `MIN_SAMPLES` | 50 | Minimum telemetry samples required |
```

---

### 3. SkunkOps/atlas/flows.md

**Change:** Added new flow section for local lap detection

**New Section:**
```markdown
## Flow: Local Lap Detection (Offline)

### Goal
Detect lap boundaries from telemetry file immediately after recording stops...

### Execution Path
RecordingViewModel.stopRecording()
→ serviceBinder?.stopRecording(sessionId)
→ processLapsLocally(sessionId)
  → LocalLapDetector.readTelemetryFile(filePath)
  → LocalLapDetector.detectLaps(samples, startLine)
  → lapDao.insertAll(laps)
```

---

### 4. 01_requirements/DrivingCoach_SRS_v1.md

**Changes:**

1. **Updated LD-06:** Clarified server-side 200m threshold
2. **Added TC-06a:** Telemetry file header format requirement
3. **Added Section 8.1:** Local lap detection requirements (LD-20 through LD-28)

**New Requirements:**
| ID | Requirement |
|---|---|
| LD-20 | Local lap detection shall run immediately after recording stops |
| LD-21 | Same segment-intersection algorithm as server |
| LD-22 | 20,000ms minimum lap time guard |
| LD-23 | 50m minimum distance from start (kart-compatible) |
| LD-24 | Laps stored with `isLocalOnly=true` flag |
| LD-25 | Server results overwrite local results |
| LD-26 | "No laps detected" message on failure |
| LD-27 | "📶 Offline" indicator when offline |
| LD-28 | Start line from header or SessionEntity |

---

### 5. docs/USER_MANUAL.md

**Changes:**

1. **Section 4.3:** Rewritten to explain offline mode, status bar, upload states
2. **Section 5.1:** Added "Local vs Server Detection" explanation, updated lap criteria
3. **Section 7 (Troubleshooting):** Expanded "No Laps Detected" with more causes/solutions

**Key User-Facing Updates:**
- Lap times appear immediately after stopping (no network needed)
- "📶 Offline • Tap to upload" status bar explained
- 50m threshold documented for kart track users
- Minimum 2 laps requirement clarified

---

## Code Changes Referenced

| File | Change |
|------|--------|
| `LocalLapDetector.kt` | New: Local lap detection algorithm |
| `TelemetrySample.kt` | Added: `TelemetryHeader`, `StartLineData` classes |
| `TelemetryFileWriter.kt` | Added: `writeHeader()` method |
| `TelemetryForegroundService.kt` | Modified: Calls `writeHeader()` on start |
| `RecordingViewModel.kt` | Modified: Calls `processLapsLocally()` directly on stop |
| `SessionResultFragment.kt` | Modified: Compact dismissible status bar |
| `fragment_session_result.xml` | Modified: `uploadStatusBar` replaces `processingCard` |

---

## Related Incidents

| Incident | RCA Document |
|----------|--------------|
| Lap detection not triggering | `03_incidents/02_lap_detection_not_triggering/` |
| 200m threshold too large | `03_incidents/03_200m_threshold_too_large/` |
| Telemetry header missing | `03_incidents/04_telemetry_header_missing/` |
| Status banner blocks data | `03_incidents/05_status_banner_blocks_data/` |

---

## Validation

- [x] system.md reflects new `LocalLapDetector` component
- [x] components.md has detailed component specification
- [x] flows.md has local lap detection flow diagram
- [x] SRS has new requirements (LD-20 through LD-28, TC-06a)
- [x] USER_MANUAL explains offline lap times to end users
- [x] Troubleshooting updated with new diagnostic steps

---

## Notes

1. **Backward Compatibility:** Files without header are still parseable (header is optional)
2. **Threshold Difference:** Server uses 200m, local uses 50m (intentional for kart tracks)
3. **Authoritative Source:** Server results overwrite local when available
