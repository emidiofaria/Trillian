# Trillian - Driving coach — User Manual

> **Version:** 1.1  
> **Last Updated:** 2026-09-21  
> **Platform:** Android 8.0+

Welcome to Trillian! This app helps you become a faster, smoother driver by analyzing your track sessions and providing personalized AI coaching feedback.

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

Every time you open the app you'll see two screens in quick succession. First Android shows
the helmet emblem on its own — that's the phone launching the app, and its length is out of
our hands. Then the **TRILLIAN** loading screen takes over: the emblem, the wordmark, and a
short engineering note.

The timings below all describe that **second** screen.

The progress bar underneath is **not decoration**. It tracks the app actually getting ready:

| Bar | What's happening |
|-----|------------------|
| Preparing your garage | Reading your settings |
| Warming up the telemetry | Opening your local session database |
| Ready to roll | Taking you to the right screen |

**How long does it take?** Usually one to three seconds. If your phone is busy or storage is
slow it may take a little longer, but the app will never make you wait more than 8 seconds —
after that it moves on regardless and takes you to the setup screens, so you can always get
going.

**Why does it linger on the first few launches?** The loading screen carries a short
engineering note, and at normal speed it disappears before anyone could actually read it. So
on the **first 3 launches** after you install the app the screen is held for about 4 seconds
and invites you to *"Tap to continue"*. Those 4 seconds are counted from the moment the
loading screen actually appears, so a slow-starting phone doesn't eat into your reading time. From the 4th launch onwards it gets out of your way
and shows *"Tap to skip"* instead.

You are never *required* to tap. The screen always moves on by itself — the tap only makes it
faster. If you missed the note, you can read it any time under **Profile → About Trillian**.

**In a hurry?** Tap anywhere on the loading screen to skip the wait. The app still finishes
getting ready in the background, so nothing is skipped that matters.

**Pressing Back** on the first screen after loading closes the app — it won't send you back
to the loading screen.

### 2.3 First Launch & Permissions

When you first open Trillian, you'll be asked to grant permissions:

| Permission | Why It's Needed | Required? |
|------------|-----------------|-----------|
| **Location** | To record your position on track | Yes |
| **Notifications** | To show recording status — including the "GPS signal lost" warning | Optional |

**Trillian does not ask for Physical Activity.** Earlier versions did, but
nothing in the app ever used it, so it has been removed.

Choose **"While using the app"** for location. Trillian never tracks you in the
background: location is only ever sampled during a session you started yourself,
and a notification stays visible the whole time it is. You do not need to grant
"Allow all the time", and Trillian will not ask for it.

If you decline **Notifications**, recording still works normally — you just won't
see the status bar entry or the warning when GPS drops out. You can turn it on
later in Android Settings → Apps → Trillian → Notifications.

### 2.4 Telling Trillian Your Name

There are no accounts, no passwords, and no email addresses in this version. Trillian just
needs to know what to call you.

1. After permissions, you'll see the **Driver Name** screen
2. Type the name you want to race under — anything from 2 to 100 characters
3. Tap **LET'S RACE!!**

That's it. You're on the Home screen with an empty session list, ready to go.

Your name is stored **on your phone only**. Nothing is uploaded, and nothing leaves the
device when you set it.

### 2.5 Opening the App Again

Trillian remembers you. Every launch after the first goes straight to the Home screen — you
will not be asked for your name again.

If you *are* asked for your name a second time, something cleared the app's data. See
[Troubleshooting](#7-troubleshooting).

**Want to change your name?** You can, at any time — see [6.2 Your Profile](#62-your-profile).

**What about signing in?** Accounts, cloud backup, and signing in on a second phone are
planned for a future version. This version is deliberately offline-first: everything lives
on your phone.

---

## 3. Before Your Track Day

### 3.0 Open the App Early

The first GPS fix of the day is the slow one. Your phone has to download fresh satellite data
before it can tell you where you are, and outdoors from cold that genuinely takes **30 to 60
seconds**. No app can make that faster — but Driving Coach can make it happen while you're
still in the paddock instead of while you're standing at the track edge.

**So: open the app a couple of minutes before you walk out.** The moment the Home screen
appears, the app starts hunting for satellites — quietly, with nothing for you to watch or
wait on. Home stays out of your way; the searching happens in the background.

That head start is spent by the time you reach the track edge, so **Track Setup is ready the
moment you arrive** — CAPTURE is live, with no second wait. Track Setup is also where the GPS
status is shown, because that's the one screen where it changes what you can do: it reads
*Acquiring GPS...* until the fix is good enough, then switches to the satellite count and
accuracy.

If the app has no location permission it can't search at all, and Track Setup will sit on
*Acquiring GPS...* indefinitely — see 2.3 to grant it.

A few practical notes:

- Get out from under a roof, an awning or a garage — GPS needs a clear view of the sky.
- The head start carries over. Moving from Home to Track Setup doesn't restart the search, so
  you only ever wait once.
- Searching stops when you leave the app — put your phone away and the receiver is released.
  Reopen it and the search starts again. It also stops on its own after half an hour if you
  simply leave the app sitting open.
- Warming up is only about *readiness*. Your actual start-line points are always taken fresh,
  at the moment you tap CAPTURE, from a live reading — and never from a reading older than a
  few seconds, so a position from back in the paddock can't become your start line.
- Curious how long it actually took? **Profile → About** shows the timings from your last
  acquisition. That's the number to quote if you ever report a slow lock.

### 3.1 Choosing Your Circuit

From v3.0, starting a session asks you one extra question — and it saves you a walk.

1. Tap **START SESSION**
2. Type a name for the session and tap **Start**
3. You'll be asked **"Where are you driving?"** with two choices:

| Choice | Use it when | What happens |
|--------|-------------|--------------|
| **SELECT TRACK** | The circuit is already in the app — one that ships with Trillian, or one you saved earlier | Pick it from a list, confirm, and go straight to recording. No walking, no capturing |
| **NEW CIRCUIT** | You're somewhere new | Exactly the same as before: walk to the track edges and capture two points (see 3.2) |

**If you pick SELECT TRACK:**

4. You'll see a list of circuits. Each one shows its name, where it is, and how wide its
   start/finish line is. Both the circuits that ship with Trillian and the ones you've saved
   appear in the same list.
5. Tap the one you're at. A confirmation screen shows the circuit's name, lap length, the
   number of corners and roughly how long a lap there should take — check it's the right
   place.
6. **START** stays greyed out until your GPS is ready, exactly as it does on the capture
   screen. Picking a known circuit doesn't skip the GPS check — you still need a good, current
   fix before recording begins.
7. Tap **START RECORDING** and drive.

**Circuits that ship with Trillian**

Open **SELECT TRACK** to see the current list — it's the authoritative one, and it works with
no signal. Each entry shows the circuit's name, where it is, and how long a lap is.

Every built-in circuit has had its start/finish line and its track outline surveyed on foot,
rather than captured in ten seconds before a session — which is why the app can be more precise
about your laps there than it can anywhere it's never been. Where a real recorded session of the
circuit exists, the numbers the app expects were checked against it, so the lap times it
anticipates are ones somebody actually set rather than ones somebody guessed at.

Knowing a circuit gives Trillian three things it can't get from a line you capture:

- **Which way you drive through the start/finish** — so it can tell a lap from a walk across
  the track, right from the first crossing
- **How long a lap should take** — so a "lap" of fifteen minutes is recognised as nonsense and
  not shown to you
- **The shape of the track** — so time you spend in the paddock or queueing doesn't get counted
  into your session distance and average speed

### 3.2 Setting Up the Start/Finish Line (New Circuit)

Before recording, you need to tell the app where your start/finish line is. This lets the app automatically split your session into individual laps.

**How to capture the line:**

1. Open the app a couple of minutes before you walk out, so the first fix is already done (see 3.0)
2. Tap **START SESSION**, name the session, then choose **NEW CIRCUIT**
3. You'll see the **Track Setup** screen
4. Walk to **one edge** of the track at the start/finish line
5. Wait for GPS accuracy to show **< 5 meters** (the better, the better!)
6. Tap **Capture Point A**
7. Walk across the track to the **opposite edge** of the start/finish line
8. Tap **Capture Point B**
9. You'll see the distance between points — it should be roughly the track width (typically 10-15 meters)
10. Tap **Start Recording** when ready
11. Trillian will ask **"Save this circuit?"** — tap **SAVE & START** to keep it for next time,
    or **JUST START** to use it only for this session

> **Save it and you never capture it again.** A circuit you save appears in the SELECT TRACK
> list from then on, so your next visit is two taps instead of a walk across the track. You can
> rename or delete your saved circuits at any time. The circuits that ship with Trillian can't
> be renamed or deleted — they come with the app.

> **Don't worry about which way round you capture the two points.** The app works out the
> direction you're driving from the recording itself, so it only needs to know *where* the
> start/finish is, not which way it faces. Capture them in either order.

**Tips for best results:**
- Walk to the actual track edge, not the pit lane
- Wait for good GPS signal before capturing each point
- The line should cross the entire track width
- Pick a spot you'll definitely cross every lap (not a chicane)
- Pick a spot you pass **once** per lap — if the circuit crosses over itself, don't put the
  start/finish where the two paths meet
- If your screen switches off or you jump to another app mid-setup, that's fine — GPS picks
  straight back up when you return to the screen

### 3.3 Phone Mounting Tips

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

### 4.3 Stopping a Session

When your session is done:

1. Open the app and tap **Stop Recording**
2. **Lap times appear immediately** — the app detects laps locally from your GPS trace
3. Open the session to see your lap breakdown, speed trace and track map

**Everything happens on your phone.** Trillian has no server and no account.
Lap detection, the speed trace, the track map and the session stats are all
computed on the device from your own GPS recording.

That is why the app works perfectly in **flight mode**, which is often how track
days go: no signal at the circuit, no problem. There is nothing to wait for, no
"pending upload", and no feature that only works once you get bars again.

**Where your data lives:** in Trillian's private storage on this phone. It is
never uploaded. The app does not even have permission to use the internet — see
the FAQ (§8) and the privacy policy. If you want a session somewhere else, export or share
it yourself from the session screen (§6).

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

### 5.4 The Analysis Tab

The **Analysis** tab is your post-session debrief — the sort of thing a race engineer would hand
you when you climb out of the car. Everything on it is worked out on your phone from the session
you just drove, so it works with no signal at all.

**At the top: your session at a glance**

| | |
|---|---|
| **Total distance** | How far you drove, start to finish |
| **Session duration** | How long the recording lasted |
| **Max speed** | Your fastest moment of the day |
| **Average speed** | Averaged over the *whole* recording — including any time you spent sitting still, so it will look low |
| **Best lap** | Your quickest lap time |

**The track map**

Below the numbers is a map of the track — drawn from your own GPS trace, not downloaded from
anywhere:

- The **colour of the line shows your speed**: blue where you were slowest, through amber, to
  green where you were fastest.
- **Red stretches are where you braked.**
- **T1, T2, T3…** mark the corners the app found, numbered in the order you drove through them.
- A **gold marker** shows the start/finish line, if you set one up.

**Choosing which lap to look at**

Under the map is a row of lap chips. Your best lap is selected to begin with. Tap any other lap and
the map, the corner list and the braking list are all redrawn for that lap. The line above the map
always tells you which lap you're looking at.

**Corners**

For every corner on the selected lap you get the direction, how many degrees you turned, and your
**apex speed** — the slowest point through the corner. That's the number to watch: carrying more
speed at the apex is usually where lap time hides.

> The corner numbers are the app's own, based on the order you pass them. They won't always match
> the numbering on the circuit's official map.

**Braking zones**

For every place you braked hard: the speed you came in at, the speed you got down to, how much
speed you shed, how long you were on the brakes, and the **peak g**.

> These figures come from how quickly your GPS speed drops, not from the phone's motion sensors.
> That's deliberate: what those sensors read depends entirely on how the phone happens to be
> mounted, so they can't be trusted to tell forwards from sideways. GPS speed doesn't care which
> way up your phone is.

**Speed over the session**

At the bottom, a graph of your speed for the entire session — every lap, in and out laps included.
Useful for spotting the lap where you finally got it right, or the one where traffic ruined it.

**If the tab looks empty**

- *"The telemetry file for this session is no longer on this device"* — the recording has been
  deleted from the phone. Nothing can be recovered, but your lap times are safe.
- *"This session has too little telemetry to analyse"* — the recording is genuinely too short.
- *"Reference: whole session"* — no usable lap was found, so the analysis covers everything you
  recorded rather than a single lap.

### 5.5 Managing Sessions

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

**Note:** Delete and rename are local operations — and since nothing is ever uploaded, deleting here deletes the only copy. There is no server copy and no undo.

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

If something goes wrong while preparing the card, the app now tells you with a
message at the bottom of the screen instead of appearing to do nothing.

### 6.1.1 Sending a Diagnostic File (for bug reports)

If you hit a problem and want to help get it fixed, you can send the raw data
from a session straight to the developer.

1. Open the session that went wrong
2. **Press and hold** the **Share** button (a normal tap gives you the picture card instead)
3. Wait a moment while the file is packaged
4. Pick an app — email, chat, cloud storage — and send it

**What's inside:** the file is a `.zip` containing the session's raw recording plus
a small summary (track name, lap times, your app version and phone model). That
recording includes the **exact GPS path you drove**, so only send it to someone
you trust.

**What it does *not* do:** exporting never changes, moves or deletes anything.
Your session, your laps and your recording stay exactly as they were — you can
export the same session as many times as you like.

If the original recording is no longer on your phone, you'll see
*"Telemetry file not found"* and nothing is sent. Older sessions recorded before
this feature existed may not have a file to share.

### 6.2 Your Profile

Tap your **Profile** (top right icon) to see:

- Your driver name and initials
- Total sessions recorded
- Total laps driven
- All-time best lap time

**Changing your name:** tap your name, or the edit icon beside it, type a new one, and tap
**Save**. The same 2–100 character rule applies. Renaming is purely cosmetic — none of your
sessions, laps, or coaching notes are affected.

### 6.3 About Trillian

Tap **About Trillian** at the bottom of the Profile screen. This screen is the permanent home
for the brand and the engineering note that flashes past on the loading screen, plus the
**app version** (for example `2.92 (292)`) and your **last GPS acquisition** timings — how
long the last fix took, and how long until it was accurate enough to use.

If you're reporting a problem, this is the screen to quote the version from — and the GPS
timings too, if the problem was a slow lock.

### 6.4 Clear User Data

> ⚠️ **This permanently deletes everything and cannot be undone.** There is no account and
> no cloud backup in this version, so there is nothing to restore from. Export or share
> anything you want to keep *before* using this.

1. Go to Profile
2. Scroll down and tap **Clear User Data**
3. Read the confirmation and tap **Delete everything** if you're sure

This removes your driver name, every recorded session, every lap, every coaching note, and
the raw telemetry files behind them. The app returns to the very first screen, as if freshly
installed.

Use it when handing the phone to someone else, or to start completely fresh.

---

## 7. Troubleshooting

### GPS Not Locking

**Symptoms:** GPS accuracy stays above 10m, or shows "No GPS"

**Solutions:**
1. Make sure Location permission is granted, and set to **precise** rather than approximate
2. Go outside with clear sky view
3. Restart the app
4. Toggle Location off/on in your phone settings
5. Wait 1-2 minutes for GPS lock
6. Open the app a few minutes before walking out (see 3.0) — the first fix of the day is
   always the slow one, and the app spends that time for you while you're still in the paddock
7. Check **Profile → About**: it shows how long your last fix actually took. If that number
   is small but you waited a long time, mention it when you report the problem — it means
   something other than satellite reception was at fault

### "Getting a current GPS fix…" on Track Setup

**Symptoms:** the CAPTURE buttons are greyed out and the status reads *Getting a current GPS
fix…* rather than *Acquiring GPS…*

This is not the app searching for satellites — it already has them. It's telling you the last
reading it received is too old to say where you're standing *now*, so it won't let you capture
a start line from it. That usually happens for a second or two after your phone has been in
your pocket or the screen has been off.

**What to do:** nothing. Hold the phone still with a view of the sky and it clears itself
within a second or two. There's no need to leave the screen or restart anything.

**Why it matters:** a position captured from where you *were* would put your start/finish line
in the wrong place, and every lap time for the session would be wrong by the same amount — with
nothing on screen to tell you. The short wait is the app refusing to guess.

### The App Asks for My Name Again

**Symptoms:** you set your name once, but a later launch shows the Driver Name screen again —
and your sessions are gone too.

**This means the app's data was cleared,** not that it forgot you. Common causes:

| Cause | What to do |
|-------|------------|
| **Clear User Data** was tapped | Nothing to recover — it's permanent by design |
| Android "Clear storage" in system settings | Same; this wipes the app completely |
| The app was uninstalled and reinstalled | Sessions live on the phone only, so they don't come back |
| Device storage is full | Free up space; the app may be unable to save your name at all |

If your **sessions are still there** but you're asked for a name anyway, that's a bug —
please report it with your app version from *About Trillian*.

### Session Stuck on "Processing"

**Symptoms:** Results never appear

Processing is entirely local, so this is not a connection problem — do not bother
checking your signal.

**Solutions:**
1. Go back to Home and reopen the session
2. Wait a few moments — a long session has more samples to work through
3. If it persists, report it with your app version from *About Trillian*

### No Laps Detected

**Symptoms:** Session shows "No laps detected" or 0 laps

**Possible causes:**
1. You drove more than about 15 metres to the side of where you captured the start/finish
2. GPS signal was too poor during recording
3. You didn't complete 2 full laps (minimum required)
4. Laps were too short (under 20 seconds — kart tracks are fine!)

**Solutions:**
1. Capture the start/finish point close to the racing line, not at the far edge of the track
2. Ensure good GPS lock before recording
3. Make sure you drive at least 2 full laps past that point
4. Wait for GPS accuracy under 5 metres before capturing

**Note for kart tracks:** The app uses a 50-metre minimum distance threshold, which works well for small kart tracks (300–500 m lap length).

**Fixed in version 2.94:** Earlier versions could report "No laps detected" after a perfectly
good session, because of *which way round* you happened to capture the two start/finish points.
The app no longer uses the direction of the line you capture — only where it is — so this can no
longer happen. If you saw this on an older version, re-recording on 2.94 or later will work; the
old session cannot be recovered.

**Improved in version 2.96:** The app now tells you what it actually saw. Instead of "Complete at
least 2 laps" — which was unhelpful if you had just driven five — it reports how many times you
went past the start/finish and how far to the side you were.

It also corrects itself where it can. Your phone is least accurate at placing itself when it is
**standing still**, which is exactly when you capture the start/finish. It can record that point
10–20 metres to one side of where you were actually standing — and because both taps happen
within seconds of each other, the line still looks perfectly sensible. On one real session the
start/finish was recorded 16 metres off the track; the driver completed three laps and the app
reported none.

If the app finds no laps, it now checks whether moving the start/finish onto the path you actually
drove would find them, and uses that only if it is a small correction (under 20 metres) and only if
it genuinely produces laps. Your recorded sessions are unaffected where detection already worked.

**What you can do about it:** stand on the part of the track you actually drive when you capture,
wait for the GPS dot to go green, and give it a few seconds to settle before tapping. If the app
still finds no laps, the message will now tell you how far off it was, so you know whether to
re-capture or whether something else went wrong.

### My Lap Times Are About Half What I Drove

**Symptoms:** Twice as many laps as you completed, each about half the time you expected. No
error message — the numbers just look wrong.

**Cause:** The app decides you've completed a lap when you drive past the start/finish point.
If your circuit passes that same point twice per lap — a figure-of-eight, or a layout where the
start/finish is on a bridge over another part of the track — it counts each pass.

**Solution:** Set the start/finish at a point you pass **once** per lap. Any normal piece of
straight will do.

If your track really does cross over itself and there is nowhere that works, please send a
diagnostic file (see 6.1.1) — we'd like to see a real recording of a layout like that.

### Fewer Laps Than I Drove, Each About Twice as Long

**Symptoms:** Half the laps you completed, each roughly double the time. The app now adds a note
to the result: *"1 pass of the start line was too soon after the one before to be counted — some
laps here may be timed as one."*

**Cause:** You are quicker than the circuit expects. Every track in the library carries a fastest
realistic lap time, and the app refuses to believe a lap shorter than 80 % of it — that guard is
what stops a wobble across the line being counted as a lap of its own. If you genuinely lap
faster than that, a real crossing gets refused, and the lap that followed it is timed from the
crossing before instead. Two laps come out as one.

**What changed:** the app used to do this without saying anything. A combined lap looks entirely
ordinary — it is inside the expected window and at a perfectly believable speed — so nothing
downstream could tell it apart from a slow lap, and you were shown the wrong number of laps with
no warning at all. It now tells you when passes went uncounted.

**Solution:** there is nothing to change in the app, and nothing you have done wrong — the note
means the circuit's expected lap time needs correcting, not your driving. Please send a
diagnostic file (see 6.1.1) with your session. A real recording from a driver quicker than the
catalogue is exactly the evidence needed to fix the entry for everyone.

**If you are recording without choosing a track** from the library, this cannot affect you above
20-second laps, since the app then falls back to a fixed 20-second minimum.

### My Lap Times Are in Minutes, Not Seconds

**Symptoms:** A full session comes back as two or three "laps" of several minutes each. No error
message. The numbers are confidently, specifically wrong.

**Cause:** The recording was started before you got in the kart, and you walked across the
start/finish line while waiting. The app used to accept that walk as your first lap crossing —
and worse, it then measured every *real* lap against the direction you'd been walking, throwing
most of them away.

This is exactly what happened at Baltar: sixteen minutes in the queue beside the start straight,
twelve laps driven, two absurd "laps" reported.

**Fixed in version 3.0.** A crossing made below walking-to-jogging pace (about 4 m/s) is no
longer counted as a lap, on any circuit — whether or not it's one the app knows. If you pick a
circuit from **SELECT TRACK**, the app also knows which way you drive through the start/finish
before your session even begins, so a wrong-way pass can't set the reference at all.

**What you can do:** nothing is required, but starting the recording when you get in the kart
rather than when you join the queue still gives cleaner session statistics.

**Older sessions:** the stored lap times can't be recalculated. Re-recording on 3.0 or later will
be correct.

### The Circuit List Is Empty

**Symptoms:** You tap **SELECT TRACK** and there's nothing there, or one of the circuits that
ships with the app is missing.

**Possible causes:**
1. You haven't saved any circuits yet, and something went wrong reading the ones that ship with
   the app
2. You're on a version before 3.0, which didn't have the track library

**Solution:** Use **NEW CIRCUIT** — it always works and is completely unaffected. Then report the
problem with your app version from *About Trillian*; an empty list, or a missing built-in
circuit, is a packaging fault worth knowing about.

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
A: Yes — and it makes no difference whatsoever. Trillian works fully in flight
mode. Every feature, including lap detection, the speed trace and the track map,
runs on your phone. The app has no server to reach and does not hold the Android
internet permission at all.

**Q: Does Trillian send my location anywhere?**  
A: No. Your GPS recordings stay in the app's private storage on your phone.
There is no account, no server, no analytics and no tracking. The release build
does not have permission to use the internet, so it could not send your data
even if it tried. The only way anything leaves your phone is if you export or
share it yourself (§6).

**Q: How do I delete my data?**  
A: Delete individual sessions in the app, or uninstall Trillian / clear its
storage in Android Settings to remove everything. Since nothing is uploaded,
there is no server copy to worry about.

**Q: How accurate is lap timing?**  
A: Typically within ±0.2 seconds using GPS-based start/finish line detection. Professional timing systems are more accurate, but this is great for tracking improvement.

**Q: Can I use this for public roads?**  
A: The app is designed for closed tracks only. Lap detection requires a defined start/finish line, which doesn't apply to normal driving.

**Q: How long are sessions stored?**  
A: On your phone, for as long as you keep them. This version stores everything locally — there is no account and no cloud backup — so uninstalling the app or using **Clear User Data** deletes your history permanently.

**Q: Do I need to create an account?**  
A: No. Trillian only asks for a name so it knows what to call you. Sign-in is planned for a future version.

**Q: Will I lose my sessions if I change my name?**  
A: No. Your name is purely a label; renaming never touches your recorded sessions.

**Q: What happens if GPS signal is lost during recording?**  
A: The app shows a warning, but recording continues. Gaps in GPS may affect lap detection accuracy. Try to maintain good sky visibility.

**Q: Can I edit the start/finish line after recording?**  
A: Not currently. You set the line before recording, and that's used for that session's lap detection.

**Q: Which circuits come with the app?**  
A: Tap **SELECT TRACK** and you'll see them — that list is always current, and it doesn't need a
connection. Their start/finish lines and track outlines were surveyed properly rather than
captured in a hurry before a session, which is why the app can be more precise about laps there.
More will follow — and anything you save yourself sits in the same list.

**Q: Why should I pick SELECT TRACK instead of just capturing the line again?**  
A: Three reasons, all of them about accuracy. A surveyed line is in the right place — your phone
is at its *worst* at fixing its own position while you stand still, which is exactly what
capturing asks you to do. The app also knows which direction you drive through the start/finish,
so it can tell a lap from a walk across the track. And it knows roughly how long a lap should
take, so it won't show you a "lap" that obviously isn't one.

**Q: Can I edit or delete a circuit I saved?**  
A: Yes — rename or delete any circuit you saved, from the circuit list. The ones that ship with
Trillian can't be changed or removed; they're part of the app.

**Q: I picked a circuit but START is greyed out. Why?**  
A: Your GPS isn't ready yet, or the last fix the phone has is too old to trust. Picking a known
circuit doesn't skip that check — you still need a good, current fix before recording starts.
Hold the phone with a view of the sky and it clears in a moment.

**Q: Does the app work on tablets?**  
A: Yes, any Android device running 8.0+ with GPS hardware should work, though it's optimized for phones.

**Q: How does the AI coaching work?**  
A: We analyze your telemetry data — speed, acceleration, position — and compare your laps to identify areas for improvement. The AI looks for patterns like late braking, inconsistent corner entry, and speed scrubbing.

**Q: Is my data private?**  
A: Yes. In this version your sessions never leave your phone unless you deliberately share or export them, and the name you enter is stored locally only. We don't share individual data with third parties.

**Q: The corner numbers in the Analysis tab don't match the circuit's map. Why?**  
A: Trillian works out corners from the way your car actually changed direction, and numbers them in the order you drove through them. It has no knowledge of the circuit's official layout — a chicane the circuit calls one corner may show up as two, and a gentle kink they number may not register at all.

**Q: Does the Analysis tab use my phone's motion sensors?**  
A: No. The g figures come from how fast your GPS speed drops. Your phone's accelerometer measures whatever direction the phone is pointing, which depends on your mount — so it can't reliably tell braking from cornering. GPS speed is independent of how the phone sits in the car.

**Q: Why is my average speed so low?**  
A: It's the average over the entire recording, including the time you spent stationary in the paddock or queuing at the pit exit. It's a session statistic, not a driving one — look at the best lap and the speed graph for that.

**Q: Does the Analysis tab need internet?**  
A: No. The map is drawn from your own GPS trace, not downloaded, so the whole tab works in flight mode.

---

## Need More Help?

If you're still having issues:

1. Check for app updates
2. Review this troubleshooting section
3. Contact support with:
   - Your email address
   - Device model and Android version
   - **The Driving Coach version** (see below)
   - Description of the issue
   - Screenshots if helpful

### Finding your app version

Open **Profile → About Trillian**. The version is shown near the bottom of the screen, for
example `2.92 (292)`. Please quote the whole thing, including the number in brackets — it tells
us exactly which build you have.

(You can also find it the long way round, via **Settings → Apps → Driving Coach**, but the
About screen is quicker.)

This tells support exactly which build you're running, which is often the fastest way to identify a known issue.

---

*Happy lapping! 🏁*
