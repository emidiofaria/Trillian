# Annex A — Circuit evidence register

Companion to [DrivingCoach_SRS_v1.md](DrivingCoach_SRS_v1.md). Normative requirements live
there; this annex holds the evidence for individual circuits.

**Why the split.** A requirement is a claim about the app and is true of every circuit. A
catalogue entry is a set of claims about one real place, and there will eventually be more of
them than a requirements document can carry. Naming circuits inside requirements made the two
inseparable: a reader could not tell which sentences were rules and which were examples, and
adding a circuit meant editing the specification. TL-17 forbids it; TL-18 requires that every
shipped circuit appear here.

**What this file is not.** It is a *register*, not a derivation. Every figure below is asserted
by a test, and the test is named beside it. Where this file and a test disagree, the test is
right and this file is a defect. It is deliberately tabular: prose per circuit does not survive
a hundred circuits, so §A.3 is reserved for the cases where something genuinely unusual
happened, not for restating a row in sentences.

---

## A.1 Catalogue claims

The contents of `app/src/main/assets/tracks/tracks.json`, per TL-04 and TL-05.

| Circuit id | Name | Location | Lap length | Corners | Travel heading | Lap envelope |
|---|---|---|---|---|---|---|
| `baltar` | Kartódromo de Baltar | Baltar, Paredes, Portugal | 1020 m | 13 | 137.8° | 40–120 s |
| `cabo_do_mundo` | Cabo do Mundo | Leça da Palmeira, Matosinhos, Portugal | 826 m | 14 | 59.8° | 50–120 s |

Provenance is recorded per dataset, not per circuit (TL-05). The two datasets of a circuit may
have been obtained by different means and may be re-surveyed independently.

| Circuit id | Start/finish line | Line provenance | Line surveyed | Centreline | Centreline provenance | Centreline surveyed |
|---|---|---|---|---|---|---|
| `baltar` | 10.97 m | `MAP_COORDINATES` | 2026-09-20 | 140 waypoints | walked on foot | 2026-09-20 |
| `cabo_do_mundo` | 7.00 m | `SURVEYED_ON_FOOT` | 2026-09-28 | 159 waypoints | walked on foot, mid-track | 2026-09-28 |

Structural conformance of every row above — identifier uniqueness, closed centreline ring, line
squareness against the declared heading, envelope plausibility — is asserted by
`BundledTrackCatalogTest` for all circuits and by `CaboDoMundoCatalogueTest` for `cabo_do_mundo`.

---

## A.2 Evidence and tier

Tier definitions and the release rule they gate are in [docs/RELEASE.md](../docs/RELEASE.md).
A release travels no further than its **lowest**-tier circuit.

Per TL-15, `travelHeadingDeg`, `lengthM` and the lap envelope are corroborated against a
recorded session before a circuit ships. The corroborating session must **predate the
catalogue entry**, otherwise agreement proves only that the data was fitted to itself.

| Circuit id | Tier | Corroborating session | Session predates entry | Heading agreement | Length agreement | Owning test |
|---|---|---|---|---|---|---|
| `baltar` | C | Incident 15 recording | yes — recorded before the catalogue existed | every crossing within 10° of 137.8° | median 1094 m measured vs 1020 m surveyed | `BaltarSurveyCorroborationTest` |
| `cabo_do_mundo` | C | 2026-09-26 kart session, 10 laps @ 1 Hz | yes — 2 days before survey, 3 before cataloguing | every racing crossing within 3.4° of 59.8° | median 841 m measured vs 826 m surveyed | `CaboDoMundoSurveyCorroborationTest` |

Measured lap distance exceeding surveyed distance is expected, not an error: summing the
straight-line distance between consecutive 1 Hz fixes over-reads, because each fix carries its
own error and the sum accumulates a random walk on top of the true path.

**The lap envelope is corroborated differently from the other two, and the difference matters.**
`travelHeadingDeg` and `lengthM` are properties of the place, so a recorded session measures
them directly. An envelope is not: it is a claim about the range of drivers a circuit will see,
and one session shows one driver. A session can therefore *falsify* an envelope — by containing
a lap outside it, or by showing the declared pace to be physically unreachable — but it cannot
establish one. Where a circuit's fast end comes from operator knowledge rather than from the
session, as `cabo_do_mundo`'s 50 s does, the table above should be read as recording that the
session does not contradict it. What is asserted of such a figure is that it stays below every
lap recorded, stays inside the fastest speed observed, and — at the slow end — sits above the
slowest real lap and no wider than LD-22's discard floor. See §A.3.

### Detection corridor margin

`LocalLapDetector` accepts fixes within a fixed 15 m half-width of the start/finish midpoint.
Where another part of the circuit re-enters that corridor, the catalogued heading (LD-20) is
the only thing separating the two pieces of track — see the remark on LD-20 in the SRS.

| Circuit id | Nearest other part of circuit to the start point | Margin over the 15 m corridor | Owning test |
|---|---|---|---|
| `baltar` | 24.7 m | 9.7 m | `BaltarSurveyCorroborationTest` |
| `cabo_do_mundo` | 18.4 m | 3.4 m | `CaboDoMundoCatalogueTest` |

### Lap length guard floor

LD-22 derives its floor from the surveyed length at 5 m/s. It is stated here because the figure
is circuit-specific and drivers do occasionally drive slower than it.

| Circuit id | LD-22 floor | Observed laps in the corroborating session |
|---|---|---|
| `baltar` | 204 s | 12 laps recovered |
| `cabo_do_mundo` | 165.2 s | 8 flying laps, 63.4–70.8 s, at 12–13 m/s; one out-lap at 167.1 s correctly discarded |

### Outstanding work

| Circuit id | To reach tier D |
|---|---|
| `baltar` | An L4 acceptance run driven at the circuit on the shipped build |
| `cabo_do_mundo` | LD-CAT-04 in [`05_tests/L4_SYS5_acceptance/50_LAP_DETECTION_TESTS.md`](../05_tests/L4_SYS5_acceptance/50_LAP_DETECTION_TESTS.md) |

---

## A.3 Exception notes

Only circuits whose evidence departs from the normal pattern appear here. A circuit surveyed
and corroborated in the ordinary way needs no entry; its rows in §A.1 and §A.2 say everything.

### `baltar` — the start/finish line is derived from map imagery

Baltar is the only shipped circuit whose two datasets come from different sources: the
centreline was walked, the line was read off map imagery. This is what TL-05's per-dataset
provenance was written for. The line is not weaker for it — a 10.97 m line read from imagery is
better than one captured while standing still on a phone (FP-STATIONARY-POSITION-BIAS) — but it
is a different kind of evidence with different error characteristics, and a reader deciding
whether to trust it needs to be told which.

### `cabo_do_mundo` — corroborated from a session whose own start line was in the pit lane

The driver of the 2026-09-26 session marked their start line **in the pit lane**: 7 m off the
racing line, 55 m short of the real start/finish, with the kart stationary on it for the first
76 s of the recording.

This strengthens rather than weakens the corroboration. The catalogued line and the recorded
line are 57 m apart around the lap and were produced by entirely unrelated means, yet timing the
same session at both points yields lap times agreeing to within a third of a second. A lap is a
lap wherever it is counted from, and the surveyed geometry reproduces independently what the
driver measured on the day.

### `cabo_do_mundo` — the lap envelope was drafted wrong, corrected wrongly, then corrected

The entry was first drafted carrying Baltar's 40 s fastest lap on a circuit 194 m shorter.
Replaying the recorded session showed the best lap was 63.4 s, and that a 40 s lap would demand
a 74.3 km/h average against a 72.5 km/h peak recorded anywhere in the session — not optimistic
but impossible.

The figure was then set to 60 s, taken from the best lap in that session. **That was a
methodological error and is recorded here rather than quietly overwritten, because every test in
the suite went on passing through it.** A recorded session bounds what *has* been driven, not
what *can* be. It is evidence for the fast end of an envelope only if a fast driver happened to
be the one holding the phone, and this driver was an average one. The number therefore described
a person rather than a place, and it set LD-21's floor — the minimum gap the detector will
believe between two crossings — at 48 s on a circuit where quick pilots lap in the low fifties.

The shipped figure is **50 s**, supplied by the operator as what quick pilots lap this circuit
in. It is reachable (59.5 km/h average, inside the 72.5 km/h peak) and still sits below every
lap in the recorded session, which is what makes it an envelope rather than a guess. Its
provenance is operator knowledge, not the session, and §A.2 records it as such.

`slowestLapMs` remains 120 s. It arrived as a copy of Baltar's figure and the assertion guarding
it read `assertEquals(120_000L, slowestLapMs)` — a restatement of the catalogue that would have
passed on any number at all. There is no measurement that fixes the slow end, because a slow lap
is a driver having a bad one rather than a property of the circuit, so what is now asserted is
the band it has to fall in: above the slowest lap actually driven (70.9 s), and at or below
LD-22's discard floor of 165.2 s, beyond which the app would be promising a window narrower than
the one it actually enforces.

It is worth being exact about what none of this changed, because it would be easy to overclaim.
On this circuit and this session, 40 s, 48 s and even 20 s floors all produce the **same eight
laps** — measured, not reasoned, by
`looseningTheFloorToFortySecondsAdmitsNothingTheOldOneWasHolding`, which replays one session
against two floors and requires identical output. In particular the return section, which passes
18.4 m from the start point and had been suspected of being held back by the 48 s floor, turns
out never to offer a candidate crossing at all: it is filtered on geometry and direction long
before timing is consulted. The envelope was corrected because it was untrue, not because it was
doing damage. A catalogue entry is a set of claims about a place, and claims that happen not to
be load-bearing today are still claims — which is precisely how the 60 s figure survived.

### `cabo_do_mundo` — the direction prior is load-bearing here

With a 3.4 m margin over the detection corridor, the corridor alone does not separate the return
section from the start/finish. Replaying the recorded session with `travelHeadingDeg` reversed
180° — the single likeliest data-entry error, and the exact shape of incident 15 — yields **zero
laps**, no error, and a driver shown an empty session.
`CaboDoMundoSurveyCorroborationTest` keeps that mutation as a permanent assertion, so the guard
cannot be weakened without a test failing.
