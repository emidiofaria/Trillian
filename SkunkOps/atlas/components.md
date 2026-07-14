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

## Component: Authentication Layer

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

- User actions: start session, stop session, view session
- Service state changes via `StateFlow`

### Outputs

- `HomeEvent` (navigation events)
- `RecordingUiState` (elapsed time, GPS status, lap count)
- UI updates

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

- `di/AppModule.kt` — DataStore provider
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
| Session State Machine | MEDIUM | UX confusion |
| Stale Upload Detection | LOW | Missing UX warning |
