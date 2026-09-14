# Incident: "No laps detected" After Five Completed Laps — Start Line Parallel to Direction of Travel

**Date Reported**: 2026-09-08
**Reported By**: Developer testing / session review (telemetry supplied by the operator)
**Severity**: **HIGH** — the core product feature (lap timing and coaching) produced nothing for a fully valid session
**Status**: Documented — RCA complete, no remediation performed
**Component**: `LocalLapDetector` (on-device lap detection) / Track Setup start-line capture
**Build Under Test**: `versionName "2.8"`, `versionCode 208`
**Device**: ZTE Blade A53+, Android SDK 31
**Session**: id 5, track name `teste. 3`, recorded 2026-09-07 19:19

---

## Original Report (verbatim, as submitted)

> Please look into /00_sandbox/test_024_Track_record_app/test_03/telemetry_teste_3_20260907-1919
> this is the telemetry files from a session, app did not detect any lap, investigate why

---

## Description

A driver recorded a six-minute session on a kart track. The session recorded
cleanly: GPS locked, telemetry written continuously, no crash, no error, no
interruption.

At the end of the session the application reported:

> **"No laps detected. Complete at least 2 laps."**

The driver had completed **five passes of the start/finish line**.

The telemetry file contains all five passes. The data is of good quality. The
application was holding a complete, correct record of five laps and reported
that none had been driven.

This is not a data-capture failure. It is an **interpretation failure**: the
recorded evidence was sufficient and the detector could not read it.

---

## Observed Behaviour

| Observation | Value |
|-------------|-------|
| Message shown to user | `No laps detected. Complete at least 2 laps.` |
| Laps stored in session | `[]` (empty — see `data/session.json`) |
| `processingStatus` | `PENDING` |
| `uploadStatus` | `FAILED` (no network; expected — the app is offline-first) |
| Coaching insights generated | **None** — `generateOfflineCoaching()` only runs on a `Success` result |

### Expected Behaviour

The session should have reported **4 completed laps** (five crossings bound four
laps; the final partial lap is correctly discarded), with lap times and an
offline coaching summary.

---

## Evidence — Real Telemetry (attached)

The complete, unmodified session as exported from the device is preserved in
this incident folder:

| File | Description |
|------|-------------|
| `data/session.json` | Session metadata as exported by the app, including the captured start line and the empty `laps` array |
| `data/telemetry.jsonl` | 87 KB — the full telemetry stream: 1 header line + 362 samples |
| `analysis/replay_analysis.py` | The exact analysis script used during the RCA; reproduces every number in this report |

### Session summary (measured from `data/telemetry.jsonl`)

| Metric | Value |
|--------|-------|
| Telemetry samples | 362 (plus 1 header line) |
| Session duration | 360.2 s (6 minutes) |
| Sample interval | 900 / 954 / 999 / 1000 ms — **nominally 1 Hz** with minor jitter |
| Path length driven | 3 328 m |
| Track bounding box | approx. 213 m × 291 m |
| Maximum speed | 20.2 m/s (~73 km/h) |
| Mean speed | 8.9 m/s |
| Samples above 2 m/s | 315 of 362 — the car was moving for the great majority of the session |
| GPS accuracy | min 1.4 m, mean 4.8 m, max 15.0 m (17 samples worse than 10 m) |
| Distance from start line midpoint | min 2.1 m, max 176.4 m |

The data is entirely healthy: continuous, plausible speeds, good accuracy, no
gaps. The largest gap between consecutive samples is 28.3 m, which is simply the
distance covered at speed in one second at 1 Hz.

### Start line as captured by the user

From `data/session.json` and the telemetry header (the two agree exactly):

```json
"startLine": {
  "lat1": 41.20052719116211, "lng1": -8.610971450805664,
  "lat2": 41.20054244995117, "lng2": -8.611054420471191
}
```

| Property | Value |
|----------|-------|
| Length of the captured line | **7.15 m** |
| Bearing of the captured line | **283.7°** |

### The five passes of the start/finish

The car approached the start line midpoint closely on five distinct occasions,
spread evenly through the session:

| Pass | Sample index | Time into session | Closest approach | Heading of travel | Speed |
|------|--------------|-------------------|------------------|-------------------|-------|
| 1 | 33–35 | ~32 s | **0.5 m** | 280.3° – 282.2° | 7.0 – 7.8 m/s |
| 2 | 113–114 | ~112 s | **1.8 m** | 282.5° – 284.3° | 14.6 – 14.9 m/s |
| 3 | 190–191 | ~189 s | **2.1 m** | 280.4° – 284.2° | 17.9 – 19.7 m/s |
| 4 | 267–268 | ~266 s | **2.0 m** | 282.9° – 283.6° | 14.5 – 15.5 m/s |
| 5 | 351–352 | ~350 s | **1.7 m** | 286.1° – 288.8° | 7.1 – 10.0 m/s |

The driver was repeatable to within about two metres, five times in a row. This
is a clean, well-driven session.

### Crossings detected by the shipped algorithm

```
CROSSINGS DETECTED: 0
```

---

## Impact

| Aspect | Assessment |
|--------|------------|
| Data loss | **None** — the telemetry is intact and complete |
| Feature loss | **Total for this session** — no lap times, no best lap, no lap comparison, no coaching insights |
| Correctness of the message shown | **The message is factually wrong.** The driver completed five laps and was told to complete at least two |
| User trust | High risk. The failure is silent as to cause and implicitly blames the driver |
| Recoverability by the user | **None.** Nothing in the UI indicates the start line was the problem, so the user has no corrective action available |
| Frequency | Unknown. Depends on how the start line happens to be captured — see the RCA |

### Severity rationale

Rated **HIGH** rather than MEDIUM despite there being no data loss, because:

1. The primary purpose of the application — telling a driver their lap times —
   produced nothing at all for a valid session.
2. The user-facing message is **incorrect and misattributes the fault to the
   driver**, which is worse than an honest failure.
3. There is **no diagnostic path for the user**. The session is simply lost.

---

## Prior Related Incidents

This is the **fourth** recorded incident in the "laps not detected" family. That
pattern is itself a finding and is examined in the RCA.

| Incident | Title | Root cause found | Status |
|----------|-------|------------------|--------|
| 02 | Lap detection not triggering | Service teardown race — `stopSelf()` destroyed the binding before `processLapsLocally()` ran | Resolved |
| 03 | 200 m threshold too large | `MIN_DISTANCE_FROM_START_M = 200` copied from the backend; too large for kart tracks. Changed to 50 m | Resolved |
| 09 | No laps after 4 laps on emulator | **Not established — MEDIUM confidence (55%), concluded without log or telemetry evidence** | Nominally closed, cause unproven |
| **13** | **This incident** | Established from real telemetry — see RCA | Documented |

Incident **09** deserves particular attention: the same symptom, investigated
without data, closed at 55 % confidence on a hypothesis. The present incident is
the first time this symptom has been investigated against a real recorded
session. Whether 09 was in fact the same defect cannot now be determined,
because its telemetry was never retained.

---

## Related Work

Incident **12** (GPS acquisition / warm-up discarded at the Track Setup
handover) fixed a defect in which **stale GPS fixes could be used for
start-line capture** (finding F4). That fix landed in v2.93. The session
documented here was recorded on **v2.8**, which predates it. The relationship
between that defect and this one is examined in the RCA under contributing
factors.

---

## Investigation

See: `13_RCA_start_line_parallel_to_direction_of_travel.md`

---

## Status

**Closed — verified in the field on v2.94** (2026-09-09).

Human acceptance testing on v2.94 confirmed:

| Check | Result |
|-------|--------|
| **LD-CROSS-06** — start/finish captured deliberately *along* the track, the exact geometry that produced zero laps on v2.8 | **Laps detected correctly** |
| Lap times and lap counts against the drivers' own count | **Matched** — no sign of `FP-LAP-DOUBLE-COUNT` |
| Normal sessions | No regressions reported |

LD-CROSS-06 is the check that closes this incident. Laps detecting correctly in
*normal* use would not have been sufficient evidence: the defect only appears
when the captured line happens to align with the direction of travel, which is
why it survived to reach a user in the first place. Reproducing that geometry
deliberately and getting correct laps is the direct disproof.

Merged to `main` in PR #39.

---

## Resolution

### What was changed

`LocalLapDetector` no longer uses the *orientation* of the captured start/finish
line. It takes the line's **midpoint** — a position, which GPS can measure — and
derives the crossing direction from the car's own motion, measured over hundreds
of metres rather than over a 7 m baseline.

| Change | Where |
|--------|-------|
| Crossing plane perpendicular to direction of travel, through the start point | `LocalLapDetector.detectCrossings()` |
| Lateral gate at `DETECTION_HALF_WIDTH_M` = 15 m | same |
| Heading-consistency guard at `MAX_HEADING_DIFFERENCE_DEG` = 60° | same |
| Crossing instants interpolated between bracketing samples (F3) | same |
| `isValid()` rejects an endpoint at (0, 0) while keeping Greenwich valid (F1) | `LocalLapDetector.StartLine` |
| Detection reasoning written beside the telemetry | `LapDiagnosticsWriter` (new) |

### Verification

Replaying **this session** through the fixed detector recovers the laps the
driver actually drove:

**4 laps — 79.83 / 77.08 / 77.26 / 83.44 s**

The same answer is produced for lateral half-widths of 10, 15, 20 and 25 m. That
insensitivity is the evidence that this is a corrected geometric model rather than
a threshold tuned until the numbers looked right.

`LapDetectionRealSessionTest` replays the session in `data/` on every build. It
fails against the v2.8 algorithm with the exact message the user saw, and passes
after the fix.

Validation: **L1 233 tests / 0 failures**, **L2 64 tests / 0 failures**.

### Findings and their disposition

| Finding | Disposition |
|---------|-------------|
| F1 — `isValid()` accepts a zeroed endpoint | Fixed |
| F2 — `maxDistanceFromStart` not reset on rejection | **Not a defect.** Investigated, "fixed", found to be a regression, reverted — see the correction in the RCA |
| F3 — crossing timestamps not interpolated | Fixed; now SRS **NF-16** |
| F4 — telemetry rate hardware-limited | No code change possible; mitigated by F3 |
| F5 — SRS says detection is server-side | SRS §8 reworked to local-first |
| F6 — LD-06 specifies 200 m, code uses 50 m | SRS corrected to 50 m, with the rationale as a Remark |

### Known limitation accepted with this fix

Because the crossing plane follows the car, a circuit passing the same point
twice per lap in different directions may double-count. The heading guard covers
the common cases; the residue is documented as `FP-LAP-DOUBLE-COUNT` in the Atlas,
as a Remark under SRS LD-04, and as a troubleshooting entry in the User Manual.
No venue in use has this geometry.

### Follow-up

`docs/plans/PLAN_003_One_Tap_Start_Finish_Capture.md` — the capture screen still
asks for a second point the detector no longer uses. Measurement during this work
showed a single point yields identical lap times, so PLAN-003 is a UI change with
no algorithm work behind it.
