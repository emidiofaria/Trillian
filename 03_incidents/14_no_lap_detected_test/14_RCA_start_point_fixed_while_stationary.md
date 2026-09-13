# Root Cause Analysis: A Start Point Fixed While Standing Still

| Field | Value |
|-------|-------|
| **Incident ID** | 14 |
| **Application** | Driving Coach |
| **Severity** | HIGH |
| **RCA Date** | 2026-09-13 |
| **Analyst** | SW_dev agent (session with operator) |
| **APK Version** | v2.94 per `session.json` (incident report states 2.95; not material — see below) |
| **Device** | ZTE Blade A53+, Android SDK 31 |
| **Evidence** | Real recorded session — `telemetry_no_lap_detection_20260913-2045/` |
| **Reproducibility** | `LapDetectionIncident14Test` — deterministic, offline, no device required |
| **Confidence** | **HIGH (>95 %)** — the cause is measured from the recorded session, not inferred |
| **Fixed in** | v2.96 |

---

## Incident Summary

A driver completed three laps of a kart track. The application reported
*"No laps detected. Complete at least 2 laps."* — instructing a driver to do
something he had just finished doing — and produced no lap times and no
coaching output.

The telemetry was complete and of good quality. Unlike incident 13, **every
diagnostic the app records looked healthy**: the start line was 8.15 m long,
its bearing was 171.6°, it sat 80° across the direction of travel, the sample
rate was steady, the reported accuracy was unremarkable, and the detector
recorded **no rejected crossings whatsoever**. There was nothing to investigate.

---

## Statement of Root Cause

> **The application requires the start/finish line to be captured while the user
> is standing still, which is the single condition in which a consumer GPS
> receiver is least able to place itself. On this session that produced a start
> point 15.9 m to the side of the track. The detector then searched for the car
> within 15 m of that point, and correctly refused every pass the driver made.**

The deeper cause is that **both endpoints were captured seconds apart and
therefore share the same positional bias**. Every internal consistency check the
app performs compares the captured values against each other, so all of them
passed. Only the *absolute* position was wrong, and nothing in the session was
compared against anything capable of revealing it.

---

## The Measurements

All figures are computed from the recorded session.

| Signal | Value | Reading |
|--------|-------|---------|
| Racing laps overlaid on one another | median **4.5 m**, max 8.7 m | GPS is *good* while moving |
| Reported movement while the kart stood still | **11.2 m** | the same receiver, stationary |
| Scatter about its own mean during capture | 9.2 m at 7.7 m *claimed* accuracy | the claim understates the error |
| Offset of start point, **perpendicular** to track | **15.9 m** | against a 15 m corridor |
| Offset of start point, **along** the track | 6.2 m | harmless |
| Lateral offset of the four genuine passes | 16.1 / 24.7 / 30.2 / 30.2 m | all outside the corridor |
| Candidate plane hits found | 9 | all discarded |

### The decisive observation

Tracking the signed lateral offset of the reported position against the racing
line over the opening of the session:

| Time | Offset from racing line |
|------|------------------------|
| 0–38 s (stationary, then walking) | **10–20 m to the LEFT** |
| 38 → 40 s (kart accelerates away) | jumps **~11 m in 2 seconds** |
| 40 s onward | **0.3 m to the RIGHT** — on the racing line |

The bias does not decay. It **vanishes the instant the receiver gets velocity
aiding**. This is the signature of a position solution unconstrained by Doppler,
not of a user error.

### This vindicates the tester

The operator reported that the kart was parked on the **right** of the track and
that the tester walked to the right, while the telemetry shows him on the
**left**. That is exactly what the table above predicts: the tester walked right,
and the phone drew him left. The tester did nothing wrong.

---

## What Was Ruled Out, and How

| Hypothesis | Verdict | Evidence |
|------------|---------|----------|
| Poor GPS throughout | **Rejected** | Racing laps overlay within 4.5 m; accuracy is flat at ~8 m all session |
| Points A and B captured in the wrong order | **Rejected** | `midpoint()` is symmetric — A `(-0.6, 4.0)`, B `(0.6, -4.0)`, midpoint `(0,0)` either way. Since commit `566f435` the line's bearing is not used for detection at all. Asserted by `LapDetectionIncident14Test` |
| The corridor is simply too narrow | **Rejected as a fix** | Widening gives 15 m → 0 laps, 25 m → 1, 35 m → 3. An answer that depends this strongly on the threshold is measuring the threshold, not the track |
| Driver did not complete laps | **Rejected** | Three laps are plainly visible in the path, and recovered at 104.2 / 85.8 / 94.5 s |
| Incident 13's fix regressed | **Rejected** | That fix landed in 2.93; replay against HEAD still fails, and incident 13's own session still detects 4 laps |

### Note on the version discrepancy

The incident report states 2.95; `session.json` records **2.94**. Not material:
the incident 13 fix landed in **2.93**, and replaying this telemetry against HEAD
reproduces the failure exactly. This is a genuine open defect either way.

---

## Contributing Factor: The Failure Was Silent

`RejectionReason.TOO_FAR_TO_THE_SIDE` **already existed in the enum, was fully
documented, and was referenced nowhere in the code.** A comment had argued the
rejection away as "not noteworthy".

The consequence is that a session in which four passes were discarded for being
16–30 m wide reported *no rejections at all*. The diagnostics sidecar built after
incident 13 — whose entire purpose is to make the next failure explicable —
recorded silence for the one fact that explained everything.

This cost more investigation time than the bug itself.

---

## The Fix

Three parts, in `LocalLapDetector`, plus the message.

### 1. Say what was refused

Passes rejected solely for width, within `REJECTION_REPORTING_RADIUS_M` (60 m),
are now recorded with their measured distance. (LD-04)

### 2. Correct the start point against the only trustworthy evidence

The driven path is the only thing in the session recorded *under velocity
aiding*, so it is the only thing the stationary fix can be corrected against.
Where detection yields fewer than 2 laps, the midpoint is projected
perpendicularly onto that path, considering only stretches driven at
`MIN_ANCHOR_SPEED_MS` (4 m/s) or more. (LD-17)

The speed filter is **load-bearing, not an optimisation**: projecting onto the
*whole* path lands on the out-lap — a stretch visited once — and yields **0 laps
at every corridor width**. This was tested before the moving-only filter was
chosen.

### 3. Refuse to guess

Three gates keep this a correction rather than a search:

| Gate | Value | Purpose |
|------|-------|---------|
| Only when detection already failed | < 2 laps | Healthy sessions never reach it |
| Only a small correction | `MAX_ANCHOR_PROJECTION_M` = 20 m | A line 500 m out is a different fault and must fail loudly |
| Only if it actually works | retry must yield ≥ 2 laps | The retry is discarded entirely otherwise |

### 4. Tell the driver what happened

The empty-result message now reports how many passes were seen, how far to the
side the nearest went, and whether the line was captured while stationary —
each only when the evidence for it is present in that session. (LD-18)

---

## Why the Fix Is Self-Limiting

The strongest evidence that this is a correction and not a tuning exercise:

| Session | Projection would move the point | Fallback reached? | Laps |
|---------|-------------------------------|-------------------|------|
| Incident 13 (`teste3`) — captured while moving | **0.8 m** | No | 4, unchanged |
| Incident 14 (`ines3`) — captured while parked | **16.1 m** | Yes | 0 → **3** |

Incident 13's session is untouched because it never needs the fallback, not
because the fallback happens to agree with it.

---

## Requirement Impact

| ID | Change |
|----|--------|
| LD-02 | Amended — the start point is the captured midpoint *except where LD-17 replaces it* |
| LD-04 | Amended — a pass refused for width must be recorded, with its distance |
| **LD-17** | **New** — bounded projection of the start point onto the driven path |
| **LD-18** | **New** — an empty result must describe the session, not instruct the driver |

---

## Residual Risk

**The 20 m bound consumed 80 % of its budget on the one real session that needed
it** (16.1 m of 20 m). This was accepted deliberately by the operator: a session
beyond the bound now fails *loudly*, reporting how far off it was, rather than
silently. If a second session approaches the bound, the correct response is to
change the **capture workflow**, not the number.

`pathRepeats()` is calibrated on two real sessions plus shuffled controls
(real 0.15 and 0.21; shuffled 0.87 and 0.88; threshold 0.5). It answers only
"was the driver lapping a circuit?" and deliberately reports **no lap time** —
on incident 13 the strongest repeat lag is 157 s, exactly **twice** the true 79 s
lap. A check that reported that number would hand the driver a confidently wrong
answer.

---

## Monitoring

Watch the diagnostics sidecar for `anchor: PROJECTED_ONTO_PATH` appearing
*routinely* rather than exceptionally. That would indicate the capture workflow,
not the receiver, is the thing to fix.

---

## The Generalisation Worth Keeping

Incident 13 established that a **direction** derived while parked is noise.
This incident is the stronger claim:

> **The position itself is biased while parked, and the bias is shared by
> everything captured in that window — so no amount of cross-checking captured
> values against each other can reveal it. A stationary fix can only be
> validated against something measured while moving.**

Corollary, and the reason this took so long to find:

> **An internal consistency check is not a validity check.** The start line was
> perfectly self-consistent, and wrong.

Recorded as `FP-STATIONARY-POSITION-BIAS` in `SkunkOps/atlas/failure-patterns.md`.

---

## Verification

| Check | Result |
|-------|--------|
| `LapDetectionIncident14Test` | 7 tests — replays the real session, asserts 3 laps at 104.2 / 85.8 / 94.5 s |
| `LocalLapDetectorGuardsTest` | 20 m bound refuses a displaced line; incident 13 never reaches the fallback |
| `NoLapsExplanationTest` | 6 tests — the old wording cannot return |
| `LapDiagnosticsWriterTest` | The new evidence reaches disk |
| Full L1 suite | **281 tests, 0 failures** (262 before this work) |

The session that caused this incident is now a permanent regression fixture at
`app/src/test/resources/lapfixtures/ines3/`.
