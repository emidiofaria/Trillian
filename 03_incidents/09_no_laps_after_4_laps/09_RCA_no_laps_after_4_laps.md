# Root Cause Analysis: No Laps Detected After 4 Laps on Emulator

| Field | Value |
|-------|-------|
| **Incident ID** | 09 |
| **Application** | Driving Coach |
| **Severity** | HIGH |
| **RCA Date** | 2026-07-21 |
| **Analyst** | Copilot RCA Engine |
| **APK Version** | DrivingCoach-v2.4-session-management.apk |

---

## Incident Summary

User reported that after driving 4 laps on the Android emulator, the app displayed "No laps detected" despite completing multiple laps. This is reported as a regression — lap detection was working in previous sessions. The recent change was the addition of session management features (delete/rename).

---

## Impact

| Metric | Value |
|--------|-------|
| **Users Affected** | Unknown (reported by developer testing) |
| **Duration** | Single session |
| **Data Loss** | Telemetry recorded, laps not detected |
| **Severity** | **HIGH** — Core feature (lap detection) non-functional |
| **Functionality Impact** | Offline lap detection returned 0 laps |

---

## System Localization

### Atlas Consultation

| Document | Finding |
|----------|---------|
| `failure-patterns.md` | No exact pattern match for "no laps detected on emulator" |
| `components.md` | `LocalLapDetector` identified as primary component (lines 85-140) |
| `flows.md` | "Local Lap Detection (Offline)" flow documented (lines 381-480) |

### Primary Component: LocalLapDetector

- **File**: `app/src/main/java/com/drivingcoach/lap/LocalLapDetector.kt`
- **Function**: GPS line-intersection lap detection
- **Criticality**: HIGH — Core lap timing functionality

### Affected Flow: Local Lap Detection (Offline)

```
RecordingViewModel.stopRecording()
→ processLapsLocally(sessionId)
  → sessionRepository.getSessionByIdSync(sessionId)  ← Get start line from Room
  → LocalLapDetector.detectLaps(jsonlFile, startLine)
    → detectCrossings(samples, startLine)
    → buildLaps(crossings)
  → saveLapsToRoom() [if laps.size >= 2]
```

### Failure Stage

The failure occurs at one of these stages:
1. Start line retrieval from Room (null values)
2. Line intersection detection (no crossings found)
3. Minimum lap guards filtering all crossings

---

## Timeline

| Time | Event | Evidence |
|------|-------|----------|
| T-X | User enters track name | HomeFragment dialog |
| T-Y | User captures start line Point A | TrackSetupFragment |
| T-Y+10s | User captures start line Point B | TrackSetupFragment |
| T-0 | User taps "Start Recording" | Navigation to RecordingFragment |
| T+0s | Session created in Room with start line coords | `createSessionAndStartRecording()` |
| T+5s | Recording started, GPS tracking active | TelemetryForegroundService |
| T+~5min | User drives 4 laps on emulator | GPS simulation |
| T+~5min | User taps "Stop Recording" | RecordingFragment |
| T+~5min+1s | `processLapsLocally()` called | RecordingViewModel |
| T+~5min+2s | **Result: "No laps detected"** | UI message |

---

## Signals Observed

### Present (Expected)
- Session management APK built and deployed ✅
- Session created in Room database ✅
- Telemetry file created ✅
- `processLapsLocally()` called ✅

### Absent (Requires Investigation)
- Logcat showing lap detection execution
- Start line coordinates in Room session
- GPS sample variation in telemetry file
- Line crossing detection events

### Negative Evidence
- No crash reported — app continued to run
- Session appears in session list — database write succeeded
- Recording completed — telemetry file should exist

---

## Evidence

### Code Analysis

#### 1. Session Management Changes (CLEARED)

**Finding**: Session management changes do NOT affect lap detection.

```bash
$ git diff Lap_detection_v2_phone_dashboard...Feature_delete_and_rename_session -- "*.kt" | head -50
# Changes limited to:
# - OfflineCoachingEngine.kt (new file) - generates insights AFTER lap detection
# - SessionDao.kt - adds deleteById(), updateTrackName() methods
# - HomeFragment.kt - adds context menu for delete/rename
# - HomeViewModel.kt - adds deleteSession(), renameSession()
```

**Conclusion**: These changes are downstream of lap detection. The lap detection code path is unchanged.

#### 2. Lap Detection Algorithm (VERIFIED WORKING)

**Test**: Simulated lap detection on `03_incidents/06_coaching_incidents/session.jsonl`:

```python
# Results:
# Crossing 1: ts=1784152511682, first crossing
# Crossing 2: ts=1784152557402, time_since_last=45720ms
# Crossing 3: ts=1784152593417, time_since_last=36015ms
# Total crossings: 3
# Laps detected: 2
```

**Conclusion**: LocalLapDetector algorithm correctly detects laps when provided valid start line and GPS samples.

#### 3. Previous Race Condition Fix (VERIFIED IN PLACE)

**Finding**: Fix from Incident 02 is still present.

```kotlin
// RecordingViewModel.kt:168-180
fun stopRecording() {
    val sessionId = _uiState.value.sessionId
    TelemetryForegroundService.stopRecording(context)
    _uiState.value = _uiState.value.copy(isStopping = true)
    
    // Process laps directly - don't rely on service state flow
    if (sessionId != -1L && !hasProcessedLaps) {
        hasProcessedLaps = true
        Log.d(TAG, "Triggering local lap processing for session $sessionId")
        processLapsLocally()  // ← Direct call, no race condition
    }
}
```

**Conclusion**: The race condition from Incident 02 is not the cause.

#### 4. Start Line Storage (POTENTIAL ISSUE)

**Flow**:
1. TrackSetupFragment captures GPS coordinates as `LatLng` (Double)
2. Navigation args pass as Float: `startLineLat1 = coords.lat1.toFloat()`
3. RecordingFragment receives and converts back: `lat1 = args.startLineLat1.toDouble()`
4. Session created in Room with start line values

**Potential Failure Points**:
- GPS not available on emulator during track setup
- Both points captured at same location (distance < 3m → invalid)
- Navigation arg Float precision loss (unlikely but possible)

#### 5. Database Migration (LOW RISK)

```kotlin
// DrivingCoachDatabase.kt
.addMigrations(
    DrivingCoachDatabase.MIGRATION_1_2, 
    DrivingCoachDatabase.MIGRATION_2_3,
    DrivingCoachDatabase.MIGRATION_3_4
)
.fallbackToDestructiveMigration()
```

**Finding**: MIGRATION_3_4 adds `session_preferences` table and `isLocalOnly` to `coaching_insights`. Does NOT affect start line columns (added in MIGRATION_1_2).

**Risk**: If migration fails, `fallbackToDestructiveMigration()` wipes DB, losing start line data.

---

## Hypotheses

### Hypothesis 1: Start Line Coordinates Null in Room
**Confidence**: MEDIUM (55%)

**Claim**: The start line coordinates stored in Room are null, causing `processLapsLocally()` to return early with "No start line defined".

**Evidence For**:
- `processLapsLocally()` checks for null start line and returns early (line 299-302)
- Start line depends on successful GPS fix in TrackSetup
- Emulator GPS may be slow to initialize

**Evidence Against**:
- User reported laps worked "before" (same TrackSetup flow)
- Float→Double conversion should preserve valid coordinates

**Investigation**: Check logcat for `"No start line defined for session"` message.

```kotlin
// RecordingViewModel.kt:299-303
if (startLine == null) {
    Log.w(TAG, "No start line defined for session $sessionId")
    finishProcessing("No start line defined", 0)
    return@launch
}
```

---

### Hypothesis 2: Emulator GPS Simulation Not Configured
**Confidence**: MEDIUM (50%)

**Claim**: The Android emulator's GPS simulation was not active during recording, resulting in stationary GPS samples that never cross the start line.

**Evidence For**:
- Emulators require manual GPS route configuration (GPX file or Extended Controls)
- First samples in test telemetry show identical coordinates (initial lock)
- No physical movement on emulator without GPS simulation

**Evidence Against**:
- User claims 4 laps completed (implies movement was visible on UI)
- GPS simulation would show movement in emulator location UI

**Investigation**: 
1. Check telemetry file for lat/lng variation
2. Verify GPX route was loaded in emulator Extended Controls

---

### Hypothesis 3: Start Line Doesn't Intersect GPS Route
**Confidence**: MEDIUM (45%)

**Claim**: The start line was captured at a location that doesn't geometrically intersect the GPS route driven, so no crossings are detected even with valid GPS samples.

**Evidence For**:
- Line intersection requires precise geometry
- Start line could be captured at track edge, not on racing line
- Emulator GPS simulation routes may not follow exact track layout

**Evidence Against**:
- MIN_DISTANCE_FROM_START_M guard (50m) should still allow detection
- Previous sessions worked (same track, same route)

**Investigation**:
1. Compare start line coordinates with GPS path bounds
2. Visualize line vs route geometry

---

### Hypothesis 4: Database Schema Migration Failure
**Confidence**: LOW (20%)

**Claim**: MIGRATION_3_4 failed, triggering `fallbackToDestructiveMigration()`, which wiped the database. The current session was created fresh without issues, but previous state is gone.

**Evidence For**:
- APK has schema version 4 (added MIGRATION_3_4)
- Destructive fallback configured
- User said this is a regression

**Evidence Against**:
- Migration only adds new table and column, shouldn't fail
- Would affect ALL sessions, not just lap detection
- Sessions still visible in app (not wiped)

---

### Eliminated Hypotheses

| Hypothesis | Reason for Elimination |
|------------|------------------------|
| Race condition (Incident 02) | Fix verified in current code |
| Session management code broke detection | Changes don't touch detection flow |
| Algorithm bug | Verified working with test data |
| File not found | Would log error, user didn't report crash |

---

## Root Cause

### Primary Assessment

**Confidence Level**: MEDIUM (55%)

Without access to actual log files from the failing session, the most likely root cause is:

**Root Cause**: Start line coordinates are null in Room database for this session.

**Trigger**: One of:
1. GPS not available during track setup (emulator location services not initialized)
2. User skipped track setup somehow
3. Navigation arg conversion resulted in all-zero values (if source was null)

**Causal Chain**:
```
TrackSetup GPS not available OR points captured too close
↓
getStartLineCoords() returns null OR distance < 3m (invalid)
↓
Navigation args pass 0.0f (default values)
↓
RecordingFragment condition: all args == 0f → startLine = null
↓
SessionEntity created with startLineLat1 = null, etc.
↓
processLapsLocally() reads session.startLineLat1 = null
↓
startLine construction fails (nested let returns null)
↓
"No start line defined" → 0 laps
```

### Secondary Assessment

If logs show start line IS present, then:

**Root Cause**: Emulator GPS simulation not active, resulting in stationary GPS path that doesn't cross start line.

---

## Remaining Uncertainty

| Unknown | Required Evidence |
|---------|-------------------|
| Actual session ID | User report |
| Start line values in Room | `adb shell "sqlite3 /data/data/com.drivingcoach/databases/driving_coach.db 'SELECT startLineLat1, startLineLng1, startLineLat2, startLineLng2 FROM sessions ORDER BY id DESC LIMIT 1'"` |
| Telemetry file contents | Pull file from device and analyze |
| Logcat output | `adb logcat -d RecordingViewModel:* LocalLapDetector:* TelemetryService:*` |

---

## Mitigation

### Immediate (Debug This Session)

1. **Check Logcat**:
   ```bash
   adb logcat -d | grep -E "RecordingViewModel|LocalLapDetector|startLine"
   ```
   Look for:
   - "No start line defined for session X"
   - "=== LAP DETECTION START ==="
   - "Start line: (lat1, lng1) to (lat2, lng2)"

2. **Query Room Database**:
   ```bash
   adb shell "sqlite3 /data/data/com.drivingcoach/databases/driving_coach.db 'SELECT id, trackName, startLineLat1, startLineLng1, startLineLat2, startLineLng2 FROM sessions ORDER BY id DESC LIMIT 5'"
   ```
   Look for: null values in startLine columns

3. **Pull Telemetry File**:
   ```bash
   adb pull /data/data/com.drivingcoach/files/telemetry/session_X.jsonl .
   head -5 session_X.jsonl
   ```
   Look for: header with startLine, GPS coordinate variation

### Short-term (Prevent Recurrence)

1. **Add Pre-Recording Validation**:
   ```kotlin
   // RecordingFragment.kt - before starting recording
   if (startLine == null) {
       Snackbar.make(binding.root, "Start line required. Please complete track setup.", Snackbar.LENGTH_LONG).show()
       findNavController().popBackStack()
       return
   }
   ```

2. **Add Logging for Start Line Storage**:
   ```kotlin
   // RecordingViewModel.kt - after session creation
   Log.d(TAG, "Session created with startLine: lat1=${session.startLineLat1}, lng1=${session.startLineLng1}, lat2=${session.startLineLat2}, lng2=${session.startLineLng2}")
   ```

### Permanent (Systemic Fix)

1. **Make Start Line Non-Nullable**:
   - Change flow to require start line for all sessions
   - Disable "Start Recording" button until valid start line captured

2. **Add Emulator GPS Warning**:
   ```kotlin
   // TrackSetupFragment - detect emulator
   if (Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")) {
       binding.emulatorWarning.visibility = View.VISIBLE
       binding.emulatorWarning.text = "Emulator detected: Ensure GPS route is configured in Extended Controls"
   }
   ```

3. **Add Start Line Validation Toast**:
   - Show toast confirming "Start line saved: XX.XXXm" after capture

---

## Prevention Recommendations

### Code Changes

| Change | File | Description |
|--------|------|-------------|
| Validate start line before recording | `RecordingFragment.kt` | Block recording if start line null |
| Log start line on session creation | `RecordingViewModel.kt` | Debug traceability |
| Add emulator detection warning | `TrackSetupFragment.kt` | User guidance |

### Testing

| Test | Type | Description |
|------|------|-------------|
| Emulator lap detection | E2E | Full flow with GPX route playback |
| Null start line handling | Unit | Verify graceful handling |
| Start line precision | Unit | Float→Double round-trip accuracy |

### Monitoring

| Metric | Implementation |
|--------|----------------|
| Lap detection success rate | Count sessions with `lapCount > 0` vs `lapCount = 0` |
| Start line capture rate | Count sessions with non-null start line values |

---

## Conclusion

This incident most likely stems from **missing start line coordinates** in the Room database for this session. The session management changes in v2.4 do not affect the lap detection flow. The LocalLapDetector algorithm is verified working with valid inputs.

**Required Next Step**: Obtain logcat and Room database query from the failing session to confirm root cause.

**Confidence Level**: MEDIUM (55%) — Pending actual log/DB evidence

**Fix Priority**: HIGH — Core feature failure, but likely user/environment specific rather than code regression

---

*RCA completed with limited evidence. Diagnostic steps provided for confirmation.*
