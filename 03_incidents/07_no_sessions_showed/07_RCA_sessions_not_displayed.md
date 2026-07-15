# RCA: Completed Sessions Do Not Appear in Recent Sessions

| Field | Value |
|-------|-------|
| **Incident ID** | 07 |
| **Severity** | High |
| **Confidence** | MEDIUM (65%) |
| **RCA Date** | 2026-07-16 |
| **Analyst** | RCA Engine |

---

## Incident Summary

Completed recording sessions do not appear in the `RECENT SESSIONS` list on the Home screen when running on Android emulator. Users cannot access recorded sessions through normal navigation, blocking the post-session workflow.

---

## Impact

- **Users Affected**: All users on emulator (potentially affects real devices)
- **Duration**: Unknown (discovered during emulator testing)
- **Data Loss**: No data loss — sessions may exist in Room but are not visible in UI
- **Severity**: HIGH — core functionality blocked

---

## Timeline

| Time | Event | Evidence |
|------|-------|----------|
| T+0s | User taps "Start Session" FAB | UI action |
| T+1s | Navigate Home → TrackSetup → Recording | Navigation action |
| T+Ns | Session created in Room | `sessionRepository.createSession()` |
| T+Ns | Recording starts | `TelemetryForegroundService.startRecording()` |
| T+Ns | User stops recording | `stopRecording()` called |
| T+Ns | Laps processed locally | `processLapsLocally()` |
| T+Ns | Navigate Recording → SessionResult | `popUpTo="@id/homeFragment"` |
| T+Ns | User presses back → Home | Expected: session visible |
| T+Ns | **Session NOT visible in Recent Sessions** | **FAILURE POINT** |

---

## Signals Observed

### Present (Expected)
- Navigation flow works correctly
- Session can be viewed in SessionResult screen (user navigates there after recording)

### Present (Unexpected)
- Empty `RECENT SESSIONS` list despite successful recording

### Absent (Expected but Missing)
- Session row in RecyclerView
- Best lap hero card update
- No error messages shown (silent failure)

---

## Systems Involved

| Component | Role in Incident | Reference |
|-----------|------------------|-----------|
| `HomeViewModel` | Loads sessions via Room Flow | components.md |
| `HomeFragment` | Displays session list in RecyclerView | UI layer |
| `SessionDao` | `getAllSessions()` query | Data layer |
| `RecordingViewModel` | Creates session record | Session creation |
| `SessionRepository` | Persists session to Room | Data layer |

---

## Evidence

### Primary Evidence

**1. HomeViewModel loads sessions in `init{}`:**
```kotlin
// HomeViewModel.kt:78
init {
    loadSessions()
}

private fun loadSessions() {
    viewModelScope.launch {
        try {
            sessionDao.getAllSessions().collect { sessions ->
                // ... process sessions
            }
        } catch (e: Exception) {
            _uiState.value = HomeUiState(
                isLoading = false,
                error = e.message ?: "Failed to load sessions"
            )
        }
    }
}
```

**2. Session query does not filter:**
```kotlin
// SessionDao.kt:29
@Query("SELECT * FROM sessions ORDER BY startedAt DESC")
fun getAllSessions(): Flow<List<SessionEntity>>
```

**3. Navigation uses `popUpTo` without destroying Home:**
```xml
<!-- nav_graph.xml:106 -->
<action
    android:id="@+id/action_recording_to_session_result"
    app:destination="@id/sessionResultFragment"
    app:popUpTo="@id/homeFragment">
```

**4. Flow collection uses lifecycle-aware collection:**
```kotlin
// HomeFragment.kt:100
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
            viewModel.uiState.collect { state ->
                updateUI(state)
            }
        }
    }
}
```

### Negative Evidence

- No error logged from `loadSessions()` catch block
- No crash reports
- SessionResult screen can display the session (session exists in Room)

---

## Hypotheses

### Primary Hypothesis: Flow Collection Never Resumes After Navigation

**CLAIM**: When HomeFragment returns to STARTED state after being in the back stack, the `repeatOnLifecycle` collection does not properly re-emit the latest Room data.

**EVIDENCE FOR**:
- `repeatOnLifecycle(Lifecycle.State.STARTED)` pauses collection when Fragment is STOPPED
- Room Flow should emit latest data when collection resumes, but behavior may be inconsistent with fragments kept in back stack
- HomeViewModel survives navigation (scoped to Fragment, Fragment not destroyed)
- No explicit `refresh()` call when returning to Home

**EVIDENCE AGAINST**:
- Room Flows are documented to emit latest data on subscription
- `repeatOnLifecycle` should handle this correctly

**CONFIDENCE**: MEDIUM (55%)

---

### Alternative Hypothesis 1: Nested Suspend Call Exception

**CLAIM**: Exception in `lapDao.getLapsForSession(session.id).first()` causes silent failure for new sessions that have no laps yet.

**EVIDENCE FOR**:
```kotlin
val sessionSummaries = sessions.map { session ->
    val laps = lapDao.getLapsForSession(session.id).first() // Could throw if no laps
    // ...
}
```
- If Flow emits empty list and `.first()` behaves unexpectedly, could cause issues
- Exception would be caught and error state set, but user might navigate away before seeing it

**EVIDENCE AGAINST**:
- Room Flows should handle empty results gracefully
- `.first()` on empty Flow should return empty list, not throw

**CONFIDENCE**: LOW (25%)

---

### Alternative Hypothesis 2: ViewModel Not Recreated

**CLAIM**: HomeViewModel is retained while Home is in back stack, but its coroutine collecting the Flow is cancelled when the Fragment goes to STOPPED state, and never restarts.

**EVIDENCE FOR**:
- `viewModelScope.launch` starts in `init{}`
- If coroutine is cancelled due to lifecycle, it won't restart
- `repeatOnLifecycle` is in Fragment, but `loadSessions()` uses `viewModelScope`

**EVIDENCE AGAINST**:
- `viewModelScope` is tied to ViewModel lifecycle, not Fragment lifecycle
- Coroutine should continue running while ViewModel exists

**CONFIDENCE**: MEDIUM (45%)

---

### Alternative Hypothesis 3: StateFlow Not Emitting to UI

**CLAIM**: `_uiState` StateFlow updates in ViewModel, but Fragment doesn't receive the emission due to lifecycle timing.

**EVIDENCE FOR**:
- StateFlow replays last value to new collectors
- But if collector starts while StateFlow is in middle of update, race condition possible

**EVIDENCE AGAINST**:
- StateFlow is designed to handle this scenario
- Collection is lifecycle-aware

**CONFIDENCE**: LOW (20%)

---

## Eliminated Hypotheses

| Hypothesis | Reason Eliminated |
|------------|-------------------|
| Session not saved to Room | SessionResult screen can display session data |
| Wrong userId filter | `getAllSessions()` has no userId filter |
| Database migration failure | `fallbackToDestructiveMigration()` would wipe DB, not cause partial issues |
| RecyclerView adapter issue | Adapter uses proper DiffUtil and ListAdapter |

---

## Root Cause

**Root Cause**: Suspected lifecycle timing issue between HomeViewModel's Flow collection and HomeFragment's lifecycle-aware UI collection.

**Trigger**: Navigation pattern where Home is kept in back stack while recording completes.

**Contributing Factors**:
1. No explicit refresh mechanism when returning to Home screen
2. Flow collection started in `init{}` (once) vs lifecycle-aware collection in Fragment
3. `repeatOnLifecycle` pauses/resumes may not trigger Room Flow re-emission

---

## Confidence Level

**Overall Confidence**: MEDIUM (65%)

**Confidence Rationale**:
- Cannot reproduce locally (need emulator testing)
- Multiple plausible hypotheses, none definitively proven
- Code path appears correct but timing/lifecycle issues are notoriously subtle

**Remaining Uncertainty**:
- Exact point of failure in data flow
- Whether issue is emulator-specific or affects real devices
- Whether session is saved but not queried, or queried but not displayed

---

## Mitigation

### Immediate (User Recovery)
1. Force-close and reopen the app — ViewModel recreated, `loadSessions()` called fresh
2. Navigate to Profile and back — may trigger state refresh

### Short-term (Prevent Recurrence)
1. **Add explicit refresh on `onResume()`**:
```kotlin
// HomeFragment.kt
override fun onResume() {
    super.onResume()
    viewModel.refreshSessions()
}

// HomeViewModel.kt
fun refreshSessions() {
    // Force re-emission or re-query
}
```

2. **Add logging to diagnose**:
```kotlin
Log.d("HomeViewModel", "Sessions loaded: ${sessions.size}")
Log.d("HomeFragment", "UI updated with ${state.sessions.size} sessions")
```

### Permanent (Systemic Fix)
1. **Refactor to use `stateIn()` with `SharingStarted.WhileSubscribed()`**:
```kotlin
val sessions = sessionDao.getAllSessions()
    .map { sessions -> processToSummaries(sessions) }
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
```

2. **Add pull-to-refresh on Home screen** as explicit user action

3. **Add integration test** for navigation + session visibility scenario

---

## Prevention Recommendations

### Code Changes
- [ ] Add `refreshSessions()` public method to HomeViewModel
- [ ] Call `refreshSessions()` in HomeFragment.onResume()
- [ ] Add logging for session loading at ViewModel and Fragment levels
- [ ] Consider using `stateIn()` pattern for better Flow lifecycle handling

### Monitoring
- [ ] Add analytics event for "sessions loaded" with count
- [ ] Add analytics event for "empty state shown" on Home

### Documentation
- [ ] Document lifecycle considerations for Room Flow + Navigation in Atlas
- [ ] Add this as a failure pattern in `failure-patterns.md`

### Process
- [ ] Add emulator E2E test for: Create session → Stop → Return to Home → Verify visible

---

## Atlas Update Recommendations

**Add to `failure-patterns.md`:**

```markdown
## Pattern: Room Flow Not Updating After Back Navigation

### Symptoms
- List/data not refreshing when returning to screen via back navigation
- Data exists in Room but not visible in UI
- No error shown

### Root Cause
Room Flow collection may not re-emit when Fragment returns from back stack due to lifecycle timing between `repeatOnLifecycle` in Fragment and coroutine launched in ViewModel `init{}`.

### Evidence Checklist
1. Check if ViewModel uses `init{}` to start Flow collection
2. Check if Fragment uses `repeatOnLifecycle` for UI collection
3. Verify navigation uses `popUpTo` keeping Fragment in back stack

### Mitigation
- Add explicit refresh method called in `onResume()`
- Use `stateIn()` with `SharingStarted.WhileSubscribed()`
```
