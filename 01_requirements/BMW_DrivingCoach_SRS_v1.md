# BMW Driving Coach — System Requirements Specification
**Document ID:** BMW-DC-SRS-001  
**Version:** 1.0  
**Status:** Approved — Ready for coding  
**Platform:** Android (phone-only)  
**Design principle:** All analysis is post-session. No real-time coaching.

---

## Table of contents

1. [Architecture decisions](#1-architecture-decisions)
2. [User management](#2-user-management)
3. [Onboarding and permissions](#3-onboarding-and-permissions)
4. [Track setup — start/finish line](#4-track-setup--startfinish-line)
5. [Session recording](#5-session-recording)
6. [Telemetry capture](#6-telemetry-capture)
7. [Telemetry storage and upload](#7-telemetry-storage-and-upload)
8. [Lap detection](#8-lap-detection)
9. [Lap comparison](#9-lap-comparison)
10. [AI coaching feedback](#10-ai-coaching-feedback)
11. [Driver progression tracking](#11-driver-progression-tracking)
12. [Share accomplishments](#12-share-accomplishments)
13. [Backend API](#13-backend-api)
14. [Data model](#14-data-model)
15. [Non-functional requirements](#15-non-functional-requirements)
16. [Security requirements](#16-security-requirements)
17. [Out of scope for V1](#17-out-of-scope-for-v1)

---

## 1. Architecture decisions

These decisions are locked. All requirements and implementation prompts reflect them.

| ID | Decision | Choice |
|---|---|---|
| AD-01 | Lap detection method | User draws start/finish line on a map before recording |
| AD-02 | Backend hosting | Microsoft Azure — App Service + Blob Storage + PostgreSQL Flexible Server |
| AD-03 | Telemetry file format | JSONL — one JSON object per line, UTF-8 encoded |
| AD-04 | Authentication provider | Firebase Authentication — email/password for V1, extensible to Google/Apple in V2 |
| AD-05 | Android language | Kotlin, min SDK 26 (Android 8.0), target SDK 35 |
| AD-06 | Backend language | Node.js 20, TypeScript 5.x, Express |
| AD-07 | Dependency injection | Hilt (Android) |
| AD-08 | Local database | Room (Android) backed by SQLite |
| AD-09 | Network client | Retrofit 2 + OkHttp 4 (Android) |
| AD-10 | Background upload | WorkManager with exponential backoff |

---

## 2. User management

### 2.1 Registration

| ID | Requirement |
|---|---|
| UM-01 | The app shall allow a new user to register with an email address, password, and display name. |
| UM-02 | Registration shall be handled entirely by Firebase Authentication SDK on the Android client; the backend shall not expose a `/register` endpoint. |
| UM-03 | Password minimum length shall be 8 characters, enforced client-side before submitting to Firebase. |
| UM-04 | The display name shall be a minimum of 2 characters and a maximum of 100 characters. |
| UM-05 | On successful registration, the user shall be automatically signed in and directed to the Home screen with the back stack cleared. |
| UM-06 | On registration failure (e.g. email already in use), the app shall display the Firebase error message as a Snackbar. |

### 2.2 Login

| ID | Requirement |
|---|---|
| UM-07 | The app shall allow an existing user to sign in with email and password via Firebase Authentication. |
| UM-08 | On successful login, the user shall be directed to the Home screen with the back stack cleared. |
| UM-09 | On login failure, the app shall display a Snackbar with a clear error message; the password field shall not be cleared. |
| UM-10 | While a login or registration request is in flight, the submit button shall be disabled and a circular progress indicator shall be visible. |
| UM-11 | The email field shall use `inputType=textEmailAddress`. The password field shall use `inputType=textPassword` with a visibility toggle icon. |

### 2.3 Session management

| ID | Requirement |
|---|---|
| UM-12 | Firebase ID tokens shall be attached automatically to every backend API request via an OkHttp interceptor (`FirebaseAuthInterceptor`). |
| UM-13 | The Firebase SDK shall handle token refresh transparently; the app shall not implement custom token refresh logic. |
| UM-14 | If the backend returns HTTP 401, the app shall emit a `sessionExpired` event, display a Snackbar 'Session expired — please sign in again', and navigate to the Login screen, clearing the back stack. |
| UM-15 | The backend shall verify all incoming Firebase ID tokens using the Firebase Admin SDK (`admin.auth().verifyIdToken()`). Requests with a missing, invalid, or expired token shall receive HTTP 401. |

### 2.4 Sign out

| ID | Requirement |
|---|---|
| UM-16 | The app shall allow the user to sign out from the Profile screen. |
| UM-17 | On sign out, the app shall call `FirebaseAuth.signOut()`, delete all local Room DB records belonging to the current Firebase UID, and navigate to the Login screen. |

### 2.5 User profile

| ID | Requirement |
|---|---|
| UM-18 | The Profile screen shall display the user's display name, email address, total session count, total lap count, and overall best lap time. |
| UM-19 | The user avatar shall be a circle displaying the first two letters of the display name on a BMW blue background. |

---

## 3. Onboarding and permissions

| ID | Requirement |
|---|---|
| ON-01 | On first launch, the app shall display an onboarding screen before any other screen. |
| ON-02 | Onboarding shall consist of three information pages presented in a ViewPager2: Location tracking, Motion analysis, Data privacy. |
| ON-03 | The onboarding screen shall have a single 'GRANT PERMISSIONS & START' button that requests the following permissions: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACTIVITY_RECOGNITION`. |
| ON-04 | If all permissions are granted, the app shall mark onboarding as complete in DataStore and navigate to the Login screen. |
| ON-05 | If any permission is denied, the app shall display a dialog listing the denied permissions and offer an 'OPEN SETTINGS' button that deeplinks to the app's system settings. |
| ON-06 | If the user taps 'SKIP' after a denial, the app shall still mark onboarding as complete and navigate to Login. The missing permission will be re-requested when recording starts. |
| ON-07 | Onboarding shall only be shown once. On all subsequent launches, the app shall skip directly to Login (or Home if already signed in). |
| ON-08 | Onboarding completion state shall be persisted in `DataStore<Preferences>` with key `onboarding_complete`. |

---

## 4. Track setup — start/finish line

| ID | Requirement |
|---|---|
| TS-01 | Before starting a recording, the user shall define a start/finish line on a Google Map by tapping two points. |
| TS-02 | The Track Setup screen shall display a Google Map with a dark (Aubergine) style applied via a JSON style resource. |
| TS-03 | The first tap on the map shall place point A (a BMW blue marker). |
| TS-04 | The second tap on the map shall place point B (a second marker) and draw a BMW blue polyline between point A and point B representing the start/finish line. |
| TS-05 | If both points are already placed, further taps on the map shall do nothing. A Snackbar shall inform the user to tap CLEAR to redraw. |
| TS-06 | A 'USE MY LOCATION' button shall set point A to the device's last known GPS location and place a marker at that position. |
| TS-07 | A 'CLEAR' button in the toolbar shall remove both markers and the polyline and reset the state to the initial tap-point-A state. |
| TS-08 | The line shall be validated on 'START RECORDING': the distance between point A and point B shall be between 2 m and 200 m inclusive. |
| TS-09 | If the distance is less than 2 m, the app shall display an error: 'Points are too close'. |
| TS-10 | If the distance is greater than 200 m, the app shall display an error: 'Line is too long — place points closer to the start/finish'. |
| TS-11 | The 'START RECORDING' button shall remain disabled until both points are placed and the line passes validation. |
| TS-12 | On confirmation, the two lat/lng pairs (startLineLat1, startLineLng1, startLineLat2, startLineLng2) shall be stored in the `SessionEntity` in Room and passed as navigation arguments to the Recording screen. |
| TS-13 | The instruction card on the Track Setup screen shall read: 'Tap two points on the map to draw the start/finish line. Stand at the line when placing points for best accuracy.' |

---

## 5. Session recording

| ID | Requirement |
|---|---|
| SR-01 | The app shall provide a 'START RECORDING' button on the Track Setup screen that begins a telemetry capture session. |
| SR-02 | The app shall provide a 'STOP RECORDING' button on the Recording screen that ends the session and triggers upload. |
| SR-03 | On 'STOP RECORDING', the app shall set `SessionEntity.endedAt` to the current epoch ms and enqueue the upload worker. |
| SR-04 | The Recording screen shall display the elapsed session time in `MM:SS.mmm` format, updating every second. |
| SR-05 | The Recording screen shall display a GPS status indicator: a green dot labelled 'GPS LOCKED' when a fix is acquired, or a red dot labelled 'ACQUIRING GPS...' while awaiting a fix. |
| SR-06 | The Recording screen shall display a blinking 'REC' badge (red pill) using an `ObjectAnimator` alpha animation (1 → 0 → 1, 1 s repeat). |
| SR-07 | The device screen shall remain on during an active recording session using `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`. The flag shall be cleared when the screen is paused. |
| SR-08 | If `ACCESS_FINE_LOCATION` permission is not granted when recording attempts to start, the service shall stop itself and the app shall show: 'Location permission is required to record'. |
| SR-09 | Recording shall continue when the app is sent to the background via a foreground service with `foregroundServiceType=location`. |
| SR-10 | The foreground service notification shall display: title 'BMW Driving Coach', content text 'Recording — MM:SS', updating every second. |
| SR-11 | If GPS hardware is not available on the device, the foreground service shall stop itself and post a state indicating GPS is unavailable. |

---

## 6. Telemetry capture

| ID | Requirement |
|---|---|
| TC-01 | GPS samples shall be collected using `LocationManager` with provider `GPS_PROVIDER`, `minTimeMs=100` (targeting 10 Hz), `minDistanceM=0`. |
| TC-02 | Each GPS sample shall include: `timestampMs` (epoch ms), `latitude` (Double), `longitude` (Double), `speedMs` (m/s, Float), `headingDeg` (0–360°, Float), `gpsAccuracyM` (horizontal accuracy in metres, Float). |
| TC-03 | IMU samples shall be collected by registering a `SensorEventListener` for `TYPE_ACCELEROMETER` and `TYPE_GYROSCOPE` at `SENSOR_DELAY_FASTEST`. |
| TC-04 | Each IMU sample shall include: `accelX`, `accelY`, `accelZ` (m/s², Float) and `gyroX`, `gyroY`, `gyroZ` (rad/s, Float). |
| TC-05 | IMU values shall not be written to file on every sensor event. The latest accelerometer and gyroscope values shall be stored in `AtomicReference<FloatArray>` and merged into the next GPS sample at write time. |
| TC-06 | All telemetry samples shall be written to a local JSONL file — one JSON object per line — at `context.filesDir/telemetry/session_{id}.jsonl`. |
| TC-07 | File writes shall be protected by a `kotlinx.coroutines.sync.Mutex` to prevent concurrent write corruption. |
| TC-08 | File writes shall run on `Dispatchers.IO`. The capture service shall not block its main thread on I/O. |
| TC-09 | If a GPS fix is not received for 10 continuous seconds during recording, the foreground notification shall update to: 'GPS signal lost — move to open sky'. |
| TC-10 | When GPS signal is restored after a loss, the notification shall revert to normal recording text and `gpsLocked` shall be set to `true`. |
| TC-11 | The app shall warn the user on the Recording screen (via GPS status indicator) if GPS accuracy drops below 10 m. |
| TC-12 | Exceptions thrown in `SensorEventListener.onSensorChanged` or `LocationListener.onLocationChanged` shall be caught and logged; they shall never propagate and crash the service. |

---

## 7. Telemetry storage and upload

| ID | Requirement |
|---|---|
| TU-01 | Telemetry data shall be written to a local JSONL file during capture; the app shall not depend on a live network connection during recording. |
| TU-02 | On session stop, the app shall enqueue a `TelemetryUploadWorker` (WorkManager `OneTimeWorkRequest`) with constraint `NetworkType.CONNECTED`. |
| TU-03 | The upload worker shall use exponential backoff, with `initialDelay=10s` and a maximum of 5 attempts. |
| TU-04 | The upload worker shall POST the JSONL file to the backend as a `multipart/form-data` request, including: `trackName`, `startedAt`, `endedAt`, `startLineLat1`, `startLineLng1`, `startLineLat2`, `startLineLng2`, and the JSONL `file`. |
| TU-05 | `SessionEntity.uploadStatus` shall be set to `UPLOADING` before the upload attempt begins. |
| TU-06 | On HTTP 200–201, `uploadStatus` shall be set to `DONE` and `remoteSessionId` stored. The worker shall return `Result.success()`. |
| TU-07 | On HTTP 5xx, `uploadStatus` shall be set to `FAILED` and the worker shall return `Result.retry()`. |
| TU-08 | On HTTP 4xx, `uploadStatus` shall be set to `FAILED` and the worker shall return `Result.failure()` (no retry). |
| TU-09 | If the local JSONL file does not exist when the worker runs, the worker shall return `Result.failure()`. |
| TU-10 | The backend shall store the JSONL file in Azure Blob Storage under the path `{firebase_uid}/{sessionId}.jsonl` in the `telemetry` container. |
| TU-11 | The backend shall accept JSONL files up to 50 MB. |
| TU-12 | If a session has `uploadStatus=PENDING` for more than 5 minutes, the Home screen shall show a dismissible banner: 'Session upload pending — connect to Wi-Fi'. |
| TU-13 | If `uploadStatus=FAILED`, the Session Result screen shall offer a 'RETRY ANALYSIS' button that re-enqueues the upload worker. |

---

## 8. Lap detection

| ID | Requirement |
|---|---|
| LD-01 | Lap detection shall be performed server-side, asynchronously, after the JSONL file is successfully stored in Azure Blob Storage. |
| LD-02 | Lap boundaries shall be detected using a segment-intersection algorithm against the user-defined start/finish line (two lat/lng points). |
| LD-03 | The algorithm shall skip the first 10 seconds of telemetry samples to avoid false crossings at session start. |
| LD-04 | A lap boundary shall be recorded when consecutive GPS samples form a line segment that intersects the start/finish line segment, using the cross-product sign-of-area method. |
| LD-05 | A minimum lap time guard of 20,000 ms shall be enforced. Any crossing detected less than 20 s after the previous boundary shall be ignored. |
| LD-06 | The last incomplete lap (started after the final boundary, session ends before re-crossing) shall be discarded. Only complete laps shall be stored. |
| LD-07 | If fewer than 50 telemetry samples are present in the file, the backend shall set `processingStatus=FAILED` and log: 'Insufficient samples'. |
| LD-08 | If fewer than 2 complete laps are detected, the backend shall set `processingStatus=FAILED`, store no laps, and log a warning. The user shall be notified in the app. |
| LD-09 | Each detected lap shall be divided into 3 equal-time sectors. `sector1Ms + sector2Ms + sector3Ms` shall equal `durationMs` exactly (sector 3 absorbs rounding). |
| LD-10 | The lap with the minimum `durationMs` shall be flagged as `isBestLap=true`. Exactly one lap per session shall have this flag set. |
| LD-11 | `SessionEntity.processingStatus` shall progress through: `PENDING → PROCESSING → LAPS_DONE → COMPLETE` on success, or `FAILED` on any error. |
| LD-12 | The lat/lng approximation used for segment intersection (Cartesian) is valid for tracks smaller than 5 km in extent. This is the supported use case. |

---

## 9. Lap comparison

| ID | Requirement |
|---|---|
| LC-01 | The Session Result screen shall display a 'LAPS' tab listing all laps for the session, sorted by lap time ascending. |
| LC-02 | Each lap card shall show: lap number, lap time formatted as `M:SS.mmm`, sector 1 / sector 2 / sector 3 times, and a delta badge vs the best lap. |
| LC-03 | The best lap card shall have a gold (#C9A227) left-border accent (3 dp) and a 'BEST' chip. |
| LC-04 | The delta badge shall be colour-coded: green (`#2ECC71`) if the lap is faster than the best lap (delta < 0), red (`#E74C3C`) if slower (delta > 0). The best lap row shall not show a delta badge. |
| LC-05 | Delta shall be formatted as `+0.456s` or `-0.123s`, always 3 decimal places. |
| LC-06 | Tapping a lap card shall navigate to the Lap Detail screen for that lap. |
| LC-07 | The Lap Detail screen shall display the selected lap and the best lap side by side: lap times in large `LapTime` style, total delta (coloured), and per-sector breakdown. |
| LC-08 | Each sector comparison on the Lap Detail screen shall show: sector label, selected time (monospace), delta badge (coloured pill), best time (monospace), and a horizontal relative-performance bar. |
| LC-09 | The Session Result screen shall display a 'CHART' tab with a speed trace (km/h vs lap progress) for all laps. All laps shall be plotted in grey (#444444), the best lap in BMW blue (#1C69D4). |
| LC-10 | Speed values in the chart shall be converted from m/s to km/h using the factor 3.6. |

---

## 10. AI coaching feedback

| ID | Requirement |
|---|---|
| AI-01 | After lap processing completes (`processingStatus=LAPS_DONE`), the backend shall trigger an asynchronous AI coaching job. |
| AI-02 | The coaching job shall call the Anthropic API using the model `claude-sonnet-4-20250514` with `max_tokens=700`. |
| AI-03 | The coaching prompt shall include: track name, total lap count, best lap time, average lap time, consistency score, and a sector-by-sector delta table comparing all laps to the best lap. |
| AI-04 | The system prompt shall instruct the model to act as a professional motorsport driving coach and to always reference specific lap numbers, sector numbers, and time values. |
| AI-05 | The model shall be instructed to respond with **exactly 4 coaching tips** in a valid JSON array and no other text: `[{"headline":"...","detail":"..."}]`. |
| AI-06 | Each coaching tip headline shall be a maximum of 8 words. Each detail shall be a maximum of 35 words. |
| AI-07 | The backend shall strip markdown code fences from the response before JSON parsing. |
| AI-08 | The backend shall validate the parsed response: must be an array of 3–5 items, each with `headline` (string, ≤ 100 chars) and `detail` (string, ≤ 300 chars). |
| AI-09 | Validated coaching tips shall be inserted into the `coaching_insights` table linked to the session. |
| AI-10 | If the Anthropic API call fails, or the response cannot be parsed or validated, the error shall be logged and coaching insights shall not be stored. The failure shall NOT set `processingStatus=FAILED`; the session shall still complete. |
| AI-11 | The `COACH` tab on the Session Result screen shall display a consistency score summary card and one card per coaching insight (headline in H3 style, detail in Body style). |
| AI-12 | While `processingStatus` is not `COMPLETE`, the `COACH` tab shall show a progress card with three labelled steps: 'Uploading', 'Detecting laps', 'Generating coaching'. |
| AI-13 | The consistency score formula is: `(1 − σ / μ) × 100`, where `σ` is the standard deviation of lap times and `μ` is the mean lap time. The result is clamped to [0, 100] and expressed as a percentage to 1 decimal place. |
| AI-14 | If `processingStatus=FAILED`, the `COACH` tab shall display a 'RETRY ANALYSIS' button. |

---

## 11. Driver progression tracking

| ID | Requirement |
|---|---|
| DP-01 | The Home screen shall display a history list of all sessions for the signed-in user, ordered by `startedAt` descending. |
| DP-02 | Each session history card shall show: track name, date formatted as `dd MMM yyyy HH:mm`, best lap time for that session, lap count, consistency score, and an upload status chip (`UPLOADED` / `PENDING` / `FAILED`). |
| DP-03 | The Home screen shall display a hero card showing the user's overall best lap time across all sessions, the track it was set on, and the date. |
| DP-04 | If the user has no sessions, the hero card shall display: 'Record your first session to see your best lap'. |
| DP-05 | The Session Result screen shall display the consistency score as a large percentage in the `COACH` tab summary card, with the subtitle 'across {N} laps'. |
| DP-06 | Sessions shall be fetched from the local Room database as the single source of truth. Remote data from the API shall be written back to Room and consumed from there. |

---

## 12. Share accomplishments

| ID | Requirement |
|---|---|
| SH-01 | The Session Result screen shall include a share icon in the toolbar. |
| SH-02 | Tapping the share icon shall generate a 1080×1080 px share card as an Android `Bitmap`. |
| SH-03 | The share card shall contain: 'BMW DRIVING COACH' branding text, best lap time (large, BMW blue, monospace), 'BEST LAP' label, track name, session date, consistency score, and a BMW blue bottom border line. |
| SH-04 | The bitmap shall be saved to the app's FileProvider cache directory and shared via `Intent.ACTION_SEND` with MIME type `image/png` through the Android Share Sheet. |
| SH-05 | The FileProvider authority shall be `${applicationId}.fileprovider`. |
| SH-06 | Share is a V1 placeholder for future social features. No social backend infrastructure is required in V1. |

---

## 13. Backend API

### 13.1 Authentication middleware

| ID | Requirement |
|---|---|
| BE-01 | All backend routes except health check shall require a valid Firebase ID token in the `Authorization: Bearer <token>` header. |
| BE-02 | On valid token, the middleware shall attach `req.user = { uid, email }` and call `next()`. |
| BE-03 | On missing, expired, or invalid token, the middleware shall return HTTP 401 `{ error: 'Unauthorized' }`. |

### 13.2 Telemetry upload

| ID | Requirement |
|---|---|
| BE-04 | `POST /telemetry/upload` shall accept `multipart/form-data` with fields: `trackName`, `startedAt`, `endedAt`, `startLineLat1`, `startLineLng1`, `startLineLat2`, `startLineLng2`, and `file` (JSONL). |
| BE-05 | All fields shall be validated with Zod. Missing or unparseable fields shall return HTTP 400 with a structured error body. |
| BE-06 | The JSONL file shall be uploaded to Azure Blob Storage at path `{firebase_uid}/{sessionId}.jsonl` before the database record is created. |
| BE-07 | A session row shall be inserted into PostgreSQL with `processing_status='PENDING'`. |
| BE-08 | Lap processing shall be triggered as a fire-and-forget async function — the HTTP response shall be returned immediately. |
| BE-09 | `POST /telemetry/upload` shall return HTTP 201 `{ sessionId, status: 'PROCESSING' }`. |

### 13.3 Session queries

| ID | Requirement |
|---|---|
| BE-10 | `GET /sessions` shall return all sessions for `req.user.uid`, ordered by `started_at DESC`. Each record shall include `bestLapMs` and `lapCount`. |
| BE-11 | `GET /sessions/:sessionId` shall return the session, its laps array, and its coaching insights array. |
| BE-12 | `GET /sessions/:sessionId` shall return HTTP 403 if `session.firebase_uid` does not match `req.user.uid`. |

### 13.4 Processing pipeline

| ID | Requirement |
|---|---|
| BE-13 | The processing pipeline shall run in this order: parse JSONL → detect laps → compute sectors → insert laps → generate coaching insights → set `processingStatus=COMPLETE`. |
| BE-14 | Any unhandled exception in the pipeline shall set `processingStatus=FAILED` and log the error with the `sessionId`. |
| BE-15 | The backend shall support concurrent session processing without one session's failure affecting another. |

---

## 14. Data model

### 14.1 Android Room entities

| Entity | Fields |
|---|---|
| `SessionEntity` | `id` (Long, PK), `firebaseUid` (String), `trackName` (String), `startedAt` (Long), `endedAt` (Long?), `rawFilePath` (String), `uploadStatus` (String: PENDING/UPLOADING/DONE/FAILED), `remoteSessionId` (String?), `processingStatus` (String: PENDING/PROCESSING/LAPS_DONE/COMPLETE/FAILED), `startLineLat1` (Double), `startLineLng1` (Double), `startLineLat2` (Double), `startLineLng2` (Double) |
| `LapEntity` | `id` (Long, PK), `sessionId` (Long, FK→Session CASCADE), `lapNumber` (Int), `startTs` (Long), `endTs` (Long), `durationMs` (Long), `sector1Ms` (Long), `sector2Ms` (Long), `sector3Ms` (Long), `isBestLap` (Boolean) |
| `CoachingInsightEntity` | `id` (Long, PK), `sessionId` (Long, FK→Session CASCADE), `headline` (String), `detail` (String), `generatedAt` (Long) |

### 14.2 Telemetry sample (JSONL, not stored in Room)

| Field | Type | Description |
|---|---|---|
| `timestampMs` | Long | Epoch ms |
| `latitude` | Double | WGS-84 latitude |
| `longitude` | Double | WGS-84 longitude |
| `speedMs` | Float | Speed in m/s |
| `headingDeg` | Float | Heading 0–360° |
| `accelX/Y/Z` | Float | Accelerometer m/s² |
| `gyroX/Y/Z` | Float | Gyroscope rad/s |
| `gpsAccuracyM` | Float | Horizontal GPS accuracy in metres |

### 14.3 Backend PostgreSQL tables

| Table | Key columns |
|---|---|
| `sessions` | `id` (UUID PK), `firebase_uid` (VARCHAR 128), `track_name`, `started_at`, `ended_at`, `blob_path`, `processing_status`, `start_line_lat1/lng1/lat2/lng2` (DOUBLE PRECISION), `created_at` |
| `laps` | `id` (UUID PK), `session_id` (UUID FK→sessions CASCADE), `lap_number`, `start_ts`, `end_ts`, `duration_ms`, `sector_1_ms`, `sector_2_ms`, `sector_3_ms`, `is_best_lap` |
| `coaching_insights` | `id` (UUID PK), `session_id` (UUID FK→sessions CASCADE), `headline` (VARCHAR 200), `detail` (TEXT), `generated_at` |

---

## 15. Non-functional requirements

| ID | Requirement |
|---|---|
| NF-01 | The app shall target Android API 26 (Android 8.0) as minimum and API 35 as target. |
| NF-02 | Minimum supported screen size: 5-inch display, 1080×1920 px. |
| NF-03 | The app shall not crash during recording if the network is unavailable. |
| NF-04 | Telemetry JSONL upload for a 30-minute session shall complete within 30 seconds on a 4G connection. |
| NF-05 | Lap detection and AI coaching generation shall complete within 60 seconds of upload for sessions with 20 laps or fewer. |
| NF-06 | Battery consumption during a 30-minute recording session shall not exceed 15% on a mid-range device (Pixel 5 equivalent). |
| NF-07 | The JSONL file writer shall handle 18,000 samples (simulated 30-minute session at 10 Hz) without data loss or file corruption. |
| NF-08 | Room database queries shall respond within 100 ms for session lists of up to 100 sessions. |
| NF-09 | The app shall display a loading state for any operation expected to take longer than 300 ms. |
| NF-10 | The backend shall process requests from multiple concurrent users without session data cross-contamination. |
| NF-11 | Unit test line coverage shall be ≥ 70% across domain and data layers. |
| NF-12 | `./gradlew assembleRelease` shall succeed with R8/ProGuard enabled. |
| NF-13 | The backend Docker image shall build and start within 60 seconds. |

---

## 16. Security requirements

| ID | Requirement |
|---|---|
| SEC-01 | All network traffic shall use HTTPS / TLS 1.2 or higher. |
| SEC-02 | Firebase ID tokens shall be verified server-side on every request; they shall never be trusted client-side only. |
| SEC-03 | The `ANTHROPIC_API_KEY` shall only be stored in Azure App Service Application Settings and shall never be committed to source control. |
| SEC-04 | The Firebase service account JSON shall be base64-encoded and stored in Azure App Settings; it shall never be committed to source control. |
| SEC-05 | `google-services.json` shall never be committed to source control; this shall be documented in `README.md`. |
| SEC-06 | The Azure Blob Storage container `telemetry` shall have private access level; blobs shall not be publicly accessible. |
| SEC-07 | The backend shall enforce that a user can only read their own sessions; `GET /sessions/:id` shall return HTTP 403 if the session belongs to a different `firebase_uid`. |
| SEC-08 | The API base URL shall be configured via `BuildConfig.API_BASE_URL` per build flavour; it shall not be hardcoded. |
| SEC-09 | ProGuard/R8 rules shall be added for Retrofit, Room, Gson, and Firebase to prevent stripping of required classes in the release build. |

---

## 17. Out of scope for V1

| ID | Item |
|---|---|
| OOS-01 | Real-time (in-session) coaching or audio feedback |
| OOS-02 | External OBD / CAN bus sensor integration |
| OOS-03 | Social feed, follows, leaderboards, or social network backend |
| OOS-04 | iOS application |
| OOS-05 | Track map library or pre-loaded track database |
| OOS-06 | Video overlay or external camera synchronisation |
| OOS-07 | Google / Apple sign-in (Firebase infrastructure is ready for V2 addition) |
| OOS-08 | In-app purchase or subscription management |
| OOS-09 | Driving Academy / AI learning curriculum (placeholder only in UI) |

---

*End of document. Requirements count: 117. All IDs are unique and stable for traceability.*
