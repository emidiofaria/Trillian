# Provided Fix: Start Point Projected onto the Driven Path

| Field | Value |
|-------|-------|
| **Incident ID** | 14 |
| **Application** | Driving Coach |
| **Severity** | HIGH |
| **Fix Date** | 2026-09-13 (message correction 2026-09-14) |
| **Engineer** | SW_dev agent, in session with the operator |
| **Shipped in** | v2.96 |
| **Commits** | *"Correct a start point fixed while the phone was standing still"* (fix), *"Make the release evidence describe the build it ships with"* (release tooling), *"Stop telling the driver to do something the app has no button for"* (message correction). Cited by message rather than SHA: this branch is squash-merged, so its per-commit hashes do not survive onto `main`. |
| **RCA** | [`14_RCA_start_point_fixed_while_stationary.md`](14_RCA_start_point_fixed_while_stationary.md) |

> Informative record of what changed. The RCA owns the evidence; this owns the
> change. Where a number appears in both, the RCA is authoritative.

---

## Incident Summary

A driver completed three laps of a kart track and the app reported
*"No laps detected. Complete at least 2 laps."* The start/finish line was
captured while standing still, which placed it **15.9 m to the side of the
track**. The detector searched within 15 m of that point and correctly refused
all four genuine passes — then recorded no rejections at all, leaving the
failure with no visible cause.

---

## Root Cause

The app requires the start/finish line to be captured at a standstill, the
condition in which a consumer GPS receiver is least able to place itself. Both
endpoints were captured seconds apart and so **share the same positional bias**.
Every validity check the app performs compares the captured values against each
other — length, bearing, angle to travel — so all of them passed on a line that
was 15.9 m out of position.

---

## Fix Strategy

Correct the start point against the only data in the session recorded under
velocity aiding — the driven path — and refuse to do so unless the correction is
small, needed, and demonstrably effective.

Rejected alternatives and why:

| Considered | Rejected because |
|------------|------------------|
| Widen the detection corridor | 15 m → 0 laps, 25 m → 1, 35 m → 3. Measures the threshold, not the track, and weakens every well-captured session |
| Project onto the entire driven path | Lands on the out-lap, a stretch driven once. **0 laps at every corridor width** |
| Re-derive the line from its bearing | Bearing is not used for detection since `566f435`, and is derived from the same biased pair |
| Change the capture workflow | Correct long-term answer, out of scope for a fix to a shipped defect. Recorded as residual risk in the RCA |

---

## Files Changed

*"Correct a start point fixed while the phone was standing still"* — 23 files, +2154 / −21.

### Production code

| File | Change |
|------|--------|
| `lap/LocalLapDetector.kt` | +299. New constants, `AnchorSource`, private `Anchor`, 5 diagnostics fields, `lateralOffsetM` on `RejectedCrossing`, `projectOntoDrivenPath()`, `pathRepeats()`, gated fallback |
| `lap/NoLapsExplanation.kt` | **New**, +64. The empty-result message, extracted from the ViewModel so it can be tested directly |
| `ui/recording/RecordingViewModel.kt` | +3/−1. Delegates to `NoLapsExplanation.of(...)` |
| `util/GeoUtils.kt` | +15. Public `fromLocalMetres()` — the inverse of the existing projection, needed to convert a corrected point back to lat/lon. Lives here because `EARTH_RADIUS_M` is private |
| `app/build.gradle.kts` | Version 2.95 → 2.96 |

### Tests

| File | Change |
|------|--------|
| `LapDetectionIncident14Test.kt` | **New**, 7 tests. Replays the real session |
| `NoLapsExplanationTest.kt` | **New**, 7 tests. The old wording cannot return |
| `LocalLapDetectorGuardsTest.kt` | **New**, guards the 20 m bound and the no-op on incident 13 |
| `LapDiagnosticsWriterTest.kt` | +31. Pins that new diagnostics fields reach disk |
| `GeoUtilsTest.kt` | +22. `toLocalMetres`/`fromLocalMetres` round-trip |
| `lapfixtures/ines3/` | **New**. The incident session, now a permanent regression fixture |

### Documentation

`SRS_v1.md`, `atlas/{components,flows,failure-patterns}.md`,
`docs/USER_MANUAL.md`, `docs/SYNC_REPORT.md`, `05_tests/coverage-map.tsv`.

---

## Code Changes

### 1. Record what was refused (LD-04)

`RejectionReason.TOO_FAR_TO_THE_SIDE` existed in the enum, was documented, and
was **referenced nowhere**. It is now emitted, with the measured distance, for
passes rejected solely for width within `REJECTION_REPORTING_RADIUS_M` (60 m).

The radius exists so the diagnostics describe the start straight rather than
every point on the circuit.

### 2. Project the start point onto the driven path (LD-17)

Where detection yields fewer than 2 laps, the captured midpoint is projected
perpendicularly onto the driven path, considering only stretches at
`MIN_ANCHOR_SPEED_MS` (4 m/s) or above.

The speed filter is **load-bearing**: without it the projection lands on the
out-lap and detection returns 0 laps at every corridor width. This was measured
before the filter was chosen, not assumed.

### 3. Three gates (LD-17)

| Gate | Constant | Purpose |
|------|----------|---------|
| Detection already failed | `< 2` laps | Healthy sessions never reach the fallback |
| Correction is small | `MAX_ANCHOR_PROJECTION_M` = 20 m | A line 500 m out is a different fault and must fail loudly |
| Retry actually works | `≥ 2` laps | Otherwise the retry is discarded entirely |

`AnchorSource` records which path was taken, so the diagnostics say whether the
fallback fired.

### 4. Describe the session instead of instructing the driver (LD-18)

The message now reports how many passes were seen, how far to the side the
nearest went, and whether the line was captured while stationary — **each only
when the evidence for it is present in that session**. With no passes recorded
it names no distance, because inventing one is worse than saying little.

Extracted from `RecordingViewModel` deliberately: there is no
`RecordingViewModelTest`, and the old wording had survived for months precisely
because nothing asserted on it.

### 5. Correction after release — advice the app makes impossible

v2.96 shipped this message ending *"Set the start line again while driving past
it."* Track Setup captures both points on foot — *"Walk to each edge of the
track…"* — and offers no capture-while-moving control anywhere.

The driver received a correct diagnosis followed by an instruction he could not
carry out. Found while writing this document; corrected in place at the
operator's decision, keeping v2.96.

Now reads: *"Set it again from the track edges, and wait for the GPS signal to
settle before you capture each point."*

Guarded by `NoLapsExplanationTest.the advice must be something the app lets you
do`, which rejects "while driving", "as you drive", "driving past" and "while
moving" across every message branch. The guard matters more than the wording:
the same mistake was caught and removed from `USER_MANUAL.md` during the docs
sync but missed in the code, which is the copy a driver actually reads.

---

## Requirement Impact Analysis

### Requirements Affected

**YES.**

| ID | Change |
|----|--------|
| LD-02 | Amended — the start point is the captured midpoint *except where LD-17 replaces it* |
| LD-04 | Amended — a pass refused for width must be recorded, with its distance |
| **LD-17** | **New** — bounded projection of the start point onto the driven path |
| **LD-18** | **New** — an empty result must describe the session, not instruct the driver |

### Details

LD-17 and LD-18 change user-visible behaviour: a session that previously
produced nothing may now produce lap times, and the empty-result text is
different. No API, persistence, permission or privacy behaviour changes. The
projection runs only on sessions that already failed, so there is no cost on the
healthy path.

### Human Approval Required

**YES — obtained.** The operator approved the plan before implementation, the
20 m bound explicitly, and the post-release message correction.

LD-18 and the 2.96 version bump were agent decisions beyond the approved plan
and were flagged as such.

---

## Validation Results

### Compilation

**PASS** — `compileDebugKotlin`, `compileDebugAndroidTestKotlin`.

### Tests

**PASS.**

| Level | Result |
|-------|--------|
| L1 (SWE.4 unit) | **282 / 282**, 0 failures (262 before this work; 281 after the detector fix, +1 for the advice guard) |
| L2 (SWE.5 integration) | **60 passed**, 4 `@Ignore`d (pre-existing) |

Behavioural outcomes:

| Session | Before | After |
|---------|--------|-------|
| `ines3` (incident 14) | 0 laps | **3 laps** at 104.2 / 85.8 / 94.5 s |
| `teste3` (incident 13) | 4 laps | **4 laps, unchanged** — never reaches the fallback |

### Runtime Validation

**PASS** — clean rebuild, cold-booted emulator, full L1 + L2 green. Packaged to
`releases/v2.96-stationary-start-point/`.

Two environmental findings during validation, neither a code regression:

- An L2 run failed 29 tests with `RootViewWithoutFocusException`. The headless
  emulator's screen was timing out while idle; holding the device awake for the
  run cleared it.
- `AboutScreenTest` reported `2.94 (294)` against a manifest reading 2.96.
  **Not a stale install.** `BuildConfig.VERSION_NAME` is a compile-time constant
  that Kotlin inlines at call sites, and an incremental build left
  `AboutFragment` holding the old value — the dex contained *both* literals. A
  clean rebuild removed it. `AboutScreenTest` caught this unaided, so no new
  guard was added; the remedy is procedural: **a release build must be a clean
  build.**

---

## Regression Risk Analysis

| Risk | Assessment |
|------|------------|
| Healthy sessions altered | **Low.** The fallback is unreachable at ≥ 2 laps. Guarded by incident 13's fixture |
| Fallback rescues a genuinely bad session | **Low.** The retry must itself find ≥ 2 laps or it is discarded |
| 20 m bound too tight or too loose | **Accepted risk.** The only real session needing it used 16.1 m — 80 % of budget. Beyond the bound the session now fails *loudly*, reporting the distance |
| `pathRepeats()` misreads a session | **Bounded by design.** Calibrated on 2 real sessions plus shuffled controls (real 0.15 / 0.21; shuffled 0.87 / 0.88; threshold 0.5). Returns Boolean only — on incident 13 its strongest lag is 157 s, exactly **twice** the true 79 s lap. Reporting that number would hand the driver a confidently wrong answer |
| `pathRepeats()` cost | Computed only when `lapCount < 2`, avoiding an O(n²) sweep on healthy sessions. Two tests document the laziness |

### What could still fail

A start line captured more than 20 m out will not be corrected. That is
deliberate — but it now fails with an explanation rather than in silence.

### What should be monitored

`anchor: PROJECTED_ONTO_PATH` appearing **routinely** rather than exceptionally
in the diagnostics sidecar. That would mean the capture workflow, not the
receiver, is the thing to fix.

---

## Atlas Update Recommendations

### Updated

- `components.md` — fallback section and 5 new constants
- `flows.md` — fallback branch and rejection recording

### Pattern added

`FP-STATIONARY-POSITION-BIAS` — a stationary GPS fix is biased, the bias is
shared by everything captured in that window, and it can only be revealed by
comparison with something measured while moving.

---

## Conclusion

The root cause is corrected rather than masked, and the correction is
self-limiting: the session that needed it moves 16.1 m and recovers 3 laps; the
session that did not need it moves 0.8 m and never reaches the code.

Two secondary faults mattered as much as the geometry. A reason code that nobody
emitted made the failure invisible, and a message that diagnosed correctly then
misdirected the remedy would have left the driver as stuck as before. Both are
now guarded by tests, because both had previously survived on the strength of
someone being sure they were fine.
