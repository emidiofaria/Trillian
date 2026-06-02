# components.md

Operational component inventory for RCA localization in BMW Driving Coach Android app.

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

- `LocationManager` (GPS_PROVIDER)
- `SensorManager` (accelerometer, gyroscope)
- `SessionDao` — updates session end time
- `WorkManager` — enqueues upload on stop
- Android foreground service runtime

### Inputs

- Session ID (from `HomeViewModel.startNewSession()`)
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
| Notification channel `bmw_recording` | System notification bar |
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
- DataStore file: `bmw_driving_coach_prefs.preferences_pb`
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

- `BMWDatabase.kt` — Room database definition, version 1
- `SessionDao.kt` — session CRUD, upload status tracking
- `LapDao.kt` — lap queries
- `CoachingInsightDao.kt` — insight queries
- `db/entity/*.kt` — entity classes
- `DatabaseModule.kt` — Hilt provider

### Dependencies

- Room 2.x
- SQLite (Android bundled)

### Inputs

- Insert/update from `SessionRepository`, `TelemetryForegroundService`
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

- `bmw_driving_coach.db` in `databases/`
- Android Studio Database Inspector
- `adb shell run-as com.bmw.drivingcoach ls databases/`

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

- `adb shell run-as com.bmw.drivingcoach ls -la files/telemetry/`
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
| File: `bmw_driving_coach_prefs.preferences_pb` | App data directory |

### Evidence Sources

- `adb shell run-as com.bmw.drivingcoach ls files/datastore/`
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
