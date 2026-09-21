# Incident: Inaccurate Lap Detection and Track Inference During Kart Session with Queue Wait

**Date Reported**: 2026-09-20  
**Reported By**: Driver / Field User (Karting Session)  
**Severity**: **HIGH** — Core lap timing and track map features failed to correctly record and present a 12-lap session  
**Status**: Reported / Under Investigation  
**Component**: `LocalLapDetector` (on-device lap detection) / Track Setup & Session Analysis UI  
**App Version**: `versionName "2.97"`, `versionCode 297`  
**Device**: ZTE Blade A53+ (Android 12, SDK 31)  
**Session**: ID 2, Track Name: `Baltar 2` (Kartódromo de Baltar), Recorded 2026-09-20 17:24–17:56  

---

## User Experience & Incident Summary

The user went to a 15-minute karting session with friends at Kartódromo de Baltar.

### User Workflow & Actions Taken
1. **Track Setup**: The user walked to the track edge and marked the Start/Finish (SF) line coordinates in the app.
2. **Session Start & Wait**: The user started recording in the app, placed the phone in their pocket, and joined the queue waiting for their turn to get into the kart.
3. **Queue Duration**: The user waited in the pit/queue area for approximately 15 minutes before the session began.
4. **Driving**: The user boarded the kart and drove a full 15-minute session, completing **12 full laps** around the circuit.
5. **Session End**: After pulling into the pit area, the user stopped the recording and checked the session results.

---

## User Observations & Complaints

1. **Incorrect Lap Count & Erroneous Lap Times**:
   - The user completed **12 full laps** (plus out-lap and in-lap), but the app only registered **2 laps** in total.
   - **Lap 1** was reported as **15 minutes 54 seconds (954.4 s)** — capturing the stationary/walking queue time rather than a real driving lap.
   - **Lap 2** was reported as **6 minutes 44 seconds (404.5 s)** — grouping multiple driven laps into a single false lap.
   - The remaining **7 driving laps** were completely ignored and never detected by the app.

2. **Track Map / Circuit Trajectory Not Well Inferred**:
   - The Analysis tab and track map display were heavily distorted/polluted.
   - GPS drift, pocket movement, and walking data from the 15-minute queue period were blended into the racing circuit trajectory, obscuring the actual track shape driven during the session.

---

## Observed Behaviour vs Expected Behaviour

| Metric / Feature | Observed Behaviour (Actual) | Expected Behaviour |
|------------------|-----------------------------|--------------------|
| **Lap Count** | 2 laps shown | **12 completed laps** |
| **Lap 1 Duration** | `15m 54s` (954.36 s) | ~`1m 20s` (80.0 s) |
| **Lap 2 Duration** | `6m 44s` (404.51 s) | ~`1m 18s` (78.0 s) |
| **Subsequent Laps (3–12)** | None detected (`0` laps recorded) | Laps 3 through 12 detected (~72s – 85s each) |
| **Best Lap** | Lap 2 marked as best (`6m 44s`) | Fastest real driving lap (~`1m 12s` / 72.0 s) |
| **Track Map / Circuit View** | Polluted by ~16 min stationary/pocket drift | Clean circuit map based on active driving path |
| **Coaching Insights** | Distorted due to invalid multi-minute lap durations | Accurate sector and lap coaching based on 12 driven laps |

---

## Telemetry Evidence Summary

**Session Directory**: `03_incidents/15_Baltar_session_track_detection/telemetry_Baltar_2_20260920-1724/`

| File | Size / Content | Description |
|------|----------------|-------------|
| `session.json` | JSON metadata | Contains session configuration, start line, device info, and the erroneous 2 laps recorded |
| `telemetry.jsonl` | 464 KB (1923 samples) | Full continuous 1 Hz GPS and IMU telemetry stream covering the entire 32.0-minute period |

### Telemetry Profile

- **Total Duration**: 1,921.9 seconds (~32.0 minutes)
- **Total Samples**: 1,923 samples at nominally 1 Hz
- **Recorded Speed**: Max 21.88 m/s (78.8 km/h), average 6.41 m/s
- **High-Speed Samples (> 8 m/s)**: 879 samples (~14.6 minutes of active kart driving)
- **Captured Start Line**:
  - Point 1: `(41.186409, -8.396519)`
  - Point 2: `(41.186459, -8.396396)`
  - Midpoint: `(41.186434, -8.396457)`
  - Length: 11.68 m, Bearing: 61.9°

### Physical Start/Finish Line Passes in Telemetry

Analysis of the raw telemetry identifies **14 distinct passes** within 20 m of the start line midpoint:

| Pass # | Sample Index | Elapsed Time | Speed | Heading | User Activity / Phase |
|:------:|:------------:|:------------:|:-----:|:-------:|:----------------------|
| **1**  | 1 | 0.9 s (0.0 min) | 2.58 m/s (9.3 km/h) | 275.8° | Walking / Pocket in queue area |
| — | 2–955 | 1.0 s – 955.0 s | < 3.0 m/s | — | *Waiting in queue (~15.9 min)* |
| **2**  | 956 | 955.9 s (15.9 min) | 11.78 m/s (42.4 km/h) | 336.9° | Out-lap crossing (Driving starts) |
| **3**  | 1036 | 1035.9 s (17.3 min) | 11.92 m/s (42.9 km/h) | 337.7° | **Lap 1 Complete** (~80.0 s) |
| **4**  | 1114 | 1113.9 s (18.6 min) | 11.84 m/s (42.6 km/h) | 345.1° | **Lap 2 Complete** (~78.0 s) |
| **5**  | 1199 | 1198.9 s (20.0 min) | 13.11 m/s (47.2 km/h) | 337.9° | **Lap 3 Complete** (~85.0 s) |
| **6**  | 1282 | 1281.9 s (21.4 min) | 14.38 m/s (51.8 km/h) | 336.5° | **Lap 4 Complete** (~83.0 s) |
| **7**  | 1360 | 1359.9 s (22.7 min) | 15.02 m/s (54.1 km/h) | 330.6° | **Lap 5 Complete** (~78.0 s) |
| **8**  | 1435 | 1434.9 s (23.9 min) | 14.00 m/s (50.4 km/h) | 338.5° | **Lap 6 Complete** (~75.0 s) |
| **9**  | 1508 | 1507.9 s (25.1 min) | 14.60 m/s (52.6 km/h) | 335.3° | **Lap 7 Complete** (~73.0 s) |
| **10** | 1582 | 1581.9 s (26.4 min) | 14.24 m/s (51.2 km/h) | 344.7° | **Lap 8 Complete** (~74.0 s) |
| **11** | 1654 | 1653.9 s (27.6 min) | 14.34 m/s (51.6 km/h) | 331.9° | **Lap 9 Complete** (~72.0 s) |
| **12** | 1728 | 1727.9 s (28.8 min) | 14.93 m/s (53.7 km/h) | 331.0° | **Lap 10 Complete** (~74.0 s) |
| **13** | 1801 | 1800.9 s (30.0 min) | 13.91 m/s (50.1 km/h) | 339.1° | **Lap 11 Complete** (~73.0 s) |
| **14** | 1877 | 1876.9 s (31.3 min) | 7.73 m/s (27.8 km/h) | 333.0° | **Lap 12 Complete** / In-lap pit entry (~76.0 s) |

---

## User Impact

1. **Loss of Lap Timing & Performance Insights**:
   - The user did not get accurate lap times, sector analysis, or delta comparisons for their 12-lap session.
   - The session summary displayed unusable durations (15m 54s and 6m 44s) instead of real kart lap times (~72s to 85s).
2. **User Confusion & Lack of Guidance**:
   - The app provided no feedback indicating that starting a session well before driving (e.g. waiting in a queue) would corrupt lap detection and track inference.
3. **Compromised Track Visualization**:
   - The user could not clearly inspect their racing line or track map due to queue wander data being included in the session trajectory.

---

## Root Cause

The heading guard (LD-14) rejects a crossing that disagrees by more than 60° with **the session's
first accepted crossing**. That reference is taken from the data it is guarding, so the first
crossing is structurally the one candidate it cannot evaluate.

The driver started recording, pocketed the phone, and queued ~16 minutes for a kart. The queue at
Baltar stands beside the start straight. A **2.9 m/s walk across the start point at 272°** was
accepted — inside the corridor, no previous crossing to compare against, no minimum-lap or
minimum-distance guard applicable to a first crossing — and became the session's reference.

The 12 racing crossings arrived at **331–343°**. Ten were rejected for disagreeing with a
pedestrian. The two that squeaked under the 60° window (54.3° and 59.6°) became the reported
"laps" of 954 s and 404 s.

A second, independent fault compounded it: the start/finish line the driver captured was
**164.6 m** from the real one.

Recorded in the Atlas as `FP-PEDESTRIAN-REFERENCE`.

## Fix

| Part | Mechanism | Scope | Requirement |
|------|-----------|-------|-------------|
| Refuse walking passes | Reject a crossing below `MIN_CROSSING_SPEED_MS` (4 m/s); recorded as `TOO_SLOW` | **Every** session | LD-19 |
| Seed the reference from outside the session | `TrackPriors.travelHeadingDeg` from the track catalogue replaces the first-crossing reference, guarding the first crossing too | Catalogued circuits | LD-20 |
| Tighten the minimum lap gap | Catalogued fastest lap × 0.8 raises `MIN_LAP_TIME_MS` | Catalogued circuits | LD-21 |
| Refuse to report nonsense | Discard a lap set whole only when **every** lap in it averages under 5 m/s over the circuit's surveyed length | Circuits with a surveyed length | LD-22 |
| Exclude standing time from statistics | Centreline corridor filter (15 m, stands down below 30 % retention) applied to session stats and the speed trace only | Circuits with a centreline | AS-18 |

The speed gate is applied **after** the corridor check, so Incident 14's `TOO_FAR_TO_THE_SIDE`
diagnostics keep their meaning.

Alongside the fix, the underlying capability gap — the app knew nothing about the circuit — was
closed by the **track library** (SRS §4a, TL-01…TL-14): a bundled catalogue of circuits, a
SELECT TRACK / NEW CIRCUIT fork at session start, and the ability to save a captured circuit.
Kartódromo de Baltar ships with a corrected start/finish line (10.97 m, 137.8° travel heading,
88.7° to the direction of travel) and a 140-point, 1020 m centreline walked on foot by the driver.

## Verification

The session's telemetry is now a permanent replay fixture at
`app/src/test/resources/lapfixtures/baltar2/`.

| Claim | Evidence |
|-------|----------|
| 12 laps recovered from the **driver's own mis-captured line**, by the speed gate alone | `LapDetectionIncident15Test.theTwelveLapsAreDetectedFromTheLineTheDriverCaptured` |
| 12 laps recovered from the catalogued start line | `…theTwelveLapsAreDetectedFromTheCataloguedStartLine` |
| The walking pass is rejected as `TOO_SLOW` | `…theWalkingPassAcrossTheStartPointIsRejectedAsTooSlow` |
| The catalogue heading guards the first crossing | `…theCatalogueHeadingIsUsedAsTheReference` |
| A wrong catalogue heading is never worse than no catalogue | `…aWrongCatalogueHeadingFallsBackToTheSession` |
| An implausible lap set is discarded rather than shown | `…lapsImpossibleForTheCircuitAreNotReported` |
| A driver slower than the declared envelope keeps their laps | `…aDriverSlowerThanTheCircuitsDeclaredEnvelopeStillGetsTheirLaps` |
| A single spin lap among normal ones survives | `…aSingleSpinLapDoesNotDiscardTheSessionAroundIt` |
| The guard comes from the surveyed length, not the declared envelope | `…withoutASurveyedLengthNothingIsDiscarded` |
| The shipped Baltar geometry matches the real session | `BundledTrackCatalogTest` (6 tests) |
| Queue samples leave the statistics; lap geometry is untouched | `TrackCorridorFilterTest` (4 tests) |
| Selecting a circuit does not bypass the GPS gate | `TrackLibraryGateTest` (L2, 3 tests) |

Measured lap times from the fixture: **72.5 – 84.3 s** across 12 laps, against the two reported
durations of 954 s and 404 s.

**Suite status at close:** L1 305/305 pass, L2 64/64 pass.

> **Follow-up, after close.** Reviewing what LD-22 does to a *slow* driver exposed a second
> defect in the fix itself. Baltar shipped with a 90 s upper bound, so the original rule
> discarded anything over 135 s — a timid weekend driver lapping in 150 s would have had every
> lap thrown away and been shown nothing, the guard against incident 15 reproducing incident
> 15's symptom for a different reason. LD-22 now measures against the **surveyed** lap length
> rather than the typed-in envelope, and discards a set only when **no** lap in it could have
> been driven, so a single spin lap survives. Baltar's envelope was also corrected to
> 40–120 s; at the old 70 s figure LD-21's minimum gap was 56 s, which would have refused a
> genuine 40 s lap.

**The fix is not conditional on the track library.** That the speed gate alone recovers all 12
laps against the mis-captured line is asserted as a separate test, deliberately, so the correction
cannot quietly become dependent on a circuit being catalogued. An uncatalogued venue gets it too.

## Incidental finding

Removing `fallbackToDestructiveMigration()` — required once a walked circuit lives in the database
and exists nowhere else — promoted a latent fault to a launch crash: schema `1.json` **already
contained** the columns `MIGRATION_1_2` adds, so that migration had never been able to succeed. The
destructive fallback had been silently wiping user databases instead. `MIGRATION_1_2` is now
idempotent, and the chain is walked end to end by `DrivingCoachDatabaseMigrationTest`.

Recorded in the Atlas as `FP-DESTRUCTIVE-FALLBACK-MASK`.

## Status

**CLOSED — fixed and verified.**
