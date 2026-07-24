# Skill: Test Coverage Checker

> **Purpose:** Analyze test coverage against requirements, identify gaps by ASPICE level and test type  
> **Output:** Markdown tables in chat + auto-update `TRACEABILITY_MATRIX.md`  
> **Trigger:** "Run test coverage checker", "Check test coverage", "Analyze test gaps"

---

## Overview

This skill performs automated analysis of test coverage against SRS requirements:

1. Reads requirements + test strategy together
2. Inventories all tests (L1/L2/L3)
3. Classifies tests by type (Happy/Boundary/Edge/Stress/Negative)
4. Maps tests to requirements using heuristics
5. Identifies coverage gaps
6. Generates prioritized action plan
7. Updates `TRACEABILITY_MATRIX.md`

---

## Phase 1: Context Loading

**⚠️ MANDATORY: Load requirements AND test strategy TOGETHER**

### 1.1 Load Requirements + Test Strategy

Execute these view commands in parallel:

```bash
# Requirements - source of truth for all requirement IDs
view 01_requirements/DrivingCoach_SRS_v1.md

# Test Strategy - ASPICE levels, coverage goals, test locations
view 05_tests/Test_Strategy.md
```

**Extract from Test Strategy:**

| Level | Location | ASPICE | Coverage Goal |
|-------|----------|--------|---------------|
| L1 | `app/src/test/` | SWE.4 | 80%+ business logic |
| L2 | `app/src/androidTest/` | SWE.5 | All critical Android components |
| L3 | `app/src/androidTest/e2e/` | SWE.6 | Happy path + top 5 error scenarios |
| L4 | `05_tests/L4_SYS5_acceptance/` | SYS.5 | 100% SRS requirements (manual) |

### 1.2 Scan Test Files

```bash
# L1 Unit Tests
glob app/src/test/**/*Test*.kt

# L2 Integration Tests
glob app/src/androidTest/**/*Test*.kt

# L3 Qualification Specs
glob 05_tests/L3_SWE6_qualification/**/*.md
```

---

## Phase 2: Requirement Extraction

### 2.1 Parse Requirement IDs

Scan SRS for all patterns matching `XX-NN` where:
- `XX` = 2-3 letter prefix
- `NN` = 2-digit number

### 2.2 Requirement Domains

| Prefix | Domain | Section |
|--------|--------|---------|
| AD | Architecture Decisions | §1 |
| UM | User Management | §2 |
| ON | Onboarding | §3 |
| TS | Track Setup | §4 |
| SR | Session Recording | §5 |
| TC | Telemetry Capture | §6 |
| TU | Telemetry Upload | §7 |
| LD | Lap Detection | §8 |
| LC | Lap Comparison | §9 |
| AI | AI Coaching | §10 |
| OC | Offline Coaching | §10.1 |
| DP | Driver Progression | §11 |
| SM | Session Management | §11.1 |
| SH | Share | §12 |
| BE | Backend API | §13 |
| NF | Non-Functional | §15 |
| SEC | Security | §16 |

### 2.3 Output: Requirement Count Table

```markdown
| Domain | Prefix | Count | Priority |
|--------|--------|-------|----------|
| User Management | UM | 19 | Core |
| Track Setup | TS | 15 | Core |
| Session Recording | SR | 11 | Core |
| Telemetry Capture | TC | 12 | Core |
| Telemetry Upload | TU | 13 | Core |
| Lap Detection | LD | 13 | Core |
| Lap Comparison | LC | 15 | Feature |
| AI Coaching | AI | 14 | Feature |
| Offline Coaching | OC | 10 | Feature |
| Session Management | SM | 10 | Feature |
| Onboarding | ON | 8 | UX |
| Driver Progression | DP | 6 | UX |
| Share | SH | 6 | UX |
| Non-Functional | NF | 13 | Quality |
| Security | SEC | 9 | Quality |
| **TOTAL** | — | **~160** | — |
```

---

## Phase 3: Test Inventory

### 3.1 List Test Files

For each test file found, read and extract:
- File path
- Test class name
- All `@Test` annotated methods

### 3.2 Output: Test Inventory Table

```markdown
| Level | Test Class | File | Test Count |
|-------|------------|------|------------|
| L1 | OfflineCoachingEngineTest | `app/src/test/.../coaching/` | 30 |
| L1 | GeoUtilsTest | `app/src/test/.../util/` | 12 |
| L2 | TelemetryForegroundServiceTest | `app/src/androidTest/.../service/` | 6 |
| ... | ... | ... | ... |
```

---

## Phase 4: Test Type Classification

### 4.1 Primary: Naming Pattern Detection

Scan test method names for keywords:

| Pattern Keywords | Test Type | Priority |
|------------------|-----------|----------|
| `boundary`, `exactly`, `threshold`, `limit`, `minimum`, `maximum`, `edge value` | **Boundary** | 1 |
| `edge`, `corner`, `special`, `unusual`, `rare` | **Edge Case** | 2 |
| `stress`, `benchmark`, `performance`, `load`, `18000`, `large` | **Stress/Perf** | 3 |
| `invalid`, `reject`, `fail`, `error`, `empty`, `null`, `missing`, `malformed`, `negative`, `not`, `no `, `without` | **Negative** | 4 |
| *(none of above)* | **Happy Path** | 5 |

### 4.2 Fallback: Content Analysis

If naming is ambiguous, read test body and check:

| Content Pattern | Indicates |
|-----------------|-----------|
| `assertEquals(exactValue, ...)` with boundary numbers | Boundary |
| `assertThrows`, `shouldThrow`, `expectException` | Negative |
| Loop with `N > 1000`, timing assertions | Stress |
| Multiple condition checks, `when` blocks | Edge Case |
| Single straightforward assertion | Happy Path |

### 4.3 Output: Test Classification Table

```markdown
| Test Class | Method | Type |
|------------|--------|------|
| OfflineCoachingEngineTest | `generateInsights returns 3 insights` | Happy |
| OfflineCoachingEngineTest | `only 1 lap → empty list` | Boundary |
| OfflineCoachingEngineTest | `GPS noise > 350 km/h filtered` | Edge |
| TelemetryFileWriterTest | `18000 samples under 100ms` | Stress |
| HomeViewModelTest | `empty name rejected` | Negative |
```

---

## Phase 5: Requirement-to-Test Mapping

### 5.1 Heuristic Rules

Map test classes to requirement domains using these rules:

| Test Class Pattern | Maps To | Requirements |
|--------------------|---------|--------------|
| `*CoachingEngine*` | Offline Coaching | OC-01 → OC-10 |
| `*GeoUtils*` | Track Setup, Lap Detection | TS-08, LD-04 |
| `*TelemetryFileWriter*` | Telemetry Capture/Upload | TC-06, TC-07, NF-07 |
| `*TelemetryForegroundService*` | Session Recording | SR-04 → SR-11 |
| `*UploadWorker*` | Telemetry Upload | TU-02 → TU-08 |
| `*HomeViewModel*` | Driver Progression, Session Mgmt | DP-01 → DP-06, SM-01 → SM-10 |
| `*TrackSetup*` | Track Setup | TS-01 → TS-15 |
| `*Recording*` | Session Recording | SR-01 → SR-11 |
| `*Login*`, `*Register*`, `*Auth*` | User Management | UM-01 → UM-19 |
| `*Onboarding*` | Onboarding | ON-01 → ON-08 |
| `*SessionResult*`, `*LapComparison*` | Lap Comparison | LC-01 → LC-15 |
| `*LapDetector*` | Lap Detection | LD-01 → LD-13 |
| `*Share*` | Share | SH-01 → SH-06 |
| `*Api*`, `*Network*` | Backend API | BE-01 → BE-15 |

### 5.2 Method-Level Hints

Also scan test method names for requirement hints:

| Method Contains | Additional Mapping |
|-----------------|-------------------|
| `gps status`, `gps locked` | SR-05, TS-03, TS-04 |
| `elapsed`, `timer` | SR-04 |
| `foreground`, `notification` | SR-09, SR-10 |
| `haversine`, `distance` | TS-08 |
| `intersection`, `crossing` | LD-04 |
| `sector` | LD-10, OC-03, OC-08 |
| `consistency` | AI-13, OC-07 |
| `top speed` | OC-05, OC-06 |

---

## Phase 6: Coverage Matrix Build

### 6.1 Per-Domain Summary

```markdown
## Coverage Summary by Domain

| Domain | Total Reqs | L1 ✅ | L2 ✅ | L3 ✅ | Coverage |
|--------|-----------|-------|-------|-------|----------|
| User Management (UM) | 19 | 0 | 0 | 0 | 0% |
| Track Setup (TS) | 15 | 5 | 3 | 0 | 33% |
| Session Recording (SR) | 11 | 2 | 4 | 0 | 55% |
| Telemetry Capture (TC) | 12 | 4 | 0 | 0 | 33% |
| Telemetry Upload (TU) | 13 | 3 | 0 | 0 | 23% |
| Lap Detection (LD) | 13 | 2 | 0 | 0 | 15% |
| Lap Comparison (LC) | 15 | 0 | 0 | 0 | 0% |
| AI/Offline Coaching | 24 | 10 | 0 | 0 | 42% |
| Session Management (SM) | 10 | 4 | 0 | 0 | 40% |
| Share (SH) | 6 | 0 | 0 | 0 | 0% |
| Non-Functional (NF) | 13 | 2 | 0 | 0 | 15% |
| **TOTAL** | **~160** | — | — | — | **~23%** |
```

### 6.2 Test Type Coverage by Domain

```markdown
## Test Type Coverage

| Domain | Happy | Boundary | Edge | Stress | Negative |
|--------|:-----:|:--------:|:----:|:------:|:--------:|
| TS | ✅ | ⚠️ | ✅ | ❌ | ✅ |
| SR | ✅ | ❌ | ❌ | ❌ | ⚠️ |
| TC | ✅ | ❌ | ✅ | ✅ | ✅ |
| OC | ✅ | ✅ | ✅ | ❌ | ✅ |
| LD | ✅ | ❌ | ✅ | ❌ | ✅ |
| SM | ✅ | ✅ | ❌ | ❌ | ✅ |

Legend: ✅ Good | ⚠️ Partial | ❌ None
```

---

## Phase 7: Gap Analysis

### 7.1 Identify Gaps

**Gap Types:**

| Gap Type | Detection |
|----------|-----------|
| **Uncovered Domain** | No tests map to domain |
| **Missing Level** | UI/Service req has L1 but no L2 |
| **Missing Test Type** | Threshold req has no Boundary test |

### 7.2 Gap Prioritization

| Priority | Criteria |
|----------|----------|
| 🔴 **High** | Core domain (LD, SR, TC, TU) + no coverage |
| 🟡 **Medium** | Feature domain + partial coverage |
| 🟢 **Low** | UX/Quality domain or well-covered |

### 7.3 Output: Gap Table

```markdown
## 🚨 Coverage Gaps

| Domain | Gap Type | Specific Gap | Priority |
|--------|----------|--------------|----------|
| UM | Uncovered | No auth tests | 🔴 |
| ON | Uncovered | No permission tests | 🔴 |
| LD | Missing Tests | LD-05→13 not tested | 🔴 |
| LC | Uncovered | No comparison tests | 🟡 |
| TS | Missing Type | No boundary tests for TS-09 | 🟡 |
| SH | Uncovered | No share tests | 🟢 |
```

---

## Phase 8: Action Plan Generation

### 8.1 Recommended Tests Table

```markdown
## 📋 Action Plan

### L1 Tests to Create

| # | Test Class | Requirements | Test Types Needed | Priority | Effort |
|---|------------|--------------|-------------------|----------|--------|
| 1 | `LocalLapDetectorTest` | LD-04 → LD-13 | Happy, Boundary | 🔴 | 2h |
| 2 | `LoginViewModelTest` | UM-07 → UM-15 | Happy, Negative | 🔴 | 1.5h |
| 3 | `RegisterViewModelTest` | UM-01 → UM-06 | Happy, Negative | 🟡 | 1h |
| 4 | `SessionResultViewModelTest` | LC-01 → LC-06 | Happy, Boundary | 🟡 | 1.5h |
| 5 | `ConsistencyScoreTest` | AI-13 | Boundary | 🟢 | 0.5h |

### L2 Tests to Create

| # | Test Class | Requirements | Test Types Needed | Priority | Effort |
|---|------------|--------------|-------------------|----------|--------|
| 1 | `OnboardingFragmentTest` | ON-01 → ON-08 | Happy, Negative | 🔴 | 2h |
| 2 | `LoginFragmentTest` | UM-07 → UM-11 | Happy, Negative | 🔴 | 2h |
| 3 | `SessionResultFragmentTest` | LC-01 → LC-09 | Happy | 🟡 | 2h |

### Boundary Tests to Add (Existing Classes)

| Test Class | Missing Boundary Test | Requirement |
|------------|----------------------|-------------|
| `TrackSetupViewModelTest` | `distance exactly 3m is valid` | TS-09 |
| `TrackSetupViewModelTest` | `distance 2.99m is invalid` | TS-09 |
| `GeoUtilsTest` | `accuracy exactly 10m` | TS-04 |
```

### 8.2 Impact Summary

```markdown
## Impact Summary

| Metric | Current | After L1 | After L1+L2 |
|--------|---------|----------|-------------|
| Test Files | 9 L1, 4 L2 | 14 L1, 4 L2 | 14 L1, 7 L2 |
| Reqs Covered | ~36 | ~80 | ~110 |
| Coverage | ~23% | ~50% | ~70% |
```

---

## Phase 9: Update Traceability Matrix

### 9.1 Read Current Matrix

```bash
view 01_requirements/TRACEABILITY_MATRIX.md
```

### 9.2 Update Coverage Summary

Replace the `## Coverage Summary` section with current analysis data.

### 9.3 Update Matrix Rows

For each requirement with test coverage, update:

| Column | Value |
|--------|-------|
| Test Level | L1 / L2 / L3 / Manual |
| Test Location | Test class and method |
| Status | ✅ / 📋 / ⚠️ / ❌ |

### 9.4 Write Updated File

```bash
edit 01_requirements/TRACEABILITY_MATRIX.md
# Replace content with updated matrix
```

---

## Output Format

When skill completes, output these sections in order:

1. **📊 Coverage Summary by Domain** (table)
2. **🔬 Test Type Coverage** (table)
3. **🚨 Coverage Gaps** (table)
4. **📋 Action Plan** (tables for L1, L2, boundary tests)
5. **✅ Traceability Matrix Updated** (confirmation)

---

## Example Invocation

```
User: "Run test coverage checker"

Agent:
1. Reads skill file
2. Executes Phase 1-9
3. Outputs:

📊 COVERAGE SUMMARY BY DOMAIN

| Domain | Total | L1 ✅ | L2 ✅ | Coverage |
|--------|-------|-------|-------|----------|
| User Management | 19 | 0 | 0 | 0% |
| Track Setup | 15 | 5 | 3 | 33% |
...

🔬 TEST TYPE COVERAGE

| Domain | Happy | Boundary | Edge | Stress | Negative |
|--------|-------|----------|------|--------|----------|
| TS | ✅ | ⚠️ | ✅ | ❌ | ✅ |
...

🚨 COVERAGE GAPS (6 High Priority)

| Domain | Gap | Priority |
|--------|-----|----------|
| UM | No auth tests | 🔴 |
...

📋 ACTION PLAN

L1 Tests to Create: 5
L2 Tests to Create: 3
Boundary Tests to Add: 3

✅ TRACEABILITY_MATRIX.md updated
```

---

## Maintenance

- Update heuristic mappings when new features are added
- Review test type patterns if naming conventions change
- Run after major feature completion to track progress

---

*Skill version: 1.0 | Created: 2026-07-22*
