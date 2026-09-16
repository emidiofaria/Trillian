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

## Pattern: Location Subscription Not Restored After Stop — ✅ FIXED (GPS warm-up work)

### Symptoms (historical)

- Track Setup shows "Acquiring GPS…" **forever**, not just for a cold-fix duration
- Satellite count and accuracy frozen at their last values, or never populated
- Both CAPTURE buttons remain disabled indefinitely
- Backing out to Home and re-entering Track Setup clears it — the giveaway

### Signals

| Signal | Meaning |
|--------|---------|
| GPS status stuck after a screen-off | Subscription was torn down and never restored |
| Recovery only after re-navigating to the screen | View recreation, not resubscription, is what fixed it |
| Measured TTFF (About screen) small while the user reports a long wait | The wait was this defect, not GNSS physics |

### Likely Cause

`TrackSetupFragment` subscribed to location exactly once, from `onViewCreated()`, while
`onStop()` removed updates. The fragment view survives a stop/start cycle, so
`onViewCreated()` does not run again — nothing ever resubscribed. Every screen-off,
notification pull or app switch therefore killed location updates permanently. This is
overwhelmingly likely while walking to the track edge.

### Common Triggers

- Screen timeout / manual screen-off while walking to the start/finish line
- Switching apps (camera, messages) mid-setup
- Any system dialog that stops the activity

### Permanent Fix — IMPLEMENTED

`TrackSetupFragment` now collects through
`viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` over the injected
`LocationUpdates` abstraction. The subscription is structurally tied to the STARTED state:
it is torn down on stop and recreated on start, with no manual `onStop()` override to get
out of sync. The former `FusedLocationProviderClient`/`LocationCallback` pair was removed.

### Regression Guard

`TrackSetupResubscribeTest` (L2) drives the activity to `CREATED` and back to `RESUMED` and
asserts the subscription count increases and capture still unlocks on an accurate fix. With
the old code the count stays at 1 and the screen never recovers.

### Generalisation

Any `Flow` subscription created in `onViewCreated()` and cancelled in `onStop()` has this
bug. Audit for the pattern rather than fixing instances one at a time — `repeatOnLifecycle`
is the correct construct.

### Confidence

**HIGH** — Defect and fix both verified in `TrackSetupFragment`; guarded at L2.

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

---

## Pattern: Brand Asset Geometry (Unverified Static Artwork)

The first *static asset* pattern in this Atlas. Every other pattern here
describes runtime behaviour; this one ships broken and never throws.

### Symptoms

- A brand asset renders visibly wrong — squashed, off-centre, clipped or blank.
- The defect appears on **every** surface referencing the resource at once.
- Nothing crashes, no exception is logged, and no test fails.
- Frequently reported by a human looking at the screen, not by telemetry.

### Signals

| Signal | Source | Meaning |
|--------|--------|---------|
| No signal at all | — | **This is the defining characteristic.** Static assets emit nothing |
| `content aspect ... FAIL` | `brand-asset.py check` | Artwork geometry is out of tolerance |
| Bucket dimension mismatch | `BrandAssetGeometryTest` | Shipped densities drifted from the master |

### Likely Causes

| Cause | Detail |
|-------|--------|
| Hand-authored geometry with no shared construction reference | Paths drawn independently, with no centre line, silhouette or bounding constraint to check against |
| Merged on code inspection | `pathData` strings are unreadable, so review approves text that was never rendered |
| Presence-only test assertions | `isDisplayed()` passes for any drawable, including a blank one |
| Non-premultiplied RGBA downsample | Backing colour bleeds into edge pixels, producing a fringe on dark backgrounds |
| Same-name vector and bitmap | Resource merger picks one non-deterministically |

### Evidence To Check

1. Render the asset **in isolation, composited**, at every shipped size — not in
   an IDE preview of a single layer.
2. Measure the content bounding box. Compare its aspect and centre against the
   canvas. This is the single most diagnostic measurement.
3. Measure each layer's spill outside the intended silhouette *before* assuming
   misalignment. In Incident 11 that hypothesis was wrong: 7 of 8 layers spilled
   0.0 %, and the shell itself was the defect.
4. Check the containing views for `scaleType` and fixed-size mismatches, then
   rule them out explicitly rather than leaving them as a suspicion.
5. Read the assertions that cover the asset and ask whether any of them
   *could* have failed while the defect was present.

### Common Triggers

- A branding or visual-identity commit.
- Replacing artwork without regenerating every density bucket.
- Adding a bitmap alongside a same-named vector.

### Mitigation

Measure the artwork, do not inspect its source. The gate must assert geometry:
square canvas, content aspect within tolerance, centred content, transparent
border, and legibility at the smallest render site.

### Permanent Fix

| Control | Implementation |
|---------|----------------|
| Geometry gate | `BrandAssetGeometryTest` (L1) measures the committed master |
| Falsification | `gateRejectsTheLegacyDeformedEmblem` runs the same assertions against the known-bad asset and requires them to fail |
| Reproducibility | `brand-asset.py build` regenerates every bucket bit-for-bit from `docs/brand/helmet_source.png` |
| Conflict guard | Asserts no same-named vector coexists with the bitmaps |

### Generalisation

**An assertion that cannot fail when the defect is present is not coverage.**
This is the second time this repository has hit that failure of reasoning — the
first was Espresso main-thread assertions that could not observe an ANR. Any new
gate should be run against a known-bad input before it is trusted.

### Confidence

**HIGH** — root cause measured, both original hypotheses falsified.

### Related RCA

`03_incidents/11_helmet_emblem_deformed/11_RCA_unconstrained_hand_authored_vector.md`

### Resolution History

| Date | Action |
|------|--------|
| 2026-08-11 | Incident 11 raised: helmet emblem deformed on all surfaces |
| 2026-08-11 | RCA falsified both reported hypotheses; shell measured 88x76 in a 120x120 viewport |
| 2026-08-11 | Vector replaced with owner-supplied raster across 5 density buckets; L1 geometry gate added and proven falsifiable |

### Open Gap

The repository has **no CI** (`.github/workflows/` does not exist), so this gate
only runs when someone runs it locally.

---

## Pattern: Timing Budget Anchored to the Wrong Instant (FP-TIMING-ANCHOR)

### Symptoms

- A screen with a configured minimum display time is visibly shorter than that number.
- Raising the configured number helps only partially, and the shortfall returns whenever
  device or startup speed changes.
- Automated tests pass: they assert the *budget was applied*, which it was.

### Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| `D/SplashViewModel: splash visible Nms after start` | Logcat | Size of the gap being (mis)charged to the budget |
| `I/ActivityTaskManager: Displayed <pkg>/<activity>: +Xms` | Logcat | Platform's own first-frame marker — the ground truth |
| Gap between the two above | Derived | The error in the anchor. Should be tens of ms, not hundreds |
| `W/SplashViewModel: handoff never reported` | Logcat | Fallback path taken; budget silently reverts to the old, short behaviour |

### Root Cause

On Android 12+ (and through `androidx.core:core-splashscreen` below it) a cold start shows
**two** screens: the platform splash window, which the OS holds until the activity's first
frame is composited, and then the app's own screen. Any clock started in `onCreate`,
`onViewCreated`, or a ViewModel `init` is running while the *platform* splash is still on
screen. A budget measured from there is partly spent on a screen the user cannot read.

### Why It Recurred

The first fix moved the anchor to `View.doOnPreDraw`, which *sounds* like a first-frame
signal and is not: it fires during the fragment's layout pass, still ~1.0–1.4 s before the
window is presented. Measured error by anchor:

| Anchor | Error | Delivered from a 4000 ms budget |
|--------|-------|--------------------------------|
| ViewModel `start()` | ~1700 ms | ~2.3 s |
| `View.doOnPreDraw` | ~1000–1400 ms | ~2.6–3.0 s |
| `SplashScreen.setOnExitAnimationListener` | ~17 ms | ~4.0 s |

Worse, the second attempt's own evidence — "the anchor fires 1.0–1.4 s *before* `Displayed`"
— was read as confirmation rather than as the remaining defect. A signal firing *before*
the ground-truth marker is early by definition.

### Mitigation

- Anchor to `SplashScreen.setOnExitAnimationListener`; it fires exactly at the handoff.
  Remember to call `provider.remove()`, or the platform splash never goes away.
- Always validate a timing anchor against `ActivityTaskManager: Displayed`. It is free,
  it is the platform's own measurement, and it is not subject to the same mistake.
- Keep a bounded fallback so a missing signal degrades the hold rather than hanging it.
- Do **not** try to assert this arithmetic with Espresso. Espresso waits for the main thread
  to fall idle before it looks, and on a loaded emulator that wait can outlast the window
  being measured — the assertion then fails without a defect. Gate the arithmetic at L1 with
  an injected clock, and mutation-verify it.

### Confidence

**HIGH** — measured on device across repeated cold starts; the corrected anchor agrees with
the platform's own first-frame marker to ~17 ms.

### Resolution History

| Date | Action |
|------|--------|
| 2026-08-11 | Introduction window shipped with the budget anchored to `start()` |
| 2026-08-12 | User reported the manifesto still unreadable; `doOnPreDraw` anchor measured ~1.2 s early |
| 2026-08-12 | Re-anchored to the platform splash exit via `SplashVisibilitySignal`; error now ~17 ms |

---

## Pattern: A Suite That Looks Green Because Its Tests Never Ran (FP-TEST-BLINDSPOT)

### Symptoms

- `connectedDebugAndroidTest` reports **0 failures** and the run is called a success.
- The JUnit XML contains far fewer `<testcase>` entries than there are `@Test`
  functions in the source, but nobody notices because nothing compares the two.
- A requirement is cited as covered by a test that has not executed in months.
- Test count in the XML is oddly round and lower than expected — 64 entries for
  89 declared tests.

### Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| `<testcase name="null" classname="com.example.FooTest" />` | JUnit XML | **The whole class is `@Ignore`d.** Gradle emits exactly one entry for it regardless of how many tests it holds |
| Declared `@Test` count ≠ executed count | `generate-html-report.py` output | The difference is the number of tests silently not running |
| `[INFO] 322/351 tests executed, 29 never ran` | Report stdout | The gap, stated |

### Root Cause

A class-level `@Ignore` collapses an entire test class into a **single skipped
entry** in the JUnit XML. The runner does not enumerate what it skipped, so the
information about how many tests were disabled is destroyed at the moment it
would be most useful.

The failure is one of measurement, not of code. Every tool downstream —
Gradle's console summary, CI status, a coverage table written by hand — reads
the XML and faithfully reports what it says. Nothing lies; the question is
simply never asked. Four classes hid **29 tests** this way:

| Class | Hidden tests |
|-------|--------------|
| `EndToEndTest` | 5 |
| `RecordingFragmentTest` | 7 |
| `TelemetryForegroundServiceTest` | 6 |
| `TrackSetupFragmentTest` | 11 |

The compounding harm is traceability: 8 coverage claims pointed at tests inside
those classes. Coverage looked earned while nothing was verifying it.

### Mitigations

- **Count the declared tests from the source, not from the run.** The report
  parses `@Test` out of the Kotlin files and prints declared and executed side by
  side. A gap cannot hide, because a suite that skips everything scores zero
  rather than passing.
- **Parse with a brace-depth stack, not line-by-line.** A private fake declared
  inside a test class will otherwise absorb every `@Test` below it and the
  totals will quietly stop matching. This bug was real: `FailingDataStore`
  nested in `DriverProfileStoreTest` swallowed 22 tests until the parser
  tracked nesting.
- **Match `@Test` on a word boundary** (`^@Test\b`). A naive `startswith("@Test")`
  also matches `@TestInstallIn` and inflates the declared count — here by 2.
- **Resolve every coverage claim against actual results.** A claim on a skipped
  test resolves to `SKIPPED`, never `PASS`, and is listed as unbacked.
- **Treat a class-level `@Ignore` as debt with an owner**, not as a neutral
  state. It is invisible by construction; only an external count makes it visible.

### Confidence

**HIGH** — found 2026-09-09 while building the HTML test report. Verified by
arithmetic: 89 declared − 29 ignored = 60 real, and the XML holds 64 entries,
being those 60 plus 4 `name="null"` placeholders.

### Status

⚠️ **OPEN.** The measurement gap is closed — every report states it plainly —
but the 29 tests are still switched off and 8 requirement claims still rest on
them.

---

## Pattern: Instrumented Test Hangs Forever Instead of Failing (FP-TEST-HANG)

### Symptoms

- A `connectedAndroidTest` run reaches `TestRunner: started: <test>` and then produces no
  further output. Ever. No failure, no timeout, no stack trace.
- The emulator is alive and responsive; `adb devices` is healthy.
- CI or a human eventually kills the run, and the JUnit XML shows an empty `<failure></failure>`
  or nothing at all.

### Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| `I/MonitoringInstr: Stubbing intent Intent { act=MAIN ... cmp=<pkg>/.testing.HiltTestActivity }` | Logcat | **The test stubbed its own host activity launch.** The screen will never appear |
| `testTimeoutSeconds=31536000` in `AndroidJUnitRunner: onCreate Bundle[...]` | Logcat | The default per-test timeout is *one year* — nothing will rescue a stuck test |
| Last `TestRunner: started:` with no matching `finished:` | Logcat | Identifies the exact stuck test |

### Root Causes

| Cause | Mechanism |
|-------|-----------|
| `Intents.intending(anyIntent())` called **before** launching the screen | Espresso-Intents stubs *every* outgoing intent, including the one `launchFragmentInHiltContainer`/`ActivityScenario` uses to start the host activity. The activity never starts and the launch blocks forever |
| An Espresso call wrapped in a polling loop (`awaitUntil { runCatching { onView(...) } }`) | `onView` blocks internally until the UI thread is idle. Wrapping it cannot impose a deadline — the loop never gets control back, so the wrapper's timeout is decorative |
| A never-ending animation on screen | Espresso synchronises on UI-thread idleness. An indeterminate `ProgressBar` (upload spinner, loading indicator) never lets the thread go idle |

### Mitigations

- **Never stub `anyIntent()` before the screen is up.** Call `Intents.init()` *after* the
  fragment/activity is displayed and stub the narrowest matcher that works — for a share
  sheet, `hasAction(Intent.ACTION_CHOOSER)`.
- **Never wrap `onView`/`intended` in a retry loop for synchronisation.** Espresso already
  waits. Use a bare call and let it fail. Use polling only for genuinely off-thread results
  (work handed to `Dispatchers.IO`), and accept that such a poll is bounded only if the
  Espresso call inside it can return.
- **Disable animations on the emulator.** `start-emulator.sh` now sets
  `window_animation_scale`, `transition_animation_scale` and `animator_duration_scale` to 0.
  This is a documented Espresso prerequisite, not an optimisation.
- **Seed test state that avoids indeterminate spinners** (e.g. a `COMPLETE`/`DONE` session
  rather than one mid-upload).
- When a run does hang, read `adb logcat -s TestRunner:I` for the last `started:` line, then
  the surrounding logcat for `MonitoringInstr`. Do not guess.

### Confidence

**HIGH** — reproduced and fixed on 2026-08-26 while adding `SessionShareTest`; the
`Stubbing intent ... HiltTestActivity` line named the cause exactly.

---

## Pattern: WorkManager Uninitialised Under Instrumentation (FP-TEST-WORKMANAGER)

### Symptoms

- Instrumentation dies with `Process crashed.` almost immediately after a test starts.
- Gradle reports a failure with an empty `<failure></failure>` body; `adb logcat -b crash` is empty.
- Only tests that open a screen backed by a WorkManager-injecting ViewModel are affected.

### Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| `IllegalStateException: WorkManager is not initialized properly.` | Logcat (`TestRunner`, `MonitoringInstr`) | The app disables `WorkManagerInitializer` in its manifest and initialises WorkManager itself — which never happens under instrumentation |
| `at ...TestNetworkModule.provideWorkManager` | Stack trace | Hilt tried to satisfy a `WorkManager` dependency |
| `W/ActivityManager: Crash of app com.drivingcoach running instrumentation` | Logcat | The whole test process, not just one test, is gone |

### Mitigation

`TestNetworkModule.provideWorkManager` falls back to
`WorkManagerTestInitHelper.initializeTestWorkManager(context)` when `getInstance` throws.
The test initialiser installs a test driver, so enqueued work stays parked behind its
constraints rather than firing real uploads during a UI test.

### Lesson

A crash inside DI takes down the entire instrumentation process, so the JUnit XML is
useless and the crash buffer may be empty. The stack trace exists **only** in the live
logcat main buffer — capture it with `adb logcat > file` *during* the run rather than
trying to recover it afterwards.

### Confidence

**HIGH** — observed and fixed on 2026-08-26.

---

## Pattern: Navigation Gate Reads State No Code Path Ever Writes (FP-UNREACHABLE-GATE) — ✅ FIXED (v2.9)

### Symptoms

- The driver enters the app successfully, uses it, closes it — and the next launch puts
  them back on a login screen they have no credentials for.
- Reported by users as "the app forgot me". Nothing crashes; no error is logged.
- Every launch after the first is affected, so it looks like a persistence bug in whatever
  screen the user last touched.

### Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| `SplashViewModel.UiState.destination == LOGIN` on every launch | Logcat / L2 `currentDestinationId()` | The gate is never satisfied |
| `jwt_token` absent from `driving_coach_prefs.preferences_pb` | DataStore dump | Nothing writes it |
| Grep for the preference key yields only *readers*, no *writers* | Source | The tell |

### Root Cause

Two independent defects that only manifested together:

1. `LoginFragment` "Skip Login (Demo Mode)" navigated to Home **while persisting nothing**.
   The user reached the app, so the shortcut looked like it worked.
2. `SplashViewModel` gated Home on `AuthInterceptor.KEY_JWT` — a token that V1, having no
   backend, never issues. The branch was structurally unreachable.

A third defect hid the inconsistency: `OnboardingFragment.completeOnboarding()` navigated
straight to Home, so the *first* launch bypassed the gate entirely and only later launches
exposed it.

### Mitigation

Gate on state the app actually writes. The startup decision now reads
`driver_profile_complete`, written atomically alongside `user_name` by
`DriverProfileStore.saveName()` — the same call that lets the driver into the app.
Entry and persistence became one operation instead of two that could disagree.

### Lesson

**A navigation gate must read state that some code path in the same build actually
writes.** When a feature is deferred (here: authentication), the gate keyed to it does not
become permissive — it becomes permanently closed, and any "skip" affordance around it
papers over the fault on first use while guaranteeing it on every subsequent launch.

Two cheap checks catch this class of bug:

- For every preference key a *decision* reads, grep for a writer. No writer = dead gate.
- Never let a UI shortcut grant access without writing the state that access implies.

### Automated Guard

`DriverNameFlowTest.aSavedDriverGoesStraightToHomeOnRelaunch` (L2) relaunches the activity
against a shared mutable DataStore and asserts Home. `theDemoModeShortcutIsGone` asserts
the `skipLoginButton` id no longer resolves, so the shortcut cannot return unnoticed.

### Confidence

**HIGH** — reproduced, root-caused, and fixed on 2026-09-03.

---

## Pattern: Resource Scoped to the Wrong Lifecycle (FP-LIFECYCLE-SCOPE) — ✅ FIXED (Incident 12)

### Symptoms

- A feature that exists to make one journey faster is *slowest* on exactly that journey.
- The user waits for a "ready" indicator, acts on it, and arrives to find the state gone.
- Nothing crashes and nothing is logged as an error. The screen simply says "acquiring" again.
- Users report it as "it got slower", often while rating an older build as better, because
  the regression is in perceived waiting rather than in any measurable failure.

### Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| `D/LocationWarmUp: warm-up started` twice with a `warm-up stopped` between | Logcat | The receiver was released and re-acquired during a navigation |
| `first fix after Nms` reported twice in one sitting | Logcat / About screen | The user paid the acquisition cost more than once |
| Test asserts `activeSubscriptions >= 1` after a navigation | Test source | The assertion cannot see the defect — it holds either way |

### Root Cause

A long-lived resource — the GNSS subscription — had its lifetime bound to the visibility of a
single screen (`HomeFragment.onStop()` → `LocationWarmUp.stop()`). The screen was the wrong
unit: the resource serves a *task* that spans two screens, so the handover between them became
the stop condition.

The component's own KDoc already claimed warmth survived Home → Track Setup. The design was
right; one caller violated it. Nothing in the type system or the test suite expressed the
claim, so the violation was invisible.

Ordering made it unrepairable from the receiving side: under Navigation's replace transaction
`onStop()` of the outgoing fragment runs *before* `onStart()` of the incoming one, and `stop()`
nulls the job that `start()`'s idempotence guard checks. Starting again in the new screen
therefore re-acquires from cold rather than inheriting a warm receiver.

### Why the suite did not catch it

`TrackSetupResubscribeTest` performed the exact failing navigation, then asserted
`activeSubscriptions >= 1`. That predicate is true whether or not the subscription survived,
because the arriving screen opens one of its own. **The interesting event happened between two
observations.** A sampled count cannot express continuity; only a timeline can.

The test doubles were equally blind: with no acquisition latency modelled, a receiver that
died and instantly re-acquired was indistinguishable from one that never stopped.

### Mitigation

| Action | Status | Evidence |
|--------|--------|----------|
| Scope the resource to the task, not the screen | ✅ Done | `WarmUpForegroundBinder`, `TelemetryForegroundService`, idle ceiling |
| Remove the screen's ability to release it at all | ✅ Done | `HomeViewModel` has no stop method; asserted by reflection in `HomeViewModelTest` |
| Give test doubles a subscription timeline | ✅ Done | `SubscriptionLog.everReachedZero` |
| Model acquisition latency on the receiver | ✅ Done | `ScriptedLocationUpdates.acquisitionLatencyMs`, scoped to the receiver rather than a subscription |
| Raise the idle ceiling above a real paddock walk | ✅ Done | 180 s → 1800 s |

### Generalisation

Ask of any long-lived resource: **whose lifetime is this, really?** If the answer is a task
that spans screens — a warm receiver, an open socket, a decoded model, a prepared camera — then
binding it to a `Fragment`, `Activity` or view lifecycle will make the handover between screens
its destruction point. That is usually the exact moment it is most needed.

Two corollaries, both learned here:

- **A guarantee that cannot be expressed as a test is not a guarantee.** `ProcessLifecycleOwner`
  was the idiomatic answer to "is the app in the foreground", but it does not dispatch `ON_STOP`
  under `ActivityScenario`, so the privacy bound could not be proven. A hand-written activity
  counter — equivalent in production, observable under test — was preferred for that reason
  alone.
- **`ActivityScenario.moveToState(CREATED)` does not background an app.** It stops the Activity
  by launching an empty one on top, which is exactly what navigating to another screen does. A
  test that used it to assert "backgrounding releases the resource" was in fact asserting the
  bug. Real backgrounding needs a launcher intent.

### Confidence

**HIGH** — root cause reproduced by test on the unfixed code (3 of 4 assertions failed as
predicted), then observed to flip green on the fix.

---

## Pattern: Stale Fix Captured as Ground Truth (FP-STALE-FIX) — ✅ FIXED (Incident 12, F4)

### Symptoms

- Lap times are plausible but consistently wrong for a whole session.
- The start/finish line sits tens of metres from where the user stood when capturing it.
- Nothing is reported by the user, because nothing looks broken. This is the failure mode's
  defining property: **a wrong number that looks right.**

### Root Cause

Nothing on the capture path checked a fix's *age*. The fused client may answer a new
subscription from cache; the screen retained the last fix it ever saw across screen-off and
app-switch cycles; and the readiness flag never fell once raised. Keeping the receiver warm
across the paddock walk (FP-LIFECYCLE-SCOPE, above) is correct, and it makes an old fix
reaching the capture screen an ordinary event rather than an exotic one — the two fixes had to
ship together.

### Mitigation

| Layer | Guard | Location |
|-------|-------|----------|
| Request | `setMaxUpdateAgeMillis(3 s)` | `FusedLocationUpdates` — every consumer inherits it |
| Arrival | Reject fixes not current on delivery | `TrackSetupFragment.onLocation()` |
| Restart | Discard the held fix when the collector restarts | `TrackSetupFragment.collectLocationUpdates()` |
| Click | Re-check at the moment of capture | `TrackSetupFragment.captureWithFreshFix()` |

The gate **waits, it never blocks**: the state clears on the next current fix with no user
action, and the screen says "Getting a current GPS fix…" rather than the misleading "Acquiring
GPS…". Where age cannot be established — an unset or future monotonic timestamp — the fix is
treated as current, because stranding the user at the track edge is the worse failure and the
≤10 m accuracy gate still applies.

`elapsedRealtimeNanos` is used rather than `getTime()`: the wall clock can be adjusted by the
network mid-session, the monotonic clock cannot.

### Confidence

**HIGH** — `StartLineFreshnessTest` fails on the code before the fix and passes after it.

---

## Pattern: A Direction Derived From Points Closer Than the Measurement Error (FP-DEGENERATE-BASELINE) — ✅ FIXED (Incident 13)

### Symptoms

- The app reports **"No laps detected. Complete at least 2 laps."** after a session in which
  the driver completed several laps.
- All coaching silently disappears with it: `generateOfflineCoaching()` never runs when there
  are no laps, so the user loses the entire value of the session, not just the timing.
- The telemetry is *good*. The path is complete, the track shape is obvious when plotted, and
  the car passes within a couple of metres of the start/finish on every lap.

### Signals

| Signal | Value in Incident 13 |
|--------|----------------------|
| Angle between captured start line and direction of travel | **0.1°–5.1°** |
| Closest approach to the start point, per pass | 0.5–2.1 m |
| Captured line length | 7.15 m |
| GPS accuracy in the same session | 1.4–15.0 m, mean **4.8 m** |
| Crossings found by the shipped algorithm | **0**, including against the *infinite* extension of the line |

### Root Cause

Track Setup asks the user to capture a point at each edge of the start/finish. Those points are
typically 5–10 m apart — the same order as the phone's own position error. The *position* of
their midpoint is a real measurement; the *direction* of the line between them is not. It is
noise.

The detector then used that direction as the thing a car must cross. When the noise happened to
point the line along the track instead of across it, no crossing was geometrically possible,
and no threshold value would have rescued it: extending the line to 10, 15, 20 or 30 m still
yields zero, because a car driving *along* a line does not cross it however long the line is.

### The generalisation worth keeping

> **Never derive a direction from two points separated by less than the measurement error.**

The distance between two GPS points is a measurement. The *bearing* between two GPS points a
few metres apart is not — the same instrument error that moves each point by ±5 m can swing the
bearing between them through any angle at all. This is a property of the arithmetic, not of the
particular phone, and it applies anywhere a heading, gradient, or alignment is computed from
closely spaced samples.

### Mitigation

| Layer | Guard | Location |
|-------|-------|----------|
| Geometry | Cross a plane **perpendicular to the car's direction of travel**, through the start point | `LocalLapDetector.detectCrossings()` |
| Extent | Accept only passes within `DETECTION_HALF_WIDTH_M` (15 m) laterally | same |
| Direction | Reject candidates more than `MAX_HEADING_DIFFERENCE_DEG` (60°) from the session's first accepted crossing | same |
| Precision | Interpolate the crossing instant between the two samples either side | same |
| Evidence | Write the detector's reasoning beside the telemetry | `LapDiagnosticsWriter` |

The direction of travel is measured over the whole lap — hundreds of metres — so it is a real
measurement, unlike the 7 m baseline it replaces. Replaying the incident session gives **4 laps
(79.83 / 77.08 / 77.26 / 83.44 s)**, and the answer is **identical for half-widths of 10 through
25 m**. That insensitivity is the point: the fix is a corrected model, not a tuned threshold.

Note what this removes: the user could previously *aim* the start line. They no longer can — but
that lever never worked, and believing it did is what produced this incident.

### Confidence

**HIGH** — `LapDetectionRealSessionTest` replays the actual failing session. It fails on the code
as shipped in v2.8 with the exact user-facing message, and passes after the fix.

**Confirmed in the field on v2.94 (2026-09-09):** acceptance test LD-CROSS-06 reproduced the
failing geometry deliberately — start/finish captured *along* the track rather than across it —
and laps were detected correctly. This matters more than the replay test: it shows the fix holds
against a live GNSS receiver rather than against one recorded session.

---

## Pattern: One Lap Counted Twice (FP-LAP-DOUBLE-COUNT) — ⚠️ ACCEPTED LIMITATION

### Symptoms

- Lap times are roughly **half** what the driver knows they drove.
- Twice as many laps as were actually completed.
- Nothing errors. The numbers look internally consistent, so this is again *a wrong number that
  looks right* — the same class as FP-STALE-FIX, and harder to notice than "no laps detected".

### Root Cause

This is the cost of the FP-DEGENERATE-BASELINE fix, stated openly. Because the crossing plane
now follows the car rather than a fixed line, a circuit that passes through the same point twice
per lap in different directions — a figure-of-eight, or a start/finish under its own bridge —
presents two valid crossings per lap.

There is a second, narrower case: where the start/finish sits **on a corner**, one pass can
produce two candidates about a second apart (car arriving, car leaving) because the plane
rotates with the car through the turn.

### Mitigation

`MAX_HEADING_DIFFERENCE_DEG = 60.0` rejects a candidate whose heading differs too far from the
session's first accepted crossing. This covers the corner case and the common figure-of-eight
(a return pass at ~180°). It does **not** cover a second pass at, say, 70°.

The threshold is 60° rather than the more obvious 90° for a concrete reason: at a corner
start/finish, arriving and leaving differ by *exactly* 90°, so a 90° threshold decided a real
case on floating-point rounding (observed: 90.00004 > 90). Real passes across the incident 13
fixture vary by only ~9°, so 60° is generous while being unambiguous.

### Why it is accepted rather than fixed

No venue currently in use has this geometry, and the alternative — reinstating a user-aimed line
— is what caused Incident 13. A layout-aware fix should wait until a track that needs it exists.

### Detection

`LapDiagnosticsWriter` records every accepted crossing with its heading. A session where lap
times look halved can be confirmed in seconds by reading the sidecar file: alternating headings
about 180° apart, or a cluster of crossings a second or two apart, identify it immediately.

### Confidence

**MEDIUM** — the corner case is covered by a test on a synthetic circuit. The figure-of-eight
case is reasoned, not measured, because no such recording exists.

Field testing on v2.94 (2026-09-09) found lap times and counts matching the drivers' own count,
so this pattern did **not** occur on the venues tested. That is absence of evidence on ordinary
circuit layouts, not evidence of absence on a crossing one — the limitation stands as written.

---

## Pattern: A Flag That Describes the Request Instead of the Result (FP-LABEL-VS-DATA) — ✅ FIXED (v2.95)

### Symptoms

- The screen shows a value that is correct for something the code *asked for*, next to a value
  computed from something else it actually *got*.
- Two instances were found in the ANALYSIS tab within an hour of each other, both by tests:
  - A lap whose recorded window did not overlap the telemetry was analysed over the **whole
    session**, while the caption above the map still read *"Reference: Lap 2"*. The numbers on
    screen were real, but they described a different stretch of driving than the label claimed.
  - A session whose telemetry file had been deleted still had a non-blank `rawFilePath`, so
    "do we have telemetry?" answered **yes** and the driver was told their lap was *"too short
    to analyse"* — a statement about their driving — when the truth was that the recording was
    gone.

### Signals

| Signal | Meaning |
|--------|---------|
| A boolean derived from a *request* parameter (`lapId != null`, `path != null`) | The flag cannot see failure |
| The same concept computed twice, in two places, from two sources | The two will diverge |
| A caption that is built before the data it captions | Ordering bug waiting to happen |

### Root Cause

The flag was computed from the **input** (`lapWindows[id] == null`, `filePath.isNullOrBlank()`)
rather than from the **outcome** (`lapRange == null`, `File(path).canRead()`). Inputs describe an
intention; only the outcome knows whether that intention survived contact with the device. When
the two disagree — a lap outside the file, a path to a file that was cleaned up — the UI reports
the intention and the numbers report the outcome.

### Fix

Compute the outcome first and derive every label from it:

- `analyzeSamples()` resolves `lapRange` *before* anything else and sets
  `isWholeSession = lapRange == null`; the lap caption is suppressed when the fallback fired.
- `AnalysisViewModel` probes `File(path).canRead()` on `Dispatchers.IO` and uses *that* as
  `hasTelemetryFile`, so a missing recording is reported as a missing recording.

### Detection

Both instances were invisible to the compiler and to a casual read; both were caught the first
time a test asserted on the *caption* rather than on the numbers. Any state where a message and a
measurement come from different sources deserves a test that reads the message.

### Generalisation

Whenever a UI string names the data it is describing — a lap, a file, a driver, a track — assert
that the name and the data have a single common origin. If a fallback can change which data is
used, the fallback must also change the name.

### Confidence

**HIGH** — both instances are reproduced by tests
(`SessionAnalysisGuardsTest.aLapWindowThatDoesNotOverlapTheTelemetryFallsBackToTheWholeSession`,
`AnalysisTabTest.missingTelemetryShowsTheEmptyStateInsteadOfCrashing`) that fail against the old
behaviour.

---

## Pattern: Geometry Derived While the Car Is Parked (FP-STATIONARY-GEOMETRY) — ✅ FIXED (v2.95)

### Symptoms

- Corners appear in the analysis that the driver never drove: a "turn" of **310°** with an apex
  speed of **0 km/h**, at the moment the phone was sitting in the paddock before the out-lap.
- The rest of the report is credible, which makes the phantom entries more damaging than an
  obvious failure: they are indistinguishable from real corners in the table.

### Signals

| Signal | Value observed |
|--------|----------------|
| Apex speed of the detected corner | 0.0 km/h |
| Total heading change | 310° |
| Distance travelled across the bearing window | < 1 m |

### Root Cause

This is [FP-DEGENERATE-BASELINE](#pattern-a-direction-derived-from-points-closer-than-the-measurement-error-fp-degenerate-baseline--fixed-incident-13)
reappearing in a second consumer. A stationary phone still reports a moving position — GPS scatter
of a few metres — and a bearing computed between two scattered fixes is *pure noise with a
plausible magnitude*. Integrated over thirty stationary seconds it looks exactly like sustained
cornering.

### Fix

Two guards in `SessionAnalysisProcessor`, both expressed in physical units rather than samples:

| Guard | Value | Rejects |
|-------|-------|---------|
| `MIN_BEARING_TRAVEL_M` | 2.0 m | A bearing measured over a distance smaller than the fix error |
| `MIN_CORNERING_SPEED_KMH` | 10.0 km/h | A "corner" whose fastest moment is a walking pace |

### Detection

Any derived heading must be accompanied by the distance it was measured over. If that distance is
not recorded, the value cannot be distinguished from noise after the fact.

### Generalisation

The lap detector learned this lesson in Incident 13; the analysis engine had to learn it again
because the guard lived in the lap detector rather than in a shared primitive. **Every** new
consumer of GPS-derived direction needs the same minimum-baseline check.

### Confidence

**HIGH** — covered by `SessionAnalysisGuardsTest`, which replays stationary scatter and asserts
that no corner is produced.

---

## Pattern: A Position Fixed While Standing Still (FP-STATIONARY-POSITION-BIAS) — ✅ FIXED (Incident 14)

### Symptoms

A driver completes several laps. The app reports **"No laps detected. Complete at least 2 laps."**
Every diagnostic looks healthy: the start line has a sensible length, its bearing is sensible, the
telemetry is dense and continuous, the reported accuracy is unremarkable, and the detector records
**no rejected crossings at all**. There is nothing to investigate and nothing was done wrong.

### Signals

Measured on the incident 14 session (`ines3`, 3 laps driven, 0 reported):

| Signal | Value | Reading |
|--------|-------|---------|
| Racing laps overlaid on each other | median **4.5 m**, max 8.7 m | GPS is *good* while moving |
| Reported movement while the kart stood still | **11.2 m** | the same receiver, stationary |
| Scatter about its own mean during capture | 9.2 m at 7.7 m *claimed* accuracy | the claim understates the error |
| Offset of start point, perpendicular to track | **15.9 m** | against a 15 m corridor |
| Offset of start point, along the track | 6.2 m | harmless |
| Lateral jump as the kart accelerated away | **~11 m in 2 s**, landing 0.3 m from the racing line | the bias collapsing |

The last row is the signature. The bias does not decay — it **vanishes the moment the receiver
gets velocity aiding**. Before that instant the whole opening of the session is drawn 10–20 m to
one side of a path the kart never took.

### Root Cause

The app *mandates* capturing the start line while standing still (TS-05, TS-07), then searches for
it with a 15 m corridor (LD-04). Standing still is the condition in which a consumer GPS receiver
is **least** able to place itself: with no Doppler velocity to constrain the solution, multipath
and atmospheric error express themselves as a slowly wandering position offset.

What makes this invisible is that **both endpoints are captured seconds apart and therefore share
the same bias**. The line's length (8.15 m) and bearing (171.6°) come out perfect. Every internal
consistency check passes. Only the *absolute* position is wrong, and nothing in the session has
anything to compare it against.

The driver then laps a track 16 m away from where the app believes the start is, and the corridor
— correctly — refuses every pass.

### The generalisation worth keeping

[FP-STATIONARY-GEOMETRY](#pattern-geometry-derived-while-the-car-is-parked-fp-stationary-geometry--fixed-v295)
established that a *direction* derived while parked is noise. This incident is the stronger claim:
**the position itself is biased while parked, and the bias is shared by everything captured in that
window** — so no amount of cross-checking captured values against each other can reveal it. A
stationary fix can only be validated against something measured *while moving*.

Corollary: an internal consistency check is not a validity check. The start line was
self-consistent and wrong.

### Fix

Three parts, in `LocalLapDetector`:

| Part | Mechanism | Why |
|------|-----------|-----|
| Say what was refused | `TOO_FAR_TO_THE_SIDE` recorded for passes within `REJECTION_REPORTING_RADIUS_M` (60 m) | The enum value already existed and was wired to nothing. The failure was silent because a comment had argued the rejection was "not noteworthy". |
| Correct it | Retry once against the midpoint projected perpendicularly onto the **driven** path, considering only stretches at ≥ `MIN_ANCHOR_SPEED_MS` (4 m/s) | The driven path is the only evidence in the session recorded under velocity aiding, and therefore the only thing the stationary fix can be corrected against. |
| Refuse to guess | Accept the projection only within `MAX_ANCHOR_PROJECTION_M` (20 m) **and** only if the retry yields ≥ 2 laps | Keeps it a correction, not a search. A line hundreds of metres out is a different fault and must fail loudly. |

The moving-only filter is load-bearing, not an optimisation: projecting onto the *whole* path lands
on the out-lap — a stretch visited once — and yields **0 laps at every corridor width**.

The fix is self-limiting by construction. On incident 13, whose line was captured while moving, the
projection would move the point **0.8 m**, so the fallback is never reached and that session's 4 laps
are untouched. On incident 14 it moves **16.1 m** and recovers **3 laps** at 104.2 / 85.8 / 94.5 s.

### Detection

Two numbers now ride in the diagnostics sidecar for every session: the speed and the positional
scatter during the capture window. Together they say whether the start line was fixed under velocity
aiding or not — which is the question that could not be answered when this incident was opened.

Watch for: `anchor: PROJECTED_ONTO_PATH` appearing routinely rather than exceptionally. That would
mean the capture workflow, not the receiver, is the thing to fix.

### Open risk

The 20 m bound consumed **80% of its budget** on the single real session that needed it (16.1 m).
Accepted deliberately: a session beyond the bound fails loudly and reports why, rather than
silently. If a second session approaches the bound, the capture workflow should change rather than
the number.

### Confidence

**HIGH** — `LapDetectionIncident14Test` replays the real session and asserts the recovered lap
count and times; `LocalLapDetectorGuardsTest` asserts the bound refuses a displaced line and that
incident 13 never reaches the fallback.

---

## Pattern: A Test That Cannot Fail (FP-HOLLOW-TEST) — ✅ RESOLVED (2026-09-16)

### Symptoms

The suite is green, the test count is reassuring, and the traceability matrix shows ✅ against
requirements nobody has verified. `BUILD SUCCESSFUL` is reported for a run in which entire
classes executed nothing. Coverage is quoted in meetings from a number that was never earned.

### Signals

Measured on the L2 suite as it stood on 2026-09-15:

| Signal | Value | Reading |
|--------|-------|---------|
| L2 tests declared vs executed | **89 declared, 60 executed** | 29 existed but were switched off |
| Classes carrying a class-level `@Ignore` | 4 | each with a different root cause |
| Ignored tests asserting only `isDisplayed()` | **14 of 29** | cannot fail when the app is broken |
| Coverage claims citing a non-running test | **8** | all 8 requirements had no second claim |
| Claims that were false even if revived | **SR-05** | cited class had no such assertion at all |
| Gradle exit code | **0** | reported success throughout |

The signature is the gap between *declared* and *executed*. A skipped class is invisible in a
pass/fail summary, so the deception is silent and survives indefinitely.

### Root Cause

Two distinct defects compounded:

1. **Tests were written to the shape of the screen, not to the behaviour of the requirement.**
   `capturePointAButton_isDisplayedAndClickable` was cited as evidence for TS-05, *"Point A shall
   be captured by tapping CAPTURE"*. It asserts the button exists and is clickable. It never
   captures a point. The assertion cannot fail while the defect is present, which is the
   definition of a hollow test.

2. **Disabling a test cost nothing.** `@Ignore` needs no approval, leaves the suite green, and
   leaves the coverage claim standing. The claim ledger and the run results were never reconciled,
   so a test could stop running without anything noticing.

The four blockers themselves were ordinary — `HiltTestActivity` not resuming, a missing
`launchFragmentInHiltContainer` migration, a service demanding real GPS, MockWebServer setup. None
was hard. They persisted because nothing forced the issue.

### Mitigation

The 29 tests were **deleted rather than revived**, and the 8 claims removed. This cost zero
assurance — none of them ran — while making the gap visible: V1 coverage fell from a reported 37%
to an honest 33%.

- **Prefer deletion to `@Ignore`.** A deleted test is visibly absent; an ignored one looks like
  deferred work forever. Git preserves the body (here, `0216475`).
- **Never claim a requirement from a test that cannot fail.** Ask: *if this behaviour broke, would
  this assertion turn red?* If not, it is not coverage.
- **Reconcile claims against results, not against source.** The HTML report already resolves every
  `coverage-map.tsv` claim to `PASS`/`SKIPPED`/`MISSING`. Read that, never the exit code.
- **Parse the JUnit XML.** `declared` vs `executed` is the only place a skipped class shows up.

### Related

- The service test was un-runnable because `TelemetryForegroundService` bypasses the
  `LocationUpdates` seam (TS-15) and calls `LocationManager.GPS_PROVIDER` directly. Restoring
  SR-09 coverage means putting it on that seam, as `StartLineFreshnessTest` already does.
- Same family as **FP-ASSERTION-THAT-CANNOT-FAIL** — see the brand-asset falsification guard,
  which exists precisely so a gate cannot be weakened into a no-op.
