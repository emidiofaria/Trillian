# BMW Driving Coach — V1 Implementation Plan & Copilot CLI Prompts

> **How to use this document**
> Each phase contains: the goal, the ordered list of Copilot CLI prompt commands to run, and the acceptance criteria that must pass before moving to the next phase.
> Run prompts sequentially inside your project root unless stated otherwise.
> Model: use `claude-opus-4-5` for architecture scaffolding; `claude-sonnet-4-5` for feature implementation and tests.

---

## Project structure overview

```
bmw-driving-coach/
├── app/                        # Android application module
│   └── src/
│       ├── main/
│       │   ├── java/com/bmw/drivingcoach/
│       │   │   ├── data/           # Repositories, data sources, Room DB
│       │   │   ├── domain/         # Use cases, models, interfaces
│       │   │   ├── ui/             # ViewModels, Fragments, Activities
│       │   │   ├── service/        # TelemetryForegroundService
│       │   │   └── di/             # Hilt modules
│       │   └── res/
│       └── test/ androidTest/
├── backend/                    # Node.js / Python backend (separate repo or subfolder)
│   ├── src/
│   │   ├── auth/
│   │   ├── telemetry/
│   │   ├── lap/
│   │   └── coaching/
│   └── tests/
└── shared/                     # Shared constants, telemetry file schema
```

---

## Phase 1 — Android project scaffold & design system

### Goal
Bootstrap a clean Android project with all dependencies, a BMW-themed design system, navigation graph, and empty screen shells. No business logic yet.

### Prompt 1.1 — Project scaffold

```
gh copilot suggest "Create a new Android project scaffold for BMW Driving Coach with the following setup:

Language: Kotlin
Min SDK: 26 (Android 8.0)
Target SDK: 35
Build system: Gradle with Kotlin DSL (build.gradle.kts)
Package name: com.bmw.drivingcoach

Add these dependencies to app/build.gradle.kts:
- androidx.core:core-ktx:1.13.1
- androidx.appcompat:appcompat:1.7.0
- com.google.android.material:material:1.12.0
- androidx.constraintlayout:constraintlayout:2.1.4
- androidx.navigation:navigation-fragment-ktx:2.8.0
- androidx.navigation:navigation-ui-ktx:2.8.0
- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0
- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0
- com.google.dagger:hilt-android:2.51
- com.google.dagger:hilt-compiler:2.51 (kapt)
- androidx.room:room-runtime:2.6.1
- androidx.room:room-ktx:2.6.1
- androidx.room:room-compiler:2.6.1 (kapt)
- com.squareup.retrofit2:retrofit:2.11.0
- com.squareup.retrofit2:converter-gson:2.11.0
- com.squareup.okhttp3:logging-interceptor:4.12.0
- androidx.datastore:datastore-preferences:1.1.1
- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1
- junit:junit:4.13.2 (testImplementation)
- androidx.test.ext:junit:1.2.1 (androidTestImplementation)
- androidx.test.espresso:espresso-core:3.6.1 (androidTestImplementation)

Enable plugins: kotlin-android, kotlin-kapt, dagger.hilt.android.plugin, androidx.navigation.safeargs.kotlin

Create AndroidManifest.xml with permissions:
- android.permission.ACCESS_FINE_LOCATION
- android.permission.ACCESS_COARSE_LOCATION  
- android.permission.ACTIVITY_RECOGNITION
- android.permission.INTERNET
- android.permission.FOREGROUND_SERVICE
- android.permission.FOREGROUND_SERVICE_LOCATION

Register a TelemetryForegroundService (exported=false).
Register MainActivity as LAUNCHER with theme @style/Theme.BMWDrivingCoach.
"
```

### Prompt 1.2 — Design system (theme + typography)

```
gh copilot suggest "Create the BMW Driving Coach design system for Android.

Create res/values/colors.xml with:
- colorPrimary: #1C69D4 (BMW blue)
- colorPrimaryDark: #0A3D7C
- colorPrimaryVariant: #155FBD
- colorSecondary: #FFFFFF
- colorBackground: #0D0D0D (near-black, luxury feel)
- colorSurface: #1A1A1A
- colorSurfaceVariant: #242424
- colorOnPrimary: #FFFFFF
- colorOnBackground: #FFFFFF
- colorOnSurface: #FFFFFF
- colorOnSurfaceVariant: #B0B0B0
- colorError: #CF3A3A
- colorSuccess: #2ECC71
- colorDeltaPositive: #2ECC71 (faster = green)
- colorDeltaNegative: #E74C3C (slower = red)
- colorGold: #C9A227 (best lap highlight)

Create res/values/themes.xml:
- Theme.BMWDrivingCoach extends Theme.MaterialComponents.DayNight.NoActionBar
- Set colorPrimary, colorPrimaryVariant, colorOnPrimary, colorSecondary, colorBackground, colorSurface, colorOnSurface
- windowBackground = colorBackground
- Use dark status bar

Create res/values/type.xml with TextAppearances using Roboto:
- TextAppearance.BMW.H1: 28sp, medium
- TextAppearance.BMW.H2: 22sp, medium  
- TextAppearance.BMW.H3: 18sp, medium
- TextAppearance.BMW.Body: 14sp, regular
- TextAppearance.BMW.Caption: 12sp, regular, colorOnSurfaceVariant
- TextAppearance.BMW.LapTime: 48sp, bold, monospace (for large lap time display)
- TextAppearance.BMW.Delta: 14sp, medium, monospace

Create res/values/dimens.xml:
- spacing_xs: 4dp
- spacing_sm: 8dp
- spacing_md: 16dp
- spacing_lg: 24dp
- spacing_xl: 32dp
- card_corner_radius: 12dp
- button_corner_radius: 8dp

Create a reusable component res/layout/component_lap_time_card.xml (MaterialCardView, dark surface, lap number, lap time in LapTime style, delta badge).
"
```

### Prompt 1.3 — Navigation graph and screen shells

```
gh copilot suggest "Create the Android Navigation Component setup for BMW Driving Coach.

Create res/navigation/nav_graph.xml with these destinations:
1. LoginFragment (startDestination)
2. RegisterFragment
3. HomeFragment (session history + start session CTA)
4. RecordingFragment (live recording screen, fullscreen)
5. SessionResultFragment (post-session tabs: Laps, Coach, Chart)
6. LapDetailFragment (lap comparison detail)
7. ProfileFragment

Actions:
- Login → Home (clearBackStack)
- Login → Register
- Register → Home (clearBackStack)
- Home → Recording (startSession action)
- Recording → SessionResult (sessionId arg, Long)
- SessionResult → LapDetail (lapId arg, Long)
- Home → Profile

Create MainActivity.kt:
- Single activity, NavHostFragment fills the window
- Handle back press with NavController
- Apply edge-to-edge window insets
- Inject with @AndroidEntryPoint

Create empty Kotlin Fragment shells for each destination (just onCreateView returning the layout, @AndroidEntryPoint annotation, corresponding ViewModel injection placeholder with 'by viewModels()').

Create corresponding empty layout XML files using ConstraintLayout, dark background, with a placeholder TextView showing the screen name.
"
```

### Phase 1 acceptance criteria
- Project builds without errors: `./gradlew assembleDebug`
- Navigation graph renders in Android Studio without errors
- All 7 fragment shells exist and are reachable in the nav graph

---

## Phase 2 — Data layer: Room database & local telemetry storage

### Goal
Define the complete local database schema, DAOs, and the telemetry file writer. This layer must work fully offline.

### Prompt 2.1 — Room entities and database

```
gh copilot suggest "Create the Room database layer for BMW Driving Coach Android app.

Package: com.bmw.drivingcoach.data.db

Create these @Entity classes:

SessionEntity.kt:
- id: Long (PrimaryKey, autoGenerate)
- userId: String
- trackName: String
- startedAt: Long (epoch ms)
- endedAt: Long? (nullable, null while recording)
- rawFilePath: String (local file path of telemetry file)
- uploadStatus: String (enum string: PENDING, UPLOADING, DONE, FAILED)
- remoteSessionId: String? (server-assigned ID after upload)

LapEntity.kt:
- id: Long (PrimaryKey, autoGenerate)
- sessionId: Long (ForeignKey → SessionEntity.id, onDelete CASCADE)
- lapNumber: Int
- startTs: Long
- endTs: Long
- durationMs: Long
- sector1Ms: Long
- sector2Ms: Long
- sector3Ms: Long
- isBestLap: Boolean

CoachingInsightEntity.kt:
- id: Long (PrimaryKey, autoGenerate)
- sessionId: Long (ForeignKey → SessionEntity.id, onDelete CASCADE)
- headline: String
- detail: String
- generatedAt: Long

Create DAOs:

SessionDao.kt:
- insertSession(session): Long
- updateSession(session)
- getSessionById(id): Flow<SessionEntity?>
- getAllSessionsForUser(userId): Flow<List<SessionEntity>>
- getPendingUploadSessions(): List<SessionEntity>
- updateUploadStatus(id, status, remoteId)

LapDao.kt:
- insertLaps(laps: List<LapEntity>)
- getLapsForSession(sessionId): Flow<List<LapEntity>>
- getBestLap(sessionId): Flow<LapEntity?>
- updateBestLap(sessionId, lapId)

CoachingInsightDao.kt:
- insertInsights(insights: List<CoachingInsightEntity>)
- getInsightsForSession(sessionId): Flow<List<CoachingInsightEntity>>

Create BMWDatabase.kt:
- @Database(entities=[SessionEntity, LapEntity, CoachingInsightEntity], version=1)
- Abstract class extending RoomDatabase
- Include migration strategy (destructive for v1)

Create DatabaseModule.kt (@Module @InstallIn(SingletonComponent)):
- Provide BMWDatabase as singleton
- Provide each DAO from the database instance
"
```

### Prompt 2.2 — Telemetry file writer

```
gh copilot suggest "Create the local telemetry file writer for BMW Driving Coach.

Package: com.bmw.drivingcoach.data.telemetry

Create TelemetrySample.kt (data class, NOT a Room entity — this is written to file):
- timestampMs: Long
- latitude: Double
- longitude: Double
- speedMs: Float (m/s)
- headingDeg: Float
- accelX: Float, accelY: Float, accelZ: Float (m/s²)
- gyroX: Float, gyroY: Float, gyroZ: Float (rad/s)
- gpsAccuracyM: Float

Create TelemetryFileWriter.kt:
- Constructor takes context: Context and sessionId: Long
- Creates file at context.filesDir/telemetry/session_{sessionId}.jsonl
- fun writeSample(sample: TelemetrySample): writes one JSON line (JSONL format, one object per line) using Gson
- fun close(): flushes and closes the writer
- fun getFilePath(): String
- All IO on Dispatchers.IO
- Thread-safe: use a mutex (kotlinx.coroutines.sync.Mutex) around writes

Create TelemetryFileReader.kt:
- fun readAll(filePath: String): List<TelemetrySample> — reads JSONL file, parses each line, skips malformed lines with a warning log
- Returns empty list if file does not exist

Write unit tests in test/TelemetryFileWriterTest.kt:
- Test write and read round-trip: write 100 samples, read back, assert count and field values match
- Test that a malformed line in the file does not crash readAll
"
```

### Phase 2 acceptance criteria
- `./gradlew test` passes all unit tests in Phase 2
- Room schema exports without errors (enable schema export in build config)
- TelemetryFileWriter can write 10 Hz samples for 30 minutes (~18,000 samples) in under 100ms total (benchmark test)

---

## Phase 3 — Sensor capture: GPS + IMU foreground service

### Goal
Implement the TelemetryForegroundService that captures GPS and IMU and writes to file. This is the most safety-critical component — it must be reliable and handle all edge cases.

### Prompt 3.1 — Foreground service

```
gh copilot suggest "Create TelemetryForegroundService.kt for BMW Driving Coach.

Package: com.bmw.drivingcoach.service

Requirements:
- Extends Service, declared in AndroidManifest with foregroundServiceType='location'
- Shows a persistent notification: 'BMW Driving Coach — Recording' with elapsed time updating every second, channel id 'bmw_recording'
- Exposes a Binder for the RecordingFragment to bind and get live status

Implement these intents/actions the service responds to:
- ACTION_START_RECORDING (extra: SESSION_ID: Long) — begins capture
- ACTION_STOP_RECORDING — ends capture, closes file, triggers upload work

GPS capture:
- Use LocationManager (not FusedLocationProvider — avoids Play Services dependency)
- Request updates with: provider=GPS_PROVIDER, minTimeMs=100 (10 Hz), minDistanceM=0f
- On each LocationResult: create TelemetrySample, populate lat/lng/speed/heading/accuracy, write via TelemetryFileWriter
- Track GPS lock: if no fix within 5 seconds of start, post GPS_NOT_LOCKED event via StateFlow

IMU capture:
- Register SensorEventListener for TYPE_ACCELEROMETER and TYPE_GYROSCOPE
- Sampling rate: SENSOR_DELAY_FASTEST (fastest available, typically 50-200 Hz)
- Buffer the latest accelerometer and gyroscope values
- When a GPS sample is written, embed the latest IMU buffer values in that sample (merge by timestamp)

State machine:
- IDLE → RECORDING → STOPPING → IDLE
- Expose currentState: StateFlow<RecordingState> where RecordingState is a sealed class:
  - Idle
  - Recording(sessionId: Long, elapsedMs: Long, lapCount: Int, gpsLocked: Boolean)
  - Stopping

On STOP:
1. Unregister sensors and location listener
2. Close TelemetryFileWriter
3. Update SessionEntity.endedAt in Room via SessionDao
4. Enqueue TelemetryUploadWorker (WorkManager) for the session

Error handling:
- If GPS permission is denied when START is received: post error state, stop self
- If file write fails: log error, continue capture (do not crash)
- Catch all exceptions in sensor callbacks — never let them propagate

Write androidTest/TelemetryForegroundServiceTest.kt:
- Test that service starts and posts Recording state within 2 seconds
- Test that stopping the service transitions to Idle state
- Test that elapsedMs increases over time
"
```

### Prompt 3.2 — WorkManager upload worker

```
gh copilot suggest "Create TelemetryUploadWorker.kt for BMW Driving Coach.

Package: com.bmw.drivingcoach.data.worker

Use WorkManager with HiltWorker injection.

Constructor inputs (via WorkerParameters):
- KEY_SESSION_ID: Long (inputData)

Implement doWork():
1. Load SessionEntity from Room by sessionId; if not found, return Result.failure()
2. Read telemetry file from SessionEntity.rawFilePath using TelemetryFileWriter.getFilePath()
3. Update SessionEntity.uploadStatus = UPLOADING in Room
4. Call TelemetryApiService.uploadSession(sessionId, telemetryFile) — multipart POST
5. On HTTP 200-201: update uploadStatus = DONE, store remoteSessionId; return Result.success()
6. On network error or HTTP 4xx/5xx: update uploadStatus = FAILED; return Result.retry() for 5xx, Result.failure() for 4xx
7. Constraints: NetworkType.CONNECTED

WorkRequest setup (create in a companion object):
- Use OneTimeWorkRequest with exponential backoff (initial delay 10s, max 5 retries)
- Add tag 'telemetry_upload'

Write unit tests using WorkManagerTestInitHelper:
- Test successful upload transitions status to DONE
- Test that 5xx triggers retry
- Test that session not found returns failure
"
```

### Phase 3 acceptance criteria
- Service runs on a real or emulated device without crashing
- TelemetryFileWriter produces a valid JSONL file after a 1-minute test recording
- WorkManager upload worker retries correctly on network failure (tested with MockWebServer)

---

## Phase 4 — Backend: API server

### Goal
Build the backend API that accepts telemetry uploads, processes laps, and triggers AI coaching. Implement as a Node.js (TypeScript) Express server.

### Prompt 4.1 — Project scaffold and auth

```
gh copilot suggest "Create a Node.js TypeScript Express backend for BMW Driving Coach.

Project root: backend/
Init with: npm init, TypeScript 5.x, ts-node, nodemon

Dependencies:
- express, @types/express
- jsonwebtoken, bcryptjs, @types/bcryptjs
- multer (file uploads), @types/multer
- pg (PostgreSQL), @types/pg
- dotenv
- zod (request validation)
- uuid

Dev dependencies: typescript, ts-node, nodemon, jest, supertest, @types/jest, @types/supertest

tsconfig.json: target ES2022, module commonjs, strict true, outDir dist/

Create src/app.ts: Express app, JSON middleware, multer for /telemetry/upload, error handler middleware.
Create src/server.ts: listen on PORT from env.

Create database schema (src/db/schema.sql):
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) UNIQUE NOT NULL,
  display_name VARCHAR(100),
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  track_name VARCHAR(200),
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  raw_file_path TEXT,
  processing_status VARCHAR(30) DEFAULT 'PENDING',
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE laps (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID REFERENCES sessions(id) ON DELETE CASCADE,
  lap_number INT NOT NULL,
  start_ts BIGINT NOT NULL,
  end_ts BIGINT NOT NULL,
  duration_ms BIGINT NOT NULL,
  sector_1_ms BIGINT,
  sector_2_ms BIGINT,
  sector_3_ms BIGINT,
  is_best_lap BOOLEAN DEFAULT FALSE
);

CREATE TABLE coaching_insights (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID REFERENCES sessions(id) ON DELETE CASCADE,
  headline VARCHAR(200) NOT NULL,
  detail TEXT NOT NULL,
  generated_at TIMESTAMPTZ DEFAULT NOW()
);

Create src/auth/authRouter.ts with routes:
POST /auth/register — validate email/password (zod), hash password (bcrypt cost=12), insert user, return JWT
POST /auth/login — validate credentials, return JWT (expires 30d)
GET /auth/me — require JWT middleware, return user profile

Create src/middleware/requireAuth.ts — JWT verification middleware, attach req.user
"
```

### Prompt 4.2 — Telemetry ingestion endpoint

```
gh copilot suggest "Create the telemetry ingestion endpoint for BMW Driving Coach backend.

File: src/telemetry/telemetryRouter.ts

POST /telemetry/upload
- Requires JWT auth (requireAuth middleware)
- Accepts multipart/form-data with fields:
  - sessionId: string (UUID)
  - trackName: string
  - startedAt: number (epoch ms)
  - endedAt: number (epoch ms)
  - file: the .jsonl telemetry file (max 50MB)
- Validate all fields with zod
- Store the file to disk at uploads/{userId}/{sessionId}.jsonl
- Insert or update a row in sessions table with the provided metadata and raw_file_path
- Set processing_status = 'PENDING'
- Enqueue the session for async lap processing by calling processSessionAsync(sessionId) — a fire-and-forget async function (do not await)
- Return HTTP 201 with { sessionId, status: 'PROCESSING' }

GET /sessions — list all sessions for the authenticated user, ordered by started_at DESC
GET /sessions/:sessionId — return session + laps + coaching_insights (joined query)
GET /sessions/:sessionId/laps — return laps array for session

Create src/telemetry/telemetryService.ts:
- parseJsonlFile(filePath): Promise<TelemetrySample[]> — reads file line by line, parses JSON, skips malformed lines
- TelemetrySample interface: { timestampMs, latitude, longitude, speedMs, headingDeg, accelX, accelY, accelZ, gyroX, gyroY, gyroZ, gpsAccuracyM }
"
```

### Prompt 4.3 — Lap detection algorithm

```
gh copilot suggest "Create the lap detection algorithm for BMW Driving Coach backend.

File: src/lap/lapDetector.ts

Export function detectLaps(samples: TelemetrySample[]): DetectedLap[]

Algorithm — start/finish line auto-detection:
1. Take the first 30 seconds of samples (or first 300 samples, whichever is smaller) to define the 'start zone'
2. Compute the centroid of the start zone: avgLat, avgLng
3. Define a start/finish radius of 15 metres around the centroid
4. Walk through all samples chronologically. A lap boundary is detected when:
   a. The driver was OUTSIDE the start zone radius (at least 200m away, to avoid false triggers at session start)
   b. AND then re-enters the start zone radius
   c. AND at least 30 seconds have elapsed since the last lap boundary (minimum lap time guard)
5. Each lap is: { lapNumber, startTs, endTs, durationMs, samples: TelemetrySample[] }

Sector computation (computeSectors):
- Split each lap's samples into 3 equal-time thirds
- sector1Ms = time of first third, sector2Ms = second, sector3Ms = third

Haversine distance helper:
- haversineMetres(lat1, lng1, lat2, lng2): number — use the standard formula, return distance in metres

Export function computeSectors(lap: DetectedLap): { sector1Ms, sector2Ms, sector3Ms }

Validation:
- If detectLaps returns fewer than 2 laps, throw LapDetectionError('Insufficient laps detected')
- Log a warning (not an error) if any lap duration is under 20 seconds

File: src/lap/lapProcessor.ts

Export async function processSession(sessionId: string): Promise<void>
1. Load session from DB, get raw_file_path
2. Parse JSONL file → TelemetrySample[]
3. Run detectLaps → DetectedLap[]
4. Run computeSectors for each lap
5. Identify best lap (minimum durationMs)
6. Insert all laps into DB (laps table), mark best lap
7. Update sessions.processing_status = 'LAPS_DONE'
8. Trigger coaching: await generateCoaching(sessionId) 
9. Update sessions.processing_status = 'COMPLETE'
10. On any error: update processing_status = 'FAILED', log error

Write unit tests in tests/lapDetector.test.ts:
- Test with synthetic samples: a circular GPS trace (simulate a 400m oval, 5 laps)
- Assert exactly 5 laps detected
- Assert each lap duration is within 5% of the true simulated lap time
- Test minimum lap time guard: inject a crossing at 10 seconds, assert it is ignored
- Test sector computation: assert sector1+sector2+sector3 = lap duration
"
```

### Prompt 4.4 — AI coaching generation

```
gh copilot suggest "Create the AI coaching insight generator for BMW Driving Coach backend.

File: src/coaching/coachingService.ts

Use Anthropic Node.js SDK: import Anthropic from '@anthropic-ai/sdk'
API key from process.env.ANTHROPIC_API_KEY

Export async function generateCoaching(sessionId: string): Promise<void>

Steps:
1. Load session from DB
2. Load all laps for session (with sector times) from DB
3. Compute session stats:
   - bestLapMs: minimum durationMs
   - avgLapMs: mean durationMs
   - consistencyScore: (1 - stddev/mean) * 100, rounded to 1 decimal
   - worstSector per lap (sector with highest time relative to best lap's sector)
   - deltaPerSector for each lap vs best lap (positive = slower)

4. Build the coaching prompt:

System prompt (set as system parameter):
'You are a professional motorsport driving coach. You analyse telemetry data and provide precise, actionable coaching feedback. Be direct. Use specific numbers. Reference sectors and laps by number. You are coaching a driver who just completed a track session.'

User prompt (construct from data):
'Session analysis for {trackName}:
- Total laps: {lapCount}
- Best lap: {bestLapMs}ms (Lap {bestLapNumber})
- Average lap: {avgLapMs}ms
- Consistency score: {consistencyScore}%
- Sector breakdown vs best lap (delta in ms, positive = slower than best):
{lapSectorTable}

Provide exactly 4 coaching tips. Each tip must:
- Start with a headline of maximum 8 words
- Follow with a detail sentence of maximum 35 words
- Be specific: reference lap numbers, sector numbers, or time values
- Be actionable: tell the driver exactly what to do differently

Respond ONLY with a JSON array, no other text:
[{"headline": "...", "detail": "..."}, ...]'

5. Call Anthropic API:
   model: 'claude-sonnet-4-20250514'
   max_tokens: 600
   system: (system prompt above)
   messages: [{ role: 'user', content: (user prompt above) }]

6. Parse the response: extract JSON array from content[0].text, strip markdown code fences if present
7. Validate: array must have 3-5 items, each with headline (string) and detail (string)
8. Insert into coaching_insights table
9. On parse error or API error: log error, do NOT throw (coaching failure must not break the session)

Write unit tests in tests/coachingService.test.ts:
- Mock the Anthropic SDK
- Test that prompt is correctly constructed from sample session data
- Test that a valid JSON response is correctly parsed and inserted
- Test that a malformed API response (non-JSON text) does not throw and logs an error
"
```

### Phase 4 acceptance criteria
- `npm test` passes all backend unit tests
- POST /auth/register and /auth/login return valid JWTs
- POST /telemetry/upload accepts a JSONL file and returns 201
- Lap detection unit test passes with synthetic circular GPS data
- Coaching generation inserts 3-5 tips for a valid session

---

## Phase 5 — Android UI: Recording screen

### Goal
Build the live recording screen. It binds to the TelemetryForegroundService, shows elapsed time, GPS status, and a stop button.

### Prompt 5.1 — Recording screen

```
gh copilot suggest "Create the RecordingFragment and RecordingViewModel for BMW Driving Coach Android.

Layout: res/layout/fragment_recording.xml
- Full screen, dark background (#0D0D0D)
- Top: BMW logo (placeholder vector, blue circle) centred
- Large centre: elapsed time in TextAppearance.BMW.LapTime style (MM:SS.mmm format)
- Below time: GPS status row — green dot icon + 'GPS LOCKED' or red dot + 'ACQUIRING GPS...'
- Below status: 'TAP TO STOP RECORDING' button — large, red (#CF3A3A), full width minus 32dp margins, rounded 8dp, uppercase
- Bottom safe area padding

RecordingViewModel.kt:
- Binds to TelemetryForegroundService via ServiceConnection
- Exposes:
  - uiState: StateFlow<RecordingUiState>
  - RecordingUiState: data class { elapsedMs: Long, gpsLocked: Boolean, isRecording: Boolean }
- fun startRecording(sessionId: Long): sends ACTION_START_RECORDING intent, binds service
- fun stopRecording(): sends ACTION_STOP_RECORDING intent
- Collects service.currentState and maps to uiState
- On service state = Stopping → navigate to SessionResultFragment

RecordingFragment.kt:
- @AndroidEntryPoint
- Requests FINE_LOCATION and ACTIVITY_RECOGNITION permissions on first launch using ActivityResultContracts.RequestMultiplePermissions
- If permissions denied: show Snackbar 'Location permission is required to record' and pop back stack
- Binds ViewModel, collects uiState as lifecycle-aware
- Updates elapsed time text every collected emission
- Updates GPS indicator colour based on gpsLocked
- Stop button click → viewModel.stopRecording()
- Navigate to SessionResultFragment on state change to Stopping, passing sessionId

Write espresso test in androidTest/RecordingFragmentTest.kt:
- Launch fragment with a mock ViewModel
- Assert elapsed time text is displayed
- Assert stop button is visible and clickable
- Click stop button, assert navigation to SessionResultFragment is triggered
"
```

### Phase 5 acceptance criteria
- Recording screen renders correctly on a 5-inch 1080p emulator
- GPS status indicator updates correctly
- Stop button triggers service stop and navigation to results

---

## Phase 6 — Android UI: Session results screen

### Goal
Build the tabbed session results screen with three tabs: Laps, Coach, Chart.

### Prompt 6.1 — Session results + Laps tab

```
gh copilot suggest "Create SessionResultFragment with a ViewPager2 tab layout for BMW Driving Coach.

Layout: res/layout/fragment_session_result.xml
- CollapsingToolbarLayout at top showing track name and best lap time in LapTime style
- TabLayout with 3 tabs: LAPS | COACH | CHART
- ViewPager2 filling remaining space
- Toolbar with back navigation

Create SessionResultViewModel.kt:
- Injected with SessionRepository (which calls the backend GET /sessions/{id} or reads from Room)
- sessionId: Long argument from nav args
- Exposes:
  - sessionState: StateFlow<SessionUiState>
  - SessionUiState: data class { session: SessionEntity, laps: List<LapEntity>, insights: List<CoachingInsightEntity>, bestLap: LapEntity?, consistencyScore: Float }
- fun loadSession(id: Long): triggers load, updates state
- computeConsistencyScore(laps): (1 - stddev/mean) * 100

Create LapsFragment.kt (tab 1):
Layout: RecyclerView of lap cards
- LapCardViewHolder: shows lap number, lap time (formatted MM:SS.mmm), sector 1/2/3 times, delta vs best lap
- Best lap row has a gold (#C9A227) left border accent and 'BEST' badge
- Delta shown as: green ▼ if faster, red ▲ if slower, with value in seconds to 3 decimal places
- On lap card tap: navigate to LapDetailFragment(lapId)

Create CoachFragment.kt (tab 2):
Layout: vertical ScrollView with cards
- Header card: 'AI COACHING' label, consistency score as a large percentage, session summary (laps, best time)
- One MaterialCardView per coaching insight: headline in H3 style, detail in Body style
- If no insights yet: show 'Analysing your session...' with a circular progress indicator

Create ChartFragment.kt (tab 3):
Layout: full-width chart area
- Use MPAndroidChart library (add to dependencies: com.github.PhilJay:MPAndroidChart:v3.1.0)
- Line chart: X axis = distance/time through lap, Y axis = speed in km/h
- Plot a line for each lap in grey, highlight the best lap in BMW blue (#1C69D4)
- Legend at top: 'Best Lap' (blue) | 'Other laps' (grey)
- Chart background dark, grid lines at 20% opacity
"
```

### Prompt 6.2 — Lap detail and comparison

```
gh copilot suggest "Create LapDetailFragment for lap comparison in BMW Driving Coach.

Layout: res/layout/fragment_lap_detail.xml
- Toolbar: 'Lap N vs Best Lap'
- Two-column header: left = selected lap time, right = best lap time
- Three sector comparison rows, each row:
  - Label: 'Sector 1', 'Sector 2', 'Sector 3'
  - Selected lap sector time (monospace)
  - Delta badge: '+0.342s' in red or '-0.108s' in green
  - Thin horizontal bar showing relative performance (full width = worst time, coloured fill = best time position)
- Bottom card: 'Speed trace comparison' placeholder (or MPAndroidChart if time allows)

LapDetailViewModel.kt:
- Takes lapId (selected lap) and sessionId
- Loads selected LapEntity and best LapEntity from Room
- Exposes lapDetailState: StateFlow<LapDetailUiState>
- LapDetailUiState: { selectedLap: LapEntity, bestLap: LapEntity, sectorDeltas: List<SectorDelta> }
- SectorDelta: { sectorNumber: Int, selectedMs: Long, bestMs: Long, deltaMs: Long }

Format helpers (in a LapTimeFormatter.kt utility object):
- formatLapTime(ms: Long): String → 'M:SS.mmm' (e.g. '1:23.456')
- formatDelta(deltaMs: Long): String → '+0.456s' or '-0.123s', always 3 decimal places
- deltaColor(deltaMs: Long, context: Context): Int → colorDeltaPositive if negative (faster), colorDeltaNegative if positive (slower)
"
```

### Phase 6 acceptance criteria
- All three tabs render with correct data from Room
- Lap cards correctly colour-code best lap and deltas
- MPAndroidChart renders speed traces without crash
- LapDetailFragment correctly computes and displays sector deltas

---

## Phase 7 — Android UI: Home, Profile, Share

### Prompt 7.1 — Home screen and session history

```
gh copilot suggest "Create HomeFragment and HomeViewModel for BMW Driving Coach.

Layout: res/layout/fragment_home.xml
- Top bar: BMW logo left, profile icon right (navigate to ProfileFragment on tap)
- Hero card (dark surface, blue accent border): 
  - 'YOUR BEST LAP' label
  - Large best lap time across all sessions (LapTime style), or '—' if no sessions
  - Track name and date of best session below
- Section label 'RECENT SESSIONS'
- RecyclerView of session history cards
- FAB (BMW blue, ⏺ icon): tap to start a new session

Session history card (SessionHistoryViewHolder):
- Track name (H3), date formatted 'dd MMM yyyy' (Body), best lap time for that session (H2 blue), laps count and consistency score (Caption)
- On tap: navigate to SessionResultFragment(sessionId)

HomeViewModel.kt:
- Loads all sessions for current user from SessionRepository
- Computes overall best lap across all sessions
- Exposes homeState: StateFlow<HomeUiState>
- HomeUiState: { sessions: List<SessionSummary>, overallBestLap: BestLapInfo? }
- fun startNewSession(trackName: String): inserts a new SessionEntity in Room, starts TelemetryForegroundService, navigates to RecordingFragment

On FAB tap: show a MaterialAlertDialog with a single EditText for track name (hint: 'e.g. Circuito de Braga'), confirm → viewModel.startNewSession(trackName)
"
```

### Prompt 7.2 — Profile and share

```
gh copilot suggest "Create ProfileFragment and the share accomplishment feature for BMW Driving Coach.

ProfileFragment layout: res/layout/fragment_profile.xml
- Toolbar with back button: 'PROFILE'
- Avatar circle (initials from display name, BMW blue background)
- Display name (H2), email (Body, muted)
- Stats row: total sessions, total laps, overall best lap time — each in a metric card style
- 'SIGN OUT' button at bottom (outlined, red text)

ProfileViewModel.kt:
- Loads user profile from AuthRepository (local DataStore cache + API)
- Loads aggregate stats from SessionRepository
- fun signOut(): clears JWT from DataStore, clears Room DB, navigates to LoginFragment

Share feature (implement in SessionResultFragment):
- Add a share icon in the toolbar of SessionResultFragment
- On tap: call ShareCardGenerator.generate(session, bestLap, consistencyScore, context)

Create ShareCardGenerator.kt:
- fun generate(session, bestLap, consistencyScore, context): Bitmap
- Creates a 1080×1080px Bitmap programmatically using Canvas:
  - Background: #0D0D0D
  - BMW Driving Coach logo text top-left (white, 28sp bold)
  - Large best lap time centred (BMW blue, 72sp bold, monospace)
  - Track name below (white, 22sp)
  - Date bottom-left (grey, 14sp)
  - Consistency score bottom-right: 'CONSISTENCY {score}%' (white, 14sp)
  - Thin BMW blue bottom border line (4dp)
- Return the Bitmap

After generating bitmap: save to FileProvider cache dir, create a share Intent via Intent.ACTION_SEND, set type 'image/png', attach FileProvider URI, start chooser via startActivity(Intent.createChooser(...))

Add FileProvider to AndroidManifest and create res/xml/file_paths.xml.
"
```

### Phase 7 acceptance criteria
- Home screen loads and lists sessions within 500ms on emulator
- Start session dialog accepts track name and transitions to RecordingFragment
- Share generates a 1080×1080 bitmap and opens the Android share sheet

---

## Phase 8 — API client and repository layer (Android)

### Prompt 8.1 — Retrofit API client

```
gh copilot suggest "Create the complete API client and repository layer for BMW Driving Coach Android.

Package: com.bmw.drivingcoach.data.api

Create ApiService.kt (Retrofit interface):
- @POST('auth/register') suspend fun register(@Body req: RegisterRequest): Response<AuthResponse>
- @POST('auth/login') suspend fun login(@Body req: LoginRequest): Response<AuthResponse>
- @GET('auth/me') suspend fun getMe(): Response<UserDto>
- @Multipart @POST('telemetry/upload') suspend fun uploadTelemetry(@Part sessionId: RequestBody, @Part trackName: RequestBody, @Part startedAt: RequestBody, @Part endedAt: RequestBody, @Part file: MultipartBody.Part): Response<UploadResponse>
- @GET('sessions') suspend fun getSessions(): Response<List<SessionDto>>
- @GET('sessions/{id}') suspend fun getSession(@Path('id') id: String): Response<SessionDetailDto>

Create DTOs in data/api/dto/:
- AuthResponse: token: String, userId: String
- UserDto: id, email, displayName
- SessionDto: id, trackName, startedAt, endedAt, processingStatus, bestLapMs, lapCount
- SessionDetailDto: session: SessionDto, laps: List<LapDto>, insights: List<InsightDto>
- LapDto: id, lapNumber, startTs, endTs, durationMs, sector1Ms, sector2Ms, sector3Ms, isBestLap
- InsightDto: id, headline, detail, generatedAt

Create AuthInterceptor.kt:
- OkHttp interceptor that reads JWT from DataStore and adds Authorization: Bearer {token} header
- If 401 response: clear token, post 'session expired' event via a SharedFlow in AuthEventBus

Create NetworkModule.kt (@Module @InstallIn(SingletonComponent)):
- Provide OkHttpClient with AuthInterceptor and HttpLoggingInterceptor (BODY level for debug)
- Provide Retrofit with BASE_URL from BuildConfig
- Provide ApiService

Create repositories:
- AuthRepository.kt: login(email, pw), register(email, pw, name), getMe(), signOut(), isLoggedIn(): Flow<Boolean>, token: Flow<String?>. Persist token in DataStore.
- SessionRepository.kt: getSessions(), getSessionDetail(id), uploadSession(sessionEntity, filePath). Sync remote data into Room after fetch. Return from Room as the single source of truth.

Write unit tests using MockWebServer:
- Test login success: stores token in DataStore
- Test 401 on any request: AuthEventBus emits session expired
- Test uploadSession: sends correct multipart fields
"
```

### Phase 8 acceptance criteria
- All Retrofit calls compile with correct types
- MockWebServer tests pass
- AuthInterceptor correctly attaches token to every request
- 401 response triggers sign-out flow

---

## Phase 9 — Integration, permissions polish, and edge cases

### Prompt 9.1 — Permission handling and onboarding

```
gh copilot suggest "Create a permission onboarding flow for BMW Driving Coach Android.

Create OnboardingFragment.kt:
- Only shown once (track with DataStore boolean 'onboarding_complete')
- Three permission explanation cards (ViewPager2 or vertical scroll):
  1. Location — 'We use GPS to track your lap times precisely. No data is shared without your consent.'
  2. Activity recognition — 'Detects when you are in a vehicle for smarter recording.'
  3. Storage — 'Telemetry is saved locally until uploaded.'
- 'GRANT PERMISSIONS' button: requests all three permissions using ActivityResultContracts.RequestMultiplePermissions
- On all granted: set onboarding_complete = true, navigate to HomeFragment
- On any denied: show which permissions were denied, offer 'OPEN SETTINGS' button linking to app settings

Add to nav_graph.xml: OnboardingFragment as a conditional start destination.
In MainActivity.kt: on launch, check DataStore for 'onboarding_complete'. If false, set start destination to OnboardingFragment, else HomeFragment.
"
```

### Prompt 9.2 — Background and error state handling

```
gh copilot suggest "Add robust error and background state handling to BMW Driving Coach Android.

1. In TelemetryForegroundService: add a GPS timeout handler. If no GPS sample is received for 10 continuous seconds during recording, post a notification update: 'GPS signal lost — move to open sky'. Resume normal notification when signal returns.

2. In SessionResultFragment: poll processing status every 5 seconds while processingStatus is PENDING or PROCESSING (use a repeating coroutine). Show a progress card: 'Processing your session...' with steps: 'Uploading ✓ | Detecting laps ○ | Generating coaching ○'. Update steps as processingStatus changes. Stop polling when status = COMPLETE or FAILED.

3. In HomeFragment: add a WorkManager status observer for any PENDING or FAILED uploads. Show a dismissible banner: 'Session upload pending — connect to Wi-Fi' if any session has uploadStatus = PENDING for more than 5 minutes.

4. Create a global error handler in MainActivity: observe AuthEventBus.sessionExpired SharedFlow; when emitted, show a Snackbar 'Session expired — please log in again' and navigate to LoginFragment clearing back stack.

5. Add a retry mechanism in SessionResultFragment: if processingStatus = FAILED, show a 'RETRY ANALYSIS' button that re-enqueues TelemetryUploadWorker.
"
```

### Phase 9 acceptance criteria
- Onboarding shown on first launch, skipped on subsequent launches
- GPS loss notification appears within 10 seconds of signal loss
- Processing status polling updates UI correctly
- Expired JWT triggers logout and redirect to login

---

## Phase 10 — Tests: end-to-end and instrumentation

### Prompt 10.1 — Full instrumentation test suite

```
gh copilot suggest "Create a comprehensive instrumented test suite for BMW Driving Coach Android.

File: androidTest/EndToEndTest.kt
Use Hilt testing (@HiltAndroidTest), mock API with MockWebServer, in-memory Room DB.

Test scenarios (implement each as a separate @Test):

1. testFullSessionFlow:
   - Log in with test credentials (mock server returns valid JWT)
   - Navigate to HomeFragment: assert session list is empty
   - Tap FAB, enter 'Test Track', confirm
   - Assert RecordingFragment is shown and elapsed time is ticking
   - Wait 3 seconds, tap STOP
   - Assert navigation to SessionResultFragment
   - Assert 'Processing your session...' card is visible
   - Mock server returns processingStatus = COMPLETE after 1 poll
   - Assert coaching insights are displayed

2. testLapComparisonDeltaColors:
   - Seed Room with a session containing 3 laps: best=83000ms, lap2=84200ms, lap3=82100ms (this is now the new best but we are comparing vs first best)
   - Navigate to SessionResultFragment(sessionId)
   - Open Laps tab
   - Assert lap 2 shows a red delta ('+1.200s')
   - Assert lap 3 shows a green delta ('-0.900s')

3. testShareCard:
   - Seed Room with a completed session
   - Navigate to SessionResultFragment
   - Tap share icon
   - Assert Android ShareSheet is presented (use Espresso Intents.intended)

4. testSignOut:
   - Log in, navigate to ProfileFragment
   - Tap SIGN OUT
   - Assert navigation to LoginFragment
   - Assert Room DB is empty (sessions count = 0)
"
```

### Prompt 10.2 — Backend integration tests

```
gh copilot suggest "Create integration tests for the BMW Driving Coach backend.

File: tests/integration/api.test.ts
Use supertest with the real Express app and a test PostgreSQL database (use DATABASE_URL_TEST env var).

beforeAll: run schema.sql migrations on test DB, seed a test user
afterAll: truncate all tables, close DB connection

Tests:

1. Auth flow:
   POST /auth/register with valid email/password → 201, returns token
   POST /auth/login with same credentials → 200, returns token
   GET /auth/me with token → 200, returns user
   GET /auth/me without token → 401

2. Upload and processing:
   POST /telemetry/upload with a synthetic JSONL file (generate 1000 samples: 5-lap oval circuit) → 201
   GET /sessions → returns the uploaded session
   Wait for processing (poll GET /sessions/:id every 500ms, max 15s)
   Assert processingStatus = COMPLETE
   Assert laps array has 5 entries
   Assert one lap has isBestLap = true
   Assert coaching_insights array has 3-5 entries (mock Anthropic API in test env with ANTHROPIC_API_KEY=test, mock the SDK)

3. Lap detection edge cases:
   Upload a file with only 1 complete lap → session processingStatus = FAILED, laps = []
   Upload an empty file → 400 error

Generate the synthetic JSONL file in a test helper: generateSyntheticLaps(lapCount, lapDurationMs, startLat, startLng): TelemetrySample[]
- Simulate a circular track: each lap is a circle of 400m circumference
- Compute lat/lng offsets using haversine inverse
- Set speedMs to a realistic 20 m/s (72 km/h for karts) with ±2 m/s noise
"
```

### Phase 10 acceptance criteria
- All 4 Android instrumented tests pass on API 30 emulator
- All 3 backend integration test groups pass
- Test coverage report shows >70% line coverage on domain and data layers

---

## Delivery checklist — before handoff to QA

| # | Item | Owner |
|---|---|---|
| 1 | All Gradle lint warnings resolved | Android |
| 2 | ProGuard/R8 rules added for Retrofit, Room, Gson | Android |
| 3 | API base URL configurable via BuildConfig (not hardcoded) | Android |
| 4 | ANTHROPIC_API_KEY never committed to repo — use environment variable | Backend |
| 5 | Backend Dockerfile created for deployment | Backend |
| 6 | All TODO comments resolved or converted to GitHub issues | Both |
| 7 | README.md with setup, build, and run instructions | Both |
| 8 | Manual test: record a 5-minute kart session on a real device, verify laps and coaching | QA |

---

## Copilot CLI reference

| Task | Command |
|---|---|
| Suggest code | `gh copilot suggest "<prompt>"` |
| Explain existing code | `gh copilot explain "<code snippet or file>"` |
| Run with Opus | Add `--model claude-opus-4-5` flag |
| Run with Sonnet | Add `--model claude-sonnet-4-5` flag |

**Recommended model usage:**
- Prompts 1.1, 1.2, 4.1, 8.1 (architecture/scaffold): use Opus
- All other prompts (features, tests): use Sonnet
- Do not mix model flags in the same session — restart between model switches

---

*End of implementation plan. Total prompts: 14. Estimated phases: 10. Proceed to coding only after this plan is reviewed and approved.*
