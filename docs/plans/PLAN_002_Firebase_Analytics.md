# Implementation Plan: Firebase Analytics & Crashlytics

> **Plan ID:** PLAN-002  
> **Created:** 2026-08-04  
> **Status:** Draft  
> **Priority:** High  
> **Estimated Effort:** 1 week

---

## 1. Executive Summary

Add **Firebase Analytics** and **Firebase Crashlytics** to the Driving Coach app to track user engagement, feature usage, and crash reports — without building or maintaining a custom backend.

---

## 2. Problem Statement

### Current State
- No visibility into how many users are active
- No data on which features are used
- No crash reporting (crashes go undetected)
- No retention or engagement metrics
- Can't make data-driven product decisions

### Desired State
- See daily/weekly/monthly active users
- Track feature usage (sessions recorded, coaching viewed, shares)
- Get instant alerts when crashes occur
- Understand user retention and drop-off points
- All at zero cost

---

## 3. What Is Firebase?

### 3.1 Simple Explanation

Firebase is a **free service by Google** that collects data from your app and shows it in a dashboard.

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   PHONES RUNNING YOUR APP          GOOGLE'S SERVERS         │
│                                                             │
│   ┌───────────┐                   ┌─────────────────┐       │
│   │  Phone 1  │ ─── "event" ────► │                 │       │
│   └───────────┘                   │                 │       │
│   ┌───────────┐                   │    Firebase     │       │
│   │  Phone 2  │ ─── "event" ────► │    Database     │       │
│   └───────────┘                   │                 │       │
│   ┌───────────┐                   │                 │       │
│   │  Phone 3  │ ─── "crash" ────► │                 │       │
│   └───────────┘                   └────────┬────────┘       │
│                                            │                │
│                                            ▼                │
│   YOUR BROWSER                    ┌─────────────────┐       │
│   ┌───────────┐                   │    Firebase     │       │
│   │ Dashboard │◄──────────────────│    Console      │       │
│   └───────────┘  You see reports  │    (website)    │       │
│                                   └─────────────────┘       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Two Services We'll Add

| Service | Purpose | Cost |
|---------|---------|------|
| **Firebase Analytics** | Track events (app opened, session recorded, etc.) | Free |
| **Firebase Crashlytics** | Catch crashes, show stack traces | Free |

---

## 4. What We'll Track

### 4.1 Analytics Events

| Event Name | When Fired | Parameters |
|------------|------------|------------|
| `session_recorded` | User stops recording | `duration_minutes`, `laps_detected` |
| `coaching_viewed` | User opens Coach tab | `insights_count` |
| `lap_times_viewed` | User opens Laps tab | `lap_count` |
| `chart_viewed` | User opens Chart tab | `chart_mode` (fast/detailed) |
| `share_card_created` | User shares results | — |
| `track_setup_completed` | Start line captured | — |
| `session_deleted` | User deletes session | — |
| `session_renamed` | User renames session | — |
| `login_success` | User logs in | — |
| `register_success` | User creates account | — |
| `offline_mode_used` | Recording while offline | — |

### 4.2 User Properties

| Property | Description |
|----------|-------------|
| `total_sessions` | Lifetime sessions recorded |
| `total_laps` | Lifetime laps driven |
| `app_version` | Current app version |

### 4.3 Automatic Events (Free)

Firebase automatically tracks these without any code:

- `first_open` — First time app opened
- `app_open` — Each time app opened
- `session_start` — Each usage session
- `screen_view` — Each screen viewed
- `app_update` — When user updates app

### 4.4 Crashlytics Data

Automatically collected on crash:

- Stack trace (where it crashed)
- Device model & OS version
- Memory usage at crash
- App state
- Last few log messages

---

## 5. What You'll See (Dashboard Examples)

### 5.1 Analytics Dashboard

```
┌────────────────────────────────────────────────────────────┐
│  FIREBASE ANALYTICS — Driving Coach                        │
│  Last 30 Days                                              │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ACTIVE USERS                                              │
│  ┌────────────────────────────────────────────────────┐   │
│  │  Daily:    127  │  Weekly:   412  │  Monthly: 1,247│   │
│  └────────────────────────────────────────────────────┘   │
│                                                            │
│  ─────────────────────────────────────────────────────    │
│                                                            │
│  TOP EVENTS                                                │
│  ┌──────────────────────────────────────────────────┐     │
│  │  Event                    Count       Users      │     │
│  │  ─────────────────────────────────────────────── │     │
│  │  session_recorded         4,102        892       │     │
│  │  coaching_viewed          2,891        756       │     │
│  │  lap_times_viewed         3,847        891       │     │
│  │  chart_viewed             2,456        634       │     │
│  │  share_card_created         412        201       │     │
│  └──────────────────────────────────────────────────┘     │
│                                                            │
│  ─────────────────────────────────────────────────────    │
│                                                            │
│  RETENTION                                                 │
│  ┌──────────────────────────────────────────────────┐     │
│  │  Day 1:  45%  ████████████░░░░░░░░░░░░░░░░░░░   │     │
│  │  Day 7:  28%  ██████████░░░░░░░░░░░░░░░░░░░░░   │     │
│  │  Day 30: 18%  ██████░░░░░░░░░░░░░░░░░░░░░░░░░   │     │
│  └──────────────────────────────────────────────────┘     │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### 5.2 Crashlytics Dashboard

```
┌────────────────────────────────────────────────────────────┐
│  FIREBASE CRASHLYTICS — Driving Coach                      │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  CRASH-FREE USERS: 98.4%  ████████████████████░░          │
│                                                            │
│  ─────────────────────────────────────────────────────    │
│                                                            │
│  RECENT ISSUES                                             │
│  ┌──────────────────────────────────────────────────┐     │
│  │  ⚠️ NullPointerException                         │     │
│  │     LocalLapDetector.kt line 142                 │     │
│  │     23 crashes • 19 users • Samsung devices      │     │
│  │                                                  │     │
│  │  ⚠️ IllegalStateException                        │     │
│  │     TelemetryForegroundService.kt line 89        │     │
│  │     8 crashes • 8 users • Android 12             │     │
│  │                                                  │     │
│  │  ⚠️ OutOfMemoryError                             │     │
│  │     ChartDataProcessor.kt line 234               │     │
│  │     3 crashes • 2 users • Low-memory devices     │     │
│  └──────────────────────────────────────────────────┘     │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### 5.3 DebugView (Real-Time Testing)

While developing, see events **instantly** (within seconds):

```
┌────────────────────────────────────────────────────────────┐
│  DEBUGVIEW — Real-time Events                              │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  12:15:01  first_open                                      │
│  12:15:02  screen_view { screen: "HomeFragment" }          │
│  12:15:08  track_setup_completed                           │
│  12:15:45  session_recorded { duration: 32, laps: 8 }      │
│  12:15:47  screen_view { screen: "SessionResultFragment" } │
│  12:15:50  lap_times_viewed { lap_count: 8 }               │
│  12:15:55  coaching_viewed { insights_count: 4 }           │
│  12:16:02  share_card_created                              │
│                                                            │
│  ● Live                            Device: Pixel 6         │
└────────────────────────────────────────────────────────────┘
```

---

## 6. Technical Implementation

### 6.1 Files to Create/Modify

```
Trillian/
├── app/
│   ├── build.gradle.kts              # ADD Firebase dependencies
│   ├── google-services.json          # NEW - Firebase config (download)
│   └── src/main/java/com/drivingcoach/
│       ├── DrivingCoachApp.kt        # MODIFY - Initialize Crashlytics
│       ├── analytics/
│       │   ├── AnalyticsTracker.kt   # NEW - Wrapper for Firebase calls
│       │   └── AnalyticsEvents.kt    # NEW - Event name constants
│       └── ui/
│           ├── home/
│           │   └── HomeViewModel.kt  # MODIFY - Track session events
│           ├── recording/
│           │   └── RecordingViewModel.kt  # MODIFY - Track recording
│           └── session/
│               └── SessionResultViewModel.kt  # MODIFY - Track views
├── build.gradle.kts                  # ADD Google services plugin
└── settings.gradle.kts               # No changes needed
```

### 6.2 Gradle Dependencies

```kotlin
// app/build.gradle.kts

plugins {
    // ... existing plugins
    id("com.google.gms.google-services")  // ADD
    id("com.google.firebase.crashlytics") // ADD
}

dependencies {
    // ... existing dependencies
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
}
```

```kotlin
// build.gradle.kts (project root)

plugins {
    // ... existing plugins
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}
```

### 6.3 Analytics Tracker Class

```kotlin
// app/src/main/java/com/drivingcoach/analytics/AnalyticsTracker.kt

package com.drivingcoach.analytics

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.ktx.Firebase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsTracker @Inject constructor() {
    
    private val analytics: FirebaseAnalytics = Firebase.analytics
    
    fun trackSessionRecorded(durationMinutes: Int, lapsDetected: Int) {
        analytics.logEvent(Events.SESSION_RECORDED) {
            param("duration_minutes", durationMinutes.toLong())
            param("laps_detected", lapsDetected.toLong())
        }
    }
    
    fun trackCoachingViewed(insightsCount: Int) {
        analytics.logEvent(Events.COACHING_VIEWED) {
            param("insights_count", insightsCount.toLong())
        }
    }
    
    fun trackLapTimesViewed(lapCount: Int) {
        analytics.logEvent(Events.LAP_TIMES_VIEWED) {
            param("lap_count", lapCount.toLong())
        }
    }
    
    fun trackChartViewed(mode: String) {
        analytics.logEvent(Events.CHART_VIEWED) {
            param("chart_mode", mode)
        }
    }
    
    fun trackShareCardCreated() {
        analytics.logEvent(Events.SHARE_CARD_CREATED, null)
    }
    
    fun trackTrackSetupCompleted() {
        analytics.logEvent(Events.TRACK_SETUP_COMPLETED, null)
    }
    
    fun trackSessionDeleted() {
        analytics.logEvent(Events.SESSION_DELETED, null)
    }
    
    fun trackSessionRenamed() {
        analytics.logEvent(Events.SESSION_RENAMED, null)
    }
    
    fun trackLoginSuccess() {
        analytics.logEvent(Events.LOGIN_SUCCESS, null)
    }
    
    fun trackRegisterSuccess() {
        analytics.logEvent(Events.REGISTER_SUCCESS, null)
    }
    
    fun trackOfflineModeUsed() {
        analytics.logEvent(Events.OFFLINE_MODE_USED, null)
    }
    
    // User properties
    fun setUserTotalSessions(count: Int) {
        analytics.setUserProperty("total_sessions", count.toString())
    }
    
    fun setUserTotalLaps(count: Int) {
        analytics.setUserProperty("total_laps", count.toString())
    }
    
    object Events {
        const val SESSION_RECORDED = "session_recorded"
        const val COACHING_VIEWED = "coaching_viewed"
        const val LAP_TIMES_VIEWED = "lap_times_viewed"
        const val CHART_VIEWED = "chart_viewed"
        const val SHARE_CARD_CREATED = "share_card_created"
        const val TRACK_SETUP_COMPLETED = "track_setup_completed"
        const val SESSION_DELETED = "session_deleted"
        const val SESSION_RENAMED = "session_renamed"
        const val LOGIN_SUCCESS = "login_success"
        const val REGISTER_SUCCESS = "register_success"
        const val OFFLINE_MODE_USED = "offline_mode_used"
    }
}
```

### 6.4 Example ViewModel Integration

```kotlin
// Example: RecordingViewModel.kt modification

@HiltViewModel
class RecordingViewModel @Inject constructor(
    application: Application,
    private val analyticsTracker: AnalyticsTracker  // ADD injection
) : AndroidViewModel(application) {

    fun stopRecording() {
        // ... existing stop logic ...
        
        // Track the event
        analyticsTracker.trackSessionRecorded(
            durationMinutes = (recordingDurationMs / 60000).toInt(),
            lapsDetected = detectedLaps
        )
    }
}
```

### 6.5 Hilt Module

```kotlin
// app/src/main/java/com/drivingcoach/di/AnalyticsModule.kt

package com.drivingcoach.di

import com.drivingcoach.analytics.AnalyticsTracker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    
    @Provides
    @Singleton
    fun provideAnalyticsTracker(): AnalyticsTracker {
        return AnalyticsTracker()
    }
}
```

---

## 7. Implementation Tasks

### Phase 1: Firebase Project Setup (Day 1)

| Task | Description | Est. Time |
|------|-------------|-----------|
| 1.1 | Create Firebase project at console.firebase.google.com | 10 min |
| 1.2 | Register Android app (package: `com.drivingcoach`) | 5 min |
| 1.3 | Download `google-services.json` | 2 min |
| 1.4 | Place `google-services.json` in `app/` folder | 2 min |
| 1.5 | Enable Analytics in Firebase Console | 5 min |
| 1.6 | Enable Crashlytics in Firebase Console | 5 min |

### Phase 2: Gradle Configuration (Day 1)

| Task | Description | Est. Time |
|------|-------------|-----------|
| 2.1 | Add Google services plugin to root `build.gradle.kts` | 5 min |
| 2.2 | Add Firebase plugins to app `build.gradle.kts` | 5 min |
| 2.3 | Add Firebase BOM and dependencies | 5 min |
| 2.4 | Sync Gradle and verify build succeeds | 10 min |

### Phase 3: Analytics Implementation (Day 2-3)

| Task | Description | Est. Time |
|------|-------------|-----------|
| 3.1 | Create `AnalyticsTracker` class with all event methods | 1 hour |
| 3.2 | Create Hilt module for dependency injection | 30 min |
| 3.3 | Add tracking to `RecordingViewModel` (session recorded) | 30 min |
| 3.4 | Add tracking to `SessionResultViewModel` (tab views) | 30 min |
| 3.5 | Add tracking to `HomeViewModel` (delete, rename) | 30 min |
| 3.6 | Add tracking to `TrackSetupFragment` (setup complete) | 20 min |
| 3.7 | Add tracking to `LoginViewModel` (login success) | 20 min |
| 3.8 | Add tracking to `RegisterViewModel` (register success) | 20 min |
| 3.9 | Add tracking to share functionality | 20 min |

### Phase 4: Crashlytics Setup (Day 3)

| Task | Description | Est. Time |
|------|-------------|-----------|
| 4.1 | Verify Crashlytics auto-initializes (no code needed) | 10 min |
| 4.2 | Add custom log messages at key points (optional) | 30 min |
| 4.3 | Test crash reporting with forced crash | 20 min |
| 4.4 | Verify crash appears in Firebase Console | 10 min |

### Phase 5: Testing & Validation (Day 4)

| Task | Description | Est. Time |
|------|-------------|-----------|
| 5.1 | Enable DebugView for real-time testing | 10 min |
| 5.2 | Test all events fire correctly | 1 hour |
| 5.3 | Verify events appear in Firebase Console | 30 min |
| 5.4 | Test on emulator and physical device | 30 min |
| 5.5 | Test offline behavior (events queued) | 20 min |

### Phase 6: Documentation (Day 5)

| Task | Description | Est. Time |
|------|-------------|-----------|
| 6.1 | Update Atlas with Firebase components | 30 min |
| 6.2 | Document tracked events in README | 30 min |
| 6.3 | Create Firebase Console access guide | 20 min |

---

## 8. Firebase Project Setup Guide

### Step-by-Step Instructions

#### 1. Create Firebase Project

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Click **"Create a project"**
3. Name it **"Driving Coach"**
4. Disable Google Analytics (we'll use Firebase Analytics instead) → **Continue**
5. Wait for project creation → **Continue**

#### 2. Add Android App

1. Click **Android icon** (Add Firebase to your Android app)
2. Enter package name: `com.drivingcoach`
3. Enter app nickname: `Driving Coach`
4. Skip SHA-1 (not needed for Analytics/Crashlytics)
5. Click **Register app**

#### 3. Download Config File

1. Click **Download google-services.json**
2. Save to your computer
3. Copy to `Trillian/app/google-services.json`

#### 4. Enable Crashlytics

1. In Firebase Console, go to **Crashlytics** (left menu)
2. Click **Enable Crashlytics**
3. Follow the setup wizard (we'll add SDK in code)

#### 5. Enable DebugView (for Testing)

Run this once on your computer (with phone connected):

```bash
adb shell setprop debug.firebase.analytics.app com.drivingcoach
```

Now events appear instantly in **Firebase Console → Analytics → DebugView**

---

## 9. Acceptance Criteria

### Functional Requirements

- [ ] All 11 custom events fire at correct times
- [ ] Events include correct parameters
- [ ] Events visible in Firebase Console within 24 hours
- [ ] DebugView shows events in real-time during development
- [ ] Crashes appear in Crashlytics dashboard
- [ ] Crash reports include stack traces and device info

### Technical Requirements

- [ ] App builds successfully with Firebase dependencies
- [ ] No performance degradation (events are async)
- [ ] Events queued when offline, sent when connected
- [ ] No sensitive data in event parameters (no PII)

### Verification

- [ ] Test on debug APK (no Play Store needed)
- [ ] Test on emulator
- [ ] Test on physical device
- [ ] Test offline → online event delivery

---

## 10. Privacy Considerations

### What IS Sent

- Anonymous user ID (random, not email)
- Event names and parameters
- Device model and OS version
- App version
- Country (from IP)
- Crash stack traces

### What is NOT Sent

- User's name or email
- GPS coordinates
- Telemetry data
- Any PII

### Compliance

- No special privacy policy changes needed for basic analytics
- Consider adding Firebase mention to privacy policy for transparency
- Users can opt-out via device-level Google settings

---

## 11. Risks & Mitigations

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Events not appearing | Can't see data | Low | Use DebugView for testing |
| Gradle sync issues | Build fails | Medium | Follow exact dependency versions |
| google-services.json missing | App crashes | Low | Verify file placement |
| Event names too long | Truncated data | Low | Keep names under 40 chars |

---

## 12. Cost Analysis

| Service | Free Tier Limit | Our Expected Usage | Cost |
|---------|-----------------|-------------------|------|
| Analytics | Unlimited events | Any | **$0** |
| Crashlytics | Unlimited crashes | Any | **$0** |
| BigQuery export | 1M events/month | Probably <100K | **$0** |

**Total monthly cost: $0**

---

## 13. Success Metrics

After 30 days of usage, we should be able to answer:

| Question | Firebase Feature |
|----------|------------------|
| How many active users? | Analytics → Active Users |
| Which features are most used? | Analytics → Events |
| Are users coming back? | Analytics → Retention |
| What's crashing? | Crashlytics → Issues |
| Which devices have problems? | Crashlytics → Device breakdown |

---

## 14. Dependencies

- Firebase account (free, use Google account)
- Android Studio with internet access
- Physical device or emulator for testing
- `adb` command-line tool (for DebugView)

---

*Document Version: 1.0*
