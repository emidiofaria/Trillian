# Documentation Sync Report

Cumulative changelog of documentation synchronizations with codebase.

---

## [2026-09-16] Deletion of all @Ignore'd tests

**Codebase Version:** v2.96
**Trigger:** Decision to delete every `@Ignore`'d test class after analysis showed the
suite was reporting coverage for tests that had never executed.

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `app/src/androidTest/**` | ✅ Deleted | 4 classes, 29 tests, 1,181 lines |
| `05_tests/coverage-map.tsv` | ✅ Updated | −8 claims, +5-line explanatory note |
| `01_requirements/TRACEABILITY_MATRIX.md` | ✅ Updated | 8 rows ✅→❌; obsolete caveat rewritten |
| `01_requirements/DrivingCoach_SRS_v1.md` | ✅ Updated | +2 coverage-status remarks (TS block, SR block) |
| `05_tests/L2_SWE5_integration/README.md` | ✅ Updated | Tree entries removed; `@Ignore` narrative corrected |
| `05_tests/Test_Strategy.md` | ✅ Updated | Stale example command + ledger example fixed |
| `05_tests/test_strategy_execution_instructions.md` | ✅ Updated | 2 stale example commands fixed |
| `atlas/failure-patterns.md` | ✅ Updated | +1 pattern: FP-HOLLOW-TEST |
| `docs/USER_MANUAL.md` | ⏭️ No change | No user-facing behaviour affected |

### Deleted

| File | Tests | Lines | `@Ignore` reason |
|------|-------|-------|------------------|
| `EndToEndTest.kt` | 5 | 445 | MockWebServer init / app state |
| `ui/tracksetup/TrackSetupFragmentTest.kt` | 11 | 269 | Needs `launchFragmentInHiltContainer` |
| `service/TelemetryForegroundServiceTest.kt` | 6 | 263 | Requires real GPS hardware |
| `ui/recording/RecordingFragmentTest.kt` | 7 | 204 | `HiltTestActivity` not resuming |

**Recovery SHA:** `02164750ba14e3e60f2e03022a5bf5636b74b3db`

### Deliberately kept

- `HiltTestActivity.kt` / `HiltExt.kt` — four *passing* tests depend on them
  (`StartupBackStackTest`, `SessionShareTest`, `AnalysisTabTest`, `AboutScreenTest`)
- `di/TestNetworkModule.kt` + `mockwebserver` — `@TestInstallIn(replaces = [NetworkModule::class])`
  redirects **all** L2 tests to `localhost:8080`; deleting it would have pushed the 60 passing
  tests onto the production network module
- Historical `05_tests/reports/TEST_REPORT_*.md` — dated evidence, not rewritten

### Requirement Impact

⚠️ **Eight requirements lost their only claim** and are now marked ❌ with dated remarks in
the SRS: **TS-02, TS-03, TS-05, TS-06, TS-12, SR-04, SR-05, SR-09**.

- **SR-09** (recording continues when backgrounded) is core V1 behaviour and is the most
  significant gap. It was already unverified — the cited test never ran.
- **SR-05**'s claim was **unfounded regardless**: `TelemetryForegroundServiceTest` contained
  no GPS-status assertion at all. Removing it corrects a false claim.
- **14 of the 29** deleted tests were `isDisplayed()` assertions that could not fail when the
  app was broken.

No new requirement IDs added; none removed.

### Validation

| Check | Before | After |
|-------|--------|-------|
| `@Ignore` in `app/src` | 4 classes | **0** |
| L1 unit tests | 282/282 | **282/282** |
| L2 integration | 60 exec / 89 declared, 4 skipped | **60/60, 0 skipped** |
| Tests executed | 342/371, 29 never ran | **342/342, 0 never ran** |
| Unbacked coverage claims | `[WARN] 8` | **none** |
| V1 requirement coverage | 71/192 (37%) | **63/192 (33%)** |

The coverage drop is a correction, not a regression: nothing that previously executed stopped
executing. Report: `05_tests/reports/RUN_20260916_110333/`.

### Recommendations

- [ ] SR-09 is the gap worth closing. It needs `TelemetryForegroundService` moved onto the
      `LocationUpdates` seam (TS-15) so fixes can be scripted, as `StartLineFreshnessTest`
      already does — the service currently calls `LocationManager.GPS_PROVIDER` directly
- [ ] Real drive telemetry exists for replay (`lapfixtures/`, Incidents 13 & 14) and is already
      used at L1 by `LapReplayHarness`; emulator GPS cannot feed `SensorManager`, so the seam is
      the only route that replays accel/gyro
- [ ] Consider promoting the report's `[WARN] N coverage claim(s) not backed` to a non-zero exit
      so a claim can never again outlive its test
- [ ] Android Lint remains the only active build gate, still red on 3 real errors

---

## [2026-09-15] Removal of the ktlint and JaCoCo quality gates

**Codebase Version:** v2.96
**Trigger:** Decision to disable two build quality gates after a full build and
L1+L2 test run exposed that neither was providing real assurance.

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `build.gradle.kts` | ✅ Updated | −2 lines (ktlint plugin declaration) |
| `app/build.gradle.kts` | ✅ Updated | −81 net (both plugins, `enableUnitTestCoverage`, JaCoCo task block); +19 decision comment |
| `DrivingCoach_SRS_v1.md` | ✅ Updated | NF-11 annotated; enforcement remark added |
| `05_tests/Test_Strategy.md` | ✅ Updated | Stale `testDebugUnitTestCoverage` command removed |
| `atlas/*.md` | ⏭️ No change | No Atlas file documented either gate |
| `docs/USER_MANUAL.md` | ⏭️ No change | No user-facing behaviour affected |

### Rationale

**ktlint** was permanently red: accumulated style debt plus an upstream crash in
the `argument-list-wrapping` rule on `OfflineCoachingEngineTest.kt:141`. A gate
that can never go green trains everyone to pass `-x` flags, which is how three
genuine Android Lint errors went unaddressed.

**JaCoCo** declared a 95% line/branch threshold that was never attached to
Gradle's `check` task, so it had never executed. It was an assurance claim with
no evidence behind it.

### Requirement Impact

⚠️ **NF-11** ("Unit test line coverage shall be ≥ 70% across domain and data
layers") loses its only nominal automated enforcement. Because the JaCoCo
verification task never ran, NF-11 was already unenforced in practice; this
change makes the gap visible rather than creating it. Approved explicitly by the
operator. No new requirement IDs were added and none were removed.

The requirement IDs cited in the removed build comments — `LA-02`, `UA-02`,
`UA-03`, `CD-AD-06` — were found to exist **nowhere** in the SRS, `coverage-map.tsv`
or `scope-map.tsv`. They were rotted traceability and have been deleted with the
code that cited them.

### Validation

| Check | Result |
|-------|--------|
| `./gradlew tasks --all \| grep -iE 'ktlint\|jacoco\|coverage'` | Empty — both gates gone |
| L1 unit tests | 282/282 passed, 0 failures, 0 skipped |
| `./gradlew build` | Now fails on `lintDebug` **only** (4 errors, 290 warnings) |

### Files Modified

```
M  build.gradle.kts                        (+0,  -2)
M  app/build.gradle.kts                    (+19, -96)
M  01_requirements/DrivingCoach_SRS_v1.md  (+11, -1)
M  05_tests/Test_Strategy.md               (+6,  -2)
```

### Recommendations

- [ ] Android Lint is now the only active gate — fix its 3 real errors
      (`POST_NOTIFICATIONS` ×2 in `TelemetryForegroundService.kt`, `android:tint`
      in `fragment_session_result.xml`) and baseline the 290 warnings
- [ ] The `HiltTestActivity [MissingClass]` lint error is a false positive
      (class lives in `androidTest`, invisible to debug-variant lint)
- [ ] 29 L2 tests remain `@Ignore`d, leaving SR-04/05/09 and TS-02/03/05/06/12
      unbacked — higher value than either removed gate
- [ ] If coverage enforcement is ever restored, wire it into `check`; an
      unattached gate is worse than none

---

## [2026-09-14] Incident 14 explainer and fix record, and a message that misdirected

**Codebase Version:** v2.96
**Trigger:** Request for an informative write-up of the Incident 14 RCA. Writing
it surfaced a defect in the shipped user-facing copy.

### Defect found while documenting

The empty-result message introduced by LD-18 ended *"Set the start line again
while driving past it."* Track Setup captures both start-line points on foot —
the UI reads *"Walk to each edge of the track and capture two GPS points"* — and
there is no capture-while-moving control in the app.

The driver was handed a correct diagnosis followed by an instruction he could
not carry out, which leaves him as stuck as the message it replaced.

The same wording had already been caught and removed from `USER_MANUAL.md`
during the Incident 14 docs sync. It was missed in `NoLapsExplanation.kt` — the
copy a driver actually reads.

**Fixed in place at the operator's decision, keeping v2.96.** Now reads: *"Set it
again from the track edges, and wait for the GPS signal to settle before you
capture each point."*

Guarded by `NoLapsExplanationTest.the advice must be something the app lets you
do`, which rejects "while driving", "as you drive", "driving past" and "while
moving" across every message branch. The guard matters more than the wording:
nothing had been comparing the remedy the app offers against the controls the
app ships.

### Artifacts

| Artifact | Status | Notes |
|----------|--------|-------|
| `14_EXPLAINER_why_no_laps_were_detected.md` | ✅ New | The reasoning, for a developer new to lap detection. Owns no facts — cites the RCA |
| `14_Provided_Fix_start_point_projected_onto_driven_path.md` | ✅ New | The change record, following the `01_Provided_Fix_*` precedent |
| `14_RCA_start_point_fixed_while_stationary.md` | ✅ Updated | Cross-links added; records the post-release message correction |
| `NoLapsExplanation.kt` | ✅ Fixed | Advice now matches the shipped capture workflow |
| `NoLapsExplanationTest.kt` | ✅ Updated | +1 guard test |

Division of responsibility, so the three incident documents do not drift:
**report** = what was observed · **RCA** = the evidence · **explainer** = the
reasoning · **provided fix** = the change. Only the RCA owns measurements.

### Validation

| Level | Result |
|-------|--------|
| L1 (SWE.4 unit) | ✅ 282/282 (281 before the new guard) |

No production behaviour changed beyond the message text.

---

## [2026-09-14] The release evidence described a different build

**Codebase Version:** v2.96
**Trigger:** Packaging the v2.96 release surfaced three defects in the release
evidence chain. No application code changed; this entry records tooling fixes
and one build-integrity finding.

### What was wrong

| # | Defect | Consequence |
|---|--------|-------------|
| 1 | `package-release.sh` copied the newest `RUN_*/TEST_REPORT.html` without checking it | The v2.96 APK shipped a test report generated on 2026-09-10 against v2.95. The evidence described a different build to the one in the box. |
| 2 | Release-notes commit range was anchored with `git log -1 -- releases/<prev>/`, but `releases/` is gitignored | The anchor never resolved, so the script silently fell back to "last 15 commits". v2.96's true range is 6. |
| 3 | `BuildConfig.VERSION_NAME` is a compile-time constant that Kotlin inlines at call sites | After the 2.95 → 2.96 bump, an incremental build produced an APK whose manifest said 2.96 while `AboutFragment` still rendered the inlined `2.94`. Verified by extracting the dex: the APK contained **both** literals. |

### Fixes

- `package-release.sh` now refuses to package unless the report's embedded
  `Version:` and `Commit:` match the version being released and `HEAD`. The
  check runs *before* the directory is created, so a refusal writes nothing
  rather than leaving a half-built release.
- The release-notes anchor now reads the previous release's own
  `RELEASE_NOTES.md` commit hash, which survives the gitignore.
- `.gitignore` now also ignores the stray `05_tests/reports/TEST_REPORT.html`,
  consistent with the existing rule that working-directory runs are scratch and
  evidence lives in `releases/`.

### Defect 3 needs no code change

`AboutScreenTest.aboutScreenReportsTheBuildIdentityFromBuildConfig` caught the
stale constant on its own — that is the test doing exactly its job, at the right
layer. The remedy is procedural: **a release build must be a clean build.** A
clean rebuild removed the `2.94` literal from the dex entirely.

Left deliberately unfixed: no guard was added to `package-release.sh` for this.
A second check of the same property would duplicate the test without adding
information, and the test already fails the release run.

### Validation

Clean rebuild, then L1 + L2 on a cold-booted emulator:

| Level | Result |
|-------|--------|
| L1 (SWE.4 unit) | ✅ 281/281 |
| L2 (SWE.5 integration) | ✅ 60 passed, 4 `@Ignore`d (pre-existing) |

An earlier L2 run failed 29 tests with `RootViewWithoutFocusException`. This was
environmental — the headless emulator's screen timing out while idle, not a code
regression (L1 stayed green throughout). Holding the device awake for the
duration of the run cleared it.

Packaged `releases/v2.96-stationary-start-point/` and verified the shipped
report header reads v2.96 and carries the commit it was built from, and the
notes list 6 commits.

---

## [2026-09-13] A start point fixed while standing still (Incident 14)

**Codebase Version:** v2.96
**Trigger:** Incident 14 — driver completed three laps, app reported none.

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| failure-patterns.md | ✅ Updated | +93 lines (1 new pattern: `FP-STATIONARY-POSITION-BIAS`) |
| components.md | ✅ Updated | +30 lines (new section + 5 constants) |
| flows.md | ✅ Updated | +9 lines (fallback branch, rejection recording) |
| SRS_v1.md | ✅ Updated | 2 amended (LD-02, LD-04), 2 new (LD-17, LD-18) |
| USER_MANUAL.md | ✅ Updated | Section 7 "No Laps Detected" extended |
| coverage-map.tsv | ✅ Updated | +7 claims, all PASS |
| 03_incidents/14 | ✅ Added | RCA document + raw evidence committed |

### New Requirement IDs

- **LD-17** — bounded projection of the start point onto the driven path
- **LD-18** — an empty result must describe the session, not instruct the driver

### What changed and why

The app mandates capturing the start/finish line **while standing still** (TS-05, TS-07), which is
the one condition in which a consumer GPS receiver is least able to place itself. On the incident
14 session that put the start point **15.9 m perpendicular** to the track, while the racing laps
themselves overlaid within **4.5 m**. Because both endpoints were captured seconds apart they
shared the same bias, so the line's length and bearing came out perfect and every internal check
passed. Only the absolute position was wrong.

The decisive measurement: the reported position sat 10–20 m to one side for the whole opening of
the session, then jumped ~11 m in 2 seconds as the kart accelerated, landing 0.3 m from the racing
line. The bias does not decay — it vanishes the moment the receiver gets velocity aiding.

Contributing factor: `RejectionReason.TOO_FAR_TO_THE_SIDE` already existed in the enum, fully
documented, **referenced nowhere**. Four passes were discarded and the diagnostics reported no
rejections at all.

### Documentation decisions worth recording

- The user manual originally gained advice to "capture while rolling slowly past" the line. This
  was **removed before commit**: TS-05/TS-07 have the user capture on foot at the track edges, so
  the advice was not actionable in the shipped UI.
- `LD-18` was added beyond the approved plan, because the message change is user-visible and the
  SRS had no requirement covering it. Flagged to the operator.
- The message logic was extracted from `RecordingViewModel` into `NoLapsExplanation` so it could be
  tested directly — the previous wording survived for months because nothing asserted on it.

### Verification

**281 L1 tests, 0 failures** (262 before this work). All 7 new coverage claims resolve to PASS; the
8 unbacked claims in the report are the pre-existing `@Ignore`d L2 classes, unchanged.

### Files Modified

```
M  atlas/failure-patterns.md                                 (+93)
M  atlas/components.md                                       (+30, -2)
M  atlas/flows.md                                            (+9)
M  01_requirements/DrivingCoach_SRS_v1.md                    (+4, -2)
M  docs/USER_MANUAL.md                                       (+20)
M  05_tests/coverage-map.tsv                                 (+7)
M  app/build.gradle.kts                                      (2.95 -> 2.96)
M  app/src/main/java/com/drivingcoach/lap/LocalLapDetector.kt
M  app/src/main/java/com/drivingcoach/ui/recording/RecordingViewModel.kt
M  app/src/main/java/com/drivingcoach/util/GeoUtils.kt
A  app/src/main/java/com/drivingcoach/lap/NoLapsExplanation.kt
A  app/src/test/java/com/drivingcoach/lap/LapDetectionIncident14Test.kt
A  app/src/test/java/com/drivingcoach/lap/NoLapsExplanationTest.kt
A  app/src/test/resources/lapfixtures/ines3/
A  03_incidents/14_no_lap_detected_test/14_RCA_start_point_fixed_while_stationary.md
```

### Recommendations

- [ ] The 20 m bound spent 80% of its budget on the one session that needed it. If a second session
      approaches it, change the capture workflow rather than the number.
- [ ] Watch for `anchor: PROJECTED_ONTO_PATH` appearing routinely — that would mean the capture
      workflow, not the receiver, is the thing to fix.

---

## [2026-09-10] Remove the last of the BMW branding

**Codebase Version:** v2.95
**Trigger:** Requested removal of the word "BMW" from all documents and code.

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `DrivingCoach_SRS_v1.md` | Updated | Title, Document ID, UM-19, SR-10, SH-03 (+5, -5) |
| `generate-html-report.py` | Updated | CSS token `--bmw` renamed `--brand` (+3, -3) |
| `backend/package-lock.json` | Updated | Two stale `name` fields aligned to `package.json` (+2, -2) |
| `40_SESSION_RECORDING_TESTS.md` | Updated | SVC-01 steps 3-4 corrected with SR-10 (+2, -2) |
| `system.md` / `components.md` / `flows.md` / `failure-patterns.md` | No change | Already free of the term |
| `USER_MANUAL.md` | No change | Already free of the term |

### New Requirement IDs

None. No requirements were added or removed; three were re-worded.

### Detailed Changes

#### `01_requirements/DrivingCoach_SRS_v1.md`

```diff
-# BMW Driving Coach — System Requirements Specification
-**Document ID:** BMW-DC-SRS-001
+# Trillian — Driving Coach SRS
+**Document ID:** DC-SRS-001
```

The identifier was renamed rather than frozen because nothing in the repository
cites it; `git grep DC-SRS` returns only the definition itself.

Three requirements were re-worded. UM-19 is a pure rename, but SR-10 and SH-03
were **describing behaviour the app no longer has**, so they were corrected
against the source rather than merely de-branded:

| ID | Was | Now | Evidence |
|----|-----|-----|----------|
| UM-19 | "on a BMW blue background" | "on a brand blue (`#1C69D4`) background" | `bg_avatar_circle.xml` -> `@color/colorPrimary` |
| SR-10 | title `'BMW Driving Coach'`, content `'Recording — MM:SS'` | title `'Driving Coach — Recording'`, content `'MM:SS'` | `TelemetryForegroundService.kt:530` |
| SH-03 | `'BMW DRIVING COACH'` branding, BMW blue | `'Driving Coach'` branding, brand blue | `ShareCardGenerator.kt:46` draws mixed case, not caps |

#### `05_tests/L4_SYS5_acceptance/40_SESSION_RECORDING_TESTS.md`

SVC-01 carried the same drift as SR-10 — it expected the em-dash in the content
line instead of the title. A tester following it would have raised a false defect.

```diff
-| 3 | Notification title | "Driving Coach" | ☐ |
-| 4 | Notification content | "Recording — MM:SS" | ☐ |
+| 3 | Notification title | "Driving Coach — Recording" | ☐ |
+| 4 | Notification content | "MM:SS" | ☐ |
```

SHARE-03 needed no change; it already described the card the code draws.

#### `05_tests/infra/scripts/generate-html-report.py`

```diff
-:root{--bmw:#1C69D4;...
+:root{--brand:#1C69D4;...
```

Three sites — the declaration, the header gradient, and the KPI border-top.
Rendered output is byte-identical apart from the token name.

#### `backend/package-lock.json`

`package.json` was renamed to `driving-coach-backend` at some point without the
lockfile following. Both `name` fields were aligned by hand.

**Two occurrences of the letters `bmw` deliberately remain**, at lines 1210 and
3457. They are fragments of base64-encoded SHA-512 integrity hashes
(`sha512-bEPFOaMAHTEP1EzpvHTbmwR8...`, `sha512-Rm0BMWtxBcio...`) and are not
branding. Editing them would break `npm ci`. A future de-branding sweep must not
"finish the job" here — line 1210 was checksummed before and after this edit to
prove it was untouched.

### Verification

```
git grep -Iin bmw            -> 2 hits, both integrity hashes
sha256sum <line 1210>        -> unchanged before/after
json.load(package-lock.json) -> parses, name = driving-coach-backend
py_compile generate-html-report.py -> OK
--brand x3, --bmw x0
```

No Kotlin, XML or resource file was modified, so app behaviour is unchanged and
no build or L1 run was required.

### Files Modified

```
M  01_requirements/DrivingCoach_SRS_v1.md                        (+5, -5)
M  05_tests/infra/scripts/generate-html-report.py                (+3, -3)
M  05_tests/L4_SYS5_acceptance/40_SESSION_RECORDING_TESTS.md     (+2, -2)
M  backend/package-lock.json                                     (+2, -2)
```

### Recommendations

- [ ] Historical APKs under `releases/00_old_versions/` keep the `BMW_DrivingCoach_*` filenames and contain the old strings internally. Left deliberately — they are a record of what shipped.
- [ ] Git history still contains the term; rewriting it was out of scope.

---

## [2026-09-10] The report names the human, and section 2 names the strategy

**Codebase Version:** v2.95
**Trigger:** Two requested changes to the HTML report header and section titles.

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `generate-html-report.py` | Updated | `HUMAN` constant, meta entry, section 2 renamed |
| `generate-report.sh` | Updated | Same attribution line in the Markdown report |
| `test_generate_html_report.py` | Updated | +3 tests (51 -> 54), order guard strengthened |
| `Test_Strategy.md` | Updated | Section table, and why the name is a constant |
| `05_tests/reports/README.md` | Updated | Section table |
| `test_strategy_execution_instructions.md` | Updated | Expected test count |

### Attribution

The header now closes with **Human behind the wheel: Emidio Costa**. An
ASPICE-style test record names who executed it; automation produced the numbers
but a person is answerable for them.

Hardcoded rather than read from `git config`. Reading the machine would make the
report differ depending on who regenerated it, and evidence that changes with
the reader is not reproducible evidence. It also keeps an email address out of
an artefact that gets handed to people — a test asserts `HUMAN` contains no `@`.

The Markdown report carries the same line, on the same reasoning that already
aligned the two titles: two artefacts describing one run should not disagree.

### Section 2

`2. How we test` is now `2. How we test — Software and System Test Strategy`.

The originally requested wording was *System Integration Test Strategy*. The
section documents four levels — SWE.4 unit, SWE.5 integration, SWE.6
qualification and SYS.5 acceptance — so naming it after one of them would have
described the section as narrower than it is. In a report whose value rests on
not overstating anything, a heading that misdescribes its own contents is a
poor trade. The agreed wording spans software and system levels and stays true.

### A guard that could not see an edit

The rename **passed the existing order test untouched**, which it should not
have. `test_sections_run_evidence_before_interpretation` matched heading
substrings, and the old title `2. How we test` is still a prefix of the new one.

It now extracts every `<h2>` and asserts exact list equality, which catches
renames, reorders, insertions and deletions in a single assertion. Verified
negatively: appending one word to a heading fails the suite. The previous
version would have let any suffix through silently.

### Validation

| Check | Result |
|-------|--------|
| Generator self-tests | 54/54 |
| Order guard fails on a deliberate rename | Confirmed |
| Determinism (`--source-date`) | Byte-identical |
| Meta block renders 6 fields ending in the attribution | Confirmed |

## [2026-09-10] Evidence lives with the release, not in the working tree

**Codebase Version:** v2.95
**Trigger:** Two decisions taken by the human: test-run directories should not
accumulate in the repository, and the README must not carry hardcoded counts.

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `.gitignore` | Updated | `05_tests/reports/RUN_*/` now ignored |
| `05_tests/reports/` | Untracked | 1 `RUN_` directory and 9 legacy flat reports removed from the index; files kept on disk |
| `05_tests/reports/README.md` | Updated | "What is committed" now says nothing is |
| `README.md` | Updated | Every count replaced by a link |

### Why runs are no longer committed

A `RUN_` directory is about 220 KB, almost all of it HTML that differs little
between runs. Committing one per run would add tens of megabytes of
near-duplicate evidence to a repository whose `.git` is already 62 MB, in
exchange for snapshots nobody navigates by.

Evidence still exists where it means something: `releases/v<version>-<slug>/`
holds the APK together with the report describing it. That is the moment worth
freezing — a build someone might install.

Nine legacy `TEST_REPORT_*.md` files had been tracked while simultaneously
matching an ignore rule, because git does not retroactively ignore a file it
already follows. That contradiction is now resolved. Nothing is lost: they
remain in history.

### Why the README no longer states counts

It had drifted to claiming 84 passing tests when 322 ran, and 117 requirements
when there were 245. The fix earlier today was to correct the numbers, which
only reset the clock — the next run would make them wrong again.

Counts now appear only in generated artefacts. The README links to the
traceability matrix and the test report and says "see there". Badges no longer
carry figures: `tests-ASPICE L1 + L2` and `SRS-traceability matrix` are true for
as long as the statements behind them are.

The status table keeps its two ⚠️ rows but describes them qualitatively —
"several classes sit behind a class-level `@Ignore`", "partial coverage" —
so the admission survives without a number to go stale.

## [2026-09-10] Documentation drift audit — four stale artefacts

**Codebase Version:** v2.95
**Trigger:** Asked whether the docs still reflected the project. Audited every
documentation artefact against the code rather than assuming. Four had drifted;
one was actively wrong.

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `README.md` | ✅ Updated | Badges, counts, dead directory reference, test commands, Analysis tab, V1/V2 scope |
| `05_tests/reports/README.md` | ✅ Rewritten | Documented the pre-`RUN_` world and denied that reports are committed |
| `atlas/system.md` | ✅ Updated | `releases/` is directories now, not a filename series; version snippet said 2.8 |
| `atlas/failure-patterns.md` | ✅ Updated | New `FP-TEST-BLINDSPOT` |

### What was wrong

The root README had not been touched since 26 Aug and **understated the test
suite by roughly 4x**:

| Claim | Reality |
|-------|---------|
| Badge `tests-84 passing` | 322 executed of 351 declared |
| Badge `SRS-117 requirements` | 245 |
| `human_system_acceptance_tests/` in the tree | Directory does not exist |
| `135` acceptance tests | 133 |
| "Run tests: `./gradlew test`" | `run-all-tests.sh` is the documented path |

The dead directory is worth noting: a sync entry from an earlier session
recorded renaming that path to `05_tests/L4_SYS5_acceptance/`, but two
references in the README were missed. A rename is not complete until nothing
points at the old name.

`05_tests/reports/README.md` (22 Jul) described a single flat Markdown file per
run and stated that reports are *not* committed. Both untrue since the `RUN_<ts>/`
change: runs now produce Markdown **and** HTML, and the directories are tracked.

`system.md` described `releases/` as a flat filename series. `package-release.sh`
has since made releases directories carrying the APK, its test report and notes.
Documentation contradicting the code is the failure mode the Atlas exists to
prevent, so it is now written down with the reasoning: an APK on its own asserts
nothing about whether it was tested.

### The pattern worth keeping

`FP-TEST-BLINDSPOT` records the most valuable finding of the report work: a
class-level `@Ignore` collapses a whole test class into **one** `<testcase
name="null">` entry, so a suite looks green while dozens of tests never run.
Four classes hid 29 tests, and 8 coverage claims rested on them.

It is a measurement failure rather than a code failure — every tool downstream
faithfully reported what the XML said. The mitigation is to count `@Test` in the
source and print declared against executed, so a suite that skips everything
scores zero instead of passing. Two parser subtleties are recorded with it,
because both were real bugs: brace-depth nesting (a private fake inside a test
class swallowed 22 tests) and matching `@Test` on a word boundary (`@TestInstallIn`
inflated the count by 2).

### Honesty in the status table

The README's status table now carries two ⚠️ rows it did not have before: L2 at
60/89, and V1 requirements coverage at 68/190 (36%). These are not new problems,
only newly visible ones. For a project whose thesis is engineering rigour, a
number that flatters us is worth less than one we can defend.

### Reviewed, no change needed

`SRS`, `TRACEABILITY_MATRIX`, `USER_MANUAL`, `flows.md`, `components.md`,
`Test_Strategy.md` — synced during the work they describe.
`instructions.md` (14 Jul) is a stable operating model carrying no stale facts.

### Open decision

`RUN_<ts>/` directories are tracked, so every future run adds ~215 KB of HTML to
the repository permanently. Flagged to the human, not decided here.

---

## [2026-09-10] Report identity — the helmet and the name

**Codebase Version:** v2.95
**Trigger:** The report is handed to people who were not in the room. It carried
no mark and no programme name, so nothing tied it to the product it describes.

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `05_tests/infra/assets/helmet.png` | ✅ Created | 144x144 emblem, 3.2 KB, pre-scaled from the brand master |
| `05_tests/infra/assets/README.md` | ✅ Created | Provenance, the determinism and size rationale, regeneration command |
| `generate-html-report.py` | ✅ Updated | `logo_data_uri()`, `default_title()`, flex header, logo left of the title |
| `generate-report.sh` | ✅ Updated | Markdown report takes the same title, read from `appVersionName` |
| `test_generate_html_report.py` | ✅ Updated | +8 tests (43 → 51), new `BrandingTest` |
| `Test_Strategy.md` | ✅ Updated | §7 records the title, the embedding rule and why the asset is committed |
| `test_strategy_execution_instructions.md` | ✅ Updated | §1.6 expects 51 tests and explains the emblem guards |
| `atlas/components.md` | ✅ Updated | 3 failure modes, asset rows, cross-link from Brand Assets |

### What changed

Title is now `Trillian · Driving Coach v2.95 — Test Report` in both the HTML and
the Markdown report, which previously disagreed — the Markdown heading was a
bare `# Test Report`. The string is also the `<title>`, so it names the browser
tab and the PDF a reader saves.

The app's yellow helmet sits left of the title, the same mark seen on the splash
screen, so the report and the product are visibly one thing.

### Three decisions worth keeping

**Embedded, not linked.** `package-release.sh` copies only `TEST_REPORT.html`
into the release directory. A relative `src` would pass the no-external-URL test
and still render broken in the one place a stranger opens the file.

**Committed pre-scaled, not resized at generate time.** Running Pillow during
generation would tie output to the image library on the machine and break
byte-reproducibility. The generator now only base64-encodes bytes; it has no
image dependency at all. Cost: 4.5 KB on a 213 KB report, +2.1%.

**Ornaments cannot break evidence.** A missing asset degrades to a plain header
rather than raising. A test report that fails to generate over decoration would
be a worse defect than the missing decoration.

### Guards added

Incident 11 was this exact artwork rendered deformed. Tests now assert the asset
is square, that the `<img>` states equal explicit width and height, and that it
stays under 16 KB so the 257 KB master cannot be copied over it unnoticed.

### Validation

| Check | Result |
|-------|--------|
| Generator self-tests | ✅ 51/51 |
| Determinism (`--source-date`) | ✅ byte-identical |
| HTML nesting | ✅ 149/149 divs balanced |
| Embedded logo round-trip | ✅ decodes to 144x144, hash matches committed asset |
| Release repackaged | ✅ HTML + Markdown retitled |

---

## [2026-09-10] Report section order — evidence before interpretation

**Codebase Version:** v2.95
**Trigger:** The report asserted requirements coverage before showing a single test name.
The test inventory and the manual checklist now come first.

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `generate-html-report.py` | ✅ Updated | Section order, 7 headings renumbered, 5 cross-references, closing L4 reminder |
| `test_generate_html_report.py` | ✅ Updated | +3 tests (40 → 43): full order assertion, closing reminder, dangling-reference check |
| `Test_Strategy.md` | ✅ Updated | §7 section table reordered, with the rationale |

### New order

| # | Section | Was |
|---|---------|-----|
| 1 | Results by test level | 1 |
| 2 | How we test | 2 |
| 3 | **Failures** | 5 |
| 4 | Every automated test in this run | 6 |
| 5 | Manual acceptance tests (L4) | 7 |
| 6 | Requirements coverage | 3 |
| 7 | Gaps and caveats | 4 |

### Two deviations from the literal request, both agreed

The request was to move sections 6 and 7 above section 3. Taken literally that would have
left **Failures last**, below 245 requirement rows and a 55-row deferred table — on a red
run, the one thing that matters would be the hardest thing to reach. Failures moved to 3
instead.

Moving L4 up also reversed an earlier explicit requirement that manual tests end the
document. The warning banner stays at its new position, and a closing reminder was added
after section 7 so the parting thought is still the human's:

> Before this build is trusted on track, a human still has to run the 133 manual checks in
> section 5.

### Guarding the order

Reordering prose is exactly the kind of edit that silently leaves a *"see section 4"*
pointing at the wrong heading. Two new tests prevent it:

- `test_sections_run_evidence_before_interpretation` asserts all seven headings appear in
  the intended sequence, so a future move fails loudly instead of shuffling the narrative.
- `test_no_dangling_section_references` extracts every `see section N` from the rendered
  page and asserts that section exists.

### Validation

- Generator tests: **43/43 PASS** (the one ordering test that should have broken, did)
- Rendered order verified: 1–7 in sequence; all 5 cross-references resolve
- Deterministic, valid HTML nesting, no JS, no external URLs

---

## [2026-09-10] Scope map — separating deferred from untested

**Codebase Version:** v2.95
**Trigger:** The report counted a backend route V1 never builds and a shipped lap-detection
requirement as the same kind of gap. Scope decisions are now recorded and reported separately.

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `05_tests/scope-map.tsv` | ✅ New | 27 lines: 2 section sweeps, 2 V1 overrides, 23 per-ID deferrals |
| `generate-html-report.py` | ✅ Updated | Scope parsing, two denominators, `V2-BACKEND` badge, deferred table, 3 guards |
| `test_generate_html_report.py` | ✅ Updated | +14 tests (26 → 40) |
| `Test_Strategy.md` | ✅ Updated | New "The scope map" subsection in §7 |
| `test_strategy_execution_instructions.md` | ✅ Updated | §1.4: quote both denominators; how to read a scope warning |
| `components.md` | ✅ Updated | Test Evidence Pipeline: scope inputs and 2 new failure modes |
| `DrivingCoach_SRS_v1.md` | — | Deliberately unchanged; scope facts live in one reviewable file, not scattered through 550 lines of prose |

### The rule applied

The discriminator is **the subject of the sentence**:

- *"The backend shall …"* → `V2-BACKEND`. There is nothing to run it on.
- *"The app shall …"* → V1, **even when the sentence mentions upload.** The client half of a
  network feature is testable against a fake server, and TU-04, TU-06 and TU-07 already pass
  that way against MockWebServer. Deferring them would have hidden work that is done.

This rule caught two sweeps that would have been wrong:

| Nearly swept | Why it stayed V1 |
|---|---|
| `OC-01` … `OC-09` | Sit inside the *AI coaching feedback* SRS section, but are the offline engine — shipped and 100% covered |
| `AI-11` … `AI-14` | The COACH tab, which renders today from local insights |
| `UM-18`, `UM-19` | The Profile screen ships in V1; the SRS already amends UM-18 for it |

### Result

| Measure | Before | After |
|---|---|---|
| Requirements claimed | 68 / 245 (28%) | 68 / **190 V1** (36%), 55 deferred |
| "Uncovered" pile | 177, undifferentiated | 122 V1 gaps + 55 deferred, each with a reason |

The 122 remaining gaps are the honest ones: `LC` (15), `LD` (15), `SH` (11), `TS` (11),
`DP`/`SM` (13) all ship today and are untested.

### Guards, verified by deliberately breaking the file

| Guard | Message produced |
|---|---|
| Contradiction | `OC-01 is marked V2-BACKEND but has a passing test (OfflineCoachingEngineTest)` |
| Unknown section | `no SRS section named 'No Such Section'` |
| Unknown requirement | `no such requirement ZZ-99` |
| Unknown scope value | `unknown scope 'BOGUS'` |

A deferred requirement may still carry an L4 manual claim; only a passing automated test is
contradictory.

### Validation

- Generator tests: **40/40 PASS**
- Report regenerated: deterministic, valid HTML nesting, no JS, no external URLs
- Real data: 0 scope errors — no contradiction, no rotted target

---

## [2026-09-10] Test Evidence Pipeline — HTML report and release packaging

**Codebase Version:** v2.95
**Trigger:** Test reports now ship with the APK. A run produces a self-contained HTML
report generated from machine-readable evidence, and a release groups the build with
the report and notes that belong to it.

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `components.md` | ✅ Updated | +1 component (Test Evidence Pipeline), criticality matrix row |
| `Test_Strategy.md` | ✅ Updated | §7 rewritten: run directories, HTML report, coverage map, releases; 4 script rows added |
| `test_strategy_execution_instructions.md` | ✅ Updated | §1.4 rewritten, new §1.5 (packaging) and §1.6 (generator self-tests) |
| `TRACEABILITY_MATRIX.md` | ✅ Updated | Header note: the TSV is now the machine truth, this table is the narrative |
| `system.md` | — | No change (build-time tooling, no runtime component) |
| `flows.md` | — | No change (no new runtime flow) |
| `failure-patterns.md` | — | No change (no new product failure mode) |
| `DrivingCoach_SRS_v1.md` | — | No change (no product behaviour changed) |
| `USER_MANUAL.md` | — | No change (nothing user-facing in the app changed) |

### New Files

| File | Purpose |
|------|---------|
| `05_tests/infra/scripts/generate-html-report.py` | The report generator |
| `05_tests/infra/scripts/test_generate_html_report.py` | 26 unit tests for it |
| `05_tests/infra/scripts/package-release.sh` | APK + report + release notes under `releases/v<ver>-<slug>/` |
| `05_tests/coverage-map.tsv` | 94 requirement-to-test claims, machine-checked every run |

### What the first report found

Numbers that were previously invisible, now stated on the front page:

| Measure | Value |
|---------|-------|
| L1 declared / executed | 262 / 262 |
| L2 declared / executed | 89 / 60 |
| Tests that exist but never run | **29**, behind 4 class-level `@Ignore`s |
| Requirements with an automated claim | 68 / 245 (28%) |
| Claims not backed by a passing test | 8 |
| Claims citing a test that no longer exists | 0 |

The 29 hidden tests are the reason declared counts are read from the Kotlin source
rather than the JUnit XML: Gradle reports an `@Ignore`d class as one skipped entry
regardless of how many tests it contains, so the XML alone cannot see them.

### Design decisions

- **No prose is parsed.** Inputs are JUnit XML, a TSV, and rigid SRS table rows. A
  report that guesses at a document's meaning can overstate coverage without anyone
  noticing, which is exactly the failure it exists to prevent.
- **Level is not stored in the TSV.** It is derived from which results file the class
  appears in, so a claim cannot assert the wrong level.
- **Failures are annotated, never hidden.** Skipped, missing and failed claims all get
  their own section.
- **Manual L4 tests come last**, with an explicit note that a human must run them.
- **Deterministic output.** Sorted iteration and `--source-date` mean two runs of the
  same inputs are byte-identical, so reports can be diffed and therefore reviewed.

---

## [2026-09-09] Session Analysis Tab (ANALYSIS)

**Codebase Version:** v2.95
**Trigger:** New fourth tab on the Session Result screen — a derived, fully offline
track-engineer report (statistics, drawn track map, corners, braking zones, speed trace)

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `system.md` | ✅ Updated | +4 lines (UI key classes), version table corrected 2.8 → 2.95 |
| `components.md` | ✅ Updated | +141 lines (2 new components, criticality matrix rows) |
| `flows.md` | ✅ Updated | +98 lines (1 new flow) |
| `failure-patterns.md` | ✅ Updated | +113 lines (2 new patterns) |
| `DrivingCoach_SRS_v1.md` | ✅ Updated | +29 lines (new §9a, 17 requirements) |
| `TRACEABILITY_MATRIX.md` | ✅ Updated | +19 rows, coverage summary and total revised |
| `USER_MANUAL.md` | ✅ Updated | New §5.4, §5.4 → §5.5, 4 new FAQ entries |
| `60_SESSION_RESULTS_TESTS.md` | ✅ Updated | +89 lines (ANA-01..ANA-06), RES-01 now expects 4 tabs |

### New Requirement IDs

`AS-01` … `AS-17` — Session analysis (SRS §9a).

### New Failure Patterns

| ID | Pattern |
|----|---------|
| `FP-LABEL-VS-DATA` | A flag that describes the request instead of the result |
| `FP-STATIONARY-GEOMETRY` | Geometry derived while the car is parked (recurrence of `FP-DEGENERATE-BASELINE` in a second consumer) |

### New Acceptance Test IDs

`ANA-01` … `ANA-06`.

---

## Detailed Changes

### 📁 atlas/system.md

**Modified:**
```diff
- | Version Code | 208 (derived: `major*100 + minor`) |
- | Version Name | 2.8 (single source of truth in `app/build.gradle.kts`) |
+ | Version Code | 295 (derived: `major*100 + minor`) |
+ | Version Name | 2.95 (single source of truth in `app/build.gradle.kts`) |
```

**Added:**
```diff
+ - `AnalysisFragment` / `AnalysisViewModel` — ANALYSIS tab: derived session report, offline
+ - `TrackMapView` — custom `View` drawing the track outline from GPS, no map SDK
```

### 📁 atlas/components.md

**Added Sections:** *Session Analysis Engine (`SessionAnalysisProcessor`)*, *TrackMapView* —
including the full threshold table and the note that every threshold is expressed per second
rather than per sample. Two rows appended to the criticality matrix (both LOW).

### 📁 atlas/flows.md

**Added Section:** *Flow: Session Analysis (ANALYSIS Tab)* — execution path, async boundaries
(IO probe → IO read → Default maths → main thread bind), failure points, and the explicit
statement that the flow has no external dependencies and writes nothing.

### 📁 atlas/failure-patterns.md

Both patterns were **found by the tests written for this feature**, not by inspection:

| ID | Found by | Fix |
|----|----------|-----|
| `FP-LABEL-VS-DATA` | `SessionAnalysisGuardsTest`, `AnalysisTabTest` | Derive labels from the outcome (`lapRange`, `File.canRead()`), never from the request |
| `FP-STATIONARY-GEOMETRY` | scratch probe over the `teste3` fixture | `MIN_BEARING_TRAVEL_M = 2.0`, `MIN_CORNERING_SPEED_KMH = 10.0` |

### 📁 01_requirements/DrivingCoach_SRS_v1.md

**Added Section 9a — Session analysis (ANALYSIS tab)**

| ID | Requirement |
|----|-------------|
| AS-01 | Fourth tab labelled 'ANALYSIS', after 'CHART' |
| AS-02 → AS-03 | Session statistics and their exact definitions |
| AS-04 → AS-05 | Offline track map: no SDK, no tiles; speed gradient, red braking, `T1..Tn`, S/F marker |
| AS-06 → AS-07 | Lap chips from §8 laps; reference defaults to best lap and is selectable |
| AS-08 → AS-09 | Yaw-rate corner detection and the order-of-passage numbering caveat |
| AS-10 → AS-12 | GPS-only braking detection, g provenance caveat, corner association |
| AS-13 | Whole-session speed graph |
| AS-14 → AS-15 | Rate invariance; minimum baseline and minimum cornering speed |
| AS-16 → AS-17 | Honest degraded states: missing file vs. short session; labelled whole-session fallback |

### 📁 docs/USER_MANUAL.md

**Added Section 5.4 — The Analysis Tab** (previous §5.4 *Managing Sessions* renumbered to §5.5),
covering the stats table, how to read the coloured map, lap selection, apex speeds, braking
figures, the speed graph and every empty state.

**Added FAQ Entries:**
```markdown
**Q: The corner numbers in the Analysis tab don't match the circuit's map. Why?**
**Q: Does the Analysis tab use my phone's motion sensors?**
**Q: Why is my average speed so low?**
**Q: Does the Analysis tab need internet?**
```

### 📁 05_tests/L4_SYS5_acceptance/60_SESSION_RESULTS_TESTS.md

**Added Test Cases:**

| ID | Test | Expected Result |
|----|------|-----------------|
| ANA-01 | Analysis tab layout | 4th tab opens, all five statistics populated and consistent with the LAPS tab |
| ANA-02 | Track map fidelity | Outline recognisable, colours match speed, red where braked, draws in flight mode |
| ANA-03 | Corner detection plausibility | Corner count, directions and apex speeds match the real circuit |
| ANA-04 | Braking zones | One per heavy braking point, 0.1–1.0 g, correct corner association |
| ANA-05 | Reference lap selection | Best lap preselected, selection redraws everything and relabels |
| ANA-06 | Degraded sessions | Missing file, short session, no laps and stationary recording all handled honestly |

**Modified:** RES-01 now expects **4** tabs and the label list `"LAPS", "COACH", "CHART", "ANALYSIS"`.

---

## Validation

| Level | ASPICE | Result |
|-------|--------|--------|
| L1 unit | SWE.4 | **262/262 passed** (29 new across 4 analysis test classes) |
| L2 integration | SWE.5 | **64/64 executed passed**, 4 pre-existing `@Ignore` skips (4 new in `AnalysisTabTest`) |
| L3 qualification | SWE.6 | Not implemented |
| L4 acceptance | SYS.5 | Checklist extended (ANA-01…ANA-06), awaiting a track day |

Report: `05_tests/reports/TEST_REPORT_2026-09-09_22-22-34.md`

---

## Files Modified

```
M  atlas/system.md                                            (+4, -2)
M  atlas/components.md                                        (+141)
M  atlas/flows.md                                             (+98)
M  atlas/failure-patterns.md                                  (+113)
M  01_requirements/DrivingCoach_SRS_v1.md                     (+29)
M  01_requirements/TRACEABILITY_MATRIX.md                     (+22, -3)
M  docs/USER_MANUAL.md                                        (+79, -1)
M  05_tests/L4_SYS5_acceptance/60_SESSION_RESULTS_TESTS.md    (+89, -2)
M  app/build.gradle.kts                                       (+1, -1)
```

---

## Recommendations

- [ ] ANA-02 and ANA-03 need a real track day: corner counts have only been checked against one
      recorded fixture (`teste3`) and the user's own Python analysis of it.
- [ ] Rate invariance is proven by interpolating the 1 Hz fixture to 10 Hz. A genuine 10 Hz
      recording should be added as a fixture at the next opportunity.
- [ ] `MIN_BEARING_TRAVEL_M` now exists in two places conceptually — the lap detector and the
      analysis engine. Consider a shared primitive so the next consumer inherits the guard
      instead of rediscovering `FP-DEGENERATE-BASELINE`.

---

## [2026-09-08] Lap Detection Geometry Corrected (Incident 13)

**Codebase Version:** v2.94 (branch `improve_lap_detection`)
**Trigger:** Incident 13 — "No laps detected" reported after a session in which the driver
completed five laps; the captured start/finish line lay parallel to the direction of travel

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `SRS_v1.md` §8 | ✅ Reworked | Local-first; LD-01–LD-13 rewritten, LD-14–LD-16 added, 4 Remarks |
| `SRS_v1.md` §15 | ✅ Updated | NF-16 added with a Remark |
| `components.md` | ✅ Updated | Lap detector section rewritten; `LapDiagnosticsWriter` added (+128) |
| `flows.md` | ✅ Updated | Detection path, constants, signals, failure points; backend section marked future (+45) |
| `failure-patterns.md` | ✅ Updated | `FP-DEGENERATE-BASELINE`, `FP-LAP-DOUBLE-COUNT` (+117) |
| `USER_MANUAL.md` | ✅ Updated | 3.1 note on capture order; troubleshooting rewritten; halved-lap-times entry added |
| `50_LAP_DETECTION_TESTS.md` | ✅ Updated | LD-CROSS-04 corrected; LD-CROSS-05/06/07 added |
| Incident 13 report | ✅ Updated | Resolution section |
| Incident 13 RCA | ✅ Updated | F2 correction |
| `PLAN_003` | ✅ Created | Backlog: one-tap start/finish capture |

### New Requirement IDs

- **LD-14** — heading-consistency guard (reject candidates > 60° from the first crossing)
- **LD-15** — crossing instants interpolated between GPS samples
- **LD-16** — detection reasoning recorded beside the telemetry
- **NF-16** — lap timing not quantised to the GPS sample interval

### Amended Requirements

| ID | Was | Now |
|----|-----|-----|
| LD-01 | "shall be performed server-side" | "shall be performed on the device… shall not require network connectivity" |
| LD-02 | segment-intersection against the user-defined line | crossing at the **midpoint** of the captured line |
| LD-03 | centroid fallback on the backend | no start line → no laps, reported to the user |
| LD-04 | cross-product intersection with the line segment | plane **perpendicular to direction of travel**, 15 m lateral extent |
| LD-06 | **200 m** | **50 m** (the value always shipped) |
| LD-08/09 | `processingStatus=FAILED` on the backend | on-device reporting |

### New Remarks (per the operator's convention — explanation, not requirement)

- Under §8 — Delivery 1 is local-only; backend is a future option
- Under LD-02/LD-04 — why the captured line's *direction* is discarded (it is noise, not a measurement)
- Under LD-04 — figure-of-eight double-count limitation, referencing Incident 13
- Under LD-06 — why 50 m and not more
- Under LD-16 — why observability is a requirement (Incident 09 was closed without a cause)
- Under NF-16 — why 1 Hz quantisation matters more than it looks

### New Test IDs

| ID | Test |
|----|------|
| LD-CROSS-05 | Capture order does not matter |
| LD-CROSS-06 | Start/finish captured along the track (Incident 13 regression) |
| LD-CROSS-07 | Lap boundary timing precision |

**LD-CROSS-04 was corrected, not extended.** It previously required a pass 1–2 m to the side
*not* to count as a lap. That expectation was itself part of the defect: real laps pass within
0.5–2.1 m of the captured point every time.

### Detailed Changes

#### 📁 `01_requirements/DrivingCoach_SRS_v1.md`

```diff
- | LD-01 | Lap detection shall be performed server-side, asynchronously, after the JSONL file
-          is successfully stored in Azure Blob Storage. |
+ | LD-01 | Lap detection shall be performed on the device, after recording stops, from the local
+          JSONL telemetry file. It shall not require network connectivity. |
- | LD-06 | The driver must travel at least 200m from the start zone before a lap crossing is counted |
+ | LD-06 | The driver must travel at least 50 m from the start point before a lap crossing is counted |
+ | LD-14 | A candidate crossing whose direction of travel differs by more than 60° from the
+          direction of travel at the first accepted crossing of the session shall be rejected. |
```

#### 📁 `atlas/failure-patterns.md`

```diff
+ ## Pattern: A Direction Derived From Points Closer Than the Measurement Error
+     (FP-DEGENERATE-BASELINE) — ✅ FIXED (Incident 13)
+ ## Pattern: One Lap Counted Twice (FP-LAP-DOUBLE-COUNT) — ⚠️ ACCEPTED LIMITATION
```

The generalisation recorded, which outlives this incident:

> **Never derive a direction from two points separated by less than the measurement error.**

#### 📁 `docs/USER_MANUAL.md`

```diff
+ > **Don't worry about which way round you capture the two points.** The app works out the
+ > direction you're driving from the recording itself…
+ ### My Lap Times Are About Half What I Drove
```

### Code Changes Behind This Sync

| File | Change |
|------|--------|
| `LocalLapDetector.kt` | Crossing geometry rewritten; diagnostics types; `isValid()` fixed (+443, -107 overall) |
| `GeoUtils.kt` | `toLocalMetres()`, `bearingDegrees()`, `angularDifferenceDegrees()` (+39) |
| `LapDiagnosticsWriter.kt` | **New** — sidecar diagnostics, no Room migration |
| `RecordingViewModel.kt` | Switched to `detectLapsWithDiagnostics()`, writes the sidecar |
| `LapReplayHarness.kt` + `lapfixtures/teste3/` | **New** — replays real recorded sessions |
| `LapDetectionRealSessionTest.kt` | **New** — the Incident 13 session as ground truth |
| `LocalLapDetectorGuardsTest.kt` | **New** — guards, adversarial corner case, geometry self-check |
| `LapDiagnosticsWriterTest.kt` | **New** |
| `OneTapStartFinishCaptureTest.kt` | **New** — proves capture shape does not affect lap times |

### Validation

| Level | Result |
|-------|--------|
| L1 (SWE.4) | **233 tests, 0 failures, 0 skipped** |
| L2 (SWE.5) | **64 tests, 0 failures** (4 pre-existing skips) |

### Files Modified

```
M  01_requirements/DrivingCoach_SRS_v1.md                      (+39, -14)
M  atlas/components.md                                         (+128, -25)
M  atlas/failure-patterns.md                                   (+117)
M  atlas/flows.md                                              (+45, -12)
M  docs/USER_MANUAL.md                                         (+41, -16)
M  05_tests/L4_SYS5_acceptance/50_LAP_DETECTION_TESTS.md       (+67, -9)
M  app/src/main/java/com/drivingcoach/lap/LocalLapDetector.kt  (+443, -107)
M  app/src/main/java/com/drivingcoach/util/GeoUtils.kt         (+39)
M  app/src/main/java/com/drivingcoach/ui/recording/RecordingViewModel.kt (+13, -3)
A  app/src/main/java/com/drivingcoach/lap/LapDiagnosticsWriter.kt
A  app/src/test/java/com/drivingcoach/lap/*.kt                 (5 files)
A  app/src/test/resources/lapfixtures/teste3/
A  docs/plans/PLAN_003_One_Tap_Start_Finish_Capture.md
A  03_incidents/13_no_laps_detected_start_line_parallel_to_travel/
```

### Recommendations

- [x] `versionName` bumped to **2.94** (`versionCode` 294, derived); debug APK at
      `releases/DrivingCoach-v2.94-lap-detection-geometry.apk`
- [x] **LD-CROSS-06 run at the track on 2026-09-09 and passed** — start/finish captured along
      the track, laps detected correctly, lap times matched the drivers' own count
- [ ] `DETECTION_HALF_WIDTH_M = 15` is reasoned from **one** session. Add a second replay fixture
      from a different venue before treating it as settled
- [ ] Consider retaining `.lapdiag.json` in the diagnostic file share (6.1.1) so a user reporting
      a lap problem sends the reasoning with it

---

## [2026-09-07] GPS Warm-Up Lifetime and Start-Line Fix Freshness (Incident 12)

**Codebase Version:** v2.93
**Trigger:** Incident 12 — GPS acquisition slow / UX regression reported from human acceptance
testing, and finding F4 of its RCA

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `atlas/components.md` | ✅ Updated | +28 / -12 (warm-up lifetime, two new classes, revised design notes and failure modes) |
| `atlas/flows.md` | ✅ Updated | +38 / -6 (new Home → Track Setup handover flow; stop conditions rewritten) |
| `atlas/failure-patterns.md` | ✅ Updated | +122 (2 new patterns) |
| `01_requirements/DrivingCoach_SRS_v1.md` | ✅ Updated | +9 / -3 (TS-17 and TS-18 amended, 5 new requirements) |
| `docs/USER_MANUAL.md` | ✅ Updated | +31 / -8 (§3.0 corrected, new troubleshooting entry) |
| `05_tests/L4_SYS5_acceptance/30_TRACK_SETUP_TESTS.md` | ✅ Updated | +65 / -6 (2 new checklists, TS-00b corrected) |
| `atlas/system.md` | ⏭️ No change | No dependency, service or manifest change — `lifecycle-process` was trialled and removed |

### New Requirement IDs

| ID | Requirement |
|----|-------------|
| TS-21 | Capture shall reject fixes older than 3 s, checked at the request, on arrival and at the click |
| TS-22 | "Getting a current GPS fix…" shall be shown while only stale fixes are held, and shall clear automatically |
| TS-23 | Track Setup shall discard its held position whenever the location collector restarts |
| NF-14 | High-accuracy location shall not be held while the app is not in the foreground |
| NF-15 | Capture shall be reachable within 1 s of arriving at Track Setup when GPS was already ready |

### Amended Requirements

| ID | Change |
|----|--------|
| TS-17 | Readiness must now persist across the Home → Track Setup navigation |
| TS-18 | **Rewritten.** Previously *mandated* the defect: "GPS warm-up shall stop when the Home screen is no longer visible". Warm-up is now bounded by the app leaving the foreground, a recording starting, or a 30-minute backstop (was 3 minutes) |

### New Acceptance Test IDs

| ID | Test |
|----|------|
| TS-00d | The warm-up survives the walk from the paddock to the line — the journey whose absence from the checklist let this reach the track |
| TS-00e | Stale positions cannot become a start line, and the gate waits rather than blocks |

TS-00b was corrected: step 4 asserted that leaving Home ends the warm-up, which is the defect.

### Detailed Changes

#### 📁 01_requirements/DrivingCoach_SRS_v1.md

```diff
- | TS-18 | GPS warm-up shall stop when the Home screen is no longer visible, and shall stop
-   automatically after 3 minutes ... |
+ | TS-18 | GPS warm-up shall be bounded by the user's task rather than by any one screen. It
+   shall stop when the app leaves the foreground, when a recording starts, or after 30 minutes
+   ... It shall **not** stop merely because the Home screen is no longer visible. |
```

#### 📁 atlas/failure-patterns.md

**Added: FP-LIFECYCLE-SCOPE — Resource Scoped to the Wrong Lifecycle**

Generalises the root cause: when a long-lived resource serves a *task* that spans screens,
binding it to a fragment or view lifecycle makes the handover between screens its destruction
point — usually the moment it is most needed. Includes two reusable testing lessons:
`ProcessLifecycleOwner` does not dispatch `ON_STOP` under `ActivityScenario`, and
`ActivityScenario.moveToState(CREATED)` does not background an app (it launches an empty
Activity on top, exactly as navigation does), so a test using it to prove "backgrounding
releases the resource" is in fact asserting the bug.

**Added: FP-STALE-FIX — Stale Fix Captured as Ground Truth**

The silent counterpart: a cached or retained position becomes Point A, offsetting every lap in
the session by the same amount while the times still look plausible.

#### 📁 atlas/flows.md

**Added: "Flow: Home → Track Setup handover"** — this handover was documented nowhere before
this incident, which is part of why the defect was invisible. Records the ordering that made it
unrepairable from the receiving side: `HomeFragment.onStop()` runs *before*
`TrackSetupFragment.onStart()`, and `stop()` nulls the job that `start()`'s idempotence guard
checks.

#### 📁 docs/USER_MANUAL.md

```diff
- - Searching stops when you leave the app, and after 3 minutes of sitting on Home ...
+ - Once it's green, it stays green while you walk. Moving from Home to Track Setup doesn't
+   restart the search, so you only ever wait once.
+ - Searching stops when you leave the app ... also stops on its own after half an hour ...
```

The old §3.0 promised "Track Setup will be ready the moment you arrive". That promise was false
when written; it is true now, and the wording is sharpened to say so.

### Validation

Clean build, emulator `DrivingCoach_Test` (API 35):

| Level | ASPICE | Result |
|-------|--------|--------|
| L1 | SWE.4 | ✅ 209/209 (was 203 — +6) |
| L2 | SWE.5 | ✅ 56/60, 4 pre-existing `@Ignore` (was 26 tests — +6 new) |

Report: `05_tests/reports/TEST_REPORT_2026-09-07_22-53-25.md`

### Files Modified

```
M  atlas/components.md                                      (+28, -12)
M  atlas/flows.md                                           (+38, -6)
M  atlas/failure-patterns.md                                (+122, -0)
M  01_requirements/DrivingCoach_SRS_v1.md                   (+9, -3)
M  docs/USER_MANUAL.md                                      (+31, -8)
M  05_tests/L4_SYS5_acceptance/30_TRACK_SETUP_TESTS.md      (+65, -6)
A  03_incidents/12_gps_acquisition_slow_ux_regression/      (2 files)
```

### Recommendations

- [ ] **Measure the real TTFF delta trackside.** Every latency figure here is modelled by a
      test double. The user-visible magnitude is still unquantified — `Profile → About` now
      records it, so a single track session settles it.
- [ ] `FixFreshness.MAX_FIX_AGE_MS = 3 s` is reasoned, not measured. Watch for reports of the
      capture gate feeling sticky on devices with slower fused delivery.
- [ ] `setMaxUpdateAgeMillis` behaviour is provider-dependent; the two application-level checks
      are deliberately not relying on it.
- [ ] The 30-minute idle ceiling is a backstop, not a considered battery budget. If battery
      complaints appear, that constant is the first thing to revisit — not the handover fix.
- [ ] Consider whether other long-lived resources are scoped to a fragment lifecycle
      (FP-LIFECYCLE-SCOPE): the recording service's sensor registration is the obvious next
      candidate to audit.

---

## [2026-09-03] Local Driver Profile Replaces Login

**Codebase Version:** v2.92-driver-profile  
**Trigger:** V1 ships without a backend. Login was removed and replaced with a persistent
local driver name, fixing the defect where the app "forgot" the driver on every relaunch.

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `SRS_v1.md` | ✅ Updated | +8 requirements (DR-01…DR-08), new §2a; §2 (UM-01…UM-17) marked deferred to V2; AD-04, UM-18, ON-04, ON-06, ON-07 amended |
| `TRACEABILITY_MATRIX.md` | ✅ Updated | DR-01…DR-08 added at 100% coverage; UM rows re-classified as V2; ON-04/ON-07 now covered |
| `components.md` | ✅ Updated | +1 component (*Driver Profile Store*); Authentication Layer marked DORMANT; Splash inputs corrected |
| `flows.md` | ✅ Updated | +1 flow (*First-Run Driver Naming*); *App Startup* and *Onboarding* execution paths corrected |
| `failure-patterns.md` | ✅ Updated | +1 pattern (FP-UNREACHABLE-GATE), marked FIXED |
| `USER_MANUAL.md` | ✅ Updated | §2.4/§2.5 rewritten (account creation → driver name), §6.2 rename, §6.4 Clear User Data, +1 troubleshooting entry, 3 FAQ entries |

### New Requirement IDs

DR-01, DR-02, DR-03, DR-04, DR-05, DR-06, DR-07, DR-08 (new `DR-` prefix, §2a *Driver profile (V1 local)*)

### Root Cause Documented

Two defects that only manifested together: "Skip Login (Demo Mode)" navigated to Home while
persisting nothing, and `SplashViewModel` gated Home on a JWT that V1 never issues. A third
(`onboarding → home` directly) hid the inconsistency on first launch. Recorded as
**FP-UNREACHABLE-GATE** — *a navigation gate must read state some code path in the same
build actually writes*.

### Validation

| Level | Result |
|-------|--------|
| L1 (SWE.4 unit) | ✅ 203/203 passed |
| L2 (SWE.5 integration) | ✅ 49/53 passed, 0 failed (4 pre-existing `@Ignore`d E2E tests) |

New tests: `DriverProfileStoreTest` (22), `DriverNameViewModelTest` (12),
`ProfileViewModelTest` (11), `DriverNameFlowTest` (6, L2). `SplashViewModelTest` extended
from token-based to profile-based destination resolution.

### Files Modified

```
A  app/src/main/java/com/drivingcoach/data/profile/DriverProfileStore.kt
A  app/src/main/java/com/drivingcoach/ui/driver/DriverNameViewModel.kt
A  app/src/main/java/com/drivingcoach/ui/driver/DriverNameFragment.kt
A  app/src/main/res/layout/fragment_driver_name.xml
A  app/src/test/.../DriverProfileStoreTest.kt
A  app/src/test/.../DriverNameViewModelTest.kt
A  app/src/test/.../ProfileViewModelTest.kt
A  app/src/androidTest/.../DriverNameFlowTest.kt
M  app/src/main/java/com/drivingcoach/ui/splash/SplashViewModel.kt       (+25, -12)
M  app/src/main/java/com/drivingcoach/ui/profile/ProfileViewModel.kt     (+124, -47)
M  app/src/main/java/com/drivingcoach/ui/profile/ProfileFragment.kt      (+97, -12)
M  app/src/main/java/com/drivingcoach/ui/auth/LoginFragment.kt           (V2 placeholder)
M  app/src/main/java/com/drivingcoach/ui/auth/RegisterFragment.kt        (V2 placeholder)
M  app/src/main/java/com/drivingcoach/ui/onboarding/OnboardingFragment.kt
M  app/src/main/java/com/drivingcoach/data/db/dao/SessionDao.kt          (+getAllRawFilePaths)
M  app/src/main/res/navigation/nav_graph.xml                             (+35, -7)
M  app/src/main/res/layout/fragment_login.xml                            (fields removed)
M  app/src/main/res/layout/fragment_profile.xml
M  app/src/main/res/values/strings.xml                                   (+23)
M  01_requirements/DrivingCoach_SRS_v1.md                                (+45, -7)
M  01_requirements/TRACEABILITY_MATRIX.md                                (+22, -6)
M  atlas/components.md                                                   (+85, -4)
M  atlas/flows.md                                                        (+98, -4)
M  atlas/failure-patterns.md                                             (+62)
M  docs/USER_MANUAL.md                                                   (+70, -18)
```

### Release

`releases/DrivingCoach-v2.92-driver-profile.apk` — debug-signed, `versionName` 2.92,
`versionCode` 292. `appVersionName` bumped from 2.91, so the APK filename and the version
shown on *About Trillian* cannot disagree.

### Deferred to V2

`LoginFragment`, `RegisterFragment`, `AuthRepository`, `AuthInterceptor` and `AuthEventBus`
remain in the codebase, unreferenced by navigation and documented with `TODO(V2)` blocks, so
authentication can be restored without re-plumbing the startup flow.

---

## [2026-08-26] Session Telemetry Export (Developer Share)

**Codebase Version:** v2.9 (branch `FT-Dev-telemtry-export`)  
**Trigger:** Hidden developer telemetry export + fixing silent share failures

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| components.md | ✅ Updated | +63 lines (1 new component: Session Share & Telemetry Export) |
| flows.md | ✅ Updated | +87 lines (1 new flow: Session Share, both branches) |
| failure-patterns.md | ✅ Updated | +86 lines (2 new patterns: FP-TEST-HANG, FP-TEST-WORKMANAGER) |
| DrivingCoach_SRS_v1.md | ✅ Updated | +5 requirements (SH-07 → SH-11) |
| TRACEABILITY_MATRIX.md | ✅ Updated | +1 row (SH-07→11) |
| USER_MANUAL.md | ✅ Updated | New §6.1.1 "Sending a Diagnostic File" + share error note |
| 80_SHARE_AND_PROFILE_TESTS.md | ✅ Updated | +4 acceptance tests (SHARE-07 → SHARE-10) |
| L2_SWE5_integration/README.md | ✅ Updated | Test tree + new "Espresso Prerequisites" section |

### New Requirement IDs

- **SH-07** — hidden telemetry export via long-press, event consumed (no tooltip)
- **SH-08** — ZIP bundle: `telemetry.jsonl` (byte-for-byte) + `session.json` (diagnostics)
- **SH-09** — shared as `application/zip` via `ACTION_SEND` + FileProvider
- **SH-10** — share failures surfaced via Snackbar; never silent
- **SH-11** — **read-only invariant**: sharing never modifies recorded session data

### New Test IDs

| Level | IDs |
|-------|-----|
| L1 | 14 tests in `SessionShareBuilderTest` |
| L2 | 6 tests in `SessionShareTest` (SH-07, SH-08, SH-10, SH-11) |
| L4 | SHARE-07, SHARE-08, SHARE-09, SHARE-10 |

### Validation

| Level | Result |
|-------|--------|
| L1 | **134 / 134 passed**, 0 skipped |
| L2 | **36 tests, 0 failures**, 4 pre-existing `@Ignore` skips; all 6 share tests executed |
| Mutation check (L1) | Made the builder delete its source file → **4 read-only tests failed** as designed |
| Mutation check (L2) | Detached `attachTelemetryExportGesture()` → **3 gesture tests failed** as designed |

### Notes

- Plan deviation: the planned L1 test *"image path yields an `image/png` intent"* is not
  JVM-testable (`Bitmap.compress` is stubbed by `isReturnDefaultValues`, and `FileProvider`
  needs a real context). That coverage moved to L2 (`tappingShareSendsTheSessionCardImage`).
- Two pre-existing defects fixed along the way: share failures were caught and discarded
  (`// Handle error silently`), and share-card cache files were never pruned.
- Two test-infrastructure gaps fixed: the emulator never disabled animations, and
  `TestNetworkModule` could not provide a `WorkManager` under instrumentation (which crashed
  the whole instrumentation process for any WorkManager-backed screen).

### Files Modified

```
A  app/src/main/java/com/drivingcoach/util/SessionShareBuilder.kt          (+221)
A  app/src/test/java/com/drivingcoach/util/SessionShareBuilderTest.kt      (+329)
A  app/src/androidTest/java/com/drivingcoach/ui/session/SessionShareTest.kt (+276)
M  app/src/main/java/com/drivingcoach/ui/session/SessionResultFragment.kt  (+130, -20)
M  app/src/main/res/values/strings.xml                                     (+11)
M  app/src/androidTest/java/com/drivingcoach/di/TestNetworkModule.kt       (+12, -1)
M  05_tests/infra/scripts/start-emulator.sh                                (+15)
M  atlas/components.md                                                     (+63)
M  atlas/flows.md                                                          (+87)
M  atlas/failure-patterns.md                                               (+86)
M  01_requirements/DrivingCoach_SRS_v1.md                                  (+5)
M  01_requirements/TRACEABILITY_MATRIX.md                                  (+1)
M  docs/USER_MANUAL.md                                                     (+26)
M  05_tests/L4_SYS5_acceptance/80_SHARE_AND_PROFILE_TESTS.md               (+65)
M  05_tests/L2_SWE5_integration/README.md                                  (+24, -1)
```

### Recommendations

- [ ] The long-press gesture is inherently fragile (no compile-time anchor). If
      `SessionShareTest` is ever `@Ignore`d, the export is effectively unprotected.
- [ ] Consider surfacing the export in a developer-options screen once one exists.
- [ ] Deferred: backend/Android `MIN_DISTANCE_FROM_START_M` mismatch (200 m vs 50 m) and
      the live lap display feature.

---

## [2026-08-26] GPS Warm-Up on Home

**Codebase Version:** v2.8 (branch `UX_start_GPS_early`)  
**Trigger:** Field report — 45 s wait for GPS at the start/finish line during a manual track test

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| components.md | ✅ Updated | +78 lines — new component `GPS Warm-Up (LocationWarmUp)` |
| flows.md | ✅ Updated | +103/-13 — new flow `GPS Warm-Up (Home)`; Track Setup flow rewritten onto `LocationUpdates` + `repeatOnLifecycle` |
| failure-patterns.md | ✅ Updated | +57 lines — new pattern `Location Subscription Not Restored After Stop` (✅ FIXED) |
| SRS_v1.md | ✅ Updated | +5 requirements (TS-16…TS-20); TS-15 amended |
| TRACEABILITY_MATRIX.md | ✅ Updated | +6 covered rows; TS coverage 40% → 60% |
| USER_MANUAL.md | ✅ Updated | New §3.0 "The GPS Badge"; §3.1, §6.3, §7 updated |
| L2 README | ✅ Updated | Tree updated; new section "Location Is Injected, Not Real" |
| 30_TRACK_SETUP_TESTS.md | ✅ Updated | +3 human test cases (TS-00, TS-00b, TS-00c) |

### New Requirement IDs

- **TS-16** — Warm-up starts when Home becomes visible
- **TS-17** — Home GPS readiness chip (hidden / amber / green)
- **TS-18** — Stops on background and after a 3-minute idle ceiling
- **TS-19** — Time-to-first-fix metrics recorded and shown on About
- **TS-20** — Warm-up exposes readiness only, never a position

**Amended:** TS-15 — now names `FusedLocationProviderClient` behind the `LocationUpdates`
abstraction (the SRS had said `LocationManager`/`GPS_PROVIDER`; the code has used Fused since
before this change) and adds the resubscribe-on-STARTED obligation.

### New Test IDs

- L4: `TS-00`, `TS-00b`, `TS-00c`
- L2: `HomeGpsChipTest` (6 tests), `TrackSetupResubscribeTest` (4 tests)
- L1: `LocationWarmUpTest` (17 tests), 4 new tests in `HomeViewModelTest`

### Validation

| Level | Result |
|-------|--------|
| L1 (SWE.4) | ✅ 142/142 passed |
| L2 (SWE.5) | ✅ 40 tests, 0 failures (4 skipped — pre-existing `@Ignore`d legacy classes) |
| L4 (SYS.5) | ⏳ New checklist items pending the next track day |

### Defect Recorded

`TrackSetupFragment` subscribed to location once in `onViewCreated()` while `onStop()`
removed updates — so any screen-off or app switch left "Acquiring GPS…" on screen
**permanently**. This may well be what the 45 s field report actually was. Fixed structurally
with `repeatOnLifecycle(STARTED)`; guarded by `TrackSetupResubscribeTest`.

### Files Modified

```
M  atlas/components.md                                       (+78)
M  atlas/flows.md                                            (+103, -13)
M  atlas/failure-patterns.md                                 (+57)
M  01_requirements/DrivingCoach_SRS_v1.md                    (+6, -1)
M  01_requirements/TRACEABILITY_MATRIX.md                    (+9, -3)
M  docs/USER_MANUAL.md                                       (+55, -8)
M  05_tests/L2_SWE5_integration/README.md                    (+22, -1)
M  05_tests/L4_SYS5_acceptance/30_TRACK_SETUP_TESTS.md       (+59, -1)
```

### Recommendations

- [ ] Run the L4 TS-00 checklist at the next track day and record the About-screen timings —
      this converts "45 seconds" from anecdote into evidence
- [ ] Audit for other `onViewCreated()`-subscribe / `onStop()`-cancel pairs; the same defect
      class may exist elsewhere
- [ ] Revisit the 3-minute idle ceiling once real usage data exists

---

## [2026-08-12] Splash Display Budget Anchored to the Platform Handoff

**Codebase Version:** v2.8  
**Trigger:** User report — the engineering manifesto was still unreadable on first run

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| components.md | ✅ Updated | `SplashVisibilitySignal` added; 2 new logcat signals |
| flows.md | ✅ Updated | Operational note rewritten with the measured anchor-error table |
| failure-patterns.md | ✅ Updated | +69 lines — new pattern `FP-TIMING-ANCHOR` |
| SRS_v1.md | ✅ Updated | UI-02 revised (no new IDs) |
| USER_MANUAL.md | ✅ Updated | §2.2 — the two-screen cold start explained |
| 20_ONBOARDING_TESTS.md | ✅ Updated | BRD-02 rewritten around the two-screen trap |
| L2 README | ✅ Updated | `visibilityTimeoutMs` propagation note |

### The Defect

SRS UI-02 promises a 4000 ms hold on the branded loading screen. It was being measured from
`SplashViewModel.start()`, which runs while the *platform* splash — the emblem alone on
black — still owns the window. The user therefore read the manifesto for far less than the
budget claimed.

A first attempt on 2026-08-11 moved the anchor to `View.doOnPreDraw`. That fires during the
fragment's layout pass, still ~1.2 s before the window is presented, so it only halved the
error. Measured against the platform's own `ActivityTaskManager: Displayed` marker:

| Anchor | Error | Delivered from a 4000 ms budget |
|--------|-------|--------------------------------|
| `start()` | ~1700 ms | ~2.3 s |
| `doOnPreDraw` | ~1000–1400 ms | ~2.6–3.0 s |
| `setOnExitAnimationListener` (current) | **~17 ms** | **~4.0 s** |

### The Fix

`SplashVisibilitySignal` (`@ActivityRetainedScoped`, a `CompletableDeferred<Long>`) carries
the handoff instant from `MainActivity` — which owns the platform splash — to
`SplashViewModel`, which owns the budget. The wait is bounded by the new
`SplashTimings.visibilityTimeoutMs` (2000 ms) and falls back to the startup clock, so a
missing signal degrades the hold instead of hanging it.

`@ActivityRetainedScoped` rather than `@Singleton` is deliberate: a process-wide instance
would leak the first launch's timestamp into every later one, which is harmless in
production but would silently delete the hold across an instrumented suite.

### No New Requirement IDs

UI-02 was **revised**, not replaced — the requirement always meant "this screen for 4 s";
the implementation simply measured it from the wrong instant.

### Validation

| Level | Result |
|-------|--------|
| L1 (SWE.4) | 120 tests, 0 failures — mutation-verified: reverting the anchor fails `hold is measured from the handoff, not from start` |
| L2 (SWE.5) | 34 tests, 0 failures (30 executed, 4 legacy `@Ignore`) |
| Device | Handoff agreed with `ActivityTaskManager: Displayed` to 16 ms and 18 ms across cold starts |

### Deliberately Not Covered at L2

An L2 assertion on this arithmetic would have to observe a 4 s window through Espresso,
which waits for main-thread idleness before it looks; on a loaded emulator that wait can
outlast the window. A first attempt did exactly that — green in the suite, red in isolation
— and was removed rather than left as a flaky gate. The reasoning is recorded in
`SplashIntroductionTest.kt` so it is not re-attempted.

### Files Modified

```
A  app/src/main/java/com/drivingcoach/ui/splash/SplashVisibilitySignal.kt   (+38)
M  app/src/main/java/com/drivingcoach/ui/MainActivity.kt                    (+17, -2)
M  app/src/main/java/com/drivingcoach/ui/splash/SplashViewModel.kt          (+86, -3)
M  app/src/main/java/com/drivingcoach/ui/splash/SplashTimings.kt            (+23, -2)
M  app/src/test/java/com/drivingcoach/ui/splash/SplashViewModelTest.kt      (+313, -5)
M  atlas/failure-patterns.md                                                (+69)
M  atlas/components.md                                                      (+71, -3)
M  atlas/flows.md                                                           (+39, -2)
M  01_requirements/DrivingCoach_SRS_v1.md                                   (+4, -1)
M  docs/USER_MANUAL.md                                                      (+30, -5)
M  05_tests/L4_SYS5_acceptance/20_ONBOARDING_TESTS.md                       (+55)
M  05_tests/L2_SWE5_integration/README.md                                   (+44, -1)
```

### Recommendations

- [ ] Judge BRD-02 on real hardware. 4.0 s against ~3.5 s of reading is a thin margin; if it
      still feels short, `introDisplayMs` is now a number that means what it says.
- [ ] Separately, the platform splash itself runs 3.0–3.7 s on the API 30 emulator because
      the first frame waits on a ~2 s DataStore read. Shortening that is untouched work.

---

## [2026-08-11] First-Run Introduction Window + About Screen

**Codebase Version:** v2.8 (versionCode 208)
**Trigger:** The engineering manifesto on the loading screen was unreadable — 14 words held
for 1200 ms, against the ~4 s a normal reader needs.

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `SRS_v1.md` | ✅ Updated | +4, −1 (UI-02 revised; UI-10, UI-11, UI-12 added) |
| `atlas/components.md` | ✅ Updated | +67, −3 (1 new component, splash failure modes + test hooks) |
| `atlas/flows.md` | ✅ Updated | +22, −2 (timing contract, execution path, L2 coverage, readability caveat) |
| `atlas/system.md` | ✅ Updated | +6, −3 ("no in-app version" gap closed) |
| `USER_MANUAL.md` | ✅ Updated | +23, −3 (§2.2 rewritten, §6.3 added, version lookup replaced) |
| `L2_SWE5_integration/README.md` | ✅ Updated | +38, −1 (new classes; `SplashTimings` propagation warning + baseline) |
| `L4_SYS5_acceptance/20_ONBOARDING_TESTS.md` | ✅ Updated | +47 (BRD-02, BRD-03) |
| `atlas/failure-patterns.md` | ⏭️ Not changed | No new *pattern* — the new failure modes are component-local and recorded in `components.md` |

### New Requirement IDs

- **UI-10** — visible tap affordance on the loading screen (an accessibility label alone does not satisfy it)
- **UI-11** — manifesto has a permanent home in an About screen reachable from Profile
- **UI-12** — About screen reports the build identity from `BuildConfig`

**Revised:** **UI-02** — adds the 4000 ms introduction window for the first 3 launches, and
states explicitly that a tap is *never required* to proceed.

### New Test IDs

- **BRD-02** (L4) — Engineering manifesto is actually readable
- **BRD-03** (L4) — About screen reports the build

### Verification

| Level | Result |
|-------|--------|
| L1 (SWE.4) | **117 / 0 / 0** — 8 new splash tests |
| L2 (SWE.5) | **34 tests, 0 failures** (30 executed, 4 legacy `@Ignore`) — 8 new |
| On-device | Logcat confirms `budget=4000ms` on launches 1–3 and `budget=1200ms` from launch 4; launch counter correctly stops incrementing at 3 |
| Visual | Loading screen verified on emulator with the new "Tap to continue" hint; Profile verified showing the About entry point |

### Notable Findings

1. **`BuildConfig` was not generated.** AGP 8 omits it unless `buildFeatures { buildConfig = true }`
   is set. Nothing in the app had referenced it, so the About screen would not have compiled.
2. **The introduction budget is a floor on *total* splash time, not on *readable* time.**
   It is measured from `SplashViewModel.start()`, so slow initialisation consumes part of it
   (measured: 1.4–3.4 s on the API 30 emulator). Documented in `flows.md`; `introDisplayMs`
   is a single constant if the field says it is still too fast.
3. **`hero_footer_metrics` was stale** — claimed "Tests - 104 ; SRS - 123 Requirements" against
   an actual 147 tests and 220 requirement IDs. Corrected, since the About screen now makes
   this claim permanently readable rather than momentary.

### Files Modified

```
M  01_requirements/DrivingCoach_SRS_v1.md                    (+4,  -1)
M  05_tests/L2_SWE5_integration/README.md                    (+38, -1)
M  05_tests/L4_SYS5_acceptance/20_ONBOARDING_TESTS.md        (+47, -0)
M  atlas/components.md                                       (+67, -3)
M  atlas/flows.md                                            (+22, -2)
M  atlas/system.md                                           (+6,  -3)
M  docs/USER_MANUAL.md                                       (+23, -3)
M  app/build.gradle.kts                                      (+3,  -0)
M  app/src/main/java/.../ui/splash/SplashViewModel.kt        (+59, -1)
M  app/src/main/java/.../ui/splash/SplashTimings.kt          (+16, -1)
M  app/src/main/java/.../ui/splash/SplashFragment.kt         (+5,  -0)
M  app/src/main/java/.../ui/profile/ProfileFragment.kt       (+4,  -0)
M  app/src/main/res/layout/fragment_splash.xml               (+17, -1)
M  app/src/main/res/layout/fragment_profile.xml              (+13, -0)
M  app/src/main/res/navigation/nav_graph.xml                 (+12, -1)
M  app/src/main/res/values/strings.xml                       (+9,  -1)
M  app/src/test/java/.../ui/splash/SplashViewModelTest.kt    (+206, -4)
M  5x L2 test classes (introDisplayMs pinned)                (+26, -0)
A  app/src/main/java/.../ui/about/AboutFragment.kt           (55 lines)
A  app/src/main/res/layout/fragment_about.xml                (142 lines)
A  app/src/androidTest/java/.../ui/about/AboutScreenTest.kt  (132 lines)
A  app/src/androidTest/java/.../splash/SplashIntroductionTest.kt (199 lines)
```

### Recommendations

- [ ] Validate the 4 s window on real hardware (BRD-02); the emulator's slow startup makes it
      the pessimistic case, but a fast phone may make 4 s feel long.
- [ ] Still **no CI** (`.github/workflows/` absent) — every gate here runs on demand only.
- [ ] `Log.d` startup calls could now be gated behind `BuildConfig.DEBUG`, newly available.

---

## [2026-08-11] Build Versioning (versionCode / versionName)

**Codebase Version:** v2.8  
**Trigger:** `versionName` had been frozen at `1.0.0` / `versionCode 1` across all 20 shipped APKs

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| `app/build.gradle.kts` | ✅ Updated | +17 −2 (derived version + APK output naming) |
| system.md | ✅ Updated | +49 −2 (Versioning Scheme section, corrected build-config table) |
| USER_MANUAL.md | ✅ Updated | +9 (how to find your app version, for support requests) |
| `L4/03_ANDROID_BUILD.md` | ✅ Updated | +13 −4 (corrected stale APK paths, added version-verification check) |
| components.md | ⏭️ No change | No component behaviour changed |
| flows.md | ⏭️ No change | No runtime flow changed |
| failure-patterns.md | ⏭️ No change | Build-config issue, not a runtime failure mode |
| SRS_v1.md | ⏭️ No change | No functional/user requirement affected — build metadata only |

### Problem

Version identity existed **only in `releases/` filenames**. Every APK from v1.0 to v2.8
reported `versionCode 1` / `versionName 1.0.0` internally, so:

- `adb` and the launcher's app-info screen could not distinguish any two releases
- a future crash reporter would attribute every crash to "1.0.0"
- users could not report which build they were on
- upgrade/downgrade semantics were undefined (equal `versionCode`)

### Change

`appVersionName` is now the single source of truth in `app/build.gradle.kts`;
`versionCode` is **derived** (`major*100 + minor`) so the two cannot drift:

| | Before | After |
|---|---|---|
| `versionName` | `1.0.0` | `2.8` |
| `versionCode` | `1` | `208` (derived) |
| APK filename | `app-debug.apk` | `DrivingCoach-v2.8-debug.apk` (generated) |

The generated filename is the important half: it makes a mislabelled copy into `releases/`
structurally impossible, which was the actual origin of the drift.

**Bump procedure:** edit `appVersionName` only. Never hand-edit `versionCode`.

### Verification

Confirmed against the built binary, not the Gradle declaration:

```
$ aapt2 dump badging app/build/outputs/apk/debug/DrivingCoach-v2.8-debug.apk | head -1
package: name='com.drivingcoach' versionCode='208' versionName='2.8' ...
```

- `assembleDebug` — SUCCESSFUL
- L1 — **109 tests / 0 failures / 0 skipped** (forced `--rerun`; an initial run reported
  `UP-TO-DATE` and was discarded as a stale result)
- `releases/DrivingCoach-v2.8-helmet-artwork.apk` refreshed — the previous copy reported `1.0.0`

### Decisions

- **No `-debug` versionName suffix.** Considered, since every APK in `releases/` is a debug
  build; the user opted for a plain `2.8` in both variants.
- **No in-app version display.** There is no About/Settings screen; adding one is a feature,
  not a build fix. Documented as a known gap in system.md and worked around in USER_MANUAL.md
  by pointing users at the OS app-info screen.

### Known Gaps

| Gap | Impact |
|-----|--------|
| Version not surfaced in-app | Users must use Settings → Apps → Driving Coach |
| `versionCode 1 → 208` | Devices with an existing install cannot downgrade to an older APK without uninstalling first |
| Bump is manual | Nothing enforces that `appVersionName` is incremented before a release copy |
| No CI | Unchanged — the version check in `03_ANDROID_BUILD.md` is a human step |

---

## [2026-08-11] Helmet Emblem Artwork (Incident 11)

**Codebase Version:** v2.8-helmet-artwork  
**Trigger:** Incident 11 fix — hand-authored vector emblem replaced with a measured raster asset

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| system.md | ✅ Updated | +18 lines (brand artifact locations) |
| components.md | ✅ Updated | +84 lines (1 new component, 2 new failure modes on Home Brand Hero) |
| flows.md | ✅ Updated | +7 lines (coverage-gap callout on the L2 startup table) |
| failure-patterns.md | ✅ Updated | +95 lines (1 new pattern — first static-asset pattern) |
| SRS_v1.md | ✅ Updated | +2 requirements (UI-08, UI-09) |
| TRACEABILITY_MATRIX.md | ✅ Updated | +2 rows, UI category 7 → 9, TOTAL ~44 → ~46 |
| L1_SWE4_unit/README.md | ✅ Updated | +28 lines (brand gate section, test layout) |
| test_strategy_execution_instructions.md | ✅ Updated | +30 lines (§3.5 brand gate, new common issue) |
| L4_SYS5_acceptance/20_ONBOARDING_TESTS.md | ✅ Updated | +28 lines (new BRD suite, 1 test) |
| USER_MANUAL.md | ⏭️ No change | Emblem is described generically; no workflow or UI behaviour changed |

### New Requirement IDs
- **UI-08** — emblem supplied as one resource across all densities, undistorted at 132/88/36dp
- **UI-09** — brand artwork verified by measurement, not presence-only assertions

### New Test Case IDs
- **BRD-01** — Helmet Emblem Renders Correctly at All Sizes (L4, human visual)

### Detailed Changes

#### 📁 atlas/components.md

**Added Section: Brand Asset Pipeline (Helmet Emblem)** — render sites, density
buckets, six invariants with thresholds, six failure modes with their detecting
test, and signals.

**Modified: Home Brand Hero → Failure Modes**
```diff
+ | Emblem artwork deformed | Brand asset geometry defect | Emblem renders squashed at all three sizes; `isDisplayed()` tests still pass |
+ | Emblem clipped by ring | Content bbox exceeds the `bg_hero_ring` radius | Artwork edges cut off inside the navy disc |
```

**Modified: Criticality Matrix**
```diff
+ | Brand Asset Pipeline | LOW | Deformed or missing emblem on all branded surfaces |
```

#### 📁 atlas/failure-patterns.md

**Added Pattern: Brand Asset Geometry (Unverified Static Artwork)** — the first
static-asset pattern in the Atlas. Its defining signal is that there is **no
signal**: static assets ship broken and never throw.

Records the generalisation that cost two incidents:
```
An assertion that cannot fail when the defect is present is not coverage.
```

#### 📁 atlas/system.md

**Added:** brand artwork layout block — the five WebP buckets, the deliberate
absence of `drawable/ic_helmet_emblem.xml`, the L1 gate package, test fixtures,
source art, and the build script.

#### 📁 01_requirements/DrivingCoach_SRS_v1.md

| ID | Requirement |
|----|-------------|
| UI-08 | The helmet emblem shall be supplied as a single `@drawable/ic_helmet_emblem` resource across all density buckets, and shall render undistorted and uncropped at 132dp, 88dp and 36dp |
| UI-09 | Brand artwork shall be verified by measurement of the asset itself — square canvas, aspect 1.00 ± 0.05, centred within 3 %, transparent border — rather than by presence-only assertions |

#### 📁 05_tests/

**L1_SWE4_unit/README.md** — added a *Brand Asset Gate* section documenting all
four tests and, specifically, why the falsification test exists:

```
A gate that has never been observed to fail is indistinguishable from a no-op.
```

**test_strategy_execution_instructions.md** — added §3.5 with the targeted run
command, the venv setup for `brand-asset.py`, and how to interpret the
falsification failure message. Added a common-issue row for
`Unresolved reference 'BufferedImage'`, since `android.jar` provides neither
`java.awt` nor `javax.imageio` — the reason `ArgbBitmap` exists.

**L4_SYS5_acceptance/20_ONBOARDING_TESTS.md** — added a new **BRD** suite with
`BRD-01`, a 7-step human visual check across all three emblem sizes plus edge
quality and cross-density crispness. A human check is warranted precisely
because the automated L2 suite could not see this defect.

#### 📁 03_incidents/11_helmet_emblem_deformed/

- Incident status `Open — Awaiting RCA` → `Resolved`; **acceptance criterion 5
  removed** at the project owner's request (artwork is their own work).
- Added a Resolution section with per-criterion verification and validation results.
- Added an RCA addendum recording that the fix **diverged** from the RCA's own
  recommendation, and that the proposed bilateral-symmetry constraint was
  deliberately dropped — the supplied artwork is a side profile, and the RCA had
  already measured asymmetry as *not* being the defect.

### Code Changes Documented

| Change | Detail |
|--------|--------|
| Removed | `res/drawable/ic_helmet_emblem.xml` (9 hand-authored paths, −56 lines) |
| Added | 5 lossless WebP density buckets (389 KB total) |
| Added | `BrandAssetGeometryTest` + `ArgbBitmap` (4 tests, incl. a falsification test) |
| Added | `05_tests/infra/scripts/brand-asset.py` (build + check) |
| Added | `docs/brand/helmet_source.png` |

### Validation

| Level | Result |
|-------|--------|
| Build | `assembleDebug` BUILD SUCCESSFUL |
| L1 | 109 tests, 0 failures, 0 skipped (was 105) |
| L2 | 26 tests, 0 failures, 4 skipped (pre-existing `@Ignore`) |
| On-device | Pixel 4 / API 30 — splash + Home hero verified; ring 318px vs emblem 226px, no clipping |
| Reproducibility | `brand-asset.py build` reproduces all 5 buckets bit-for-bit |

### Files Modified

```
M  atlas/system.md                                              (+18, -0)
M  atlas/components.md                                          (+84, -0)
M  atlas/flows.md                                               (+7, -0)
M  atlas/failure-patterns.md                                    (+95, -0)
M  01_requirements/DrivingCoach_SRS_v1.md                       (+2, -0)
M  01_requirements/TRACEABILITY_MATRIX.md                       (+5, -3)
M  05_tests/L1_SWE4_unit/README.md                              (+28, -0)
M  05_tests/test_strategy_execution_instructions.md             (+30, -0)
M  05_tests/L4_SYS5_acceptance/20_ONBOARDING_TESTS.md           (+28, -0)
M  03_incidents/11_helmet_emblem_deformed/11_helmet_emblem_deformed.md   (+146, -0)
M  03_incidents/11_helmet_emblem_deformed/11_RCA_unconstrained_hand_authored_vector.md (+358, -0)
A  05_tests/infra/scripts/brand-asset.py                        (+259)
A  app/src/test/java/com/drivingcoach/brand/BrandAssetGeometryTest.kt (+170)
A  app/src/test/java/com/drivingcoach/brand/ArgbBitmap.kt       (+157)
A  app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_helmet_emblem.webp
A  app/src/test/resources/brand/{ic_helmet_emblem_master,legacy_deformed_emblem}.png
A  docs/brand/helmet_source.png
D  app/src/main/res/drawable/ic_helmet_emblem.xml               (-56)
```

### Recommendations

- [ ] **No CI exists** (`.github/workflows/` absent) — the new L1 gate only runs manually
- [ ] Launcher icon still uses separate `ic_launcher_foreground.png` artwork — brand inconsistency
- [ ] `versionName` remains `1.0.0`; versioning lives only in `releases/` filenames
- [ ] 4 legacy L2 classes remain `@Ignore`d

---

## [2026-08-06] L2 Integration Coverage for Branded Startup (SWE.5)

**Trigger:** SRS UI-01…UI-06 were nominally covered, but the L2 suite was a *false green* —
all four existing instrumented classes carry a class-level `@Ignore`, and Gradle reports
`BUILD SUCCESSFUL` for a fully skipped suite. UI-05 was manual-only and UI-06 had no
coverage at all.

**Outcome:** 5 new instrumented test classes (18 executing tests, 0 failures, 0 skipped),
enabled by a Hilt module split. Documentation realigned to reflect real, executing coverage.

### Summary

| Area | Change |
|------|--------|
| Production | `AppModule` split into `DataStoreModule`, `DispatcherModule`, `SplashModule` |
| Tests | +5 L2 classes (18 tests), +3 shared test fixtures |
| Requirements | UI-01…UI-06 promoted to L2; UI-05 ⚠️→✅, UI-06 ❌→✅ |
| Atlas | Test hooks, L2 verification table, strengthened regression guard |

### Key Finding — Espresso Cannot Detect a Blocked Main Thread

The first UI-04 design asserted "main thread is not blocked" by performing an Espresso click
during a stalled startup. To validate it, the original defect was deliberately reintroduced:

```kotlin
// SplashFragment.onViewCreated — TEMPORARY, for falsification only
runBlocking { delay(60_000) }
```

The Espresso-based test **still passed**. Espresso synchronises *with* the main looper — it
waits for idle rather than timing out — so blocking the main thread only makes it slower.

The replacement detector samples main-looper round-trip latency from a background thread:

```kotlin
// MainThreadResponsivenessProbe.kt
private const val SAMPLE_INTERVAL_MS = 50L
private const val PER_SAMPLE_TIMEOUT_MS = 15_000L
// posts a Runnable to Handler(Looper.getMainLooper()), records worstLatencyMs
```

Re-running the falsification with the probe in place produced:

```
main thread was unresponsive for 15000ms during startup (budget 2000ms)
```

The injected block was then fully reverted (`SplashFragment.kt` verified clean).
**Rule adopted:** any "must not block" assertion is untrusted until it has been falsified.

### Files Changed

```
 M app/src/main/java/com/drivingcoach/di/AppModule.kt                (-28 lines, now context only)
 A app/src/main/java/com/drivingcoach/di/DataStoreModule.kt
 A app/src/main/java/com/drivingcoach/di/DispatcherModule.kt
 A app/src/main/java/com/drivingcoach/di/SplashModule.kt
 A app/src/androidTest/java/com/drivingcoach/testing/FakePreferencesDataStores.kt
 A app/src/androidTest/java/com/drivingcoach/testing/NavigationTestExtensions.kt
 A app/src/androidTest/java/com/drivingcoach/testing/MainThreadResponsivenessProbe.kt
 A app/src/androidTest/java/com/drivingcoach/ui/splash/SplashScreenTest.kt        (6 tests, UI-01/02)
 A app/src/androidTest/java/com/drivingcoach/ui/splash/SplashFallbackTest.kt      (3 tests, UI-03)
 A app/src/androidTest/java/com/drivingcoach/ui/splash/SplashMainThreadTest.kt    (3 tests, UI-04)
 A app/src/androidTest/java/com/drivingcoach/StartupBackStackTest.kt              (2 tests, UI-05)
 A app/src/androidTest/java/com/drivingcoach/ui/home/HomeHeroTest.kt              (4 tests, UI-06)
 M 01_requirements/TRACEABILITY_MATRIX.md
 M atlas/system.md
 M atlas/components.md
 M atlas/flows.md
 M atlas/failure-patterns.md
 M 05_tests/L2_SWE5_integration/README.md
 M docs/SYNC_REPORT.md
```

### Documentation Updates

**`01_requirements/TRACEABILITY_MATRIX.md`**
- UI-01…UI-04: level `L1` → `L1 + L2`, test column now names the L2 class.
- UI-05: `Manual` / ⚠️ *No automated test* → `L2` / `StartupBackStackTest` / ✅.
- UI-06: ❌ *Not covered* → ✅ `HomeHeroTest`.
- Startup & Branding category: **71% ⚠️ → 100% ✅**; TOTAL **~42 → ~44 (~24%)**.
- New "Coverage caveat — `@Ignore`d L2 classes" callout above the matrix, naming the eight
  rows whose ✅ is backed by a skipped class and giving the XML-parsing verification command.

**`atlas/system.md`**
- Repository-layout `di/` entry now enumerates the split modules and states *why*
  (per-binding `@UninstallModules` in tests).

**`atlas/components.md`**
- Preferences DataStore component: key code area `di/AppModule.kt` → `di/DataStoreModule.kt`.
- Branded Startup component: new **Test Hooks** section — a goal→uninstall→substitute table
  plus the "do not use Espresso for UI-04" warning.
- New observable signal row: `MainThreadResponsivenessProbe.worstLatencyMs`.

**`atlas/flows.md`**
- Timing-contract table: `SplashTimings` now injected via `SplashModule` (was `AppModule`).
- New **Automated Verification (L2)** section mapping each stage of the startup flow to its
  instrumented test and SRS ID.

**`atlas/failure-patterns.md`**
- *DataStore ANR on Startup* → **Regression Guard** rewritten: L1 + L2 split, the 2000 ms
  latency budget, and the Espresso falsification evidence.
- Confidence rationale extended to "empirically falsified".

**`05_tests/L2_SWE5_integration/README.md`**
- Test-location tree updated with all new files and `@Ignore` markers on legacy classes.
- New **"`BUILD SUCCESSFUL` Is Not Evidence"** section with the JUnit-XML parsing snippet.
- New **Test Isolation Strategy** section explaining the module split and why the fallback
  and main-thread tests must live in separate classes.

**`docs/USER_MANUAL.md`** — *no change required.* This work added test coverage and
refactored DI only; no user-visible behaviour changed.

### Validation

| Level | Result |
|-------|--------|
| `assembleDebug` / `compileDebugKotlin` | ✅ PASS |
| `compileDebugAndroidTestKotlin` | ✅ PASS |
| L1 (SWE.4 unit) | ✅ **105 tests, 0 failures, 0 skipped** |
| L2 new (SWE.5) | ✅ **18 tests, 0 failures, 0 skipped** |
| L2 legacy | ⚠️ 4 classes still `@Ignore`d — out of scope, tracked separately |
| UI-04 detector falsification | ✅ Fails when the defect is reintroduced |

### Known Gaps / Follow-ups

- The 4 legacy `@Ignore`d L2 classes remain decorative; un-ignoring them is separate work.
- `MAX_ACCEPTABLE_LATENCY_MS = 2_000` is emulator-derived and may need tuning on slower CI.
- The three `Log.d` startup-timing statements are unconditional; consider gating behind
  `BuildConfig.DEBUG`.

---

## [2026-07-25] Startup Hardening — Device Verification & Timeout Redesign

**Codebase Version:** v2.8.1-brand-startup-fixes
**Trigger:** Attempt to execute L2 (SWE.5 integration) tests for the v2.8 branded startup work

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| SplashTimings.kt | ✅ Updated | Essential timeout 3000→8000 ms; new `warmUpTimeoutMs = 2000` |
| SplashViewModel.kt | ✅ Updated | Essential/optional split, ONBOARDING fallback, permanent timing logs |
| fragment_splash.xml | ✅ Updated | `fitsSystemWindows`; tagline↔manifesto constraint chain |
| themes.xml + ic_splash_emblem.xml | ✅ Added/Updated | System-splash icon no longer mask-cropped |
| strings.xml | ✅ Updated | Footer metrics refreshed (Tests 104, SRS 123) |
| SplashViewModelTest.kt | ✅ Updated | 13 tests; new warm-up isolation regression test |
| DrivingCoach_SRS_v1.md | ✅ Updated | UI-03 reworded; **new UI-07** (bounded non-essential warm-up) |
| TRACEABILITY_MATRIX.md | ✅ Updated | UI-05 corrected to manual-only; UI-07 added |
| flows.md | ✅ Updated | Split timeout contract, fallback rationale, logcat signals |
| components.md | ✅ Updated | Failure modes split, measured signals added |
| failure-patterns.md | ✅ Updated | Post-mitigation table + measured evidence table |
| USER_MANUAL.md | ✅ Updated | §2.2 "3 seconds" → 8 s ceiling + Onboarding fallback |

### Key Finding — L2 Is a False Green

`./gradlew connectedDebugAndroidTest` reports `BUILD SUCCESSFUL`, but the results XML shows
`tests=4 failures=0 skipped=4`. **All four L2 classes are class-level `@Ignore`d**
(`EndToEndTest`, `TelemetryForegroundServiceTest`, `RecordingFragmentTest`,
`TrackSetupFragmentTest`) for pre-existing reasons unrelated to this work. L2 therefore
provides **zero** real coverage today. Always parse the results XML, never trust the exit code.

Consequence: the earlier claim that UI-05 was "⚠️ Partial via EndToEndTest" was wrong and has
been corrected in the traceability matrix.

### Defects Found by Direct Device Verification

Because L2 gave no signal, verification was performed directly on `emulator-5554`.

| # | Defect | Root cause | Fix |
|---|--------|-----------|-----|
| 1 | Fresh install landed on **Login**, not Onboarding | 3 s global timeout fired on a *normal* cold start (`DataStore.data.first()` = 3 573 ms); fallback became the common path | Essential timeout → 8 s; warm-up split behind its own 2 s bound; fallback → ONBOARDING |
| 2 | Splash footer clipped by navigation bar | `MainActivity` applies only left/right insets by design; fragment must opt in | `fitsSystemWindows="true"` on `splashRoot` |
| 3 | Stale footer metrics string | Not refreshed after test/SRS growth | Updated to Tests 104 / SRS 123 |
| 4 | Android 12+ system splash icon hard-cropped | Adaptive-icon circular mask vs. full-bleed `ic_launcher_foreground` | New `ic_splash_emblem.xml` (`<inset>` 20%) |
| 5 | Tagline colliding with manifesto card | No constraint linking the two views | `Top_toBottomOf` + margins + `verticalBias=1.0` |

### Design Rationale — Why ONBOARDING, Not LOGIN

If the preferences read fails we do not know whether the user has onboarded. The costs are
asymmetric: routing an **already-onboarded** user through Onboarding is a recoverable
annoyance that still ends at Home; routing a **fresh** user to Login skips permission granting
entirely and leaves the app unable to record. ONBOARDING is therefore the strictly safer default.

### Design Rationale — Essential vs. Optional Work

The destination decision depends **only** on the preferences read. The Room warm-up is a pure
optimisation, so it now carries its own 2 s bound and is wrapped in `runCatching`: a slow or
broken database can never change where the user lands. Guarded by the L1 test
`slow database warm up does not change the destination`.

### Validation

| Level | Result |
|-------|--------|
| `compileDebugKotlin` / `compileDebugAndroidTestKotlin` / `assembleDebug` | ✅ PASS |
| L1 (SWE.4 unit) | ✅ **105 tests, 0 failures, 0 skipped** (clean run, emulator stopped) |
| L1 splash subset | ✅ 13 tests, 0 failures |
| L2 (SWE.5 integration) | ⚠️ Runs, but 4/4 classes `@Ignore`d — **no coverage** |
| Device verification (`emulator-5554`) | ✅ Splash renders correctly; destination = Onboarding on fresh install; footer clear of nav bar; Home hero matches mockup; **UI-05 confirmed** (Back from Home exits to launcher) |

Measured startup (Pixel 4 API 30 emulator): cold `datastore read 3 573 ms` →
`resolved in 4 458 ms`; warm `datastore read 1 878 ms`, `room warm-up 955 ms` →
`resolved=ONBOARDING in 2 889 ms`.

Note: `TelemetryFileWriterTest > benchmark 18000 samples writes in under 100ms` is
**load-sensitive**, not a regression — it fails only under CPU contention from a running
emulator and passes on a clean run.

### Open Items

- Four `@Ignore`d L2 classes make the entire L2 level decorative — recommend a follow-up to
  un-ignore at minimum `EndToEndTest`, which would give real automated UI-05 coverage.
- The 8 s essential budget is derived from emulator measurements; real-device figures are
  unknown and it may be tunable downward.
- The three `Log.d` startup timing statements are currently unconditional; decide whether to
  gate them behind `BuildConfig.DEBUG`.

---

## [2026-07-24] Branded Loading Screen & Home Brand Hero

**Codebase Version:** v2.8-brand-startup  
**Trigger:** TRILLIAN brand mockup implementation — splash loading screen + collapsing Home hero

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| system.md | ✅ Updated | +6/-3 (splash module, SplashScreen dep, ANR risk closed) |
| components.md | ✅ Updated | +141 lines (2 new components) |
| flows.md | ✅ Updated | +74/-16 (startup flow fully rewritten) |
| failure-patterns.md | ✅ Updated | +80/-45 (DataStore ANR marked MITIGATED) |
| DrivingCoach_SRS_v1.md | ✅ Updated | ON-01 reworded, +6 requirements (§3a) |
| TRACEABILITY_MATRIX.md | ✅ Updated | +6 rows, new UI category (83% covered) |
| USER_MANUAL.md | ✅ Updated | §2.2 new, §4.0 new, §2.3–2.5 renumbered |

### New Requirement IDs

- **UI-01** — Branded loading screen with progress tied to real initialisation work
- **UI-02** — Minimum display time (1.2 s) with tap-to-skip
- **UI-03** — 3 s initialisation timeout with Login fallback
- **UI-04** — Startup state resolved off the main thread
- **UI-05** — Loading screen removed from the back stack
- **UI-06** — Home brand hero collapses on scroll

**Modified:** ON-01 (reworded — start destination is now resolved asynchronously)

### Architectural Change

The `runBlocking` DataStore read at `MainActivity:68` — a **P1 documented ANR risk** in
`failure-patterns.md` — has been removed. Startup state now resolves in `SplashViewModel`
on an injected `@IoDispatcher`, bounded by `withTimeoutOrNull(3000)` with a Login fallback.
The pattern is now marked **✅ MITIGATED** and downgraded from P1 to Closed.

### Detailed Changes

#### 📁 atlas/system.md

```diff
  ├── recording/
- │   └── session/
+ │   ├── session/
+ │   └── splash/               # Branded loading screen (startup resolution)

+ | Splash | AndroidX Core SplashScreen | 1.0.1 | `installSplashScreen()`, Android 12+ handoff |

- | **Blocking DataStore read** | MEDIUM | `runBlocking` in `MainActivity.setupNavigation()` | Move to suspending |
+ | ~~**Blocking DataStore read**~~ | ✅ RESOLVED v2.8 | Was `runBlocking` … | Replaced by async `SplashViewModel` |
```

#### 📁 atlas/components.md

**Added Section: App Startup / Branded Loading Screen** (criticality HIGH) — key code areas,
dependencies, the 5-stage progress model, failure modes, observable signals.

**Added Section: Home Brand Hero (Collapsing Toolbar)** (criticality LOW) — layout structure,
scroll/alpha behaviour table, failure modes.

#### 📁 atlas/flows.md

**Rewrote: Flow: App Startup & Navigation Resolution**

```diff
- → DataStore.data.first() [BLOCKING runBlocking]
- → NavController.setStartDestination()
+ → installSplashScreen()
+ → NavController starts at splashFragment
+ → SplashViewModel.start()
+   → withTimeoutOrNull(3000) { withContext(IO) { … } }
+   → awaitMinimumDisplay(1200)  [skippable]
+ → navigate(popUpTo splashFragment inclusive)
```

Added a **Timing Contract** table and rewrote Async Boundaries, Failure Points,
Retry/Recovery and Operational Signals.

#### 📁 atlas/failure-patterns.md

```diff
- ## Pattern: DataStore ANR on Startup
+ ## Pattern: DataStore ANR on Startup — ✅ MITIGATED (v2.8)

- | DataStore ANR on Startup | MEDIUM | HIGH | HIGH | P1 |
+ | DataStore ANR on Startup | ~~MEDIUM~~ MITIGATED | HIGH | HIGH | ~~P1~~ Closed (v2.8) |
```

Added *Regression Signals*, *Current Behaviour (post-mitigation)*, a Permanent Fix status
table, and a *Regression Guard* pointing at `SplashViewModelTest`.

#### 📁 01_requirements/DrivingCoach_SRS_v1.md

**Added Requirements (new §3a — Application startup and branding):**

| ID | Requirement |
|----|-------------|
| UI-01 | The app SHALL display a branded loading screen on cold start with a progress indicator reflecting real initialisation work |
| UI-02 | The loading screen SHALL remain visible for a minimum of 1.2 s and SHALL be dismissible early by tapping |
| UI-03 | Startup initialisation SHALL be bounded by a 3 s timeout, after which the app SHALL navigate to Login |
| UI-04 | Startup state resolution SHALL NOT block the main thread |
| UI-05 | The loading screen SHALL be removed from the back stack on navigation |
| UI-06 | The Home screen SHALL present a brand hero that collapses as the user scrolls |

#### 📁 01_requirements/TRACEABILITY_MATRIX.md

```diff
+ | Startup & Branding (UI) | 6 | 5 | 83% ✅ |
- | **TOTAL** | **~179** | **~37** | **~21%** |
+ | **TOTAL** | **~185** | **~42** | **~23%** |
```

#### 📁 docs/USER_MANUAL.md

**Added Section 2.2 — The Loading Screen**: explains the progress bar reflects real work, the
step-by-step table, the ~1 s typical / 3 s maximum wait, tap-to-skip, and Back-exits-app.

**Added Section 4.0 — The Home Screen**: describes the collapsing brand hero, the persistent
profile button, and the content below it.

**Renumbered:** 2.2→2.3 (First Launch & Permissions), 2.3→2.4 (Creating Your Account),
2.4→2.5 (Logging In).

### Validation

| Level | ASPICE | Result |
|-------|--------|--------|
| L1 unit | SWE.4 | ✅ 104 tests, 0 failures (12 new in `SplashViewModelTest`) |
| Compile (main) | — | ✅ `compileDebugKotlin` |
| Compile (androidTest) | — | ✅ `compileDebugAndroidTestKotlin` |
| L2 instrumented | SWE.5 | ⏸️ Not run (requires emulator/KVM) |
| Visual verification | — | ⏸️ Not performed |

### Files Modified

```
M  atlas/system.md                                   (+6, -3)
M  atlas/components.md                               (+141, -0)
M  atlas/flows.md                                    (+74, -16)
M  atlas/failure-patterns.md                         (+80, -45)
M  01_requirements/DrivingCoach_SRS_v1.md            (+16, -2)
M  01_requirements/TRACEABILITY_MATRIX.md            (+12, -3)
M  docs/USER_MANUAL.md                               (+49, -5)
M  app/build.gradle.kts                              (+1)
M  app/src/main/AndroidManifest.xml                  (+1, -1)
M  app/src/main/java/com/drivingcoach/di/AppModule.kt (+11)
M  app/src/main/java/com/drivingcoach/ui/MainActivity.kt (+3, -23)
M  app/src/main/java/com/drivingcoach/ui/home/HomeFragment.kt (+28, -4)
M  app/src/main/res/layout/fragment_home.xml         (+150, -41)
M  app/src/main/res/navigation/nav_graph.xml         (+26, -2)
M  app/src/main/res/values/{colors,dimens,strings,themes,type}.xml
M  app/src/androidTest/java/com/drivingcoach/EndToEndTest.kt (+2, -2)
A  app/src/main/java/com/drivingcoach/ui/splash/SplashViewModel.kt
A  app/src/main/java/com/drivingcoach/ui/splash/SplashFragment.kt
A  app/src/main/java/com/drivingcoach/ui/splash/SplashDestination.kt
A  app/src/main/java/com/drivingcoach/ui/splash/SplashTimings.kt
A  app/src/main/java/com/drivingcoach/di/IoDispatcher.kt
A  app/src/main/res/layout/fragment_splash.xml
A  app/src/main/res/drawable/{ic_helmet_emblem,bg_hero_ring,ic_dot}.xml
A  app/src/test/java/com/drivingcoach/ui/splash/SplashViewModelTest.kt
```

### Recommendations

- [ ] **Visual check needed** — `ic_helmet_emblem.xml` was hand-authored and has never been
      rendered; the emblem may need tuning
- [ ] **L2 run needed** — collapsing hero animation and splash→destination routing unverified
      on a device (requires emulator/KVM)
- [ ] Add L2 coverage for UI-06 (hero collapse) — currently uncovered
- [ ] DataStore corruption recovery remains open (falls back to empty preferences)
- [ ] Pre-existing: SRS AD-04 mandates Firebase Auth but the code uses JWT-in-DataStore —
      still unreconciled

---

## [2026-07-22] Test Documentation Sync to Agent Instructions

**Codebase Version:** v2.7-aspice-tests  
**Trigger:** Update agent instructions with ASPICE test infrastructure references

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| trillian-docs-sync.md | ✅ Updated | Path references to `05_tests/L4_SYS5_acceptance/` |
| SW_dev_agent.md | ✅ Updated | Enhanced Step 7 Validation with ASPICE details |
| RCA_agent.md | ✅ Updated | Added Step 8 Test-Based Verification |
| system.md (Atlas) | ✅ Updated | Added emulator configuration details |

### Detailed Changes

**trillian-docs-sync.md:**
- Updated acceptance test path: `human_system_acceptance_tests/` → `05_tests/L4_SYS5_acceptance/`
- Added full test infrastructure section with ASPICE levels (L1-L4)
- Updated file locations reference tree

**SW_dev_agent.md (Step 7 — Validation):**
- Added ASPICE test level reference table
- Added infrastructure script commands
- Added emulator management instructions
- Added KVM requirement documentation
- Added test report verification workflow
- Added minimum validation requirements matrix

**RCA_agent.md:**
- Added new Step 8: Test-Based Verification (optional)
- Added when-to-use decision table
- Added test verification output format

**system.md (Atlas):**
- Added emulator configuration block (API 30, Pixel 4, AVD name)
- Added KVM access requirement note

### Files Modified

```
M  .github/skills/trillian-docs-sync.md     (+18, -8)
M  .github/agents/SW_dev_agent.md           (+65, -25)
M  .github/agents/RCA_agent.md              (+35, -0)
M  atlas/system.md                          (+5, -0)
```

---

## [2026-07-22] ASPICE Test Infrastructure

**Codebase Version:** v2.7-aspice-tests  
**Trigger:** User request for ASPICE-aligned test structure with emulator automation

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| 05_tests/ structure | ✅ Reorganized | ASPICE-aligned level folders (L1-L4) |
| infra/scripts/*.sh | ✅ Created | 6 automation scripts |
| Test_Strategy.md | ✅ Updated | ASPICE alignment section added |
| test_strategy_execution_instructions.md | ✅ Updated | Full rewrite with ASPICE naming |
| system.md (Atlas) | ✅ Updated | Testing Structure section updated |
| reports/README.md | ✅ Updated | Single-file report format |

### New Structure

```
05_tests/
├── L1_SWE4_unit/           # SWE.4 — Unit Verification
├── L2_SWE5_integration/    # SWE.5 — Integration Test
├── L3_SWE6_qualification/  # SWE.6 — SW Qualification Test
├── L4_SYS5_acceptance/     # SYS.5 — System Qualification Test
├── data/                   # Test data files
├── infra/
│   ├── scripts/
│   │   ├── setup-emulator.sh
│   │   ├── start-emulator.sh
│   │   ├── stop-emulator.sh
│   │   ├── run-instrumented.sh
│   │   ├── run-all-tests.sh
│   │   └── generate-report.sh
│   └── config/
│       └── avd-config.ini
└── reports/
    └── TEST_REPORT_*.md    # Single-file consolidated reports
```

### Scripts Created

| Script | Purpose |
|--------|---------|
| `setup-emulator.sh` | Install emulator, system image (API 30), create AVD |
| `start-emulator.sh` | Start headless emulator, wait for boot |
| `stop-emulator.sh` | Graceful emulator shutdown |
| `run-instrumented.sh` | Run L2 tests with optional emulator auto-start |
| `run-all-tests.sh` | Master orchestrator (L1 + L2 + report) |
| `generate-report.sh` | Generate consolidated markdown report |

### Report Format

Single file per execution: `TEST_REPORT_YYYY-MM-DD_HH-MM-SS.md`

Contains:
- ASPICE-aligned summary table
- Results per level (or "NOT EXECUTED")
- Failed test details
- Environment information

### ASPICE Traceability

| Folder | ASPICE | V-Model Alignment |
|--------|--------|-------------------|
| L1_SWE4_unit | SWE.4 | Implementation → Unit Verification |
| L2_SWE5_integration | SWE.5 | Design → Integration Test |
| L3_SWE6_qualification | SWE.6 | Architecture → SW Qualification |
| L4_SYS5_acceptance | SYS.5 | Requirements → System Qualification |

---

## [2026-07-22] Smooth Timer Display (100ms UI Updates)

**Codebase Version:** v2.6-smooth-timer  
**Trigger:** User feedback — recording timer display jumps inconsistently

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| TelemetryForegroundService.kt | ✅ Updated | +100ms UI state runnable, separated from notification |
| TelemetryForegroundServiceTest.kt | ✅ Updated | +1 test (elapsedMsUpdatesAtHighFrequency) |
| EndToEndTest.kt | ✅ Fixed | Pre-existing compile errors resolved |
| components.md | ✅ Updated | +Runtime Intervals section |

### Changes Made

#### TelemetryForegroundService.kt
```diff
+ private const val UI_STATE_UPDATE_INTERVAL_MS = 100L // 10 Hz for smooth timer display

+ // Fast UI state updates (100ms) for smooth timer display
+ private val uiStateUpdateRunnable = object : Runnable {
+     override fun run() {
+         updateElapsedTime()
+         handler.postDelayed(this, UI_STATE_UPDATE_INTERVAL_MS)
+     }
+ }

+ // Slower notification updates (1000ms) to save battery
  private val notificationUpdateRunnable = object : Runnable {
      override fun run() {
          updateNotification()
-         updateElapsedTime()
          checkGpsSignalLost()
          handler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL_MS)
      }
  }
```

#### components.md
```diff
+ ### Runtime Intervals
+ 
+ | Runnable | Interval | Purpose |
+ |----------|----------|---------|
+ | `uiStateUpdateRunnable` | 100ms (10 Hz) | Smooth timer display in RecordingFragment |
+ | `notificationUpdateRunnable` | 1000ms (1 Hz) | Notification bar + GPS signal check |
+ | `gpsLockTimeoutRunnable` | 5000ms (once) | GPS lock timeout detection |
+ | `periodicFlushRunnable` | 30000ms | Telemetry file flush for crash resilience |
+ 
+ **Design Note**: UI state updates are separated from notification updates...
```

### New Tests

| ID | Test | Purpose |
|----|------|---------|
| elapsedMsUpdatesAtHighFrequency | Verify ≥3 distinct elapsed samples in 500ms | Confirms 10Hz update rate |

### Bug Fixes

- **EndToEndTest.kt**: Fixed `rawFilePath = null` → `rawFilePath = ""` (non-null field)
- **EndToEndTest.kt**: Fixed `ProcessingStatus.NOT_STARTED` → `ProcessingStatus.PENDING`

### Files Modified

```
M  app/src/main/java/.../service/TelemetryForegroundService.kt  (+17, -2)
M  app/src/androidTest/java/.../service/TelemetryForegroundServiceTest.kt  (+53)
M  app/src/androidTest/java/com/drivingcoach/EndToEndTest.kt  (+2, -2)
M  atlas/components.md           (+12)
```

### Design Rationale

- **Why 100ms?** Matches GPS capture rate (10 Hz), provides visually smooth millisecond counter
- **Why separate runnables?** Notification system calls are heavier; 1Hz is sufficient for notification bar
- **Performance impact:** Negligible — TextView.setText() is sub-millisecond

---

## [2026-07-22] Crash Resilience Improvements

**Codebase Version:** v2.5-crash-resilience  
**Trigger:** RCA finding — system crash during emulator recording (incident #10)

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| TelemetryFileWriter.kt | ✅ Updated | +1 method (flush) |
| TelemetryForegroundService.kt | ✅ Updated | +periodic flush runnable (30s) |
| DrivingCoachApp.kt | ✅ Updated | +DeadSystemException handler |
| TelemetryFileWriterIntegrationTest.kt | ✅ Updated | +2 flush tests |
| components.md | ✅ Updated | +Data Persistence section |
| failure-patterns.md | ✅ Updated | +DeadSystemException pattern |

### Changes Made

#### TelemetryFileWriter.kt
```kotlin
+ /**
+  * Flushes buffered data to disk without closing the writer.
+  * Call periodically to minimize data loss on unexpected termination.
+  */
+ suspend fun flush() = withContext(Dispatchers.IO) { ... }
```

#### TelemetryForegroundService.kt
```kotlin
+ private const val TELEMETRY_FLUSH_INTERVAL_MS = 30000L // 30s periodic flush
+ 
+ private val periodicFlushRunnable = object : Runnable {
+     override fun run() {
+         serviceScope.launch(Dispatchers.IO) { telemetryWriter?.flush() }
+         handler.postDelayed(this, TELEMETRY_FLUSH_INTERVAL_MS)
+     }
+ }
```

#### DrivingCoachApp.kt
```kotlin
+ private fun setupUncaughtExceptionHandler() {
+     Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
+         if (isSystemDeathException(throwable)) {
+             Log.e(TAG, "System crash detected (DeadSystemException)...")
+         } else {
+             defaultHandler?.uncaughtException(thread, throwable)
+         }
+     }
+ }
```

### New Tests

| Test | Purpose |
|------|---------|
| `periodicFlushPersistsDataBeforeClose` | Verifies flush persists data before close |
| `unflushedDataMayBeLostOnCrash` | Demonstrates why periodic flush matters |

### Atlas Updates

**components.md** — Added Data Persistence section to Telemetry Recording Service:
```markdown
+ ### Data Persistence
+ | Mechanism | Interval | Purpose |
+ |-----------|----------|---------|
+ | Sample write | On GPS fix (~100ms) | Append to buffer |
+ | Periodic flush | 30 seconds | Persist buffered data |
+ | Close flush | On stop | Final flush |
```

**failure-patterns.md** — Added DeadSystemException pattern:
- Symptoms, signals, causes, mitigation
- Links to RCA `10_RCA_emulator_system_server_crash.md`
- Documents app-level handling (graceful termination)

### Files Modified

```
M  app/src/main/java/.../data/telemetry/TelemetryFileWriter.kt     (+15)
M  app/src/main/java/.../service/TelemetryForegroundService.kt     (+14)
M  app/src/main/java/.../DrivingCoachApp.kt                        (+45)
M  app/src/test/java/.../TelemetryFileWriterIntegrationTest.kt     (+75)
M  atlas/components.md                                              (+12)
M  atlas/failure-patterns.md                                        (+85)
```

### Related Incident

- `03_incidents/10_emulator_system_crash/10_RCA_emulator_system_server_crash.md`

---

## [2026-07-20] Session Management (Delete & Rename)

**Codebase Version:** v2.4-session-management  
**Trigger:** New feature — long-press context menu for session delete and rename

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| SessionDao.kt | ✅ Updated | +2 DAO methods (deleteById, updateTrackName) |
| HomeViewModel.kt | ✅ Updated | +2 methods, +2 events |
| HomeFragment.kt | ✅ Updated | +context menu, +dialogs |
| HomeViewModelTest.kt | ✅ Created | 6 new unit tests |
| components.md | ✅ Updated | +Session Management tables |
| SRS_v1.md | ✅ Updated | +10 requirements (SM-01 to SM-10) |
| USER_MANUAL.md | ✅ Updated | +Section 5.4 Managing Sessions |
| build.gradle.kts | ✅ Updated | +testOptions.returnDefaultValues |

### New Requirement IDs

| ID | Description |
|----|-------------|
| SM-01 | Delete via long-press context menu |
| SM-02 | CASCADE delete (session + laps + insights) |
| SM-03 | Delete telemetry JSONL file |
| SM-04 | Delete confirmation dialog |
| SM-05 | Rename via long-press context menu |
| SM-06 | Rename dialog pre-fills current name |
| SM-07 | Track name validation (1-100 chars) |
| SM-08 | Local-only operations |
| SM-09 | Delete success Snackbar |
| SM-10 | Rename success Snackbar |

### New DAO Methods

```kotlin
@Query("DELETE FROM sessions WHERE id = :sessionId")
suspend fun deleteById(sessionId: Long)

@Query("UPDATE sessions SET trackName = :trackName WHERE id = :sessionId")
suspend fun updateTrackName(sessionId: Long, trackName: String)
```

### New ViewModel Methods

```kotlin
fun deleteSession(sessionId: Long)  // Room delete + file delete + emit event
fun renameSession(sessionId: Long, newName: String)  // Validate + Room update + emit event
```

### New Events

```kotlin
data class ShowSessionDeleted(val trackName: String) : HomeEvent()
data class ShowSessionRenamed(val newName: String) : HomeEvent()
```

### New Unit Tests

| Test | Assertion |
|------|-----------|
| `deleteSession calls DAO deleteById` | verify(sessionDao).deleteById(42L) |
| `deleteSession handles missing session gracefully` | No crash when session null |
| `renameSession calls DAO updateTrackName with trimmed name` | verify(..., "New Track Name") |
| `renameSession rejects empty name` | verify(never()).updateTrackName() |
| `renameSession rejects name longer than 100 chars` | verify(never()).updateTrackName() |
| `renameSession accepts name with exactly 100 chars` | verify().updateTrackName() |

### Files Modified

```
M  app/src/main/java/.../data/db/dao/SessionDao.kt         (+6)
M  app/src/main/java/.../ui/home/HomeViewModel.kt          (+48)
M  app/src/main/java/.../ui/home/HomeFragment.kt           (+55)
A  app/src/test/java/.../ui/home/HomeViewModelTest.kt      (+165)
M  app/build.gradle.kts                                     (+5)
M  atlas/components.md                                     (+18)
M  01_requirements/DrivingCoach_SRS_v1.md                  (+15)
M  docs/USER_MANUAL.md                                     (+24)
```

---

## [2026-07-16] Track Name Navigation Fix

**Codebase Version:** v2.3-trackname-fix  
**Trigger:** RCA #08 track name lost in navigation

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| nav_graph.xml | ✅ Updated | +trackName args to 3 destinations |
| HomeFragment.kt | ✅ Updated | Pass trackName in navigation |
| TrackSetupFragment.kt | ✅ Updated | Receive + forward trackName |
| RecordingFragment.kt | ✅ Updated | Use args.trackName |
| 08_RCA_track_name_lost_in_navigation.md | ✅ Updated | Resolution status |

### Bug Fixed

| Before | After |
|--------|-------|
| All sessions named "Track Session" | Sessions use user-entered track name |

### Files Modified

```
M  app/src/main/res/navigation/nav_graph.xml         (+12)
M  app/src/main/java/.../home/HomeFragment.kt        (+1, -1)
M  app/src/main/java/.../tracksetup/TrackSetupFragment.kt (+4, -2)
M  app/src/main/java/.../recording/RecordingFragment.kt (+1, -1)
M  03_incidents/08_.../08_RCA_track_name_lost_in_navigation.md (+25)
```

---

## [2026-07-16] Coaching Bug Fix + Top Speed + Session Visibility

**Codebase Version:** v2.2-session-fix  
**Trigger:** RCA #06 invalid sector coaching + session visibility bug

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| SRS_v1.md | ✅ Updated | +10 requirements (OC-01 to OC-10), DP-01 clarified |
| components.md | ✅ Updated | Offline Coaching Engine fully rewritten |
| 06_RCA_invalid_sector_coaching.md | ✅ Updated | Resolution status added |

### New Requirement IDs

| ID | Description |
|----|-------------|
| OC-01 | Offline coaching generates local insights after lap detection |
| OC-02 | Produce 3-4 insights: Best Lap, Top Speed, Consistency, Sector Focus |
| OC-03 | Best Lap shows time delta vs average |
| OC-04 | Suppress sector detail when sector*Ms = 0 |
| OC-05 | Top Speed reads telemetry JSONL |
| OC-06 | GPS noise filter: reject >350 km/h |
| OC-07 | Consistency says "within" not "vary by" |
| OC-08 | Sector Focus shows "Coming Soon" upsell |
| OC-09 | Local insights stored with source="LOCAL" |
| OC-10 | Backend insights replace local insights |

### Bug Fixes

| Bug | Root Cause | Fix |
|-----|------------|-----|
| "0ms quicker in Sector 1" | No guard for zero sectors | `areSectorsAvailable()` check |
| Sessions not appearing | userId mismatch (`demo_user` vs `default_user`) | Removed userId filter |

### Files Modified

```
M  01_requirements/DrivingCoach_SRS_v1.md           (+18)
M  atlas/components.md                             (+25, -15)
M  03_incidents/06_coaching_incidents/06_RCA_invalid_sector_coaching.md (+28)
```

---

## [2026-07-15] Real Speed Chart

**Codebase Version:** v1.9-real-speed-chart  
**Trigger:** Real telemetry speed chart implementation (distance-based X-axis)

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| components.md | ✅ Updated | +55 lines (TelemetryChartProcessor) |
| flows.md | ✅ Updated | +85 lines (Chart Data Loading flow) |
| SRS_v1.md | ✅ Updated | +5 requirements (LC-11 to LC-15) |
| USER_MANUAL.md | ✅ Updated | Section 5.3 rewritten |

### New Requirement IDs

| ID | Description |
|----|-------------|
| LC-11 | X-axis = distance in meters (haversine) |
| LC-12 | Real telemetry data from JSONL |
| LC-13 | Warning for >10 laps |
| LC-14 | Fast/Detailed processing dialog |
| LC-15 | Fast mode = ~100 points/lap |

### Files Modified

```
M  atlas/components.md                  (+55)
M  atlas/flows.md                       (+85)
M  01_requirements/SRS_v1.md            (+7, -2)
M  docs/USER_MANUAL.md                  (+22, -6)
```

---

## [2026-07-13] Local Lap Detection

**Codebase Version:** v1.7-local-lap-detection  
**Trigger:** Offline lap detection feature + incident fixes

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| system.md | ✅ Updated | +LocalLapDetector component |
| components.md | ✅ Updated | +LocalLapDetector spec |
| flows.md | ✅ Updated | +Local Lap Detection flow |
| SRS_v1.md | ✅ Updated | +9 requirements (LD-20 to LD-28, TC-06a) |
| USER_MANUAL.md | ✅ Updated | Offline mode, troubleshooting |

### New Requirement IDs

| ID | Description |
|----|-------------|
| LD-20 | Local detection runs immediately after stop |
| LD-21 | Same algorithm as server |
| LD-22 | 20,000ms minimum lap time |
| LD-23 | 50m minimum distance (kart-compatible) |
| LD-24 | `isLocalOnly=true` flag |
| LD-25 | Server results overwrite local |
| LD-26 | "No laps detected" message |
| LD-27 | "📶 Offline" indicator |
| LD-28 | Start line from header or entity |
| TC-06a | Telemetry file header format |

### Related Incidents

- `03_incidents/02_lap_detection_not_triggering/`
- `03_incidents/03_200m_threshold_too_large/`
- `03_incidents/04_telemetry_header_missing/`
- `03_incidents/05_status_banner_blocks_data/`

### Files Modified

```
M  atlas/system.md
M  atlas/components.md
M  atlas/flows.md
M  01_requirements/DrivingCoach_SRS_v1.md
M  docs/USER_MANUAL.md
```

---
