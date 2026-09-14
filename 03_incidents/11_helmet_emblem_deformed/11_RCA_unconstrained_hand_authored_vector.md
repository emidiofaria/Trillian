# RCA — Incident 11: Helmet Emblem Rendered Totally Deformed

**Incident**: `03_incidents/11_helmet_emblem_deformed/`
**RCA Date**: 2026-08-11
**Analyst**: RCA Engine
**Status**: Root cause identified — HIGH confidence
**Severity**: MEDIUM (cosmetic / brand quality — no functional loss)

---

## Incident Summary

The Trillian racing-helmet emblem renders as an unrecognisable shape on every branded
surface (splash screen, Home hero, collapsed toolbar). Analysis of
`app/src/main/res/drawable/ic_helmet_emblem.xml` confirms the defect is entirely contained
in the vector asset: the drawable was hand-authored as nine independent Bézier paths with
no shared construction geometry — no centre line, no symmetry constraint, no silhouette
reference. Each layer's coordinates were chosen in isolation, so the composite has no
coherent helmet structure. The base silhouette is a **dome that is wider than it is tall**
(88 × 76 units), the visor is **5.1 units left of the shell centre**, and the two hardware
details land in arbitrary positions.

The incident report's stated hypothesis — *"misaligned path coordinates between these
layers"* causing detached/overlapping shapes — is **contradicted by measurement**: 7 of the
8 overlay layers fall 100 % inside the shell. The layers are not detached; the *shapes
themselves* were never constructed to a common design.

---

## Impact

- **Users Affected**: 100 % of users — the emblem is the first element rendered at cold start.
- **Duration**: Since commit `7350eb3` ("Add better UI signature"). The asset has never been revised.
- **Data Loss**: None.
- **Functional Loss**: None. Recording, lap detection, telemetry and upload are unaffected.
- **Severity**: MEDIUM — brand identity is compromised on every branded surface, and the
  defect is visible on the very first screen shown.

---

## Method

Because the incident concerns a static asset, the primary evidence is the asset itself
rendered in isolation — not logs. The vector was converted to SVG (preserving `pathData`
and `fillColor` verbatim, viewport `120 × 120`) and rasterised with `cairosvg`, then
analysed with per-layer masks at 600 × 600 (5× supersampling).

This is **reproducible offline** and independent of device, API level or theme, which is
itself an important finding (see *Eliminated Hypotheses*).

**Evidence artefacts** (saved beside this report):

| File | Content |
|------|---------|
| `evidence_emblem_isolated.png` | The drawable rendered alone at 480 px |
| `evidence_layer_buildup.png` | Cumulative 4-stage build-up, final panel annotated |
| `evidence_actual_sizes.png` | Rendered at the three shipped sizes (132/88/36 dp @ xhdpi) |

---

## Signals Observed

### Present (Unexpected)

| # | Measurement | Value | Why it is wrong |
|---|-------------|-------|-----------------|
| 1 | Shell bounding box | x 16–104, y 16–92 → **88 w × 76 h** | Aspect **1.16 : 1 (wider than tall)**. A racing helmet is taller than wide or square. This alone makes the silhouette read as an egg/dome. |
| 2 | Shell vertical placement | centre y = **54** in a 120 viewport | **6 units above centre**; 16 u padding above vs 28 u below. Emblem sits high with dead space beneath in every square container. |
| 3 | Visor horizontal centre | **54.9** vs shell centre **60.0** | Visor is **5.1 u (5.8 % of shell width) left of centre**. Left margin 10 u, right margin 23 u. |
| 4 | Accent band (blue) occlusion | **48.8 %** hidden by the visor | Only ~5 u of the 15 u band survives; the intended tricolour livery collapses to a sliver. |
| 5 | Visor occlusion | **45.1 %** hidden by the reflection layer | The "aperture" is mostly reflection; the dark visor reads as an outline, not an opening. |
| 6 | Visor pivot circle | x 75–83, y 32–40 | Visor spans to x = 81, y from 33 — the circle **straddles the visor's top-right corner**, half on dark, half on yellow. A pivot belongs at mid-visor-height (~y 47). |
| 7 | Side fastener circle | x 83–93, y 55–65 | Sits on bare shell with no adjoining strap or vent. Reads as a stray white dot. |
| 8 | Chin bar | x 27–96, y **68–92**, flat top edge | Occupies the bottom third as a hard-edged block of `#C99A1E`. It is a **flat tonal band, not chin-bar geometry** — no jaw, no chin protrusion. |
| 9 | Visor bottom-left vertex | `…C81,58 78,61 74,61 L31,63 C28,63 26,61 26,58 Z` | Outline descends to (31,63) then hooks back up-left to the start (26,58), producing a **degenerate pointed spur** (visible in the zoomed render). |

### Present (Expected)

| Signal | Value | Interpretation |
|--------|-------|----------------|
| Shell bilateral symmetry (IoU vs mirror) | **94.5 %** | Shell is *approximately* symmetric — asymmetry is **not** the dominant defect. |
| Vector parses; all 9 paths render | Yes | No malformed `pathData`, no parse error, no missing layer. |
| Layers contained within shell | 7 of 8 at **0.0 %** spill | Layers are **not** detached or floating outside the silhouette. |

### Absent (Expected but Missing)

- **No layer spill.** Expected if the incident's "misaligned coordinates" hypothesis held.
  Measured spill: `accent_band` 0.0 %, `rear_sweep` 0.0 %, `visor` 0.0 %, `visor_refl` 0.0 %,
  `chin_bar` 0.0 %, `fastener` 0.0 %, `pivot` 0.0 %. Only `upper_band` shows **1.2 %**,
  consistent with antialiasing along a shared edge, not misplacement.
- **No `scaleType` or aspect mismatch.** All three `ImageView`s are square
  (132 × 132 dp, 88 × 88 dp, 36 × 36 dp) against a square `120 × 120` viewport, and none
  sets `android:scaleType`, so the default `fitCenter` applies uniform scaling.
  **The layouts do not contribute to the distortion** — this closes the open question
  raised in the incident's *Notes*.
- **No density-specific or API-specific variant.** `find res -name ic_helmet_emblem*`
  returns exactly one file; there is no `drawable-v24/`, `-hdpi`, or night variant that
  could differ per device.
- **No CI gate.** `.github/` contains `agents/`, `instructions/`, `skills/` only —
  **no `workflows/` directory exists**. No lint, build or asset check runs on push.
- **No geometry assertion in the test suite.** See *Contributing Factor 2*.

---

## Systems Involved

| Component | Role in Incident | Reference |
|-----------|------------------|-----------|
| Branding asset `ic_helmet_emblem.xml` | **Sole defect location** | — (not catalogued in Atlas) |
| App Startup / Branded Loading Screen | Renders the emblem at 132 dp | `components.md` |
| Home Brand Hero (Collapsing Toolbar) | Renders at 88 dp and 36 dp | `components.md` |
| `ic_splash_emblem.xml` | 20 % inset wrapper for the Android 12+ system splash | — |

**Atlas coverage gap**: `failure-patterns.md` contains no pattern for static-asset defects,
and `components.md` lists only three failure modes for the Home Brand Hero — all behavioural
(collapse thresholds, stale id). Asset *correctness* is not modelled anywhere. This incident
is therefore a **novel pattern**.

---

## Hypotheses

### Primary Hypothesis — ACCEPTED

**Unconstrained hand-authored vector**: The nine paths were written by hand without a shared
construction grid, centre line, or silhouette reference, so no layer is geometrically
derived from any other.

**Evidence for**:
- Shell aspect 88 × 76 — wider than tall (signal 1).
- Visor centre 54.9 vs shell centre 60.0 (signal 3) — an offset that could not survive a
  shared centre line.
- Visor margins 10 u left / 23 u right (signal 3) — no edge-referencing.
- Chin bar top edge is a straight horizontal at y = 68 (signal 8) — a tonal band, not a
  derived jaw contour.
- Pivot circle straddles the visor boundary (signal 6) — placed by eye, not snapped to it.
- 48.8 % of the accent band is swallowed by a later layer (signal 4) — the paint order was
  never reconciled with the shapes.
- Degenerate spur at the visor's bottom-left (signal 9) — a hand-editing artefact.

**Evidence against**:
- None. Every measured signal is consistent with this explanation.

**Confidence**: **HIGH (93 %)**

### Alternative Hypothesis — PARTIALLY SUPPORTED (contributing, not causal)

**Detail budget exceeds the render size**: nine layers, including 8–10 u circles and a 7 u
livery band, inside a 120 u viewport that ships at 36 dp in the collapsed toolbar.

At 36 dp the livery band is ≈ 2.1 dp and the pivot circle ≈ 2.4 dp — at or below the
threshold where they merge into adjacent fills. This **worsens** the appearance at the
smallest size but does not explain the deformation at 132 dp, where the shape is equally
unrecognisable (`evidence_actual_sizes.png`).

**Confidence**: **MEDIUM (60 %)** as a *contributing factor*; **LOW (10 %)** as a root cause.

### Eliminated Hypotheses

| Hypothesis | Why eliminated |
|------------|----------------|
| **Misaligned/overlapping path coordinates between layers** (the incident's own stated hypothesis) | Measured spill is 0.0 % for 7 of 8 layers, 1.2 % for the eighth. Layers sit inside the shell as intended. |
| **Layout `scaleType` / fixed-size mismatch** (open question in the incident *Notes*) | All three `ImageView`s are square against a square viewport with default `fitCenter`. No non-uniform scaling exists. |
| **Clipping by `bg_hero_ring`** | The emblem renders identically in isolation, with no container. |
| **Density-specific or API-specific asset variant** | Exactly one `ic_helmet_emblem.xml` exists; no qualified variants. |
| **Regression introduced by `4a1afd1`** (L2 test commit) | `git log --follow` shows the drawable has exactly one commit, `7350eb3`. `4a1afd1` touched no `res/drawable/` file. |
| **Renderer/device-specific bug** | The defect reproduces in an offline SVG rasteriser with no Android runtime involved. |

---

## Root Cause

**ROOT CAUSE** — *The emblem was authored as nine independent hand-written Bézier paths with
no shared construction geometry, and the project has no gate that can detect an incorrect
vector.* Because no layer is derived from a common centre line or from the shell silhouette,
the composite has no coherent helmet structure: the base is a dome wider than it is tall, the
visor is off-centre, the "chin bar" is a flat tonal band rather than jaw geometry, and the
hardware details sit in arbitrary positions.

**TRIGGER** — Commit `7350eb3` ("Add better UI signature") introduced
`ic_helmet_emblem.xml` and wired it into four render sites simultaneously. The asset was
merged on visual inspection of code alone; the composite was never rendered and reviewed.

**CONTRIBUTING FACTORS**

1. **Blast radius by design.** All four sites reference the same drawable, so a single bad
   asset reaches every branded surface at once. (This same property makes the fix a
   one-file change — it cuts both ways.)
2. **Tests assert presence, not correctness.** `SplashScreenTest` checks
   `onView(withId(R.id.emblemContainer)).check(matches(isDisplayed()))`; `HomeHeroTest` does
   the equivalent. **These pass with any drawable, including a blank one.** The L2 suite
   added in `4a1afd1` gave real coverage of startup *behaviour* while leaving asset
   *correctness* entirely unguarded — a coverage blind spot that this incident exposes.
3. **No CI.** `.github/workflows/` does not exist, so nothing renders, lints or diffs assets
   on push.
4. **Detail budget exceeds the smallest render size** (36 dp collapsed), amplifying the
   defect where it is least legible.
5. **Asset correctness is absent from Atlas.** No component or failure pattern models it, so
   the RCA workflow had no prior art to match against.

---

## Confidence Level

**Overall Confidence: HIGH (93 %)**

**Rationale**: The defect is fully reproducible from the checked-in file with no device,
runtime or environment dependency. Every claim above is a direct measurement of the asset,
and the two hypotheses proposed in the incident report were tested and falsified rather than
assumed. The causal chain accounts for all nine observed signals with no residual.

**Remaining uncertainty (7 %)**:
- The *intended* design is not documented anywhere, so "correct" is judged against the
  generic form of a racing helmet rather than an approved reference. A design source of
  truth would raise this to ~99 %.
- No screenshots from a physical device were supplied. The offline render is authoritative
  for geometry, but device screenshots would also confirm no theme/tint layer interferes.
  This is a low-value gap — signal 1 alone is sufficient to explain the report.

---

## Mitigation

### Immediate (User Recovery)

None available or required. The defect is cosmetic; no user action can work around it and
no data is at risk. The next build carrying a corrected asset resolves all four sites.

### Short-term (Correct the Asset)

Redraw `ic_helmet_emblem.xml` against explicit construction constraints:

1. **Fix the silhouette first.** Target ~**84 w × 92 h** in the 120 viewport (taller than
   wide), vertically centred (y ≈ 14–106), mirrored about **x = 60**.
2. **Derive every layer from the shell**, not from absolute guesses. Visor centred on
   x = 60 with equal left/right margins; chin bar as a true protruding jaw with a curved,
   not horizontal, upper contour.
3. **Reconcile paint order with shape.** The accent band must not be re-covered — currently
   48.8 % is wasted geometry.
4. **Cut the detail budget.** Drop the pivot and fastener circles, or move them to a
   `-v24`/large-only variant; neither survives 36 dp.
5. **Remove the degenerate spur** at the visor's bottom-left.
6. Keep the artwork original in-house work (incident acceptance criterion 5).

### Permanent (Systemic Fix)

1. **Render assets during review, not just read them.** Attach an isolated render of any new
   or changed vector to its PR. The `vd2svg` + `cairosvg` approach used in this RCA is ~20
   lines and runs offline; promote it to `05_tests/infra/scripts/render-vector.py`.
2. **Add a geometry smoke check** for brand assets — cheap, deterministic assertions that
   would have caught this at authoring time:
   - shell bounding box is taller than wide,
   - silhouette is centred in the viewport within ±2 units,
   - bilateral symmetry IoU ≥ 90 %,
   - no layer occluded > 40 % by later layers,
   - no layer spills outside the base silhouette.
3. **Establish `.github/workflows/`** and run `assembleDebug` + L1 on push. Absence of any
   CI is a broader risk than this incident.

---

## Prevention Recommendations

### Code / Asset Changes

- Replace `ic_helmet_emblem.xml` per the *Short-term* constraints above.
- Verify all four render sites afterwards (splash 132 dp, `ic_splash_emblem` 20 % inset,
  hero 88 dp, collapsed 36 dp) — a single asset change propagates to all of them.
- Consider a `-v24` qualified variant if the detailed version is wanted at large sizes only.

### Testing

- **Do not** attempt to assert emblem correctness with Espresso `isDisplayed()` — it is
  presence-only and will pass on a blank drawable. This is the same class of mistake as the
  Espresso/main-thread finding recorded in `failure-patterns.md`: *an assertion that cannot
  fail when the defect is present is not coverage.*
- Prefer an offline **render-and-measure** unit check over an instrumented test — it is
  faster, deterministic, and needs no emulator.
- Falsify any new asset check by pointing it at the current broken emblem: it **must** fail.

### Documentation

- Add a **"Brand Asset Geometry"** failure pattern to `atlas/failure-patterns.md`
  (first of its kind — static-asset defects are currently unmodelled).
- Add asset correctness as a failure mode under *Home Brand Hero* and *App Startup* in
  `atlas/components.md`.
- Record the intended emblem design (proportions, centre line, colour roles) so "correct"
  becomes verifiable rather than subjective.

### Process

- Require a rendered preview for any PR touching `res/drawable/*.xml`.
- Treat brand assets as reviewable artefacts, not configuration.

---

## Notes on the Original Incident Report

Two statements in `11_helmet_emblem_deformed.md` were tested and are **not supported**:

| Report statement | Finding |
|------------------|---------|
| *"Misaligned path coordinates between these layers are the most likely cause"* | Contradicted — 7 of 8 layers show 0.0 % spill outside the shell. |
| *"The RCA should nevertheless confirm that no `scaleType` / fixed-size mismatch in the layouts contributes to the distortion"* | Confirmed: it does not. All containers are square, viewport is square, default `fitCenter` applies. |

One statement is **confirmed**: *"this is expected to be a single-asset defect"* — correct.
The fix is a single file, `app/src/main/res/drawable/ic_helmet_emblem.xml`.

---

## Addendum — Resolution Diverged From the Recommendation (2026-08-11)

This RCA recommended **redrawing the vector from shared construction geometry**
(a centre line, a silhouette reference, and a symmetry constraint). That is not
what shipped, and the divergence is worth recording because it changes which
prevention control actually holds.

**What happened**: the project owner supplied their own helmet artwork as a
raster PNG. Redrawing a vector to imitate that artwork would have reintroduced
the exact failure mode this RCA identified — hand-authored geometry with no
verifiable relationship to a reference. Using the raster directly removes the
authoring step altogether.

**What this changes:**

| Control | As recommended | As implemented |
|---------|----------------|----------------|
| Construction geometry | Centre line + silhouette in the vector source | Not applicable — no hand authoring remains |
| Symmetry constraint | Enforce bilateral symmetry | **Dropped.** The supplied artwork is a side profile and is legitimately asymmetric |
| Composite render review | Manual, before merge | Automated: `BrandAssetGeometryTest` measures the artwork itself |
| Reproducibility | Implicit in the vector source | Explicit: `brand-asset.py build` regenerates every bucket bit-for-bit from `docs/brand/helmet_source.png` |

**The symmetry constraint was deliberately dropped.** The RCA measured the old
shell at 94.5% bilateral symmetry and concluded asymmetry was *not* the defect;
asserting symmetry would therefore have added a constraint that this incident
never justified, and it would reject the new artwork outright.

**What the gate actually asserts** — the constraints that were measurably
violated by the old asset:

| Assertion | Old emblem | New emblem |
|-----------|-----------|------------|
| Content aspect 1.00 ± 0.05 | **0.866 — FAIL** | 1.020 — pass |
| Square canvas | pass | pass |
| Content centred within 3% | fail (shell centre y=54 vs 60) | 0.0% |
| Transparent border | pass | pass |
| Legible at 36dp | pass | pass |

`BrandAssetGeometryTest.gateRejectsTheLegacyDeformedEmblem` runs the identical
assertions against the emblem from `7350eb3`, preserved at
`app/src/test/resources/brand/legacy_deformed_emblem.png`, and requires them to
fail. This is a direct response to the systemic finding below: the existing
`isDisplayed()` assertions could not fail while the defect was present, so a
gate that is never proven to fail is not treated as coverage.

**Systemic finding still open**: the repository has no CI
(`.github/workflows/` does not exist), so the new L1 gate only runs when
someone runs it. Automating it remains unaddressed.
