# failure-patterns.md

Operational failure patterns for RCA acceleration in Driving Coach Android app.

---

## Pattern: Telemetry Upload Retry Exhaustion

### Symptoms

- Session shows "Upload Failed" status in UI
- User sees stale upload banner on Home screen
- No coaching insights available for session
- Session stuck in FAILED state indefinitely

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| "Max retries exceeded for session: X" | Logcat `TelemetryUploadWorker` ERROR | Any occurrence |
| `runAttemptCount > 5` | WorkManager logs | Exceeded |
| `uploadStatus = 'FAILED'` | Room `sessions` table | Multiple rows |
| Repeated 5xx in HTTP logs | OkHttp logs | 5+ for same session |
| WorkInfo.state = FAILED | WorkManager inspection | Session-tagged work |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| Persistent server errors | `TelemetryUploadWorker:119-139` | 5xx responses in logs |
| Large telemetry file | File > server limit | 413 response code |
| Backend service down | API unavailable | Connection timeouts |
| Malformed multipart | `MultipartBody.Part` construction | 400 response |
| Auth expired during retry | Token invalidated mid-retry | 401 after initial success |

### Evidence To Check

1. **Logcat**: Filter `TAG:TelemetryUploadWorker` for attempt count and error codes
2. **Room DB**: `SELECT id, uploadStatus, rawFilePath FROM sessions WHERE uploadStatus = 'FAILED'`
3. **File system**: Verify file exists at `session.rawFilePath`
4. **WorkManager**: `WorkManager.getInstance().getWorkInfosByTag("telemetry_upload")`
5. **HTTP logs**: Look for response codes 4xx/5xx for `/api/v1/sessions/*/telemetry`
6. **Backend logs**: Check server-side errors for session ID

### Common Triggers

- Backend deployment/outage during upload window
- Network flakiness causing repeated timeouts
- User recorded very long session (large file)
- Token expired between recording stop and upload execution
- Backend schema change rejecting old payload format

### Mitigation

1. Manual retry via `SessionResultViewModel.retryAnalysis()` (re-enqueues worker)
2. Clear WorkManager for session: `WorkManager.cancelAllWorkByTag("telemetry_upload")`
3. Manually re-enqueue: `WorkManager.enqueue(TelemetryUploadWorker.buildRequest(sessionId))`
4. If file missing: Session data unrecoverable

### Permanent Fix

- Implement resumable uploads for large files
- Add user-facing "Retry Upload" button
- Increase `MAX_RETRIES` for transient failures
- Add server health check before upload attempt
- Implement upload progress tracking

### Confidence

**HIGH** — Direct code path analysis of `TelemetryUploadWorker.doWork()` lines 66-161

---

## Pattern: GPS Lock Failure During Recording

### Symptoms

- "ACQUIRING GPS..." never changes to "GPS LOCKED"
- Notification stuck on "GPS..."
- Recording starts but telemetry file has no samples
- `gpsLocked = false` in RecordingState for entire session

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| "GPS lock timeout" | Logcat `TelemetryService` WARN | Any occurrence |
| `onLocationChanged` not called | Logcat absence | No calls after start |
| `gpsLocked = false` | StateFlow observation | >30s after start |
| Empty telemetry file | `files/telemetry/session_*.jsonl` | 0 bytes or no lines |
| Notification text "GPS..." | System notification | Persists >10s |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| Indoor location | `FusedLocationProviderClient` | No satellite visibility |
| GPS hardware disabled | System settings | Provider disabled callback |
| Location permission revoked | Runtime permission check | `RecordingState.Error` |
| GPS chipset failure | Hardware | No fix even outdoors |
| Mock location active | Developer options | Inconsistent fixes |

### Evidence To Check

1. **Logcat**: Filter `TAG:TelemetryService` for "GPS lock timeout" and "GPS locked"
2. **StateFlow**: Observe `TelemetryForegroundService.state` for `gpsLocked` value
3. **System Settings**: Verify Location is enabled, GPS mode is "High accuracy"
4. **Telemetry file**: Count lines in `session_{id}.jsonl`
5. **Notification**: Check notification content text

### Common Triggers

- User starts recording indoors/in parking garage
- Location permission revoked while app backgrounded
- Battery saver disabling GPS
- Poor satellite visibility (urban canyon, heavy tree cover)
- Device in airplane mode

### Mitigation

1. Move device to location with clear sky view
2. Wait for GPS lock (service continues attempting)
3. Check system Location settings
4. Verify app has location permission in Settings
5. Restart recording after establishing lock

### Permanent Fix

- Add pre-flight GPS check before allowing "Start Session"
- Show estimated time to GPS lock
- Allow fallback to network location for initial fix
- Add "GPS not available" guidance in UI
- Store partial session even without GPS (IMU-only)

### Confidence

**HIGH** — Direct code path in `TelemetryForegroundService` lines 179-240, 348-390

---

## Pattern: Foreground Service Start Blocked (Android 12+)

### Symptoms

- App crashes on "Start Session" tap
- `ForegroundServiceStartNotAllowedException` in crash logs
- Recording never begins
- No notification appears

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| `ForegroundServiceStartNotAllowedException` | Crashlytics/Logcat | Any occurrence |
| Crash on `startForegroundService()` | Stack trace | `HomeViewModel.startNewSession()` |
| No `TelemetryService` lifecycle logs | Logcat | Absence of "Service created" |
| Session created but never started | Room | `endedAt = NULL`, no telemetry file |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| App in background when starting | `context.startForegroundService()` | App not visible |
| Battery optimization aggressive | Android Doze | Background restricted |
| Task manager killed app | OEM modifications | Process death |
| Timing race | Service start vs foreground call | >5s delay |

### Evidence To Check

1. **Crashlytics**: Search for `ForegroundServiceStartNotAllowedException`
2. **Logcat**: Filter `TAG:TelemetryService` for "Service created"
3. **Room DB**: Sessions with `endedAt = NULL` and no matching telemetry file
4. **App state**: Was app in foreground when crash occurred?
5. **Battery settings**: Check if app is battery-restricted

### Common Triggers

- User taps "Start" then immediately switches apps
- Device locked during session start
- OEM aggressive battery optimization (Xiaomi, Huawei, Samsung)
- App restored from recent apps after process death
- Split-screen/PiP mode edge cases

### Mitigation

1. Keep app in foreground during session start
2. Disable battery optimization for app in Settings
3. Whitelist app in device-specific battery manager
4. Restart app and try again

### Permanent Fix

- Check `ActivityManager.isBackgroundRestricted()` before starting
- Show warning if battery optimization is enabled
- Use `PendingIntent` with foreground service for deferred start
- Add OEM-specific guidance for battery whitelist
- Implement fallback to bound service for data collection

### Confidence

**HIGH** — Android 12+ documented behavior; code uses `startForegroundService()` in `HomeViewModel:184-188`

---

## Pattern: DataStore ANR on Startup — ✅ MITIGATED (v2.8)

> **Status: RESOLVED.** The `runBlocking` DataStore read in `MainActivity.setupNavigation()`
> was removed when the branded loading screen landed. Startup state is now resolved by
> `SplashViewModel` on `@IoDispatcher` with an 8 s essential timeout and an Onboarding fallback.
> This pattern is retained for historical RCA context and regression detection.

### Symptoms (historical)

- App shows "Not Responding" dialog on launch
- Black screen for >5 seconds
- ANR trace in `/data/anr/`
- Startup time significantly degraded

### Regression Signals

If any of these reappear, the mitigation has been reverted or bypassed:

| Signal | Location | Threshold |
|--------|----------|-----------|
| ANR dialog on launch | User device | Any occurrence |
| `runBlocking` on main thread | Stack trace | Any occurrence in startup path |
| Main thread blocked | ANR trace | `DataStore.data.first()` |
| Loading screen never dismisses | UI | >8 s (essential timeout should have fired) |

### Current Behaviour (post-mitigation)

| Condition | Old behaviour | New behaviour |
|-----------|---------------|---------------|
| Slow disk I/O | Main thread blocked → ANR | Progress bar stalls; essential timeout → Onboarding |
| DataStore corruption | `runBlocking` exception → crash | Empty prefs → Onboarding |
| Essential work > 8 s | Indefinite block | `usedFallback = true` → Onboarding |
| Room warm-up > 2 s | Deferred, first screen janks | Warm-up abandoned; destination unaffected |
| Room migration on startup | Deferred, first screen janks | Warmed during loading screen (bounded) |

### Measured Evidence (emulator, Pixel 4 API 30)

| Measurement | Cold start (first ever launch) | Warm start |
|-------------|-------------------------------|------------|
| `DataStore.data.first()` | ~3 570 ms | ~1 880 ms |
| Room warm-up query | — | ~955 ms |
| Total startup resolve | ~4 460 ms | ~2 890 ms |

> The original 3 s global timeout fired on **normal** cold starts, making the fallback the
> common path rather than the exceptional one. This is why the essential budget was raised to
> 8 s and non-essential warm-up was split out behind its own 2 s bound.
>
> The fallback destination was changed from Login to **Onboarding** on an asymmetric-cost
> argument: if preferences cannot be read we do not know whether the user has onboarded.
> Routing an already-onboarded user through Onboarding is a recoverable annoyance that still
> ends at Home; routing a fresh user to Login skips permission granting entirely and leaves
> the app unable to record.

### Evidence To Check

1. **Verify mitigation is present**: `MainActivity` must contain no `runBlocking`
2. **`SplashViewModel.uiState.usedFallback`** — `true` means essential init exceeded its 8 s budget
3. **Logcat timings** (tag `SplashViewModel`) — `datastore read took Xms`,
   `room warm-up took Xms, result=N`, `startup resolved=DEST in Xms`
4. **Progress plateau** — the step label identifies which init stage is slow
5. **DataStore file**: size of `driving_coach_prefs.preferences_pb`
6. **Device info**: storage health, available space, device tier

### Mitigation (user-side, if slow startup persists)

1. Force stop app, clear cache, relaunch
2. Free device storage space
3. Clear app data (loses preferences, requires re-login)

### Permanent Fix — IMPLEMENTED

| Fix | Status | Location |
|-----|--------|----------|
| Replace `runBlocking` with async init + splash screen | ✅ Done | `SplashViewModel`, `SplashFragment` |
| Bounded startup with fallback destination | ✅ Done | `withTimeoutOrNull(timings.timeoutMs)` |
| Non-essential warm-up isolated behind own bound | ✅ Done | `withTimeoutOrNull(timings.warmUpTimeoutMs)` + `runCatching` |
| Room warm-up before first query | ✅ Done | `sessionDao.getPendingUploadSessions()` |
| System-splash handoff (no double splash) | ✅ Done | `installSplashScreen()`, `Theme.DrivingCoach.Splash` |
| DataStore migration/corruption recovery | ⚠️ Open | Still falls back to empty preferences |

### Regression Guard

**L1 —** `SplashViewModelTest` asserts the timeout fallback, the non-blocking destination
resolution and the skip semantics.

**L2 —** `SplashMainThreadTest` (`app/src/androidTest/.../ui/splash/`) runs the real
`MainActivity` against a `StallingPreferencesDataStore` and samples main-looper round-trip
latency from a background thread via `MainThreadResponsivenessProbe`. Budget: **2000 ms**.

> ⚠️ **Espresso is not a valid detector for this pattern.** Espresso synchronises *with* the
> main looper — it waits for idle rather than timing out — so a deliberately reintroduced
> `runBlocking { delay(60_000) }` in `SplashFragment.onViewCreated` left an Espresso-click
> based test **passing**, just slower. The latency probe caught the same injected regression
> immediately: `main thread was unresponsive for 15000ms during startup (budget 2000ms)`.
> Any future "must not block" assertion must be falsified the same way before it is trusted.

### Confidence

**HIGH** — root cause removed, covered by unit tests, and the instrumented guard has been
empirically falsified (proven to fail when the defect is reintroduced).

---

## Pattern: Session State Desync (UI vs Service)

### Symptoms

- Recording screen shows "00:00" while recording is active
- GPS status doesn't update despite service logging fixes
- Stop button unresponsive
- Elapsed time frozen
- UI shows "Idle" while notification shows "Recording"

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| `isBound = false` | RecordingViewModel | After `bindToService()` call |
| StateFlow not emitting | Service observation | No UI updates |
| Service logs active, UI frozen | Logcat vs UI | Mismatch |
| `onServiceDisconnected` called | ServiceConnection | Unexpected disconnect |
| Notification updates, UI doesn't | System vs app | Divergence |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| Service binding failed | `RecordingViewModel:98-104` | `isBound = false` |
| Fragment lifecycle mismatch | `onStart`/`onStop` binding | Premature unbind |
| Service died, UI didn't notice | Process death | Service logs stop |
| StateFlow collection cancelled | Coroutine cancellation | Flow not collected |
| Configuration change | Activity recreation | Binding lost |

### Evidence To Check

1. **RecordingViewModel**: Check `isBound` and `serviceBinder` state
2. **Logcat**: Filter `TelemetryService` — is service still logging?
3. **Service state**: `TelemetryForegroundService.state.value`
4. **Notification**: Does it reflect current state?
5. **Fragment lifecycle**: `onStart`/`onStop` timing

### Common Triggers

- Screen rotation during recording
- App backgrounded then foregrounded
- Memory pressure causing service rebind
- Fragment transaction timing issues
- Split-screen mode changes

### Mitigation

1. Navigate away and back to Recording screen
2. Kill and restart app (loses recording)
3. Use notification controls if available

### Permanent Fix

- Make `RecordingViewModel` lifecycle-aware with `ServiceConnection`
- Use `LiveData` or `StateFlow` that survives configuration changes
- Add periodic state sync check
- Implement service health ping
- Use `SavedStateHandle` to preserve binding state

### Confidence

**MEDIUM** — Service binding is inherently fragile; code structure in `RecordingViewModel:45-64` is standard but prone to lifecycle issues

---

## Pattern: Authentication Token Expiration Loop

### Symptoms

- Repeated 401 errors in logs
- User redirected to login repeatedly
- "Session expired" snackbar appears frequently
- API calls fail even after re-login

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| Multiple 401 responses | OkHttp logs | >2 in 1 minute |
| `AuthEvent.SessionExpired` repeated | AuthEventBus | >1 in session |
| Navigation to login multiple times | NavController | Repeated actions |
| Token cleared then re-401 | DataStore + HTTP | Immediate 401 after clear |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| Token expired naturally | JWT expiry | Time since login |
| Server invalidated session | Backend action | All tokens rejected |
| Clock skew | Device time wrong | JWT exp check fails |
| Token not persisted | DataStore write failed | Token null on next request |
| Interceptor race condition | `runBlocking` in interceptor | Concurrent requests |

### Evidence To Check

1. **OkHttp logs**: Count 401s, check which endpoints
2. **DataStore**: Is `jwt_token` present after login?
3. **JWT payload**: Decode token, check `exp` claim vs current time
4. **Device time**: Is system time accurate?
5. **AuthEventBus**: How many `SessionExpired` events emitted?

### Common Triggers

- Long session without API activity
- Device time changed manually
- Backend token rotation/revocation
- Multiple devices logged in, one logs out
- App backgrounded for extended period

### Mitigation

1. Re-login with correct credentials
2. Verify device time is automatic/accurate
3. Clear app data and re-login
4. Check if backend requires re-authentication

### Permanent Fix

- Implement token refresh before expiration
- Add proactive token validity check on app foreground
- Handle concurrent 401s with single refresh attempt
- Add retry-with-refresh for authenticated requests
- Store token expiry time and refresh proactively

### Confidence

**MEDIUM** — `AuthInterceptor:49-64` clears token on 401 but no refresh logic exists; natural expiration will cause this pattern

---

## Pattern: Telemetry File Missing on Upload

### Symptoms

- Upload fails immediately with "Telemetry file not found"
- Session stuck in FAILED status
- `rawFilePath` points to non-existent file
- Session recorded but data unrecoverable

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| "Telemetry file not found" | Logcat `TelemetryUploadWorker` ERROR | Any occurrence |
| `Result.failure()` immediate | WorkManager | No retry attempts |
| File missing at path | File system check | `!file.exists()` |
| `uploadStatus = 'FAILED'` | Room | With no retry logs |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| App data cleared | User action | All files gone |
| Storage cleanup | Android system | Low storage action |
| Uninstall/reinstall | User action | New app directory |
| rawFilePath never set | `SessionEntity` creation | Path is empty string |
| File written to wrong path | `TelemetryFileWriter` | Path mismatch |

### Evidence To Check

1. **Room DB**: `SELECT rawFilePath FROM sessions WHERE id = X`
2. **File system**: `adb shell ls -la /data/data/com.drivingcoach/files/telemetry/`
3. **Upload worker logs**: Look for "Telemetry file not found" with session ID
4. **Storage settings**: Was "Clear Data" used?
5. **Session creation**: Was `rawFilePath` set correctly?

### Common Triggers

- User cleared app data before upload completed
- Storage manager cleaned old files
- App uninstalled then reinstalled (session in backup, file not)
- Recording stopped abnormally (file never closed)
- Bug in `rawFilePath` assignment

### Mitigation

1. Session data is unrecoverable if file is gone
2. Delete session from database to clear failed state
3. Re-record the session

### Permanent Fix

- Set `rawFilePath` during session creation, not service start
- Verify file exists before marking upload complete
- Implement file integrity check (hash verification)
- Add file existence check in `HomeViewModel` stale detection
- Consider cloud backup of telemetry before upload

### Confidence

**HIGH** — Code path `TelemetryUploadWorker:97-102` explicitly checks file existence and fails permanently

---

## Pattern: WorkManager Constraint Starvation

### Symptoms

- Uploads never execute despite being enqueued
- Sessions stuck in PENDING for hours/days
- WorkManager shows ENQUEUED state indefinitely
- No upload attempt logs appear

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| WorkInfo.state = ENQUEUED | WorkManager | >1 hour |
| No worker execution logs | Logcat | Absence of "Starting upload" |
| `uploadStatus = 'PENDING'` | Room | Old sessions |
| Stale upload banner | Home UI | Persistent |
| Network constraint never satisfied | WorkManager | Device offline |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| No network connectivity | `NetworkType.CONNECTED` constraint | Device offline |
| Battery Saver blocking | WorkManager restrictions | System setting |
| Doze mode | Background execution limits | Device idle |
| WorkManager not initialized | Hilt/app setup | Missing initialization |
| Work cancelled externally | `cancelAllWork()` | No work in queue |

### Evidence To Check

1. **WorkManager**: `WorkManager.getInstance().getWorkInfosByTag("telemetry_upload")`
2. **Network**: Is device connected to network?
3. **Battery**: Is Battery Saver enabled?
4. **Doze**: Is device in Doze mode?
5. **WorkManager logs**: Enable verbose WorkManager logging

### Common Triggers

- Device in airplane mode for extended period
- Battery Saver aggressive on low battery
- Device idle/charging (Doze)
- Metered network avoided (if constraint added later)
- OEM background restrictions

### Mitigation

1. Connect to Wi-Fi or mobile data
2. Disable Battery Saver temporarily
3. Open app to force WorkManager scheduling
4. Manually trigger worker from code

### Permanent Fix

- Add expedited work for critical uploads
- Implement fallback immediate upload when app is foreground
- Add user-visible "Pending Uploads" with manual trigger
- Consider removing network constraint for small files
- Add WorkManager diagnostic logging

### Confidence

**HIGH** — `TelemetryUploadWorker:49-51` uses `NetworkType.CONNECTED` constraint; constraint starvation is documented WorkManager behavior

---

## Pattern: Room Database Migration Failure

### Symptoms

- App crashes on launch after update
- `IllegalStateException: Room cannot verify data integrity`
- All local data lost after reinstall to fix
- New app version incompatible with old DB

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| `Room cannot verify data integrity` | Crashlytics | Any occurrence |
| `IllegalStateException` on DB access | Stack trace | Room migration |
| Version mismatch | `DrivingCoachDatabase` | Deployed vs installed |
| DB file corrupt | `driving_coach.db` | SQLite errors |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| Schema change without migration | `DrivingCoachDatabase` version bump | No `Migration` object |
| Destructive migration disabled | Default Room behavior | `fallbackToDestructiveMigration()` not called |
| Column type change | Entity modification | SQLite type mismatch |
| Index/FK change | Entity annotations | Schema diff |

### Evidence To Check

1. **Crashlytics**: Search for Room migration exceptions
2. **Schema files**: Compare `app/schemas/*.json` between versions
3. **DrivingCoachDatabase**: Check version number and migrations
4. **SQLite**: Direct DB inspection for schema
5. **Entity changes**: Diff entity classes between versions

### Common Triggers

- Developer changed entity without migration
- Version bump without schema export
- Nullable → non-nullable column change
- New required column without default
- Foreign key constraint added

### Mitigation

1. User must clear app data (loses all local sessions)
2. Uninstall and reinstall app
3. Cannot recover data from incompatible DB

### Permanent Fix

- Always write `Migration` objects for schema changes
- Enable `exportSchema = true` (already set)
- Test migrations with `MigrationTestHelper`
- Consider `fallbackToDestructiveMigration()` for non-critical data
- Add migration version check at startup

### Confidence

**LOW** — `DrivingCoachDatabase` is at version 2 with `MIGRATION_1_2` implemented. Pattern addressed for v1→v2; future schema changes must add new migrations.

---

## Pattern: GPS Signal Lost During Recording

### Symptoms

- Notification shows "GPS signal lost — move to open sky"
- `gpsLocked = false` after initial lock
- Gaps in telemetry data
- Recording continues but samples stop

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| "GPS signal lost" | Logcat `TelemetryService` WARN | Any occurrence |
| `gpsSignalLost = true` | Service state | >10s without sample |
| Notification text change | System | "GPS signal lost" |
| `lastGpsSampleTime` stale | Service internal | >10s old |
| Sample gaps in JSONL | Telemetry file | >10s between timestamps |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| Physical obstruction | Environment | Tunnel, garage, trees |
| Device orientation | Antenna position | GPS antenna blocked |
| Interference | RF environment | Urban canyon, jamming |
| Hardware degradation | GPS chipset | Sporadic fixes |
| Power management | System | GPS throttled |

### Evidence To Check

1. **Logcat**: Filter `TelemetryService` for "GPS signal lost" / "GPS signal recovered"
2. **Notification**: Check notification content during issue
3. **Telemetry file**: Look for timestamp gaps >10s
4. **Location**: Was user in covered area?
5. **StateFlow**: `gpsSignalLost` value

### Common Triggers

- Driving through tunnel
- Recording in underground parking
- Dense urban environment
- Heavy tree canopy
- Device in pocket/bag blocking antenna
- Power saving mode throttling GPS

### Mitigation

1. Move to location with clear sky view
2. Signal auto-recovers when GPS resumes
3. Reposition device for better antenna exposure
4. Wait for current obstruction to pass

### Permanent Fix

- Implement dead reckoning with IMU during GPS gaps
- Store last known position with uncertainty
- Add signal quality indicator to UI
- Consider network-assisted GPS (AGPS)
- Mark low-quality sections in telemetry

### Confidence

**HIGH** — Direct code path `TelemetryForegroundService:293-308` implements 10s signal lost detection

---

## Pattern: Service Killed by System

### Symptoms

- Recording stops unexpectedly
- Notification disappears
- Service logs end abruptly
- Partial session data

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| "Service destroyed" unexpected | Logcat | Without "Stopping recording" |
| OOM kill | System logs | `lowmemorykiller` |
| Process death | `ActivityManager` | Force stop |
| Incomplete telemetry | File | Abrupt end, no close |
| Session `endedAt = NULL` | Room | Never updated |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| Memory pressure | System OOM | Low memory logs |
| User force stop | Settings/Task manager | Intentional kill |
| Battery optimization | Doze/App Standby | Background restricted |
| System update | Android upgrade | Process restarted |
| OEM task killer | Vendor software | Aggressive cleanup |

### Evidence To Check

1. **Logcat**: Look for "Service destroyed" without prior "Stopping recording"
2. **System logs**: `adb logcat -b events` for `am_kill` events
3. **Room**: Sessions where `endedAt IS NULL`
4. **Telemetry file**: Does it end mid-sample or have corrupt last line?
5. **Battery stats**: Was app restricted?

### Common Triggers

- Running memory-intensive apps alongside
- Device has low RAM (<=3GB)
- OEM aggressive battery management
- User swiped app from recent apps
- Extended recording (>1 hour)

### Mitigation

1. Recording data up to kill point is preserved
2. Manually update session end time
3. Upload partial session if acceptable

### Permanent Fix

- Use `START_STICKY` for service restart (already implemented)
- Implement recording state recovery on restart
- Add periodic file sync/flush
- Persist recording state to survive process death
- Guide users to whitelist app from battery optimization

### Confidence

**MEDIUM** — `START_STICKY` in `onStartCommand:165` provides some protection, but state is lost. Service recovery would need explicit implementation.

---

## Pattern: Coroutine Scope Cancellation Data Loss

### Symptoms

- Telemetry samples dropped during recording
- File close fails silently
- Database updates not persisted
- Operations succeed from code path but data missing

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| `CancellationException` | Logcat | During IO operations |
| Truncated telemetry file | JSONL inspection | Missing expected samples |
| DB state inconsistent | Room queries | Missing updates |
| `serviceScope.cancel()` | Service lifecycle | Before IO completes |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| Service destroy during write | `TelemetryForegroundService:174` | `serviceScope.cancel()` |
| ViewModel clear during save | `RecordingViewModel:148-151` | `onCleared()` |
| Fragment destroy during async | Lifecycle mismatch | Operation in flight |
| Rapid start/stop | Quick succession | Race condition |

### Evidence To Check

1. **Logcat**: Search for `CancellationException` or `JobCancellationException`
2. **Telemetry file**: Check if last line is truncated/corrupt
3. **Room state**: Compare expected vs actual updates
4. **Lifecycle logs**: Service/Fragment lifecycle events
5. **Timing**: How quickly was stop triggered after start?

### Common Triggers

- User taps stop immediately after start
- Service killed while writing to disk
- App backgrounded during IO
- Configuration change during async operation
- Rapid navigation between screens

### Mitigation

1. Data written before cancellation is preserved
2. Retry from last known good state
3. Manual database cleanup may be needed

### Permanent Fix

- Use `NonCancellable` for critical IO: `withContext(NonCancellable + Dispatchers.IO)`
- Implement proper coroutine job hierarchy
- Add write-ahead logging for critical updates
- Use atomic file operations
- Add data integrity verification

### Confidence

**MEDIUM** — `serviceScope` uses `SupervisorJob()` (line 82) which provides isolation, but cancellation during `writeSample` (line 380) could still cause data loss if not handled with `NonCancellable`

---

## Summary: Pattern Priority Matrix

| Pattern | Likelihood | Impact | Detectability | Priority |
|---------|------------|--------|---------------|----------|
| Telemetry Upload Retry Exhaustion | HIGH | MEDIUM | HIGH | P1 |
| GPS Lock Failure | HIGH | HIGH | HIGH | P1 |
| Foreground Service Start Blocked | MEDIUM | HIGH | HIGH | P1 |
| DataStore ANR on Startup | ~~MEDIUM~~ MITIGATED | HIGH | HIGH | ~~P1~~ Closed (v2.8) |
| Session State Desync | MEDIUM | MEDIUM | MEDIUM | P2 |
| Auth Token Expiration Loop | MEDIUM | MEDIUM | HIGH | P2 |
| Telemetry File Missing | LOW | HIGH | HIGH | P2 |
| WorkManager Constraint Starvation | MEDIUM | MEDIUM | MEDIUM | P2 |
| Room Migration Failure | LOW | CRITICAL | HIGH | P2 |
| GPS Signal Lost | HIGH | LOW | HIGH | P3 |
| Service Killed by System | MEDIUM | MEDIUM | MEDIUM | P3 |
| Coroutine Cancellation Data Loss | LOW | MEDIUM | LOW | P3 |

### Cross-Pattern Correlations

| If you see... | Also check... |
|---------------|---------------|
| Upload failures | Auth expiration, file missing |
| GPS issues | Permission state, service state |
| ANRs | DataStore reads, main thread operations |
| State desync | Service lifecycle, binding state |
| Data loss | Coroutine cancellation, service kill |
| Startup crashes | Migration, DataStore corruption |
| Sensor permission crash | HIGH_SAMPLING_RATE_SENSORS on API 31+ |

---

## Pattern: HIGH_SAMPLING_RATE_SENSORS Permission Denial (Android 12+)

### Symptoms

- App crashes immediately when user taps "Start Recording"
- `SecurityException` in logcat mentioning `HIGH_SAMPLING_RATE_SENSORS`
- Crash occurs after GPS registration succeeds but before sensor registration
- Only affects Android 12+ (API 31+) devices

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| "SecurityException: To use the sampling rate of 0 microseconds" | Logcat crash | Any occurrence |
| `SystemSensorManager$BaseEventQueue.enableSensor` in stack | Crash trace | Any occurrence |
| Crash at `TelemetryForegroundService.startRecording` | Stack trace | Line ~225-240 |
| Android version ≥ 12 (API 31+) | Device info | Required |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| Missing HIGH_SAMPLING_RATE_SENSORS permission | AndroidManifest.xml | Permission absent |
| SENSOR_DELAY_FASTEST without permission | TelemetryForegroundService:225-240 | registerListener() call |

### Evidence To Check

1. **AndroidManifest.xml**: Verify `HIGH_SAMPLING_RATE_SENSORS` permission is declared
2. **Logcat**: Filter for `SecurityException` + `HIGH_SAMPLING_RATE_SENSORS`
3. **Device API level**: Confirm device is running Android 12+ (API 31+)
4. **Sensor registration code**: Check sampling rate parameter

### Common Triggers

- App installed on Android 12+ device without the permission declared
- App targeting API 31+ using SENSOR_DELAY_FASTEST

### Mitigation

1. **Immediate**: Add `<uses-permission android:name="android.permission.HIGH_SAMPLING_RATE_SENSORS" />` to manifest
2. **Defensive**: Wrap sensor registration in try-catch with fallback to SENSOR_DELAY_GAME

### Permanent Fix

- Declare `HIGH_SAMPLING_RATE_SENSORS` in AndroidManifest.xml (normal permission, auto-granted)
- Add defensive try-catch around sensor registration with fallback sampling rate
- Log warning when fallback is used for monitoring

### Confidence

**HIGH** — Android framework explicitly states the missing permission in the exception message.

### Resolution History

| Date | Fix Applied |
|------|-------------|
| 2026-05-28 | Added HIGH_SAMPLING_RATE_SENSORS permission + defensive try-catch fallback |

---

## Pattern: Sentinel Value Treated as Valid Data (FP-SENTINEL-VALUE)

### Symptoms

- Coach tab displays "0ms quicker than average" as sector praise
- Sector-specific insights generated when no sector data exists
- Consistency insight says "Laps vary by X" but shows standard deviation
- Insights appear technically correct but are semantically meaningless

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| "0ms quicker than average" | Coach tab UI | Any occurrence |
| `sector1Ms = 0, sector2Ms = 0, sector3Ms = 0` | Room `laps` table | All sectors zero for local laps |
| `isLocalOnly = true` | Room `laps` and `coaching_insights` | Indicates offline-generated data |
| Sector insight headline for local session | Coach tab | Local sessions have no sector data |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| Missing sector data stored as 0L | `RecordingViewModel.saveLapsToRoom():260-280` | `sector*Ms = 0L` with comment |
| No guard for zero/missing sectors | `OfflineCoachingEngine.generateBestLapInsight():48-65` | Arithmetic on zeros |
| No nullable sector representation | `LapEntity.kt` | `sector*Ms: Long` (non-nullable) |
| No test coverage for zero sectors | `OfflineCoachingEngineTest.kt` | All tests use non-zero sectors |

### Evidence To Check

1. **Room DB**: `SELECT id, sector1Ms, sector2Ms, sector3Ms, isLocalOnly FROM laps WHERE sessionId = ?`
2. **Room DB**: `SELECT headline, detail FROM coaching_insights WHERE sessionId = ? AND isLocalOnly = 1`
3. **Logcat**: Filter `TAG:RecordingViewModel` for "Generated X offline coaching insights"
4. **Source**: Verify `DetectedLap` class has no sector fields
5. **Source**: Verify `saveLapsToRoom()` writes `sector*Ms = 0L`

### Common Triggers

- User completes a session using local lap detection (no backend processing)
- User opens Coach tab before backend processing completes
- Offline usage without network connectivity

### Root Cause

The system uses `0L` as a sentinel value to represent "no data" but the coaching engine interprets it as "measured value of 0ms". This violates the principle that missing data should be explicitly represented (null) rather than using magic values.

### Causal Chain

```
DetectedLap has no sector fields (design decision)
    ↓
saveLapsToRoom() writes sector*Ms = 0L (sentinel)
    ↓
OfflineCoachingEngine.generateBestLapInsight() calculates:
  - avgS1 = average of all zeros = 0.0
  - gainS1 = 0.0 - 0 = 0
    ↓
maxByOrNull selects Sector 1 as "best" (first of equal values)
    ↓
Output: "You nailed Sector 1 — 0ms quicker than average"
```

### Mitigation

1. **Immediate**: Add guard in `OfflineCoachingEngine`:
   ```kotlin
   private fun areSectorsAvailable(laps: List<LapEntity>): Boolean =
       laps.all { it.sector1Ms > 0 && it.sector2Ms > 0 && it.sector3Ms > 0 }
   ```

2. **Suppress sector insights**: When `!areSectorsAvailable(laps)`:
   - Replace sector detail with lap-only insight
   - Skip `generateSectorFocusInsight()` entirely

3. **Fix consistency wording**: Either:
   - Change calculation to `max(durationMs) - min(durationMs)` (range)
   - Change copy to "Standard deviation: Xms"

### Permanent Fix

1. **Schema change**: Make `LapEntity.sector*Ms` nullable (`Long?`)
2. **Persist null**: `saveLapsToRoom()` uses `null` instead of `0L`
3. **Guard all consumers**: Check for null before sector calculations
4. **Add tests**: Cover zero/null sector scenarios in `OfflineCoachingEngineTest`

### Confidence

**HIGH** — Direct code path traced from `DetectedLap` → `saveLapsToRoom()` → `OfflineCoachingEngine`

### Related RCA

- `03_incidents/06_coaching_incidents/06_RCA_invalid_sector_coaching.md`

### Resolution History

| Date | Fix Applied |
|------|-------------|
| (pending) | Sector guard + wording fix required |

---

## Pattern: DeadSystemException (System Server Crash)

### Symptoms

- App crashes with `DeadSystemException`
- Emulator/device restarts unexpectedly
- Active recording session lost
- Logs show "The system died; earlier logs will point to the root cause"

### Signals

| Signal | Location | Threshold |
|--------|----------|-----------|
| `DeadSystemException` | Crashlytics/Logcat | Any occurrence |
| "Binder transaction failure: -3" | Logcat `IPCThreadState` | DEAD_OBJECT error |
| "The system died" | Logcat `AndroidRuntime` | Fatal exception |
| ANRs in `com.google.android.gms.*` | Logcat `ActivityManager` | Multiple in succession |
| Process killed with SIG 9 | Logcat `Process` | SIGKILL after exception |

### Likely Causes

| Cause | Code Path | Evidence |
|-------|-----------|----------|
| Emulator GMS instability | Google Play Services | ANRs in gms.persistent/unstable |
| system_server overload | Android framework | High CPU in system_server |
| DroidGuard service hang | Security attestation | ANR in DroidGuardService |
| Emulator resource exhaustion | Virtual environment | Memory/CPU pressure |

### Evidence To Check

1. **Logcat**: Look for ANRs in `com.google.android.gms.*` preceding the crash
2. **Timeline**: Check for cascading ANRs before `DeadSystemException`
3. **CPU stats**: Check `/proc/pressure/cpu` in ANR dumps
4. **Binder logs**: Look for "Binder transaction failure" with error -3 (DEAD_OBJECT)
5. **Environment**: Was this on emulator or physical device?

### Common Triggers

- Extended recording session on Android emulator
- Google Play Services background service instability
- Emulator lacking hardware attestation (DroidGuard issues)
- High-frequency sensor polling on under-resourced emulator
- Multiple ANRs overwhelming system_server

### Mitigation

1. **Emulator**: Restart emulator, use physical device for long sessions
2. **Physical device**: Restart device (rare on real hardware)
3. **Data recovery**: Check telemetry file — periodic flush may have saved partial data

### App-Level Handling

The app handles `DeadSystemException` gracefully in `DrivingCoachApp.kt`:
- Global uncaught exception handler detects system death
- Logs gracefully instead of showing ugly crash report
- Allows process to terminate cleanly (system will restart everything)

```kotlin
if (isSystemDeathException(throwable)) {
    Log.e(TAG, "System crash detected (DeadSystemException)...")
    // Don't invoke default handler — let process die quietly
}
```

### Permanent Fix

1. **Already implemented**: Periodic telemetry flush every 30 seconds minimizes data loss
2. **Already implemented**: `DeadSystemException` handler for graceful termination
3. **Testing**: Use physical devices for QA of extended recording sessions
4. **Documentation**: Note emulator GMS instability as known limitation

### Confidence

**MEDIUM** — This is an Android system-level failure, not an app bug. The app cannot prevent it but can minimize impact through defensive measures (periodic flush, graceful exception handling).

### Related RCA

- `03_incidents/10_emulator_system_crash/10_RCA_emulator_system_server_crash.md`

### Resolution History

| Date | Fix Applied |
|------|-------------|
| 2026-07-22 | Periodic flush (30s) + DeadSystemException handler |
