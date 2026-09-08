# components.md

Operational component inventory for RCA localization in Driving Coach Android app.

---

## Component: Telemetry Recording Service

### Purpose

Foreground service that captures GPS and IMU (accelerometer/gyroscope) data at 10Hz during driving sessions. Runs independently of UI lifecycle with persistent notification.

### Key Code Areas

- `TelemetryForegroundService.kt` — main service, GPS/sensor listeners
- `RecordingState.kt` — sealed class state machine (Idle → Recording → Stopping → Error)
- `TelemetryFileWriter.kt` — thread-safe JSONL file writer
- `TelemetrySample.kt` — sample data class

### Dependencies

- `FusedLocationProviderClient` (Google Play Services Location)
- `SensorManager` (accelerometer, gyroscope)
- `SessionDao` — updates session end time
- `WorkManager` — enqueues upload on stop
- Android foreground service runtime

### Inputs

- Session ID (from `RecordingViewModel.createSessionAndStartRecording()`)
- Start line coordinates (optional, from `TrackSetupFragment`)
- `ACTION_START_RECORDING` / `ACTION_STOP_RECORDING` intents

### Outputs

- JSONL telemetry file at `files/telemetry/session_{id}.jsonl`
- StateFlow of `RecordingState` (observed by `RecordingViewModel`)
- Enqueued `TelemetryUploadWorker` on session end

### Data Persistence

| Mechanism | Interval | Purpose |
|-----------|----------|---------|
| Sample write | On each GPS fix (~100ms) | Append sample to buffer |
| Periodic flush | 30 seconds | Persist buffered data to disk |
| Close flush | On stop | Final flush before file close |

**Crash Resilience**: Periodic flush every 30 seconds ensures at most 30 seconds of telemetry data is lost if the app or system crashes unexpectedly. This protects against:
- System crashes (e.g., `DeadSystemException` from GMS instability)
- OOM kills
- Battery death
- User force-stop

### Runtime Intervals

| Runnable | Interval | Purpose |
|----------|----------|---------|
| `uiStateUpdateRunnable` | 100ms (10 Hz) | Smooth timer display in RecordingFragment |
| `notificationUpdateRunnable` | 1000ms (1 Hz) | Notification bar + GPS signal check |
| `gpsLockTimeoutRunnable` | 5000ms (once) | GPS lock timeout detection |
| `periodicFlushRunnable` | 30000ms | Telemetry file flush for crash resilience |

**Design Note**: UI state updates are separated from notification updates to provide smooth millisecond-precision timer display without excessive notification system calls (battery optimization).

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| GPS lock timeout | `RecordingState` stuck with `gpsLocked=false` | Indoor/poor sky view |
| GPS signal lost | Notification shows "GPS signal lost" | Tunnel, garage |
| Permission denied | Immediate `RecordingState.Error` | Runtime permission not granted |
| Write failure | Samples dropped, log error | Disk full, file system error |
| Service killed | Recording stops unexpectedly | Memory pressure, Android Doze |

### Observable Signals

| Signal | Location |
|--------|----------|
| `TelemetryService` tag | Logcat |
| "GPS lock timeout" | Logcat WARN |
| "GPS signal lost" | Logcat WARN / Notification |
| "Error writing telemetry sample" | Logcat ERROR |
| Notification channel `drivingcoach_recording` | System notification bar |
| `RecordingState.Error` | StateFlow observation |

### Evidence Sources

- Logcat filter: `TAG:TelemetryService`
- `files/telemetry/session_{id}.jsonl` — verify samples written
- `sessions` Room table — `startedAt`, `endedAt`
- System Settings → Running Services → `TelemetryForegroundService`

### Recovery/Mitigation

- GPS timeout: service continues; will lock when user moves to open sky
- GPS signal lost: automatic recovery when signal regained (10s threshold)
- Permission denied: user must grant permission, then retry
- Write failure: samples lost; no automatic retry
- Service killed: user must manually restart recording

### Criticality

**CRITICAL** — Core data capture; failure = no session data = no coaching

---

## Component: GPS Warm-Up (`LocationWarmUp`)

### Purpose

Starts GNSS acquisition as soon as the Home screen is visible so that the cold time-to-first-fix
(TTFF, typically 30–60 s: ephemeris download) elapses while the user is still preparing, instead
of on the Track Setup screen where they are standing at the track edge with nothing to do.
Publishes readiness for the Home hero chip and records TTFF metrics for diagnosis.

Implements SRS TS-16 to TS-23.

> **Lifetime is scoped to the user's task, not to a screen (Incident 12).** Warm-up used to be
> stopped by `HomeFragment.onStop()`, which made the single navigation it exists to serve —
> Home → Track Setup — its own stop condition. Three stop conditions replace it: the app leaving
> the foreground (`WarmUpForegroundBinder`), a recording starting
> (`TelemetryForegroundService`), and the idle ceiling as a backstop. No screen may call
> `stop()`.

### Key Code Areas

- `data/location/LocationWarmUp.kt` — `@Singleton` state machine; `start()` / `stop()` / `onFix()`
- `data/location/LocationUpdates.kt` — abstraction seam; `positionUpdates()` (raw `Location`) and `updates()` (reduced `LocationFix`)
- `data/location/FusedLocationUpdates.kt` — Play Services impl, `callbackFlow` + `PRIORITY_HIGH_ACCURACY`
- `data/location/GpsReadiness.kt` — `Idle | Acquiring | Ready(accuracyM)`, `READY_ACCURACY_M = 10f`
- `data/location/WarmUpTimings.kt` — `intervalMs = 1000`, `idleCeilingMs = 1_800_000`
- `data/location/WarmUpForegroundBinder.kt` — `@Singleton`; counts started activities via `Application.registerActivityLifecycleCallbacks`, calls `stop()` at zero
- `data/location/FixFreshness.kt` — `MAX_FIX_AGE_MS = 3_000`; monotonic age arithmetic, JVM-testable
- `data/location/GpsAcquisitionMetricsStore.kt` — DataStore-backed TTFF metrics
- `di/LocationModule.kt` — `@Binds LocationUpdates`, `@ApplicationScope CoroutineScope`, timings
- `ui/home/HomeViewModel.kt` / `HomeFragment.kt` — `start()` on `onStart()`; **no stop path exists**
- `ui/tracksetup/TrackSetupFragment.kt` — `start()` on `onStart()` so the receiver stays warm across the handover
- `ui/MainActivity.kt` — binds `WarmUpForegroundBinder` (not the Application class: `@HiltAndroidTest` replaces it)
- `ui/about/AboutFragment.kt` — renders the last acquisition metrics

### Dependencies

- `FusedLocationProviderClient` (Google Play Services Location)
- DataStore Preferences (metrics only)
- `@ApplicationScope CoroutineScope` — warm-up outlives a single fragment view

### Inputs

- `HomeFragment.onStart()`, `TrackSetupFragment.onStart()`, foreground transitions from `WarmUpForegroundBinder`
- Location fixes (accuracy in metres, timestamp)

### Outputs

- `StateFlow<GpsReadiness>` — consumed by `HomeViewModel.gpsReadiness`
- `GpsAcquisition(timeToFirstFixMs, timeToAccurateFixMs, recordedAtMs)` in DataStore

### Safety Invariant

**Readiness only — never a `Location`.** Handing a warm-up position to start-line capture would
let a stale fix become Point A and silently offset every lap time in the session. Capture
subscribes to live updates and applies its own ≤10 m gate (SRS TS-20).

### Design Notes

| Decision | Rationale |
|----------|-----------|
| Two-method `LocationUpdates` | `updates()` reduces to `LocationFix` so warm-up logic is JVM-testable; `android.location.Location` is a stub on the JVM and reports accuracy 0 |
| Idempotent `start()` | Repeated Home visits refresh the idle ceiling instead of stacking subscriptions. This is also why a screen-scoped `stop()` was so damaging: `stop()` nulls the job, so the guard cannot protect a handover it has already torn down |
| 30-minute idle ceiling | A backstop, not a UX bound. The previous 3 minutes was shorter than a real paddock-to-line walk, so it expired mid-journey and re-created the incident on a timer |
| Activity counter over `ProcessLifecycleOwner` | `ProcessLifecycleOwner` does not dispatch `ON_STOP` under `ActivityScenario`, so the privacy guarantee could not be proven by a test. An unverifiable guarantee is how Incident 12 reached HAT |
| `isChangingConfigurations` guard | Without it a rotation reads as a departure and releases the chip mid-walk — the same incident in a form that only appears if the user turns the phone |
| Secondary `@Inject constructor` | Dagger cannot inject the `now: () -> Long` clock lambda used by tests |

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| Permission not granted | Chip stays hidden, no warm-up | `hasPermission()` false — Track Setup still prompts |
| No fix indoors | Chip stuck amber "Acquiring GPS…" | No sky view; identical latency to pre-feature behaviour |
| Idle ceiling reached | Chip disappears after 30 min | By design; re-entering the app restarts |
| Stale fix held at the line | CAPTURE withdrawn, "Getting a current GPS fix…" | Fix older than `FixFreshness.MAX_FIX_AGE_MS`; clears on the next current fix (SRS TS-21 to TS-23) |
| Metrics write fails | About shows no acquisition data | DataStore I/O — caught, non-fatal |

### Observable Signals

| Signal | Location |
|--------|----------|
| GPS chip state (hidden / amber / green) | Home hero |
| Time-to-first-fix, time-to-accurate-fix | About screen |
| `GpsReadiness` | `HomeViewModel.gpsReadiness` |

### Criticality

**MEDIUM** — Not on the data-capture path. Failure degrades to the previous behaviour
(waiting for the fix on Track Setup); it cannot corrupt telemetry or lap times.

---

## Component: Local Lap Detector

### Purpose

Detects laps locally (offline) from a JSONL telemetry file using start/finish line crossing. Enables immediate lap time display after recording stops, without requiring network connectivity.

### Key Code Areas

- `LocalLapDetector.kt` — core detection algorithm, JSONL parsing
- `RecordingViewModel.kt` — triggers `processLapsLocally()` on stop
- `LapDao.kt` / `LapEntity.kt` — stores detected laps with `isLocalOnly` flag

### Dependencies

- `GeoUtils` — haversine distance, line intersection math
- `Gson` — JSON parsing
- Room database — lap persistence

### Inputs

- JSONL telemetry file (`files/telemetry/session_{id}.jsonl`)
- Start line coordinates (from session entity or file header)

### Outputs

- List of `LapEntity` records saved to Room with `isLocalOnly=true`
- Detection result (Success, InsufficientLaps, NoStartLine, Error)

### Algorithm

1. Read telemetry file (header + samples)
2. For each consecutive GPS sample pair, check if segment crosses start line
3. Apply guards: minimum 20s lap time, minimum 50m traveled from start
4. Build laps from valid crossings
5. Mark best lap, save to Room

### Configuration Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `MIN_LAP_TIME_MS` | 20,000 | Prevents GPS jitter false positives |
| `MIN_DISTANCE_FROM_START_M` | 50 | Ensures driver traveled around track (kart-track compatible) |
| `MIN_SAMPLES` | 50 | Minimum telemetry samples for valid detection |

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| No crossings detected | "No laps detected" message | Start line doesn't intersect GPS path |
| Insufficient laps | "Only 1 lap detected" | User didn't complete 2+ laps |
| File not found | Error result | Telemetry file missing or wrong path |
| Invalid start line | NoStartLine result | All coordinates are 0.0 |

### Observable Signals

| Signal | Location |
|--------|----------|
| `LocalLapDetector` tag | Logcat |
| "=== LAP DETECTION START ===" | Logcat DEBUG |
| "Detected X line crossings" | Logcat DEBUG |
| "Built X laps from crossings" | Logcat DEBUG |

### Criticality

**HIGH** — Enables offline user experience; failure = no lap times shown locally

---

## Component: TelemetryChartProcessor

### Purpose

Processes telemetry data into chart-ready speed vs distance data points for the Chart tab. Computes cumulative distance using GPS coordinates and supports downsampling for large sessions.

### Key Code Areas

- `TelemetryChartProcessor.kt` — core processing logic, distance computation
- `ChartData.kt` — `SpeedDataPoint` and `ChartProcessingMode` enum
- `ChartFragment.kt` — UI integration, processing mode dialog

### Dependencies

- `TelemetryFileReader` — reads samples from JSONL file
- `GeoUtils` — haversine distance calculation
- `LapEntity` — provides lap start/end timestamps

### Inputs

- JSONL telemetry file path (via `SessionUiState.rawFilePath`)
- Lap timestamps (`LapEntity.startTs`, `LapEntity.endTs`)
- Processing mode (FAST or DETAILED)

### Outputs

- `List<SpeedDataPoint>` — distance (meters) and speed (km/h) for charting
- MPAndroidChart `Entry` list for rendering

### Algorithm

1. Read telemetry samples within lap time range (`TelemetryFileReader.readRange()`)
2. If FAST mode, downsample to ~100 points per lap (uniform sampling)
3. Compute cumulative distance: for each sample, add haversine distance from previous sample
4. Convert speed: `speedKmh = speedMs * 3.6`
5. Return list of `SpeedDataPoint(distanceMeters, speedKmh)`

### Configuration Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `LARGE_SESSION_LAP_THRESHOLD` | 10 | Trigger processing mode dialog |
| `FAST_MODE_POINTS_PER_LAP` | 100 | Target points after downsampling |

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| Empty data | "No speed data available" message | No samples in lap time range |
| File not found | Error loading data | Telemetry file missing |
| Memory pressure | Slow rendering or ANR | Very large session (50+ laps) in DETAILED mode |

### Observable Signals

| Signal | Location |
|--------|----------|
| Processing mode dialog | UI when laps > 10 |
| Loading spinner | Chart tab during processing |
| "Offline processing is limited..." | Dialog message |

### Criticality

**MEDIUM** — Chart is a value-add feature; failure doesn't block lap times or coaching

---

## Component: Authentication Layer

> **V1 status: DORMANT.** V1 ships with no backend and no accounts. `LoginFragment` and
> `RegisterFragment` are unreachable placeholders (no nav action targets them), and no
> code path writes `jwt_token`. `AuthRepository`/`AuthInterceptor` remain wired for V2
> restoration. Local identity is owned by **Driver Profile Store** (below).
> Anything in this section describes intended V2 behaviour, not current runtime behaviour.

### Purpose

Handles user registration, login, token storage, and automatic token injection/expiration handling for all authenticated API calls.

### Key Code Areas

- `AuthRepository.kt` — login/register/signOut logic
- `AuthInterceptor.kt` — OkHttp interceptor for Bearer token injection + 401 handling
- `AuthEventBus.kt` — SharedFlow for session expiration events
- `DataStore<Preferences>` — persists JWT and user info

### Dependencies

- `ApiService` (Retrofit) — `/auth/login`, `/auth/register`, `/auth/me`
- `DataStore<Preferences>` — key-value storage

### Inputs

- User email + password (login/register)
- Network requests requiring auth (via interceptor)

### Outputs

- `AuthResult<Unit>` (Success/Error) from login/register
- JWT token persisted to DataStore
- `AuthEvent.SessionExpired` on 401 response

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| Invalid credentials | `AuthResult.Error(code=401)` | Wrong password/email |
| Network error | `AuthResult.Error("Network error")` | No connectivity |
| Token expired | 401 on any authenticated request | JWT past expiry |
| DataStore corruption | Token read fails | Rare; app data corruption |

### Observable Signals

| Signal | Location |
|--------|----------|
| HTTP 401 response | OkHttp logs (BODY level) |
| `AuthEvent.SessionExpired` | `AuthEventBus.events` Flow |
| HTTP 200 with `token` | Login/register response |
| DataStore keys | `jwt_token`, `user_id`, `user_name`, `user_email` |

### Evidence Sources

- Logcat filter: `OkHttp` or HTTP logging interceptor
- DataStore file: `driving_coach_prefs.preferences_pb`
- `AuthResult.Error` in UI (via ViewModel state)

### Recovery/Mitigation

- Token expired: `AuthInterceptor` clears token + emits `SessionExpired`; UI should navigate to login
- Network error: UI shows error; user retries
- Invalid credentials: user corrects input

### Criticality

**HIGH** — Blocked auth = blocked uploads/session fetch, but local recording still works

---

## Component: Driver Profile Store

### Purpose

Single owner of the app's V1 local identity. V1 has no accounts, so identity is reduced
to one fact — what to call the driver — plus a flag saying that fact has been captured.
Existed to fix the defect where "demo mode" entered Home while persisting nothing, so
every relaunch bounced the driver back to a login screen they could not use.

### Key Code Areas

- `data/profile/DriverProfileStore.kt` — validation, persistence, initials derivation
- `ui/driver/DriverNameViewModel.kt` — first-run naming state machine
- `ui/driver/DriverNameFragment.kt` + `res/layout/fragment_driver_name.xml` — the "LET'S RACE!!" screen
- `ui/profile/ProfileViewModel.kt` — rename and Clear User Data
- `ui/splash/SplashViewModel.kt` — consumer; resolves the startup destination

### Dependencies

- `DataStore<Preferences>` — the only persistence used
- `@IoDispatcher CoroutineDispatcher` — all reads/writes move off the main thread

### Inputs

- Raw display name typed by the driver (untrimmed, unvalidated)
- `Preferences` snapshot supplied by `SplashViewModel` via `profileFrom(prefs)`

### Outputs

- `DriverProfile(displayName)` or `null` when no profile exists
- `NameValidation` = `Valid(value)` | `TooShort` | `TooLong`
- Preference keys `user_name` (String) and `driver_profile_complete` (Boolean), written
  atomically in one `edit` block

### Design Constraints

| Constraint | Reason |
|---|---|
| Two keys, not one | An interrupted rename must never look like "first run". Inferring completion from `user_name != null` would drop an established driver into setup with history apparently gone. |
| Name is cosmetic only | Sessions stay keyed to `demo_user` (SRS DP-01). Keying on an editable name would orphan history on a typo fix. |
| `profileFrom(Preferences)` overload | Lets `SplashViewModel` reuse its single startup preferences snapshot instead of a second DataStore read on the cold-start critical path (SRS UI-04). |
| Code-point-safe initials | Emoji and astral-plane names must not produce mojibake in the avatar. |

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| Preferences read throws | `readProfile()` returns `null`; driver is asked to name again | DataStore file corruption |
| Save throws | Error text under the field; driver stays on the naming screen (never advances to Home unpersisted) | Disk full / IO error |
| Name below 2 or above 100 chars | Primary action stays disabled | Validation (SRS DR-02) |

### Observable Signals

| Signal | Location |
|--------|----------|
| DataStore keys `user_name`, `driver_profile_complete` | `driving_coach_prefs.preferences_pb` |
| `DriverProfileStore` tag | Logcat, on read/write failure |
| Startup destination `DRIVER_NAME` | `SplashViewModel.UiState.destination` |

### Automated Verification

| Level | Test | Guards |
|---|---|---|
| L1 | `DriverProfileStoreTest` (22) | Bounds 1/2/100/101, trimming, round-trip, flag independence, degraded read, initials |
| L1 | `DriverNameViewModelTest` (12) | Submit gating, double-tap, failed-save containment |
| L1 | `ProfileViewModelTest` (11) | Rename never touches the database; `clearUserData` ordering |
| L1 | `SplashViewModelTest` | Destination resolution across all profile states |
| L2 | `DriverNameFlowTest` (6) | First-run naming, **relaunch remembers the driver**, trimming, demo-mode shortcut absent |

### Criticality

**HIGH** — Gates every launch. A failure here either blocks entry to the app entirely or
silently resets a driver to first-run state.

---

## Component: Networking Layer

### Purpose

Retrofit/OkHttp stack for REST API communication with backend. Handles timeouts, logging, and authentication via interceptors.

### Key Code Areas

- `ApiService.kt` — Retrofit interface (auth, telemetry, sessions)
- `TelemetryApiService.kt` — upload-specific endpoint
- `di/NetworkModule.kt` — Hilt DI provider
- `data/api/NetworkModule.kt` — alternative configuration

### Dependencies

- OkHttp 4.x
- Retrofit 2.x with Gson converter
- `AuthInterceptor`
- Base URL: `http://10.0.2.2:3000/` (emulator localhost)

### Inputs

- API requests from repositories
- JWT token (via interceptor)

### Outputs

- `Response<T>` wrapper with body/error/code
- HTTP logs at BODY level (in debug)

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| Connect timeout | `SocketTimeoutException` after 30s | Backend down, network blocked |
| Read timeout | `SocketTimeoutException` after 60s | Backend slow |
| DNS failure | `UnknownHostException` | Wrong host, no DNS |
| 5xx server error | `Response.code() >= 500` | Backend crash |
| TLS failure | `SSLHandshakeException` | Certificate issue |

### Observable Signals

| Signal | Location |
|--------|----------|
| Full HTTP request/response | Logcat `OkHttp` tag |
| `SocketTimeoutException` | Exception in Logcat |
| HTTP status codes | Response headers in logs |
| Retry count (WorkManager) | `runAttemptCount` in worker logs |

### Evidence Sources

- Logcat: `HttpLoggingInterceptor` output
- Network profiler in Android Studio
- Backend access logs (server-side)

### Recovery/Mitigation

- Timeouts: caught by repositories, returned as `Error`
- 5xx errors: `TelemetryUploadWorker` auto-retries with exponential backoff
- DNS/TLS: requires environment fix

### Criticality

**HIGH** — All server communication; failure isolates device from backend

---

## Component: Local Database (Room)

### Purpose

SQLite database via Room for offline-first session storage. Single source of truth for sessions, laps, and coaching insights.

### Key Code Areas

- `DrivingCoachDatabase.kt` — Room database definition, version 2
- `SessionDao.kt` — session CRUD, upload status tracking, start line updates
- `LapDao.kt` — lap queries
- `CoachingInsightDao.kt` — insight queries
- `db/entity/*.kt` — entity classes
- `DatabaseModule.kt` — Hilt provider

### Schema Version History

| Version | Changes | Migration |
|---------|---------|-----------|
| 1 | Initial schema | — |
| 2 | Added `startLineLat1`, `startLineLng1`, `startLineLat2`, `startLineLng2` to `sessions` table | `MIGRATION_1_2` |

### SessionEntity Fields (v2)

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key (auto-generate) |
| `userId` | String | User identifier |
| `trackName` | String | Track name |
| `startedAt` | Long | Session start timestamp |
| `endedAt` | Long? | Session end timestamp |
| `rawFilePath` | String | Path to telemetry JSONL file |
| `uploadStatus` | String | PENDING/UPLOADING/DONE/FAILED |
| `processingStatus` | String | PENDING/UPLOADING/DETECTING_LAPS/GENERATING_COACHING/COMPLETE/FAILED |
| `remoteSessionId` | String? | Backend session ID |
| `startLineLat1` | Double? | Start line point A latitude |
| `startLineLng1` | Double? | Start line point A longitude |
| `startLineLat2` | Double? | Start line point B latitude |
| `startLineLng2` | Double? | Start line point B longitude |

### Dependencies

- Room 2.x
- SQLite (Android bundled)

### Inputs

- Insert/update from `SessionRepository`, `TelemetryForegroundService`
- Start line coords from `TrackSetupFragment` via `RecordingViewModel`
- Flow observations from ViewModels

### Outputs

- `Flow<SessionEntity?>`, `Flow<List<LapEntity>>`, etc.
- Upload status: `PENDING`, `UPLOADING`, `DONE`, `FAILED`
- Processing status: `PENDING`, `UPLOADING`, `DETECTING_LAPS`, `GENERATING_COACHING`, `COMPLETE`, `FAILED`

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| DB corruption | `SQLiteDatabaseCorruptException` | Disk error, force kill during write |
| Migration failure | Crash on upgrade | Schema change without migration |
| Disk full | `SQLiteFullException` | No storage space |
| Lock contention | ANR or slow queries | Long-running transaction |

### Observable Signals

| Signal | Location |
|--------|----------|
| SQLite exceptions | Logcat `SQLite` tag |
| Room query logs (debug) | Logcat |
| `sessions`, `laps`, `coaching_insights` tables | DB inspector / `adb pull` |
| `uploadStatus` column | `sessions` table |

### Evidence Sources

- `driving_coach.db` in `databases/`
- Android Studio Database Inspector
- `adb shell run-as com.drivingcoach ls databases/`

### Recovery/Mitigation

- Corruption: app reinstall (data loss) or auto-recover via Room callback
- Migration: define `Migration` objects
- Disk full: user frees storage

### Criticality

**HIGH** — Offline storage; corruption = loss of unsynced sessions

---

## Component: Telemetry Upload Worker

### Purpose

WorkManager job that uploads telemetry JSONL files to backend. Runs with network constraint, exponential backoff, and max 5 retries.

### Key Code Areas

- `TelemetryUploadWorker.kt` — HiltWorker implementation
- `TelemetryApiService.uploadSession()` — multipart upload endpoint
- `SessionDao.updateUploadStatus()` — status tracking

### Dependencies

- `WorkManager`
- `TelemetryApiService`
- `SessionDao`
- Network connectivity

### Inputs

- `session_id` via `Data.Builder`
- Telemetry file at `session.rawFilePath`

### Outputs

- `Result.success()` — marks `uploadStatus = DONE`, sets `remoteSessionId`
- `Result.retry()` — schedules exponential backoff retry
- `Result.failure()` — marks `uploadStatus = FAILED`

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| File not found | Immediate `FAILED` | File deleted or wrong path |
| 4xx client error | Immediate `FAILED` | Bad request, auth failure |
| 5xx server error | Retry up to 5x | Backend issue |
| Network error | Retry up to 5x | Connectivity lost |
| Max retries exceeded | Final `FAILED` | Persistent failures |

### Observable Signals

| Signal | Location |
|--------|----------|
| `TelemetryUploadWorker` tag | Logcat |
| "Starting upload for session" | Logcat INFO |
| "Upload successful" | Logcat INFO |
| "Max retries exceeded" | Logcat ERROR |
| `runAttemptCount` | Logged on each attempt |
| `WorkInfo.state` | WorkManager inspection |

### Evidence Sources

- Logcat filter: `TAG:TelemetryUploadWorker`
- WorkManager status: `WorkManager.getInstance().getWorkInfosByTag("telemetry_upload")`
- `sessions.uploadStatus` column in DB
- Backend upload logs (server-side)

### Recovery/Mitigation

- Automatic exponential backoff (10s base)
- User can trigger manual retry from UI (future feature)
- Stale upload banner shown via `HomeViewModel.hasStaleUploads`

### Criticality

**MEDIUM** — Session data captured locally; upload failure = delayed coaching but no data loss

---

## Component: Session Repository

### Purpose

Mediates between UI/ViewModels and data sources (Room + API). Implements offline-first pattern where Room is single source of truth.

### Key Code Areas

- `SessionRepository.kt` — all session operations
- Injects: `ApiService`, `SessionDao`, `LapDao`, `CoachingInsightDao`

### Dependencies

- `ApiService`
- Room DAOs
- Caller: `HomeViewModel`, `SessionResultViewModel`

### Inputs

- CRUD operations from ViewModels
- Sync triggers from UI

### Outputs

- `SessionResult<T>` (Success/Error)
- `Flow<SessionEntity>`, `Flow<List<LapEntity>>`, etc.

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| API failure | `SessionResult.Error` | Network or backend issue |
| File not found (upload) | `SessionResult.Error("Telemetry file not found")` | File deleted |
| DB error | Exception propagates | Room failure |

### Observable Signals

| Signal | Location |
|--------|----------|
| `SessionResult.Error` | ViewModel state |
| Exception logs | Logcat |
| HTTP response codes | Network logs |

### Evidence Sources

- ViewModel error state
- Logcat
- Database tables

### Recovery/Mitigation

- Errors surfaced to UI for user retry
- Local data preserved on network failure

### Criticality

**MEDIUM** — Coordination layer; failures isolated to specific operations

---

## Component: Telemetry File Storage

### Purpose

Local file storage for raw telemetry JSONL files. Each session writes to `files/telemetry/session_{id}.jsonl`.

### Key Code Areas

- `TelemetryFileWriter.kt` — write logic
- `TelemetryFileReader.kt` — read logic (future)
- Directory: `Context.filesDir/telemetry/`

### Dependencies

- Android filesystem
- Gson for JSON serialization

### Inputs

- `TelemetrySample` objects from recording service

### Outputs

- JSONL file (one JSON object per line)
- Each line: `{timestampMs, latitude, longitude, speedMs, headingDeg, accelX, accelY, accelZ, gyroX, gyroY, gyroZ, gpsAccuracyM}`

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| Disk full | Write exception, samples lost | No storage |
| Concurrent access | Unlikely (Mutex protected) | Bug |
| Write after close | Logged warning, sample dropped | Lifecycle bug |

### Observable Signals

| Signal | Location |
|--------|----------|
| `TelemetryFileWriter` tag | Logcat |
| "Error writing telemetry sample" | Logcat ERROR |
| "Closed telemetry writer" | Logcat DEBUG |
| File size growth | File system |

### Evidence Sources

- `adb shell run-as com.drivingcoach ls -la files/telemetry/`
- `adb pull` to inspect JSONL content
- Line count = sample count

### Recovery/Mitigation

- Disk full: user frees space; lost samples not recoverable
- Mutex ensures thread safety

### Criticality

**CRITICAL** — Primary data capture format; corruption = session data loss

---

## Component: Session State Machine (UI)

### Purpose

Manages session lifecycle from creation through recording to results display. Coordinates navigation between Home → Recording → Results screens.

### Key Code Areas

- `HomeViewModel.kt` — session creation, navigation events
- `RecordingViewModel.kt` — service binding, state observation
- `SessionResultViewModel.kt` — results display
- `RecordingUiState` — UI state data class

### Dependencies

- `TelemetryForegroundService` (via binding)
- `SessionDao`, `LapDao`
- Navigation component

### Inputs

- User actions: start session, stop session, view session, delete session, rename session
- Service state changes via `StateFlow`

### Outputs

- `HomeEvent` (navigation events, session deleted/renamed confirmations)
- `RecordingUiState` (elapsed time, GPS status, lap count)
- UI updates

### Session Management (HomeViewModel)

| Action | Method | Behavior |
|--------|--------|----------|
| Delete | `deleteSession(sessionId)` | Removes session from Room (CASCADE deletes laps + insights), deletes telemetry JSONL file |
| Rename | `renameSession(sessionId, newName)` | Updates `trackName` in Room, validates 1-100 chars |

### UI Interactions (HomeFragment)

| Gesture | Trigger | Result |
|---------|---------|--------|
| Tap session card | `onSessionClick` | Navigate to session results |
| Long-press session card | `onSessionLongClick` | Show context menu (Rename/Delete) |

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| Service binding failure | No state updates | Service not running |
| State desync | UI shows stale data | Flow collection issue |
| Navigation failure | User stuck on screen | NavController error |

### Observable Signals

| Signal | Location |
|--------|----------|
| `RecordingUiState` changes | ViewModel logging |
| `HomeEvent` emissions | Event flow |
| Service connection logs | System logs |

### Evidence Sources

- UI state inspection in debugger
- Logcat for state transitions
- Android navigation logs

### Recovery/Mitigation

- Service disconnect: rebind on resume
- State desync: service emits fresh state on reconnect

### Criticality

**MEDIUM** — UI coordination; failure = confusing UX but data safe

---

## Component: Preferences DataStore

### Purpose

Encrypted key-value storage for auth tokens and user profile data. Replaces SharedPreferences for type safety and coroutine support.

### Key Code Areas

- `di/DataStoreModule.kt` — DataStore provider (isolated so tests can substitute it)
- `AuthRepository.kt` — token read/write
- `AuthInterceptor.kt` — token read

### Dependencies

- Jetpack DataStore
- Proto DataStore (preferences variant)

### Inputs

- Token writes from login/register
- User profile writes

### Outputs

- `Flow<String?>` for token
- `Flow<Boolean>` for `isLoggedIn`

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| Corruption | Read exception, fallback to empty | Disk error |
| Concurrent access issue | Rare; DataStore handles | Bug |

### Observable Signals

| Signal | Location |
|--------|----------|
| DataStore exceptions | Logcat |
| File: `driving_coach_prefs.preferences_pb` | App data directory |

### Evidence Sources

- `adb shell run-as com.drivingcoach ls files/datastore/`
- Flow emission observation

### Recovery/Mitigation

- Corruption: fallback to empty, user re-authenticates

### Criticality

**HIGH** — Auth persistence; corruption = forced re-login

---

## Component: Lap & Coaching Data Sync

### Purpose

Fetches processed lap times and AI coaching insights from backend after upload completes. Syncs to local Room database.

### Key Code Areas

- `SessionRepository.fetchSessionDetail()` — pulls laps + insights
- `ApiService.getSession()` — GET `/sessions/{id}`
- `LapEntity`, `CoachingInsightEntity` — Room entities

### Dependencies

- `ApiService`
- Room DAOs
- Backend processing pipeline completion

### Inputs

- Remote session ID after upload
- Trigger from UI or poll

### Outputs

- `SessionDetailDto` from API
- Persisted `LapEntity`, `CoachingInsightEntity` rows

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| API error | `SessionResult.Error` | Network, backend |
| Processing not complete | Empty laps/insights | Backend still processing |
| Parse error | `JsonSyntaxException` | Schema mismatch |

### Observable Signals

| Signal | Location |
|--------|----------|
| HTTP response | Network logs |
| Empty laps array | API response body |
| `processing_status` field | Session DTO |

### Evidence Sources

- API response inspection
- Room `laps` and `coaching_insights` tables
- Backend processing status (server-side)

### Recovery/Mitigation

- Poll retry when `processingStatus != COMPLETE`
- Schema mismatch: app update required

### Criticality

**MEDIUM** — Coaching data delivery; failure = delayed insights but recording intact

---

## Component: Stale Upload Detection

### Purpose

Identifies sessions stuck in `PENDING` upload status for >5 minutes. Surfaces warning banner on Home screen.

### Key Code Areas

- `HomeViewModel.kt` — stale detection logic
- `SessionDao.getStaleUploadSessions()` — query

### Dependencies

- `SessionDao`
- System clock

### Inputs

- Current timestamp
- `sessions` table

### Outputs

- `hasStaleUploads: Boolean` in `HomeUiState`
- UI warning banner

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| False positive | Banner shown for in-progress upload | Clock skew, long upload |
| Missed detection | No banner for stuck upload | Query not run |

### Observable Signals

| Signal | Location |
|--------|----------|
| `hasStaleUploads = true` | ViewModel state |
| Stale session IDs | Logcat (if added) |

### Evidence Sources

- `sessions` table: `uploadStatus = PENDING AND startedAt < threshold`
- UI inspection

### Recovery/Mitigation

- User dismisses banner (resets flag)
- Manual retry option (future)

### Criticality

**LOW** — UX indicator only; no data impact

---

## Component: Track Setup (Start Line Capture)

### Purpose

GPS-based capture of two points defining the start/finish line. User walks to each edge of the track and captures coordinates. This enables precise line-intersection lap detection instead of centroid-based fallback.

### Key Code Areas

- `ui/tracksetup/TrackSetupFragment.kt` — GPS capture UI, permission handling
- `ui/tracksetup/TrackSetupViewModel.kt` — state management for two-point capture
- `util/GeoUtils.kt` — haversine distance calculation, line intersection math

### Dependencies

- `FusedLocationProviderClient` (Google Play Services Location)
- `ACCESS_FINE_LOCATION` permission
- Navigation Component (Safe Args)

### Inputs

- User taps "Capture" buttons at two GPS locations
- Live GPS coordinates from `FusedLocationProviderClient`

### Outputs

- `TrackSetupState` with `pointA`, `pointB`, `distance`, `isValid`
- Navigation to `RecordingFragment` with 4 start line coordinates

### State Flow

```
Initial → GPS Acquiring → Point A Captured → Point B Captured → Valid (distance ≥ 3m) → Navigate
                                    ↓
                              Clear → Back to Initial
```

### Key Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `MIN_LINE_DISTANCE_M` | 3.0 | Minimum track width for valid line |
| `MAX_GPS_ACCURACY_M` | 10.0 | GPS accuracy threshold for "ready" |

### GeoUtils Functions

| Function | Purpose |
|----------|---------|
| `haversineDistance()` | Calculate distance between two GPS points |
| `lineIntersection()` | Detect if car path crosses start line |
| `toLocal()` | Convert GPS to local X/Y for intersection math (private) |

### Failure Modes

| Mode | Symptom | Cause |
|------|---------|-------|
| GPS not acquiring | Status shows "Acquiring GPS..." indefinitely | Indoor, poor sky view, emulator without location set |
| Points too close | "Minimum 3m required" hint visible | User captured same edge twice |
| Permission denied | Fragment pops back, snackbar shown | User denied location permission |

### Observable Signals

| Signal | Location |
|--------|----------|
| GPS accuracy | `TrackSetupState.gpsAccuracy` |
| Satellite count | `TrackSetupState.satelliteCount` |
| Line distance | `TrackSetupState.distance` |
| Capture success | `TrackSetupState.pointA/pointB` non-null |

### Evidence Sources

- Logcat: `FusedLocationProviderClient` updates
- UI: GPS indicator color (green = ready, red = acquiring)
- ViewModel state via debugging

### Recovery/Mitigation

- GPS not acquiring: Move outdoors, wait for satellite lock, set emulator location manually
- Permission denied: Re-request via app settings
- Points too close: Clear and recapture at correct positions

### Criticality

**MEDIUM** — Without valid start line, app falls back to 30-second centroid-based lap detection (less accurate but functional)

---

## Component: GeoUtils (Geometry Utilities)

### Purpose

Provides geographic calculation utilities for lap detection and track setup.

### Key Functions

```kotlin
// Calculate distance between two GPS points (meters)
fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double

// Check if GPS path segment crosses start/finish line
fun lineIntersection(
    lineLat1: Double, lineLng1: Double, lineLat2: Double, lineLng2: Double,
    prevLat: Double, prevLng: Double, currLat: Double, currLng: Double
): Boolean

// Convert GPS to local Cartesian coordinates (private helper)
private fun toLocal(lat: Double, lng: Double, refLat: Double, refLng: Double): Pair<Double, Double>
```

### Usage

- **TrackSetupViewModel**: `haversineDistance()` for line width validation
- **Backend lapDetector**: `lineIntersection()` for lap detection

### Criticality

**HIGH** — Core math for lap detection accuracy

---

## Component: Offline Coaching Engine

### Purpose

Generates coaching insights locally (offline) from lap data stored in Room. Provides immediate feedback after recording stops without requiring backend AI processing. Produces 3-4 insights: Best Lap Highlight, Top Speed, Consistency Score, and Sector Focus (upsell).

### Key Code Areas

- `OfflineCoachingEngine.kt` — stateless insight generation rules engine
- `RecordingViewModel.kt` — calls `generateOfflineCoaching()` after lap detection
- `CoachingInsightDao.kt` / `CoachingInsightEntity.kt` — persists generated insights

### Dependencies

- `LapEntity` from Room — provides lap timing and sector data
- `CoachingInsightDao` — persists generated insights
- Telemetry JSONL file — for top speed extraction

### Inputs

- `List<LapEntity>` — laps from the session (minimum 2 required)
- Each lap contains: `durationMs`, `sector1Ms`, `sector2Ms`, `sector3Ms`, `startTs`, `endTs`
- `rawFilePath` — path to telemetry JSONL for top speed extraction

### Outputs

- `List<OfflineInsight>` — 3-4 insights with `headline` and `detail` strings
- Persisted `CoachingInsightEntity` rows in Room

### Insight Types (v2.1+)

| Insight | Condition | Example |
|---------|-----------|---------|
| Best Lap | Always (if ≥2 laps) | "Lap 3 Was Your Fastest — 2.3s ahead of average" |
| Top Speed | If telemetry file exists | "🚀 Top Speed: 247 km/h — Hit on Lap 3" |
| Consistency | Always (if ≥2 laps) | "Laps within 1.2s of each other" |
| Sector Focus | If sectors unavailable | "Sector Analysis Coming Soon" (upsell) |

### Failure Modes

| Mode | Symptom | Cause | Mitigation (v2.1) |
|------|---------|-------|-------------------|
| Insufficient laps | Empty insight list | `laps.size < 2` | Show message |
| Zero sector data | ~~"0ms quicker"~~ | Sectors not calculated | ✅ FIXED: `areSectorsAvailable()` guard |
| No telemetry file | No top speed insight | File missing/deleted | Graceful skip |
| GPS noise spike | False top speed | Bad GPS data | ✅ FIXED: 350 km/h cap |

### Observable Signals

| Signal | Location |
|--------|----------|
| `RecordingViewModel` tag | Logcat |
| "Generated X offline coaching insights" | Logcat DEBUG |
| "No offline coaching insights generated" | Logcat DEBUG |
| `isLocalOnly = true` | `CoachingInsightEntity` |

### Evidence Sources

- Room query: `SELECT * FROM coaching_insights WHERE sessionId = ? AND isLocalOnly = 1`
- Logcat filter: `TAG:RecordingViewModel`
- `session.jsonl` for lap count and speed verification

### Recovery/Mitigation

- Insufficient laps: User must complete at least 2 laps
- Zero sector data: ✅ `areSectorsAvailable()` guard prevents false insights

### Known Limitations

| Limitation | Impact | Status |
|------------|--------|--------|
| No sector timing (local) | Sector insights show "0ms" | ✅ FIXED v2.1 |
| GPS noise (>350 km/h) | False top speed | ✅ FIXED v2.1 |
| Wording "vary by" | Misleading | ✅ FIXED v2.1 → "within" |
| No driving technique insights | Limited feedback | Backend-only feature |

### Criticality

**MEDIUM** — Enhances UX; failure = no coaching but session data intact

---

## Component: App Startup / Branded Loading Screen

### Purpose

Owns cold-start initialisation. Resolves whether the user goes to Onboarding, Driver Name or
Home while presenting the TRILLIAN brand moment with a progress bar tied to **real**
startup work. Replaces the former main-thread `runBlocking` DataStore read in
`MainActivity`.

### Key Code Areas

| Element | Path |
|---------|------|
| `SplashViewModel` | `app/src/main/java/com/drivingcoach/ui/splash/SplashViewModel.kt` |
| `SplashFragment` | `app/src/main/java/com/drivingcoach/ui/splash/SplashFragment.kt` |
| `SplashDestination` | `app/src/main/java/com/drivingcoach/ui/splash/SplashDestination.kt` |
| `SplashTimings` | `app/src/main/java/com/drivingcoach/ui/splash/SplashTimings.kt` |
| `SplashVisibilitySignal` | `app/src/main/java/com/drivingcoach/ui/splash/SplashVisibilitySignal.kt` |
| `@IoDispatcher` | `app/src/main/java/com/drivingcoach/di/IoDispatcher.kt` |
| Layout | `app/src/main/res/layout/fragment_splash.xml` |
| Nav entry | `app/src/main/res/navigation/nav_graph.xml` (`startDestination`) |
| System splash theme | `res/values/themes.xml` → `Theme.DrivingCoach.Splash` |

### Dependencies

| Dependency | Type | Purpose |
|------------|------|---------|
| `DataStore<Preferences>` | Injected | Read `onboarding_complete`, `splash_launch_count` |
| `DriverProfileStore` | Injected | Resolve `driver_profile_complete` from the same preferences snapshot |
| `SessionDao` | Injected | Room warm-up + pending upload count |
| `SplashTimings` | Injected | `minDisplayMs` / `introDisplayMs` / `timeoutMs` / `warmUpTimeoutMs` / `visibilityTimeoutMs` (0 in tests) |
| `SplashVisibilitySignal` | Injected | Reports when the platform splash exits, which anchors the display budget |
| `@IoDispatcher CoroutineDispatcher` | Injected | Keeps all I/O off the main thread |
| `androidx.core:core-splashscreen:1.0.1` | Library | Android 12+ system-splash handoff |

### Inputs

- DataStore preferences: `onboarding_complete` (Boolean), `user_name` (String), `driver_profile_complete` (Boolean), `splash_launch_count` (Int)
- Room: `sessionDao.getPendingUploadSessions()`
- User tap on `splashRoot` (skip request)

### Outputs

| Output | Consumer |
|--------|----------|
| `UiState.progress` (0–100) | `LinearProgressIndicator` |
| `UiState.stepLabel` (`@StringRes`) | `progressLabel` TextView |
| `UiState.destination` | `SplashFragment` navigation |
| `UiState.usedFallback` | Diagnostics / tests |
| `UiState.hintLabel` (`@StringRes`) | `splashHint` TextView |
| `pendingUploadCount` | Diagnostics, Home upload banner seed |

### Progress Model

| Progress | Step | Real work |
|----------|------|-----------|
| 0 % | `splash_step_preferences` | Begin DataStore read |
| 25 % | `splash_step_database` | Preferences read; open Room |
| 55 % | `splash_step_uploads` | Pending-upload query returned |
| 80 % | `splash_step_ready` | Evaluating destination |
| 100 % | `splash_step_ready` | Ready; minimum display satisfied |

### Failure Modes

| Mode | Cause | Symptom | Handling |
|------|-------|---------|----------|
| Essential read timeout | Slow disk | Bar stalls at 25%, then jumps | `withTimeoutOrNull(8 s)` → ONBOARDING, `usedFallback = true` |
| Warm-up timeout | Slow Room open | None visible | Bounded at 2 s, non-fatal; `pendingUploadCount = 0` |
| DataStore corruption | Force-kill during write | Unexpected onboarding | Empty prefs → ONBOARDING |
| Double navigation | Rapid state re-emit | `IllegalArgumentException` | Guarded by `currentDestination` check |
| Double splash | `installSplashScreen()` after `super.onCreate()` | Icon flash then brand screen | Must be called first |
| Test slowdown | `minDisplayMs` not zeroed | Every instrumented test pays 1.2 s | Inject `SplashTimings(minDisplayMs = 0)` |
| Test slowdown (silent) | `introDisplayMs` not pinned | Every instrumented test pays 4 s on a fresh install — a *slowdown*, not a failure, so it is easy to miss | Every L2 module overriding `SplashModule` must set `introDisplayMs` explicitly |
| Launch counter never settles | Counter written on every launch | Unbounded preference writes for the life of the install | Write stops once `launchCount >= INTRO_LAUNCH_COUNT` |
| Intro hold applied to a returning user | Launch-count read failed | 4 s hold on every launch | `displayBudgetMs` initialised to `minDisplayMs`, so a failed read degrades to the *fast* path |

### Observable Signals

| Signal | Location | Meaning |
|--------|----------|---------|
| Progress plateau | UI | Identifies slow init stage |
| `usedFallback == true` | UI state / test | Essential init exceeded its 8 s budget |
| `D/SplashViewModel` timings | Logcat | Per-step startup cost (measured: 1.9–3.6 s DataStore, ~1 s Room on emulator) |
| `D/SplashViewModel: splash visible Nms after start` | Logcat | The handoff was reported. Cross-check against the platform's `I/ActivityTaskManager: Displayed` line — they agreed to ~17 ms on the API 30 emulator |
| `W/SplashViewModel: handoff never reported` | Logcat | The exit listener did not fire; the budget silently fell back to being measured from startup, so the manifesto is short again |
| Absence of `runBlocking` | `MainActivity` source | ANR mitigation intact |
| `MainThreadResponsivenessProbe.worstLatencyMs` | L2 `SplashMainThreadTest` | >2000 ms means startup work returned to the main thread |

### Recovery/Mitigation

- Separate ceilings for essential vs optional work; the optional warm-up can never change the destination.
- Fallback is ONBOARDING (safe, idempotent), not LOGIN (would skip permission granting).
- Tap-to-skip shortens only the cosmetic hold; real init must still complete. The tap is never
  *required*: the screen always auto-advances, so a user who does not notice the hint is never stuck.
- The introduction window (first `INTRO_LAUNCH_COUNT` = 3 launches held for `introDisplayMs`
  = 4000 ms) reuses the preferences read the splash already performs, so it costs no extra
  startup I/O. Both constants are single named values, deliberately easy to tune.
- All nav actions `popUpTo` the splash inclusively, so Back exits the app.

### Test Hooks

`SplashTimings` and the preferences `DataStore` are provided by dedicated Hilt modules
(`SplashModule`, `DataStoreModule`) precisely so instrumented tests can replace one without
disturbing the rest of the graph:

| Goal | Uninstall | Substitute |
|------|-----------|------------|
| Pin the loading screen on screen for assertions | `SplashModule` | `SplashTimings(minDisplayMs = 60_000)` |
| Force the timeout fallback | `SplashModule` + `DataStoreModule` | short `timeoutMs` + `StallingPreferencesDataStore` |
| Land directly on Home | `SplashModule` + `DataStoreModule` | `SeededPreferencesDataStore(onboarding + token)` |
| Exercise the first-run introduction hold | `SplashModule` + `DataStoreModule` | `SeededPreferencesDataStore()` (no launch count) + long `introDisplayMs` |
| Prove a returning user is *not* held | `SplashModule` + `DataStoreModule` | `SeededPreferencesDataStore(splash_launch_count = 3)` + long `introDisplayMs` |

> **Do not** try to prove UI-04 with an Espresso interaction. Espresso waits for the main
> looper to become idle rather than failing, so a `runBlocking(60 s)` on the main thread made
> an Espresso-based test merely slow — it still passed. Latency sampling from a background
> thread (`MainThreadResponsivenessProbe`) is what actually detects the regression; this was
> confirmed empirically by reintroducing the block and observing the test go red.

### Criticality

**HIGH** — every cold start passes through this component; total failure = app unusable.

---

## Component: Home Brand Hero (Collapsing Toolbar)

### Purpose

Presents the TRILLIAN identity on the Home screen while keeping recent sessions and the
START SESSION call to action reachable. The hero is expanded on arrival and collapses to a
compact branded bar as the user scrolls.

### Key Code Areas

| Element | Path |
|---------|------|
| Layout | `app/src/main/res/layout/fragment_home.xml` |
| Collapse logic | `HomeFragment.setupHeroCollapse()` |

### Structure

```
CoordinatorLayout
└── AppBarLayout (id: appBarLayout)
    └── CollapsingToolbarLayout (titleEnabled=false)
        ├── heroContent  [parallax]  emblem + wordmark + kicker + tagline
        └── Toolbar      [pin]       collapsedBrand (alpha 0→1) + profileButton
└── NestedScrollView  → upload banner, heroCard, recent sessions, empty state
└── startSessionButton (MaterialButton pill CTA)
```

### Behaviour

| Scroll offset | Expanded hero | Collapsed brand |
|---------------|---------------|-----------------|
| 0 % | alpha 1.0 | alpha 0.0 |
| 0–60 % | fades out linearly | 0.0 |
| 60–100 % | 0.0 | fades in linearly |

`COLLAPSE_FADE_START = 0.6f` in `HomeFragment`'s companion object.

### Failure Modes

| Mode | Cause | Symptom |
|------|-------|---------|
| Hero never collapses | Content shorter than scroll range | Static hero (acceptable) |
| Both brands visible | Fade thresholds overlapping | Visual duplication |
| Stale id reference | `startSessionFab` → `startSessionButton` rename | Instrumented test compile failure |
| Emblem artwork deformed | Brand asset geometry defect (see *Brand Asset Pipeline*) | Emblem renders squashed at all three sizes; `isDisplayed()` tests still pass |
| Emblem clipped by ring | Content bbox exceeds the `bg_hero_ring` radius | Artwork edges cut off inside the navy disc |

### Criticality

**LOW** — cosmetic; failure degrades presentation but not function.

---

## Component: Brand Asset Pipeline (Helmet Emblem)

### Purpose

Supplies the single `@drawable/ic_helmet_emblem` resource consumed by every
branded surface. Introduced by the Incident 11 fix, which replaced a
hand-authored vector with a raster emblem derived from owner-supplied artwork.

### Key Code Areas

| Element | Path |
|---------|------|
| Shipped asset | `res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_helmet_emblem.webp` |
| Source artwork | `docs/brand/helmet_source.png` |
| Build + check tool | `05_tests/infra/scripts/brand-asset.py` |
| Geometry gate (L1) | `app/src/test/java/com/drivingcoach/brand/BrandAssetGeometryTest.kt` |
| PNG decoder for the gate | `app/src/test/java/com/drivingcoach/brand/ArgbBitmap.kt` |
| Geometry master | `app/src/test/resources/brand/ic_helmet_emblem_master.png` |
| Negative fixture | `app/src/test/resources/brand/legacy_deformed_emblem.png` |

### Render Sites

| # | Surface | Layout | Size |
|---|---------|--------|------|
| 1 | Splash fragment | `fragment_splash.xml` | 132dp inside `bg_hero_ring` |
| 2 | System splash window | `res/drawable/ic_splash_emblem.xml` | 20 % inset wrapper |
| 3 | Home hero | `fragment_home.xml` | 88dp |
| 4 | Home collapsed brand bar | `fragment_home.xml` | `@dimen/hero_emblem_collapsed` (36dp) |

All four resolve the same resource name, so a single asset change corrects — or
breaks — every surface at once.

### Density Buckets

| Bucket | Pixels | Derivation |
|--------|--------|------------|
| mdpi | 132 | 1× of the 132dp render site |
| hdpi | 198 | 1.5× |
| xhdpi | 264 | 2× |
| xxhdpi | 396 | 3× |
| xxxhdpi | 528 | 4× — matches the 533px source crop, so nothing is upscaled |

### Invariants

Enforced by `BrandAssetGeometryTest`, and mirrored by `brand-asset.py check`:

| Invariant | Threshold | Why |
|-----------|-----------|-----|
| Content aspect | 1.00 ± 0.05 | The Incident 11 emblem was 0.866 — squashed |
| Canvas square | exact | `fitCenter` would otherwise letterbox |
| Content centred | ≤ 3 % off each axis | The old shell sat 6/120 units high |
| Transparent border | zero edge alpha | Prevents clipping inside `bg_hero_ring` |
| Legible at 36dp | > 15 % opaque | The smallest render site must still read |
| No `drawable/ic_helmet_emblem.xml` | must not exist | Same-name vector + bitmap is a resource-merger conflict |

### Failure Modes

| Mode | Cause | Symptom | Detection |
|------|-------|---------|-----------|
| Deformed artwork | Non-square content bbox | Emblem squashed on every surface | `emblemMasterSatisfiesBrandGeometry` |
| Gate becomes a no-op | Assertions weakened | Defects pass silently | `gateRejectsTheLegacyDeformedEmblem` |
| Missing density bucket | Partial asset drop | Blurry or absent emblem on some devices | `emblemDensityBucketsAreCompleteAndCorrectlySized` |
| Vector resurrected | `.xml` re-added beside the WebP | Non-deterministic resource merge | `noConflictingVectorEmblemRemains` |
| Master drifts from shipped buckets | Buckets regenerated without the master | Gate measures artwork that is not shipped | Bucket dimension check + `brand-asset.py build` reproducibility |
| Dark edge fringing | Non-premultiplied RGBA downsample | Halo around the emblem on dark backgrounds | Visual review; `brand-asset.py` premultiplies |

### Signals

| Signal | Where | Meaning |
|--------|-------|---------|
| `content aspect 1.00 +/- 0.05` FAIL | `brand-asset.py check` | Artwork is deformed |
| `gate accepted the known-deformed legacy emblem` | L1 failure message | The gate has stopped being falsifiable |
| Bit-identical rebuild | `brand-asset.py build` | Shipped assets are reproducible from source |

### Criticality

**LOW** — cosmetic. Escalated in review weight because the defect is visible on
the first screen shown at cold start and is invisible to presence-only tests.

---

## Component: About Screen (Brand & Build Identity)

### Purpose

Permanent, user-reachable home for the engineering manifesto and the build identity. Exists
because the loading screen — the only other place the manifesto appears — is transient by
design, and because a user reporting a bug previously had no way to state which build they
were running.

### Key Code Areas

| Element | Path |
|---------|------|
| `AboutFragment` | `app/src/main/java/com/drivingcoach/ui/about/AboutFragment.kt` |
| Layout | `app/src/main/res/layout/fragment_about.xml` |
| Entry point | `app/src/main/res/layout/fragment_profile.xml` (`aboutButton`) |
| Nav action | `res/navigation/nav_graph.xml` → `action_profile_to_about` |
| Version source | `BuildConfig.VERSION_NAME` / `VERSION_CODE` |

### Dependencies

| Dependency | Type | Purpose |
|------------|------|---------|
| `BuildConfig` | Generated | Version name and code |
| `NavController` | Fragment-scoped | Entry from Profile, `popBackStack()` on Up |

No Hilt injection, no ViewModel, no I/O — the screen is entirely static apart from the
version string, so there is nothing to fail asynchronously.

### Design Constraints

| Constraint | Rationale |
|------------|-----------|
| Reuses `manifesto_title`, `manifesto_body`, `hero_*` string resources | The loading screen and About screen render the *same* text; sharing the resources makes drift structurally impossible rather than merely discouraged |
| Root is a `NestedScrollView` | Unlike `fragment_profile.xml`, the content is long enough to overflow at large font scales |
| Version is read, never typed | A hardcoded version is a version that will eventually be wrong; `BuildConfig` derives from the same `appVersionName` that names the APK |

### Failure Modes

| Mode | Cause | Symptom | Handling |
|------|-------|---------|----------|
| Screen unreachable | Nav action removed or misdirected | Dead button | Runtime failure only — caught by `AboutScreenTest.aboutIsReachableFromTheProfileScreen`, which asserts the resulting destination id |
| `BuildConfig` missing | `buildFeatures { buildConfig = true }` removed | **Compile** failure | Fails fast; AGP 8 omits `BuildConfig` unless explicitly enabled |
| Version disagrees with the APK | Hand-edited `versionCode` | Bug reports cite a build that does not exist | `reportedVersionMatchesTheInstalledPackage` compares `BuildConfig` against `PackageManager` |
| Content clipped | Large font scale | Version row unreachable | `NestedScrollView` |

### Criticality

**LOW** — informational. Raised in review weight because it is the screen a user is asked to
quote when reporting any other defect.

---

## Component: Session Share & Telemetry Export

### Purpose

Turns a finished session into something that can leave the phone: a social image card
(tap) and a developer diagnostic bundle (long-press). Both paths are strictly read-only
with respect to recorded data — the export exists to *diagnose* lost sessions, so an
export that could itself corrupt one would defeat its own purpose.

### Key Code Areas

| Area | Location |
|------|----------|
| Bundle assembly, metadata, cache pruning | `app/src/main/java/com/drivingcoach/util/SessionShareBuilder.kt` |
| Gesture wiring, chooser, error surfacing | `app/src/main/java/com/drivingcoach/ui/session/SessionResultFragment.kt` |
| Image card rendering | `app/src/main/java/com/drivingcoach/util/ShareCardGenerator.kt` |
| FileProvider paths | `app/src/main/res/xml/file_paths.xml` (`<cache-path name="shared_images" path="shared/">`) |

### Structure

| Element | Detail |
|---------|--------|
| `buildTelemetryBundle(session, laps)` | Returns `ShareResult.Success(file)` or `ShareResult.Failure(reason)` — failure is a value, never an exception |
| `Reason` | `TELEMETRY_FILE_MISSING`, `EXPORT_FAILED` |
| ZIP entries | `telemetry.jsonl` (byte-for-byte copy), `session.json` (Gson metadata) |
| `pruneStaleArtifacts()` | Deletes `telemetry_*` / `session_share_*` older than 24 h, immediate children only |
| Output location | `cacheDir/shared/` — already covered by the existing FileProvider cache path |

### Design Constraints

| Constraint | Rationale |
|------------|-----------|
| Primary constructor takes a `File` dir + primitives + `now: () -> Long`; a secondary `@Inject` constructor supplies the real values | Makes the entire ZIP pipeline JVM-testable. Dagger cannot inject a lambda, hence the two-constructor split (same pattern as `LocationWarmUp`) |
| Gson, not `org.json` | `unitTests.isReturnDefaultValues = true` stubs every `android.jar` method, so `org.json` would silently emit empty objects in L1 tests and the tests would pass while the metadata was blank |
| Receives entities and a directory — never a DAO, never `TelemetryFileWriter` | The read-only invariant (SRS SH-11) holds **by construction**: there is no write path to session data to misuse |
| Writes only into `cacheDir/shared/`, never `filesDir` | Avoids exposing the telemetry directory through the FileProvider |
| Prune never recurses | A recursive delete rooted near user data is a data-loss incident waiting for a path bug |

### Failure Modes

| Mode | Cause | Symptom | Handling |
|------|-------|---------|----------|
| Telemetry file absent | Session predates the feature, or file removed | Snackbar "Telemetry file not found" | `Failure(TELEMETRY_FILE_MISSING)`; no exception, nothing sent |
| Path is a directory | Corrupted `rawFilePath` | Same as above | Explicitly treated as missing rather than throwing on read |
| ZIP write fails | Cache full / IO error | Snackbar, logged at `Log.e` | `Failure(EXPORT_FAILED)` |
| Chooser cannot start | No handler app, FileProvider misconfigured | Snackbar | try/catch around `startActivity` |
| **Long-press listener silently detached** | Refactor of `setupToolbar`, or `findViewById` returning null before layout | Export becomes unreachable, with no compile error and no crash | The *only* defence is `SessionShareTest` — three of its tests fail if `attachTelemetryExportGesture()` stops being called (verified by deliberately detaching it) |
| Cache growth | Share artifacts were never cleaned up (pre-existing defect) | Slow disk bloat | 24 h prune on every share |

### Signals

- `Log.e(TAG, "Failed to share session card"| "Failed to start share chooser")`
- User-visible Snackbars are the primary signal; the previous code caught and discarded
  every share exception, which made a broken button and a dead button indistinguishable.

### Criticality

**LOW** for the image card. **MEDIUM** for the telemetry export — it is not on any user
happy path, but it is the mechanism by which every *other* defect gets diagnosed, so its
silent failure is disproportionately expensive.

---

## Summary: Criticality Matrix

| Component | Criticality | Impact of Total Failure |
|-----------|-------------|-------------------------|
| Telemetry Recording Service | CRITICAL | No session data captured |
| Telemetry File Storage | CRITICAL | Session data loss |
| Local Database (Room) | HIGH | Offline data loss |
| Authentication Layer | HIGH | Backend access blocked |
| Networking Layer | HIGH | All sync blocked |
| Preferences DataStore | HIGH | Auth lost, re-login required |
| Telemetry Upload Worker | MEDIUM | Delayed sync, no data loss |
| Session Repository | MEDIUM | Operation-specific failures |
| Lap & Coaching Sync | MEDIUM | Delayed insights |
| Offline Coaching Engine | MEDIUM | No coaching insights |
| Session State Machine | MEDIUM | UX confusion |
| App Startup / Branded Loading Screen | HIGH | App unusable (no cold start) |
| Stale Upload Detection | LOW | Missing UX warning |
| Home Brand Hero | LOW | Degraded presentation only |
| Brand Asset Pipeline | LOW | Deformed or missing emblem on all branded surfaces |
| About Screen | LOW | Version and manifesto unreachable in-app; bug reports lose build identity |
