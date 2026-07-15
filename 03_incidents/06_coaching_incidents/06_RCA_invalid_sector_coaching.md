# RCA Report: Invalid Sector and Consistency Calculations in Coach Insights

## Incident Summary

After completing a recorded track session and opening the Coach tab, the app generated coaching insights that displayed meaningless sector-specific feedback ("0ms quicker than average") and potentially misleading consistency metrics. The fastest-lap headline was correct, but the detail section praised a sector contribution that does not exist in locally-detected lap data.

## Impact

| Field | Value |
|-------|-------|
| **Users Affected** | All users recording sessions with local lap detection |
| **Duration** | Since local lap detection feature deployment |
| **Data Loss** | None — lap timing data is accurate |
| **Severity** | HIGH |

The coaching insights are non-actionable and could confuse users about where to improve their driving. This undermines trust in the Coach tab feature.

---

## Timeline

| Time | Event | Evidence |
|------|-------|----------|
| Session Start | Recording begins for Session 5 | `session.jsonl` header: `sessionId: 5` |
| ~136s | Recording ends with 507 telemetry samples | JSONL contains 507 telemetry records |
| Post-recording | `LocalLapDetector.detectLaps()` processes JSONL | `LocalLapDetector.kt` — returns `DetectedLap` objects |
| Post-detection | `RecordingViewModel.saveLapsToRoom()` persists laps | Lines 260-280: `sector1Ms = 0L, sector2Ms = 0L, sector3Ms = 0L` |
| Post-persist | `OfflineCoachingEngine.generateInsights()` creates insights | Uses `LapEntity.sector*Ms` values (all 0L) |
| Coach tab open | User sees "You nailed Sector 1 — 0ms quicker than average" | Incident report observation |

---

## Signals Observed

### Present (Expected)
| Signal | Value |
|--------|-------|
| Telemetry file | `session.jsonl` — 508 lines, valid GPS data |
| Lap detection | Working correctly — crossings detected from GPS |
| Best lap identification | Correct — based on `durationMs` |

### Present (Unexpected)
| Signal | Value |
|--------|-------|
| Sector times in Room | All `0L` — stored but meaningless |
| Sector insight generated | "0ms quicker than average" displayed to user |
| Consistency wording | "Laps vary by X" when showing standard deviation |

### Absent (Expected but Missing)
| Signal | Explanation |
|--------|-------------|
| Sector timing algorithm | No code exists to calculate sector splits |
| Sector boundary definition | Track setup does not capture sector lines |
| Null/sentinel detection | No guard in `OfflineCoachingEngine` for `sector*Ms == 0L` |

---

## Systems Involved

| Component | Role in Incident | Reference |
|-----------|------------------|-----------|
| `LocalLapDetector` | Correctly detects laps but returns no sector data | `LocalLapDetector.kt:61-66` |
| `RecordingViewModel` | Persists `sector*Ms = 0L` as explicit values | `RecordingViewModel.kt:260-280` |
| `OfflineCoachingEngine` | Calculates insights from zero sector values | `OfflineCoachingEngine.kt:48-70` |
| `LapEntity` | Schema allows 0L without distinguishing "missing" | `LapEntity.kt` |

---

## Evidence

### Primary Evidence

#### Evidence 1: `DetectedLap` has no sector data
**File**: `LocalLapDetector.kt:61-66`
```kotlin
data class DetectedLap(
    val lapNumber: Int,
    val startTs: Long,
    val endTs: Long,
    val durationMs: Long
)
```
The local lap detection model intentionally excludes sector timing — this is correct for Phase 1.

#### Evidence 2: Sectors hardcoded to 0L on persist
**File**: `RecordingViewModel.kt:260-275`
```kotlin
val lapEntities = detectedLaps.map { lap ->
    LapEntity(
        ...
        sector1Ms = 0L,  // No sectors in Phase 1
        sector2Ms = 0L,
        sector3Ms = 0L,
        ...
    )
}
```
The comment acknowledges the limitation, but 0L is indistinguishable from "actually measured 0ms."

#### Evidence 3: Coach engine calculates gains from zeros
**File**: `OfflineCoachingEngine.kt:48-65`
```kotlin
val avgS1 = laps.map { it.sector1Ms }.average()   // = 0.0
val avgS2 = laps.map { it.sector2Ms }.average()   // = 0.0
val avgS3 = laps.map { it.sector3Ms }.average()   // = 0.0

val gainS1 = avgS1 - bestLap.sector1Ms            // = 0.0 - 0 = 0
val gainS2 = avgS2 - bestLap.sector2Ms            // = 0.0 - 0 = 0
val gainS3 = avgS3 - bestLap.sector3Ms            // = 0.0 - 0 = 0
```
All gains are 0ms, but `maxByOrNull` still picks Sector 1 as "best."

#### Evidence 4: Resulting insight text
The code produces:
```
headline = "Lap 2 Was Your Fastest"
detail = "You nailed Sector 1 — 0ms quicker than average. Try to replicate that next session."
```
This is technically correct given the data but semantically meaningless.

#### Evidence 5: Consistency wording mismatch
**File**: `OfflineCoachingEngine.kt:78-89`
```kotlin
val stdDev = sqrt(variance)
...
detail = "Laps vary by ${formatTime(stdDev)}..."
```
The code displays standard deviation but the copy says "vary by" — commonly interpreted as range (`max - min`).

### Negative Evidence

| Absent Signal | Implication |
|---------------|-------------|
| Sector calculation logic | Confirms sectors cannot be computed locally |
| `sector*Ms = null` option | Schema does not support null → 0L used as sentinel |
| Guard for `allSectorsZero` | No check before generating sector insights |
| Unit test for zero sectors | `OfflineCoachingEngineTest.kt` uses non-zero sector values in all tests |

---

## Hypotheses

### Primary Hypothesis
**Missing Sector Data Treated as Valid Data**

**Claim**: The coaching engine generates sector insights from placeholder zero values because it lacks a guard for missing/unmeasured data.

**Evidence for**:
- `DetectedLap` intentionally excludes sectors (design decision)
- `saveLapsToRoom()` explicitly sets `sector*Ms = 0L` with "No sectors in Phase 1" comment
- `generateBestLapInsight()` performs arithmetic on sector fields without null-checking
- Resulting output: "0ms quicker than average" — confirmed in incident report

**Evidence against**:
- None

**Confidence**: HIGH (98%)

### Alternative Hypotheses

**1. Consistency Wording vs Calculation Mismatch**

**Claim**: The consistency insight uses population standard deviation but labels it as "vary by," which users interpret as lap range.

**Evidence for**:
- Code computes `sqrt(variance)` for stdDev
- User-facing copy: "Laps vary by..."
- Standard user expectation: "vary by" = range = `max(duration) - min(duration)`

**Evidence against**:
- None — this is a secondary defect

**Confidence**: HIGH (90%)

### Eliminated Hypotheses

| Hypothesis | Elimination Reason |
|------------|-------------------|
| Lap detection bug | Lap times appear correct; issue is in coaching engine |
| Database corruption | Data persists correctly; problem is semantic |
| Network/backend issue | Purely offline flow — no network involved |

---

## Root Cause

**Root Cause**: The `OfflineCoachingEngine` assumes sector data is always available and meaningful. When sectors are unavailable (Phase 1 local detection), the engine generates insights from placeholder 0L values, producing technically correct but semantically empty feedback.

**Trigger**: User completes a session using local lap detection (no backend processing) and opens the Coach tab.

**Contributing Factors**:
1. **No nullable sector representation** — `LapEntity.sector*Ms` is non-null `Long`, so 0L is used as sentinel
2. **No data-completeness check** — Coaching engine does not verify sectors exist before generating sector insights
3. **No tests for missing sectors** — All unit tests use non-zero sector values
4. **Wording/calculation mismatch** — Consistency insight displays stdDev but labels it "vary by"

---

## Confidence Level

**Overall Confidence**: HIGH (95%)

**Confidence Rationale**:
- Complete code path traced from detection → persist → coaching
- Source code confirms explicit `sector*Ms = 0L` with explanatory comment
- `OfflineCoachingEngine` lacks any guard for zero/missing sectors
- Incident report output matches predicted behavior from code analysis

**Remaining Uncertainty**:
- Exact lap times from `session.jsonl` not decoded (file too large), but lap detection correctness is not in question

---

## Mitigation

### Immediate (User Recovery)
1. No data loss — users can re-open session when fix is deployed
2. Advise users that sector-level feedback requires backend processing (out of scope for immediate fix)

### Short-term (Prevent Recurrence)
1. **Add sector guard in `OfflineCoachingEngine`**:
   ```kotlin
   private fun areSectorsAvailable(laps: List<LapEntity>): Boolean {
       return laps.all { it.sector1Ms > 0 && it.sector2Ms > 0 && it.sector3Ms > 0 }
   }
   ```
2. **Suppress sector insights when sectors unavailable**:
   - Replace `generateBestLapInsight()` with lap-only version when `!areSectorsAvailable(laps)`
   - Replace `generateSectorFocusInsight()` with "Sector analysis requires backend processing" placeholder

3. **Fix consistency wording**:
   - Option A: Change calculation to `max - min` (range) to match "vary by" wording
   - Option B: Change wording to "Standard deviation: Xms" or "Typical variation: Xms"

### Permanent (Systemic Fix)
1. **Represent missing data explicitly**:
   - Change `LapEntity.sector*Ms` to nullable `Long?`
   - Update `saveLapsToRoom()` to use `null` instead of `0L`
   - Update all consumers to handle null sectors
   
2. **Add unit tests for zero/null sectors**:
   - Test `generateInsights()` with all `sector*Ms = 0L`
   - Verify no sector-specific text is emitted
   - Verify lap-level insight is generated instead

---

## Prevention Recommendations

### Code Changes
| File | Change |
|------|--------|
| `OfflineCoachingEngine.kt` | Add `areSectorsAvailable()` guard; provide lap-only fallback |
| `RecordingViewModel.kt` | Use `null` instead of `0L` (requires schema change) |
| `LapEntity.kt` | Make `sector*Ms` nullable |
| `OfflineCoachingEngineTest.kt` | Add tests for zero/null sector scenarios |

### Monitoring
- Add analytics event when sector insight is generated with `gainMs <= 0`
- Track ratio of local-only vs backend-processed sessions showing sector insights

### Documentation
- Update Coach tab product spec to clarify: "Sector analysis requires backend processing; local sessions show lap-level insights only"

### Process
- Add "missing data sentinel" check to PR review checklist for data model changes
- Require edge-case tests when adding coaching insights

---

## Appendix: Session Log Analysis

| Metric | Value |
|--------|-------|
| Session ID | 5 |
| Track name | "Track Session" |
| Recording duration | ~136s |
| Telemetry samples | 507 |
| Speed range | 0.0 – 342.6 m/s (includes implausible outliers) |
| Start line coords | (47.2200, 14.7640) to (47.2196, 14.7644) |

**Data Quality Note**: Maximum speed of 342.6 m/s (~1233 km/h) is physically implausible. This is a separate data-quality issue that should be addressed with input validation/smoothing, but it does not affect this RCA's root cause finding.

---

## Resolution Status

**Status:** ✅ RESOLVED (2026-07-16)

### Fix Applied (v2.1)

| Issue | Fix | Verification |
|-------|-----|--------------|
| Zero sector insights | Added `areSectorsAvailable()` guard | ✅ 30 unit tests pass |
| "0ms quicker" text | Best lap shows "ahead of average" when no sectors | ✅ Tested on emulator |
| Sector focus insight | Shows "Coming Soon" upsell instead of false advice | ✅ Verified |
| Consistency wording | Changed "vary by" → "within" | ✅ Verified |
| GPS noise (342 m/s) | Added 350 km/h cap on top speed | ✅ Unit tests verify |

### Files Changed

- `app/src/main/java/com/drivingcoach/coaching/OfflineCoachingEngine.kt`
- `app/src/main/java/com/drivingcoach/ui/recording/RecordingViewModel.kt`
- `app/src/test/java/com/drivingcoach/coaching/OfflineCoachingEngineTest.kt` (+11 tests)

### Linked Commits

- TBD (pending commit)

### Related Session Visibility Fix (v2.2)

A separate bug was discovered during testing: sessions not appearing in "Recent Sessions" due to userId mismatch (`demo_user` vs `default_user`). Fixed by removing userId filter for single-user MVP.
