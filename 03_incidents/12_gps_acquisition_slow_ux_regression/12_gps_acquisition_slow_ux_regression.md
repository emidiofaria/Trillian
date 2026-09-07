# Incident: Slow GPS Acquisition (45 s) and Suspected GPS/Lap-Detection Regression Between v2.8 and v2.92

**Date Reported**: 2026-09-07
**Reported By**: Human Acceptance Testing (HAT)
**Severity**: MEDIUM (UX degradation — no data loss observed; escalate to HIGH if lap detection is confirmed affected)
**Status**: Open — awaiting RCA
**Component**: GPS Warm-Up (`LocationWarmUp`) / Track Setup start-line capture / Local Lap Detector
**Build Under Test**: `versionName "2.8"`, `versionCode 208`
**Comparison Build**: v2.92 (current `main`, `versionName "2.92"`, `versionCode 292`)

---

## Original Bug Report (verbatim, as submitted)

> bug report
>
> on Human acceptance tests we observed:
> on appp version
> "versionName": "2.8",
> "versionCode": 208
>
> GPS took 45 s to acquir signal but was working better than in our last recent app version 2.92
>
> User experience degraded.
> Please check also as well if something changed on lap detection mechanisms

---

## Description

During Human Acceptance Testing of build **v2.8 (versionCode 208)**, GPS signal
acquisition took approximately **45 seconds**.

Despite this long acquisition time, the tester reports that GPS behaviour on
**v2.8 was still better than on the most recent build, v2.92**. The overall user
experience is therefore reported as **degraded on v2.92 relative to v2.8**.

Two distinct concerns are raised:

1. **Absolute**: 45 s to acquire a GPS signal is a poor user experience in its own right.
2. **Relative (regression)**: GPS behaviour appears to have **got worse** between
   v2.8 and v2.92, i.e. after the GPS warm-up feature was introduced in v2.9.

The reporter additionally asks whether **lap detection mechanisms changed** in
the same version range, since GPS quality and start-line capture feed directly
into lap detection.

## Severity Rationale

| Aspect | Assessment |
|--------|------------|
| Data loss | None reported |
| Telemetry corruption | None reported |
| Lap times affected | **Unknown — to be determined by RCA** |
| User experience | Degraded — long wait before the user can set up the track |
| Frequency | Observed in HAT; reproduction rate not yet quantified |

Severity is provisionally **MEDIUM**. If the RCA confirms that start-line capture
or lap detection is affected, severity must be raised to **HIGH**, because an
offset start line silently corrupts every lap time in a session.

## Version Timeline (context for the regression window)

| Version | Release artefact | Change relevant to GPS / lap detection |
|---------|------------------|----------------------------------------|
| v2.8 | `DrivingCoach-v2.8-about-intro.apk`, `-helmet-artwork`, `-splash-anchor` | **Baseline under test.** No GPS warm-up. GPS acquired on Track Setup screen only. |
| v2.9 | `DrivingCoach-v2.9-gps-warmup.apk` | `98cc272` — **GPS warm-up on Home** (`LocationWarmUp`, `FusedLocationUpdates`, `GpsReadiness`, `WarmUpTimings`, `GpsAcquisitionMetricsStore`) **and a rewrite of `TrackSetupFragment` (128 lines changed) to fix the resubscribe defect**. |
| v2.91 | `DrivingCoach-v2.91-telemetry-export.apk` | `340871e` — session telemetry export (diagnostic file). No location-path change expected. |
| v2.92 | `DrivingCoach-v2.92-driver-profile.apk` | `36952fa` — local driver profile. No location-path change expected. |

**Regression window**: the only commit in this range that touches the location
data path is **`98cc272` (v2.9, GPS warm-up + Track Setup resubscribe fix)**.

## Preliminary Code Observations (not yet an RCA)

These are facts gathered from the repository to scope the investigation. They are
**hypotheses to be confirmed or falsified** by the RCA — no conclusion is drawn here.

### Lap detection algorithm

`app/src/main/java/com/drivingcoach/lap/LocalLapDetector.kt` has **not been modified
since commit `8cd7dec` ("feat: local lap detection v2 with offline support")**, which
predates v2.8. The detection algorithm and its constants are therefore **unchanged**
between v2.8 and v2.92.

### Lap detection *inputs* did change

Lap detection depends on the **start/finish line coordinates captured on the Track
Setup screen**. `TrackSetupFragment.kt` **was substantially rewritten in `98cc272`**
(v2.9). Consequently, even with an unchanged detector, lap detection behaviour can
change if the captured start line changes in quality or timing.

Relevant documented invariant (Atlas, `components.md` → GPS Warm-Up):

> **Readiness only — never a `Location`.** Handing a warm-up position to start-line
> capture would let a stale fix become Point A and silently offset every lap time in
> the session. Capture subscribes to live updates and applies its own ≤10 m gate
> (SRS TS-20).

The RCA must verify this invariant still holds in v2.92 as shipped.

### Warm-up configuration to check

| Constant | File | Value |
|----------|------|-------|
| `READY_ACCURACY_M` | `data/location/GpsReadiness.kt` | `10f` |
| `intervalMs` | `data/location/WarmUpTimings.kt` | `1000` |
| `idleCeilingMs` | `data/location/WarmUpTimings.kt` | `180_000` (3 min) |

Note the documented expectation for cold TTFF is **30–60 s (ephemeris download)**,
so the observed 45 s on v2.8 falls **inside the documented normal range** — the GPS
warm-up feature was introduced precisely to hide this latency. The RCA should
establish whether the v2.92 complaint is about *TTFF itself*, about the *readiness
chip* never turning green, or about the *3-minute idle ceiling* stopping warm-up
before the user reaches Track Setup.

## Reproduction Steps (to be executed and quantified)

1. Install `DrivingCoach-v2.8-*.apk` on the reference device.
2. Cold-start the app outdoors with clear sky view (kill process, clear location cache).
3. Navigate to Track Setup and measure **time from screen open to first usable fix**.
4. Record the value. Expected from HAT: **~45 s**.
5. Uninstall; install `DrivingCoach-v2.92-driver-profile.apk`.
6. Repeat steps 2–4 under **the same conditions** (same location, same sky view, same time window).
7. Additionally on v2.92: open Home first, observe the **GPS badge** (hidden → amber "Acquiring GPS…" → green), and record when it turns green.
8. On both builds: capture the start/finish line, drive/simulate **at least 4 laps**, stop, and record the number of laps detected.
9. **Expected**: v2.92 acquisition ≤ v2.8, and identical lap counts on both builds.
10. **Actual (reported)**: v2.92 GPS behaviour perceived as worse than v2.8.

## Evidence Needed

- [ ] Exact APK filename tested for "v2.8" (three v2.8 artefacts exist: `about-intro`, `helmet-artwork`, `splash-anchor`)
- [ ] Device model, Android API level, and whether Google Play Services Location is up to date
- [ ] Test environment: real device outdoors vs emulator with simulated GPS
- [ ] Measured TTFF on v2.8 and on v2.92 under matched conditions (≥3 runs each, cold start)
- [ ] **About screen** acquisition metrics from v2.92 (`timeToFirstFixMs`, `timeToAccurateFixMs`) — these are recorded by `GpsAcquisitionMetricsStore`
- [ ] Screenshot/video of the Home GPS badge state over time on v2.92
- [ ] Logcat covering app start → Track Setup → first fix, both builds
- [ ] Exported session telemetry (`.jsonl`) from a v2.92 session — use **Share → diagnostic file** (added in v2.91)
- [ ] Lap count and lap times for the same driven route on both builds
- [ ] Room query of the session's stored start-line coordinates on v2.92

## Open Questions / Ambiguities in the Report

These must be resolved before or during the RCA; the report as written is
ambiguous on two points:

1. **Which build is actually defective?** The report states the defect on v2.8
   (45 s) but simultaneously says v2.8 "was working better than v2.92". Read
   literally, **v2.92 is the regressed build** and v2.8 is the better baseline.
   Confirm the direction of the comparison.
2. **What does "better" mean concretely?** Faster fix, more stable fix, better
   accuracy, clearer UI feedback, or fewer lost fixes during recording? The
   remediation differs substantially per interpretation.
3. **Was the 45 s measured on Home (warm-up) or on Track Setup?** v2.8 has no
   warm-up, so only Track Setup timing is comparable across the two builds.
4. **Was lap detection actually observed to fail on v2.92**, or is this a
   precautionary check request? No lap-detection failure is reported as observed.

## Impact

- Users wait up to ~45 s before they can capture the start/finish line, at the
  trackside where time is limited.
- Perceived reliability of the app is reduced at the most safety-visible moment
  of the workflow.
- **Potential (unconfirmed)** knock-on risk: a degraded or stale first fix used
  for start-line capture would offset every lap time in the session without any
  visible error.

## Acceptance Criteria for the Fix

1. Time-to-first-fix on v2.92+ is **no worse than v2.8** under matched conditions,
   measured over at least 3 cold-start runs.
2. The Home GPS badge accurately reflects acquisition state and reaches green
   within the documented 30–60 s cold TTFF window with clear sky view.
3. The warm-up safety invariant is verified: **no warm-up `Location` ever reaches
   start-line capture**; capture uses live updates with the ≤10 m gate (SRS TS-20).
4. Lap detection produces the **same lap count and comparable lap times** on
   v2.92+ as on v2.8 for an identical route.
5. A regression test covers the acquisition/readiness path (L1 for `LocationWarmUp`
   state machine, L2 for the Home badge and Track Setup resubscribe behaviour).
6. If the 3-minute idle ceiling is a contributing factor, its behaviour is either
   corrected or explicitly documented in the User Manual.

## Related Incidents & Patterns

| Reference | Relevance |
|-----------|-----------|
| **Incident 02** — Lap detection not triggering (race condition) | FIXED — prior lap-detection failure mode |
| **Incident 03** — 200 m threshold too large | FIXED (now 50 m) — lap-detection geometry |
| **Incident 09** — No laps after 4 laps | Prior lap-detection regression report |
| **Pattern** — *GPS Lock Failure During Recording* (`failure-patterns.md`) | Directly applicable signals and evidence checklist |
| **Pattern** — *Location Subscription Not Restored After Stop* — ✅ FIXED by the GPS warm-up work (`98cc272`) | The **same commit** implicated in this regression window; check for an incomplete or over-corrected fix |
| **Pattern** — *DataStore ANR on Startup* — ✅ MITIGATED (v2.8) | `GpsAcquisitionMetricsStore` writes to DataStore; check for regression of this mitigation |

## Requirements Context

- **SRS TS-16 to TS-20** — GPS warm-up and start-line capture accuracy gate (≤10 m)
- **SRS §4** — Track setup, start/finish line
- **SRS §8** — Lap detection
- **SRS §15 (Non-functional requirements)** — responsiveness expectations

The RCA must state whether any of these requirements are violated as implemented,
or whether the requirement itself under-specifies the acceptable acquisition time.

## Notes

The GPS warm-up feature (v2.9) was introduced **specifically to make the ~45 s cold
TTFF invisible to the user** by starting acquisition on Home. A report that GPS
behaviour is *worse* after that feature shipped is therefore a strong signal that
either the warm-up is not running when expected, is being stopped prematurely, or
is interfering with the Track Setup subscription that was rewritten in the same
commit. This should be the primary line of investigation.

Lap detection code itself is unchanged; investigate its **inputs**, not its algorithm.

---

## Next Step

Hand to the **RCA agent** with this report plus the evidence listed above. Do not
implement a fix before the direction of the regression (Open Question 1) is
confirmed.

---

**RCA**: [`12_RCA_warmup_discarded_at_track_setup_handover.md`](12_RCA_warmup_discarded_at_track_setup_handover.md)
— completed 2026-09-07. No functional TTFF regression found; root cause is that
`LocationWarmUp` is stopped by `HomeFragment.onStop()` (SRS TS-18) at exactly the
Home → Track Setup navigation it exists to serve. Lap-detection algorithm and inputs
confirmed unchanged; one **new** latent risk to start-line capture is raised (Finding F4).
