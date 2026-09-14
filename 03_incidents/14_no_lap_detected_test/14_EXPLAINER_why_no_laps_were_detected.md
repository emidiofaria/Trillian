# Explainer: Why Three Laps Produced No Lap Times

| Field | Value |
|-------|-------|
| **Incident ID** | 14 |
| **Purpose** | Informative. Explains the failure for a developer new to this area of the code. |
| **Authoritative source** | [`14_RCA_start_point_fixed_while_stationary.md`](14_RCA_start_point_fixed_while_stationary.md) |
| **Status** | Fixed in v2.96 |

> This document owns no facts. Every measurement quoted here is taken from the
> RCA, and every behaviour described is implemented in `LocalLapDetector`. If
> this and the RCA ever disagree, the RCA is right and this file is stale.

---

## Read this if

You are about to change lap detection, start-line handling, or anything that
consumes a GPS fix taken while the vehicle is stopped — and you would rather not
rediscover this the hard way.

The RCA has the evidence. This has the reasoning.

---

## What happened, in one paragraph

A driver completed three laps of a kart track. The app told him
*"No laps detected. Complete at least 2 laps."* The telemetry was fine. The
start line was fine by every measure the app could take of it. The detector
found nine candidate crossings and threw all of them away, and recorded that it
had thrown away **none**. There was, on the face of it, nothing to investigate.

The start point was sitting 15.9 m to the side of the track, and the detector
was looking for the kart within 15 m of it.

---

## Why this was hard to see

Lap detection needs one thing from the driver: where the start/finish line is.
The app asks for it in the only way it reasonably can — walk to one edge of the
track, capture a point, walk to the other edge, capture a second.

That instruction quietly contains the whole bug.

**A GPS receiver is at its worst when it is not moving.** While you move, the
receiver has Doppler shift to work with: your velocity constrains the solution
and pins it down. Standing still, that constraint is gone, and the reported
position wanders. On this session it wandered **11.2 m while the kart was
parked**, from a receiver that was overlaying successive racing laps to within
**4.5 m** once the kart was rolling.

So the app asks for its single most safety-critical geometric input in the
exact condition where the sensor is least able to provide it.

### The part that defeats every check

The two endpoints were captured seconds apart. **They therefore share the same
error.** The receiver was biased in one direction for that whole window, so both
points were dragged the same way by the same amount.

Which means the line came out:

- the right **length** (8.15 m — a plausible track width)
- the right **bearing** (171.6°)
- at the right **angle to the direction of travel** (80° across it)

Every one of those is a check of the captured values **against each other**. All
of them passed, and all of them would have passed no matter how far off the pair
had drifted, because the drift was common to both.

> **An internal consistency check is not a validity check.**

The line was perfectly self-consistent, and in the wrong place. Nothing in the
session compared it against anything capable of revealing that.

### The observation that settles it

Track the reported position against the racing line over the first minute:

| Time | Where the phone said he was |
|------|------------------------------|
| 0–38 s — standing, then walking | 10–20 m to the **left** |
| 38 → 40 s — kart accelerates away | jumps **~11 m in 2 seconds** |
| 40 s onward | **0.3 m to the right** — on the racing line |

The error does not decay, drift back, or average out. It **disappears the
instant the receiver gets velocity aiding**. That is a signature, not a
coincidence.

It also settles the human question. The operator watched the tester walk to the
right of the track; the telemetry draws him on the left. That is precisely what
the table predicts. **The tester did nothing wrong.** The phone drew him in the
wrong place, and the app believed it.

---

## Why the obvious fix is the wrong fix

The tempting response is to widen the 15 m corridor. Don't:

| Corridor | Laps detected |
|----------|---------------|
| 15 m | 0 |
| 25 m | 1 |
| 35 m | 3 |

An answer that moves this much with the threshold is **measuring the threshold,
not the track**. Widening would also weaken detection for every correctly
captured session in order to rescue a badly captured one. The start point was
wrong; the corridor was doing its job.

---

## What the fix does instead

The insight is narrow and worth stating precisely:

> The driven path is the only thing in the session recorded **under velocity
> aiding**. It is therefore the only thing a stationary fix can be corrected
> against.

So when detection comes up short, the start point is projected perpendicularly
onto the path the driver actually drove, considering only stretches driven at
4 m/s or more.

The speed filter is load-bearing, not a tidy-up. Project onto the *whole* path
and you land on the out-lap — a stretch visited exactly once — which yields
**zero laps at every corridor width**.

Three gates keep this a correction rather than a search:

| Gate | Reasoning |
|------|-----------|
| Only when detection already failed (< 2 laps) | A healthy session must never reach this code |
| Only a correction under 20 m | A line 500 m out is a *different* fault and must fail loudly, not be quietly rescued |
| Only if the retry actually finds ≥ 2 laps | Otherwise the whole attempt is discarded |

**The evidence that this is a correction and not tuning:** incident 13's
session, whose line was captured while moving, would be moved **0.8 m** by the
same projection — so it never reaches the fallback and still detects its 4 laps.
Incident 14's would be moved 16.1 m, and goes from 0 laps to 3. The fix is
self-limiting because the sessions that don't need it don't trigger it.

---

## Two things that were wrong besides the geometry

### The failure was silent

`RejectionReason.TOO_FAR_TO_THE_SIDE` **already existed in the enum, was fully
documented, and was referenced nowhere in the code.** A comment had argued the
rejection away as "not noteworthy".

So a session that discarded four passes for being 16–30 m wide reported *no
rejections at all*. The diagnostics sidecar built after incident 13 — whose
entire purpose is to make the next failure explicable — was silent about the one
fact that explained everything. That cost more investigation time than the bug.

**The lesson is not "add more logging".** It is that a reason code nobody emits
is not a diagnostic, it is a comment; and that the moment to record why you
discarded something is the moment you discard it.

### The app gave advice it made impossible to follow

Worth recording because it was introduced *by this very fix* and caught only
afterwards.

The new message correctly explained that capturing while stopped is the problem,
and then ended: *"Set the start line again while driving past it."*

There is no capture-while-moving control in the app. Track Setup says *"Walk to
each edge of the track…"*. The driver was handed a correct diagnosis and an
instruction he could not carry out — which leaves him exactly as stuck as the
message it replaced, having also spent his trust.

Corrected, and guarded by
`NoLapsExplanationTest.the advice must be something the app lets you do`.

> **Advice the app gives must be advice the app lets you follow.** Diagnosis and
> remedy have to be checked against the shipped UI, not just against the code
> that generates them.

---

## The generalisation worth carrying forward

Incident 13 established that a **direction** derived while parked is noise.
This incident is the stronger claim:

> The **position itself** is biased while parked, and the bias is shared by
> everything captured in that window — so no amount of cross-checking captured
> values against each other can reveal it. A stationary fix can only be
> validated against something measured while moving.

If you are writing code that trusts a GPS fix taken at a standstill, that is the
sentence to argue with before you ship it.

---

## Where to go next

| For | Read |
|-----|------|
| The evidence, the ruled-out hypotheses, residual risk | [`14_RCA_start_point_fixed_while_stationary.md`](14_RCA_start_point_fixed_while_stationary.md) |
| What changed, and how it was validated | [`14_Provided_Fix_start_point_projected_onto_driven_path.md`](14_Provided_Fix_start_point_projected_onto_driven_path.md) |
| The original report and raw telemetry | [`14_no_laps_detected_after_3_laps.md`](14_no_laps_detected_after_3_laps.md) |
| The reusable pattern | `FP-STATIONARY-POSITION-BIAS` in `SkunkOps/atlas/failure-patterns.md` |
| The requirements | LD-02, LD-04, LD-17, LD-18 in `01_requirements/DrivingCoach_SRS_v1.md` |
| The regression fixture | `app/src/test/resources/lapfixtures/ines3/` |
