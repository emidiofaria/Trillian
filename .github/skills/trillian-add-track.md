# trillian-add-track

Structures the process of adding a new **built-in circuit** to Trillian's bundled track
catalogue. Collects the survey data from the developer, sanity-checks it before any work
starts, and produces a complete implementation plan covering data, code, L1 and L2 tests,
documentation and release.

Use when a developer says they want to add a new built-in track, a pre-defined circuit,
or a new entry to the track library.

**Keywords:** track, circuit, built-in, bundled, catalogue, add track, new circuit,
track library, start line, centreline, survey, tracks.json, kartodromo

---

## Why this skill exists

Adding a circuit looks like editing one JSON file. It is not, and the gap between those
two beliefs is where the damage happens.

`Track.kt` declares no validation — no `require`, no `init` block. Every field except
`id`, `name` and `startLine` is nullable with a default. **A circuit with a backwards
heading, a missing length or a four-point centreline parses cleanly and produces laps.**
It simply produces the wrong ones, with no error anywhere. That is `FP-SILENT-DEGRADATION`
on the data side, and it is the same shape as Incident 15 itself.

So the data has no runtime guardrail. This skill is the guardrail, applied at the moment
the data is authored — which is the only moment anyone is paying attention to it.

---

## The six inputs

Ask all six. Four are required because the detector cannot work without them; two are
evidence, and what the app is allowed to claim depends on whether they exist.

| # | Input | Field in `tracks.json` | Required | Why the detector needs it |
|---|-------|------------------------|----------|---------------------------|
| 1 | **SF line** — the two coordinates of the start/finish line | `startLine` | ✅ | Nothing happens without it |
| 2 | **Car direction** — heading the kart travels when crossing | `travelHeadingDeg` | ✅ | LD-19: rejects crossings going the wrong way |
| 3 | **Track distance** | `lengthM` | ✅ | LD-22: judges whether a lap implies a believable speed |
| 4 | **Track envelope** — best realistic lap, very slow lap | `fastestLapMs` / `slowestLapMs` | ✅ | LD-20: sets the minimum gap between crossings |
| 5 | **Walk line** — surveyed centreline | `centreline.points` | Optional | Corridor checks; the shape of the circuit |
| 6 | **Real telemetry** — a recorded session, if one exists | test fixture | Optional | The only *independent* check on all of the above |

### Ask in three rounds, not one form

Later answers are validated against earlier ones, so they must arrive in order.

**Round 1 — Identity and geometry**
- Circuit id (lowercase slug, unique in the catalogue) and display name
- Location (town, country)
- SF line: `lat1, lng1` and `lat2, lng2`
- How the SF line was obtained: `MAP_COORDINATES`, `SURVEYED_ON_FOOT`, `CAPTURED_IN_APP`
- Date obtained

**Round 2 — Direction and size**
- `travelHeadingDeg`: the compass bearing a kart is travelling *as it crosses the line*,
  0–360. **Not** the bearing of the line itself, which is roughly perpendicular to it.
  Developers often find it easier to give **two points** — one before the line, one after,
  in the direction of travel. Accept that: compute the bearing yourself, round to 0.1°, and
  check the segment between the two points actually **crosses** the SF line.
- Track distance in metres, **and where that number came from** (official figure, map
  measurement, or derived from the walk — this matters, see below)
- Corner count
- Fastest realistic lap and slowest realistic lap. Ask for the extremes, not the
  average: a quick driver's best and a weekend driver's worst. For Baltar that is 40 s
  and 120 s.

**Round 3 — Evidence**
- Walked centreline points, if surveyed — plus who walked it, when, and how
- A recorded session for this circuit, if one exists
- Anything known to be uncertain, so it can be recorded rather than discovered later

**Verify the telemetry is from this circuit before using it.** The first fix and the
session's own captured start line must lie within ~200 m of the catalogued SF line. A
wrong zip is an easy mistake — S.Mamede was first supplied with a Cabo do Mundo session
8.9 km away — and every downstream check would then measure the wrong place. Also check the
session's date: it must **predate** the survey (TL-15).

---

## Sanity checks — run these before writing any plan

Cheap to run, and each one corresponds to a mistake that is expensive and silent.

### 1. Heading against the centreline — the one that matters most

Derive the direction of travel from the centreline near the start line and compare it to
the stated `travelHeadingDeg`.

- Disagreement **> 25°** → query it with the developer
- Disagreement **near 180°** → **stop**. Do not proceed.

A backwards heading is catastrophic and produces no error: every genuine crossing is
rejected as wrong-way, or every reverse crossing is accepted. This is Incident 15's exact
failure. Baltar's two candidate start lines sat **164.6° opposed** and *both* looked
entirely reasonable on a map — one of them was a driver walking across the line before
driving. Plausibility is not a check.

### 2. Envelope against distance

`lengthM ÷ fastestLapMs` and `lengthM ÷ slowestLapMs` must imply **18–126 km/h**. Below
the floor the detector stops believing a lap happened at all (`MIN_PLAUSIBLE_LAP_SPEED_MS`);
above the ceiling it is not a kart. Also assert `fastest <= slowest`, which is easier to
get backwards than it sounds.

### 3. Where the distance came from — provenance, not just value

**If `lengthM` was derived from the walked centreline, mark it `derived`.** A derived
length may not later be cited as corroboration of the survey, because it *is* the survey.

Baltar's 1020 m came from the walk, which makes "the ring perimeter is 1020 m" a true
statement that proves nothing.

The same applies the other way round. **If `lengthM` came from the recorded session**, that
session cannot corroborate it. Use the walked ring as the independent check, with a
**percentage** tolerance (S.Mamede: 802 m declared vs 821.6 m ring, +2.5%, asserted
within 3%), because the two measure different paths — racing line vs mid-track. Record
this in Annex A §A.3. Record provenance per dataset — the schema already supports
this, with `source`, `method`, `surveyedAt` and `surveyedBy` on the centreline and `source`
and `recordedAt` on the start line. Use them. One blanket claim across a map-read line and
a walked ring is untrue of half of it.

### 4. Start line and centreline geometry

- The two endpoints straddle the centreline rather than sitting to one side of it
- Width between **5 m and 25 m** — a narrow line is missed at 1 Hz sampling, a wide one
  catches traffic that never crossed. Baltar's is 10.97 m.

If a centreline is supplied, it must also be a **closed ring whose first point is the
start/finish**, because distance along the ring is what makes lap distance meaningful:

- First point within **1 m** of the start line's midpoint
- Ring perimeter within **10 m** of the published `lengthM` (or a % band — see check 3)

**If the walk did not start at the SF line**, do not discard it. With the developer's
approval, rotate the ring so it starts at the walked point nearest the line, and insert the
line's midpoint as point 0. Move and drop nothing; record it in Annex A §A.3. (S.Mamede's
walk began 173 m around the lap.)
- The closing segment (last point back to first) should be comparable to the other
  segment lengths, not an outlier — Baltar's is 12.3 m against a mean spacing of ~7 m,
  which is acceptable for hand-placed waypoints but is the kind of number to look at
  rather than assume

These tolerances match `BundledTrackCatalogTest.baltarsCentrelineIsAClosedRingStartingAtTheStartFinish`,
so data authored to them will satisfy the test suite.

### 5. Telemetry, if supplied

Replay it through `detectLapsWithDiagnostics` with `TrackPriors.NONE`, then check the
accepted crossings against the declared heading.

**Do not hand-roll crossing geometry to check this.** A naive segment-intersection pass
over the Baltar fixture finds **11** crossings where the detector finds **13**, because it
drops crossings near the ends of an 11 m line at 16 m sample spacing. A test built that
way asserts the bug, not the circuit.

Run with `TrackPriors.NONE` specifically: the detector then derives its reference heading
from the session itself, so the result measures the karts rather than echoing the value
being checked.

**GPS distance usually over-reads, but not always.** Summing 1 Hz fixes adds a random walk
on top of the true path; Baltar's karts covered a median 1094 m against a surveyed 1020 m,
**+7.3%**. On a tight circuit the opposite can win — fixes chord across corners and the
racing line is shorter than the walk; S.Mamede read **0.97×**. Any assertion must be a band
— roughly **0.9× to 1.2× the surveyed length** — never an equality and never a direction.

**Scale thresholds to the laps you have.** A three-lap session cannot carry Cabo's
"fastest > 0.75 × recorded best" check (an average driver's best says nothing about the
fast end). Prefer physical bounds: the declared fastest lap must be below every lap driven
and reachable at the session's peak speed.

### 6. Reversed-heading probe and corridor margin

Measure the distance from the SF midpoint to the nearest *other* part of the centreline
(more than 50 m along the ring) — the margin over the 15 m detection corridor. Then replay
the session with the heading reversed 180°:

- **Narrow margin** (Cabo, 3.4 m): reversed → **zero laps**. Assert that.
- **Wide margin** (S.Mamede, 65 m): reversed → **same laps**, because the detector falls
  back to the first-crossing reference. A lap-count assertion passes on either heading.
  Assert `DetectionDiagnostics.headingReference` instead: `TRACK_CATALOGUE` for the
  shipped heading, `FIRST_CROSSING` for the reversed one (`FP-FALLBACK-MASKS-PRIOR`).

Run the real detector in a scratch probe for this — never a re-implementation
(`FP-REIMPLEMENTED-GEOMETRY`) — and delete the probe afterwards.

### 7. Draw it before planning (optional, recommended)

Render the centreline, SF line, heading arrow, GPS laps and the session's captured line on
one image (matplotlib in a throwaway venv) and show it to the developer. A reversed arrow or
a line on the wrong straight is obvious in a picture and invisible in a table of numbers.

### Reporting

Report every failure to the developer with the measured value and the expected range, and
let them decide. Never quietly adjust a number to make a check pass — the number came from
a physical place and the developer is the only one who knows which is wrong.

---

## Evidence tiers — what the data earns

The release channel follows from the evidence, not from confidence.

| Tier | Evidence held | What it supports |
|------|---------------|------------------|
| **A** | SF line, heading, distance, envelope | Structural checks only — **internal testing only** |
| **B** | + walked centreline | Corridor checks, shape validation — internal testing only |
| **C** | + independent recorded session | Cross-corroboration (Baltar's current tier) — internal testing only |
| **D** | + L4 acceptance run at the circuit | Eligible for wider release |

Tiers A–C are all internal-only. Two datasets that agree are still not a measurement taken
at racing speed — corroboration between a walk and a recording is meaningful only because
the recording predates the catalogue and so cannot have been fitted to it, and even then
it is evidence, not validation. **Only a lap driven at the circuit moves a track to D.**

---

## The implementation plan to produce

Once the inputs pass their checks, produce a plan in the SW_dev Phase 2 format containing
these tasks. Most track additions are data plus tests plus docs, with no production code —
but confirm that rather than assuming it.

### Task 1 — Catalogue data
- Add the entry to `app/src/main/assets/tracks/tracks.json`
- Provenance recorded **per dataset**, not per track
- Check the id is unique and the JSON still parses

### Task 2 — Production code
Usually **none** — the catalogue is data-driven and the UI renders whatever it loads.
State explicitly that no code is needed, or name what is. A circuit needing new fields is
a schema change and a much larger piece of work.

### Task 3 — L1 unit tests
Template: `CaboDoMundoCatalogueTest` / `CaboDoMundoSurveyCorroborationTest` (or the
S.Mamede pair for a wide-margin circuit) — they are the most complete.
- Catalogue entry parses and carries the four required fields
- Structural checks from this skill, as assertions
- Fixture in `app/src/test/resources/lapfixtures/<id>/` (auto-shared with androidTest assets)
- If telemetry exists: a corroboration test, including the guard that the catalogued line is
  not the line a driver captured, and the reversed-heading assertion chosen in check 6
- **Mutation-test the new assertions.** Change the heading, change the length, confirm each
  one fails. An assertion that survives a deliberately wrong value is not testing anything.

### Task 4 — L2 instrumented tests
- The circuit appears in the track list and can be selected
- Its priors reach the detector — assert on `DetectionDiagnostics`
  (`headingReference`, `referenceHeadingDeg`, `minLapTimeMs`), not merely that laps appeared
- A `<Circuit>PriorsEndToEndTest` modelled on `CaboDoMundoPriorsEndToEndTest`
- Confirm screen shows the circuit's own facts (`TrackConfirmDisplayTest`, parameterised)
- **Never assert the catalogue size.** Use `containsAll(...)` — an `assertEquals(2, size)`
  breaks on every addition

### Task 5 — Documentation (before validation)
Via `.github/skills/trillian-docs-sync.md`. It must precede the full run, because
`CircuitEvidenceAnnexTest` (TL-18) fails until Annex A has the circuit.
- `01_requirements/ANNEX_A_circuit_evidence.md`: rows in §A.1 (claims, provenance), §A.2
  (tier, corridor margin, LD-22 floor, outstanding work) and a §A.3 note for anything unusual
- SRS: **generic rules only** — TL-17 forbids naming a circuit in a requirement
- `05_tests/coverage-map.tsv`: TL-05/TL-15 rows for the new tests
- `05_tests/L4_SYS5_acceptance/50_LAP_DETECTION_TESTS.md`: an `LD-CAT-NN` on-track test —
  it is the tier-D gate
- atlas `failure-patterns.md` if something new was learned; SYNC_REPORT entry with the tier
- USER_MANUAL only if it names circuits (it currently points users at SELECT TRACK)

### Task 6 — Validation
Full L1 and L2 (`run-all-tests.sh --start-emulator --stop-emulator`), then the report.
Catalogue changes touch shared fixtures, so a targeted run is not sufficient.

### Task 7 — Update this skill
Fold in anything the addition taught — new gotchas, thresholds that did not fit, steps that
were missing. A skill not updated after use is stale by the next circuit.

### Task 8 — Release
Package per `docs/RELEASE.md`, with the channel set by the evidence tier.

---

## Known gotchas

Status as of v3.02 (third circuit, S.Mamede). The single-track assumptions recorded at v3.0
have been fixed: `SessionStartForkTest` is generic, and `BundledTrackCatalogTest` now
requires completeness for bundled entries instead of skipping them.

### Circuit-specific tests are still per circuit

Most catalogue tests look up one id directly. A new circuit inherits only the catalogue-wide
checks in `BundledTrackCatalogTest` — its own catalogue and corroboration tests must be
written.

### `Track.kt` validates nothing

Bad catalogue data does not fail, it under-performs silently. This is the reason for this
skill.

### Annex A is enforced

`CircuitEvidenceAnnexTest` reads the first column of the Annex A tables. A circuit in
`tracks.json` without a row fails TL-18; so does a row for a circuit that is not shipped.

### The fallback hides a wrong heading on wide-margin circuits

See check 6. Assert on `headingReference`, not lap counts.

### Stale per-circuit figures in L2/L4

When a catalogue value changes, grep for its derived figures (e.g. `minLapTimeMs` =
max(20 s, 0.8 × fastest)) in androidTest and the L4 checklists. Cabo's fastest lap moved
60 → 50 s on 2026-09-29, but `CaboDoMundoPriorsEndToEndTest` and LD-CAT-04 still said 48000.

### Editing the catalogue used not to re-run its tests

`app/build.gradle.kts` declares `src/main/assets/tracks` and `01_requirements` as `Test`
task inputs. **Do not remove them.** Without them, tests reading those files by path are
invisible to Gradle's up-to-date checks (`FP-UNDECLARED-TEST-INPUT`).

---

## Reference: shipped catalogue entry

Baltar, for shape. Note provenance appearing twice, describing two different datasets.

```json
{
  "id": "baltar",
  "name": "Kartódromo de Baltar",
  "location": "Baltar, Paredes, Portugal",
  "startLine": {
    "lat1": 41.187838, "lng1": -8.395666,
    "lat2": 41.187770, "lng2": -8.395761,
    "source": "MAP_COORDINATES",
    "recordedAt": "2026-09-20"
  },
  "travelHeadingDeg": 137.8,
  "lengthM": 1020,
  "cornerCount": 13,
  "fastestLapMs": 40000,
  "slowestLapMs": 120000,
  "centreline": {
    "source": "SURVEYED_ON_FOOT",
    "method": "manually placed waypoints, walked",
    "surveyedAt": "2026-09-20",
    "surveyedBy": "Emidio Costa",
    "points": [[41.187801, -8.395719], "… 140 points, closed ring …"]
  }
}
```

**Files involved in adding a circuit:**

```
app/src/main/assets/tracks/tracks.json          # the entry itself
app/src/main/java/com/drivingcoach/data/track/  # Track.kt, BundledTrackCatalog.kt (read only)
app/src/test/java/com/drivingcoach/data/track/  # L1 catalogue + corroboration tests
app/src/androidTest/java/com/drivingcoach/      # L2 track list, confirm, priors handoff
app/src/test/resources/lapfixtures/             # telemetry fixture, if one exists
01_requirements/DrivingCoach_SRS_v1.md          # TL requirements (generic only, TL-17)
01_requirements/ANNEX_A_circuit_evidence.md     # per-circuit evidence register (TL-18)
05_tests/coverage-map.tsv                       # requirement → test claims
05_tests/L4_SYS5_acceptance/50_LAP_DETECTION_TESTS.md  # LD-CAT-NN on-track test
docs/USER_MANUAL.md                             # built-in circuits section
```
