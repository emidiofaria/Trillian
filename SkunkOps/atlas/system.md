# system.md

> Driving Coach Android Application — Operational System Map  
> Generated: 2026-05-26 | Confidence: HIGH (derived from source code analysis)

---

## System Overview

| Attribute | Value |
|-----------|-------|
| **Application Name** | Driving Coach |
| **Package ID** | `com.drivingcoach` |
| **Type** | Native Android Application (POC) |
| **Architecture** | Single-module MVVM with Clean Architecture layers |
| **Target SDK** | 35 (Android 15) |
| **Min SDK** | 26 (Android 8.0) |

### Purpose

Driving Coach is a **track-day telemetry application** that:
1. Captures GPS location and IMU sensor data during driving sessions
2. Uploads telemetry to a backend for lap detection and AI-powered coaching insights
3. Displays session results with lap times, sector analysis, and personalized driving advice

### Module Structure

Single `:app` module with internal package structure:
```
com.drivingcoach/
├── DrivingCoachApp.kt    # Hilt Application entry point
├── data/
│   ├── api/                  # Network layer (Retrofit, DTOs)
│   ├── db/                   # Room database (entities, DAOs)
│   ├── repository/           # Data repositories
│   ├── telemetry/            # Telemetry capture and file I/O
│   └── worker/               # WorkManager background jobs
├── di/                       # Hilt modules (AppModule, DataStoreModule,
│                             #   DispatcherModule, SplashModule, NetworkModule, …)
│                             # Split by concern so instrumented tests can
│                             # @UninstallModules one binding at a time.
├── domain/                   # Domain layer (empty - use cases not yet extracted)
├── service/                  # Foreground service
├── ui/                       # Presentation layer (Fragments, ViewModels)
│   ├── auth/
│   ├── home/
│   ├── onboarding/
│   ├── profile/
│   ├── recording/
│   ├── session/
│   └── splash/               # Branded loading screen (startup resolution)
└── util/                     # Formatting utilities
```

### Major Runtime Responsibilities

| Responsibility | Component |
|----------------|-----------|
| GPS/IMU capture at 10 Hz | `TelemetryForegroundService` |
| Telemetry file persistence | `TelemetryFileWriter` (JSONL with header) |
| Local (offline) lap detection | `LocalLapDetector` |
| Session state management | Room database + `SessionRepository` |
| Background telemetry upload | `TelemetryUploadWorker` (WorkManager) |
| Authentication flow | `AuthRepository` + `AuthInterceptor` |
| UI state management | Hilt ViewModels with StateFlow |

---

## Runtime Components

### UI Layer

| Component | Technology | Purpose | Operational Importance |
|-----------|------------|---------|------------------------|
| `MainActivity` | Single Activity + Navigation | Hosts NavHostFragment, handles auth events | Entry point, session-expired redirect |
| Fragments | Jetpack Navigation (SafeArgs) | Screen-level UI | `RecordingFragment` is GPS-critical |
| ViewBinding | Android View Binding | Type-safe view access | Low ops risk |

**Key Classes:**
- `HomeFragment` / `HomeViewModel` — Session list, new session creation
- `RecordingFragment` / `RecordingViewModel` — Active recording UI, service binding
- `SessionResultFragment` / `SessionResultViewModel` — Post-session analysis
- `LoginFragment` / `RegisterFragment` — Authentication (minimal implementation)

### ViewModel Layer

| ViewModel | Injects | State Pattern |
|-----------|---------|---------------|
| `HomeViewModel` | SessionDao, LapDao | `StateFlow<HomeUiState>` + `SharedFlow<HomeEvent>` |
| `RecordingViewModel` | Application (AndroidViewModel) | `StateFlow<RecordingUiState>`, binds to service |
| `SessionResultViewModel` | SessionDao, LapDao, CoachingInsightDao, WorkManager | Polls for processing updates |
| `ProfileViewModel` | SessionDao, LapDao, Database, DataStore | Aggregates user statistics |

**Operational Note:** `SessionResultViewModel` implements a **5-second polling loop** while `processingStatus` is not `COMPLETE` or `FAILED`.

### Repositories

| Repository | Purpose | Data Sources |
|------------|---------|--------------|
| `AuthRepository` | Login, register, token management | ApiService + DataStore |
| `SessionRepository` | Session CRUD, upload orchestration | SessionDao, LapDao, CoachingInsightDao, ApiService |

### Networking

| Component | Technology | Configuration |
|-----------|------------|---------------|
| HTTP Client | OkHttp 4.12.0 | 30s connect, 60s read/write timeout |
| REST Client | Retrofit 2.11.0 | Gson converter |
| Logging | `HttpLoggingInterceptor` | Level: BODY (always enabled) |
| Auth | `AuthInterceptor` | Reads JWT from DataStore, handles 401 |

**Base URL:** `http://10.0.2.2:3000/` (Android emulator localhost)

**API Endpoints:**
- `POST /auth/register` — User registration
- `POST /auth/login` — User login
- `GET /auth/me` — User profile
- `POST /telemetry/upload` — Multipart telemetry upload
- `GET /sessions` — User session list
- `GET /sessions/{id}` — Session detail with laps and insights

**Auth Flow:**
1. JWT stored in `DataStore` under key `jwt_token`
2. `AuthInterceptor` attaches `Authorization: Bearer {token}` to all non-auth requests
3. On 401 response: token cleared, `AuthEventBus.SessionExpired` emitted
4. `MainActivity` observes event, redirects to login

### Local Database

| Component | Technology | Version |
|-----------|------------|---------|
| Database | Room 2.6.1 | Schema version 2 |
| Database Name | `driving_coach.db` | — |
| Migration Strategy | `MIGRATION_1_2` + `fallbackToDestructiveMigration()` | Explicit migrations with destructive fallback |

**Tables:**

| Entity | Table | Purpose | Foreign Keys |
|--------|-------|---------|--------------|
| `SessionEntity` | `sessions` | Driving sessions | — |
| `LapEntity` | `laps` | Individual lap data | `sessionId` → sessions (CASCADE) |
| `CoachingInsightEntity` | `coaching_insights` | AI-generated coaching tips | `sessionId` → sessions (CASCADE) |

**Key Columns:**
- `sessions.uploadStatus`: `PENDING` | `UPLOADING` | `DONE` | `FAILED`
- `sessions.processingStatus`: `PENDING` | `UPLOADING` | `DETECTING_LAPS` | `GENERATING_COACHING` | `COMPLETE` | `FAILED`
- `sessions.rawFilePath`: Absolute path to JSONL telemetry file
- `laps.isBestLap`: Boolean for highlighting

### Background Jobs

| Worker | Type | Trigger | Constraints |
|--------|------|---------|-------------|
| `TelemetryUploadWorker` | `OneTimeWorkRequest` | Session recording ends | `NetworkType.CONNECTED` |

**Retry Configuration:**
- Backoff: EXPONENTIAL, starting at 10 seconds
- Max retries: 5
- 4xx errors: No retry (fail permanently)
- 5xx errors: Retry
- Network exceptions: Retry

### Foreground Service

| Component | Type | Service Type |
|-----------|------|--------------|
| `TelemetryForegroundService` | Foreground Service | `location` |

**TelemetryForegroundService GPS Configuration (Recording):**
- Provider: `LocationManager.GPS_PROVIDER`
- Interval: 100ms (10 Hz target for high-fidelity telemetry)
- Min distance: 0 metres
- Lock timeout: 5 seconds (`GPS_LOCK_TIMEOUT_MS`)
- Signal lost threshold: 10 seconds (`GPS_SIGNAL_LOST_TIMEOUT_MS`)

**TrackSetupFragment GPS Configuration (Start Line Capture):**
- Provider: `FusedLocationProviderClient` (Google Play Services)
- Priority: `PRIORITY_HIGH_ACCURACY`
- Interval: 1000ms (1 Hz for UI updates)
- Min update interval: 500ms
- Note: FusedLocation used for emulator compatibility during track setup

**IMU Configuration:**
- Sensors: Accelerometer, Gyroscope
- Sample rate: `SENSOR_DELAY_FASTEST` (~200 Hz device-dependent)
- IMU values buffered and attached to GPS samples

**Service Lifecycle:**
1. Started via `ACTION_START_RECORDING` with `sessionId` extra
2. Creates `TelemetryFileWriter` for session
3. Binds to UI via `TelemetryBinder` (exposes `StateFlow<RecordingState>`)
4. On `ACTION_STOP_RECORDING`: flushes writer, updates Room, enqueues WorkManager

### Push Notifications

**Status:** Not implemented (no Firebase Cloud Messaging or notification handling code found)

### Analytics

**Status:** Not implemented (no analytics SDK integration found)

### Crash Reporting

**Status:** Not implemented (no Firebase Crashlytics, Sentry, or similar found)

### Feature Flags

**Status:** Not implemented (no feature flag SDK or local config found)

### Auth/Session Management

| Component | Storage | Keys |
|-----------|---------|------|
| JWT Token | DataStore Preferences | `jwt_token` |
| User ID | DataStore Preferences | `user_id` |
| User Name | DataStore Preferences | `user_name` |
| User Email | DataStore Preferences | `user_email` |
| Onboarding Flag | DataStore Preferences | `onboarding_complete` |

**Session Expiry:** Handled via 401 detection in `AuthInterceptor` → `AuthEventBus.SessionExpired`

---

## Technology Stack

| Area | Technology | Version | Evidence |
|------|------------|---------|----------|
| Language | Kotlin | 1.9.24 | `build.gradle.kts` |
| UI Framework | Android Views + ViewBinding | — | `buildFeatures.viewBinding = true` |
| Navigation | Jetpack Navigation | 2.8.0 | SafeArgs plugin, nav_graph |
| DI | Dagger Hilt | 2.51 | `@HiltAndroidApp`, modules |
| Async | Kotlin Coroutines + Flow | 1.8.1 | Throughout codebase |
| Database | Room | 2.6.1 | `@Database`, DAOs |
| Preferences | DataStore Preferences | 1.1.1 | Token/user storage |
| Networking | Retrofit + OkHttp | 2.11.0 / 4.12.0 | `ApiService`, `NetworkModule` |
| JSON | Gson | (via Retrofit) | `GsonConverterFactory` |
| Background Work | WorkManager | 2.9.0 | `TelemetryUploadWorker` |
| Splash | AndroidX Core SplashScreen | 1.0.1 | `installSplashScreen()`, Android 12+ handoff |
| Charting | MPAndroidChart | 3.1.0 | Speed visualization |
| ViewPager | ViewPager2 | 1.1.0 | Session result tabs |
| Testing | JUnit4, Mockito, Espresso | — | Test dependencies |
| Testing | MockWebServer | 4.12.0 | API integration tests |

---

## Testing Structure (ASPICE-Aligned)

| Level | Folder | ASPICE | Location | Purpose |
|-------|--------|--------|----------|---------|
| L1 | `L1_SWE4_unit` | SWE.4 | `app/src/test/` | Unit Verification |
| L2 | `L2_SWE5_integration` | SWE.5 | `app/src/androidTest/` | Integration Test |
| L3 | `L3_SWE6_qualification` | SWE.6 | `app/src/androidTest/e2e/` | SW Qualification Test |
| L4 | `L4_SYS5_acceptance` | SYS.5 | `05_tests/L4_SYS5_acceptance/` | System Qualification Test |

**Documentation:**
- Test Strategy: `05_tests/Test_Strategy.md`
- Agent Execution: `05_tests/test_strategy_execution_instructions.md`
- Test Data: `05_tests/data/`
- Traceability: `01_requirements/TRACEABILITY_MATRIX.md`

**Test Infrastructure:**
- Setup emulator: `./05_tests/infra/scripts/setup-emulator.sh`
- Start emulator: `./05_tests/infra/scripts/start-emulator.sh`
- Run all tests: `./05_tests/infra/scripts/run-all-tests.sh`
- Generate report: `./05_tests/infra/scripts/generate-report.sh`

**Emulator Configuration:**
- API Level: 30 (Android 11)
- Device: Pixel 4
- AVD Name: `DrivingCoach_Test`
- **Requires:** KVM access (`/dev/kvm`) for hardware acceleration

**Test Execution Commands:**
```bash
# Recommended: Run all + generate report
./05_tests/infra/scripts/run-all-tests.sh

# Or manually:
./gradlew testDebugUnitTest                  # L1 Unit
./gradlew connectedDebugAndroidTest          # L2 Integration
```

---

## External Services

| Service | Type | Evidence | Status |
|---------|------|----------|--------|
| Backend API | REST | `ApiService`, `TelemetryApiService` | Required |
| — | — | `http://10.0.2.2:3000/` | Emulator localhost |

**Backend Stack (from `/backend/package.json`):**
- Runtime: Node.js 20+, Express 4.19
- Database: PostgreSQL 8.11
- AI: Anthropic Claude SDK (`@anthropic-ai/sdk`)
- Auth: JWT (`jsonwebtoken`) + bcrypt

**No other external services detected:**
- ❌ Firebase (Auth, Crashlytics, Analytics, FCM)
- ❌ Third-party analytics (Amplitude, Mixpanel, etc.)
- ❌ Feature flag services (LaunchDarkly, etc.)
- ❌ Payment providers
- ❌ CDN/storage providers

---

## Logging & Observability

### Logging Framework

| Component | Implementation |
|-----------|----------------|
| Application Logging | `android.util.Log` (standard Android) |
| HTTP Logging | OkHttp `HttpLoggingInterceptor` at BODY level |

**Log Tags:**
- `TelemetryService` — Foreground service lifecycle, GPS events
- `TelemetryFileWriter` — File I/O operations
- `TelemetryUploadWorker` — Upload attempts, status changes

### Crash Reporting

**Status:** NOT IMPLEMENTED

**Risk:** Production crashes will go undetected without manual log collection.

### Metrics

**Status:** NOT IMPLEMENTED

No custom metrics, performance monitoring, or APM integration detected.

### Analytics Events

**Status:** NOT IMPLEMENTED

No event tracking for user actions, session starts/ends, or feature usage.

### Debug Tooling

| Tool | Status |
|------|--------|
| Network logging | ✅ HttpLoggingInterceptor (BODY level always on) |
| Room schema export | ✅ `exportSchema = true` → `/app/schemas/` |
| StrictMode | ❌ Not configured |
| LeakCanary | ❌ Not included |

---

## Background Processing

### WorkManager Jobs

| Worker | Tag | Schedule | Constraints |
|--------|-----|----------|-------------|
| `TelemetryUploadWorker` | `telemetry_upload` | OneTimeWorkRequest | Network required |

**Input Data:**
- `session_id: Long` — Room session ID to upload

**Output:**
- Updates `sessions.uploadStatus` in Room
- Sets `sessions.remoteSessionId` on success

### Retry & Backoff

```
Initial Backoff: 10 seconds
Policy: EXPONENTIAL
Max Attempts: 5 (runAttemptCount check)

Retry triggers:
- Network exceptions
- HTTP 5xx responses

No retry:
- HTTP 4xx responses (permanent failure)
```

### Offline Queue

Implicit via Room database:
- Sessions created locally with `uploadStatus = PENDING`
- Query: `SELECT * FROM sessions WHERE uploadStatus = 'PENDING' OR uploadStatus = 'FAILED'`
- Stale upload detection: sessions pending > 5 minutes

**Manual retry:** User can trigger via `SessionResultViewModel.retryAnalysis()`

### Sync Loops

| Component | Pattern | Interval |
|-----------|---------|----------|
| `SessionResultViewModel` | Polling while processing | 5 seconds |

**Polling Logic:**
```kotlin
while (processingStatus in [PENDING, UPLOADING, DETECTING_LAPS, GENERATING_COACHING]) {
    delay(5000)
    refreshSession()
}
```

### Notification Processing

**Not applicable** — No push notification implementation.

---

## Build & Deployment

### Build Configuration

| Property | Value |
|----------|-------|
| Gradle Plugin | 8.5.0 |
| Kotlin | 1.9.24 |
| Java Compatibility | 17 |
| ProGuard/R8 | Disabled (`isMinifyEnabled = false`) |
| Compile SDK | 35 |
| Target SDK | 35 |
| Version Code | 1 |
| Version Name | 1.0.0 |

### Build Types

| Type | Minify | ProGuard |
|------|--------|----------|
| `debug` | No | — |
| `release` | No | `proguard-rules.pro` (rules defined but not active) |

### Product Flavors

**None configured** — single variant only.

### CI/CD

**Status:** NOT DETECTED

No CI/CD workflow files found in `.github/workflows/` or other common locations.

### Signing

**Not configured** in committed code — relies on debug signing or manual release signing.

### Release Flow

**Assumed manual** — no automated release pipeline detected.

---

## Operational Risks

### Critical Risks

| Risk | Severity | Evidence | Mitigation |
|------|----------|----------|------------|
| **No crash reporting** | HIGH | No Crashlytics/Sentry | Add crash reporting SDK |
| **Hardcoded dev URL** | HIGH | `http://10.0.2.2:3000/` | Use BuildConfig + flavors |
| **DB schema changes** | MEDIUM | `MIGRATION_1_2` exists, `fallbackToDestructiveMigration()` fallback | Continue adding explicit migrations |
| **No token refresh** | MEDIUM | Only 401 detection, no refresh flow | Implement token refresh |
| **HTTP logging in release** | MEDIUM | Always BODY level | Conditional on `BuildConfig.DEBUG` |

### GPS/Location Risks

| Risk | Severity | Evidence | Mitigation |
|------|----------|----------|------------|
| **GPS lock timeout** | LOW | 5s timeout, user feedback | Consider longer timeout + guidance |
| **GPS signal loss** | LOW | 10s threshold triggers warning | Implemented with notification update |
| **Location permission denial** | MEDIUM | Checked but service fails | Pre-check in UI before starting |

### Sync & Upload Risks

| Risk | Severity | Evidence | Mitigation |
|------|----------|----------|------------|
| **Retry storm potential** | LOW | Exponential backoff + max 5 attempts | Adequate |
| **Large file uploads** | MEDIUM | Full JSONL file per session | Consider chunking or compression |
| **Stale uploads unnoticed** | LOW | 5-minute threshold banner | Consider automatic retry or notification |
| **Upload failure silent** | MEDIUM | Only banner on home screen | Add push notification or persistent alert |

### Memory & Performance Risks

| Risk | Severity | Evidence | Mitigation |
|------|----------|----------|------------|
| **High-frequency sensor buffering** | LOW | `SENSOR_DELAY_FASTEST` | Only latest values kept |
| **JSONL file growth** | MEDIUM | Unbounded during session | Consider periodic flush + size limits |
| **No image caching** | LOW | ShareCardGenerator creates bitmaps | One-off generation, low risk |

### Authentication Risks

| Risk | Severity | Evidence | Mitigation |
|------|----------|----------|------------|
| **Token stored plaintext** | MEDIUM | DataStore Preferences | Use EncryptedSharedPreferences |
| **No biometric/PIN lock** | LOW | Standard app security | Consider for sensitive data |
| **Blocking runBlocking in interceptor** | MEDIUM | `AuthInterceptor.intercept()` | Consider callback-based approach |

### Startup Risks

| Risk | Severity | Evidence | Mitigation |
|------|----------|----------|------------|
| ~~**Blocking DataStore read**~~ | ✅ RESOLVED v2.8 | Was `runBlocking` in `MainActivity.setupNavigation()` | Replaced by async `SplashViewModel` resolution on `@IoDispatcher` |
| **No splash/loading state** | LOW | Direct navigation decision | Add proper splash handling |

---

## Assumptions & Confidence Levels

| Statement | Confidence | Basis |
|-----------|------------|-------|
| Architecture is MVVM with Clean Architecture intent | HIGH | Package structure, ViewModel usage |
| No Firebase integration | HIGH | No Firebase dependencies in Gradle |
| No analytics/crash reporting | HIGH | No SDK dependencies found |
| Backend uses Anthropic Claude for coaching | HIGH | `@anthropic-ai/sdk` in backend package.json |
| Single-module architecture | HIGH | `settings.gradle.kts` includes only `:app` |
| No CI/CD pipeline | MEDIUM | No workflow files found; may exist externally |
| Production URL not configured | HIGH | Only emulator localhost in code |

---

## Appendix: Key File Paths

| Purpose | Path |
|---------|------|
| Gradle build config | `/app/build.gradle.kts` |
| Android Manifest | `/app/src/main/AndroidManifest.xml` |
| Hilt Application | `/app/src/main/java/.../DrivingCoachApp.kt` |
| Network Module | `/app/src/main/java/.../di/NetworkModule.kt` |
| Database | `/app/src/main/java/.../data/db/DrivingCoachDatabase.kt` |
| Foreground Service | `/app/src/main/java/.../service/TelemetryForegroundService.kt` |
| Upload Worker | `/app/src/main/java/.../data/worker/TelemetryUploadWorker.kt` |
| Auth Interceptor | `/app/src/main/java/.../data/api/AuthInterceptor.kt` |
| Room Schemas | `/app/schemas/` |
| Backend | `/backend/` |
