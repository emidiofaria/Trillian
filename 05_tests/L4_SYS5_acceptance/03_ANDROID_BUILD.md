# Android Build Instructions

> **Purpose:** Complete guide to build the Driving Coach APK, configure Firebase and API endpoints, and install on test device.

---

## Part A: Pre-Build Configuration

### Step 1: Verify Project Structure

```bash
cd driving-coach
ls -la app/
```

| Directory/File | Purpose | Exists | Check |
|----------------|---------|--------|-------|
| `app/build.gradle.kts` | App build configuration | ☐ | ☐ |
| `app/src/main/` | Main source code | ☐ | ☐ |
| `app/src/main/AndroidManifest.xml` | App manifest | ☐ | ☐ |
| `build.gradle.kts` | Root build file | ☐ | ☐ |
| `settings.gradle.kts` | Project settings | ☐ | ☐ |
| `gradle/wrapper/` | Gradle wrapper | ☐ | ☐ |

---

### Step 2: Configure Firebase

#### 2.1 Add google-services.json

1. Download `google-services.json` from Firebase Console:
   - Firebase Console → Project Settings → Your apps → Android app
   - Download `google-services.json`

2. Copy to project:
```bash
cp ~/Downloads/google-services.json app/google-services.json
```

| Verification | Expected | Check |
|--------------|----------|-------|
| File exists | `app/google-services.json` present | ☐ |
| Package name matches | `"package_name": "com.drivingcoach"` | ☐ |
| Firebase App ID present | `"mobilesdk_app_id"` field exists | ☐ |

**Verify contents:**
```bash
cat app/google-services.json | grep package_name
```

---

#### 2.2 Update Firebase Project ID in build.gradle.kts

Verify `app/build.gradle.kts` contains:
```kotlin
plugins {
    // ...
    id("com.google.gms.google-services")
}
```

---

### Step 3: Configure API Base URL

Edit `app/build.gradle.kts` to set the backend URL:

```kotlin
android {
    defaultConfig {
        // For local testing (emulator)
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000/\"")
    }
    
    buildTypes {
        debug {
            // Local backend
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000/\"")
        }
        release {
            // Production Azure backend
            buildConfigField("String", "API_BASE_URL", "\"https://app-driving-coach.azurewebsites.net/\"")
        }
    }
}
```

**For physical device testing against local backend:**
```kotlin
// Use your machine's local IP address
buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.100:3000/\"")
```

| Configuration | Value | Check |
|---------------|-------|-------|
| Debug URL (emulator) | `http://10.0.2.2:3000/` | ☐ |
| Debug URL (physical device) | `http://<your-ip>:3000/` | ☐ |
| Release URL | Azure App Service URL | ☐ |

---

### Step 4: Configure Google Maps API Key (if using)

Create or edit `app/src/main/res/values/secrets.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="google_maps_api_key">YOUR_GOOGLE_MAPS_API_KEY</string>
</resources>
```

| Verification | Expected | Check |
|--------------|----------|-------|
| Maps API key configured | Valid Google Maps API key | ☐ |
| API enabled in GCP Console | Maps SDK for Android enabled | ☐ |

> **Note:** If not using Google Maps, skip this step. Track setup uses manual coordinates.

---

## Part B: Build APK

### Step 1: Clean Previous Builds

```bash
./gradlew clean
```

| Verification | Expected | Check |
|--------------|----------|-------|
| Clean completes | "BUILD SUCCESSFUL" message | ☐ |

---

### Step 2: Build Debug APK

```bash
./gradlew assembleDebug
```

| Verification | Expected | Check |
|--------------|----------|-------|
| Build succeeds | "BUILD SUCCESSFUL" | ☐ |
| No compilation errors | Clean console output | ☐ |
| APK generated | File exists at output path | ☐ |
| APK reports correct version | `aapt2 dump badging <apk>` shows the expected `versionCode`/`versionName` — **not** `versionCode='1'` | ☐ |

**APK location:**
```bash
ls -la app/build/outputs/apk/debug/DrivingCoach-v*-debug.apk
```

> APK filenames are generated from `appVersionName` in `app/build.gradle.kts`
> (e.g. `DrivingCoach-v2.8-debug.apk`), so the name always matches the version
> the binary reports. Verify with:
> `aapt2 dump badging <apk> | head -1`

---

### Step 3: Build Release APK (for production testing)

#### 3.1 Create Keystore (first time only)

```bash
keytool -genkey -v -keystore driving-coach.keystore \
  -alias dc-release \
  -keyalg RSA -keysize 2048 -validity 10000
```

| Information | Value to Enter | Check |
|-------------|----------------|-------|
| Keystore password | (save securely) | ☐ |
| Key alias | `dc-release` | ☐ |
| Key password | (save securely) | ☐ |
| Organization | Driving Coach | ☐ |

---

#### 3.2 Configure Signing

Create or edit `local.properties` (NOT committed to Git):
```properties
storeFile=../driving-coach.keystore
storePassword=your_keystore_password
keyAlias=dc-release
keyPassword=your_key_password
```

Or configure in `app/build.gradle.kts`:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../driving-coach.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = "dc-release"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(...)
        }
    }
}
```

---

#### 3.3 Build Release APK

```bash
./gradlew assembleRelease
```

| Verification | Expected | Check |
|--------------|----------|-------|
| Build succeeds | "BUILD SUCCESSFUL" | ☐ |
| APK signed | Not "unsigned" in filename | ☐ |
| R8/ProGuard applied | `isMinifyEnabled = true` | ☐ |

**APK location:**
```bash
ls -la app/build/outputs/apk/release/DrivingCoach-v*-release.apk
```

---

## Part C: Device Installation

### Step 1: Enable Developer Options on Device

| Step | Action | Check |
|------|--------|-------|
| C1.1 | Go to Settings → About Phone | ☐ |
| C1.2 | Tap "Build Number" 7 times | ☐ |
| C1.3 | See "You are now a developer" toast | ☐ |

---

### Step 2: Enable USB Debugging

| Step | Action | Check |
|------|--------|-------|
| C2.1 | Go to Settings → Developer Options | ☐ |
| C2.2 | Enable "USB Debugging" | ☐ |
| C2.3 | Confirm security prompt | ☐ |

---

### Step 3: Connect Device

```bash
# Check device connection
adb devices
```

| Verification | Expected | Check |
|--------------|----------|-------|
| Device listed | Shows device serial number | ☐ |
| Status "device" | Not "unauthorized" or "offline" | ☐ |

If "unauthorized":
1. Disconnect USB
2. Revoke USB debugging authorizations (Developer Options)
3. Reconnect USB
4. Accept authorization prompt on phone

---

### Step 4: Install APK

**Via command line:**
```bash
# Debug build
adb install -r app/build/outputs/apk/debug/DrivingCoach-v*-debug.apk

# Release build
adb install -r app/build/outputs/apk/release/DrivingCoach-v*-release.apk

# Confirm the installed version matches what you expect
adb shell dumpsys package com.drivingcoach | grep -E "versionCode|versionName"
```

**Via Android Studio:**
1. Open project in Android Studio
2. Select connected device in device dropdown
3. Click Run (▶) button

| Verification | Expected | Check |
|--------------|----------|-------|
| Installation succeeds | "Success" message | ☐ |
| App appears in launcher | "Driving Coach" icon | ☐ |
| App opens without crash | Splash/onboarding screen | ☐ |

---

### Step 5: Grant Permissions on Device

When prompted by app or manually in Settings:

| Permission | Settings Path | Check |
|------------|---------------|-------|
| Location | App Info → Permissions → Location → "Allow all the time" | ☐ |
| Physical Activity | App Info → Permissions → Physical Activity → Allow | ☐ |

---

## Part D: Build Verification

### Quick Smoke Test

| Test | Action | Expected Result | Check |
|------|--------|-----------------|-------|
| D1 | Launch app | Onboarding screen (first launch) or Login | ☐ |
| D2 | Tap through onboarding | Permissions requested | ☐ |
| D3 | Grant permissions | Login screen appears | ☐ |
| D4 | Attempt login (wrong credentials) | Error snackbar | ☐ |
| D5 | Register new user | Home screen appears | ☐ |
| D6 | Tap profile icon | Profile screen with user info | ☐ |
| D7 | Sign out | Returns to login screen | ☐ |

---

### Network Connectivity Test

Ensure device can reach backend:

**For local backend (same network):**
```bash
# On device (via adb shell) or using a network testing app
ping <your-local-ip>
curl http://<your-local-ip>:3000/health
```

**For Azure backend:**
- Device must have internet connectivity
- Backend URL in BuildConfig must be correct

| Test | Expected | Check |
|------|----------|-------|
| Ping backend host | Responds | ☐ |
| Health endpoint | 200 OK | ☐ |
| Firebase Auth works | Registration/login successful | ☐ |

---

## Part E: Track Day Build Checklist

### Pre-Track APK Build

| Item | Action | Check |
|------|--------|-------|
| E1 | Update API_BASE_URL to production Azure URL | ☐ |
| E2 | Build release APK | ☐ |
| E3 | Test release APK on device | ☐ |
| E4 | Verify Firebase Auth works | ☐ |
| E5 | Verify backend connectivity | ☐ |
| E6 | Charge phone to 100% | ☐ |
| E7 | Clear previous test sessions (optional) | ☐ |

### Device Readiness

| Item | Status | Check |
|------|--------|-------|
| Android version | 8.0+ (API 26+) | ☐ |
| GPS enabled | Location on, high accuracy mode | ☐ |
| Battery saver | OFF (affects GPS) | ☐ |
| Storage space | >500 MB free | ☐ |
| Screen timeout | Set to 10+ minutes | ☐ |
| Do Not Disturb | Enable during recording | ☐ |

### App Configuration

| Item | Configured | Check |
|------|------------|-------|
| User account created | Email/password set | ☐ |
| Logged in | Session active | ☐ |
| Permissions granted | Location + Activity | ☐ |

---

## Troubleshooting

### Build Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "SDK location not found" | ANDROID_HOME not set | Export ANDROID_HOME in shell profile |
| "Could not find google-services.json" | Missing Firebase config | Download from Firebase Console |
| "minSdk version 26" | Device too old | Use API 26+ device |
| "Execution failed for task ':app:processDebugGoogleServices'" | Package mismatch | Verify package_name in google-services.json |
| "ProGuard error" | Missing keep rules | Check proguard-rules.pro |

### Installation Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "INSTALL_FAILED_USER_RESTRICTED" | Installation blocked | Enable "Install via USB" in Developer Options |
| "INSTALL_FAILED_UPDATE_INCOMPATIBLE" | Signature mismatch | Uninstall existing app first |
| "INSTALL_FAILED_INSUFFICIENT_STORAGE" | No space | Clear device storage |

### Runtime Errors

| Issue | Cause | Solution |
|-------|-------|----------|
| App crashes on start | Firebase misconfigured | Check google-services.json |
| "Network error" on login | Backend unreachable | Verify API_BASE_URL, network connectivity |
| GPS not acquiring | Location off | Enable location services, high accuracy |

---

## Summary Checklist

| Phase | Items | Completed |
|-------|-------|-----------|
| Firebase Configuration | 4 items | ☐ |
| API URL Configuration | 3 items | ☐ |
| Debug Build | 4 items | ☐ |
| Release Build | 6 items | ☐ |
| Device Setup | 6 items | ☐ |
| APK Installation | 4 items | ☐ |
| Smoke Test | 7 items | ☐ |
| Track Day Prep | 11 items | ☐ |

**Total: 45 items**

---

*Document ID: SAT-ANDROID-001 | Version: 1.0 | Date: 2026-05-06*
