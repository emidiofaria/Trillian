# Implementation Plan: One-Tap Start/Finish Capture

> **Plan ID:** PLAN-003  
> **Created:** 2026-09-08  
> **Status:** Backlog — not scheduled  
> **Priority:** Medium  
> **Estimated Effort:** 2–3 days  
> **Origin:** Incident 13 — *No laps detected: start line parallel to direction of travel*

---

## 1. Executive Summary

Replace the two-point start/finish capture in Track Setup with a **single tap taken from the driving position**, and let the app derive the crossing plane from the direction the car is actually travelling.

This plan is **deliberately not part of the incident 13 fix**. That fix changes only the detector, so no user has to relearn anything and no released behaviour changes shape. This plan is the follow-up that removes the underlying awkwardness in the capture step itself.

---

## 2. Problem Statement

### Current state

Track Setup asks the user to walk to **each edge** of the start/finish and capture a point at both. The app stores the two points and, until the incident 13 fix, treated the segment between them as the line a car must cross.

Two things are wrong with this:

1. **It asks for a measurement the phone cannot make.** The two points are typically 5–10 m apart. GPS accuracy in the incident 13 session averaged 4.8 m and reached 15.0 m. The *direction* of a line derived from two points that close together is dominated by noise, not by where the user stood. In that session the captured line came out within 0.1°–5.1° of the direction of travel — pointing **along** the track rather than across it.

2. **It costs the user real effort at the track.** Two walks, in racing gear, before a session, to produce a figure the app can obtain for free from the telemetry it is already recording.

### Desired state

- The driver taps once, sitting in the car, at the start/finish.
- The app records the position and the heading, and derives the crossing plane perpendicular to the direction of travel.
- The capture step takes seconds and cannot be aimed wrongly, because there is nothing to aim.

---

## 3. Why This Is Not Urgent

The incident 13 fix already makes the detector ignore the captured *orientation* and derive the crossing plane from the car's direction of travel. That means:

- Sessions recorded with a badly captured line are now detected correctly.
- The two captured points are still used, but only for their **midpoint**, which is a position — the thing GPS *can* measure.

So the defect is closed. What remains is that the UI still asks for a second point it no longer meaningfully uses. That is a user-experience debt, not a functional defect.

---

## 4. Scope

**In scope**

- Track Setup: single capture step, from the driving position.
- Persisting a captured heading alongside the position.
- Migration for sessions and tracks captured with two points.
- Updating the User Manual and the L4 acceptance tests for Track Setup.

**Out of scope**

- Any change to `LocalLapDetector`'s crossing geometry. The detector after incident 13 already derives its plane from travel direction and does not need a stored heading.
- Retroactive re-detection of stored sessions.

---

## 5. Open Questions

1. **Where does the heading come from at capture time?** The car is stationary at the start/finish when the user taps, so there is no travel bearing to take. Options: use the device compass (unreliable in a metal car body), have the driver do one slow lap first, or take the heading from the first pass of the session and store it after the fact. **The third is the most promising**: nothing needs to be captured at all, and the geometry comes from real motion.

2. **Does a stored heading add anything over deriving it per-pass?** **No — this has now been measured.** `OneTapStartFinishCaptureTest` shows that replacing the two captured points with a single point at their midpoint produces *identical* lap times on the incident 13 session, because the detector derives its crossing plane from travel direction on every pass. **This plan therefore collapses to "remove the second capture step from Track Setup"**, with no detector work at all. The estimate above is conservative for that reason.

3. **What replaces the second point for track *width*?** Nothing: `DETECTION_HALF_WIDTH_M` is a fixed 15 m, chosen because results were identical across 10–25 m in the incident 13 replay. If a venue ever needs a different value it should be a setting, not a walk.

---

## 6. Validation

- A replay fixture recorded through the new one-tap flow, added to `LapReplayHarness`, producing the same lap times as the equivalent two-point session.
- `05_tests/L4_SYS5_acceptance/30_TRACK_SETUP_TESTS.md` updated for the new flow.
- The existing incident 13 fixture must continue to pass unchanged, proving old sessions are unaffected.

---

## 7. References

- `03_incidents/13_no_laps_detected_start_line_parallel_to_travel/` — incident report and RCA
- `app/src/main/java/com/drivingcoach/lap/LocalLapDetector.kt` — the detector as fixed
- `app/src/test/java/com/drivingcoach/lap/LapReplayHarness.kt` — replay harness for fixtures
