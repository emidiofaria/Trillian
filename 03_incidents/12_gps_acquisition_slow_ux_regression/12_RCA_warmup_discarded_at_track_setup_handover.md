# RCA — Incident 12: Slow GPS Acquisition (45 s) and Suspected GPS / Lap-Detection Regression Between v2.8 and v2.92

| Field | Value |
|-------|-------|
| **Incident** | `03_incidents/12_gps_acquisition_slow_ux_regression/` |
| **RCA Date** | 2026-09-07 |
| **Analyst** | RCA Engine |
| **Build under test (reported)** | `versionName "2.8"`, `versionCode 208` |
| **Comparison build** | v2.92 (`main`, `bc14212`) |
| **Regression window** | `98cc272` (v2.9) — the only commit in v2.8 → v2.92 touching the location path |
| **Status** | Root cause identified — MEDIUM-HIGH confidence; two sub-findings require field data |
| **Severity** | **MEDIUM** for the reported UX regression. **Raised to HIGH** for one newly-discovered latent defect in start-line capture (Finding F4) that was *not* in the incident report |

---

## Incident Summary

Human Acceptance Testing reported a ~45 s GPS acquisition on **v2.8**, while simultaneously
stating that v2.8 "was working better than in our last recent app version 2.92" — i.e. read
literally, **v2.92 is the regressed build**. The reporter also asked whether lap detection
mechanisms changed in the same range.

Code analysis **falsifies a functional acquisition regression** between v2.8 and v2.92. The
only commit touching the location path, `98cc272`, strictly *removed* a defect that could
leave Track Setup stuck on "Acquiring GPS…" permanently, and added a warm-up that starts the
GNSS chip earlier. Nothing in the diff makes time-to-first-fix longer. The one measurable
timing delta is in the *opposite* direction of the complaint and is bounded at 500 ms.

What **did** regress is the *promise* the v2.9 feature makes to the user, and it regressed
for a structural reason that is visible in the code: **`LocationWarmUp` is bound to the
visibility of the Home screen, and the only navigation that matters — Home → Track Setup —
satisfies its stop condition.** `HomeFragment.onStop()` calls `LocationWarmUp.stop()`
(`HomeFragment.kt:81-84`), which cancels the collection job, removes the fused-location
request (`FusedLocationUpdates.kt:64`) and resets readiness to `Idle`
(`LocationWarmUp.kt:107-119`). Track Setup then subscribes from scratch
(`TrackSetupFragment.kt:218-225`) and paints a **red "Acquiring GPS…"** indicator with
CAPTURE disabled until *its own* first fix lands (`TrackSetupFragment.kt:126, 172-181`).

The user is therefore shown the acquisition wait **twice**: once on Home as an amber chip
they were told to wait on, then again on Track Setup after the chip has gone green — which
is precisely the outcome `USER_MANUAL.md:150-151` promises will not happen ("Track Setup will
be ready the moment you arrive, instead of making you stand there watching a spinner").
v2.8 had no chip and made no such promise; it simply showed one wait. **A single unavoidable
wait, shown once and unpromised, is experienced as better than the same wait shown twice
after a green "ready" signal.** That is a sufficient and complete explanation of "worse in
v2.92" without any TTFF regression existing.

The `LocationWarmUp` KDoc states the opposite of what the code does:

> "Process-scoped, holding no context, so warmth survives navigation between Home and Track
> Setup instead of being torn down and re-acquired at the worst possible moment."
> — `LocationWarmUp.kt:37-38`

The class *is* process-scoped (`@Singleton`, `LocationModule.kt:44-46`), so it *could* have
survived navigation. Its caller cancels it anyway.

---

## Impact

| Metric | Value |
|--------|-------|
| **Users affected** | All v2.9 – v2.92 users who follow the documented workflow (open app early, wait for green, walk to the line) |
| **Duration** | Since `98cc272` (v2.9, 2026-08-26) — three releases |
| **Data loss** | None observed |
| **Telemetry corruption** | None observed |
| **Lap times affected** | **Not by the reported symptom.** See Finding F4 for a separate, unreported latent risk |
| **Functional loss** | None. Acquisition still completes; capture still works |
| **Severity** | MEDIUM (reported UX regression) / HIGH (Finding F4, latent, unobserved) |

---

## Method

Three independent lines of evidence, in the order they were used:

1. **Atlas localisation** — `failure-patterns.md` (Location Subscription Not Restored,
   GPS Lock Failure, DataStore ANR), `components.md:107-167`, `flows.md:360-415`,
   `system.md`. Matched the incident to the GPS Warm-Up component and the Track Setup
   capture flow *before* reading source.
2. **Source verification at HEAD** — every Atlas and incident-report claim re-read against
   the checked-in code, with line citations. Atlas claims were **not** taken on trust; two
   were found incomplete (see *Atlas Discrepancies*).
3. **Differential git analysis** — `98cc272` diffed line-by-line against its parent for the
   two files that can change GPS behaviour (`TrackSetupFragment.kt`) and object-hash
   comparison for the file that must not have changed (`LocalLapDetector.kt`).

The full L1 suite was executed to establish that the regression is invisible to it:

```
./gradlew testDebugUnitTest --offline
→ 203 tests, 0 failures, 0 skipped   (16 result XMLs, app/build/test-results/testDebugUnitTest/)
```

No emulator or device was available, so every conclusion that depends on real GNSS timing is
labelled **UNVERIFIABLE WITHOUT FIELD DATA** and listed in *Field Evidence Required*.

---

## System Localization

```
SYSTEM LOCALIZATION
- Pattern match:      Partial — "Location Subscription Not Restored After Stop" (✅ FIXED)
                      is the *inverse* of this incident: the same commit that fixed it
                      introduced a new, narrower subscription discontinuity at a
                      navigation boundary rather than at a lifecycle boundary.
- Primary component:  GPS Warm-Up (LocationWarmUp) — components.md:107-167
- Secondary component:Track Setup start-line capture — TrackSetupFragment/ViewModel
- Affected flow:      "GPS Warm-Up (Home)" — flows.md:360-415
- Failure stage:      The transition OUT of that flow. flows.md documents
                      HomeFragment.onStop() → stop() as a terminal step and does NOT
                      document the Home → Track Setup handover at all. The failure lives
                      exactly in the undocumented gap.
- Async boundaries:   callbackFlow (FusedLocationUpdates), @ApplicationScope job
                      (LocationWarmUp), delay(180 s) ceiling, StateFlow +
                      repeatOnLifecycle(STARTED) in both fragments, FragmentManager
                      navigation transaction ordering
- Persistence:        DataStore `driving_coach_prefs` (TTFF metrics only);
                      Room SessionEntity (start-line coordinates, downstream)
```

---

## Timeline (reconstructed workflow, v2.92)

| Time | Event | Evidence |
|------|-------|----------|
| T+0 s | Splash resolves, Home becomes STARTED | `SplashViewModel.kt:76-90`, `HomeFragment.kt:76-79` |
| T+0 s | `startGpsWarmUp()` → `LocationWarmUp.start()`; readiness = `Acquiring`; chip amber | `HomeViewModel.kt:84`, `LocationWarmUp.kt:97`, `HomeFragment.kt:238-242` |
| T+0 s | Fused request issued, `PRIORITY_HIGH_ACCURACY`, 1 s interval | `FusedLocationUpdates.kt:48-62` |
| T+~30–60 s | First fix ≤ 10 m → readiness = `Ready`; chip green; TTFF written to DataStore | `LocationWarmUp.kt:124-151` |
| — | User (following `USER_MANUAL.md:150`) walks to the line and taps START SESSION | `HomeFragment.kt:99-101` |
| T+X | Navigation Home → Track Setup; **Home stops** | `HomeFragment.kt:294-296` |
| T+X | `stopGpsWarmUp()` → `stop()`: job cancelled, `removeLocationUpdates()`, readiness = `Idle` | `HomeViewModel.kt:87`, `LocationWarmUp.kt:110-116`, `FusedLocationUpdates.kt:64` |
| T+X | Track Setup subscribes independently; state starts `isGpsReady = false` | `TrackSetupFragment.kt:218-225`, `TrackSetupViewModel.kt:47-48` |
| T+X | Screen paints **red** "Acquiring GPS..." and disables CAPTURE | `TrackSetupFragment.kt:126, 172-181` |
| T+X+Δ | First Track Setup fix arrives → green, CAPTURE unlocks | `TrackSetupFragment.kt:229-234`, `TrackSetupViewModel.kt:53-61` |

**Δ is the disputed quantity.** It is the re-acquisition time after a hot release/re-request
of the receiver. It is **not** a cold TTFF (the GNSS engine retains ephemeris for hours) —
but it is also **not zero**, and it is displayed to the user in the same red "Acquiring GPS…"
language that a cold start uses. Δ is UNVERIFIABLE WITHOUT FIELD DATA.

**Additional path (idle ceiling):** if the user does what `USER_MANUAL.md:139` instructs
("open the app a couple of minutes before you walk out") and lingers, the 180 s ceiling
(`WarmUpTimings.kt:20`) fires `stop()` (`LocationWarmUp.kt:154-161`), readiness → `Idle`,
and the chip **disappears entirely** (`HomeFragment.kt:233-236`). Nothing restarts it while
Home remains STARTED — `start()` is only called from `HomeFragment.onStart()`. The user's
guidance window (2 minutes) and the ceiling (3 minutes) are 60 s apart.

---

## Code Verification

Every claim in the incident report and in Atlas, checked against HEAD.

| Claim (source) | Code location | Actual | Verified |
|---|---|---|---|
| `READY_ACCURACY_M = 10f` (report §Warm-up config) | `GpsReadiness.kt:28` | `const val READY_ACCURACY_M = 10.0f` | ✅ |
| `intervalMs = 1000`, `idleCeilingMs = 180_000` | `WarmUpTimings.kt:19-20` | `1000L`, `180_000L` | ✅ |
| `LocationWarmUp` is a true singleton | `LocationWarmUp.kt:42-43`, `LocationModule.kt:27-29` | `@Singleton` on class; `LocationUpdates` bound `@Singleton` to `FusedLocationUpdates` | ✅ |
| Application scope is process-lived and never cancelled | `LocationModule.kt:41-46`, `ApplicationScope.kt` | `CoroutineScope(SupervisorJob() + ioDispatcher)`, `@Singleton`, no cancellation site | ✅ |
| `start()` is idempotent | `LocationWarmUp.kt:90-92` | `restartIdleCeiling(); if (updatesJob != null) return` | ✅ |
| Warm-up stops when Home is not visible (SRS TS-18) | `HomeFragment.kt:81-84` → `HomeViewModel.kt:87` → `LocationWarmUp.kt:107-119` | Confirmed; also removes the fused request via `awaitClose` | ✅ |
| `removeLocationUpdates` is actually called | `FusedLocationUpdates.kt:64` | `awaitClose { client.removeLocationUpdates(callback) }` | ✅ |
| Warm-up never carries a `Location` (SRS TS-20 / Atlas invariant) | `LocationUpdates.kt:36-37`, `GpsReadiness.kt:11-20`, `LocationWarmUp.kt:121-152` | `updates()` maps to `LocationFix(accuracy, timestamp)`; no coordinate field exists on the warm-up path | ✅ |
| Capture uses live updates with its own ≤10 m gate | `TrackSetupFragment.kt:229-234`, `TrackSetupViewModel.kt:53-61` | `currentLocation` set only from `positionUpdates()`; gate `accuracy ≤ 10 && satellites ≥ 4` | ✅ |
| `LocalLapDetector.kt` unchanged since `8cd7dec` | `git log --follow` | Exactly one commit: `8cd7dec` | ✅ |
| Detector byte-identical v2.8 → HEAD | `git rev-parse` | `d2b80df…` at `98cc272^` **and** at `HEAD` | ✅ |
| `98cc272` is the only location-path commit in range | `git show --stat 98cc272`, `git log` v2.8→HEAD | `340871e` (export) and `36952fa` (profile) touch no `data/location` or track-setup file | ✅ |
| Warm-up KDoc: "warmth survives navigation between Home and Track Setup" | `LocationWarmUp.kt:37-38` vs `HomeFragment.kt:81-84` | **Contradicted by the caller** | ❌ |
| Atlas: idle ceiling "re-entering Home restarts" | `components.md:167` vs `LocationWarmUp.kt:154-161` | True only if the user *leaves and returns*; sitting on Home never restarts it | ⚠️ incomplete |
| Atlas `flows.md` documents the warm-up flow | `flows.md:360-415` | Documents start, fix handling, ceiling, `onStop`. **Does not document the Home → Track Setup handover** | ⚠️ gap |

---

## Differential Analysis: what `98cc272` actually changed in the acquisition path

`git --no-pager show 98cc272 -- .../TrackSetupFragment.kt` (128 lines changed):

| Aspect | v2.8 | v2.92 | Effect on TTFF |
|---|---|---|---|
| Provider | `FusedLocationProviderClient` created in `onViewCreated` from `requireActivity()` | Injected `LocationUpdates` singleton (`FusedLocationUpdates`) | None; same client underneath |
| Priority | `PRIORITY_HIGH_ACCURACY` | `PRIORITY_HIGH_ACCURACY` | **Unchanged** |
| Interval | `1000 ms` | `1000 ms` (`TrackSetupFragment.kt:289`) | **Unchanged** |
| Min update interval | `setMinUpdateIntervalMillis(500L)` | `setMinUpdateIntervalMillis(intervalMs)` = `1000L` (`FusedLocationUpdates.kt:49`) | v2.8 could receive a fix up to **500 ms sooner**. Only measurable timing regression found. Bounded at 0.5 s |
| `setWaitForAccurateLocation` | `false` | `false` (`FusedLocationUpdates.kt:52`) | **Unchanged** |
| Subscription lifetime | subscribe once in `onViewCreated`, remove in `onStop` — **never resubscribed** | `repeatOnLifecycle(STARTED)` (`TrackSetupFragment.kt:221-225`) | **Strictly better**: fixes a permanent stall |
| Capture gate | `accuracy ≤ 10 && satellites ≥ 4` | identical — `TrackSetupViewModel.kt` untouched by `98cc272` (`git log` shows last change `8cd7dec`) | **Unchanged** |
| Satellite estimate | inline `when` on accuracy | extracted `satelliteCountOf()` (`TrackSetupFragment.kt:241-250`) — identical thresholds | **Unchanged** |
| Permission prompt | re-prompted on every screen restart | guarded by `permissionRequested` (`TrackSetupFragment.kt:53`) | Better |

**Net**: one bounded 500 ms cadence regression, one significant defect removed, no change to
priority, gate, or capture semantics. The commit's own message anticipated this incident:

> "TrackSetupFragment subscribed to location exactly once from onViewCreated() while onStop()
> removed updates. … any screen-off or app switch left 'Acquiring GPS…' on screen permanently.
> … **This may well be what the 45 s actually was.**" — `98cc272`

---

## Hypotheses

### H1 — Warm-up is cancelled by navigating Home → Track Setup, so the user waits anyway (and possibly longer)

**CONFIRMED in mechanism; PARTIALLY FALSIFIED in consequence.**

*Confirmed*: `HomeFragment.onStop()` → `stopGpsWarmUp()` → `LocationWarmUp.stop()` cancels
`updatesJob`, which completes the `callbackFlow` and runs
`awaitClose { client.removeLocationUpdates(callback) }` (`FusedLocationUpdates.kt:64`), and
sets `_readiness.value = Idle` (`LocationWarmUp.kt:116`). Track Setup then calls
`locationUpdates.positionUpdates(1000L)` (`TrackSetupFragment.kt:224`), which is a **cold
flow** — a *new* `LocationRequest` and a *new* `LocationCallback`
(`FusedLocationUpdates.kt:42-62`). There is **no sharing, no replay and no state carry-over**
between the two subscriptions. The readiness `StateFlow` that held `Ready(±4 m)` a moment
earlier is reset to `Idle` and is not consumed by Track Setup at all.

*Falsified*: the claim that this makes acquisition **worse than v2.8**. Releasing and
re-requesting the receiver within the same second does not discard downloaded ephemeris; the
subsequent acquisition is a hot start, not a cold one. The physical wait in v2.92 is
therefore ≤ the v2.8 wait in every ordering. What is worse than v2.8 is not the *wait* but
the *contract*: v2.8 never told the user GPS was ready.

*Ordering caveat, honestly stated*: whether Home's `onStop()` runs before or after Track
Setup's first `positionUpdates()` subscription depends on FragmentManager transaction
ordering under `setReorderingAllowed(true)` (which the Navigation component uses) and on
whether a transition animation defers the exiting fragment's teardown. Both orderings are
possible in principle. **This is UNVERIFIABLE WITHOUT FIELD DATA** — a logcat trace of
`LocationWarmUp: warm-up stopped` against the Track Setup subscription is required. It
matters only for the size of Δ, not for the existence of the discontinuity: in *either*
ordering the warm-up's request is removed and its readiness is discarded.

**Confidence: HIGH (90 %) on mechanism. HIGH (85 %) that it does not increase physical TTFF
versus v2.8. MEDIUM (55 %) on the size of Δ.**

### H2 — The 3-minute idle ceiling stops warm-up before the user reaches Track Setup

**CONFIRMED as a real defect; MEDIUM as a contributor to this specific report.**

`restartIdleCeiling()` launches `delay(180_000) → stop()` (`LocationWarmUp.kt:154-161`).
`stop()` sets readiness to `Idle`, and `updateGpsChip` responds by hiding the chip entirely
(`HomeFragment.kt:233-236`). Nothing re-arms it: `start()` has exactly two call sites —
`HomeFragment.onStart()` (`HomeFragment.kt:78`) and nothing else. A user sitting on Home
therefore sees the chip go **green → gone**, with no explanation, and is then in exactly the
v2.8 situation. Per `USER_MANUAL.md:146`, a hidden chip means "usually location permission
isn't granted" — so the manual actively mis-explains the state the ceiling produces.

The conflict is documented on both sides: `USER_MANUAL.md:139` says open the app "a couple of
minutes" early; `USER_MANUAL.md:156` says searching stops "after 3 minutes of sitting on
Home". The guidance and the timeout are 60 s apart, in a paddock, where users are not
watching a stopwatch.

Whether this fired in the reported HAT run is unknown — it depends on dwell time, which was
not recorded.

**Confidence: HIGH (92 %) that the behaviour exists as described. LOW-MEDIUM (40 %) that it
caused this particular report.**

### H3 — The TS-20 safety invariant is violated (a warm-up `Location` reaches start-line capture)

**FALSIFIED as stated — but a different, unguarded staleness path exists (see F4).**

The invariant is enforced *by type*, not by convention. `LocationUpdates.updates()` maps every
`Location` to `LocationFix(accuracyM, timestampMs)` (`LocationUpdates.kt:36-37, 44-47`) — the
warm-up literally cannot obtain a coordinate. `GpsReadiness` carries only an accuracy
(`GpsReadiness.kt:20`). `LocationWarmUp` never references `android.location.Location`. The
only consumer of warm-up output is `HomeViewModel.gpsReadiness` (`HomeViewModel.kt:81`),
consumed only by the chip (`HomeFragment.kt:213-214`). `TrackSetupFragment` sets
`currentLocation` at exactly one site — `onLocation()`, fed by `positionUpdates()`
(`TrackSetupFragment.kt:229-230`).

`grep -rn "LocationWarmUp\|GpsReadiness" app/src/main/java` returns no reference from any
track-setup or recording file. **The invariant holds. SRS TS-20 is met.**

**Confidence: HIGH (96 %).**

### H4 — The `TrackSetupFragment` rewrite changed the gate, the fixes awaited, or capture quality

**FALSIFIED for the gate and the number of fixes; CONFIRMED for one 500 ms cadence delta.**

- Gate: `TrackSetupViewModel.kt` was **not** touched by `98cc272`; `git log` shows its last
  change is `8cd7dec` (pre-v2.8). `MAX_GPS_ACCURACY_M = 10.0f` and `satelliteCount ≥ 4` are
  unchanged (`TrackSetupViewModel.kt:29, 59`).
- Fixes awaited: both versions unlock CAPTURE on the **first** fix that satisfies the gate.
  No debounce, averaging or fix-count requirement was added or removed.
- Point capture: identical — `viewModel.setPointA(currentLocation)` from the button click
  (`TrackSetupFragment.kt:89-93`).
- Cadence: `setMinUpdateIntervalMillis` went from `500L` to `1000L`. Worst-case, the
  `currentLocation` used for a capture is 500 ms staler than in v2.8. At a walking pace of
  ~1.4 m/s that is **≤ 0.7 m** — an order of magnitude inside the 10 m accuracy gate, 4× inside
  the 3 m minimum line width (SRS TS-09), and 70× inside the detector's 50 m distance guard
  (`LocalLapDetector.kt:32`). **Immaterial to lap detection.**

**Confidence: HIGH (94 %).**

### H5 — `GpsAcquisitionMetricsStore` DataStore writes regressed the v2.8 ANR mitigation

**FALSIFIED.**

- Writes run `withContext(ioDispatcher)` (`GpsAcquisitionMetricsStore.kt:68`) — never the main
  thread.
- `grep -rn "runBlocking" app/src/main/java` returns **two** hits, both in `AuthInterceptor.kt`
  (an OkHttp interceptor, off the main thread by construction). None in the startup or
  location path. The v2.8 mitigation (`SplashViewModel.kt:30-36`) is intact.
- Write volume is bounded to **two per warm-up cycle** by the `isFirstFix` / `isFirstAccurateFix`
  guards (`LocationWarmUp.kt:132-136`), and `LocationWarmUpTest` asserts this
  (`milestones are recorded once, not on every fix`).
- Failures are swallowed and logged (`GpsAcquisitionMetricsStore.kt:75-79`); reads are guarded
  by `.catch { emit(emptyPreferences()) }` (`GpsAcquisitionMetricsStore.kt:45-48`).
- Warm-up cannot even start until Home is STARTED, i.e. *after* the splash has resolved — so it
  is not on the startup critical path at all.

Residual observation (not a defect): metrics share the single `driving_coach_prefs` DataStore
file with auth, onboarding and driver profile (`DataStoreModule.kt:14`), so each `edit` rewrites
the whole file. At ≤2 writes per cycle this is negligible.

**Confidence: HIGH (95 %).**

### H6 — Null hypothesis: nothing regressed; the chip merely made a pre-existing wait visible

**PARTIALLY CONFIRMED — and it is the larger half of the explanation.**

Evaluated on its merits, as required. The evidence strongly supports the perceptual component:

- No code change lengthens acquisition (differential table above).
- The cold 30–60 s TTFF is physics, documented identically in `USER_MANUAL.md:134-136`,
  `components.md:111-113` and `flows.md:364-366`. The 45 s reported on v2.8 is inside that band.
- v2.8 surfaced no GPS state on Home whatsoever; v2.92 surfaces an amber "Acquiring GPS…" chip
  for the entire TTFF (`HomeFragment.kt:238-242`). Attribution that previously went to "the
  app is loading" now goes to "GPS is slow".

But H6 is **not sufficient on its own**, and it must not be used to close the incident:

- Pure perception would predict the same *behaviour* on both builds. Instead, v2.92 makes a
  concrete promise (`USER_MANUAL.md:150-151`: "Track Setup will be ready the moment you
  arrive") and then structurally breaks it by discarding readiness at the navigation boundary
  (H1). A broken promise is a functional defect in the feature's own terms, not a perception.
- The idle-ceiling behaviour (H2) is a genuine state regression: green → hidden, with no
  restart and a manual that mis-explains the hidden state.

**Confidence: HIGH (88 %) that no TTFF regression exists. HIGH (85 %) that visibility explains
part of the report. HIGH (90 %) that visibility alone does *not* explain all of it.**

### F4 — NEW FINDING (not in the incident report): an unguarded stale-fix path into Point A

**PROBABLE — requires device confirmation. This is the only finding with lap-time impact.**

Three facts, each verified:

1. `currentLocation` (`TrackSetupFragment.kt:44`) is **never invalidated**. It is written only
   in `onLocation()` and cleared nowhere — not on stop, not in `onDestroyView`
   (`TrackSetupFragment.kt:281-286` clears only `collecting` and `_binding`).
2. `isGpsReady` lives in `TrackSetupViewModel`'s `StateFlow` (`TrackSetupViewModel.kt:47-48`),
   which **survives the view's stop/start cycle**, so the CAPTURE button
   (`TrackSetupFragment.kt:126`) can be enabled from a previous fix before any new fix arrives.
3. **No freshness check exists anywhere in the app.**
   `grep -rn "elapsedRealtime\|location.time\|setMaxUpdateAge" app/src/main/java` returns a
   single hit — `TelemetryForegroundService.kt:426`, which only *writes* `location.time` into a
   telemetry sample. Neither `FusedLocationUpdates` nor `TrackSetupFragment` pins
   `setMaxUpdateAgeMillis`, so whether the fused provider's *first* delivered update may be a
   cached historical fix is left to a Play Services default the app does not control.

**Consequence if the provider does deliver a cached fix on subscribe**: the user warms up in
the paddock at position P, walks 50 m to the line, opens Track Setup; the first callback
delivers the cached paddock fix (accuracy ~5 m ⇒ `satelliteCountOf` returns 8 ⇒ gate passes);
the indicator turns green immediately and the user — who has been trained by the manual to act
on green — taps CAPTURE within the ~1 s before the first live fix overwrites it. **Point A is
then the paddock, not the start line.** Point B, captured seconds later, is correct. The
resulting line passes the ≥3 m validation (SRS TS-09) with a plausible-looking width and is
stored to Room (SRS TS-13) and into the telemetry header. `LocalLapDetector` then intersects
against a line tens of metres from the real one, with no error surfaced.

**Attribution, stated carefully**: this path exists in **both** v2.8 and v2.92 — v2.8 also set
no `maxUpdateAge` and also kept `currentLocation` alive. v2.9 does not *create* the defect. But
the warm-up materially **raises the probability of the precondition**: warm-up guarantees a
recent, accurate fix is sitting in the provider's cache at the exact moment Track Setup
subscribes, which on a first-of-the-day run in v2.8 was frequently not the case. This is a
warm-up-adjacent risk amplification, and it is the honest answer to the reporter's "check lap
detection" request.

**Confidence: MEDIUM (60 %)** — the code-side facts are CONFIRMED (no freshness guard, no
invalidation, sticky gate); the Play Services delivery behaviour is PROBABLE and must be
measured on device.

---

## Root Cause

**ROOT CAUSE** — *The GPS warm-up's lifetime is defined by the visibility of the Home screen
rather than by the user's progress toward capturing a start line, so the single navigation the
feature exists to serve — Home → Track Setup — is also its stop condition. Warm-up state is
discarded, the receiver is released, and Track Setup restarts acquisition from an `Idle`
readiness state and re-displays "Acquiring GPS…" to a user who was just shown a green "GPS
ready" chip and told by the User Manual that the wait was over.*

This is a **requirement-level** root cause, not a coding slip. **SRS TS-18**
(`01_requirements/DrivingCoach_SRS_v1.md:187`) mandates exactly this behaviour:

> "GPS warm-up shall stop when the Home screen is no longer visible…"

The implementation is **conformant**. TS-18 was written to bound receiver cost (a legitimate
goal) but expresses the stop condition in terms of a *screen* rather than a *task*, and so
directly undercuts **TS-16** (`:185`), whose stated purpose is that TTFF "elapses while the
user is still preparing rather than while standing at the start/finish line". TS-16 and TS-18
are in conflict, and the code follows TS-18.

**TRIGGER** — Commit `98cc272` (v2.9) shipped the warm-up, the Home chip and the
`TrackSetupFragment` rewrite together, then `USER_MANUAL.md:150-151` and the About-screen
metrics (TS-19) publicised a readiness guarantee the handover does not keep.

**CONTRIBUTING FACTORS**

1. **The class's own KDoc asserts the opposite of the caller's behaviour**
   (`LocationWarmUp.kt:37-38` vs `HomeFragment.kt:81-84`). The design intent — "warmth
   survives navigation … instead of being torn down and re-acquired at the worst possible
   moment" — was correct and was not implemented. `@Singleton` + `@ApplicationScope`
   (`LocationModule.kt:41-46`) make the correct behaviour *possible*; only the two lines in
   `HomeFragment.onStop()` prevent it.
2. **No readiness handover.** Track Setup neither reads `LocationWarmUp.readiness` nor shares
   its subscription; it opens an independent cold flow (`TrackSetupFragment.kt:224`,
   `FusedLocationUpdates.kt:42`). Continuity was never designed, only assumed.
3. **The 3-minute idle ceiling contradicts the documented workflow** (H2):
   `USER_MANUAL.md:139` vs `WarmUpTimings.kt:20`, with no restart path while Home stays
   visible, and a hidden chip that the manual attributes to a missing permission.
4. **Visibility without continuity** (H6). Making the wait visible is only a UX improvement if
   the readiness it reports is honoured downstream. Here it is announced and then revoked.
5. **The L2 harness cannot express the defect.** `ScriptedLocationUpdates`
   (`app/src/androidTest/.../testing/ScriptedLocationUpdates.kt`) is a `MutableSharedFlow`
   with **zero acquisition latency** — a new subscriber receives the next emission
   immediately. In this double, tearing warm-up down and resubscribing costs nothing, so the
   entire class of "handover costs a re-acquisition" defects is structurally invisible.
6. **The one test that navigates Home → Track Setup asserts the wrong thing.**
   `TrackSetupResubscribeTest.setUp()` performs exactly the navigation under investigation,
   and `trackSetupSubscribesOnArrival` then asserts `activeSubscriptions >= 1`. That predicate
   is satisfied identically whether the warm-up subscription survived (briefly 2, then 1) or
   was dropped to 0 and replaced (0 → 1). `subscribeCount` is captured by the double but is
   **never asserted across navigation**. This is the same class of finding recorded in RCA 11:
   *an assertion that cannot fail when the defect is present is not coverage.*
7. **No non-functional requirement bounds acquisition time.** SRS §15 (`:446-461`) specifies
   upload, query, coverage and battery budgets but contains **no TTFF or
   time-to-capture-ready requirement**, so "45 s is too slow" cannot be adjudicated against
   the SRS. The requirement under-specifies the very quantity the incident is about.
8. **No CI.** `.github/workflows/` does not exist (carried forward from RCA 11), so even the
   203 passing L1 tests run only on demand.

---

## Answers to the Incident Report's Open Questions

### OQ1 — Which build is actually defective? Confirm the direction of the comparison.

**Resolved for capability; resolved for experience; the two answers differ.**

- **Acquisition capability**: the direction implied by the report (v2.92 worse) is **not
  supported by code evidence**. `98cc272` removed a permanent-stall defect, added earlier
  acquisition, and changed neither priority, interval, gate nor capture semantics. The only
  timing delta is a bounded 500 ms cadence loss. **v2.92 ≥ v2.8** on acquisition capability.
  Confidence **HIGH (88 %)**.
- **User experience**: the direction in the report is **credible and supported**. v2.92
  promises readiness and then discards it at the handover (H1), and can withdraw the chip
  silently at 3 minutes (H2). v2.8 promised nothing and so could not break a promise.
  Confidence **MEDIUM-HIGH (75 %)**.
- **A confound that cannot be closed from code**: the 45 s reported on v2.8 may itself have
  been the *resubscribe defect* (`failure-patterns.md:141-196`), not GNSS physics — the fix
  commit says as much. If so, the tester's v2.8 baseline is contaminated and the comparison is
  between "45 s of a fixed defect" and "a genuine cold TTFF", which are not comparable
  quantities. **UNVERIFIABLE WITHOUT FIELD DATA.**

### OQ2 — What does "better" mean concretely?

**NOT RESOLVABLE FROM CODE. Must be asked of the reporter.** The code analysis narrows the
plausible set to two, and they have different fixes:

| Interpretation | Supported by code evidence? | Implied fix |
|---|---|---|
| Clearer/steadier UI feedback | **Yes** — readiness reset at handover (H1), chip vanishing at 3 min (H2) | Fix A/B/C below |
| Fewer lost fixes during setup | **No** — v2.9 strictly improved this (`repeatOnLifecycle`) | none |
| Faster fix | **No** — no code change lengthens TTFF | none |
| Better accuracy | **No** — gate and priority unchanged | none |
| Fewer lost fixes during *recording* | Out of scope — `TelemetryForegroundService` untouched by `98cc272` | none |

### OQ3 — Was the 45 s measured on Home (warm-up) or on Track Setup?

**NOT RESOLVED. Critical, and cheap to obtain.** v2.8 has no Home warm-up, so only the Track
Setup timing is comparable across builds. If the 45 s was measured on Home in v2.92 and on
Track Setup in v2.8, the comparison is measuring two different things and the "regression"
is an artefact of the measurement point. `Profile → About` (SRS TS-19,
`GpsAcquisitionMetricsStore`) records `timeToFirstFixMs` and `timeToAccurateFixMs` on v2.9+
and answers this directly.

### OQ4 — Was lap detection actually observed to fail on v2.92?

**Resolved: no failure is reported as observed**, and none is expected from the reported
symptom. The request is precautionary. However, this RCA **does not return "no risk"** — see
Finding F4, which is a genuine, unguarded path by which a stale fix can become Point A. It was
not reported, has not been observed, and would be silent if it occurred.

---

## Lap-Detection Verdict

The question must be split in three, as the incident report itself insists.

| Question | Verdict | Evidence | Confidence |
|---|---|---|---|
| Did the lap-detection **algorithm** change between v2.8 and v2.92? | **NO** | `LocalLapDetector.kt` blob is `d2b80dfd1ca9ea9287626e3a0644c3df63513832` at both `98cc272^` and `HEAD`; `git log --follow` shows one commit, `8cd7dec`, predating v2.8. Constants unchanged: `MIN_LAP_TIME_MS = 20_000`, `MIN_DISTANCE_FROM_START_M = 50.0`, `MIN_SAMPLES = 50` (`LocalLapDetector.kt:29-35`) | **HIGH (99 %)** |
| Did the detector's **inputs** change in a way that alters behaviour? | **NO, materially** | Inputs are: the four start-line coordinates (`StartLine`, `LocalLapDetector.kt:41`, read from the telemetry header at `:187-193`), the sample stream, and the constants. Capture semantics and the ≤10 m / ≥4-satellite gate are untouched by `98cc272`. The only delta is the 500 ms cadence loss ⇒ ≤ 0.7 m of additional position staleness at walking pace — 14× inside the accuracy gate and 70× inside the 50 m distance guard | **HIGH (90 %)** |
| Is there **any** newly-elevated risk to lap times? | **YES — latent, unobserved, pre-existing but amplified** | Finding F4: no freshness guard on `currentLocation`, sticky `isGpsReady` across stop/start, no `setMaxUpdateAgeMillis`. Warm-up raises the probability that a recent accurate *cached* fix exists at Track Setup subscribe time | **MEDIUM (60 %)** |

**Plain-language answer to the reporter**: no, the lap-detection mechanism did not change. Its
maths, its thresholds and its inputs' quality gate are byte-for-byte or semantically identical
between the two builds. Any lap-count difference observed between v2.8 and v2.92 on the same
route would therefore point at the **captured start line**, not at the detector — and F4 is
the mechanism to check first.

---

## Confidence Level

**Overall Confidence: MEDIUM-HIGH (78 %)**

| Conclusion | Class | Confidence |
|---|---|---|
| Warm-up is torn down at the Home → Track Setup navigation, readiness reset to `Idle` | **CONFIRMED** (code) | HIGH (90 %) |
| Track Setup opens an independent subscription with no state handover | **CONFIRMED** (code) | HIGH (95 %) |
| No code change in v2.8 → v2.92 lengthens physical TTFF | **CONFIRMED** (diff) | HIGH (88 %) |
| SRS TS-18 mandates the stop condition that causes the defect; TS-16/TS-18 conflict | **CONFIRMED** (SRS `:185, :187`) | HIGH (95 %) |
| TS-20 warm-up invariant holds; H3 falsified | **CONFIRMED** (type-level) | HIGH (96 %) |
| Lap-detection algorithm unchanged | **CONFIRMED** (blob hash) | HIGH (99 %) |
| H5 (DataStore/ANR) falsified | **CONFIRMED** | HIGH (95 %) |
| Idle ceiling withdraws the chip with no restart path (H2) | **CONFIRMED** (code) | HIGH (92 %) |
| Idle ceiling contributed to *this* report | **PROBABLE** | LOW-MEDIUM (40 %) |
| Perception/visibility explains part of the report (H6) | **PROBABLE** | HIGH (85 %) |
| F4 stale-cached-fix into Point A | **PROBABLE** | MEDIUM (60 %) |
| Size of Δ (re-acquisition after handover) | **UNVERIFIABLE WITHOUT FIELD DATA** | — |
| Fragment ordering: does Home stop before Track Setup subscribes? | **UNVERIFIABLE WITHOUT FIELD DATA** | — |
| Whether the reported 45 s on v2.8 was GNSS physics or the resubscribe defect | **UNVERIFIABLE WITHOUT FIELD DATA** | — |

**Rationale**: the causal chain from `HomeFragment.kt:81-84` to a second "Acquiring GPS…"
screen is fully determined by checked-in code and requires no device to establish. What a
device is required for is *magnitude* — whether the resulting re-acquisition is 300 ms
(irritating) or 15 s (a real regression) — and that is precisely what `Profile → About`
(SRS TS-19) was built to answer and what the HAT run did not capture.

**Remaining uncertainty (22 %)** is concentrated in three places: the size of Δ, the reporter's
definition of "better" (OQ2), and the Play Services cached-first-update behaviour behind F4.
All three are closable with one instrumented trackside session.

---

## Requirements Assessment

| Requirement | Status as implemented | Note |
|---|---|---|
| **TS-15** (subscribe while STARTED, resume after stop/start) | ✅ Met | `TrackSetupFragment.kt:221-225` |
| **TS-16** (acquire as soon as Home is visible, so TTFF elapses while preparing) | ⚠️ **Met literally, defeated in effect** | Acquisition starts on Home, then is discarded before the screen that needs it |
| **TS-17** (chip: hidden / amber / green) | ⚠️ Met, but "hidden" is overloaded | Hidden means *both* "no permission" and "ceiling expired"; `USER_MANUAL.md:146` documents only the former |
| **TS-18** (stop when Home not visible; 3-min ceiling) | ✅ Met — **and this is the defect** | The requirement, not the code, needs to change |
| **TS-19** (record and display TTFF metrics) | ✅ Met | `GpsAcquisitionMetricsStore`, `AboutFragment` |
| **TS-20** (readiness only, never a position; live ≤10 m capture) | ✅ Met | Enforced by type (`LocationUpdates.kt:36-37`) |
| **TS-04 / TS-09 / TS-13** (gate, ≥3 m line, store to Room) | ✅ Met | Unchanged by `98cc272` |
| **SRS §15 NFRs** | ❌ **Gap** | No requirement bounds TTFF or time-to-capture-ready. The SRS cannot adjudicate "45 s is too slow" |

**No requirement is violated by the implementation.** The defect is that **TS-18 is wrong**:
it binds a task-scoped resource to a screen's visibility.

---

## Mitigation

### Immediate (user recovery — no build required)

1. On Track Setup, if the indicator is red, **wait ~5–10 s before walking away**; the
   subscription is live and self-heals (`TrackSetupFragment.kt:221-225`).
2. If the Home chip has disappeared, **switch away from the app and back** — `onStart()`
   re-arms warm-up (`HomeFragment.kt:76-79`).
3. Capture the number: `Profile → About` shows the last acquisition's TTFF (SRS TS-19). Per
   `failure-patterns.md:155`, a **small measured TTFF alongside a long perceived wait means
   the wait was a subscription defect, not satellites**.
4. **Do not tap CAPTURE on the first green** at the line — wait ~2 s for a live fix to land.
   (Interim guard against F4; see permanent fix 4.)

### Short-term — prevent recurrence (analysis only; no code changed by this RCA)

**Fix A — Keep the warm-up alive across the handover (addresses the root cause).**
`app/src/main/java/com/drivingcoach/ui/home/HomeFragment.kt:81-84`

| Option | Change | Trade-off |
|---|---|---|
| **A1 (recommended)** | Do not stop warm-up when navigating *within* the app to Track Setup; stop it when the app leaves the foreground, when recording starts, or on the ceiling | Cleanest match to TS-16's intent and to `LocationWarmUp.kt:37-38`. Requires a stop condition expressed on the *task*, e.g. `TrackSetupFragment` (or `RecordingViewModel`) taking ownership. **Requires amending SRS TS-18.** |
| **A2** | Have `TrackSetupFragment.onStart()` call `LocationWarmUp.start()` too | Two-line change; `start()` is idempotent (`LocationWarmUp.kt:90-92`) so it costs nothing. But it stacks a *second* fused request alongside Track Setup's own, and does not stop Home's teardown racing it. Cheap, partial |
| **A3** | Have Track Setup consume `LocationWarmUp.readiness` for its initial indicator state | Fixes the *promise* (no red flash after green) without touching lifetimes, but shows readiness derived from a subscription that has just been cancelled — arguably worse. **Not recommended alone** |

**Fix B — Make the idle ceiling honest (H2).**
`WarmUpTimings.kt:20`, `LocationWarmUp.kt:154-161`, `HomeFragment.kt:233-236`

| Option | Change | Trade-off |
|---|---|---|
| **B1 (recommended)** | On ceiling expiry, publish a distinct `GpsReadiness.Paused` and render a tappable "Tap to resume GPS" chip | Distinguishes "no permission" from "paused"; gives the user agency. Requires a new sealed sub-type and a string |
| **B2** | Raise the ceiling to 600 s | One constant; but it multiplies worst-case receiver cost 3.3× against the TS-18 rationale (battery), and only moves the cliff |
| **B3** | Reset the ceiling on any user interaction with Home | Matches "idle" semantics literally; more wiring, and a user standing still with the app open is still idle |

**Fix C — Align the documentation with whatever ships.**
`docs/USER_MANUAL.md:150-151` currently promises Track Setup "will be ready the moment you
arrive". Either make Fix A true, or soften the promise. `USER_MANUAL.md:146` must also stop
attributing every hidden chip to a missing permission.

**Fix D — Close F4 (independent of A–C; do this regardless).**
`FusedLocationUpdates.kt:48-53` and `TrackSetupFragment.kt:229-234`

1. Pin the provider contract: add `.setMaxUpdateAgeMillis(...)` to the `LocationRequest`
   builder so cached-fix delivery is an explicit app decision, not a Play Services default.
2. Reject stale fixes at the capture boundary: in `onLocation()`, discard any `Location`
   whose `elapsedRealtimeNanos` is older than a small budget (e.g. 3 s).
3. Invalidate on stop: clear `currentLocation` and reset `isGpsReady` when the collector stops,
   so CAPTURE cannot be enabled by a fix from before an interruption
   (`TrackSetupFragment.kt:44`, `TrackSetupViewModel.kt:47`).

**Fix E — Specify the missing NFR.** Add to SRS §15 a bound such as: *time from Track Setup
becoming visible to CAPTURE being enabled shall not exceed N s when a fix was already ready on
Home*. Without it, this incident's core complaint remains unadjudicable.

### Permanent (systemic)

1. **Model the handover explicitly.** The failure lives in a transition that neither the SRS,
   nor Atlas `flows.md`, nor the tests describe. Any resource whose purpose is to serve a
   *later* screen must have its lifetime expressed against the *task*, not against the screen
   that happens to start it.
2. **Give the test double a latency model.** `ScriptedLocationUpdates` should be able to
   simulate "first fix arrives T ms after subscribe". Without it, no test can ever express the
   cost of dropping a subscription, and the whole defect class stays invisible.
3. **Establish `.github/workflows/`** and run `assembleDebug` + L1 on push. Still absent;
   carried forward from RCA 11.

---

## Prevention Recommendations

### Testing — the specific coverage gap

**What is not asserted today:**

| Test | Asserts | Cannot detect |
|---|---|---|
| `LocationWarmUpTest` (20 tests, L1) | start/stop/idempotence/ceiling/thresholds/metrics — all correct and all passing | Anything about *who calls* `stop()`. The state machine is right; its invocation is wrong |
| `HomeGpsChipTest.backgroundingHomeReleasesTheChip` (L2) | `activeSubscriptions == 0` after Home stops | Cannot distinguish "backgrounded" from "navigated to Track Setup" — both are `onStop` |
| `TrackSetupResubscribeTest.trackSetupSubscribesOnArrival` (L2) | `activeSubscriptions >= 1` **after navigating Home → Track Setup in `setUp()`** | **The defect itself.** `>= 1` holds whether the warm-up survived or was dropped and replaced |
| All L2 GPS tests | Behaviour under a zero-latency `MutableSharedFlow` double | Any cost of re-acquisition; the double delivers instantly to a new subscriber |

**Tests to add (each must be falsified against current `main` — it must fail before any fix):**

1. **L2 — continuity across navigation**: capture `subscribeCount` and `activeSubscriptions`
   on Home after warm-up starts; navigate to Track Setup; assert `activeSubscriptions` never
   reaches 0 at any point during the transition. Requires sampling, not a single post-hoc read.
2. **L2 — readiness is not withdrawn**: with warm-up `Ready`, navigate to Track Setup and
   assert the GPS indicator never paints the "Acquiring GPS…" state.
3. **L2 — capture is available on arrival**: with a ≤10 m fix already delivered on Home,
   assert `capturePointAButton.isEnabled` within a short budget of arriving at Track Setup,
   with **no further emission** from the double.
4. **L1 — ceiling semantics**: assert the readiness state after the ceiling fires is
   distinguishable from "no permission" (currently both are `Idle`).
5. **L2 — F4 stale capture**: emit an accurate fix, drive the screen to `CREATED` and back,
   and assert CAPTURE is **disabled** until a *new* fix arrives. Expected to fail today.
6. **Harness** — add a configurable first-fix delay to `ScriptedLocationUpdates`; tests 1–3
   are meaningless without it.

### Atlas Updates

**`SkunkOps/atlas/failure-patterns.md` — add a new pattern (novel; no existing pattern covers it):**

> **Pattern: Warm-Up Discarded at the Navigation Boundary It Was Built to Serve**
> *Symptoms*: a readiness indicator reaches "ready" on screen A, then the destination screen B
> re-displays "acquiring" from scratch; users report the feature made things worse.
> *Signals*: `LocationWarmUp: warm-up stopped` in logcat immediately before B's subscription;
> measured TTFF on the About screen is small while the perceived wait is long (same
> discriminator as the *Location Subscription Not Restored* pattern).
> *Likely cause*: a process-scoped resource whose `stop()` is wired to a *screen's* `onStop()`,
> where navigation to the consuming screen is itself an `onStop`.
> *Generalisation*: audit every `onStop() → someSingleton.stop()` pair. `onStop` cannot
> distinguish "user left the app" from "user advanced to the next step of the task". If those
> two need different behaviour, the lifecycle callback is the wrong signal.
> *Regression guard*: assert subscription **continuity across navigation**, not subscription
> *presence* after navigation.

**`SkunkOps/atlas/flows.md:360-415` — extend the GPS Warm-Up flow** with a "Handover to Track
Setup" section documenting: `stop()` at `HomeFragment.onStop()`, the independent
`positionUpdates()` subscription, the readiness reset to `Idle`, and the fact that no state
crosses the boundary. This transition is currently undocumented, which is why localisation had
to be done from source.

**`SkunkOps/atlas/components.md:161-167` — extend the GPS Warm-Up failure-mode table**:

| Mode to add | Symptom | Cause |
|---|---|---|
| Warm-up discarded at navigation | Green chip on Home, then "Acquiring GPS…" again on Track Setup | `HomeFragment.onStop()` → `stop()` (SRS TS-18) |
| Ceiling state indistinguishable from no-permission | Chip hidden; manual blames permissions | `stop()` publishes `Idle` for both causes |

Also correct `components.md:167` ("re-entering Home restarts"): the ceiling does **not**
re-arm while Home remains STARTED.

### Documentation

- `USER_MANUAL.md:150-151` — do not promise Track Setup will be ready on arrival until Fix A
  ships.
- `USER_MANUAL.md:146` — a hidden chip has two causes, not one.
- SRS — resolve the TS-16 / TS-18 conflict explicitly, and add the missing §15 acquisition NFR.

### Process

- When a feature ships with a user-facing *promise* (a manual paragraph, a green badge), the
  acceptance test must assert the promise end-to-end across screens, not each screen in
  isolation. Both halves of this feature passed their own tests; the seam between them was
  never tested and is where the incident lives.

---

## Field Evidence Required to Close the Remaining 22 %

Collect on a real device, outdoors with clear sky view, same location and same time window for
both builds. Everything below is cheap and answers a specific open item.

| # | Evidence | Closes | How |
|---|---|---|---|
| 1 | **Exact APK filename** tested as "v2.8" (three v2.8 artefacts exist: `about-intro`, `helmet-artwork`, `splash-anchor`) | Baseline identity | Ask the reporter |
| 2 | Device model, Android API level, Play Services Location version; real device vs emulator | Whether GNSS behaviour is real at all | `adb shell getprop`; `dumpsys package com.google.android.gms` |
| 3 | **`Profile → About` TTFF and time-to-accurate-fix** after a v2.92 cold run | OQ3, Δ, and the physics-vs-defect discriminator (`failure-patterns.md:155`) | On-device screenshot |
| 4 | **Logcat, cold start → Track Setup, both builds** — filter `LocationWarmUp`, `GpsMetrics`, `TrackSetup` | H1 ordering; whether `warm-up stopped` precedes Track Setup's subscribe | `adb logcat -s LocationWarmUp:V GpsMetrics:V TrackSetupFragment:V` |
| 5 | **Δ measured directly**: stopwatch from Track Setup appearing to the indicator turning green, when the Home chip was **already green** | The magnitude of the root cause. **Single most valuable measurement** | 3 runs |
| 6 | Cold TTFF, 3 runs each build, measured **at the same screen** (Track Setup) | OQ1 direction, quantitatively | Stopwatch + item 3 |
| 7 | **Video of the Home chip over 4 minutes** without leaving Home | H2 — confirms green → hidden at 180 s with no restart | Screen recording |
| 8 | **F4 probe**: warm up on Home at point P, walk ≥30 m, open Track Setup, tap CAPTURE on the *first* green, then read the stored coordinate | F4 — the highest-severity open item | Compare Point A against the true line position |
| 9 | Room query of `sessions.startLineLat1/Lng1/Lat2/Lng2` for a v2.92 session | F4 corroboration; line width plausibility | `adb shell` + Room inspector, or item 10 |
| 10 | **Exported session `.jsonl`** (Share → diagnostic file, added in v2.91) for one v2.92 session | Header start line + sample stream; allows offline re-run of `LocalLapDetector` | In-app share |
| 11 | Lap count and lap times, same route, both builds | The reporter's lap-detection question, empirically | Drive/simulate ≥4 laps |
| 12 | **A direct answer to OQ2** — what "better" meant | OQ2. Cannot be derived from code | Ask the reporter |

---

## Notes on the Original Incident Report

| Report statement | Finding |
|---|---|
| "The only commit in this range that touches the location data path is `98cc272`" | **Confirmed.** `340871e` and `36952fa` touch no `data/location` or track-setup file |
| "The detection algorithm and its constants are therefore unchanged" | **Confirmed** by blob hash `d2b80df…` at `98cc272^` and `HEAD` |
| "Lap detection behaviour can change if the captured start line changes in quality or timing" | **Correct in principle, immaterial in fact** for the shipped change (≤ 0.7 m). But correct in a way the report did not anticipate — see F4 |
| "The RCA must verify this invariant [TS-20] still holds" | **Verified — it holds**, and it is enforced by type rather than by convention |
| "45 s falls inside the documented normal range" | **Confirmed** (`USER_MANUAL.md:134-136`, `components.md:111-113`) — **but** the fix commit for `98cc272` notes the 45 s "may well be" the resubscribe defect. The baseline may be contaminated |
| "This should be the primary line of investigation … the warm-up is not running when expected, is being stopped prematurely, or is interfering with the Track Setup subscription" | **Correct, and the middle branch is the answer**: stopped prematurely — at the navigation into Track Setup, and at the 3-minute ceiling. *Interference* is **falsified**: the two subscriptions are independent, and there is no evidence of contention |
| "Investigate its inputs, not its algorithm" | **Followed.** The inputs are unchanged in quality; the input *pipeline* nevertheless has an unguarded staleness path (F4) |
| Severity MEDIUM, "escalate to HIGH if lap detection is confirmed affected" | Lap detection is **not** affected by the reported symptom — MEDIUM stands for the report. **F4 independently warrants HIGH** and should be tracked separately |

---

*RCA complete. No production code was modified. Fix strategy is recommended, not implemented,
per the incident's instruction not to fix before OQ1 is confirmed — and OQ1 is now resolved for
capability but only partially for experience.*
