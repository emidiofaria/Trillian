# L1_SWE4_unit — Unit Verification

**ASPICE Process:** SWE.4 — Software Unit Verification  
**Test Level:** Unit Tests  
**Execution Environment:** JVM (local machine)

---

## Purpose

Verify individual software units (classes, functions) in isolation using mocked dependencies.

---

## Test Location

**Actual test files are located at:**

```
app/src/test/java/com/drivingcoach/
├── data/
│   ├── repository/
│   └── source/
├── domain/
│   └── usecase/
├── presentation/
│   └── viewmodel/
├── brand/                  # Brand asset geometry gate (Incident 11)
│   ├── BrandAssetGeometryTest.kt
│   └── ArgbBitmap.kt       # Minimal PNG reader — android.jar has no javax.imageio
└── util/
```

Fixtures for the brand gate live at `app/src/test/resources/brand/`.

---

## How to Run

```bash
# From project root
./gradlew testDebugUnitTest

# Or via infrastructure script
./05_tests/infra/scripts/run-all-tests.sh --level L1
```

---

## What Gets Tested

| Component | Purpose |
|-----------|---------|
| ViewModels | State management, UI logic |
| UseCases | Business logic |
| Repositories | Data access logic (mocked sources) |
| Utilities | Helper functions, calculations |
| Brand assets | Emblem artwork geometry — measured, not rendered (UI-08, UI-09) |

### Brand Asset Gate

`BrandAssetGeometryTest` is unusual for an L1 suite: it asserts against files on
disk rather than against code. It exists because Incident 11 shipped a deformed
emblem past a full L2 suite — every assertion touching the emblem checked
`isDisplayed()`, which passes for any drawable, including a blank one.

| Test | Asserts |
|------|---------|
| `emblemMasterSatisfiesBrandGeometry` | Square canvas, content aspect 1.00 ± 0.05, centred within 3 %, transparent border, legible at 36dp |
| `gateRejectsTheLegacyDeformedEmblem` | The **same** assertions applied to the emblem from `7350eb3` must **fail** |
| `emblemDensityBucketsAreCompleteAndCorrectlySized` | All 5 WebP buckets present at 132/198/264/396/528 px |
| `noConflictingVectorEmblemRemains` | No same-named `.xml` beside the bitmaps |

**Why the falsification test matters:** a gate that has never been observed to
fail is indistinguishable from a no-op. If someone weakens the thresholds,
`gateRejectsTheLegacyDeformedEmblem` fails and says so explicitly.

To iterate on artwork outside Gradle, or to regenerate the density buckets from
source, use `05_tests/infra/scripts/brand-asset.py` (needs `pillow` + `numpy` in
a venv — it is not part of the app build).

---

## Report Output

Results are included in the consolidated test report at:
```
05_tests/reports/TEST_REPORT_[timestamp].md
```

---

## ASPICE Traceability

| ASPICE Requirement | Implementation |
|--------------------|----------------|
| SWE.4.BP1 | Unit test specification → Test classes |
| SWE.4.BP2 | Unit test execution → `./gradlew testDebugUnitTest` |
| SWE.4.BP3 | Results analysis → Test report L1 section |
