# Bug Report: Telemetry Header Missing Start Line Data

| Field | Value |
|-------|-------|
| **Application** | Driving Coach |
| **Package** | `com.drivingcoach` |
| **Severity** | Medium |
| **Priority** | Medium |
| **Status** | Resolved |
| **Reported Date** | 2026-07-14 |
| **Resolved Date** | 2026-07-14 |

---

## Summary

When debugging lap detection issues, there was no easy way to verify if the start line coordinates were correct relative to the GPS path. The start line was stored in Room database while telemetry samples were in a separate JSONL file, requiring multiple data sources to diagnose issues.

---

## Problem Statement

### Before (Difficult Debugging)

To debug lap detection:
1. Extract telemetry JSONL file from device
2. Query Room database for start line coordinates
3. Manually compare GPS path bounds vs start line position
4. Requires database access and multiple tools

### Desired State

A single telemetry file containing:
1. Session metadata (session ID, track name)
2. Start line coordinates
3. All GPS/telemetry samples

---

## Use Case

**Scenario**: User reports "No laps detected" on a track session.

**Old Process**:
```
1. Pull telemetry file from device
2. Connect to Room DB (requires debug build or root)
3. Query: SELECT startLineLat1, startLineLng1, ... FROM sessions WHERE id = X
4. Manually compare coordinates
5. Often requires back-and-forth with user
```

**New Process**:
```
1. Pull telemetry file from device
2. First line contains header with start line
3. Immediate diagnosis possible
```

---

## Technical Specification

### New JSONL File Format

```jsonl
{"type":"header","sessionId":1,"startLine":{"lat1":41.223,"lng1":-8.714,"lat2":41.224,"lng2":-8.713},"trackName":"Viana Kart","createdAt":1784037766000}
{"timestampMs":1784037766401,"latitude":41.22317,"longitude":-8.71464,"speedMs":0.0,...}
{"timestampMs":1784037769098,"latitude":41.22373,"longitude":-8.71349,"speedMs":0.0,...}
...
```

### Header Schema

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Always "header" to identify this line |
| `sessionId` | long | Session ID for correlation |
| `startLine` | object | Start line coordinates (lat1, lng1, lat2, lng2) |
| `trackName` | string? | Optional track name |
| `createdAt` | long | Unix timestamp when recording started |

---

## Root Cause Summary

The original design stored start line coordinates only in Room database, treating the JSONL file as a pure telemetry stream. This separation made debugging difficult because it required access to two different data sources.

---

## Resolution

1. Added `TelemetryHeader` and `StartLineData` data classes
2. Modified `TelemetryFileWriter` to write header as first line
3. Updated `TelemetryForegroundService` to call `writeHeader()` on recording start
4. Updated `LocalLapDetector` to parse header line (skipping it when reading samples)

---

## Attachments

- `04_RCA_telemetry_header_missing.md` - Full root cause analysis
