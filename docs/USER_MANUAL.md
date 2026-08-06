# Driving Coach — User Manual

> **Version:** 1.0  
> **Last Updated:** 2026-07-13  
> **Platform:** Android 8.0+

Welcome to Driving Coach! This app helps you become a faster, smoother driver by analyzing your track sessions and providing personalized AI coaching feedback.

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Getting Started](#2-getting-started)
3. [Before Your Track Day](#3-before-your-track-day)
4. [Recording a Session](#4-recording-a-session)
5. [Reviewing Your Results](#5-reviewing-your-results)
6. [Sharing & Profile](#6-sharing--profile)
7. [Troubleshooting](#7-troubleshooting)
8. [FAQ](#8-faq)

---

## 1. Introduction

Driving Coach captures GPS and motion data while you drive on track, then analyzes your performance to:

- **Detect your laps** automatically using the start/finish line you set up
- **Compare lap times** to find your fastest lap
- **Provide AI coaching** with personalized tips to improve your driving
- **Track your progression** over time across all your sessions

### What You'll Need

- Android phone (8.0 or newer)
- Phone mount for your car
- A track day to attend!

---

## 2. Getting Started

### 2.1 Installation

1. Download the Driving Coach APK or install from your provided source
2. Open the installer and tap **Install**
3. If prompted about unknown sources, tap **Settings** → enable **Allow from this source** → tap **Install**

### 2.2 The Loading Screen

Every time you open the app, you're greeted by the **TRILLIAN** loading screen — the helmet
emblem, the wordmark, and a short engineering note.

The progress bar underneath is **not decoration**. It tracks the app actually getting ready:

| Bar | What's happening |
|-----|------------------|
| Preparing your garage | Reading your settings |
| Warming up the telemetry | Opening your local session database |
| Checking pending uploads | Looking for sessions still waiting to sync |
| Ready to roll | Taking you to the right screen |

**How long does it take?** Usually one to three seconds. If your phone is busy or storage is
slow it may take a little longer, but the app will never make you wait more than 8 seconds —
after that it moves on regardless and takes you to the setup screens, so you can always get
going.

**In a hurry?** Tap anywhere on the loading screen to skip the wait. The app still finishes
getting ready in the background, so nothing is skipped that matters.

**Pressing Back** on the first screen after loading closes the app — it won't send you back
to the loading screen.

### 2.3 First Launch & Permissions

When you first open Driving Coach, you'll be asked to grant permissions:

| Permission | Why It's Needed |
|------------|-----------------|
| **Location** | To record your position on track |
| **Physical Activity** | To detect when you're driving vs. walking |
| **Notifications** | To show recording status in your notification bar |

**Important:** Choose **"Allow all the time"** for location when prompted. This ensures accurate recording even if the screen turns off.

### 2.4 Creating Your Account

1. Tap **Register** on the welcome screen
2. Enter your details:
   - **Display Name** — how you'll appear in the app (2-100 characters)
   - **Email** — your email address
   - **Password** — at least 8 characters
3. Tap **Create Account**
4. You're in! You'll see the Home screen with your session list (empty for now)

### 2.5 Logging In

Already have an account?

1. Tap **Login**
2. Enter your email and password
3. Tap **Sign In**

Forgot your password? Contact support for a reset link.

---

## 3. Before Your Track Day

### 3.1 Setting Up the Start/Finish Line

Before recording, you need to tell the app where your start/finish line is. This lets the app automatically split your session into individual laps.

**How to capture the line:**

1. From the Home screen, tap the **+** button (Start New Session)
2. You'll see the **Track Setup** screen
3. Walk to **one edge** of the track at the start/finish line
4. Wait for GPS accuracy to show **< 5 meters** (the better, the better!)
5. Tap **Capture Point A**
6. Walk across the track to the **opposite edge** of the start/finish line
7. Tap **Capture Point B**
8. You'll see the distance between points — it should be roughly the track width (typically 10-15 meters)
9. Tap **Start Recording** when ready

**Tips for best results:**
- Walk to the actual track edge, not the pit lane
- Wait for good GPS signal before capturing each point
- The line should cross the entire track width
- Pick a spot you'll definitely cross every lap (not a chicane)

### 3.2 Phone Mounting Tips

- Mount your phone securely where it won't move
- Windscreen or dashboard mounts work well
- Avoid cup holder mounts (vibration affects sensors)
- Keep the phone plugged into power for long sessions
- Turn off battery saver mode

---

## 4. Recording a Session

### 4.0 The Home Screen

Once you're signed in, Home is your base. At the top sits the **brand hero** — the helmet
emblem, the TRILLIAN wordmark and the tagline.

**It gets out of your way.** As you scroll down through your recent sessions, the big hero
smoothly shrinks into a slim branded bar at the top, freeing the screen for your data. Scroll
back up and it expands again. Your profile button stays reachable in that bar the whole time.

Below the hero you'll find:

- Any **pending upload** notice, if sessions are still waiting to sync
- Your **best lap** card
- **Recent sessions**, newest first
- A **START SESSION** button — your way into a new run

If you haven't recorded anything yet, you'll see a friendly empty state instead of the
session list. The START SESSION button works exactly the same either way.

### 4.1 Starting a Recording

After setting up your start/finish line:

1. Tap **Start Recording**
2. You'll see a notification appear showing recording is active
3. The screen shows:
   - **Session duration** — total recording time
   - **GPS status** — satellites locked, signal quality
   - **Lap count** — laps crossed so far (updates live if connected)

### 4.2 During Your Session

**While recording:**
- You can turn off your screen — recording continues in the background
- The notification shows recording is active
- Green GPS indicator = good signal
- Yellow/Red = weak signal (results may be affected)

**What gets captured:**
- GPS position (10 times per second)
- Speed and heading
- Phone motion (accelerometer and gyroscope)

### 4.3 Stopping & Uploading

When your session is done:

1. Open the app and tap **Stop Recording**
2. **Lap times appear immediately** — the app detects laps locally from your GPS trace
3. If you have network connection, telemetry uploads for AI coaching analysis
4. Once complete, you'll see coaching insights on the Coach tab!

**Offline Mode (📶 Offline indicator):**
- Lap times are available immediately, even without internet
- If you're offline, you'll see "📶 Offline • Tap to upload" at the top
- Your session data is saved locally and will upload when you reconnect
- Tap the status bar to retry uploading, or dismiss it with the × button

**Upload Status:**
- **Uploading...** — Session is being sent to server
- **📶 Offline** — Offline, tap to upload when connected
- **⚠️ Upload failed** — Error occurred, tap to retry

**If you lose network connection:**
- Lap times still show (detected locally on your phone)
- Upload will retry automatically when you have signal
- AI coaching requires upload to complete

---

## 5. Reviewing Your Results

Tap any session from your Home screen to see your results.

### 5.1 Lap Times

The **Laps** tab shows all detected laps:

| Column | Meaning |
|--------|---------|
| Lap # | Which lap (1, 2, 3...) |
| Time | Lap time in minutes:seconds.milliseconds |
| Delta | Difference from your best lap (+0.5 = slower, -0.5 = faster) |
| ⭐ | Star marks your best lap |

**What counts as a lap?**
- Crossing the start/finish line you set up
- Minimum 20 seconds (to filter out accidental crossings)
- Must travel at least 50 meters away from start line (prevents false triggers)
- Your first lap starts when you first cross the line

**Local vs Server Detection:**
- Local detection shows lap times immediately after stopping
- Server detection runs after upload and may produce slightly different results
- Server results are authoritative and replace local results when available

### 5.2 AI Coaching Feedback

The **Coach** tab provides personalized driving tips:

- Analysis of your driving style
- Specific corners or sections to work on
- Comparison between your fast and slow laps
- Tips for consistency and speed

The AI looks at your speed profiles, braking points, and corner entry/exit to give relevant feedback.

### 5.3 Speed Charts

The **Chart** tab shows your speed around the track:

- **X-axis:** Distance in meters from start/finish line
- **Y-axis:** Speed in km/h
- **Blue line:** Your best lap
- **Grey lines:** All other laps

**Why distance, not time?**

Distance-based charts let you compare laps accurately. Every corner appears at the same position on the X-axis, so you can see:
- Where you braked earlier or later
- Corner entry and exit speeds
- Where you're gaining or losing time

**Large Sessions (10+ laps):**

For sessions with many laps, you'll see a choice:
- **⚡ Fast** — Loads quickly, slightly smoothed data (~100 points per lap)
- **📊 Detailed** — Full resolution, all telemetry samples

Choose Fast for a quick look, Detailed for precise analysis.

**Note:** Speed charts are processed locally from your telemetry file — no internet required!

### 5.4 Managing Sessions

You can rename or delete sessions from the Home screen:

**To rename or delete a session:**
1. **Long-press** on a session card in the "Recent Sessions" list
2. A menu will appear with options:
   - **Rename** — Change the track name
   - **Delete** — Remove the session

**Renaming a session:**
- Tap "Rename" to open the rename dialog
- Enter a new track name (1-100 characters)
- Tap "Rename" to save

**Deleting a session:**
- Tap "Delete" to open the confirmation dialog
- Confirm to permanently delete the session
- This removes all lap data, coaching insights, and the telemetry file
- **Warning:** Deletion cannot be undone

**Note:** Delete and rename are local operations only. If you've uploaded the session, data may still exist on the server.

---

## 6. Sharing & Profile

### 6.1 Share Cards

Show off your results!

1. Open a session
2. Tap the **Share** button
3. A share card is generated with:
   - Your best lap time
   - Session date and track
   - Your display name
4. Share via messages, social media, or save to gallery

### 6.2 Your Statistics

Tap your **Profile** (top right icon) to see:

- Total sessions recorded
- Total laps driven
- All-time best lap time
- Display name and email

### 6.3 Signing Out

1. Go to Profile
2. Scroll down and tap **Sign Out**
3. Your local data will be cleared
4. You can log back in anytime — your sessions are saved on the server

---

## 7. Troubleshooting

### GPS Not Locking

**Symptoms:** GPS accuracy stays above 10m, or shows "No GPS"

**Solutions:**
1. Make sure Location permission is set to "Allow all the time"
2. Go outside with clear sky view
3. Restart the app
4. Toggle Location off/on in your phone settings
5. Wait 1-2 minutes for GPS lock

### Session Stuck on "Processing"

**Symptoms:** Results never appear after upload

**Solutions:**
1. Check your internet connection
2. Pull down to refresh on the session screen
3. Wait a few minutes — large sessions take longer
4. If stuck over 10 minutes, tap "Retry" if available

### No Laps Detected

**Symptoms:** Session shows "No laps detected" or 0 laps

**Possible causes:**
1. Start/finish line wasn't crossed — check your track setup
2. GPS signal was too poor during recording
3. You didn't complete 2 full laps (minimum required)
4. Start line position doesn't intersect your actual GPS trace
5. Laps were too short (under 20 seconds — kart tracks are fine!)

**Solutions:**
1. For next session, set up the start/finish line more carefully
2. Ensure good GPS lock before recording
3. Make sure you drive at least 2 full laps crossing the line
4. Check that your driving path actually crosses where you set up the line
5. Walk the line setup points closer to where you'll actually drive

**Note for kart tracks:** The app uses a 50-meter minimum distance threshold, which works well for small kart tracks (300-500m lap length).

### Upload Keeps Failing

**Symptoms:** "Upload Failed" message, sessions stuck pending

**Solutions:**
1. Check Wi-Fi or cellular data is working
2. Move to an area with better signal
3. The app will retry automatically — check back later
4. If persistent, try signing out and back in

### App Crashes When Recording

**Solutions:**
1. Ensure you have 500+ MB free storage
2. Close other apps to free memory
3. Restart your phone
4. Update to the latest app version

---

## 8. FAQ

**Q: How much battery does recording use?**  
A: Roughly 10-15% per hour depending on your phone. We recommend keeping the phone plugged in for long sessions.

**Q: Can I record without an internet connection?**  
A: Yes! Data is stored locally and uploaded when you have a connection.

**Q: How accurate is lap timing?**  
A: Typically within ±0.2 seconds using GPS-based start/finish line detection. Professional timing systems are more accurate, but this is great for tracking improvement.

**Q: Can I use this for public roads?**  
A: The app is designed for closed tracks only. Lap detection requires a defined start/finish line, which doesn't apply to normal driving.

**Q: How long are sessions stored?**  
A: Sessions are stored on our servers indefinitely. Your session history is available whenever you log in.

**Q: What happens if GPS signal is lost during recording?**  
A: The app shows a warning, but recording continues. Gaps in GPS may affect lap detection accuracy. Try to maintain good sky visibility.

**Q: Can I edit the start/finish line after recording?**  
A: Not currently. You set the line before recording, and that's used for that session's lap detection.

**Q: Does the app work on tablets?**  
A: Yes, any Android device running 8.0+ with GPS hardware should work, though it's optimized for phones.

**Q: How does the AI coaching work?**  
A: We analyze your telemetry data — speed, acceleration, position — and compare your laps to identify areas for improvement. The AI looks for patterns like late braking, inconsistent corner entry, and speed scrubbing.

**Q: Is my data private?**  
A: Yes. Your session data is only visible to you when logged into your account. We don't share individual data with third parties.

---

## Need More Help?

If you're still having issues:

1. Check for app updates
2. Review this troubleshooting section
3. Contact support with:
   - Your email address
   - Device model and Android version
   - Description of the issue
   - Screenshots if helpful

---

*Happy lapping! 🏁*
