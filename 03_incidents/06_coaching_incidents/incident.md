# Bug Report: Coach Insights Use Invalid Sector and Consistency Calculations

| Field | Value |
|-------|-------|
| **Application** | Driving Coach |
| **Package** | `com.drivingcoach` |
| **Severity** | High |
| **Priority** | High |
| **Status** | Open |
| **Reported Date** | 2026-07-15 |
| **Affected Area** | Coach tab / offline coaching insights |
| **Evidence Log** | `session.jsonl` |

---

## Summary

After completing a recorded track session and opening the Coach tab, the app generated coaching insights that look plausible at headline level but contain incorrect or unsupported calculations in the detail text.

The fastest-lap headline appears correct, but the detail says the driver "nailed Sector 1" while being `0ms quicker than average`. This is not meaningful coaching feedback and points to sector timings being stored as zero rather than calculated from telemetry.

The consistency insight also reports "Solid Consistency" with a "Laps vary by ..." value that does not match the expected interpretation of lap variation.

---

## Steps to Reproduce

1. Start a recording session with a configured start/finish line.
2. Drive multiple laps.
3. Stop recording and allow local lap processing to complete.
4. Open the Coach tab for the completed session.
5. Review the generated offline coaching cards.

---

## Expected Behavior

- Coach insights should only mention sector performance when sector timings are actually calculated and persisted.
- A fastest-lap insight should identify the fastest lap and provide a real contributing factor.
- Sector delta text should not display `0ms quicker than average` as if it were a useful performance advantage.
- Consistency text should use a clearly defined calculation and wording that matches the displayed value.
- If only lap durations are available, the Coach tab should generate lap-level insights only, or explicitly state that sector analysis is unavailable.

## Actual Behavior

- The Coach tab reports: `Lap 2 Was Your Fastest`.
- The detail text reports: `You nailed Sector 1 - 0ms quicker than average`.
- The Coach tab reports: `Solid Consistency`.
- The consistency detail says laps vary by a value that appears inconsistent with expected lap spread/variation.
- Sector-specific coaching is generated even though sectors are not calculated for locally detected laps.

---

## Session Log Evidence

| Field | Value |
|-------|-------|
| Source file | `03_incidents/06_coaching_incidents/session.jsonl` |
| Session ID | `5` |
| Track name | `Track Session` |
| Total JSONL lines | `508` |
| Telemetry samples | `507` |
| First sample timestamp | `1784152503120` |
| Last sample timestamp | `1784152639279` |
| Recording duration | `136.159s` |
| Start line point 1 | `47.219993591308594, 14.764008522033691` |
| Start line point 2 | `47.21956253051758, 14.764364242553711` |
| Min reported speed | `0.000 m/s` |
| Max reported speed | `342.620 m/s` |
| Average reported speed | `127.498 m/s` |

Additional data-quality note: the log contains physically implausible speed values for this domain, with a maximum of `342.620 m/s`. Any coach calculation that depends on raw speed should validate, smooth, or reject outliers before generating user-facing feedback.

---

## Technical Analysis

### Finding 1: Fastest Lap Detail Uses Zero Sector Data

The fastest-lap headline can be correct because it is based on `durationMs`. The sector detail is not reliable because local lap detection does not calculate sector times.

Current local lap model only contains lap-level timing:

```kotlin
data class DetectedLap(
	val lapNumber: Int,
	val startTs: Long,
	val endTs: Long,
	val durationMs: Long
)
```

When detected laps are saved to Room, all sector values are explicitly written as zero:

```kotlin
sector1Ms = 0L,  // No sectors in Phase 1
sector2Ms = 0L,
sector3Ms = 0L,
```

The offline coach then calculates average sector values and gains from those zero values:

```kotlin
val avgS1 = laps.map { it.sector1Ms }.average()
val gainS1 = avgS1 - bestLap.sector1Ms
```

If every sector is `0`, every average is `0`, every gain is `0`, and the coach still emits sector praise. This directly explains the observed text: `Sector 1 - 0ms quicker than average`.

### Finding 2: Consistency Wording Does Not Match the Calculation

The consistency insight currently computes population standard deviation:

```kotlin
val variance = times.map { (it - mean).pow(2) }.average()
val stdDev = sqrt(variance)
val consistencyPct = ((1 - stdDev / mean) * 100).coerceIn(0.0, 100.0)
```

However, the user-facing message says `Laps vary by ...`. That wording is usually interpreted as the lap-time spread/range, not the standard deviation.

For example, if two laps differ by `2.0s`, the population standard deviation is `1.0s`, so the app would display a value that is half the actual lap-to-lap spread. This makes the coaching statement look mathematically wrong even if the standard deviation formula itself is implemented correctly.

### Finding 3: Sectors Are Not Calculated

Sector coaching is generated even though local processing has no sector boundaries and no sector timing algorithm.

This creates two defects:

- The persisted sector values are indistinguishable from a real `0ms` sector.
- The coach engine treats missing sector data as valid sector data.

---

## Root Cause Summary

- Offline lap detection currently produces only lap-level timings.
- `saveLapsToRoom()` persists missing sector timings as `0L`.
- `OfflineCoachingEngine` does not guard against missing/zero sector data before generating sector insights.
- Consistency copy says "vary by" while the implementation displays standard deviation.

---

## Recommended Fix

1. Represent missing sector timings explicitly instead of using `0L` as a sentinel value.
2. Suppress sector-specific coaching when sector data is missing or all sector values are zero.
3. Generate an alternative lap-level insight when sectors are unavailable.
4. Either change the consistency calculation to display lap range (`max(durationMs) - min(durationMs)`) or change the copy to accurately describe standard deviation.
5. Add tests for offline coaching using locally detected laps with `sector1Ms = sector2Ms = sector3Ms = 0L`.
6. Add telemetry sanity checks for implausible speed samples before using speed-derived data in coach insights.

---

## Acceptance Criteria

- Coach tab does not display `0ms quicker than average` as a positive sector insight.
- Coach tab does not generate sector-specific recommendations when sector timings are unavailable.
- Locally detected laps without sectors still produce useful lap-level coaching.
- Consistency detail uses a calculation that matches the wording shown to the user.
- Unit tests cover fastest-lap, consistency, and sector-focus insight generation for missing sector data.
- The attached `session.jsonl` can be replayed without producing misleading sector feedback.

---

## Attachments

- `session.jsonl` - Generated app log for the affected session.