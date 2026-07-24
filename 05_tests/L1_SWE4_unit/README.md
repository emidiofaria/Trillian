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
└── util/
```

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
