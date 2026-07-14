# Root Cause Analysis: Telemetry Header Missing Start Line Data

| Field | Value |
|-------|-------|
| **Incident ID** | 04 |
| **Application** | Driving Coach |
| **Severity** | MEDIUM |
| **RCA Date** | 2026-07-14 |
| **Analyst** | Copilot RCA Engine |

---

## Incident Summary

During investigation of lap detection issues (Incidents #02 and #03), debugging was difficult because the start line coordinates were stored in Room database while telemetry was in a separate JSONL file. This separation required access to multiple data sources to diagnose issues. This is a **design improvement** incident rather than a functional bug.

---

## Impact

| Metric | Value |
|--------|-------|
| **Users Affected** | Developers/Support debugging user issues |
| **Duration** | Since feature implementation |
| **Data Loss** | None |
| **Severity** | **MEDIUM** — Debugging inefficiency, not user-facing |

---

## Problem Analysis

### Data Separation Issue

```
BEFORE:
┌─────────────────────┐     ┌─────────────────────┐
│    Room Database    │     │   JSONL File        │
│                     │     │                     │
│ - sessionId         │     │ - GPS samples       │
│ - startLineLat1     │     │ - IMU samples       │
│ - startLineLng1     │     │ - timestamps        │
│ - startLineLat2     │     │                     │
│ - startLineLng2     │     │ NO START LINE!      │
│ - trackName         │     │                     │
└─────────────────────┘     └─────────────────────┘
         │                           │
         └───────────────────────────┘
                    ↓
         REQUIRES BOTH TO DEBUG
```

### Debugging Workflow (Before)

1. User reports: "No laps detected"
2. Developer asks user to extract telemetry file
3. Developer needs database access to get start line
4. On release builds, database not easily accessible
5. Must either:
   - Ask user to enable debug mode
   - Reproduce issue locally
   - Guess at start line coordinates

### Ideal State

```
AFTER:
┌──────────────────────────────────────────────────┐
│                  JSONL File                       │
│                                                  │
│ LINE 1 (Header):                                 │
│   {"type":"header","sessionId":1,                │
│    "startLine":{"lat1":...,"lng1":...},          │
│    "trackName":"Viana Kart"}                     │
│                                                  │
│ LINE 2+:                                         │
│   {"timestampMs":...,"latitude":...}             │
│   {"timestampMs":...,"latitude":...}             │
│   ...                                            │
└──────────────────────────────────────────────────┘
              ↓
    SINGLE FILE = COMPLETE CONTEXT
```

---

## Root Cause

### Causal Chain

```
DESIGN DECISION
↓ Original architecture separated metadata (Room) from samples (JSONL)
↓ Rationale: Room for structured queries, JSONL for streaming writes
↓
OVERSIGHT
↓ Start line is critical context for lap detection
↓ Not including it in JSONL means file is not self-contained
↓
CONSEQUENCE
↓ Debugging requires multiple data sources
↓ Cannot diagnose issues from telemetry file alone
↓ Support process is slower and more complex
```

### Root Cause Statement

**Root Cause**: The telemetry JSONL file was designed as a pure data stream without session metadata. The start line coordinates required for lap detection were stored only in Room database, making the JSONL file incomplete for diagnostic purposes.

**Design Rationale (Original)**: Separation of concerns - Room for metadata, JSONL for samples.

**Why This Is Problematic**: Lap detection failures cannot be diagnosed from the telemetry file alone, slowing down debugging and support workflows.

---

## Fix Implemented

### New Data Classes

```kotlin
// TelemetrySample.kt

/**
 * Header line written at the start of a telemetry JSONL file.
 */
data class TelemetryHeader(
    val type: String = "header",
    val sessionId: Long,
    val startLine: StartLineData? = null,
    val trackName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Start line coordinates embedded in telemetry header.
 */
data class StartLineData(
    val lat1: Double,
    val lng1: Double,
    val lat2: Double,
    val lng2: Double
)
```

### TelemetryFileWriter Enhancement

```kotlin
/**
 * Writes the header line with session metadata and start line coordinates.
 * Should be called once at the start of recording.
 */
suspend fun writeHeader(
    startLineLat1: Double?,
    startLineLng1: Double?,
    startLineLat2: Double?,
    startLineLng2: Double?,
    trackName: String? = null
) = withContext(Dispatchers.IO) {
    mutex.withLock {
        val startLine = if (startLineLat1 != null && startLineLng1 != null &&
            startLineLat2 != null && startLineLng2 != null) {
            StartLineData(startLineLat1, startLineLng1, startLineLat2, startLineLng2)
        } else {
            null
        }
        
        val header = TelemetryHeader(
            sessionId = sessionId,
            startLine = startLine,
            trackName = trackName
        )
        
        val json = gson.toJson(header)
        writer?.apply {
            write(json)
            newLine()
        }
        headerWritten = true
    }
}
```

### Service Integration

```kotlin
// TelemetryForegroundService.kt - startRecording()

// Write header with start line coordinates (fetch from DB)
serviceScope.launch {
    val session = sessionDao.getSessionByIdSync(sessionId)
    telemetryWriter?.writeHeader(
        startLineLat1 = session?.startLineLat1,
        startLineLng1 = session?.startLineLng1,
        startLineLat2 = session?.startLineLat2,
        startLineLng2 = session?.startLineLng2,
        trackName = session?.trackName
    )
}
```

### LocalLapDetector Header Parsing

```kotlin
/**
 * Reads telemetry file, parsing both header and samples.
 * Header line (if present) has type="header" field.
 */
private fun readTelemetryFile(file: File): TelemetryFileData {
    val samples = mutableListOf<TelemetrySample>()
    var header: TelemetryHeader? = null
    
    BufferedReader(FileReader(file)).use { reader ->
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            line?.let { jsonLine ->
                val jsonObj = gson.fromJson(jsonLine, JsonObject::class.java)
                if (jsonObj.has("type") && jsonObj.get("type").asString == "header") {
                    header = gson.fromJson(jsonLine, TelemetryHeader::class.java)
                } else {
                    val sample = gson.fromJson(jsonLine, TelemetrySample::class.java)
                    samples.add(sample)
                }
            }
        }
    }
    
    return TelemetryFileData(header, samples.sortedBy { it.timestampMs })
}
```

---

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/java/com/drivingcoach/data/telemetry/TelemetrySample.kt` | Added `TelemetryHeader`, `StartLineData` classes |
| `app/src/main/java/com/drivingcoach/data/telemetry/TelemetryFileWriter.kt` | Added `writeHeader()` method |
| `app/src/main/java/com/drivingcoach/service/TelemetryForegroundService.kt` | Call `writeHeader()` on recording start |
| `app/src/main/java/com/drivingcoach/lap/LocalLapDetector.kt` | Added header parsing, `readStartLineFromFile()` method |

---

## Benefits

| Benefit | Description |
|---------|-------------|
| **Self-contained files** | Single file has all data needed for analysis |
| **Faster debugging** | No database access required |
| **Portable** | File can be shared, analyzed offline, sent to backend |
| **Reproducible** | Anyone can re-run lap detection with same inputs |
| **Future-proof** | Header can be extended with more metadata |

---

## Verification

### Test Case
1. Start recording session
2. Stop recording
3. Extract JSONL file from device
4. **Expected**: First line is header with start line coordinates

### Sample Output
```jsonl
{"type":"header","sessionId":1,"startLine":{"lat1":41.223178,"lng1":-8.714648,"lat2":41.223738,"lng2":-8.713493},"trackName":"Test Track","createdAt":1784037766000}
{"timestampMs":1784037766401,"latitude":41.22317,"longitude":-8.71464,...}
```

---

## Conclusion

This incident represents a **design improvement** that makes the telemetry file self-contained and easier to debug. While not a functional bug, it significantly improves the development and support workflow for lap detection issues.

**Type**: Design Improvement  
**Fix Verified**: Yes  
**Backward Compatibility**: New files have header; old files (without header) still work

---

*RCA completed. Telemetry file format improved to include session metadata.*
