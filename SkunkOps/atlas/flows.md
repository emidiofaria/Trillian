# flows.md

Runtime execution flows for RCA localization in BMW Driving Coach Android app.

---

## Flow: App Startup & Navigation Resolution

### Goal

Determine correct start destination (Onboarding vs Login vs Home) and initialize app state.

### Trigger

- App launch from launcher
- App restore from background (process death)

### Execution Path

```
Application.onCreate()
→ Hilt injection completes
→ MainActivity.onCreate()
→ DataStore.data.first() [BLOCKING runBlocking]
→ Check KEY_ONBOARDING_COMPLETE preference
→ NavController.setStartDestination()
  → If onboarding incomplete: OnboardingFragment
  → If onboarding complete: default nav_graph (LoginFragment or HomeFragment)
→ observeAuthEvents() starts collecting AuthEventBus
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| DataStore read | `runBlocking` (blocks main thread) | `MainActivity.setupNavigation()` |
| AuthEventBus collection | `lifecycleScope.launch` | `MainActivity.observeAuthEvents()` |

### Persistence Boundaries

| Storage | Key | Purpose |
|---------|-----|---------|
| DataStore | `onboarding_complete` | Skip onboarding on subsequent launches |
| DataStore | `jwt_token` | Determines auth state |

### External Dependencies

None (local-only flow).

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| DataStore read | Corruption | `runBlocking` exception → crash | App won't start |
| DataStore read | Slow disk I/O | ANR (main thread blocked) | User sees "App not responding" |
| NavController | Invalid graph | `IllegalStateException` | Crash |

### Retry/Recovery Behavior

- **No retry**: `runBlocking` either succeeds or crashes
- **DataStore fallback**: Returns empty preferences on corruption → treated as fresh install
- **Recovery**: Reinstall clears corrupted DataStore

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| Black screen on launch | DataStore ANR |
| Crash on launch | NavController misconfiguration |
| Unexpected onboarding | DataStore corruption (preference lost) |

### Operational Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| `MainActivity onCreate` | Logcat | Startup initiated |
| ANR trace | `/data/anr/` | Main thread blocked >5s |
| `DataStore` exceptions | Logcat | Preference read failure |

---

## Flow: Onboarding & Permission Grant

### Goal

Obtain required runtime permissions (location, activity recognition) before app use.

### Trigger

- First app launch (onboarding incomplete)
- Return from Settings app

### Execution Path

```
OnboardingFragment.onViewCreated()
→ updatePermissionStatus() [check current grants]
→ User taps "Grant Permissions"
→ permissionLauncher.launch(requiredPermissions)
→ [ASYNC] System permission dialog shown
→ handlePermissionResults(permissions)
  → If all granted: completeOnboarding()
  → If denied: showDeniedState()
→ completeOnboarding()
  → DataStore.edit { KEY_ONBOARDING_COMPLETE = true }
  → NavController.navigate(action_onboarding_to_home)
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| Permission dialog | `ActivityResultContracts` callback | `permissionLauncher` |
| DataStore write | `lifecycleScope.launch` | `completeOnboarding()` |

### Persistence Boundaries

| Storage | Key | Purpose |
|---------|-----|---------|
| DataStore | `onboarding_complete` | Marks onboarding as done |

### External Dependencies

- Android permission system
- System Settings app (for manual grant)

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| Permission request | User denies | `showDeniedState()` shown | User stuck on onboarding |
| Permission request | "Don't ask again" | Settings button shown | User must grant in Settings |
| DataStore write | Disk full | Exception | Onboarding loops infinitely |
| Navigation | Fragment already detached | `IllegalStateException` | Crash |

### Retry/Recovery Behavior

- **Implicit retry**: `onResume()` re-checks permissions (user may grant in Settings)
- **Manual recovery**: "Open Settings" button for permanently denied permissions
- **No silent failure**: User cannot proceed without permissions

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| Stuck on onboarding | Permissions denied, user doesn't open Settings |
| Onboarding shows again next launch | DataStore write failed |
| Permission dialog doesn't appear | Already permanently denied |

### Operational Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| Permission grant/deny | System logs | Permission decision |
| `onboarding_complete = true` | DataStore | Successful completion |
| Navigation action | NavController logs | Screen transition |

---

## Flow: Session Recording (Telemetry Capture)

### Goal

Capture GPS + IMU telemetry at 10Hz during a driving session, persist to local file.

### Trigger

- User taps "Start Session" FAB on HomeFragment
- User enters track name in dialog

### Execution Path

```
HomeFragment: User taps FAB
→ showTrackNameDialog()
→ User enters track name, taps "Start"
→ HomeViewModel.startNewSession(trackName)
  → [ROOM WRITE] sessionDao.insertSession(SessionEntity)
  → Returns sessionId
  → [ASYNC] startForegroundService(ACTION_START_RECORDING, sessionId)
  → [EVENT] _events.emit(NavigateToRecording)
→ RecordingFragment created with sessionId arg
→ checkPermissionsAndStart()
  → If permissions OK: viewModel.startRecording(sessionId)
→ RecordingViewModel.startRecording()
  → TelemetryForegroundService.startRecording(context, sessionId)
  → bindToService()
→ TelemetryForegroundService.startRecording()
  → Create TelemetryFileWriter(sessionId)
  → startForeground(notification)
  → locationManager.requestLocationUpdates(GPS_PROVIDER, 100ms)
  → sensorManager.registerListener(accelerometer, FASTEST)
  → sensorManager.registerListener(gyroscope, FASTEST)
  → handler.post(notificationUpdateRunnable)
  → _state.value = Recording(...)
→ [CONTINUOUS] onLocationChanged(location)
  → Create TelemetrySample with GPS + buffered IMU
  → [ASYNC IO] telemetryWriter.writeSample(sample)
→ User taps "Stop"
→ RecordingViewModel.stopRecording()
  → TelemetryForegroundService.stopRecording(context)
→ TelemetryForegroundService.stopRecording()
  → _state.value = Stopping
  → locationManager.removeUpdates()
  → sensorManager.unregisterListener()
  → [ASYNC IO] telemetryWriter.close()
  → [ROOM WRITE] sessionDao.updateSessionEndTime()
  → [WORKMANAGER] WorkManager.enqueue(TelemetryUploadWorker)
  → _state.value = Idle
  → stopSelf()
→ RecordingFragment observes Stopping state
  → navigateToSessionResult(sessionId)
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| Session insert | `viewModelScope.launch` | `HomeViewModel.startNewSession()` |
| Service start | `startForegroundService()` | Cross-process async |
| Location updates | `LocationListener` callback | `onLocationChanged()` |
| Sensor updates | `SensorEventListener` callback | `onSensorChanged()` |
| File writes | `Dispatchers.IO` coroutine | `TelemetryFileWriter.writeSample()` |
| Service binding | `ServiceConnection` callback | `RecordingViewModel` |
| State observation | `StateFlow.collectLatest` | `RecordingFragment` |
| Worker enqueue | WorkManager | `TelemetryUploadWorker` |

### Persistence Boundaries

| Storage | Data | Trigger |
|---------|------|---------|
| Room `sessions` | SessionEntity | Session start |
| Room `sessions.endedAt` | Timestamp | Session stop |
| File `telemetry/session_{id}.jsonl` | TelemetrySamples | Each GPS update |

### External Dependencies

- GPS hardware (via `LocationManager`)
- Accelerometer sensor
- Gyroscope sensor
- Android foreground service runtime

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| Room insert | DB locked | Exception, no navigation | Session not created |
| Service start | ForegroundServiceStartNotAllowedException | Crash (Android 12+) | Recording fails |
| GPS request | Permission revoked | `RecordingState.Error` | User sees error, pops back |
| GPS lock | No satellites | `gpsLocked = false` for 5s+ | Warning in notification |
| GPS signal lost | Tunnel/garage | Notification shows "GPS lost" | Samples stop; auto-recovers |
| Sensor unavailable | Hardware missing | `null` sensor | IMU data all zeros |
| File write | Disk full | Exception logged, sample lost | Data gaps in recording |
| File write | Writer closed | Warning logged | Samples dropped |
| Service killed | Memory pressure | Service dies | Recording ends unexpectedly |
| Room update | DB error | End time not saved | Session appears incomplete |
| WorkManager | Enqueue fails | Upload never starts | Manual retry needed |

### Retry/Recovery Behavior

| Mechanism | Behavior |
|-----------|----------|
| GPS lock timeout | 5s timeout logged; continues waiting |
| GPS signal lost | 10s threshold; auto-recovers when signal returns |
| File write | No retry; sample lost |
| Service restart | `START_STICKY` may restart service (but recording state lost) |
| WorkManager | Separate retry logic (see Upload flow) |

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| "GPS ACQUIRING..." never changes | Indoor location, poor sky view |
| "GPS signal lost" notification | Tunnel, covered parking |
| Recording stops unexpectedly | Service killed by system |
| Recording button unresponsive | Service binding failed |
| Elapsed time frozen | State flow not emitting |

### Operational Signals

| Signal | Tag | Meaning |
|--------|-----|---------|
| "Starting recording for session: X" | `TelemetryService` INFO | Recording begun |
| "GPS locked" | `TelemetryService` INFO | First GPS fix |
| "GPS lock timeout" | `TelemetryService` WARN | No fix within 5s |
| "GPS signal lost" | `TelemetryService` WARN | 10s without sample |
| "Error writing telemetry sample" | `TelemetryFileWriter` ERROR | Write failure |
| "Recording stopped and saved" | `TelemetryService` INFO | Clean stop |
| "Service destroyed" | `TelemetryService` DEBUG | Service lifecycle end |
| Notification channel `bmw_recording` | System | Foreground service active |

---

## Flow: Telemetry Upload (Background Sync)

### Goal

Upload recorded telemetry JSONL file to backend for processing. Persist upload status.

### Trigger

- Recording stops → WorkManager enqueue
- Network becomes available (constraint satisfied)
- WorkManager retry (after failure)

### Execution Path

```
TelemetryForegroundService.stopRecording()
→ WorkManager.enqueue(TelemetryUploadWorker.buildRequest(sessionId))

[ASYNC - WorkManager schedules when CONNECTED]

TelemetryUploadWorker.doWork()
→ Extract sessionId from inputData
→ Check runAttemptCount > MAX_RETRIES (5)
  → If exceeded: updateStatus(FAILED), return Result.failure()
→ [ROOM READ] sessionDao.getSessionByIdSync(sessionId)
  → If null: return Result.failure()
  → If already DONE: return Result.success()
→ Get telemetry file from session.rawFilePath
  → If not exists: updateStatus(FAILED), return Result.failure()
→ [ROOM WRITE] updateStatus(UPLOADING)
→ Create MultipartBody.Part from file
→ [NETWORK] telemetryApiService.uploadSession(sessionId, multipartBody)
  → If 2xx: 
    → [ROOM WRITE] updateUploadStatus(DONE, remoteSessionId)
    → return Result.success()
  → If 4xx:
    → [ROOM WRITE] updateStatus(FAILED)
    → return Result.failure() [no retry]
  → If 5xx:
    → [ROOM WRITE] updateStatus(FAILED)
    → return Result.retry()
→ catch Exception:
  → [ROOM WRITE] updateStatus(FAILED)
  → return Result.retry()
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| Work scheduling | WorkManager | Constraint-based execution |
| Network call | `suspend fun` | `TelemetryApiService.uploadSession()` |
| DB operations | `suspend fun` | `SessionDao` methods |

### Persistence Boundaries

| Storage | Data | Trigger |
|---------|------|---------|
| Room `sessions.uploadStatus` | PENDING → UPLOADING → DONE/FAILED | Status transitions |
| Room `sessions.remoteSessionId` | Server-assigned ID | Successful upload |
| File `telemetry/session_{id}.jsonl` | Telemetry data | Read for upload |

### External Dependencies

| Service | Endpoint | Purpose |
|---------|----------|---------|
| Backend API | `POST /api/v1/sessions/{id}/telemetry` | Upload telemetry file |

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| WorkManager | Not initialized | Crash | App broken |
| Session lookup | Not found | `Result.failure()` | Upload abandoned |
| File read | File deleted | `Result.failure()` + FAILED status | Upload permanently failed |
| Network | Timeout (60s) | `SocketTimeoutException` | Retry with backoff |
| Network | No connectivity | Worker not scheduled | Waits for network |
| API | 401 Unauthorized | 4xx → no retry | Requires re-auth |
| API | 413 Payload Too Large | 4xx → no retry | File too big |
| API | 500 Server Error | 5xx → retry | Retry up to 5x |
| API | 503 Service Unavailable | 5xx → retry | Retry up to 5x |
| Max retries | 5 attempts exceeded | FAILED status | Manual retry needed |

### Retry/Recovery Behavior

| Mechanism | Behavior |
|-----------|----------|
| Exponential backoff | Base 10s, exponential increase |
| Max retries | 5 attempts before permanent failure |
| Network constraint | Only runs when `NetworkType.CONNECTED` |
| 4xx errors | No retry (client error) |
| 5xx errors | Retry with backoff |
| Network exceptions | Retry with backoff |
| Manual retry | `SessionResultViewModel.retryAnalysis()` re-enqueues worker |

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| Session stuck on "Uploading" | Worker running, slow network |
| Session shows "Upload Failed" | Max retries exceeded or 4xx error |
| No coaching insights | Upload not complete |
| Stale upload banner on Home | Session pending >5 minutes |

### Operational Signals

| Signal | Tag | Meaning |
|--------|-----|---------|
| "Starting upload for session: X, attempt: N" | `TelemetryUploadWorker` INFO | Worker execution |
| "Upload successful for session: X, remoteId: Y" | `TelemetryUploadWorker` INFO | Success |
| "Client error 4XX for session: X" | `TelemetryUploadWorker` ERROR | Client error |
| "Server error 5XX for session: X, will retry" | `TelemetryUploadWorker` WARN | Server error |
| "Max retries exceeded for session: X" | `TelemetryUploadWorker` ERROR | Gave up |
| "Network error uploading session: X" | `TelemetryUploadWorker` ERROR | Network failure |
| WorkManager state | `WorkManager.getWorkInfosByTag("telemetry_upload")` | Job status |

---

## Flow: Session Result Display & Polling

### Goal

Display lap times and coaching insights; poll for backend processing completion.

### Trigger

- Navigation from RecordingFragment after stop
- User taps session in history list

### Execution Path

```
SessionResultFragment created with sessionId arg
→ SessionResultViewModel.init
  → loadSession()
→ [FLOW] combine(sessionDao.getSessionById, lapDao.getLapsForSession, coachingInsightDao.getInsightsForSession)
  → Emit SessionUiState
  → Check processingStatus
    → If PENDING/UPLOADING/DETECTING_LAPS/GENERATING_COACHING: startPolling()
    → If COMPLETE/FAILED: stopPolling()
→ [CONTINUOUS POLLING] every 5s while processing
  → refreshSession() [placeholder for remote sync]
  → Room Flow auto-updates when data changes
→ UI observes uiState and renders tabs (Laps, Coach, Chart)
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| Room queries | `Flow<T>` | DAOs |
| Flow combine | `viewModelScope.launch` | `loadSession()` |
| Polling loop | `delay(5000)` in coroutine | `startPolling()` |

### Persistence Boundaries

| Storage | Data | Purpose |
|---------|------|---------|
| Room `sessions` | Session metadata | Display header |
| Room `laps` | Lap times | Display lap list |
| Room `coaching_insights` | AI insights | Display coach tab |

### External Dependencies

| Service | Endpoint | Purpose |
|---------|----------|---------|
| Backend API | `GET /sessions/{id}` | Fetch processed data (future) |

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| Room query | Session not found | `session = null` | Empty UI |
| Flow combine | Exception | `error` state | Error message shown |
| Polling | Coroutine cancelled | Polling stops | Expected on navigate away |

### Retry/Recovery Behavior

| Mechanism | Behavior |
|-----------|----------|
| Room Flow | Auto-updates on DB changes |
| Polling | 5s interval while processing |
| Manual retry | `retryAnalysis()` resets status + re-enqueues upload |
| Lifecycle | Polling cancelled on `onCleared()` |

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| Spinner/loading forever | Processing stuck on backend |
| Empty laps list | Laps not yet detected |
| Empty coaching tab | Insights not yet generated |
| "Failed" status | Backend processing failed |

### Operational Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| `processingStatus` values | Room `sessions` table | Processing progress |
| Polling job active | ViewModel coroutine | Waiting for completion |
| `retryAnalysis()` called | Logcat (if instrumented) | User requested retry |

---

## Flow: Authentication (Login)

### Goal

Authenticate user with email/password, obtain JWT, persist session.

### Trigger

- User taps "Login" on LoginFragment
- App detects expired session (future)

### Execution Path

```
LoginFragment: User enters credentials, taps Login
→ [NOT IMPLEMENTED - skip button exists]
→ Expected flow:
  LoginViewModel.login(email, password)
  → AuthRepository.login(email, password)
    → [NETWORK] apiService.login(LoginRequest)
      → If 2xx:
        → [DATASTORE WRITE] saveToken(token, userId)
        → [NETWORK] fetchAndSaveUserProfile()
          → apiService.getMe()
          → [DATASTORE WRITE] saveUserProfile(displayName, email)
        → return AuthResult.Success
      → If 401:
        → return AuthResult.Error("Invalid credentials", 401)
      → If network error:
        → return AuthResult.Error("Network error")
  → UI observes result
    → If Success: navigate to Home
    → If Error: show error message
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| API calls | `suspend fun` | `ApiService.login()`, `getMe()` |
| DataStore writes | `suspend fun` | `DataStore.edit()` |
| ViewModel scope | `viewModelScope.launch` | Login action |

### Persistence Boundaries

| Storage | Key | Purpose |
|---------|-----|---------|
| DataStore | `jwt_token` | Auth token |
| DataStore | `user_id` | User identifier |
| DataStore | `user_name` | Display name |
| DataStore | `user_email` | Email |

### External Dependencies

| Service | Endpoint | Purpose |
|---------|----------|---------|
| Backend API | `POST /auth/login` | Authenticate |
| Backend API | `GET /auth/me` | Fetch profile |

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| Network | Timeout | `AuthResult.Error("Network error")` | Error shown |
| API | 401 | `AuthResult.Error("Invalid credentials")` | Error shown |
| API | 5xx | `AuthResult.Error` | Error shown |
| DataStore | Write failure | Token not persisted | Auth lost on restart |
| Profile fetch | Failure | Silently ignored | Profile data missing |

### Retry/Recovery Behavior

| Mechanism | Behavior |
|-----------|----------|
| No automatic retry | User must re-submit |
| Profile fetch | Failure silently ignored (non-critical) |
| Skip button | Demo mode bypasses auth |

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| Login button unresponsive | Network slow |
| "Invalid credentials" | Wrong email/password |
| "Network error" | No connectivity |
| Logged out on restart | Token not persisted |

### Operational Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| HTTP 200 `/auth/login` | OkHttp logs | Successful auth |
| HTTP 401 `/auth/login` | OkHttp logs | Bad credentials |
| `jwt_token` written | DataStore | Session established |

---

## Flow: Session Expiration Handling

### Goal

Detect 401 responses, clear invalid token, redirect user to login.

### Trigger

- Any authenticated API call returns 401

### Execution Path

```
[Any API call via OkHttp]
→ AuthInterceptor.intercept(chain)
  → Attach Bearer token from DataStore
  → chain.proceed(request)
  → If response.code == 401:
    → [DATASTORE WRITE] Clear jwt_token
    → [EVENT] authEventBus.emitSessionExpired()
→ [ASYNC] MainActivity observes AuthEventBus.events
  → handleSessionExpired()
    → Snackbar.show("Session expired — please log in again")
    → navController.navigate(loginFragment, popUpTo inclusive)
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| Interceptor | `runBlocking` | Token read/clear |
| Event bus | `SharedFlow` | Session expired event |
| Event collection | `lifecycleScope.launch` | MainActivity |

### Persistence Boundaries

| Storage | Key | Action |
|---------|-----|--------|
| DataStore | `jwt_token` | Cleared on 401 |

### External Dependencies

- Any authenticated backend endpoint

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| DataStore clear | Exception | Token remains | Repeated 401s |
| Event emission | Buffer overflow | Event dropped | No redirect |
| Navigation | Already destroyed | Crash | App unstable |

### Retry/Recovery Behavior

| Mechanism | Behavior |
|-----------|----------|
| Automatic token clear | Prevents infinite 401 loop |
| Navigation | Forces re-login |
| No retry | Original API call fails |

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| Snackbar "Session expired" | 401 detected |
| Forced to login screen | Navigation action |
| API errors | Until re-authenticated |

### Operational Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| HTTP 401 | OkHttp logs | Token expired/invalid |
| `AuthEvent.SessionExpired` | Event bus | Expiration detected |
| Navigation to login | NavController | Redirect triggered |

---

## Flow: Sign Out

### Goal

Clear all user data and redirect to login screen.

### Trigger

- User taps "Sign Out" on ProfileFragment

### Execution Path

```
ProfileFragment: User taps Sign Out
→ ProfileViewModel.signOut()
  → [DATASTORE WRITE] dataStore.edit { remove jwt, user_name, user_email }
  → [ROOM] database.clearAllTables()
  → [EVENT] _events.emit(ProfileEvent.NavigateToLogin)
→ ProfileFragment observes event
  → navController.navigate(loginFragment)
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| DataStore clear | `viewModelScope.launch` | `signOut()` |
| Room clear | suspend | `clearAllTables()` |
| Event emission | `SharedFlow` | Navigation event |

### Persistence Boundaries

| Storage | Action |
|---------|--------|
| DataStore | All auth keys removed |
| Room | All tables cleared |

### External Dependencies

None (local-only).

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| DataStore clear | Exception | Error shown | Sign out fails |
| Room clear | Exception | Error shown | Partial data remains |
| Navigation | Exception | Error shown | User stuck |

### Retry/Recovery Behavior

| Mechanism | Behavior |
|-----------|----------|
| No retry | User must tap again |
| Error handling | `ShowError` event emitted |

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| "Failed to sign out" error | DataStore/Room exception |
| Redirected to login | Successful sign out |

### Operational Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| DataStore keys removed | DataStore | Credentials cleared |
| Room tables empty | Database | Local data cleared |
| `NavigateToLogin` event | ViewModel | Sign out complete |

---

## Flow: Home Screen Data Load

### Goal

Display session history, best lap stats, and stale upload warnings.

### Trigger

- HomeFragment created
- HomeViewModel init

### Execution Path

```
HomeViewModel.init
→ loadSessions()
  → [FLOW] sessionDao.getAllSessionsForUser(userId).collect
    → For each session:
      → [FLOW] lapDao.getLapsForSession(session.id).first()
      → Compute bestLap, consistencyScore
      → Build SessionSummary
    → [ROOM READ] sessionDao.getStaleUploadSessions(threshold)
    → Emit HomeUiState
→ HomeFragment observes uiState
  → Update hero card (best lap)
  → Update RecyclerView (session list)
  → Show/hide stale upload banner
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| Session flow | `Flow<List<SessionEntity>>` | Room |
| Lap queries | `Flow<List<LapEntity>>.first()` | Nested suspend |
| State collection | `repeatOnLifecycle` | Fragment |

### Persistence Boundaries

| Storage | Data | Purpose |
|---------|------|---------|
| Room `sessions` | Session list | History display |
| Room `laps` | Lap times | Stats computation |

### External Dependencies

None (local-only).

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| Room query | Exception | `error` state | Error message shown |
| Nested lap query | Exception | Session skipped | Partial data |

### Retry/Recovery Behavior

| Mechanism | Behavior |
|-----------|----------|
| Room Flow | Auto-updates on DB changes |
| Error handling | Error message in UI state |

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| Empty session list | No sessions or query error |
| "Failed to load" error | Room exception |
| Stale upload banner | Sessions pending >5 min |

### Operational Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| Session count | UI state | Data loaded |
| `hasStaleUploads = true` | UI state | Upload issues |

---

## Summary: Critical Flow Dependencies

```
┌─────────────────┐
│   App Startup   │
└────────┬────────┘
         │ DataStore read
         ▼
┌─────────────────┐     ┌─────────────────┐
│   Onboarding    │────▶│      Login      │
└────────┬────────┘     └────────┬────────┘
         │ Permissions           │ JWT
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│      Home       │◀────│  Session Load   │
└────────┬────────┘     └─────────────────┘
         │ Start Session
         ▼
┌─────────────────┐
│    Recording    │ GPS + IMU + File Write
└────────┬────────┘
         │ Stop
         ▼
┌─────────────────┐
│  Upload Worker  │ WorkManager + API
└────────┬────────┘
         │ Complete
         ▼
┌─────────────────┐
│ Session Result  │ Room + Polling
└─────────────────┘
```

### Cross-Flow Failure Propagation

| Upstream Failure | Downstream Impact |
|------------------|-------------------|
| DataStore corruption | Startup crash, auth lost |
| Permission denied | Recording impossible |
| GPS unavailable | No telemetry data |
| File write failure | Data gaps, upload may fail |
| Upload failure | No coaching insights |
| 401 on any API | Session expired, re-login |
| Room corruption | All local data lost |
