# flows.md

Runtime execution flows for RCA localization in Driving Coach Android app.

---

## Flow: App Startup & Navigation Resolution

### Goal

Show the branded loading screen, resolve startup state off the main thread, and hand the
user to the correct destination (Onboarding vs Login vs Home).

> **Changed in v2.8** — the former `runBlocking` DataStore read in
> `MainActivity.setupNavigation()` has been removed. Startup state is now resolved
> asynchronously by `SplashViewModel` while the branded loading screen is visible.

### Trigger

- App launch from launcher
- App restore from background (process death)

### Execution Path

```
Application.onCreate()
→ Hilt injection completes
→ MainActivity.onCreate()
  → installSplashScreen()            [system splash, Android 12+ handoff]
  → super.onCreate()
  → NavController starts at splashFragment (static start destination)
→ SplashFragment.onViewCreated()
  → SplashViewModel.start()          [idempotent]
    → withTimeoutOrNull(timeoutMs = 8000)          [essential]
      → publish  0%  "preferences"
      → withContext(IO) { dataStore.data.first() }       → 25%
      → withTimeoutOrNull(warmUpTimeoutMs = 2000)  [optional, non-fatal]
          { withContext(IO) { sessionDao.getPendingUploadSessions() } }  → 55%
      → evaluate pending uploads                          → 80%
      → resolve destination                               → 100%
    → awaitMinimumDisplay(minDisplayMs = 1200)  [skippable by tap]
    → emit UiState(destination = …)
→ SplashFragment observes destination
→ NavController.navigate(action, popUpTo splashFragment inclusive)
  → onboarding incomplete       : OnboardingFragment
  → onboarding complete + token : HomeFragment
  → otherwise                   : LoginFragment
→ observeAuthEvents() starts collecting AuthEventBus
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| DataStore read | `withContext(@IoDispatcher)` | `SplashViewModel.resolveStartupState()` |
| Room warm-up / pending uploads | `withContext(@IoDispatcher)` | `SplashViewModel.resolveStartupState()` |
| Essential init timeout | `withTimeoutOrNull(timeoutMs)` | `SplashViewModel.start()` |
| Optional warm-up timeout | `withTimeoutOrNull(warmUpTimeoutMs)` | `SplashViewModel.resolveStartupState()` |
| Minimum brand display | `delay()` loop in `viewModelScope` | `SplashViewModel.awaitMinimumDisplay()` |
| AuthEventBus collection | `lifecycleScope.launch` | `MainActivity.observeAuthEvents()` |

**Main thread is never blocked during startup.**

### Persistence Boundaries

| Storage | Key | Purpose |
|---------|-----|---------|
| DataStore | `onboarding_complete` | Skip onboarding on subsequent launches |
| DataStore | `jwt_token` | Determines auth state |
| Room | `sessions` (pending uploads) | Warm-up + Home upload banner seed |

### External Dependencies

None (local-only flow).

### Timing Contract

| Parameter | Default | Injected via | Purpose |
|-----------|---------|--------------|---------|
| `minDisplayMs` | 1200 ms | `SplashTimings` (`SplashModule`) | Brand moment; set to 0 in tests |
| `timeoutMs` | 8000 ms | `SplashTimings` (`SplashModule`) | Ceiling on the **essential** preferences read |
| `warmUpTimeoutMs` | 2000 ms | `SplashTimings` (`SplashModule`) | Ceiling on the **optional** Room warm-up |
| `PROGRESS_TICK_MS` | 60 ms | `SplashViewModel` constant | Progress bar smoothness |

### Automated Verification (L2)

| Stage of this flow | Instrumented test | SRS |
|--------------------|-------------------|-----|
| Branded screen rendered, progress determinate | `SplashScreenTest` | UI-01 |
| Minimum hold honoured, tap-to-skip | `SplashScreenTest` | UI-02 |
| Essential timeout → Onboarding (never Login) | `SplashFallbackTest` | UI-03 |
| Main thread stays responsive while init stalls | `SplashMainThreadTest` | UI-04 |
| Splash popped inclusively; Back exits the app | `StartupBackStackTest` | UI-05 |
| Home hero collapse/expand after handoff | `HomeHeroTest` | UI-06 |

These tests drive the real `MainActivity` and substitute only `SplashModule` /
`DataStoreModule`, so the navigation graph and fragment lifecycle exercised are the
production ones.

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| DataStore read | Corruption | Empty preferences returned | Treated as fresh install → Onboarding |
| DataStore read | Slow disk I/O | Progress bar stalls at 25% | Bounded by 8 s timeout → Onboarding fallback |
| Room open | Migration failure / stall | Warm-up returns null | Non-fatal: `pendingUploadCount = 0`, destination unaffected |
| Init overall | Exceeds `timeoutMs` | `usedFallback = true` | Navigates to Onboarding, never hangs |
| NavController | Double navigation | `IllegalArgumentException` | Guarded by `currentDestination` check |

### Retry/Recovery Behavior

- **Timeout fallback**: `withTimeoutOrNull` → `SplashDestination.ONBOARDING`, `usedFallback = true`.
  Onboarding is chosen over Login deliberately: the two mistakes are not symmetric. Re-running
  onboarding for an already-onboarded user is a recoverable annoyance that still ends at Home,
  whereas dropping a *fresh* user at Login skips permission granting and leaves the app unable
  to record. The user is never stranded on the loading screen.
- **DataStore fallback**: Returns empty preferences on corruption → treated as fresh install.
- **Skip**: Tapping the splash sets `skipRequested`, which only shortens the cosmetic hold.
  Real initialisation must still complete — the app never navigates to a blind destination.
- **Back stack**: All three navigation actions use `popUpTo="@id/splashFragment"` with
  `popUpToInclusive="true"`, so Back from the first real screen exits the app.

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| Loading screen sits at 25% then jumps to Onboarding | DataStore slow → timeout fallback |
| Unexpected onboarding | DataStore corruption (preference lost) |
| Double splash (icon flash then brand screen) | `installSplashScreen()` not called before `super.onCreate()` |
| Back returns to loading screen | `popUpToInclusive` missing on a nav action |

### Operational Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| `MainActivity onCreate` | Logcat | Startup initiated |
| `UiState.progress` plateau | UI | Which init step is slow |
| `UiState.usedFallback == true` | UI state / test assert | Essential init exceeded its 8 s budget |
| `D/SplashViewModel: datastore read took Nms` | Logcat | Per-step startup timing |
| `D/SplashViewModel: room warm-up took Nms` | Logcat | Warm-up cost; `result=null` means it was cut short |
| `D/SplashViewModel: startup resolved=X in Nms` | Logcat | Final destination and total budget used |
| ANR trace | `/data/anr/` | Should no longer occur for startup (see failure-patterns) |

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

## Flow: Track Setup (Start Line Capture)

### Goal

Capture two GPS points defining the start/finish line before recording, enabling precise lap detection.

### Trigger

- User taps "Start Session" FAB on HomeFragment
- User enters track name → navigates to TrackSetupFragment

### Execution Path

```
HomeFragment: User taps FAB
→ showTrackNameDialog()
→ User enters track name, taps "Start"
→ HomeViewModel.startNewSession(trackName)
  → _events.emit(NavigateToTrackSetup(trackName))
→ HomeFragment observes event
  → findNavController().navigate(actionHomeToTrackSetup)
→ TrackSetupFragment.onViewCreated()
  → fusedLocationClient = LocationServices.getFusedLocationProviderClient()
  → checkPermissionsAndStart()
→ startLocationUpdates()
  → fusedLocationClient.requestLocationUpdates(PRIORITY_HIGH_ACCURACY, 1000ms)
→ [CONTINUOUS] locationCallback.onLocationResult()
  → TrackSetupViewModel.updateGpsStatus(accuracy, satelliteCount)
→ User walks to track edge A, taps "Capture A"
  → TrackSetupViewModel.setPointA(location)
→ User walks to track edge B, taps "Capture B"
  → TrackSetupViewModel.setPointB(location)
  → Calculate distance via GeoUtils.haversineDistance()
  → If distance >= 3m: isValid = true
→ User taps "Start Recording"
  → viewModel.getStartLineCoords() returns StartLineCoords(lat1, lng1, lat2, lng2)
  → Navigate to RecordingFragment with start line args
→ RecordingFragment.startRecordingIfReady()
  → sessionId == -1L (new session)
  → viewModel.createSessionAndStartRecording(trackName, startLine)
    → [ROOM WRITE] sessionRepository.createSession(SessionEntity with startLine coords)
    → viewModel.startRecording(newSessionId)
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| Location updates | `LocationCallback` | `onLocationResult()` |
| State observation | `StateFlow.collectLatest` | `TrackSetupFragment` |
| Session creation | `viewModelScope.launch` | `RecordingViewModel` |

### Persistence Boundaries

| Storage | Data | Trigger |
|---------|------|---------|
| Room `sessions` | SessionEntity + 4 start line coords | Navigation to recording |

### External Dependencies

- GPS hardware (via `FusedLocationProviderClient`)
- Google Play Services Location API

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| GPS acquisition | No location updates | "Acquiring GPS..." indefinitely | Buttons disabled |
| Permission | Denied | Snackbar, navigate back | Flow blocked |
| Points too close | Distance < 3m | "Minimum 3m required" hint | Cannot proceed |

### Operational Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| GPS accuracy | `TrackSetupState.gpsAccuracy` | Location precision |
| Satellite count | `TrackSetupState.satelliteCount` | GPS fix quality |
| Distance | `TrackSetupState.distance` | Line width validation |
| Point capture | `pointA`/`pointB` non-null | User action completed |

---

## Flow: Session Recording (Telemetry Capture)

### Goal

Capture GPS + IMU telemetry at 10Hz during a driving session, persist to local file.

### Trigger

- User taps "Start Session" FAB on HomeFragment
- User enters track name in dialog
- User completes Track Setup (captures start line)

### Execution Path

```
HomeFragment: User taps FAB
→ showTrackNameDialog()
→ User enters track name, taps "Start"
→ HomeViewModel.startNewSession(trackName)
  → _events.emit(NavigateToTrackSetup(trackName))
→ [Track Setup Flow - see above]
→ RecordingFragment receives start line coords via Safe Args
→ RecordingViewModel.createSessionAndStartRecording(trackName, startLine)
  → [ROOM WRITE] sessionRepository.createSession(SessionEntity with startLine)
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
  → fusedLocationClient.requestLocationUpdates(locationRequest, 1000ms)
  → sensorManager.registerListener(accelerometer, FASTEST)
  → sensorManager.registerListener(gyroscope, FASTEST)
  → handler.post(notificationUpdateRunnable)
  → _state.value = Recording(...)
→ [CONTINUOUS] onLocationResult(locationResult)
  → Create TelemetrySample with GPS + buffered IMU
  → [ASYNC IO] telemetryWriter.writeSample(sample)
→ User taps "Stop"
→ RecordingViewModel.stopRecording()
  → TelemetryForegroundService.stopRecording(context)
→ TelemetryForegroundService.stopRecording()
  → _state.value = Stopping
  → fusedLocationClient.removeLocationUpdates()
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
| Session insert | `viewModelScope.launch` | `RecordingViewModel.createSessionAndStartRecording()` |
| Service start | `startForegroundService()` | Cross-process async |
| Location updates | `LocationCallback` | `onLocationResult()` |
| Sensor updates | `SensorEventListener` callback | `onSensorChanged()` |
| File writes | `Dispatchers.IO` coroutine | `TelemetryFileWriter.writeSample()` |
| Service binding | `ServiceConnection` callback | `RecordingViewModel` |
| State observation | `StateFlow.collectLatest` | `RecordingFragment` |
| Worker enqueue | WorkManager | `TelemetryUploadWorker` |

### Persistence Boundaries

| Storage | Data | Trigger |
|---------|------|---------|
| Room `sessions` | SessionEntity + start line coords | Session start |
| Room `sessions.endedAt` | Timestamp | Session stop |
| File `telemetry/session_{id}.jsonl` | TelemetrySamples | Each GPS update |

### External Dependencies

- GPS hardware (via `FusedLocationProviderClient`)
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
| Notification channel `drivingcoach_recording` | System | Foreground service active |

---

## Flow: Local Lap Detection (Offline)

### Goal

Detect lap boundaries from telemetry file immediately after recording stops, without requiring network connectivity. Provides instant lap times on Session Result screen.

### Trigger

- `RecordingViewModel.stopRecording()` completes
- Before service `stopSelf()` call

### Execution Path

```
RecordingViewModel.stopRecording()
→ serviceBinder?.stopRecording(sessionId) // service saves file
→ [AWAIT] stoppingJob.join()
→ processLapsLocally(sessionId)
  → sessionRepository.getSessionById(sessionId)
  → telemetryFilePath = getFilesDir()/telemetry/session_{id}.jsonl
  → LocalLapDetector.readTelemetryFile(filePath)
    → Read header line (if present) for start line coords
    → Parse JSONL samples into List<TelemetrySample>
  → LocalLapDetector.detectLaps(samples, startLine)
    → detectCrossings(samples, startLine)
      → For each sample pair: check line segment intersection
      → Apply guards: MIN_LAP_TIME_MS (20s), MIN_DISTANCE_FROM_START_M (50m)
    → buildLapsFromCrossings(crossings)
    → Mark best lap (shortest duration)
    → Return LocalLapResult.Success(laps)
  → lapDao.insertAll(laps.map { it.toEntity(sessionId, isLocalOnly=true) })
  → Log "Inserted X local laps"
→ _events.emit(NavigateToSessionResult(sessionId))
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| Stop recording job | `Job.join()` | Wait for service stop |
| Lap detection | `Dispatchers.IO` | `processLapsLocally()` |
| Room insert | `Dispatchers.IO` | `lapDao.insertAll()` |

### Persistence Boundaries

| Storage | Data | Trigger |
|---------|------|---------|
| File `telemetry/session_{id}.jsonl` | Header + samples (read only) | Detection start |
| Room `laps` | LapEntity with `isLocalOnly=true` | Successful detection |

### External Dependencies

None — fully offline operation.

### Algorithm Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `MIN_LAP_TIME_MS` | 20,000 | Primary guard against GPS jitter |
| `MIN_DISTANCE_FROM_START_M` | 50.0 | Ensures driver traveled around track (kart-compatible) |
| `MIN_SAMPLES` | 50 | Minimum telemetry samples required |

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| File read | File not found | `LocalLapResult.Error` | "No laps detected" message |
| Parsing | Invalid JSON | Exception logged, sample skipped | Partial data |
| No start line | All coords 0.0 | `LocalLapResult.NoStartLine` | Detection skipped |
| No crossings | GPS path doesn't cross line | `LocalLapResult.InsufficientLaps` | "Complete 2+ laps" message |
| Insufficient laps | Only 1 crossing | `LocalLapResult.InsufficientLaps` | "Complete 2+ laps" message |
| Room insert | DB error | Exception logged | Laps not persisted |

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| "No laps detected" | Start line doesn't intersect GPS trace |
| "Complete at least 2 laps" | User only completed 1 lap |
| Laps show immediately offline | Success! Local detection worked |
| "📶 Offline" banner visible | `isLocalOnly=true` laps present |

### Operational Signals

| Signal | Tag | Meaning |
|--------|-----|---------|
| "=== LAP DETECTION START ===" | `LocalLapDetector` DEBUG | Detection began |
| "Start line valid" | `LocalLapDetector` DEBUG | Coordinates non-zero |
| "Detected X line crossings" | `LocalLapDetector` DEBUG | Intersection found |
| "Built X laps from crossings" | `LocalLapDetector` DEBUG | Valid laps created |
| "Inserted X local laps" | `RecordingViewModel` INFO | Persistence complete |

### Relationship to Backend Detection

- Local detection runs immediately, provides instant feedback
- Backend detection runs later via `TelemetryUploadWorker` 
- Backend results overwrite local laps (more accurate with full session context)
- Local laps have `isLocalOnly=true`; backend laps have `isLocalOnly=false`

---

## Flow: Offline Coaching Insight Generation

### Goal

Generate coaching insights locally from detected laps. Provides immediate feedback without requiring backend AI processing.

### Trigger

- Local lap detection completes successfully (`DetectionResult.Success`)
- At least 2 laps detected

### Execution Path

```
RecordingViewModel.processLapsLocally()
→ LocalLapDetector.detectLaps() returns Success
→ saveLapsToRoom(sessionId, detectedLaps)
  → Map DetectedLap to LapEntity
  → Set sector1Ms = 0L, sector2Ms = 0L, sector3Ms = 0L  // ⚠️ No sector data
  → lapDao.insertLaps(lapEntities)
→ generateOfflineCoaching(sessionId, lapEntities)
  → OfflineCoachingEngine.generateInsights(laps)
    → Check laps.size >= 2
    → generateBestLapInsight(laps, bestLap)
      → avgS1 = laps.map { it.sector1Ms }.average()  // All 0 → avgS1 = 0.0
      → gainS1 = avgS1 - bestLap.sector1Ms           // 0.0 - 0 = 0
      → Select "best" sector (all gains equal at 0)
      → Return "Lap X Was Your Fastest" + sector detail
    → generateConsistencyInsight(laps)
      → Calculate stdDev of durationMs
      → Map to Excellent/Solid/Work thresholds
      → Return headline + "Laps vary by {stdDev}" detail
    → generateSectorFocusInsight(laps, bestLap)
      → Calculate avg delta vs best for each sector
      → Select weakest or "All Sectors Strong"
  → Return List<OfflineInsight>
→ Map to CoachingInsightEntity with isLocalOnly=true
→ coachingInsightDao.insertInsights(insightEntities)
→ Log "Generated X offline coaching insights"
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| Insight generation | `Dispatchers.Main` | Computation in ViewModel coroutine |
| Room insert | `Dispatchers.IO` | `coachingInsightDao.insertInsights()` |

### Persistence Boundaries

| Storage | Data | Trigger |
|---------|------|---------|
| Room `laps` | LapEntity with sector*Ms = 0L | Input to generation |
| Room `coaching_insights` | CoachingInsightEntity | Successful generation |

### External Dependencies

None — fully offline operation.

### Algorithm Details

| Insight | Calculation | Output |
|---------|-------------|--------|
| Best Lap | `minByOrNull { durationMs }` | Lap number + sector contribution |
| Consistency | `stdDev(durationMs) / mean` → percentage | Excellent (>95%), Solid (85-95%), Work (<85%) |
| Sector Focus | `avgDelta = avg(sector - bestLap.sector)` | Weakest sector or "All Strong" |

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| Insufficient laps | `laps.size < 2` | Empty insight list | No insights shown |
| Zero sector data | All `sector*Ms = 0L` | "0ms quicker than average" | Misleading insight |
| Room insert | DB error | Exception logged | Insights not persisted |

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| "0ms quicker than average" | Sectors not calculated (local detection) |
| "Laps vary by Xms" (misleading) | StdDev shown but "vary by" wording implies range |
| No coaching tab content | < 2 laps or generation failed |
| Insights show immediately offline | Success! Offline coaching worked |

### Operational Signals

| Signal | Tag | Meaning |
|--------|-----|---------|
| "Generated X offline coaching insights" | `RecordingViewModel` DEBUG | Generation complete |
| "No offline coaching insights generated" | `RecordingViewModel` DEBUG | Insufficient laps |
| "Error generating offline coaching insights" | `RecordingViewModel` ERROR | Exception occurred |

### Known Issue: FP-SENTINEL-VALUE

See failure pattern **"Sentinel Value Treated as Valid Data (FP-SENTINEL-VALUE)"** for the root cause of "0ms quicker than average" issue and recommended fix.

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

## Flow: Chart Data Loading (Speed vs Distance)

### Goal

Load real telemetry data, compute cumulative distance, and render speed trace chart with distance-based X-axis.

### Trigger

- User navigates to Chart tab in SessionResultFragment
- `SessionUiState` emits with `rawFilePath` and `laps`

### Execution Path

```
ChartFragment observes parentViewModel.uiState
→ state.laps.isNotEmpty() && state.rawFilePath != null
→ Check lap count:
  → If laps > 10: showProcessingModeDialog()
    → User selects FAST or DETAILED
  → If laps <= 10: use DETAILED mode
→ processAndDisplayChart()
→ [ASYNC] For each lap:
  → TelemetryChartProcessor.computeSpeedByDistance(filePath, lap.startTs, lap.endTs, mode)
    → TelemetryFileReader.readRange(filePath, startTs, endTs)
    → If FAST mode: downsample(samples, 100)
    → computeDistanceAndSpeed(samples)
      → For each sample: cumulativeDistance += haversine(prev, current)
      → Return SpeedDataPoint(distanceMeters, speedKmh)
→ Convert to MPAndroidChart Entry list
→ Create LineDataSet (blue for best lap, grey for others)
→ Bind to LineChart widget
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| File read | `withContext(Dispatchers.IO)` | `TelemetryFileReader.readRange()` |
| Distance computation | `withContext(Dispatchers.Default)` | `TelemetryChartProcessor.computeSpeedByDistance()` |
| UI update | Main thread | `binding.speedChart.data = ...` |

### Persistence Boundaries

| Storage | Data | Purpose |
|---------|------|---------|
| JSONL file | Telemetry samples | Source of speed + GPS data |
| Room `sessions` | `rawFilePath` | Path to telemetry file |
| Room `laps` | `startTs`, `endTs` | Filter samples by lap |

### External Dependencies

None — fully offline processing.

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| File not found | `rawFilePath` points to missing file | "Telemetry data not available" | Empty chart |
| Empty samples | No samples in time range | "No speed data available" | Empty chart |
| Memory pressure | Very large session in DETAILED mode | ANR / OOM | App may freeze |

### Configuration

| Constant | Value | Effect |
|----------|-------|--------|
| `LARGE_SESSION_LAP_THRESHOLD` | 10 | Dialog shown above this |
| `FAST_MODE_POINTS_PER_LAP` | 100 | Downsampling target |

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| Loading spinner | Processing telemetry |
| "Fast vs Detailed" dialog | Session has >10 laps |
| Empty chart | File missing or no data |
| Slow rendering | Large session in DETAILED mode |

### Operational Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| Processing mode dialog | UI | User choosing Fast/Detailed |
| Loading container visible | UI | Computation in progress |
| `hasLoadedRealData = true` | Fragment state | Chart rendered successfully |

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
| Zero sector data | Misleading sector insights |
| 401 on any API | Session expired, re-login |
| Room corruption | All local data lost |
