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
statement that proves nothing. Record provenance per dataset — the schema already supports
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
- Ring perimeter within **10 m** of the published `lengthM`
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

**Expect GPS distance to over-read.** Summing 1 Hz fixes adds a random walk on top of the
true path; Baltar's karts covered a median 1094 m against a surveyed 1020 m, **+7.3%**,
which is textbook rather than alarming. Any assertion must be a band — roughly
**0.9× to 1.2× the surveyed length** — never an equality.

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
- Catalogue entry parses and carries the four required fields
- Structural checks from this skill, as assertions
- If telemetry exists: a corroboration test modelled on `BaltarSurveyCorroborationTest`,
  including its guard that the catalogued line is not the line a driver captured
- **Mutation-test the new assertions.** Change the heading, change the length, confirm each
  one fails. An assertion that survives a deliberately wrong value is not testing anything.

### Task 4 — L2 instrumented tests
- The circuit appears in the track list and can be selected
- Its priors reach the detector — assert on `DetectionDiagnostics`
  (`headingReference`, `referenceHeadingDeg`, `minLapTimeMs`), not merely that laps appeared
- **Run the single-track assumption audit below.**

### Task 5 — Documentation
Via `.github/skills/trillian-docs-sync.md`: SRS, atlas, the USER_MANUAL circuit list, and
a SYNC_REPORT entry recording the evidence tier and why the circuit is trusted as far as
it is.

### Task 6 — Validation
Full L1 and L2, then generate a report. Catalogue changes touch shared fixtures, so a
targeted run is not sufficient.

### Task 7 — Release
Package per `docs/RELEASE.md`, with the channel set by the evidence tier.

---

## Known gotchas

Traps confirmed in the codebase as of v3.0. They have not been fixed, because until a
second circuit exists they are not wrong — they are assumptions that happen to hold.
**The first developer to add a second built-in track will meet all of them.**

### The catalogue is assumed to contain exactly one track

`SessionStartForkTest` (~line 233) opens the first circuit on the list and asserts:

```kotlin
assertEquals("the bundled circuit is the one on the list", "baltar", trackId)
```

A second bundled circuit sorting above Baltar fails this test. The assertion should become
"is a bundled circuit", not "is Baltar".

`TrackRepositoryTest` has recency assertions reading `observeTracks().first().first()`,
which are sensitive to catalogue ordering for the same reason.

### An incomplete track passes the catalogue-wide test by omission

`BundledTrackCatalogTest.everyCircuitsLengthAndLapEnvelopeImplyAPlausibleSpeed` is the only
test that loops the whole catalogue, and it opens with:

```kotlin
if (length == null || fastest == null || slowest == null) return@forEach
```

**A track that omits its length skips the only check that would have caught it.** When a
second track is added, this should assert completeness for bundled tracks rather than
skipping. User-saved circuits legitimately have no surveyed length, so the stricter rule
applies to bundled entries only.

### Six of the seven catalogue tests are hardcoded to Baltar

They look up `id == "baltar"` directly. A new circuit inherits almost no coverage
automatically — its tests must be written, or the existing ones generalised to loop.

### `Track.kt` validates nothing

Stated above and repeated because it is the reason for this skill: bad catalogue data
does not fail, it under-performs silently.

### Editing the catalogue used not to re-run its tests

`app/build.gradle.kts` declares `src/main/assets/tracks` as a `Test` task input. **Do not
remove it.** Without it, tests reading the asset by path are invisible to Gradle's
up-to-date checks: edit a circuit, run the tests, see green, ship the wrong circuit. This
was measured — mutating the heading and the length scored zero detections. `clean` and
`--rerun` both hide it, so CI would never have shown it. Recorded as
`FP-UNDECLARED-TEST-INPUT`.

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
01_requirements/DrivingCoach_SRS_v1.md          # TL requirements
docs/USER_MANUAL.md                             # the list of built-in circuits
```
