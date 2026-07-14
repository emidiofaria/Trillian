# Bug Report: 200m Threshold Too Large for Kart Tracks

| Field | Value |
|-------|-------|
| **Application** | Driving Coach |
| **Package** | `com.drivingcoach` |
| **Severity** | High |
| **Priority** | High |
| **Status** | Resolved |
| **Reported Date** | 2026-07-14 |
| **Resolved Date** | 2026-07-14 |

---

## Summary

After fixing the lap detection triggering issue, laps were still not detected on kart tracks. Investigation revealed the `MIN_DISTANCE_FROM_START_M` threshold of 200 meters was too large for typical kart tracks (300-500m total length), causing valid laps to be filtered out.

---

## Steps to Reproduce

1. Open the Driving Coach app
2. Set up a kart track (~400m total length)
3. Complete multiple laps
4. Stop recording
5. **Result**: "No laps detected" despite multiple valid laps

---

## Expected Behavior

Lap detection should work on kart tracks where the total track length is 300-500 meters.

## Actual Behavior

Lap detection fails because the algorithm requires traveling 200m away from the start line before the next crossing counts. On a 400m kart track, this threshold is 50% of the entire lap - too restrictive.

---

## Technical Analysis

### Current Threshold Logic

```kotlin
// Guard: Must have traveled away from start before coming back
if (maxDistanceFromStart < MIN_DISTANCE_FROM_START_M) {  // 200m
    continue  // Skip this crossing
}
```

### Problem Visualization

```
KART TRACK (400m total):
┌─────────────────────────────┐
│                             │
│    ●──────────●             │
│   200m mark    ╲            │
│                 ╲           │
│ START ●──────────●          │
│       ↑                     │
│  Entire 200m zone           │
│  = 50% of track!            │
└─────────────────────────────┘
```

### Kart Track Size Reference

| Type | Typical Length |
|------|---------------|
| Small/Indoor | 300 - 500m |
| Medium/Outdoor | 500 - 800m |
| Large/Professional | 800 - 1,500m |

---

## Environment

| Component | Details |
|-----------|---------|
| Affected Component | `LocalLapDetector.kt` |
| Constant | `MIN_DISTANCE_FROM_START_M = 200.0` |
| Track Size | ~400m (kart track simulation) |

---

## Root Cause Summary

The 200m minimum distance threshold was designed for full-size car circuits (2-5km) but is too large for kart tracks. The 20-second minimum lap time is already the primary guard against GPS jitter false positives.

---

## Resolution

Reduced `MIN_DISTANCE_FROM_START_M` from 200m to 50m:
- 50m = 10x typical GPS error margin (3-5m)
- Works for kart tracks (50m = 12% of 400m track)
- Still safe for car circuits (50m = 1.6% of 3km track)

---

## Attachments

- `03_RCA_200m_threshold_too_large.md` - Full root cause analysis
