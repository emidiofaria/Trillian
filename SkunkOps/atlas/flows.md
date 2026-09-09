# flows.md

Runtime execution flows for RCA localization in Driving Coach Android app.

---

## Flow: App Startup & Navigation Resolution

### Goal

Show the branded loading screen, resolve startup state off the main thread, and hand the
user to the correct destination (Onboarding vs Driver Name vs Home).

> **Changed in v2.9** — the destination is no longer gated on a JWT. V1 issues no token,
> so the old `hasToken` branch could never be true and every launch resolved to Login.
> The gate is now the local driver profile (SRS DR-05).

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
      → applyIntroductionBudget(prefs[splash_launch_count] ?: 0)
          → launchCount < 3 : displayBudget = introDisplayMs (4000), hint "Tap to continue",
                              and write launchCount + 1   [failure logged, non-fatal]
          → otherwise       : displayBudget = minDisplayMs (1200), hint "Tap to skip",
                              no write
      → withTimeoutOrNull(warmUpTimeoutMs = 2000)  [optional, non-fatal]
          { withContext(IO) { sessionDao.getPendingUploadSessions() } }  → 55%
      → evaluate pending uploads                          → 80%
      → driverProfileStore.profileFrom(prefs)   [reuses the snapshot above,
                                                 no second DataStore read]
      → resolve destination                               → 100%
    → awaitMinimumDisplay(displayBudgetMs)      [skippable by tap, never required]
    → emit UiState(destination = …)
→ SplashFragment observes destination
→ NavController.navigate(action, popUpTo splashFragment inclusive)
  → onboarding incomplete          : OnboardingFragment
  → driver profile incomplete      : DriverNameFragment
  → otherwise                      : HomeFragment
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
| `minDisplayMs` | 1200 ms | `SplashTimings` (`SplashModule`) | Brand moment for a returning user; set to 0 in tests |
| `introDisplayMs` | 4000 ms | `SplashTimings` (`SplashModule`) | Brand moment on the first `INTRO_LAUNCH_COUNT` launches, long enough to read the manifesto; **must be pinned in every L2 module that overrides `SplashModule`** |
| `INTRO_LAUNCH_COUNT` | 3 | `SplashTimings` constant | How many launches get the longer hold |
| `timeoutMs` | 8000 ms | `SplashTimings` (`SplashModule`) | Ceiling on the **essential** preferences read |
| `warmUpTimeoutMs` | 2000 ms | `SplashTimings` (`SplashModule`) | Ceiling on the **optional** Room warm-up |
| `PROGRESS_TICK_MS` | 60 ms | `SplashViewModel` constant | Progress bar smoothness |

### Automated Verification (L2)

| Stage of this flow | Instrumented test | SRS |
|--------------------|-------------------|-----|
| Branded screen rendered, progress determinate | `SplashScreenTest` | UI-01 |
| Minimum hold honoured, tap-to-skip | `SplashScreenTest` | UI-02 |
| First-run introduction hold applied | `SplashIntroductionTest` | UI-02 |
| Returning user *not* held by the introduction | `SplashReturningUserTest` | UI-02 |
| Tap affordance visible, not accessibility-only | `SplashScreenTest` | UI-10 |
| Manifesto has a permanent home; version shown | `AboutScreenTest` | UI-11, UI-12 |
| Essential timeout → Onboarding (never Login) | `SplashFallbackTest` | UI-03 |
| Main thread stays responsive while init stalls | `SplashMainThreadTest` | UI-04 |
| Splash popped inclusively; Back exits the app | `StartupBackStackTest` | UI-05 |
| Home hero collapse/expand after handoff | `HomeHeroTest` | UI-06 |

These tests drive the real `MainActivity` and substitute only `SplashModule` /
`DataStoreModule`, so the navigation graph and fragment lifecycle exercised are the
production ones.

**Operational note — the introduction budget is measured from the handoff, not from
startup.** Cold start shows two screens in succession: the platform splash window (the
emblem alone on black, owned by the OS and held until the activity's first frame is
composited) and then the branded loading screen. `displayBudgetMs` is measured from the
*second* of those becoming visible.

That anchor is supplied by `SplashVisibilitySignal`: `MainActivity` reports the platform
splash's exit, and `SplashViewModel` waits for it — bounded by `visibilityTimeoutMs`, after
which it falls back to measuring from startup rather than stalling.

This was wrong twice, and both mistakes cost readable time:

| Anchor | Error vs. the true handoff | Readable manifesto (4000 ms budget) |
|--------|---------------------------|-------------------------------------|
| `start()` (original) | ~1700 ms early | ~2.3 s |
| Fragment `doOnPreDraw` | ~1000–1400 ms early | ~2.6–3.0 s |
| Platform splash exit (current) | ~17 ms | ~4.0 s |

`doOnPreDraw` is the trap worth remembering: it fires during the fragment's layout pass,
while the platform splash still owns the window, so it looks like a first-frame signal and
is not one. The authoritative marker is `SplashScreen.setOnExitAnimationListener`, which can
be checked against the platform's own `ActivityTaskManager: Displayed` logcat line.

If the manifesto still reads as too fast in the field, raise `introDisplayMs`; it is a
single named constant, and the budget it names is now the time actually delivered.

**What these tests deliberately do not cover:** the emblem *artwork*. Every
assertion above that touches the emblem checks `isDisplayed()`, which passes for
any drawable — including a blank one. That gap allowed Incident 11 (deformed
emblem) to ship. Artwork geometry is measured separately at L1 by
`BrandAssetGeometryTest` (UI-08, UI-09), which asserts against the asset file
rather than the rendered view.

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
  → guard: currentDestination == onboardingFragment   [onResume can re-fire this]
  → NavController.navigate(action_onboarding_to_driver_name)
```

> **Changed in v2.9** — onboarding previously jumped straight to Home
> (`action_onboarding_to_home`), so the first run and every later run took different
> paths and the driver was never asked for a name. It now hands off to the Driver Name
> screen, matching SRS ON-04.

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

## Flow: First-Run Driver Naming

### Goal

Capture the driver's display name exactly once, persist it, and let every later launch go
straight to Home.

### Trigger

- Onboarding just completed (`action_onboarding_to_driver_name`)
- Cold start where `onboarding_complete == true` but `driver_profile_complete != true`
  (`action_splash_to_driver_name`)

### Execution Path

```
DriverNameFragment.onViewCreated()
→ driverNameInput.doAfterTextChanged
  → DriverNameViewModel.onNameChanged(raw)
    → DriverProfileStore.validate(raw)      [trims first]
    → UiState(canSubmit = result is Valid, errorLabel = … )
       └─ no error shown while the field is untouched or empty
→ User taps "LET'S RACE!!"
  → DriverNameViewModel.onSubmit(raw)
    → ignored if isSaving                   [double-tap guard]
    → re-validate                           [source of truth, not the button state]
    → [IO] DriverProfileStore.saveName(name)
        → dataStore.edit {                  [single atomic edit]
             it[user_name] = trimmed
             it[driver_profile_complete] = true
          }
    → success : Event.NavigateToHome
    → failure : Event.ShowError — driver stays on this screen
→ DriverNameFragment collects the event
  → guard: currentDestination == driverNameFragment
  → navigate(action_driver_name_to_home, popUpTo driverNameFragment inclusive)
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| Validation | Synchronous, main thread | `DriverProfileStore.validate()` — pure string work |
| Save | `withContext(@IoDispatcher)` | `DriverProfileStore.saveName()` |
| Event delivery | `Channel` → `Flow`, collected in `repeatOnLifecycle` | `DriverNameViewModel.events` |

### Persistence Boundaries

| Storage | Key | Purpose |
|---------|-----|---------|
| DataStore | `user_name` | Display name (trimmed) |
| DataStore | `driver_profile_complete` | Gate for DR-05; written in the same edit |

### Failure Points

| Point | Failure | Impact |
|-------|---------|--------|
| `saveName()` | DataStore write throws | Error message; driver stays on the screen. **Never navigates to Home unpersisted** — that was the original defect. |
| Navigation | Destination already changed | Guarded by a `currentDestination` check; the event is dropped |
| Validation | Name 1 char or 101+ chars | Button stays disabled (SRS DR-02) |

### User-Visible Symptoms

| Symptom | Meaning |
|---------|---------|
| "LET'S RACE!!" greyed out | Name is shorter than 2 or longer than 100 characters |
| Asked for a name on every launch | `driver_profile_complete` is not being written — check DataStore write failures in Logcat |
| Name shown with odd spacing | Trimming regression (SRS DR-03) |

### Related Flows

- *Rename*: Profile → tap name or edit button → dialog → `ProfileViewModel.renameDriver()`
  → same validation → `saveName()`. Never touches Room (asserted by `ProfileViewModelTest`).
- *Clear User Data*: Profile → Clear User Data → destructive confirmation →
  read `rawFilePath`s **before** `clearAllTables()` → delete those files → clear all
  preferences → navigate to Onboarding. The read-before-clear order is a contract: after
  `clearAllTables()` there is no record of which files belong to the app.

---

## Flow: GPS Warm-Up (Home)

### Goal

Absorb the GNSS time-to-first-fix (TTFF) while the user is still on the Home screen, so that
the 30–60 s cold-fix wait does not land on Track Setup where the user is standing at the
track edge unable to do anything (SRS TS-16 to TS-23).

### Trigger

- `HomeFragment.onStart()` — every time Home becomes visible
- `TrackSetupFragment.onStart()` — idempotent; keeps the receiver warm if the user arrives
  by any route other than Home

### Execution Path

```
HomeFragment.onStart()
→ HomeViewModel.startGpsWarmUp()
  → LocationWarmUp.start()
    → if already running: restartIdleCeiling() and return   (idempotent)
    → readiness = Acquiring
    → locationUpdates.updates(intervalMs = 1000)
      → FusedLocationUpdates.positionUpdates() via callbackFlow, PRIORITY_HIGH_ACCURACY
    → [CONTINUOUS] onFix(LocationFix)
        → first fix ever this cycle       → record timeToFirstFixMs
        → first fix with accuracy ≤ 10 m  → record timeToAccurateFixMs
                                          → readiness = Ready(accuracyM)
        → GpsAcquisitionMetricsStore.record(...)   [DATASTORE WRITE, non-fatal]
    → [PARALLEL] idle ceiling: delay(1_800_000) → stop()   (backstop only)
→ HomeViewModel.gpsReadiness (StateFlow) → HomeFragment.updateGpsChip()
    Idle      → chip gone
    Acquiring → amber "Acquiring GPS…"
    Ready     → green "GPS ready"

Stop conditions — none of them a screen (SRS TS-18, Incident 12)
→ WarmUpForegroundBinder: last started Activity stops, not for a configuration change
                          → LocationWarmUp.stop()
→ TelemetryForegroundService.startRecording()  → LocationWarmUp.stop()
                          (the service opens its own raw GPS_PROVIDER stream at 10 Hz;
                           the two must never run together)
→ idle ceiling expires after 30 min → LocationWarmUp.stop()
  → cancel collection job, cancel ceiling job, readiness = Idle
```

### Flow: Home → Track Setup handover

The journey the warm-up exists to serve, and the one that used to destroy it. Under
Navigation's replace transaction the ordering is:

```
HomeFragment.onStop()          ← used to call stop() here: the defect (Incident 12)
TrackSetupFragment.onStart()   → LocationWarmUp.start()   (idempotent, no-op when warm)
TrackSetupFragment collector   → locationUpdates.positionUpdates(1000 ms)
```

Because `onStop()` runs **before** the next screen's `onStart()`, no amount of re-starting in
Track Setup could have repaired it: `stop()` nulls `updatesJob`, so `start()`'s idempotence
guard sees a cold receiver and re-acquires. The subscription must never be released in the
gap. `WarmUpHandoverTest` asserts continuity across this boundary rather than sampling
`activeSubscriptions`, which cannot distinguish "held" from "dropped and instantly reopened".

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| Location updates | `callbackFlow` | `FusedLocationUpdates.positionUpdates()` |
| Warm-up collection | `@ApplicationScope CoroutineScope` job | `LocationWarmUp.start()` |
| Idle ceiling | Separate coroutine `delay(1800 s)` | `LocationWarmUp.restartIdleCeiling()` |
| Foreground transitions | `Application.ActivityLifecycleCallbacks` | `WarmUpForegroundBinder` |
| Readiness observation | `StateFlow` + `repeatOnLifecycle(STARTED)` | `HomeFragment` |

### Persistence Boundaries

| Storage | Data | Trigger |
|---------|------|---------|
| DataStore `gps_acquisition` | `timeToFirstFixMs`, `timeToAccurateFixMs`, `recordedAtMs` | Each acquisition milestone |

### External Dependencies

- GPS hardware, `FusedLocationProviderClient`, Google Play Services Location API

### Safety Invariant

`LocationWarmUp` exposes **readiness only — never a `Location`**. A stale warm-up fix reused
as Point A would silently offset the start/finish line and corrupt every lap time in the
session. Capture reads live updates and applies its own ≤10 m accuracy gate (SRS TS-20).

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| Permission not granted | `hasPermission()` false | Chip stays hidden | Warm-up no-ops; Track Setup still prompts |
| Indoors / no sky view | No fix arrives | Chip stuck amber | Same latency as before the feature — no regression |
| Idle ceiling fires | 30 min in the foreground | Chip returns to hidden | Re-entering restarts warm-up |
| Stale fix on Track Setup | Fix older than 3 s | CAPTURE withdrawn, "Getting a current GPS fix…" | Clears on the next current fix; never latches (SRS TS-22) |
| DataStore write fails | I/O error | Metrics missing on About | Caught, non-fatal; warm-up continues |

### Operational Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| `GpsReadiness` | `HomeViewModel.gpsReadiness` | Idle / Acquiring / Ready(accuracy) |
| Time-to-first-fix | About screen | How long any fix took |
| Time-to-accurate-fix | About screen | How long a usable (≤10 m) fix took |

> **Diagnostic value.** The reported 45 s wait was never measured. These two numbers make the
> next report evidence-based: a large TTFF is cold-fix physics, a small TTFF with a large
> user-perceived wait points at a subscription/lifecycle defect instead.

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
  → checkPermissionsAndStart()
→ collectLocationUpdates()
  → viewLifecycleOwner.repeatOnLifecycle(STARTED)      (resubscribes after every stop/start)
    → locationUpdates.positionUpdates(1000ms)          (LocationUpdates abstraction)
→ [CONTINUOUS] onLocation(location)
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

> By the time this flow runs, the receiver is normally already warm from the Home warm-up
> flow above, so the fix here is a hot/warm reacquisition (seconds) rather than a cold TTFF.

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| Location updates | `Flow<Location>` from `LocationUpdates` | `collectLocationUpdates()` |
| Subscription lifecycle | `repeatOnLifecycle(STARTED)` | `TrackSetupFragment` |
| State observation | `StateFlow.collectLatest` | `TrackSetupFragment` |
| Session creation | `viewModelScope.launch` | `RecordingViewModel` |

### Persistence Boundaries

| Storage | Data | Trigger |
|---------|------|---------|
| Room `sessions` | SessionEntity + 4 start line coords | Navigation to recording |

### External Dependencies

- GPS hardware (via `FusedLocationProviderClient`, behind `LocationUpdates`)
- Google Play Services Location API

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| GPS acquisition | No location updates | "Acquiring GPS..." indefinitely | Buttons disabled |
| Permission | Denied | Snackbar, navigate back | Flow blocked |
| Points too close | Distance < 3m | "Minimum 3m required" hint | Cannot proceed |
| Screen-off / app switch | *(fixed)* subscription never restored | Was: permanent "Acquiring GPS..." | See failure-patterns: Location Subscription Not Restored |

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
  → LocalLapDetector.detectLapsWithDiagnostics(file, startLine)
    → detectCrossings(samples, startLine)
      → startPoint = startLine.midpoint()   // orientation of the captured line unused
      → For each sample pair:
          heading = bearing(previous, current)          // skip if segment < 0.5 m
          cross a plane through startPoint PERPENDICULAR to heading
          reject if |lateral offset| > DETECTION_HALF_WIDTH_M (15 m)
          reject if since last accepted < MIN_LAP_TIME_MS (20 s)
          reject if travelled since last accepted < MIN_DISTANCE_FROM_START_M (50 m)
          reject if heading differs > MAX_HEADING_DIFFERENCE_DEG (60°) from first crossing
          interpolate the crossing instant between the two samples
      → record every rejection with its reason
    → buildLaps(crossings)
    → Mark best lap (shortest duration)
    → Return DetectionOutcome(result, diagnostics)
  → LapDiagnosticsWriter.write(file, sessionId, outcome)   // whatever the result
    → writes <telemetry>.lapdiag.json beside the telemetry; never throws
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
| `DETECTION_HALF_WIDTH_M` | 15.0 | Lateral extent of the crossing plane |
| `MAX_HEADING_DIFFERENCE_DEG` | 60.0 | Rejects passes in a materially different direction |
| `MIN_SEGMENT_LENGTH_M` | 0.5 | Below this the bearing between two samples is meaningless |

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| File read | File not found | `LocalLapResult.Error` | "No laps detected" message |
| Parsing | Invalid JSON | Exception logged, sample skipped | Partial data |
| No start line | All coords 0.0 | `LocalLapResult.NoStartLine` | Detection skipped |
| No crossings | Car never passed within 15 m of the captured start point | `DetectionResult.InsufficientLaps` | "Complete 2+ laps" message, **and all coaching is lost** |
| Double counting | Track passes the same point twice per lap at an admitted angle | `DetectionResult.Success` with twice the laps | Lap times ~half of reality, no error — `FP-LAP-DOUBLE-COUNT` |
| Diagnostics write | Storage full / path unwritable | `write()` returns null | None. Logged only, by design |
| Insufficient laps | Only 1 crossing | `LocalLapResult.InsufficientLaps` | "Complete 2+ laps" message |
| Room insert | DB error | Exception logged | Laps not persisted |

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| "No laps detected" | Car never passed within 15 m of the captured start point. Before the Incident 13 fix this also occurred when the captured line pointed *along* the track — see `FP-DEGENERATE-BASELINE` |
| Lap times look about half what was driven | `FP-LAP-DOUBLE-COUNT` — confirm from the `.lapdiag.json` sidecar |
| "Complete at least 2 laps" | User only completed 1 lap |
| Laps show immediately offline | Success! Local detection worked |
| "📶 Offline" banner visible | `isLocalOnly=true` laps present |

### Operational Signals

| Signal | Tag | Meaning |
|--------|-----|---------|
| "=== LAP DETECTION START ===" | `LocalLapDetector` DEBUG | Detection began |
| "Start line valid" | `LocalLapDetector` DEBUG | Coordinates non-zero |
| "Detected X crossings, rejected Y candidates" | `LocalLapDetector` DEBUG | Crossing search complete |
| "rejected at {ts}: {reason} - {detail}" | `LocalLapDetector` DEBUG | Why each candidate was discarded |
| "Start line: Nm long, bearing B, A degrees to the direction of travel" | `LocalLapDetector` DEBUG | **A near 0 identifies `FP-DEGENERATE-BASELINE`** |
| "Built X laps from crossings" | `LocalLapDetector` DEBUG | Valid laps created |
| "Wrote lap diagnostics to ..." | `LapDiagnosticsWriter` DEBUG | Sidecar persisted |
| "Inserted X local laps" | `RecordingViewModel` INFO | Persistence complete |

### Relationship to Backend Detection

**Delivery 1 is local-only.** There is no backend in the shipped product, and a session is
processed end to end with the device offline. The notes below describe the design as it would
work if a backend is added in a later delivery — they are not current behaviour.

- Local detection runs immediately, provides instant feedback
- Backend detection would run later via `TelemetryUploadWorker`
- Backend results would overwrite local laps (more accurate with full session context)
- Local laps have `isLocalOnly=true`; backend laps would have `isLocalOnly=false`

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

## Flow: Session Share (Card and Telemetry Export)

### Goal

Get session data off the phone — as an image for people, or as a diagnostic bundle for
developers — without touching the recorded session itself.

### Trigger

- **Tap** the toolbar share icon → image card
- **Long-press** the toolbar share icon → telemetry ZIP (hidden, SRS SH-07)

### Execution Path

```
                    SessionResultFragment.setupToolbar()
                                 │
              ┌──────────────────┴──────────────────┐
              │                                     │
   setOnMenuItemClickListener        attachTelemetryExportGesture()
              │                        toolbar.post { findViewById(action_share)
              │                                        .setOnLongClickListener }
              │                                     │
        shareSessionCard()                   shareTelemetryBundle()
              │                                     │
  ┌───────────┴───────────┐         ┌───────────────┴───────────────┐
  │ uiState.session ?: err│         │ uiState.session ?: err        │
  │ Dispatchers.Default:  │         │ Snackbar "Preparing export…"  │
  │   ShareCardGenerator  │         │ Dispatchers.IO:               │
  │ Dispatchers.IO:       │         │   pruneStaleArtifacts()       │
  │   prune + write PNG   │         │   copy telemetry → ZIP        │
  └───────────┬───────────┘         │   + session.json (Gson)       │
              │                     └───────────────┬───────────────┘
              │                                     │
              │                       Success(file) │ Failure(reason)
              │                                     │        │
              └──────────────┬──────────────────────┘        │
                             ▼                                ▼
                      startChooser()                   showShareError()
              FileProvider.getUriForFile                  Snackbar
              ACTION_SEND + image/png | application/zip
              FLAG_GRANT_READ_URI_PERMISSION
```

### Async Boundaries

| Boundary | Dispatcher | Note |
|----------|-----------|------|
| Card rendering | `Dispatchers.Default` | CPU-bound bitmap work |
| File IO (PNG write, ZIP assembly) | `Dispatchers.IO` | Keeps the toolbar responsive |
| Chooser launch | Main | Resumes on `viewLifecycleOwner.lifecycleScope` |

### Persistence Boundaries

**None on the write side for session data — this is the defining property of the flow.**
The only writes are new files under `cacheDir/shared/`. No DAO is called, no telemetry
file is opened for writing, nothing is renamed or deleted outside the share cache
(SRS SH-11).

### Failure Points

| Point | Failure | Result |
|-------|---------|--------|
| `uiState.session` still null | Screen not loaded yet | Snackbar, no send |
| Telemetry file missing/not a file | Old session, deleted file | `Failure(TELEMETRY_FILE_MISSING)` → Snackbar |
| ZIP write | Full cache, IO error | `Failure(EXPORT_FAILED)` → Snackbar |
| `startActivity` | No handler, bad FileProvider config | Caught, logged, Snackbar |
| Long-press listener not attached | `post` ran after view destruction, or `setupToolbar` refactored | **Silent** — export simply unreachable; covered only by L2 tests |

### User-Visible Symptoms

| Symptom | Meaning |
|---------|---------|
| "Preparing export…" then a share sheet | Normal export |
| "Telemetry file not found" | Session has no raw file (predates the feature, or file removed) |
| "Couldn't prepare the share" | Card generation or chooser failed — previously this was *silent* |

### Operational Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| `E/SessionResultFragment: Failed to share session card` | Logcat | Card path threw |
| `E/SessionResultFragment: Failed to start share chooser` | Logcat | FileProvider/intent problem |
| Files in `cacheDir/shared/` | Device | Share artifacts, auto-pruned after 24 h |

---

## Flow: Session Analysis (ANALYSIS Tab)

### Goal

Derive and render a track-engineer report for a finished session — statistics, an offline
track map, corner and braking-zone tables and a session speed trace — without any network
access and without re-deriving laps.

### Trigger

- User selects the ANALYSIS tab (position 3) on `SessionResultFragment`
- `SessionUiState` emits with `rawFilePath`, `laps` and the session's start-line corners
- User taps a different lap chip (recompute for a new reference window)

### Execution Path

```
AnalysisFragment observes parentViewModel.uiState
→ state.isLoading == false
→ startLine = midpoint of (startLineLat1/Lng1, startLineLat2/Lng2) when all four are set
→ AnalysisViewModel.submit(rawFilePath, laps, startLine)
  → de-duplicates: recomputes only when file path, lap ids or start line changed
  → default reference lap = best lap, else shortest lap
→ [ASYNC] AnalysisViewModel.recompute()
  → File(path).canRead() on Dispatchers.IO       (drives the empty-state message)
  → SessionAnalysisProcessor.analyze(...)
    → TelemetryFileReader.readAll(path)          (Dispatchers.IO)
    → prepare(): drop header line + null-island fixes, sort by timestamp
    → [Dispatchers.Default]
      → computeStats(samples, laps)
      → reference window = lapWindows[referenceLapId] ∩ samples, else whole session
      → detectCorners(reference window)
      → detectBrakingZones(reference window) + association to the next corner
      → buildTrackPath(reference window) + start/finish marker
      → speed trace over the WHOLE session, decimated
→ Main thread: bind stats, TrackMapView, lap chips, corner rows, braking rows, LineChart
```

### Async Boundaries

| Boundary | Type | Location |
|----------|------|----------|
| File readability probe | `withContext(Dispatchers.IO)` | `AnalysisViewModel.recompute()` |
| File read | `withContext(Dispatchers.IO)` | `TelemetryFileReader.readAll()` |
| Detection maths | `withContext(Dispatchers.Default)` | `SessionAnalysisProcessor.analyze()` |
| Rendering | Main thread | `AnalysisFragment.showAnalysis()` |

A recompute cancels the previous job, so rapid lap-chip taps cannot interleave results.

### Persistence Boundaries

| Storage | Data | Purpose |
|---------|------|---------|
| JSONL file | Telemetry samples | Sole source of speed, position and time |
| Room `sessions` | `rawFilePath`, start-line corners | File location and S/F marker |
| Room `laps` | `startTs`, `endTs`, `isBestLap` | Reference window and lap chips |

Nothing is written. The tab is strictly read-only.

### External Dependencies

None — fully offline, by design. No map SDK, no tiles, no geocoding.

### Failure Points

| Stage | Failure | Symptom | Propagation |
|-------|---------|---------|-------------|
| File probe | Path set but file deleted | "…no longer on this device" | Empty state, no crash |
| Parse | Fewer than 2 usable samples | "too little telemetry to analyse" | Empty state |
| Reference window | Lap window outside the file | "Reference: whole session" | Analysis still shown |
| Corner detection | Thresholds not met | "No corners detected on this lap." | Table placeholder |
| Braking detection | Thresholds not met | "No braking zones detected on this lap." | Table placeholder |

### Configuration

See the constants table in `components.md` → *Session Analysis Engine*. All thresholds are
per second so that 1 Hz archives and 10 Hz recordings analyse identically.

### User-Visible Symptoms

| Symptom | Cause |
|---------|-------|
| Spinner on entering the tab | Telemetry being parsed and analysed |
| Grey/blue-to-green track outline | Speed gradient over the reference lap |
| Red stretches on the outline | Detected braking zones |
| `T1..Tn` labels | Detection order of passage, not the circuit's own numbering |
| "Reference: whole session (no laps detected)" | No usable lap window |

### Operational Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| `contentContainer` visible | UI | Analysis succeeded |
| `emptyStateText` visible | UI | Degraded input, handled |
| Checked lap chip | UI | Which window the tables describe |

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
