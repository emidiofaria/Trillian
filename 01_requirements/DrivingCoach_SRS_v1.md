# Trillian — Driving Coach SRS
**Document ID:** DC-SRS-001  
**Version:** 1.0  
**Status:** Approved — Ready for coding  
**Platform:** Android (phone-only)  
**Design principle:** All analysis is post-session. No real-time coaching.

---

## Table of contents

1. [Architecture decisions](#1-architecture-decisions)
2. [User management](#2-user-management) — *deferred to V2*
2a. [Driver profile (V1 local)](#2a-driver-profile-v1-local)
3. [Onboarding and permissions](#3-onboarding-and-permissions)
4. [Application startup and branding](#3a-application-startup-and-branding)
4. [Track setup — start/finish line](#4-track-setup--startfinish-line)
4a. [Track library — pre-defined and saved circuits](#4a-track-library--pre-defined-and-saved-circuits)
5. [Session recording](#5-session-recording)
6. [Telemetry capture](#6-telemetry-capture)
7. [Telemetry storage and upload](#7-telemetry-storage-and-upload)
8. [Lap detection](#8-lap-detection)
9. [Lap comparison](#9-lap-comparison)
9a. [Session analysis (ANALYSIS tab)](#9a-session-analysis-analysis-tab)
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
| AD-04 | Authentication provider | **V1: none.** The app runs single-user and offline-first with a local driver profile only (see §2a). Firebase Authentication — email/password, extensible to Google/Apple — is deferred to V2. |
| AD-05 | Android language | Kotlin, min SDK 26 (Android 8.0), target SDK 35 |
| AD-06 | Backend language | Node.js 20, TypeScript 5.x, Express |
| AD-07 | Dependency injection | Hilt (Android) |
| AD-08 | Local database | Room (Android) backed by SQLite |
| AD-09 | Network client | Retrofit 2 + OkHttp 4 (Android) |
| AD-10 | Background upload | WorkManager with exponential backoff |

---

## 2. User management

> **Status: DEFERRED TO V2.** Requirements UM-01 … UM-17 describe the Firebase
> account model and are **not implemented in V1**. V1 ships without a backend and
> without accounts; local identity is specified in [§2a Driver profile](#2a-driver-profile-v1-local).
> `LoginFragment` and `RegisterFragment` remain in the codebase as unreachable
> placeholders so the V2 flow can be restored without re-plumbing navigation.
> UM-18 and UM-19 (§2.5 User profile) **are** in force in V1, as amended below.

### 2.1 Registration *(deferred to V2)*

| ID | Requirement |
|---|---|
| UM-01 | The app shall allow a new user to register with an email address, password, and display name. |
| UM-02 | Registration shall be handled entirely by Firebase Authentication SDK on the Android client; the backend shall not expose a `/register` endpoint. |
| UM-03 | Password minimum length shall be 8 characters, enforced client-side before submitting to Firebase. |
| UM-04 | The display name shall be a minimum of 2 characters and a maximum of 100 characters. |
| UM-05 | On successful registration, the user shall be automatically signed in and directed to the Home screen with the back stack cleared. |
| UM-06 | On registration failure (e.g. email already in use), the app shall display the Firebase error message as a Snackbar. |

### 2.2 Login *(deferred to V2)*

| ID | Requirement |
|---|---|
| UM-07 | The app shall allow an existing user to sign in with email and password via Firebase Authentication. |
| UM-08 | On successful login, the user shall be directed to the Home screen with the back stack cleared. |
| UM-09 | On login failure, the app shall display a Snackbar with a clear error message; the password field shall not be cleared. |
| UM-10 | While a login or registration request is in flight, the submit button shall be disabled and a circular progress indicator shall be visible. |
| UM-11 | The email field shall use `inputType=textEmailAddress`. The password field shall use `inputType=textPassword` with a visibility toggle icon. |

### 2.3 Session management *(deferred to V2)*

| ID | Requirement |
|---|---|
| UM-12 | Firebase ID tokens shall be attached automatically to every backend API request via an OkHttp interceptor (`FirebaseAuthInterceptor`). |
| UM-13 | The Firebase SDK shall handle token refresh transparently; the app shall not implement custom token refresh logic. |
| UM-14 | If the backend returns HTTP 401, the app shall emit a `sessionExpired` event, display a Snackbar 'Session expired — please sign in again', and navigate to the Login screen, clearing the back stack. |
| UM-15 | The backend shall verify all incoming Firebase ID tokens using the Firebase Admin SDK (`admin.auth().verifyIdToken()`). Requests with a missing, invalid, or expired token shall receive HTTP 401. |

### 2.4 Sign out *(deferred to V2 — superseded in V1 by DR-06/DR-07)*

| ID | Requirement |
|---|---|
| UM-16 | The app shall allow the user to sign out from the Profile screen. |
| UM-17 | On sign out, the app shall call `FirebaseAuth.signOut()`, delete all local Room DB records belonging to the current Firebase UID, and navigate to the Login screen. |

### 2.5 User profile

| ID | Requirement |
|---|---|
| UM-18 | The Profile screen shall display the user's display name, total session count, total lap count, and overall best lap time. *(Amended for V1: the email address is not displayed, because V1 has no account and therefore no email. The V2 restoration of accounts shall re-introduce it.)* |
| UM-19 | The user avatar shall be a circle displaying the first two letters of the display name on a brand blue (`#1C69D4`) background. |

---

## 2a. Driver profile (V1 local)

V1 has no backend and no authentication. Identity exists only to personalise the app:
the driver tells Trillian what to call them, and Trillian remembers it. All telemetry is
keyed to the fixed single-user identifier `demo_user` (DP-01) and is deliberately **not**
keyed to the display name, so renaming never orphans session history.

| ID | Requirement |
|---|---|
| DR-01 | After onboarding completes, and before the Home screen is reachable, the app shall present a Driver Name screen asking the driver for a display name. |
| DR-02 | The display name shall be a minimum of 2 and a maximum of 100 characters after trimming, consistent with UM-04. The primary action shall remain disabled while the entered name is invalid. |
| DR-03 | The entered name shall be trimmed of leading, trailing, and repeated internal whitespace before being persisted. |
| DR-04 | The display name and a completion flag shall be persisted in `DataStore<Preferences>` under the keys `user_name` and `driver_profile_complete`, written atomically in a single edit. |
| DR-05 | On every subsequent launch the app shall read the persisted profile and navigate directly to Home without asking for the name again. The startup destination shall be resolved as: onboarding incomplete → Onboarding; profile incomplete → Driver Name; otherwise → Home. |
| DR-06 | The driver shall be able to change the display name at any time from the Profile screen. The same validation as DR-02 and DR-03 shall apply. Renaming shall not modify, delete, or re-key any recorded session, lap, or insight. |
| DR-07 | The Profile screen shall offer a **Clear User Data** action, replacing the V1 sign-out. It shall require an explicit destructive confirmation, and on confirmation shall delete all Room records, delete every telemetry file referenced by a stored `rawFilePath`, clear all preferences, and return the driver to Onboarding. |
| DR-08 | V1 shall not present any authentication or demo-mode affordance. The Driver Name screen's primary action shall read **LET'S RACE!!**, and shall state that signing in arrives in a future version. |

**Traceability note (V2):** when Firebase authentication is restored, DR-01 … DR-05 are
superseded by UM-01 … UM-13, and the local profile becomes the offline cache of the
authenticated account rather than the sole source of identity.

---

## 3. Onboarding and permissions

| ID | Requirement |
|---|---|
| ON-01 | On first launch, the app shall display the branded loading screen, followed by the onboarding screen before any other functional screen. |
| ON-02 | Onboarding shall consist of three information pages presented in a ViewPager2: Location tracking, Motion analysis, Data privacy. |
| ON-03 | The onboarding screen shall have a single 'GRANT PERMISSIONS & START' button that requests the following required permissions: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, and alongside them the optional `POST_NOTIFICATIONS` (NF-14). *(Amended 2026-09-17: `ACTIVITY_RECOGNITION` removed. It was requested as a required permission and blocked onboarding until granted, but no code ever called the Activity Recognition API, and the disclosure card claimed it "detects when you are in a vehicle" — a false statement about a sensitive permission. See NF-21.)* |
| ON-04 | If all permissions are granted, the app shall mark onboarding as complete in DataStore and navigate to the Driver Name screen (DR-01). *(Amended in V1: previously the Login screen.)* |
| ON-05 | If any permission is denied, the app shall display a dialog listing the denied permissions and offer an 'OPEN SETTINGS' button that deeplinks to the app's system settings. |
| ON-06 | If the user taps 'SKIP' after a denial, the app shall still mark onboarding as complete and navigate to the Driver Name screen. The missing permission will be re-requested when recording starts. |
| ON-07 | Onboarding shall only be shown once. On all subsequent launches, the app shall skip directly to the Driver Name screen, or to Home once a driver profile exists (DR-05). |
| ON-08 | Onboarding completion state shall be persisted in `DataStore<Preferences>` with key `onboarding_complete`. |

---

## 3a. Application startup and branding

| ID | Requirement |
|---|---|
| UI-01 | On cold start the app shall display a branded loading screen showing the Trillian emblem, wordmark, kicker, tagline, engineering manifesto, and a determinate progress indicator reflecting actual initialisation progress. |
| UI-02 | The loading screen shall be displayed for a minimum of 1200 ms and shall be dismissible early by tapping anywhere on it. On the first 3 launches of an installation the minimum display time shall instead be 4000 ms, so the engineering manifesto can be read at least once. The minimum display time shall be measured from the moment the loading screen becomes visible to the user — that is, from the removal of the platform splash window — and not from the start of initialisation. The screen shall always dismiss itself automatically; a tap shall never be required to proceed. |
| UI-03 | If essential startup state (preferences) cannot be read within 8000 ms, the app shall proceed to Onboarding rather than blocking. Onboarding is the safe default because it is idempotent and still grants required permissions, whereas skipping it can leave the app unable to record. |
| UI-07 | Non-essential startup warm-up (database open, pending-upload lookup) shall be bounded independently at 2000 ms and shall not influence the startup destination. |
| UI-04 | Startup state resolution (onboarding flag, authentication token, pending uploads) shall be performed off the main thread. The app shall not block the main thread during startup. |
| UI-05 | The loading screen shall not remain on the navigation back stack; pressing back from the first functional screen shall exit the app. |
| UI-06 | The Home screen shall display a collapsing brand hero (emblem, wordmark, kicker) that collapses to a pinned bar on scroll, with the Start Session control remaining visible at all times. |
| UI-08 | The helmet emblem shall be supplied as a single `@drawable/ic_helmet_emblem` resource across all density buckets, and shall render undistorted and uncropped at every size it is displayed (132dp splash, 88dp Home hero, 36dp collapsed brand bar). |
| UI-09 | Brand artwork shall be verified by measurement of the asset itself — square canvas, content aspect ratio 1.00 ± 0.05, content centred within 3 % of each axis, and a fully transparent border — rather than by presence-only assertions. |
| UI-10 | The loading screen shall display a visible hint indicating that it can be tapped. The hint shall read "Tap to continue" while the introduction window is active and "Tap to skip" thereafter. An accessibility label alone shall not satisfy this requirement. |
| UI-11 | The engineering manifesto and brand identity shall have a permanent home in an About screen reachable from Profile, so that they remain readable independently of the transient loading screen. |
| UI-12 | The About screen shall display the build identity (version name and version code) read from `BuildConfig`, so that a user-reported version is derived from the same source of truth that names the APK. |

---

## 4. Track setup — start/finish line

| ID | Requirement |
|---|---|
| TS-01 | Before starting a recording, the user shall define a start/finish line by capturing two GPS points at the track edges. |
| TS-02 | The Track Setup screen shall display instructions, GPS status, Point A/B capture cards, line width display, and action buttons. |
| TS-03 | The GPS status indicator shall display satellite count and accuracy (e.g., "GPS: 8 satellites, ±3m"). |
| TS-04 | A GPS indicator dot shall be green when accuracy ≤10m (ready) or amber when >10m (acquiring). |
| TS-05 | Point A shall be captured by tapping the 'CAPTURE' button on the Point A card while standing at the left edge of the track. |
| TS-06 | After Point A is captured, its coordinates shall be displayed (e.g., "48.12345, 11.56789") and the Point B capture button shall be enabled. |
| TS-07 | Point B shall be captured by tapping the 'CAPTURE' button on the Point B card while standing at the right edge of the track. |
| TS-08 | After both points are captured, the LINE WIDTH shall be displayed in metres using haversine distance calculation. |
| TS-09 | The line shall be validated: the distance between Point A and Point B shall be at least 3 metres. |
| TS-10 | If the distance is less than 3 metres, a hint shall display "Minimum 3m required" and the START RECORDING button shall remain disabled. |
| TS-11 | A 'CLEAR' button shall appear after any point is captured and shall reset both points to allow re-capture. |
| TS-12 | The 'START RECORDING' button shall remain disabled until both points are captured and the line passes validation (≥3m). |
| TS-13 | On 'START RECORDING', the two lat/lng pairs (startLineLat1, startLineLng1, startLineLat2, startLineLng2) shall be stored in the `SessionEntity` in Room. |
| TS-14 | The instruction text shall read: "Walk to each edge of the track at the start/finish line and capture two GPS points." |
| TS-15 | The app shall request location updates through `FusedLocationProviderClient` at `PRIORITY_HIGH_ACCURACY` with a 1-second interval, behind the `LocationUpdates` abstraction. Track Setup shall subscribe whenever its view is at least STARTED and unsubscribe when it is not, so that location updates resume after a screen-off, app switch or any other stop/start cycle. |
| TS-16 | The app shall begin acquiring a GPS fix as soon as the Home screen becomes visible, so that the time-to-first-fix elapses while the user is still preparing rather than while standing at the start/finish line. |
| TS-17 | GPS warm-up shall run without reporting its progress on the Home screen. Readiness shall persist across the navigation from Home to Track Setup, so that the state reached during warm-up remains true on arrival at the start line. *(Amended 2026-09-20: Home previously displayed a readiness chip — amber "Acquiring GPS…", green "GPS ready". It was removed because it reported a wait the user could not act on or shorten, on the one screen where there is nothing to do about it. Acquisition behaviour is unchanged; Track Setup, where readiness does gate an action, still reports it.)* |
| TS-18 | GPS warm-up shall be bounded by the user's task rather than by any one screen. It shall stop when the app leaves the foreground, when a recording starts, or after 30 minutes of continuous warm-up, so that the receiver is never held while the user cannot see that it is held, and never held indefinitely. It shall **not** stop merely because the Home screen is no longer visible. *(Amended after Incident 12: the previous wording made the Home screen the stop condition, which meant navigating Home → Track Setup — the single journey warm-up exists to serve — discarded the fix the user had just waited for.)* |
| TS-19 | The app shall record the time-to-first-fix and the time-to-first-accurate-fix (≤10 m) of the most recent acquisition and display them on the About screen, so that GPS acquisition delays reported by users can be diagnosed with measured evidence. |
| TS-20 | Warm-up shall expose readiness only and shall never supply a position to start/finish line capture; captured points shall always come from a live location update that independently satisfies the ≤10 m accuracy gate. |

**Remark on TS-02, TS-03, TS-05, TS-06 and TS-12 — coverage status (2026-09-16).**
These five requirements have **no automated test coverage**. They previously cited
`TrackSetupFragmentTest`, which carried a class-level `@Ignore` and had never executed. Of
its 11 tests, 8 were `isDisplayed()` assertions that could not have failed even with the
screen badly broken, and the claims they backed were overstated in any case — TS-05 ("Point A
shall be captured by tapping CAPTURE") was cited by a test that only checked the button
existed and was clickable, never capturing a point. The class was deleted rather than revived.

TS-15 through TS-20 are unaffected: they are backed by `TrackSetupResubscribeTest`,
`LocationWarmUpTest`, `HomeGpsWarmUpTest` and `HomeViewModelTest`, all of which execute.
Deleted tests are recoverable from git at `0216475`.
| TS-21 | Start/finish line capture shall reject any fix older than 3 seconds, measured on the monotonic clock (`elapsedRealtimeNanos`). Age shall be checked when a fix arrives, at the moment of capture, and bounded at the location request itself (`setMaxUpdateAgeMillis`). Where the age cannot be established — an unset or future timestamp — the fix shall be treated as current, since refusing capture outright is a worse failure than the one being prevented and the ≤10 m accuracy gate still applies. |
| TS-22 | While only stale fixes are held, the Track Setup screen shall withdraw capture and display "Getting a current GPS fix…", distinct from "Acquiring GPS…". This state shall clear automatically on the next current fix and shall never require the user to leave and re-enter the screen. |
| TS-23 | The Track Setup screen shall discard the position it is holding whenever its location collector restarts, because a fix retained across a screen-off or app switch describes where the user was rather than where they are. |

---

## 4a. Track library — pre-defined and saved circuits

§4 describes capturing a start/finish line at the track edge, which remains the path for any
circuit the app has never seen. This section adds the other path: a circuit the app already
knows, either because it ships with the app or because the driver saved one they captured.

The motivation is measured, not cosmetic. A phone fixes its own position worst while standing
still (see FP-STATIONARY-POSITION-BIAS), which is exactly the posture §4 mandates. A circuit
whose geometry was surveyed once, carefully, is better evidence than a line captured in ten
seconds before a session — and it carries information a captured line cannot: which way the
driver travels through the start/finish, and how long a lap there should take.

| ID | Requirement |
|---|---|
| TL-01 | The app shall ship a read-only catalogue of pre-defined circuits as a bundled asset (`assets/tracks/tracks.json`). The catalogue shall be available with no network connection and shall never be fetched, updated or synchronised at runtime. |
| TL-02 | After the driver names a session, the app shall offer a choice between **SELECT TRACK** and **NEW CIRCUIT**. Choosing NEW CIRCUIT shall lead to the Track Setup screen (§4) with its behaviour unchanged. |
| TL-03 | SELECT TRACK shall present a single list containing both bundled circuits and circuits the driver has saved, each showing its name, location and start/finish line length. |
| TL-04 | A catalogue entry shall carry: a stable identifier, a display name, a location, a start/finish line as two coordinate pairs, the direction of travel through that line in degrees, the lap length in metres, the corner count, a fastest/slowest plausible lap time envelope, and an ordered closed centreline. The slowest lap figure is advisory: it is shown to the driver as the expected lap window (TL-06) and is not used to discard laps — see LD-22. |
| TL-05 | Provenance shall be recorded **per dataset, not per circuit**. The start/finish line and the centreline of the same circuit may have been obtained by different means, and the catalogue shall say which for each. A circuit shall never carry a single blanket provenance claim that is untrue of one of its datasets. |
| TL-06 | Selecting a circuit shall present a confirmation screen stating the circuit's name, start/finish line length, lap length and expected lap window, and shall apply the same GPS readiness gate as Track Setup (TS-04, TS-21, TS-22) before recording may start. Selecting a known circuit shall not become a way to bypass the readiness checks that capturing one enforces. |
| TL-07 | The start/finish line of a selected circuit shall be resolved from the repository by identifier at the moment recording starts. It shall not be passed between screens as a coordinate, because navigation arguments are 32-bit floats and would quantise a surveyed coordinate to roughly half a metre — discarding the precision that is the entire reason for having surveyed it. |
| TL-08 | A session recorded against a catalogue circuit shall persist that circuit's identifier alongside the start/finish line it used. A session recorded against a captured line shall persist no identifier. |
| TL-09 | After a driver captures a new start/finish line, the app shall offer to save it as a reusable circuit before recording begins. Declining shall start the recording exactly as before. |
| TL-10 | A circuit shall not be saved with a start/finish line shorter than 3 metres, consistent with TS-09. |
| TL-11 | Saved circuits shall be renameable and deletable. Bundled circuits shall be neither, because they are an asset of the build rather than user data. |
| TL-12 | V1 shall present the circuit list as a plain manual list. It shall not filter or reorder by the device's current position, and shall not require a location fix to be browsed. |
| TL-13 | Recording a session shall never modify a circuit's geometry. The only field a recording may update is the circuit's last-used timestamp. |
| TL-14 | If the bundled catalogue asset is missing or cannot be parsed, the app shall behave as though the catalogue were empty and shall log the failure. It shall not crash, and NEW CIRCUIT shall remain fully usable. |
| TL-15 | Where a recorded session of a circuit exists, that circuit's `travelHeadingDeg`, `lengthM` and lap time envelope shall be corroborated against it before the circuit ships, and the corroboration shall be held by a test. A figure that cannot be corroborated shall be stated as an estimate rather than presented as a measurement. |
| TL-16 | A bundled circuit shall declare its lap length and its lap time envelope. These are surveyed before the circuit ships, so a bundled entry that omits one shall fail the build's test suite rather than fall back to a default. A circuit saved by a driver has no surveyed length and is exempt. |
| TL-17 | A requirement shall not name a specific circuit. Requirements state rules that hold for every circuit in the catalogue; evidence for an individual circuit belongs in [Annex A](ANNEX_A_circuit_evidence.md). Illustrative material drawn from a recorded incident is not a circuit reference and is not restricted by this requirement. |
| TL-18 | Every circuit in the bundled catalogue shall have a corresponding entry in Annex A, and Annex A shall describe no circuit absent from the catalogue. A circuit added without its evidence shall fail the build's test suite. |
| TL-19 | Where a circuit ships a centreline of at least 8 points, the app shall be able to express any GPS fix as a **station** on that circuit: `s`, the distance travelled from the start/finish along the centreline, and `d`, the signed lateral offset from it. Comparing two laps by elapsed time is circular — "20 seconds in" is a different place on a fast lap than on a slow one — whereas `s` makes "the same place on both laps" expressible. |
| TL-20 | A missing or too-thin centreline shall yield no station rather than an error. A driver who captured their own start/finish line has no centreline at all (TL-01), and the correct response is to offer fewer features, not to fail. |
| TL-21 | The station projection shall **not** be used by lap detection. Detection has its own crossing geometry, it works, and `FP-REIMPLEMENTED-GEOMETRY` records what happens when a second implementation of the same idea is introduced beside the first. |
| TL-22 | `d` shall be treated as valid for comparing one lap against another and **not** as an absolute statement of where the car was on the road. Measured against recorded sessions, lateral repeatability between laps is several times better than the device's absolute accuracy, because much of the error is a slow session-wide bias that cancels when laps are compared to each other. |

**Remark on TL-05 — why provenance is split.**
The start/finish line and the centreline of a circuit are two measurements, and they are
routinely taken by different means: one may be read from map imagery while the other is walked
on foot. Those are different kinds of evidence with different error characteristics, and a
reader deciding whether to trust a number needs to know which one it came from. Where the
surveyor's identity is not known to the app, the field shall be left empty rather than filled
with a plausible guess — an invented provenance is worse than an absent one.

The split still earns its keep on a circuit whose datasets were gathered the same way on the
same afternoon: they remain two measurements taken by two methods, and either could be
re-surveyed without the other. Provenance that happens to read the same for both datasets
today is not a reason to collapse it into one field tomorrow.

**Remark on TL-04 and TL-15 — the priors are corroborated against a recorded session.**
Two fields in a catalogue entry are load-bearing for lap detection, and are otherwise supported
only by the survey that produced them: `travelHeadingDeg` (which LD-19 and LD-20 depend on) and
`lengthM` (which LD-22 depends on). A wrong value in either is silent — a wrong heading rejects
laps that happened, a wrong length either discards real sessions or stops catching the incident
it was written for. Neither failure announces itself; both reach the driver as an empty or
truncated session.

A circuit's priors are therefore cross-checked against a recording of that circuit before it
ships. The recording must **predate the catalogue entry**, because a session used to derive the
entry can only ever agree with it; agreement is evidence exactly to the extent that the two were
produced independently. Where the measured lap distance exceeds the surveyed distance, that is
expected rather than an error: summing distances between consecutive 1 Hz fixes over-reads,
because each fix carries its own error and the sum accumulates a random walk on top of the true
path. The opposite can also happen — 1 Hz fixes chord across tight corners, and a racing line is
shorter than a mid-track walk — so length agreement is asserted as a band, not as a direction.
Where `lengthM` was itself measured from the corroborating session, that session cannot
corroborate it; the independent walked centreline does, within a stated percentage tolerance.

Where the corridor margin is wide, a reversed heading may not lose any laps: the detector falls
back to the first-crossing reference (LD-14) when the catalogued one yields nothing plausible. On
such circuits the heading is verified through the diagnostic `headingReference`, not through the
lap count.

This is why a corroborated circuit's provenance stays as surveyed rather than being upgraded.
The honest claim is not that the data was gathered from a kart; it is that data gathered on
foot **agrees with** a kart. That is the stronger statement, and it is the one the tests make.

Per-circuit corroboration figures — which session, how close the heading agreed, measured
against surveyed distance — are recorded in [Annex A](ANNEX_A_circuit_evidence.md), §A.2.

**Remark on TL-04 — a lap time envelope is a measurement where a session exists.**
The lap length of a catalogue entry is surveyed. The lap time envelope is typically typed in
from what somebody remembers of the circuit, and an envelope copied from a different circuit, or
recalled optimistically, can be not merely wrong but physically impossible — demanding an
average speed above the fastest speed the circuit has ever produced. Where a recorded session
exists, the envelope shall be checked against it and corrected before the circuit ships.

Such a correction may change nothing observable, because LD-21's derived floor can sit below the
real gap between crossings at either value. It is made anyway, because the figure was untrue,
and because the next driver to lap the circuit faster, or the next change to LD-21, would have
found it. A catalogue entry is a set of claims about a place; claims that happen not to be
load-bearing today are still claims. Instances where this has been applied are recorded in
[Annex A](ANNEX_A_circuit_evidence.md), §A.3.

**Remark on TL-17 and TL-18 — why circuit evidence lives outside this document.**
Earlier drafts justified these requirements by naming the circuits they were derived from, with
each circuit's headings, distances and margins written into the prose. It read well at two
circuits and could not survive twenty. Worse, it made rules and examples indistinguishable: a
reader could not tell which sentences constrained the app and which merely described a place,
and adding a circuit meant editing the specification.

The separation is therefore not cosmetic. A requirement is a claim about the app and is true of
every circuit; a catalogue entry is a claim about one real place. Annex A holds the second kind,
keyed by circuit identifier and tabular by design, so that the hundredth circuit costs a row
rather than a section.

TL-18 is the half with teeth. A register nobody is forced to update is a register that silently
goes stale, and stale evidence is worse than none, because it is still believed. Tying the annex
to the shipped catalogue in the test suite means a circuit cannot be added without its evidence
being added too. TL-17's converse check — that no requirement has acquired a circuit name — is
by comparison a regression guard on a state that is already correct.

Evidence drawn from a recorded incident is deliberately exempt. There will not be a hundred
incident 15s, and the incident is the reason several of these requirements exist at all;
relocating it would leave thresholds stated without the observation that produced them.

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
| SR-10 | The foreground service notification shall display: title 'Driving Coach — Recording', content text 'MM:SS', updating every second. |
| SR-11 | If GPS hardware is not available on the device, the foreground service shall stop itself and post a state indicating GPS is unavailable. |

**Remark on SR-04, SR-05 and SR-09 — coverage status (2026-09-16).**
These three requirements have **no automated test coverage**. They previously cited
`TelemetryForegroundServiceTest`, which carried a class-level `@Ignore` ("requires real GPS
hardware") and had therefore never executed. The class was deleted rather than revived, and
the three claims removed from `coverage-map.tsv`.

Two points worth keeping visible:

- The **SR-05 claim was unfounded regardless** — that class contained no assertion about a
  GPS status indicator at all. Removing it corrects a false claim, not a real loss.
- **SR-09 is the significant gap.** Recording continuing when the app is backgrounded is
  core V1 behaviour, and nothing now verifies it — nor did anything before, since the test
  never ran. The requirement stands; the evidence does not exist.

The test was un-runnable because `TelemetryForegroundService` takes its location stream from
`LocationManager.GPS_PROVIDER` directly instead of the injectable `LocationUpdates` seam the
rest of the app uses (see TS-15). Restoring coverage means putting the service on that seam
so fixes can be scripted, as `StartLineFreshnessTest` already does. Deleted tests are
recoverable from git at `0216475`.

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

> **Remark:** Lap detection runs **entirely on the phone**. Delivery 1 of this app is local-only: there is no backend, and a session is fully processed with the device offline and in flight mode. A backend may be added in a later delivery, at which point these requirements will be revisited; until then, anything in this document describing server-side processing describes a future option, not shipped behaviour.

| ID | Requirement |
|---|---|
| LD-01 | Lap detection shall be performed on the device, after recording stops, from the local JSONL telemetry file. It shall not require network connectivity. |
| LD-02 | When a start/finish line is provided (startLineLat1/Lng1, startLineLat2/Lng2), lap boundaries shall be detected by identifying the moments at which the car passes a **start point**. That start point shall be the **midpoint** of the captured line, except where LD-17 replaces it. |
| LD-03 | When no start/finish line is provided, or either captured endpoint is at latitude 0 and longitude 0, the app shall report that no start line is defined and shall detect no laps. |
| LD-04 | A lap boundary shall be recorded when consecutive GPS samples cross a plane through the start point that is **perpendicular to the car's direction of travel**, and the car passes no further than 15 m to the side of the start point. A pass rejected solely for passing wider than 15 m, while within 60 m of the start point, shall be recorded as a rejection giving its distance to the side, so that the reason for an empty result is never lost. |
| LD-05 | A minimum lap time guard of 20,000 ms shall be enforced between consecutive lap boundaries. |
| LD-06 | The driver must travel at least 50 m from the start point before a lap crossing is counted (prevents false triggers while manoeuvring near the line). |
| LD-07 | The last incomplete lap (started after the final boundary, session ends before re-crossing) shall be discarded. Only complete laps shall be stored. |
| LD-08 | If fewer than 50 telemetry samples are present in the file, the app shall report insufficient telemetry data and detect no laps. |
| LD-09 | If fewer than 2 complete laps are detected, the app shall store no laps and shall tell the user how many laps were found. |
| LD-10 | Each detected lap shall be divided into 3 sectors at one third and two thirds of the **distance the car drove during that lap** (not of its duration). `sector1Ms + sector2Ms + sector3Ms` shall equal `durationMs` exactly (sector 3 absorbs the remainder). Thirds of *duration* move with the driver, so two laps' sectors would describe different stretches of road and any comparison between them would be measuring the misalignment rather than the driving. |
| LD-24 | Sector boundary instants shall be **interpolated** between the two samples either side of the boundary. At the ~1 Hz reference sampling rate, snapping to the nearest fix quantises every boundary to a whole second — a ~4% error on a 25 s sector, far larger than the differences sectors exist to reveal. This is the same reasoning as NF-16 applies to lap boundaries. |
| LD-25 | A lap carrying fewer than 6 telemetry samples, or covering less than 50 m, shall receive **no sectors** (all three remain `0L`) rather than estimated ones. Below these bounds the boundaries would be decided by interpolation alone, and a sector time that is a straight-line guess between two distant fixes is not a measurement. |
| LD-26 | Sectors shall be derived **only after** a candidate lap set has passed the plausibility check and all detection fallbacks have run, so that sectors belonging to a discarded attempt can never reach the driver and detection geometry is provably unaffected. |
| LD-27 | The distance ruler used to divide a lap into sectors shall be anchored at the **interpolated start/finish crossing position** at both ends of the lap, not at the first and last recorded fix inside the lap window. The crossing position shall be recovered from the persisted crossing instant by inverting the interpolation lap detection already performed, rather than by re-deriving the start-line geometry. |
| LD-28 | The minimum-sample floor of LD-25 shall count **recorded fixes only**. The two interpolated anchor points introduced by LD-27 are not measurements and shall not be counted towards it. |
| LD-11 | The lap with the minimum `durationMs` shall be flagged as `isBestLap=true`. Exactly one lap per session shall have this flag set. |
| LD-12 | `SessionEntity.processingStatus` shall progress through: `PENDING → PROCESSING → LAPS_DONE → COMPLETE` on success, or `FAILED` on any error. |
| LD-13 | The lat/lng approximation used for crossing geometry (equirectangular Cartesian) is valid for tracks smaller than 5 km in extent. This is the supported use case. |
| LD-14 | A candidate crossing whose direction of travel differs by more than 60° from the direction of travel at the first accepted crossing of the session shall be rejected. |
| LD-15 | The instant of a lap boundary shall be interpolated between the two GPS samples either side of it, rather than taken from either sample. |
| LD-16 | For each detection run the app shall record, alongside the session's telemetry file, what it observed and why each candidate crossing was accepted or rejected. Failure to record this shall not affect the outcome presented to the user. |
| LD-17 | Where detection against the captured midpoint yields fewer than 2 laps, the app shall retry once against that midpoint projected perpendicularly onto the path the car actually drove, considering only stretches driven at 4 m/s or more. The projected point shall be used only if it lies within **20 m** of the captured midpoint and only if the retry yields at least 2 laps; otherwise the captured midpoint stands and no laps are reported. The start point actually used, and the distance it moved, shall be recorded under LD-16. |
| LD-18 | Where no laps are detected, the message shown to the driver shall describe what the session contained — how many passes were seen, how far to the side they went, and whether the line was captured while stationary — rather than instructing the driver to complete laps they may already have completed. |
| LD-19 | A candidate crossing made below 4 m/s shall be rejected and recorded as `TOO_SLOW`. A person walking across the start/finish — while queueing, pushing a kart, or carrying the phone back to the paddock — is not a lap, and the app shall not treat it as one. |
| LD-20 | Where the session was recorded against a circuit from the track library (§4a), the circuit's direction of travel shall be used as the reference heading for the guard in LD-14, in place of the heading of the session's first accepted crossing. |
| LD-21 | Where the circuit declares a fastest plausible lap time, the minimum lap time guard of LD-05 shall be raised to 80 % of that value. The 20 % grace exists so that a driver who beats the catalogue's figure is not refused their own lap. |
| LD-22 | Where the circuit declares a surveyed lap length, a set of detected laps in which **every** lap implies an average speed below 5 m/s (18 km/h) shall be discarded in its entirety rather than presented. A whole set of laps none of which could have been driven is evidence that the crossings were not laps, and reporting them as laps is worse than reporting nothing. A set containing at least one credible lap shall be presented unaltered, including any individual long lap within it. |
| LD-23 | Where a candidate crossing is rejected for arriving sooner than the minimum gap, and it arrived at least half of the way to that gap, the lap count presented to the driver shall be qualified to say that passes went uncounted and that laps may have been timed as one. Rejections landing well short of the gap are repeat fixes from a single pass and shall not qualify anything. |

**Remark on LD-21 and LD-23 — why a lost lap has to be said out loud.**
LD-21's floor rejects a crossing; it does not end the lap. A driver quicker than the circuit's declared fastest lap therefore has a real boundary discarded, and the next accepted crossing is timed from the one before it, so two laps are presented as one of roughly double the duration. Nothing downstream can detect this. The merged lap sits inside the declared envelope and far above LD-22's floor, so it is indistinguishable from a lap somebody genuinely drove slowly — LD-22 is a check on whether a lap *could* have been driven, and a merged lap could have been. No tightening of it would help, because the two cases are identical in the only evidence LD-22 looks at. The record of the rejection is the one place the difference survives, which is why LD-23 reports from there rather than from the laps. Without it the app answers confidently and wrongly, which is the failure LD-22 was written for arriving by another route.

**Remark on LD-02 and LD-04 — why the *orientation* of the captured line is ignored.**
Track Setup asks the user to capture a point at each edge of the start/finish, typically 5–10 m apart. GPS accuracy on a phone is of the same order (4.8 m mean, 15.0 m worst, measured in incident 13). The *direction* of a line drawn between two points that close together is therefore dominated by measurement noise rather than by where the user stood, and can come out pointing along the track instead of across it. In incident 13 it did exactly that — within 0.1°–5.1° of the direction of travel — and no lap could be detected, because a car driving along a line never crosses it. The app therefore uses the captured points only for their midpoint, which is a *position* and is measurable, and derives the crossing direction from the car's own motion, which is measured over hundreds of metres. See `03_incidents/13_no_laps_detected_start_line_parallel_to_travel/`.

**Remark on LD-04 — a limitation on track topology.**
Because the crossing plane follows the car's direction of travel rather than a fixed line, a circuit that passes through the same point twice per lap in different directions — a figure-of-eight, or a layout where the start/finish is on a bridge over itself — may register two boundaries per lap and report lap times about half their true value. LD-14 rejects the most common form of this (a return pass in the opposite direction), but a crossing at, say, 70° would be accepted. No venue currently in use has this geometry. If lap times look roughly half what was driven, this is the reason.

**Remark on LD-06 — why 50 m and not more.**
The value must be smaller than the shortest lap the app is expected to support. Kart circuits used for testing are around 800 m, so 50 m is comfortably below a lap while still being far enough to clear the manoeuvring that happens around the start/finish before a session begins. An earlier value of 200 m was specified but never implemented; the shipped value has always been 50 m.

**Remark on LD-19 — why 4 m/s, and why this is not a tuned number.**
In the incident 15 session the two populations are separated by a factor of four: the driver's
16-minute wait in the queue beside the start straight never exceeded 3 m/s, and no racing
crossing fell below 5.9 m/s. Any threshold between those two recovers all 12 laps. The value
chosen is the one the detector already uses for `MIN_ANCHOR_SPEED_MS` (LD-17), because both
encode the same judgement — below 4 m/s this is a pedestrian, not a vehicle — and a second
number expressing the same idea would be a number to keep in step for no benefit.

**Remark on LD-20 — why a *catalogued* heading is worth more than a measured one.**
LD-14 takes its reference from the first accepted crossing, which means the first crossing is
the one thing it cannot guard. In incident 15 that crossing was a 2.9 m/s walk across the start
point at 272°; the 12 racing crossings arrived at 331–343°, and ten of them were then rejected
for disagreeing with a pedestrian. A heading that comes from the circuit rather than from the
session is fixed before the first sample is read, so it guards every crossing including the
first.

It matters most where a circuit's own geometry re-enters the detection corridor. The detector
accepts fixes within a fixed 15 m half-width of the start/finish midpoint, and on a compact
circuit another part of the track can pass close enough to that point that the corridor alone
does not separate the two — on a device reporting ±6 m, the heading guard is then the whole
margin between a correct lap count and a double count (FP-LAP-DOUBLE-COUNT).

This is not a theoretical concern. Where the margin is narrow, replaying a real session with the
catalogued heading reversed 180° — the single likeliest data-entry error, and the exact shape of
incident 15 — yields **zero laps**, no error, and a driver shown an empty session. A circuit
whose direction prior is load-bearing in this way shall keep that mutation as a permanent
assertion, so the guard cannot be weakened without a test failing. Per-circuit corridor margins
are recorded in [Annex A](ANNEX_A_circuit_evidence.md), §A.2.

**Remark on LD-22 — why the guard is derived from the surveyed length and not from the declared envelope.**
A catalogue entry carries two kinds of number. The lap length was *measured*, by walking the
circuit and recording waypoints. The lap time envelope was *typed in* from what somebody
remembers of the circuit. An earlier version of LD-22 measured detected laps against the
envelope, which put a human estimate in a position to delete real data: a circuit shipped with
an upper bound of 90 s would discard anything over 135 s, so a timid weekend driver lapping in
150 s would have had every lap thrown away and been shown nothing at all — the guard meant to
protect them erasing their session in silence. Deriving the test from the surveyed length
removes the estimate from the decision. The 5 m/s floor is the same judgement as LD-19's 4 m/s,
averaged over a lap rather than sampled at a point.

A session may legitimately contain a lap slower than that floor — an out-lap, driven while the
kart is still finding the circuit, is the usual case. It is discarded, and correctly so, but it
is worth writing down that this is the guard working rather than a defect, because "the app
dropped my first lap" is exactly the shape of a report that invites a fix to the wrong thing.
Flying laps sit an order of magnitude above the floor and are nowhere near it. Per-circuit
floors are recorded in [Annex A](ANNEX_A_circuit_evidence.md), §A.2.

**Remark on LD-22 — why the whole set must fail before anything is discarded.**
A single long lap among normal ones is a *real* lap: a spin, an off, or a slow kart ahead.
Discarding it would be editing the driver's session to make the chart tidy, and losing the one
lap they most want to look at. What incident 15 produced was different in kind — two "laps" of
954 s and 404 s, averaging 1.07 and 2.52 m/s over 1020 m, with nothing credible among them.
That is the shape LD-22 looks for: not an implausible lap, but a set in which no lap at all
could have been driven. A circuit with no surveyed length carries no such guard, so an
uncatalogued session behaves exactly as it did before.

**Remark on the relationship between LD-19 and §4a — the fix is not conditional on the library.**
LD-19 is deliberately specified independently of the track library, and is verified
independently: on the incident 15 telemetry the speed gate alone recovers all 12 laps *against
the driver's own mis-captured start line*, 164.6 m from the real one. The library improves
detection; it is not required for it. A circuit the app has never seen gets the same correction.


Incident 09 reported "no laps detected" and was closed without a cause, at 55% confidence, because nothing survived the run except the message shown to the user. Incident 13 had the same symptom two versions later and was only explicable because its raw telemetry happened to be kept by hand. Recording the detector's reasoning makes the next occurrence answerable from the session itself.

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
| LC-09 | The Session Result screen shall display a 'CHART' tab with a speed trace (km/h vs distance in meters) for all laps. All laps shall be plotted in grey (#B0B0B0 at 50% opacity), the best lap in brand primary colour with 3dp line width. |
| LC-10 | Speed values in the chart shall be converted from m/s to km/h using the factor 3.6. |
| LC-11 | The chart X-axis shall display distance in meters from lap start (e.g., "450m"), computed using cumulative haversine distance between GPS samples. |
| LC-12 | The chart shall read real telemetry data from the session's JSONL file, filtered by lap start/end timestamps. |
| LC-13 | For sessions with more than 10 laps, the app shall display a warning: "Offline processing is limited and may take some time." |
| LC-14 | For sessions with large telemetry data, the app shall prompt the user to choose between "Fast" (downsampled to ~100 points per lap) or "Detailed" (all samples) processing modes. |
| LC-15 | In Fast mode, samples shall be uniformly downsampled to approximately 100 points per lap to optimize rendering performance. |

---

## 9a. Session analysis (ANALYSIS tab)

All analysis is derived from data the app already holds: the session's telemetry file and the
laps produced by lap detection (§8). The tab is read-only, writes nothing, and shall work with
no network connection of any kind.

| ID | Requirement |
|---|---|
| AS-01 | The Session Result screen shall display a fourth tab labelled 'ANALYSIS', positioned after 'CHART'. |
| AS-02 | The ANALYSIS tab shall display session statistics: total distance (km), session duration (M:SS), maximum speed (km/h), average speed (km/h) and best lap time. |
| AS-03 | Total distance shall be the cumulative haversine distance between consecutive GPS samples over the whole session. Average speed shall be the mean speed over the whole recording, standing time included, and the tab shall state this. |
| AS-04 | The ANALYSIS tab shall display a track map drawn from the recorded GPS trace. The map shall not use any map SDK, map tiles or network service. |
| AS-05 | The track map shall colour the trace by speed on a continuous gradient from `#1C69D4` (slowest) through `#F39C12` to `#2ECC71` (fastest), overdraw detected braking zones in red (`#E74C3C`), mark each detected corner with a numbered `T1..Tn` label, and mark the start/finish line when the session has one. |
| AS-06 | The ANALYSIS tab shall list the session's laps as selectable chips showing lap number and lap time, taken from the laps produced by §8. Laps shall never be re-derived by the analysis. |
| AS-07 | The reference lap shall default to the best lap, and the user shall be able to select any other lap. Selecting a lap shall recompute the track map, the corner table and the braking table for that lap, and the tab shall state which lap the tables describe. |
| AS-08 | Corners shall be detected from the rate of change of GPS heading: a corner is a stretch where the smoothed yaw rate exceeds 6 °/s for at least 1.5 s. For each corner the app shall display its number, turn direction, total heading change and apex (minimum) speed. |
| AS-09 | Corner numbering shall follow the order in which corners are passed and the tab shall state that this numbering does not necessarily match the circuit's official numbering. |
| AS-10 | Braking zones shall be detected from GPS speed only: a zone is a stretch where longitudinal deceleration exceeds 0.8 m/s² for at least 0.5 s and speed falls by at least 4 km/h. For each zone the app shall display entry and exit speed, speed lost, peak deceleration expressed in g (a/9.81) and duration. |
| AS-11 | The tab shall state that braking figures come from GPS speed and that the phone accelerometer is deliberately not used, because its axes depend on how the phone is mounted and do not reliably track longitudinal acceleration. |
| AS-12 | Each braking zone shall be associated with the corner it leads into, where one exists. |
| AS-13 | The ANALYSIS tab shall display a speed-versus-time graph covering the whole session, not only the reference lap. |
| AS-14 | Corner and braking detection thresholds shall be expressed per unit of time, so that the same recording analysed at 1 Hz or 10 Hz yields the same corners and braking zones. |
| AS-15 | A heading shall not be derived from GPS positions less than 2 m apart, and a detected corner whose maximum speed is below 10 km/h shall be discarded, so that GPS scatter recorded while the vehicle is stationary cannot be reported as cornering. |
| AS-16 | When the session's telemetry file is missing or unreadable, the tab shall say so explicitly and shall not attribute the failure to the driving. When fewer than two usable samples exist, the tab shall say the session is too short to analyse. |
| AS-17 | When no lap is usable as a reference — no laps detected, or the lap's time window does not overlap the telemetry — the analysis shall fall back to the whole session and shall label itself as such, rather than presenting whole-session figures under a lap's name. |
| AS-18 | Where the session was recorded against a circuit that declares a centreline (§4a), session statistics (AS-02) and the speed-versus-time graph (AS-13) shall be computed only from samples lying within 15 m of that centreline, so that time spent in the paddock or the queue does not enter the session's distance, duration or average speed. The filter shall stand down and use all samples if it would retain fewer than 30 % of them, since a centreline that excludes most of a session is more likely to be describing a different session than a driver who never went on track. The filter shall **not** be applied to lap detection, the track map or corner detection: laps are decided by crossings, and filtering samples near the start/finish would change lap times in order to tidy a chart. |

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

### 10.1 Offline Coaching (Local Insights)

| ID | Requirement |
|---|---|
| OC-01 | When offline or backend unavailable, the app shall generate local coaching insights immediately after local lap detection completes. |
| OC-02 | Offline coaching shall produce a **variable number of insights (1–7)**, emitting only those the session's data supports: Best Lap, Top Speed (telemetry required), Consistency, Dream Lap, Sector Diagnostic, Outlier Lap, Pace Trend. An insight that cannot be substantiated shall be omitted entirely. *(Amended: the previous "exactly 3-4" quota is what required a slot to be filled whether or not anything was known, which is how the fabricated OC-08 upsell came to ship. The engine must be permitted to stay silent.)* |
| OC-03 | The Best Lap insight shall show "Lap N Was Your Fastest" with time delta vs average. Sector detail shall only be shown if all laps have non-zero sector times. |
| OC-04 | If sector times are unavailable (sector*Ms = 0), the Best Lap insight shall NOT reference specific sectors. Instead, it shall show "X.Xs ahead of average". |
| OC-05 | The Top Speed insight shall read telemetry JSONL, extract maximum speed, and display "🚀 Top Speed: X km/h — Hit on Lap N". |
| OC-06 | GPS noise shall be filtered: speeds > 350 km/h (97.2 m/s) shall be rejected as implausible. |
| OC-07 | The Consistency insight shall show "Laps within X.Xs of each other" (using stdDev), not "vary by". |
| OC-08 | **(Superseded — behaviour removed.)** The "Sector Focus — Coming Soon" upsell shall **not** be displayed. When sectors are unavailable the app shall show no sector insight at all. The ID is retained rather than renumbered to preserve traceability: it records that an advertisement was once presented to the driver in a coaching slot, and that this is now prohibited. |
| OC-11 | Each lap's sector times shall be persisted to `LapEntity.sector1Ms/2Ms/3Ms` as derived by LD-10. No schema change is required; the columns already existed and were previously written as `0L`. |
| OC-12 | The app shall compute a **Dream Lap** by stitching the fastest sector 1, sector 2 and sector 3 recorded anywhere in the session, and shall report the time gained against the fastest lap actually driven. |
| OC-13 | The Dream Lap shall be **suppressed entirely** unless all of the following hold: at least 3 laps carry believable sectors; every sector sums with its siblings to its own lap duration; no sector is below 10% or above 75% of its lap; the stitched total does not exceed the fastest real lap; and the gain does not exceed 25% of that lap. A minimum actively selects for the worst data in a set, so an ungated Dream Lap would preferentially present detection artefacts as achievements. |
| OC-14 | The Dream Lap shall be suppressed whenever `MergedLapCaveat` has fired for the session. A lap that may be two laps reported as one cannot yield a meaningful sector, and this cannot be detected from the sector times alone — the signal shall therefore be passed in from lap detection rather than inferred. |
| OC-15 | When a single lap holds all three fastest sectors, the Dream Lap shall report that lap as the driver's complete lap with a gain of zero, rather than being suppressed. "There was nothing left on the table" is a real and useful answer. |
| OC-16 | The **Sector Diagnostic** insight shall identify the sector with the largest aggregate time loss against that sector's best, or report that all three are strong. It shall never be shown when sectors are absent. |
| OC-17 | The **Outlier Lap** insight shall compare each lap against the **median** lap time (not the mean) and shall require at least 4 laps and a deviation of at least 15%. A mean is dragged toward the outlier by the outlier itself, which is precisely how a bad lap escapes a mean-based test. |
| OC-18 | The **Pace Trend** insight shall compare the first and second halves of a session of at least 6 laps, and shall be shown only when the difference exceeds 2%. |
| OC-19 | The COACH tab shall display a caveat stating that sectors are the app's own equal-distance thirds and not the circuit's official sectors, shown only when the session's laps actually carry sector times. |
| OC-20 | The COACH tab shall display a **sector map** pinned above the insights: the circuit's shape drawn as a closed outline, divided into three contiguously coloured regions corresponding to sectors 1, 2 and 3, with the start/finish marked. An insight naming a sector is an instruction about a place, and without the map the driver is told where they lost time in a vocabulary that points at nothing they can see. |
| OC-21 | Where the session was recorded against a circuit from the track library, the map shall be drawn from that circuit's **surveyed centreline**, rotated so that the start/finish line is the first drawn point. A catalogue centreline does not begin at the start/finish — measured origins are 400.0 m, 770.9 m and 4.6 m into the three shipped circuits — so the rotation is what makes the surveyed and derived shapes share one convention. |
| OC-22 | Where no surveyed centreline is available, the map shall be derived from the driver's own laps: each lap resampled at 240 equal fractions of its own distance, and the **per-fraction median** taken across laps. The median is used rather than the mean so that one lap off-line — a spin, a gravel excursion, a lost fix — cannot leave a bulge in a shape the driver never drove. |
| OC-23 | A derived map shall be computed in **normalised-distance space**, the same space in which sectors are defined, so that the sector boundaries fall at exactly one third and two thirds of the drawn shape by construction rather than by approximation. |
| OC-24 | A map derived from fewer than 3 laps shall be drawn from the single best lap and labelled as such. A map whose lap-to-lap spread exceeds 25 m, or which is derived from laps shorter than the 50 m floor of LD-25, shall not be drawn at all. |
| OC-25 | Where a surveyed centreline is available but the driver's laps sit further than 60 m from it, measured as the **median** lateral distance, the centreline shall be rejected as belonging to a different circuit and the derived shape used instead. The measure is lateral distance and not the ordering of the sector boundaries, because that ordering legitimately wraps whenever the start/finish falls late in the centreline's own numbering, which is the common case. |
| OC-26 | The map shall state its provenance beneath itself whenever that changes what it can be trusted for: a shape derived from laps shall say that it follows the line driven rather than the edges of the road, and a single-lap shape shall say so more strongly. A surveyed shape needs no such note. |
| OC-27 | Where no map can be drawn honestly, the map shall be **hidden entirely** and the insights shall be shown unaltered. A shape the driver cannot recognise is worse than no shape, because it invites them to read corners into GPS noise. The insights do not depend on telemetry still being readable and shall not be withheld with the picture. |
| OC-28 | The Dream Lap shall **never** be drawn as a path on the map. Its three sectors come from three different laps, and a continuous line through them would depict a trajectory nobody drove. |
| OC-29 | The map shall be computed off the main thread and shall not delay the insights, which are already persisted as text and require no telemetry to display. |
| OC-30 | The start/finish marker on the map shall be drawn at the start/finish line itself. On a derived outline this follows from LD-27; on a surveyed centreline it follows from the rotation of OC-21. The two shall agree. |
| OC-31 | When a session is opened, its stored sector times shall be recomputed from its telemetry and rewritten **only where they differ** from the stored values. This includes laps stored with no sector times at all: a session recorded before sectors existed, whose telemetry is still present, shall gain them. A lap the road cannot be divided for shall keep whatever it already had rather than be blanked. No schema migration shall be required, and a session that is never opened shall never be rewritten. |
| OC-32 | Locally generated insights shall be regenerated after a sector correction **only when the merged-lap signal of OC-14 can be recovered** from the detection diagnostics recorded under LD-16. Where it cannot, the corrected sector times shall stand and the existing insight text shall be left unchanged. Insights not generated locally shall never be removed or replaced by this correction (OC-09, OC-10). |
| OC-09 | Offline insights shall be stored with `source="LOCAL"` flag in `coaching_insights` table. |
| OC-10 | When backend coaching arrives, local insights shall be replaced by backend insights. |

**Remark on LD-27 — the clock and the ruler used to start in different places.**
Lap *times* have been interpolated to the crossing instant since LD-15. Sector boundaries
inherited that instant but not the matching position: the ruler was zeroed at `lap[0]`, the
first fix *after* the line. All three recorded fixtures sample at a median gap of exactly
**1000 ms**, and `LocalLapDetector` notes that the car covers 15–20 m between samples, so
the lap's measured distance was short by that stretch at each end while its duration was not.

Writing `d₀` for the gap from the line to the first fix and `d_e` for the gap from the last
fix to the line, the first boundary landed `(2/3)d₀ − (1/3)d_e` late and the second
`(1/3)d₀ − (2/3)d_e` late. On Cabo do Mundo (825 m at ~14 m/s) that is about **0.34 s**
moved out of sector 2 and into sector 1. The damaging part is not the size but the
variability: `d₀` depends on the GPS clock's arbitrary phase against the crossing, so it is
re-rolled every lap, giving sector 1 roughly **±0.5 s of pure artefact** — about 2% of a
28 s sector, and of the same order as the differences sectors exist to reveal.

The dream lap is where this did real harm. It takes the *fastest* sector 1, 2 and 3 across
the session, so it preferentially selects whichever lap's artefact flattered it most — the
same argument OC-13 makes about a minimum actively selecting for the worst data. The
plausible inflation is around **1 s**, comfortably under OC-13's 25% cap, so it would never
have announced itself.

Sector times always summed to the lap exactly, because sector 3 takes the remainder, and lap
times were never affected. Nothing was lost or double-counted; the *placement* of the two
boundaries was wrong. That is why OC-31 repairs rather than discards.

**Remark on OC-21 — a catalogue centreline does not start at the start/finish line.**
`Centreline`'s own documentation claimed that its points begin at the start/finish. That is
true of exactly one of the three shipped circuits. Projecting each circuit's first detected
lap crossing onto its own centreline places the start/finish at **400.0 m** into Baltar
(length 1020.1 m), **770.9 m** into Cabo do Mundo (length 825.2 m) and **4.6 m** into
S. Mamede (length 821.6 m). Only the last is near enough to zero to have hidden the problem.

This matters beyond the map. Any code that converts an absolute `s` into a position within
a lap must subtract the start/finish origin and wrap, or it will place everything at an
offset that happens to be small on one circuit and two thirds of a lap on another. The
failure is silent: the arithmetic succeeds, the shape is the right shape, and only its
*phase* is wrong — so sector boundaries land in the wrong corners while every total still
adds up. It was found by measurement rather than by reasoning, after a map test failed in a
way that pointed at the index conversion instead.

---

## 11. Driver progression tracking

| ID | Requirement |
|---|---|
| DP-01 | The Home screen shall display a history list of all sessions, ordered by `startedAt` descending. (Single-user MVP: no userId filtering) |
| DP-02 | Each session history card shall show: track name, date formatted as `dd MMM yyyy HH:mm`, best lap time for that session, lap count, consistency score, and an upload status chip (`UPLOADED` / `PENDING` / `FAILED`). |
| DP-03 | The Home screen shall display a hero card showing the user's overall best lap time across all sessions, the track it was set on, and the date. |
| DP-04 | If the user has no sessions, the hero card shall display: 'Record your first session to see your best lap'. |
| DP-05 | The Session Result screen shall display the consistency score as a large percentage in the `COACH` tab summary card, with the subtitle 'across {N} laps'. |
| DP-06 | Sessions shall be fetched from the local Room database as the single source of truth. Remote data from the API shall be written back to Room and consumed from there. |

### 11.1 Session management

| ID | Requirement |
|---|---|
| SM-01 | The app shall allow the user to delete a session via long-press context menu on the Home screen session list. |
| SM-02 | Session deletion shall remove the session record, all associated laps, and all associated coaching insights from the local Room database. |
| SM-03 | Session deletion shall delete the telemetry JSONL file from local storage if it exists. |
| SM-04 | Before deletion, the app shall display a confirmation dialog showing the track name and date. |
| SM-05 | The app shall allow the user to rename a session's track name via long-press context menu on the Home screen session list. |
| SM-06 | The rename dialog shall pre-fill the current track name and allow editing. |
| SM-07 | Track name validation: minimum 1 character, maximum 100 characters. Empty names shall be rejected. |
| SM-08 | Delete and rename operations shall be local-only; they shall not require network connectivity or synchronize with the backend. |
| SM-09 | After successful deletion, the app shall display a Snackbar confirming the deletion. |
| SM-10 | After successful rename, the app shall display a Snackbar confirming the rename. |

---

## 12. Share accomplishments

| ID | Requirement |
|---|---|
| SH-01 | The Session Result screen shall include a share icon in the toolbar. |
| SH-02 | Tapping the share icon shall generate a 1080×1080 px share card as an Android `Bitmap`. |
| SH-03 | The share card shall contain: 'Driving Coach' branding text, best lap time (large, brand blue, monospace), 'BEST LAP' label, track name, session date, consistency score, and a brand blue bottom border line. |
| SH-04 | The bitmap shall be saved to the app's FileProvider cache directory and shared via `Intent.ACTION_SEND` with MIME type `image/png` through the Android Share Sheet. |
| SH-05 | The FileProvider authority shall be `${applicationId}.fileprovider`. |
| SH-06 | Share is a V1 placeholder for future social features. No social backend infrastructure is required in V1. |
| SH-07 | The Session Result screen shall expose a hidden developer telemetry export via a **long-press** on the share icon. The gesture shall consume the long-press event so that no tooltip is shown. |
| SH-08 | The telemetry export shall produce a ZIP bundle containing exactly two entries: `telemetry.jsonl` (a byte-for-byte copy of the recorded telemetry file) and `session.json` (session metadata, start line, lap records, app version/build and device model/SDK level). |
| SH-09 | The ZIP bundle shall be shared via `Intent.ACTION_SEND` with MIME type `application/zip` through the Android Share Sheet, using the same FileProvider authority as SH-05. |
| SH-10 | Share failures shall be reported to the user via a Snackbar. The app shall never fail a share silently. A missing telemetry file shall be reported as a distinct, non-fatal error rather than raising an exception. |
| SH-11 | Sharing shall be strictly **read-only** with respect to recorded data. Neither share path shall create, modify, rename or delete any session record, lap record or telemetry file. All share artifacts shall be written only inside the app's share cache directory (`cacheDir/shared/`), and shall be pruned after 24 hours. Pruning shall only remove artifacts the app itself created and shall not recurse into subdirectories. |

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
| `SessionEntity` | `id` (Long, PK), `firebaseUid` (String), `trackName` (String), `startedAt` (Long), `endedAt` (Long?), `rawFilePath` (String), `uploadStatus` (String: PENDING/UPLOADING/DONE/FAILED), `remoteSessionId` (String?), `processingStatus` (String: PENDING/PROCESSING/LAPS_DONE/COMPLETE/FAILED), `startLineLat1` (Double), `startLineLng1` (Double), `startLineLat2` (Double), `startLineLng2` (Double), `trackId` (String?, TL-08 — null for a captured line) |
| `TrackEntity` | `id` (String, PK), `name` (String), `location` (String?), `startLineLat1/Lng1/Lat2/Lng2` (Double), `startLineSource` (String), `startLineRecordedAt` (String?), `travelHeadingDeg` (Double?), `lengthM` (Int?), `cornerCount` (Int?), `fastestLapMs` (Long?), `slowestLapMs` (Long?), `centrelineSource` (String?), `centrelineMethod` (String?), `centrelineSurveyedAt` (String?), `centrelineSurveyedBy` (String?), `centrelineJson` (String?, JSON array of `[lat, lng]`), `createdAt` (Long), `lastUsedAt` (Long?) — saved circuits only; bundled circuits are read from `assets/tracks/tracks.json` and are never written to Room, so a corrected circuit ships with a build instead of needing a migration |
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
| NF-11 | Unit test line coverage shall be ≥ 70% across domain and data layers. ⚠️ **Not automatically enforced** — see note below. |
| NF-12 | `./gradlew assembleRelease` shall succeed with R8/ProGuard enabled. ⚠️ **Not currently met** — see note below. |
| NF-13 | The backend Docker image shall build and start within 60 seconds. |
| NF-14 | High-accuracy location shall not be held while the app is not in the foreground, outside an active recording, which runs under a visible foreground-service notification. This is a privacy bound before it is a battery one: the user shall always be able to see that the receiver is in use. |
| NF-17 | The release build shall be signed with the Play upload key, and the release-packaging script shall refuse to produce a Play artifact that is unsigned, built from an unclean working tree, carries a duplicate `versionCode`, fails `lintVitalRelease`, or has no test report for its own commit. |
| NF-18 | The app's Play identity shall be `io.github.emidiofaria.trillian`. This is fixed permanently by the first upload and shall not be changed thereafter. |
| NF-19 | The app shall target the minimum API level Google Play accepts for new submissions — currently API 36 (Android 16). Raising `targetSdk` shall be treated as a behavioural change: every deprecation warning it produces shall be reviewed, and L2 evidence shall come from an emulator at that API level. |
| NF-20 | The release build shall not transmit any recorded data off the device, and shall not declare the `INTERNET` permission. This shall be enforced by two independent mechanisms: `BuildConfig.UPLOAD_ENABLED` set to false (asserted by `DataSafetyPolicyTest`), and a release-packaging gate that inspects the merged release manifest. The Google Play Data Safety declaration of "does not collect any user data" depends on this requirement, so any change to it shall update that declaration and `docs/privacy-policy.md` in the same commit. |
| NF-21 | The app shall not declare or request any permission it does not use. A permission that is requested but never exercised misleads the user, misrepresents the app to Play review, and cannot be justified in a Data Safety declaration. |
| NF-15 | Start-line capture shall be reachable within 1 second of arriving at the Track Setup screen when GPS readiness was already reported on Home, so that the warm-up the user waited for is not spent twice. |
| NF-16 | Lap timing shall not be quantised to the GPS sample interval. On a device delivering fixes at 1 Hz, the error introduced by sampling shall not exceed 100 ms per lap boundary. |

**Remark on NF-11 — enforcement status (2026-09-15).**
NF-11 has **no automated enforcement**. The JaCoCo coverage gate that nominally
backed it was removed on 2026-09-15, together with ktlint, by explicit decision.
That gate had been declaring a 95% line/branch threshold while never being
attached to Gradle's `check` task, so it had in fact never executed — the
requirement was already unenforced, and the removal makes that visible rather
than changing it. The ≥ 70% target stands as a requirement; it is currently
unmeasured. Re-establishing it means adding a coverage plugin **and** wiring the
verification task into `check`. Android Lint remains the one active build gate.

**Remark on NF-12 — enforcement status (2026-09-16).**
NF-12 is **not currently met**. `isMinifyEnabled = false` on the release build
type, so `assembleRelease` succeeds *without* R8 rather than with it. The
requirement was written as though R8 were on, and nothing ever checked. This was
found while preparing the first Play upload and deliberately left as-is for that
release: turning R8 on for the first time hours before publishing risks a
reflection failure in Gson or Room that would only appear in the shipped build.
Meeting NF-12 means enabling minification, writing the keep rules, and testing a
minified build end-to-end — planned work, not a flag flip.

**Remark on NF-14 — the Android 13+ gap (2026-09-16).**
NF-14's guarantee that "the user shall always be able to see that the receiver is
in use" was **silently broken on Android 13 and above**. `POST_NOTIFICATIONS` was
never declared in the manifest, so it could not be granted, and the platform
discards `notify()` calls from an app that lacks it. The foreground service still
ran and still recorded; what the driver lost was any sight of it — including the
"GPS signal lost — move to open sky" warning, which is the notification that
actually changes behaviour at the track. The permission is now declared and
requested during onboarding. It is requested as **optional**: recording works
without it, so treating it as required would have stranded anyone who declined on
the onboarding screen, which is a worse failure than the one being fixed.


The app requests location updates at 10 Hz, but the rate actually delivered is set by the device's GNSS hardware, not by the app. The reference phone (ZTE Blade A53+) delivers roughly 1 Hz. At 15–20 m/s, taking the timestamp of the nearest sample instead of the true crossing instant costs up to 1 second, which on a 77 s kart lap is about 1.3% — larger than the differences between laps that the coaching is meant to explain. Interpolating between the two samples either side of the crossing removes almost all of this, and costs nothing.

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
| ~~OOS-05~~ | ~~Track map library or pre-loaded track database~~ — **delivered**, see [§4a Track library](#4a-track-library--pre-defined-and-saved-circuits). Scoped down from the original exclusion: the app ships a small catalogue of circuits it has measured data for, not a general track database, and there is still no map SDK or tile service (AS-04 stands). |
| OOS-06 | Video overlay or external camera synchronisation |
| OOS-07 | Google / Apple sign-in (Firebase infrastructure is ready for V2 addition) |
| OOS-08 | In-app purchase or subscription management |
| OOS-09 | Driving Academy / AI learning curriculum (placeholder only in UI) |

---

*End of document. Requirements count: 136. All IDs are unique and stable for traceability.*
