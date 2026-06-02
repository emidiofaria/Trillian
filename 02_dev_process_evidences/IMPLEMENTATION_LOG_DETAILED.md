# BMW Driving Coach — Implementation Process Evidence

> **Document Type:** Developer Evidence Log  
> **Project:** BMW Driving Coach Android Application + Backend  
> **Implementation Period:** Multi-session development  
> **Final Documentation Date:** 2026-05-06

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Phase 1: Android Project Scaffold](#2-phase-1-android-project-scaffold)
3. [Phase 2: Room Database Layer](#3-phase-2-room-database-layer)
4. [Phase 3: Telemetry Service](#4-phase-3-telemetry-service)
5. [Phase 4: Backend Development](#5-phase-4-backend-development)
6. [Phase 5: Recording Screen UI](#6-phase-5-recording-screen-ui)
7. [Phase 6: Session Results Screen](#7-phase-6-session-results-screen)
8. [Phase 7: Home, Profile, Share](#8-phase-7-home-profile-share)
9. [Phase 8: API Client Layer](#9-phase-8-api-client-layer)
10. [Phase 9: Permissions & Error Handling](#10-phase-9-permissions-error-handling)
11. [Phase 10: Test Suite](#11-phase-10-test-suite)
12. [System Acceptance Tests](#12-system-acceptance-tests)
13. [Technical Decisions Log](#13-technical-decisions-log)
14. [Files Created Summary](#14-files-created-summary)

---

## 1. Project Overview

### 1.1 Project Description

BMW Driving Coach is a track day companion application that:
- Records GPS/IMU telemetry during track sessions
- Detects lap times using user-defined start/finish lines
- Provides AI-powered coaching feedback via Anthropic Claude API
- Generates shareable accomplishment cards

### 1.2 Technology Stack

| Component | Technology |
|-----------|------------|
| Android App | Kotlin, Min SDK 26, Target SDK 35 |
| DI Framework | Hilt 2.51 |
| Local Database | Room 2.6.1 |
| Network Client | Retrofit 2.11.0 + OkHttp 4.12.0 |
| Backend | Node.js 20, TypeScript 5.x, Express |
| Backend Database | PostgreSQL |
| AI Integration | Anthropic Claude API (claude-sonnet-4) |
| Cloud Platform | Microsoft Azure (App Service + Blob + PostgreSQL) |

### 1.3 Requirements Source

All implementation follows `01_requirements/BMW_DrivingCoach_SRS_v1.md`:
- **117 total requirements**
- **17 sections** covering all functional areas
- **10 architecture decisions** locked for V1

---

## 2. Phase 1: Android Project Scaffold

### 2.1 Objective
Bootstrap Android project with all dependencies, BMW-themed design system, navigation graph, and empty screen shells.

### 2.2 Implementation Steps

#### Step 1.1: Project Structure Creation
```
Created directory structure:
app/
├── src/main/java/com/bmw/drivingcoach/
│   ├── data/
│   ├── domain/
│   ├── ui/
│   ├── service/
│   └── di/
├── src/main/res/
├── src/test/
└── src/androidTest/
```

#### Step 1.2: Gradle Configuration

**build.gradle.kts (Project level)**
- Configured Gradle 8.7
- Added Kotlin 1.9.x plugin
- Added Hilt plugin (2.51)
- Added Navigation SafeArgs plugin

**app/build.gradle.kts**
Dependencies added:
```kotlin
// Core Android
implementation("androidx.core:core-ktx:1.13.1")
implementation("androidx.appcompat:appcompat:1.7.0")
implementation("com.google.android.material:material:1.12.0")
implementation("androidx.constraintlayout:constraintlayout:2.1.4")

// Navigation
implementation("androidx.navigation:navigation-fragment-ktx:2.8.0")
implementation("androidx.navigation:navigation-ui-ktx:2.8.0")

// Lifecycle
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")

// Hilt DI
implementation("com.google.dagger:hilt-android:2.51")
kapt("com.google.dagger:hilt-compiler:2.51")

// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// Network
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-gson:2.11.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.1.1")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

// Charts
implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
```

#### Step 1.3: AndroidManifest.xml

Permissions configured:
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

Services registered:
```xml
<service
    android:name=".service.TelemetryForegroundService"
    android:exported="false"
    android:foregroundServiceType="location" />
```

#### Step 1.4: Design System

**colors.xml**
```xml
<color name="colorPrimary">#1C69D4</color>        <!-- BMW Blue -->
<color name="colorPrimaryDark">#0A3D7C</color>
<color name="colorBackground">#0D0D0D</color>      <!-- Near-black luxury -->
<color name="colorSurface">#1A1A1A</color>
<color name="colorDeltaPositive">#2ECC71</color>   <!-- Faster = green -->
<color name="colorDeltaNegative">#E74C3C</color>   <!-- Slower = red -->
<color name="colorGold">#C9A227</color>            <!-- Best lap highlight -->
```

**type.xml - TextAppearances**
- `TextAppearance.BMW.LapTime`: 48sp, bold, monospace
- `TextAppearance.BMW.Delta`: 14sp, medium, monospace
- `TextAppearance.BMW.H1/H2/H3/Body/Caption`

**dimens.xml**
- Spacing: xs(4dp), sm(8dp), md(16dp), lg(24dp), xl(32dp)
- card_corner_radius: 12dp
- button_corner_radius: 8dp

#### Step 1.5: Navigation Graph

**nav_graph.xml destinations:**
1. `LoginFragment` (startDestination)
2. `RegisterFragment`
3. `HomeFragment`
4. `RecordingFragment`
5. `SessionResultFragment` (with sessionId: Long arg)
6. `LapDetailFragment` (with lapId: Long arg)
7. `ProfileFragment`
8. `OnboardingFragment` (added Phase 9)

### 2.3 Files Created
```
app/build.gradle.kts
build.gradle.kts
settings.gradle.kts
gradle.properties
app/src/main/AndroidManifest.xml
app/src/main/res/values/colors.xml
app/src/main/res/values/themes.xml
app/src/main/res/values/type.xml
app/src/main/res/values/dimens.xml
app/src/main/res/values/strings.xml
app/src/main/res/navigation/nav_graph.xml
app/src/main/java/.../BMWDrivingCoachApplication.kt
app/src/main/java/.../MainActivity.kt
```

### 2.4 Verification
```bash
./gradlew assembleDebug
# BUILD SUCCESSFUL
```

---

## 3. Phase 2: Room Database Layer

### 3.1 Objective
Create Room database with entities for sessions, laps, and coaching insights.

### 3.2 Implementation Steps

#### Step 2.1: Entity Definitions

**SessionEntity.kt**
```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firebaseUid: String,
    val trackName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val rawFilePath: String,
    val uploadStatus: String = "PENDING",
    val remoteSessionId: String? = null,
    val processingStatus: String = "PENDING",
    val startLineLat1: Double,
    val startLineLng1: Double,
    val startLineLat2: Double,
    val startLineLng2: Double
)
```

**LapEntity.kt**
```kotlin
@Entity(
    tableName = "laps",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class LapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val lapNumber: Int,
    val startTs: Long,
    val endTs: Long,
    val durationMs: Long,
    val sector1Ms: Long,
    val sector2Ms: Long,
    val sector3Ms: Long,
    val isBestLap: Boolean = false
)
```

**CoachingInsightEntity.kt**
```kotlin
@Entity(tableName = "coaching_insights", ...)
data class CoachingInsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val headline: String,
    val detail: String,
    val generatedAt: Long
)
```

#### Step 2.2: DAO Interfaces

**SessionDao.kt**
```kotlin
@Dao
interface SessionDao {
    @Insert suspend fun insert(session: SessionEntity): Long
    @Update suspend fun update(session: SessionEntity)
    @Query("SELECT * FROM sessions WHERE firebaseUid = :uid ORDER BY startedAt DESC")
    fun getSessionsForUser(uid: String): Flow<List<SessionEntity>>
    @Query("UPDATE sessions SET uploadStatus = :status WHERE id = :id")
    suspend fun updateUploadStatus(id: Long, status: String)
    @Query("UPDATE sessions SET processingStatus = :status WHERE id = :id")
    suspend fun updateProcessingStatus(id: Long, status: String)
    // ... more queries
}
```

#### Step 2.3: Database Configuration

**AppDatabase.kt**
```kotlin
@Database(
    entities = [SessionEntity::class, LapEntity::class, CoachingInsightEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun lapDao(): LapDao
    abstract fun coachingInsightDao(): CoachingInsightDao
}
```

**DatabaseModule.kt (Hilt)**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "bmw_driving_coach.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides fun provideSessionDao(db: AppDatabase) = db.sessionDao()
    @Provides fun provideLapDao(db: AppDatabase) = db.lapDao()
    @Provides fun provideCoachingInsightDao(db: AppDatabase) = db.coachingInsightDao()
}
```

### 3.3 Files Created
```
app/src/main/java/.../data/local/entity/SessionEntity.kt
app/src/main/java/.../data/local/entity/LapEntity.kt
app/src/main/java/.../data/local/entity/CoachingInsightEntity.kt
app/src/main/java/.../data/local/dao/SessionDao.kt
app/src/main/java/.../data/local/dao/LapDao.kt
app/src/main/java/.../data/local/dao/CoachingInsightDao.kt
app/src/main/java/.../data/local/AppDatabase.kt
app/src/main/java/.../di/DatabaseModule.kt
```

---

## 4. Phase 3: Telemetry Service

### 4.1 Objective
Implement foreground service for GPS/IMU capture and WorkManager for background upload.

### 4.2 Implementation Steps

#### Step 3.1: TelemetryForegroundService

Key implementation details:
```kotlin
class TelemetryForegroundService : Service() {
    private val GPS_SAMPLE_INTERVAL_MS = 100L  // 10 Hz target
    private val GPS_SIGNAL_LOST_TIMEOUT_MS = 10000L
    
    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    val serviceState: StateFlow<ServiceState> = _serviceState
    
    // AtomicReferences for latest IMU values
    private val latestAccel = AtomicReference<FloatArray>()
    private val latestGyro = AtomicReference<FloatArray>()
    
    // File write mutex
    private val writeMutex = Mutex()
}
```

GPS signal lost detection (added Phase 9):
```kotlin
private fun checkGpsSignalLost() {
    val elapsed = System.currentTimeMillis() - lastGpsTimestamp
    if (elapsed > GPS_SIGNAL_LOST_TIMEOUT_MS && !gpsSignalLost) {
        gpsSignalLost = true
        updateNotification("GPS signal lost — move to open sky")
    }
}
```

#### Step 3.2: TelemetryUploadWorker

```kotlin
class TelemetryUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong("sessionId", -1)
        // Update status to UPLOADING
        // POST multipart/form-data to backend
        // On success: Result.success()
        // On 5xx: Result.retry()
        // On 4xx: Result.failure()
    }
}
```

WorkRequest configuration:
```kotlin
val uploadRequest = OneTimeWorkRequestBuilder<TelemetryUploadWorker>()
    .setConstraints(Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
    .setInputData(workDataOf("sessionId" to sessionId))
    .build()

WorkManager.getInstance(context).enqueue(uploadRequest)
```

### 4.3 Files Created
```
app/src/main/java/.../service/TelemetryForegroundService.kt
app/src/main/java/.../service/TelemetrySample.kt
app/src/main/java/.../worker/TelemetryUploadWorker.kt
```

---

## 5. Phase 4: Backend Development

### 5.1 Objective
Build Node.js/TypeScript backend with auth, telemetry ingestion, lap detection, and AI coaching.

### 5.2 Implementation Steps

#### Step 4.1: Project Setup

```bash
cd backend
npm init -y
npm install express typescript @types/express @types/node
npm install pg bcryptjs jsonwebtoken multer
npm install @anthropic-ai/sdk
npm install --save-dev jest ts-jest @types/jest
```

**tsconfig.json**
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "commonjs",
    "outDir": "./dist",
    "rootDir": "./src",
    "strict": true,
    "esModuleInterop": true
  }
}
```

#### Step 4.2: Authentication Module

**authRouter.ts**
- POST `/auth/register`: bcrypt password, JWT generation
- POST `/auth/login`: credential verification, JWT
- GET `/auth/me`: token verification, user profile

**requireAuth.ts middleware**
```typescript
export const requireAuth = async (req, res, next) => {
    const token = req.headers.authorization?.replace('Bearer ', '');
    if (!token) return res.status(401).json({ error: 'Unauthorized' });
    
    try {
        const decoded = jwt.verify(token, process.env.JWT_SECRET!);
        req.user = decoded;
        next();
    } catch {
        res.status(401).json({ error: 'Unauthorized' });
    }
};
```

#### Step 4.3: Telemetry Module

**telemetryRouter.ts**
- POST `/telemetry/upload`: multipart file upload
- GET `/sessions`: list user sessions
- GET `/sessions/:id`: session detail with laps

**telemetryService.ts**
```typescript
export async function parseJsonlFile(filePath: string): Promise<TelemetrySample[]> {
    const content = await fs.readFile(filePath, 'utf8');
    return content.trim().split('\n').map(line => JSON.parse(line));
}
```

#### Step 4.4: Lap Detection Algorithm

**lapDetector.ts**
```typescript
export function detectLaps(samples: TelemetrySample[]): DetectedLap[] {
    // 1. Skip first 10 seconds (out-lap)
    // 2. Establish start zone from first 30 seconds
    // 3. Detect crossings using line-segment intersection
    // 4. Enforce 20-second minimum lap time
    // 5. Discard incomplete final lap
}

// Haversine distance calculation
export function haversineMetres(lat1, lng1, lat2, lng2): number {
    const R = 6371000; // Earth radius in metres
    // ... haversine formula
}

// Sector computation (equal time thirds)
export function computeSectors(lap: DetectedLap): Sectors {
    const third = Math.floor(lap.durationMs / 3);
    return {
        sector1Ms: third,
        sector2Ms: third,
        sector3Ms: lap.durationMs - 2 * third  // Absorbs rounding
    };
}
```

#### Step 4.5: AI Coaching Service

**coachingService.ts**
```typescript
export async function generateCoaching(sessionId: string): Promise<void> {
    // 1. Load session and laps from DB
    // 2. Compute session stats (mean, stddev, consistency)
    // 3. Build coaching prompt with sector data
    // 4. Call Anthropic API
    // 5. Parse JSON response
    // 6. Store insights in DB
}

const systemPrompt = `You are a professional motorsport driving coach...
Respond with exactly 4 coaching tips as JSON array:
[{"headline":"...","detail":"..."}]`;
```

#### Step 4.6: Database Schema

**schema.sql**
```sql
CREATE TABLE IF NOT EXISTS sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid VARCHAR(128) NOT NULL,
    track_name VARCHAR(255) NOT NULL,
    started_at BIGINT NOT NULL,
    ended_at BIGINT,
    blob_path VARCHAR(512),
    processing_status VARCHAR(50) DEFAULT 'PENDING',
    start_line_lat1 DOUBLE PRECISION,
    start_line_lng1 DOUBLE PRECISION,
    start_line_lat2 DOUBLE PRECISION,
    start_line_lng2 DOUBLE PRECISION,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS laps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES sessions(id) ON DELETE CASCADE,
    lap_number INTEGER NOT NULL,
    start_ts BIGINT NOT NULL,
    end_ts BIGINT NOT NULL,
    duration_ms BIGINT NOT NULL,
    sector_1_ms BIGINT,
    sector_2_ms BIGINT,
    sector_3_ms BIGINT,
    is_best_lap BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS coaching_insights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES sessions(id) ON DELETE CASCADE,
    headline VARCHAR(200) NOT NULL,
    detail TEXT NOT NULL,
    generated_at TIMESTAMP DEFAULT NOW()
);
```

### 5.3 Backend Unit Tests

**50 unit tests** across:
- `auth.test.ts`: 12 tests (register, login, token validation)
- `telemetry.test.ts`: 9 tests (upload, sessions, validation)
- `lapDetector.test.ts`: 14 tests (detection, sectors, edge cases)
- `coachingService.test.ts`: 15 tests (stats, prompts, parsing)

### 5.4 Files Created
```
backend/
├── src/
│   ├── app.ts
│   ├── server.ts
│   ├── auth/authRouter.ts
│   ├── telemetry/telemetryRouter.ts
│   ├── telemetry/telemetryService.ts
│   ├── lap/lapDetector.ts
│   ├── lap/lapProcessor.ts
│   ├── coaching/coachingService.ts
│   ├── middleware/requireAuth.ts
│   ├── middleware/errorHandler.ts
│   ├── db/index.ts
│   ├── db/schema.sql
│   └── config/index.ts
├── tests/
│   ├── auth.test.ts
│   ├── telemetry.test.ts
│   ├── lapDetector.test.ts
│   └── coachingService.test.ts
├── package.json
├── tsconfig.json
└── jest.config.js
```

---

## 6. Phase 5: Recording Screen UI

### 6.1 Objective
Build live recording screen with service binding, elapsed time display, GPS status, and stop button.

### 6.2 Implementation

**RecordingFragment.kt**
- Full-screen dark background
- Large elapsed time display (MM:SS.mmm)
- GPS status indicator (green/red)
- "TAP TO STOP RECORDING" button
- Permission handling via ActivityResultContracts

**RecordingViewModel.kt**
```kotlin
class RecordingViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState
    
    fun startRecording(sessionId: Long) { ... }
    fun stopRecording() { ... }
}

data class RecordingUiState(
    val elapsedMs: Long = 0,
    val gpsLocked: Boolean = false,
    val isRecording: Boolean = false
)
```

### 6.3 Files Created
```
app/src/main/java/.../ui/recording/RecordingFragment.kt
app/src/main/java/.../ui/recording/RecordingViewModel.kt
app/src/main/res/layout/fragment_recording.xml
```

---

## 7. Phase 6: Session Results Screen

### 7.1 Objective
Build tabbed results screen with Laps, Coach, and Chart tabs.

### 7.2 Implementation

**SessionResultFragment.kt**
- CollapsingToolbarLayout with track name
- TabLayout + ViewPager2 with 3 tabs
- Share icon in toolbar

**Tab Implementations:**

1. **LapsFragment**: RecyclerView of lap cards
   - Gold border on best lap
   - Delta badges (green/red)
   - Tap to navigate to LapDetailFragment

2. **CoachFragment**: ScrollView with cards
   - Consistency score summary
   - Coaching insight cards
   - Processing progress card

3. **ChartFragment**: MPAndroidChart
   - Speed trace for all laps
   - Best lap in BMW blue
   - Other laps in grey

**LapDetailFragment.kt**
- Side-by-side lap comparison
- Per-sector delta breakdown
- Performance bars

### 7.3 Files Created
```
app/src/main/java/.../ui/result/SessionResultFragment.kt
app/src/main/java/.../ui/result/SessionResultViewModel.kt
app/src/main/java/.../ui/result/LapsFragment.kt
app/src/main/java/.../ui/result/CoachFragment.kt
app/src/main/java/.../ui/result/ChartFragment.kt
app/src/main/java/.../ui/result/LapDetailFragment.kt
app/src/main/java/.../ui/result/LapDetailViewModel.kt
app/src/main/java/.../ui/result/adapter/LapAdapter.kt
app/src/main/res/layout/fragment_session_result.xml
app/src/main/res/layout/fragment_laps.xml
app/src/main/res/layout/fragment_coach.xml
app/src/main/res/layout/fragment_chart.xml
app/src/main/res/layout/fragment_lap_detail.xml
app/src/main/res/layout/item_lap_card.xml
app/src/main/res/layout/item_coaching_insight.xml
```

---

## 8. Phase 7: Home, Profile, Share

### 8.1 Objective
Build Home screen with session history, Profile screen with stats, and share card generation.

### 8.2 Implementation

**HomeFragment.kt**
- Hero card with best lap across all sessions
- RecyclerView of session history cards
- FAB to start new session
- Upload pending banner

**ProfileFragment.kt**
- Avatar with initials
- Display name, email
- Stats: total sessions, laps, best time
- Sign out button

**ShareCardGenerator.kt**
```kotlin
object ShareCardGenerator {
    fun generate(
        session: SessionEntity,
        bestLap: LapEntity,
        consistencyScore: Float,
        context: Context
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw dark background
        canvas.drawColor(0xFF0D0D0D.toInt())
        
        // Draw branding, lap time, track name, date, consistency
        // Draw BMW blue bottom border
        
        return bitmap
    }
}
```

### 8.3 Files Created
```
app/src/main/java/.../ui/home/HomeFragment.kt
app/src/main/java/.../ui/home/HomeViewModel.kt
app/src/main/java/.../ui/home/adapter/SessionAdapter.kt
app/src/main/java/.../ui/profile/ProfileFragment.kt
app/src/main/java/.../ui/profile/ProfileViewModel.kt
app/src/main/java/.../util/ShareCardGenerator.kt
app/src/main/java/.../util/LapTimeFormatter.kt
app/src/main/res/layout/fragment_home.xml
app/src/main/res/layout/fragment_profile.xml
app/src/main/res/layout/item_session_card.xml
app/src/main/res/xml/file_paths.xml  (FileProvider)
```

---

## 9. Phase 8: API Client Layer

### 9.1 Objective
Create Retrofit API client, DTOs, interceptors, and repositories.

### 9.2 Implementation

**ApiService.kt**
```kotlin
interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): Response<AuthResponse>
    
    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): Response<AuthResponse>
    
    @GET("auth/me")
    suspend fun getMe(): Response<UserDto>
    
    @Multipart
    @POST("telemetry/upload")
    suspend fun uploadTelemetry(
        @Part("sessionId") sessionId: RequestBody,
        @Part("trackName") trackName: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>
    
    @GET("sessions")
    suspend fun getSessions(): Response<List<SessionDto>>
    
    @GET("sessions/{id}")
    suspend fun getSession(@Path("id") id: String): Response<SessionDetailDto>
}
```

**AuthInterceptor.kt**
```kotlin
class AuthInterceptor(
    private val dataStore: DataStore<Preferences>
) : Interceptor {
    override fun intercept(chain: Chain): Response {
        val token = runBlocking { dataStore.data.first()[JWT_KEY] }
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        
        val response = chain.proceed(request)
        
        if (response.code == 401) {
            AuthEventBus.emitSessionExpired()
        }
        
        return response
    }
}
```

**AuthEventBus.kt**
```kotlin
object AuthEventBus {
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired
    
    fun emitSessionExpired() {
        _sessionExpired.tryEmit(Unit)
    }
}
```

### 9.3 Unit Tests

**ApiClientTest.kt** - 5 tests:
1. Login success stores token
2. Login failure does not store token
3. 401 response emits session expired
4. Auth interceptor attaches token
5. Upload sends correct multipart fields

### 9.4 Files Created
```
app/src/main/java/.../data/api/ApiService.kt
app/src/main/java/.../data/api/dto/ApiDtos.kt
app/src/main/java/.../data/api/AuthInterceptor.kt
app/src/main/java/.../data/api/AuthEventBus.kt
app/src/main/java/.../data/repository/AuthRepository.kt
app/src/main/java/.../data/repository/SessionRepository.kt
app/src/main/java/.../di/NetworkModule.kt
app/src/test/java/.../data/api/ApiClientTest.kt
```

---

## 10. Phase 9: Permissions & Error Handling

### 10.1 Objective
Add permission onboarding flow and robust error/background state handling.

### 10.2 Implementation

**OnboardingFragment.kt**
- ViewPager2 with 3 permission explanation pages
- Location, Activity Recognition, Storage explanations
- "GRANT PERMISSIONS & START" button
- "OPEN SETTINGS" for denied permissions
- DataStore persistence (`onboarding_complete`)

**Error Handling Additions:**

1. **GPS Timeout Handler** (TelemetryForegroundService)
   - 10-second timeout detection
   - Notification update on signal loss

2. **Processing Status Polling** (SessionResultViewModel)
   - 5-second polling interval
   - Step indicators (○/●/✓)
   - Retry button on failure

3. **Stale Upload Banner** (HomeFragment)
   - Shows after 5 minutes pending
   - Dismissible banner

4. **Session Expired Handler** (MainActivity)
   - Observes AuthEventBus
   - Shows Snackbar, navigates to Login

### 10.3 Files Created
```
app/src/main/java/.../ui/onboarding/OnboardingFragment.kt
app/src/main/res/layout/fragment_onboarding.xml
app/src/main/res/drawable/ic_location.xml
app/src/main/res/drawable/ic_activity.xml
app/src/main/res/drawable/ic_storage.xml
app/src/main/res/drawable/ic_check.xml
app/src/main/res/drawable/ic_close.xml
app/src/main/res/drawable/ic_cloud_upload.xml
```

### 10.4 Files Modified
```
nav_graph.xml - Added onboardingFragment
MainActivity.kt - Onboarding check, AuthEventBus observation
TelemetryForegroundService.kt - GPS timeout handler
SessionEntity.kt - Added ProcessingStatus enum
SessionDao.kt - Added updateProcessingStatus(), getStaleUploadSessions()
SessionResultViewModel.kt - Processing polling, retry logic
SessionResultFragment.kt - Processing card UI
HomeFragment.kt - Stale upload banner
```

---

## 11. Phase 10: Test Suite

### 11.1 Objective
Create comprehensive instrumented test suite for Android and integration tests for backend.

### 11.2 Android Instrumented Tests

**Test Configuration:**
- HiltTestRunner in build.gradle.kts
- TestDatabaseModule (in-memory Room)
- TestNetworkModule (MockWebServer)

**EndToEndTest.kt** - 4 tests:

1. **testFullSessionFlow**
   - Login → Home → FAB → Recording → Stop → SessionResult
   - Verify processing card visible

2. **testLapComparisonDeltaColors**
   - Seed 3 laps with different times
   - Verify red delta for slower lap
   - Verify green delta for faster lap

3. **testShareCard**
   - Navigate to SessionResult
   - Tap share icon
   - Assert ShareSheet intent via Espresso Intents

4. **testSignOut**
   - Login → Profile → Sign Out
   - Assert navigation to Login
   - Assert Room DB cleared

### 11.3 Backend Integration Tests

**testHelpers.ts**
```typescript
export function generateSyntheticLaps(
    lapCount: number,
    lapDurationMs: number,
    startLat: number,
    startLng: number
): TelemetrySample[] {
    // Generate circular track samples
    // 400m circumference, 20 m/s (72 km/h)
    // Include out-lap, realistic speed noise
}
```

**api.test.ts** - 3 test groups:

1. **Auth Flow**
   - Register, login, get me, unauthorized access

2. **Upload and Processing**
   - Upload synthetic JSONL
   - Poll for processing completion
   - Verify 5 laps detected
   - Verify coaching insights generated

3. **Edge Cases**
   - Single lap → FAILED
   - Empty file → 400 error

### 11.4 Coverage Results

**Backend (61 tests):**
```
All files             |   70.76% Statements
lapDetector.ts        |   94.50% Lines
errorHandler.ts       |  100.00% Lines
authRouter.ts         |   77.96% Lines
coachingService.ts    |   63.44% Lines
```

### 11.5 Files Created
```
app/src/androidTest/java/.../di/TestDatabaseModule.kt
app/src/androidTest/java/.../di/TestNetworkModule.kt
app/src/androidTest/java/.../EndToEndTest.kt
backend/tests/integration/testHelpers.ts
backend/tests/integration/api.test.ts
backend/tests/errorHandler.test.ts
backend/tests/lapProcessor.test.ts
```

---

## 12. System Acceptance Tests

### 12.1 Objective
Create comprehensive human tester documentation for track day validation.

### 12.2 Deliverables

Created `human_system_acceptance_tests/` directory with:

**Setup Documentation:**
- `01_ENVIRONMENT_SETUP.md` (34 checklist items)
- `02_BACKEND_DEPLOYMENT.md` (33 checklist items)
- `03_ANDROID_BUILD.md` (45 checklist items)

**Test Checklists (135 tests total):**
- `10_PRE_TRACK_DAY_TESTS.md` (10 tests)
- `20_ONBOARDING_TESTS.md` (14 tests)
- `30_TRACK_SETUP_TESTS.md` (16 tests)
- `40_SESSION_RECORDING_TESTS.md` (16 tests)
- `50_LAP_DETECTION_TESTS.md` (14 tests)
- `60_SESSION_RESULTS_TESTS.md` (17 tests)
- `70_AI_COACHING_TESTS.md` (14 tests)
- `80_SHARE_AND_PROFILE_TESTS.md` (16 tests)
- `90_ERROR_HANDLING_TESTS.md` (18 tests)

**Final Signoff:**
- `99_ACCEPTANCE_SIGNOFF.md` - Release approval checklist

### 12.3 Requirement Coverage
All 117 requirements from SRS mapped to specific test IDs.

---

## 13. Technical Decisions Log

### 13.1 Architecture Decisions (from SRS)

| ID | Decision | Rationale |
|----|----------|-----------|
| AD-01 | User-drawn start/finish line | Simpler than track database, works anywhere |
| AD-02 | Azure hosting | Enterprise-grade, BMW ecosystem compatible |
| AD-03 | JSONL telemetry format | Human-readable, easy to parse, streamable |
| AD-04 | Firebase Auth | Reliable, extensible to social login |
| AD-05 | Kotlin Android | Modern, concise, null-safe |
| AD-06 | Node.js/TypeScript backend | Fast development, good Anthropic SDK |
| AD-07 | Hilt DI | Official Android DI, compile-time safety |
| AD-08 | Room database | Official Android persistence, reactive |
| AD-09 | Retrofit/OkHttp | Industry standard, interceptor support |
| AD-10 | WorkManager | Reliable background work, constraints |

### 13.2 Implementation Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| GPS sample rate | 10 Hz (100ms) | Balance accuracy vs file size |
| Minimum lap time | 20 seconds | Prevent false crossings |
| Out-lap skip | 10 seconds | Avoid start-line false positives |
| Sector division | Equal time thirds | Simple, consistent |
| Coaching tips | Exactly 4 | Actionable, not overwhelming |
| Share card size | 1080×1080 | Social media optimal |
| Upload retry | Exponential backoff | Network reliability |
| Processing poll | 5 seconds | Responsive without overloading |

### 13.3 Issues Resolved

| Issue | Resolution |
|-------|------------|
| Kapt doesn't support Kotlin 2.0+ | Warning ignored, falls back to 1.9 |
| Coroutine test scope in runTest | Use `this.launch` instead of `kotlinx.coroutines.launch` |
| MPAndroidChart dependency | Added JitPack repository |
| Room schema location warning | Non-blocking, schema export disabled |
| View ID mismatches in tests | Corrected to actual layout IDs |

---

## 14. Files Created Summary

### 14.1 Android Application

**Total Kotlin files:** ~50
**Total XML resources:** ~30

**Key directories:**
```
app/src/main/java/com/bmw/drivingcoach/
├── data/
│   ├── api/ (ApiService, DTOs, Interceptor, EventBus)
│   ├── local/ (Room entities, DAOs, Database)
│   └── repository/ (AuthRepository, SessionRepository)
├── di/ (DatabaseModule, NetworkModule)
├── service/ (TelemetryForegroundService)
├── ui/
│   ├── auth/ (LoginFragment, RegisterFragment, ViewModels)
│   ├── home/ (HomeFragment, ViewModel, Adapter)
│   ├── onboarding/ (OnboardingFragment)
│   ├── profile/ (ProfileFragment, ViewModel)
│   ├── recording/ (RecordingFragment, ViewModel)
│   └── result/ (SessionResult, Laps, Coach, Chart, LapDetail)
├── util/ (ShareCardGenerator, LapTimeFormatter)
└── worker/ (TelemetryUploadWorker)
```

### 14.2 Backend

**Total TypeScript files:** ~15
**Total test files:** ~8

```
backend/
├── src/
│   ├── auth/
│   ├── telemetry/
│   ├── lap/
│   ├── coaching/
│   ├── middleware/
│   ├── db/
│   └── config/
└── tests/
    ├── unit tests (5 files)
    └── integration/ (2 files)
```

### 14.3 Documentation

```
human_system_acceptance_tests/
├── 01_ENVIRONMENT_SETUP.md
├── 02_BACKEND_DEPLOYMENT.md
├── 03_ANDROID_BUILD.md
├── 10-90_TEST_CHECKLISTS.md (9 files)
├── 99_ACCEPTANCE_SIGNOFF.md
└── README.md
```

---

## Appendix A: Build Verification Commands

```bash
# Android build
./gradlew clean assembleDebug
./gradlew testDebugUnitTest
./gradlew assembleDebugAndroidTest

# Backend build
cd backend
npm install
npm run build
npm run test:unit

# Coverage
npm run test:unit  # Shows coverage table
```

---

## Appendix B: Commit History Summary

| Commit | Description | Files |
|--------|-------------|-------|
| Phase 1-7 | Initial scaffold through Share | ~60 files |
| Phase 8 | API client and repository layer | 8 files |
| Phase 9.1 | Permission onboarding flow | 8 files |
| Phase 9.2 | Error and background handling | 12 files modified |
| Phase 10.1 | Android instrumented tests | 4 files |
| Phase 10.2 | Backend integration tests | 2 files |
| Test coverage | Additional unit tests | 2 files |
| System tests | Human acceptance test docs | 14 files |

---

*Document ID: DEV-EVIDENCE-001 | Version: 1.0 | Date: 2026-05-06*
