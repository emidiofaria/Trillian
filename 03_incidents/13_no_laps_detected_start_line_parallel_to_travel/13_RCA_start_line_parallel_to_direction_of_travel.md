# Root Cause Analysis: Start Line Captured Parallel to the Direction of Travel

| Field | Value |
|-------|-------|
| **Incident ID** | 13 |
| **Application** | Driving Coach |
| **Severity** | HIGH |
| **RCA Date** | 2026-09-08 |
| **Analyst** | SW_dev agent (session with operator) |
| **APK Version** | v2.8 (`versionCode` 208) |
| **Device** | ZTE Blade A53+, Android SDK 31 |
| **Evidence** | Real recorded session — `data/telemetry.jsonl`, `data/session.json` |
| **Reproducibility** | `analysis/replay_analysis.py` — deterministic, offline, no device required |
| **Confidence** | **HIGH (>95 %)** — the cause is measured from the recorded session, not inferred |

---

## Incident Summary

A driver completed five passes of the start/finish line during a six-minute kart
session. The application reported *"No laps detected. Complete at least 2 laps."*
and produced no lap times and no coaching output.

The telemetry was complete and of good quality. The failure was in
interpretation, not capture.

---

## Statement of Root Cause

> **The start/finish line captured by the user lies parallel to the direction in
> which the car travels through it — within 0.1° to 5.1° of exactly parallel.
> The car therefore drives *along* the line rather than *across* it, and a
> segment-intersection test can never return true. No length of line, and no
> adjustment of the existing distance or time thresholds, could have detected
> these laps.**

The deeper cause is that **the application derives a direction from two GPS
points that are closer together than the GPS measurement error**, and then
treats that direction as authoritative.

---

## Evidence

All figures below are produced by `analysis/replay_analysis.py` from the
attached session and can be regenerated at any time.

### 1. The car passed the start line five times

| Pass | Sample | Time | Closest approach to the line |
|------|--------|------|------------------------------|
| 1 | 34 | 33 s | **0.53 m** |
| 2 | 113 | 112 s | 1.84 m |
| 3 | 190 | 189 s | 2.06 m |
| 4 | 268 | 267 s | 1.98 m |
| 5 | 351 | 350 s | 1.70 m |

Five passes, each within about two metres of the same point, evenly spaced
through the session. This is a well-driven, entirely ordinary session.

### 2. The shipped algorithm found nothing

```
crossings detected ...... 0
laps built .............. 0
user-visible outcome .... No laps detected. Complete at least 2 laps.
```

### 3. The geometry makes detection impossible

Start line bearing: **283.7°**. Heading of travel at each pass:

| Sample | Heading | Angle between travel and the line |
|--------|---------|-----------------------------------|
| 33 | 280.3° | 3.5° |
| 34 | 282.2° | 1.5° |
| 113 | 284.3° | **0.6°** |
| 114 | 282.5° | 1.2° |
| 190 | 284.2° | **0.4°** |
| 191 | 280.4° | 3.3° |
| 267 | 282.9° | 0.8° |
| 268 | 283.6° | **0.1°** |
| 351 | 286.1° | 2.4° |
| 352 | 288.8° | 5.1° |

0° would mean the car is travelling exactly along the line. The measured values
are 0.1° to 5.1°.

Critically, the analysis also tested each pass against the **infinite extension**
of the captured line:

```
sample  33 ... does NOT cross
sample  34 ... does NOT cross
sample 113 ... does NOT cross
   ... all ten ... does NOT cross
```

**Not one segment of the driven path crosses even the unbounded line.** This
rules out "the line was too short" as the cause, definitively and without
argument.

### 4. Counter-hypothesis tested and rejected

The obvious first hypothesis — a 7.15 m line is simply too small a target — was
tested by extending the line about its midpoint while preserving its
orientation:

| Line length | Crossings | Laps |
|-------------|-----------|------|
| 7.1 m (as captured) | 0 | 0 |
| 10 m | 0 | 0 |
| 15 m | 0 | 0 |
| 20 m | 0 | 0 |
| 30 m | 0 | 0 |
| 40 m | 2 | 1 |
| 50 m | 2 | 1 |

Even a **30-metre** line — four times the width of a kart track — detects
nothing. The two crossings that appear at 40 m and 50 m are artefacts of a line
so long it reaches unrelated parts of the circuit; they are not the five real
passes.

**Length is not the controlling variable. Orientation is.**

### 5. Correct orientation recovers the session completely

Using the user's two points **only to anchor position** (their midpoint), and
taking the crossing line **perpendicular to the direction of travel**, with the
crossing instant interpolated between samples:

| Half-width | Crossings | Laps | Lap times (s) |
|------------|-----------|------|---------------|
| 10 m | 5 | 4 | 79.83, 77.08, 77.26, 83.44 |
| 15 m | 5 | 4 | 79.83, 77.08, 77.26, 83.44 |
| 20 m | 5 | 4 | 79.83, 77.08, 77.26, 83.44 |
| 25 m | 5 | 4 | 79.83, 77.08, 77.26, 83.44 |

Lateral offsets at the five crossings: 0.8 m, 1.9 m, 2.2 m, 2.3 m, 1.8 m.

Two properties of this result matter more than the numbers themselves:

1. **Four laps within six seconds of each other** on a 77-second lap — the
   result is physically plausible and consistent with a real driver.
2. **The result is identical across a 10 m–25 m range of the only free
   parameter.** It is insensitive to tuning. That is the signature of a
   correct model, as opposed to a threshold fitted to one session.

**The five laps were in the data the whole time.**

---

## Causal Chain

```
DESIGN ASSUMPTION
  the start/finish line's ORIENTATION can be obtained from two user-captured GPS points
        ↓
MEASUREMENT REALITY
  GPS accuracy in this session: 1.4 m – 15.0 m, mean 4.8 m
  the captured baseline: 7.15 m
  → the endpoint error is comparable to the baseline itself
  → the bearing derived from it is dominated by noise, not by the user's walk
        ↓
PERMISSIVE VALIDATION
  TrackSetupViewModel.MIN_LINE_DISTANCE_M = 3.0
  → a 3-metre baseline is accepted as a valid line
  → orientation is never checked at all, at capture time or at detection time
        ↓
OBSERVED OUTCOME
  the stored line came out at bearing 283.7°, which is the direction of travel
        ↓
BRITTLE DETECTION
  LocalLapDetector uses a strict segment-intersection test with zero tolerance
  → the path runs parallel to the line and never intersects it
        ↓
SILENT, MISLEADING FAILURE
  0 crossings → 0 laps → InsufficientLaps
  → "No laps detected. Complete at least 2 laps."
  → generateOfflineCoaching() never runs; the entire session's value is lost
  → nothing indicates the start line was at fault; the user cannot recover
```

---

## Why the Orientation Came Out Parallel

The RCA can prove the line **is** parallel to travel. Establishing **how it came
to be** is less certain, and the two candidate explanations are recorded here
honestly rather than resolved by assertion.

### Hypothesis A — GPS noise on a degenerate baseline (most likely)

The user follows the on-screen instruction, *"Walk to each edge of the track at
the start/finish line and capture two GPS points"*, and walks correctly across
the track. But with roughly 5 m of error on each endpoint and only 7 m between
them, **the bearing of the resulting line is close to random**. A correct walk
can produce a line pointing in any direction, including straight down the track.

This hypothesis fully explains the outcome and requires no user error. It also
predicts that the defect is **intermittent and unreproducible on demand**, which
is consistent with lap detection having appeared to work on some occasions and
not others.

*Caveat, stated plainly:* landing within 5° of parallel by chance alone is
roughly a 1-in-18 outcome. Not implausible, but not the most likely single draw
either. That residue is what motivates Hypothesis B.

### Hypothesis B — a stale fix placed Point A along the user's own path

Incident 12 established (finding F4) that on builds before v2.93, **Track Setup
could capture a start-line point from a stale GPS fix** — a position the user
occupied some seconds earlier. If Point A resolved to an earlier position along
the user's line of movement and Point B to their current one, the resulting
"line" is aligned with **the direction the user was moving**, which at a
start/finish is the direction of the track.

That is precisely the signature observed here: a short baseline pointing exactly
down the racing line.

The session was recorded on **v2.8**, which predates the F4 fix, so the defect
was present in this build. This cannot be confirmed from the telemetry alone,
because the app does not record *when* or *from what fix* each start-line point
was captured — see Observability Gaps below.

### Consequence for remediation

The two hypotheses are **not mutually exclusive**, and importantly they lead to
the same conclusion: **orientation must not be derived from two closely-spaced
GPS points.** Under Hypothesis A the measurement is too noisy to trust; under
Hypothesis B it can be actively wrong. The v2.93 fix removes one contributing
cause but does **not** remove the underlying design weakness.

---

## Contributing Factors

| # | Factor | Effect |
|---|--------|--------|
| C1 | `MIN_LINE_DISTANCE_M = 3.0` | Accepts baselines far shorter than the GPS error, guaranteeing meaningless orientation |
| C2 | No orientation validation anywhere | Neither capture nor detection ever asks whether the line is plausibly perpendicular to the track |
| C3 | Zero-tolerance intersection test | A geometric near-miss of 0.53 m is treated identically to being on the far side of the circuit |
| C4 | Stale-fix capture (pre-v2.93) | May have actively produced the parallel line — fixed in v2.93 by Incident 12 |
| C5 | 1 Hz telemetry on this device | Not causal here, but it caps lap-time resolution at ±1 s (see Secondary Findings) |
| C6 | Failure message blames the user | Converts a recoverable setup issue into an unexplained product failure |

---

## Secondary Findings

Found during this analysis. Each is real, none is the cause of this incident,
and **none has been changed** — this document is a record only.

### F1 — `StartLine.isValid()` uses `||` instead of `&&`

`LocalLapDetector.kt`:

```kotlin
fun isValid(): Boolean = lat1 != 0.0 || lng1 != 0.0 || lat2 != 0.0 || lng2 != 0.0
```

A line with an entirely zeroed endpoint — i.e. (0, 0), in the Gulf of Guinea —
passes validation. The check cannot fail unless *every* coordinate is zero.

### F2 — `maxDistanceFromStart` is not reset when a crossing is rejected

In `detectCrossings()`, `maxDistanceFromStart` is reset to `0.0` only when a
crossing is **accepted**. When a crossing is rejected by the minimum-time or
minimum-distance guard, the accumulated distance is carried forward into the
next candidate. A rejected short loop therefore lends its distance to the
crossing that follows, which may then pass a guard it should have failed. The
intended behaviour is not documented, so this is recorded as a discrepancy
rather than definitively a bug.

> **Correction, added during remediation — F2 was not a defect, and "fixing" it
> broke lap detection.**
>
> Resetting `maxDistanceFromStart` on a rejected candidate was implemented, and
> the diagnostics added under this incident immediately showed it losing a lap.
> Where the start/finish sits on a corner, a single pass produces two candidates
> about a second apart — the car arriving and the car leaving — because the
> detection plane rotates with the car. The arriving one is rejected on heading.
> Resetting the distance there made the *legitimate* departing crossing look as
> though it had travelled only 10 m, and the lap was discarded.
>
> The original behaviour is correct: the distance window means "since the last
> **accepted** crossing", not "since the last candidate". The change was reverted,
> the rationale is now recorded in the `detectCrossings()` KDoc, and the behaviour
> is pinned by `distanceTravelledSurvivesARejectedCandidate`.
>
> This is worth noting beyond the specific finding: the observability added in
> response to this incident caught a regression introduced by the incident's own
> remediation, within minutes, before it could ship.

### F3 — Crossing timestamps are not interpolated

The crossing time is taken as `curr.timestampMs`, the timestamp of the sample
*after* the crossing. At 1 Hz and 15–20 m/s the car travels 15–20 m between
samples, so lap times carry up to **±1 s** of quantisation error — about **1.3 %**
on a 77-second lap. For an application whose purpose is to help a driver detect
improvement, this is coarse enough to mask real gains. Linear interpolation
between the bracketing samples reduces this to roughly ±0.1 s; the lap times
quoted in this RCA are interpolated for that reason.

### F4 — Telemetry rate is hardware-limited, not application-limited

`TelemetryForegroundService` requests **`GPS_MIN_TIME_MS = 100L` (10 Hz)** and
`TelemetryFileWriter` writes **every** fix with no downsampling or averaging.
The ~1 Hz seen in this file is what the ZTE Blade A53+ GNSS stack delivered.
No application change can raise it on this device; better chipsets will deliver
a higher rate automatically, since the request is already 10 Hz. The correct
mitigation is to stop *depending* on the rate — see F3.

### F5 — SRS and implementation disagree about where detection happens

SRS **LD-01** specifies lap detection as a **server-side** responsibility, with
`LocalLapDetector` present only as an offline convenience. The operator has
since confirmed that **Delivery 1 is local-only**, with a backend as a possible
future addition. The requirement is therefore inverted relative to the product
being shipped — which plausibly explains why the on-device detector has never
been held to the quality bar of a primary feature. Three of the four incidents
in this family concern it.

### F6 — SRS LD-06 states 200 m; the code uses 50 m

`MIN_DISTANCE_FROM_START_M = 50.0`, changed under Incident 03 for kart tracks.
SRS LD-06 still says 200 m, and the KDoc immediately above the constant still
reads "200 metres" while the trailing comment says "50m works for kart tracks".
The operator has confirmed **50 m is correct**.

---

## Observability Gaps

This RCA was possible only because the operator supplied the raw telemetry by
hand. The following gaps are recorded because they determine how quickly a
recurrence could be diagnosed.

| Gap | Consequence |
|-----|-------------|
| The app records **no diagnostics about start-line capture** — no capture timestamp, no fix age, no accuracy at capture | Hypothesis B can never be confirmed or excluded from a session record |
| The app records **only accepted crossings**, never rejections or their reasons | A detection failure leaves no trace of what the detector decided or why |
| The app records **no line-quality metrics** — length, bearing, or angle to the racing line | The defect is invisible in the stored session even though it is obvious in the geometry |
| The app records **no observed GPS sample rate** | Lap-time precision varies by device with no visible explanation |

Incident **09** is the concrete cost of these gaps: the same symptom,
investigated without evidence, closed at **55 % confidence** on an unproven
hypothesis, with its telemetry not retained. It cannot now be determined whether
09 was this same defect.

---

## Pattern Across Incidents 02, 03, 09 and 13

Four incidents share the symptom "laps not detected":

| Incident | Root cause | Class |
|----------|------------|-------|
| 02 | Service teardown race destroyed the binding before detection ran | Lifecycle |
| 03 | `MIN_DISTANCE_FROM_START_M = 200` copied from the backend, too large for kart tracks | Constant unsuited to the real use case |
| 09 | Never established (55 % confidence, no evidence retained) | Unknown |
| 13 | Line orientation derived from a baseline shorter than the GPS error | Model unsuited to the real measurement |

The recurring theme is not a single bad line of code. It is that
**`LocalLapDetector` has never been validated against real recorded telemetry.**
Every previous fix was reasoned about analytically and shipped, because no
mechanism existed to replay a real session and check the answer. Incident 03
adjusted a threshold; incident 13 shows a case where **no value of any threshold
would have worked.**

The single most valuable structural change available is a **replay harness** —
the ability to run a stored session through the detector and assert the expected
lap count and times. The session attached to this incident constitutes the first
such fixture, and it has a known-correct answer: **4 laps of 79.83, 77.08,
77.26 and 83.44 seconds.**

---

## Known Limitation of the Corrected Approach

Recorded here so that it is not discovered later as a surprise.

Deriving orientation from the direction of travel means the detector triggers on
**passing the anchor point while travelling in a consistent direction**, rather
than on crossing a user-drawn segment. On a layout where the racing line passes
close to the start/finish **more than once per lap in a similar heading** — a
figure-of-eight, or an infield section running alongside the start straight — a
spurious crossing could be accepted, producing **halved lap times**.

The present failure mode is *"no laps"*. That failure mode would be *"wrong
laps"*, which is arguably worse, because a driver may believe it.

Mitigations available (none implemented):

- a **heading-consistency guard**, rejecting crossings whose direction of travel
  differs materially from the first accepted crossing of the session;
- retaining the existing `MIN_LAP_TIME_MS` and `MIN_DISTANCE_FROM_START_M`
  guards, which already suppress the closest cases;
- recording **rejected** crossings and their reasons, so that a recurrence is
  diagnosable from the session record rather than requiring a track visit.

It is also worth stating the trade openly: the corrected approach **removes the
user's ability to aim the line**. That control was never real — this session
demonstrates that GPS cannot measure a bearing across a 7 m baseline — but it is
a control the user nominally had, and it is being exchanged for automatic
behaviour that is correct in the ordinary case.

---

## Requirement Impact (assessment only — nothing amended)

| Requirement | Status | Note |
|-------------|--------|------|
| **LD-01** (detection is server-side) | **Contradicts the product** | Delivery 1 is local-only per the operator; the SRS should be inverted |
| **LD-06** (200 m minimum distance) | **Contradicts the code** | Code uses 50 m; 50 m confirmed correct by the operator |
| Track topology assumptions | **Absent** | The SRS is silent on circuit layout, so it implicitly promises support for all of them, including those the algorithm cannot handle |
| Lap-time precision | **Absent** | No requirement states the accuracy a reported lap time is expected to have |

---

## Conclusion

The session was fully recoverable. The telemetry was good, the driving was
consistent, and four laps were present in the data at the moment the application
told the driver they had completed none.

The defect is not a mistuned threshold. It is that the application asks the GPS
receiver a question it cannot answer — *"which way does this line point?"* —
across a baseline shorter than the receiver's own error, and then trusts the
answer without checking it.

---

## Status

**Analysis only.** No code, requirement, test or Atlas document has been
modified in response to this incident. Remediation is to be planned separately
with the operator.
