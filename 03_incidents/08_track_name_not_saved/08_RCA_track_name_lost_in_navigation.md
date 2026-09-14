# RCA: User-Defined Track Name Not Saved to Session

| Field | Value |
|-------|-------|
| **Incident ID** | 08 |
| **Severity** | Medium |
| **Confidence** | HIGH (95%) |
| **RCA Date** | 2026-07-16 |
| **Analyst** | RCA Engine |

---

## Incident Summary

User-provided track names are lost during navigation from Home → TrackSetup → Recording. All sessions are created with the hardcoded value `"Track Session"` instead of the user's input.

---

## Impact

- **Users Affected**: All users
- **Duration**: Since feature implementation
- **Data Loss**: User's track name input is discarded
- **Severity**: MEDIUM — functional but degraded UX

---

## Timeline

| Time | Event | Evidence |
|------|-------|----------|
| T+0s | User taps FAB on Home | UI action |
| T+1s | Dialog appears, user enters "Circuito de Braga" | User input |
| T+2s | `startNewSession("Circuito de Braga")` called | HomeFragment:95 |
| T+2s | `NavigateToTrackSetup("Circuito de Braga")` emitted | HomeViewModel:171 |
| T+2s | **trackName LOST** — nav action has no trackName arg | nav_graph.xml:68 |
| T+3s | TrackSetupFragment has no access to trackName | No arg defined |
| T+Ns | Navigate TrackSetup → Recording | User completes setup |
| T+Ns | `createSessionAndStartRecording("Track Session")` | RecordingFragment:206 |
| T+Ns | Session saved with `trackName = "Track Session"` | **FAILURE POINT** |

---

## Signals Observed

### Present (Expected)
- Dialog correctly captures user input
- HomeViewModel receives correct trackName
- Session is created successfully

### Present (Unexpected)
- All sessions have `trackName = "Track Session"`

### Absent (Expected but Missing)
- trackName argument in `action_home_to_track_setup`
- trackName argument in `action_track_setup_to_recording`
- trackName argument in `recordingFragment` destination

---

## Systems Involved

| Component | Role in Incident | Reference |
|-----------|------------------|-----------|
| `HomeFragment` | Captures user input | UI layer |
| `HomeViewModel` | Emits navigation event with trackName | ViewModel |
| `nav_graph.xml` | **Missing trackName args** | Navigation |
| `TrackSetupFragment` | Cannot receive trackName | UI layer |
| `RecordingFragment` | Uses hardcoded fallback | UI layer |

---

## Evidence

### Primary Evidence

**1. HomeFragment captures trackName correctly:**
```kotlin
// HomeFragment.kt:94-95
val trackName = editText.text.toString().trim()
viewModel.startNewSession(trackName)
```

**2. HomeViewModel emits trackName in event:**
```kotlin
// HomeViewModel.kt:171
_events.emit(HomeEvent.NavigateToTrackSetup(trackName.ifBlank { "Unknown Track" }))
```

**3. Navigation action MISSING trackName argument:**
```xml
<!-- nav_graph.xml:68-69 -->
<action
    android:id="@+id/action_home_to_track_setup"
    app:destination="@id/trackSetupFragment" />
<!-- NO trackName argument defined! -->
```

**4. TrackSetup → Recording also MISSING trackName:**
```xml
<!-- nav_graph.xml:92-107 -->
<action
    android:id="@+id/action_track_setup_to_recording"
    app:destination="@id/recordingFragment">
    <argument android:name="sessionId" ... />
    <argument android:name="startLineLat1" ... />
    <argument android:name="startLineLng1" ... />
    <argument android:name="startLineLat2" ... />
    <argument android:name="startLineLng2" ... />
    <!-- NO trackName argument! -->
</action>
```

**5. RecordingFragment uses hardcoded fallback:**
```kotlin
// RecordingFragment.kt:206
viewModel.createSessionAndStartRecording(
    trackName = "Track Session", // TODO: Get from nav args or preferences
    startLine = startLine
)
```

**6. The TODO comment confirms developer awareness:**
```kotlin
// TODO: Get from nav args or preferences
```

---

## Hypotheses

### Primary Hypothesis: Missing Navigation Arguments

**CLAIM**: The trackName is lost because the navigation graph does not define trackName as an argument between Home → TrackSetup → Recording.

**EVIDENCE FOR**:
- `action_home_to_track_setup` has no arguments
- `action_track_setup_to_recording` has no trackName argument
- `RecordingFragment` has no trackName in its arguments
- Hardcoded fallback with TODO comment

**EVIDENCE AGAINST**:
- None

**CONFIDENCE**: HIGH (95%)

---

## Eliminated Hypotheses

| Hypothesis | Reason Eliminated |
|------------|-------------------|
| Database not saving trackName | SessionEntity.trackName field exists and is saved |
| UI not displaying trackName | Other sessions (if manually created) would show correct name |
| Dialog not capturing input | HomeViewModel receives correct trackName in event |

---

## Root Cause

**Root Cause**: Navigation arguments for `trackName` were never implemented in the navigation graph. The user's track name is captured correctly but cannot be passed through the navigation flow.

**Trigger**: User starts a new session with a custom track name.

**Contributing Factors**:
1. TODO comment left in code without follow-up
2. No test coverage for trackName propagation
3. Incomplete implementation of navigation flow

---

## Confidence Level

**Overall Confidence**: HIGH (95%)

**Confidence Rationale**:
- Clear code evidence showing missing arguments
- TODO comment explicitly acknowledges the gap
- Straightforward data flow to trace

**Remaining Uncertainty**:
- None significant

---

## Mitigation

### Immediate (User Recovery)
None available — track name cannot be edited after session creation.

### Permanent Fix

**1. Add trackName argument to navigation destinations:**

```xml
<!-- nav_graph.xml: Add to trackSetupFragment -->
<fragment
    android:id="@+id/trackSetupFragment"
    ...>
    <argument
        android:name="trackName"
        app:argType="string"
        android:defaultValue="Unknown Track" />
    ...
</fragment>

<!-- Add to action_home_to_track_setup -->
<action
    android:id="@+id/action_home_to_track_setup"
    app:destination="@id/trackSetupFragment">
    <argument
        android:name="trackName"
        app:argType="string" />
</action>

<!-- Add to recordingFragment destination -->
<argument
    android:name="trackName"
    app:argType="string"
    android:defaultValue="Unknown Track" />

<!-- Add to action_track_setup_to_recording -->
<argument
    android:name="trackName"
    app:argType="string" />
```

**2. Update HomeFragment to pass trackName:**
```kotlin
// HomeFragment.kt
is HomeEvent.NavigateToTrackSetup -> {
    val action = HomeFragmentDirections.actionHomeToTrackSetup(event.trackName)
    findNavController().navigate(action)
}
```

**3. Update TrackSetupFragment to receive and forward trackName:**
```kotlin
// TrackSetupFragment.kt
private val args: TrackSetupFragmentArgs by navArgs()

// When navigating to Recording:
val action = TrackSetupFragmentDirections.actionTrackSetupToRecording(
    sessionId = -1L,
    trackName = args.trackName,  // Forward the trackName
    startLineLat1 = ...,
    ...
)
```

**4. Update RecordingFragment to use trackName from args:**
```kotlin
// RecordingFragment.kt:206
viewModel.createSessionAndStartRecording(
    trackName = args.trackName,  // Use nav arg instead of hardcoded
    startLine = startLine
)
```

---

## Prevention Recommendations

### Code Changes
- [ ] Add `trackName` argument to `trackSetupFragment` in nav_graph.xml
- [ ] Add `trackName` argument to `recordingFragment` in nav_graph.xml
- [ ] Update HomeFragment to pass trackName in navigation
- [ ] Update TrackSetupFragment to receive and forward trackName
- [ ] Update RecordingFragment to use `args.trackName`
- [ ] Remove hardcoded `"Track Session"` fallback

### Testing
- [ ] Add UI test: Enter track name → Complete recording → Verify name persisted
- [ ] Add unit test: TrackSetupFragmentArgs contains trackName

### Process
- [ ] Review all TODO comments before release
- [ ] Add code review checklist item for navigation argument completeness

---

## Files to Modify

| File | Change |
|------|--------|
| `nav_graph.xml` | Add trackName arguments |
| `HomeFragment.kt` | Pass trackName in navigation action |
| `TrackSetupFragment.kt` | Receive and forward trackName |
| `RecordingFragment.kt` | Use args.trackName instead of hardcoded |

---

## Atlas Update Recommendations

**Add to `failure-patterns.md`:**

```markdown
## Pattern: Navigation Argument Not Passed

### Symptoms
- Data entered by user is lost after navigation
- Hardcoded fallback values appear instead of user input
- TODO comments mention "get from nav args"

### Root Cause
Navigation graph action missing required argument definition. Data captured in source Fragment cannot be passed to destination.

### Evidence Checklist
1. Check nav_graph.xml for argument definitions
2. Check source Fragment for argument passing
3. Check destination Fragment for argument receiving
4. Search for hardcoded fallback values with TODO comments

### Mitigation
- Define argument in destination Fragment
- Define argument in navigation action
- Update source to pass argument via SafeArgs
- Update destination to receive via navArgs()
```

---

## Resolution Status

**Status:** ✅ RESOLVED (2026-07-16)

### Fix Applied (v2.3)

| Issue | Fix | File |
|-------|-----|------|
| Missing trackName arg in nav_graph | Added trackName to all destinations | `nav_graph.xml` |
| HomeFragment not passing trackName | Pass `event.trackName` in action | `HomeFragment.kt` |
| TrackSetupFragment not receiving | Added `navArgs()`, forward to Recording | `TrackSetupFragment.kt` |
| RecordingFragment hardcoded fallback | Use `args.trackName` | `RecordingFragment.kt` |

### Files Changed

- `app/src/main/res/navigation/nav_graph.xml`
- `app/src/main/java/com/drivingcoach/ui/home/HomeFragment.kt`
- `app/src/main/java/com/drivingcoach/ui/tracksetup/TrackSetupFragment.kt`
- `app/src/main/java/com/drivingcoach/ui/recording/RecordingFragment.kt`

### Verification

- Build: ✅ SUCCESS
- Tests: ✅ 84 passed
- APK: `releases/DrivingCoach-v2.3-trackname-fix.apk`
