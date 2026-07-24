# trillian-docs-sync

Synchronizes Trillian (Driving Coach) documentation artifacts with the current codebase state. Use when you've added features, modified behavior, or refactored code and need to update documentation to match.

**Keywords:** docs, documentation, sync, update, atlas, requirements, manual, SRS, user guide, synchronize, refresh docs

---

## Artifacts to Update

When invoked, analyze the codebase and update these documentation artifacts **in detail**:

### 1. Atlas Content (`SkunkOps/atlas/`)

| File | Purpose | Update Triggers |
|------|---------|-----------------|
| `system.md` | Runtime architecture, tech stack, components | New dependencies, architecture changes, service changes |
| `components.md` | Detailed component breakdown with interfaces | New classes, modified APIs, new UI screens |
| `flows.md` | Runtime flow diagrams (async, data, user journeys) | New features, workflow changes, new integrations |
| `failure-patterns.md` | Known failure modes and mitigations | New error handling, discovered edge cases |

### 2. Requirements (`01_requirements/`)

| File | Purpose | Update Triggers |
|------|---------|-----------------|
| `DrivingCoach_SRS_v1.md` | System Requirements Specification | New features, behavior changes, removed features |

### 3. App User Manual (`docs/USER_MANUAL.md`)

| File | Purpose | Update Triggers |
|------|---------|-----------------|
| `USER_MANUAL.md` | End-user documentation with screenshots/instructions | New features, UI changes, workflow changes |

**User Manual Sections:**
- Getting Started (installation, first launch)
- Registration & Login
- Track Setup (GPS start/finish line)
- Recording a Session
- Viewing Results (laps, coaching, charts)
- Sharing & Profile
- Troubleshooting & FAQ

### 4. Test Infrastructure (`05_tests/`)

| Level | Folder | ASPICE | Update Triggers |
|-------|--------|--------|-----------------|
| L1 | `L1_SWE4_unit/` | SWE.4 | New unit tests, test patterns |
| L2 | `L2_SWE5_integration/` | SWE.5 | New integration tests, Hilt configuration |
| L3 | `L3_SWE6_qualification/` | SWE.6 | Future E2E tests |
| L4 | `L4_SYS5_acceptance/` | SYS.5 | Human test checklists, new scenarios |

| File | Purpose | Update Triggers |
|------|---------|-----------------|
| `Test_Strategy.md` | Overall test strategy | New test levels, approach changes |
| `test_strategy_execution_instructions.md` | Agent execution guide | Script changes, new commands |
| `L4_SYS5_acceptance/*_TESTS.md` | Human test checklists | Feature changes, new behaviors |

---

## Execution Workflow

### Phase 1: Codebase Analysis

1. **Scan for recent changes**:
   ```bash
   git diff --stat HEAD~10 --name-only  # Recent file changes
   git log --oneline -20                 # Recent commits
   ```

2. **Extract current architecture**:
   - Scan `app/src/main/java/com/drivingcoach/` for packages and classes
   - Parse `app/build.gradle.kts` and root `build.gradle.kts` for dependencies
   - Check `AndroidManifest.xml` for permissions, services, receivers
   - Scan `backend/` for API endpoints and data models

3. **Identify feature set**:
   - List all Fragments, ViewModels, Services
   - Map data flow: UI → ViewModel → Repository → API/DB
   - Extract all Room entities and DAOs
   - List WorkManager workers and their triggers

### Phase 2: Gap Analysis

Compare codebase state against documentation:

```
┌─────────────────────┬─────────────────────┬─────────────────────┐
│   CODEBASE STATE    │    DOCUMENTATION    │        GAP          │
├─────────────────────┼─────────────────────┼─────────────────────┤
│ Current classes     │ Documented classes  │ Missing/outdated    │
│ Current features    │ SRS requirements    │ Undocumented        │
│ Current flows       │ Flow diagrams       │ Stale diagrams      │
│ Current errors      │ Failure patterns    │ Undocumented modes  │
│ Current UI screens  │ Test checklists     │ Missing test cases  │
└─────────────────────┴─────────────────────┴─────────────────────┘
```

### Phase 3: Documentation Updates

For each artifact type, apply these update strategies:

#### Atlas Updates

**system.md**:
- Update Technology Stack table with current versions
- Update Module Structure tree
- Update Runtime Components tables
- Update External Services section
- Refresh Operational Risks based on current code

**components.md**:
- Add new component entries with full specification
- Update existing component interfaces/dependencies
- Remove deprecated components
- Update component relationship diagrams

**flows.md**:
- Update sequence diagrams for modified flows
- Add new user journey flows
- Update data flow diagrams
- Update async boundary documentation

**failure-patterns.md**:
- Add newly discovered failure patterns
- Update mitigation strategies
- Link to recent incidents if applicable

#### Requirements Updates

**DrivingCoach_SRS_v1.md**:
- Add new requirements for new features (use existing ID patterns)
- Update existing requirements if behavior changed
- Mark deprecated requirements as out-of-scope
- Update Architecture Decisions table if applicable
- Maintain traceability (IDs, versions)

#### User Manual Updates

**docs/USER_MANUAL.md**:
- Add sections for new user-facing features
- Update step-by-step instructions when workflows change
- Update screenshots references when UI changes
- Add troubleshooting entries for new error scenarios
- Keep FAQ current with common user questions
- Write in friendly, non-technical language for end users

**User Manual Structure:**
```markdown
# Driving Coach User Manual

## 1. Introduction
## 2. Getting Started
   - 2.1 Installation
   - 2.2 First Launch & Permissions
   - 2.3 Creating Your Account
## 3. Before Your Track Day
   - 3.1 Setting Up the Start/Finish Line
   - 3.2 Phone Mounting Tips
## 4. Recording a Session
   - 4.1 Starting a Recording
   - 4.2 During Your Session
   - 4.3 Stopping & Uploading
## 5. Reviewing Your Results
   - 5.1 Lap Times
   - 5.2 AI Coaching Feedback
   - 5.3 Speed Charts
## 6. Sharing & Profile
   - 6.1 Share Cards
   - 6.2 Your Statistics
## 7. Troubleshooting
## 8. FAQ
```

#### Test Documentation Updates

**Test files (10-90_*.md)**:
- Add test cases for new features
- Update preconditions if dependencies changed
- Update expected results for modified behavior
- Maintain test ID numbering scheme

---

## Output Format

After analysis and updates, **append** a new entry to `docs/SYNC_REPORT.md` (create if doesn't exist).

### SYNC_REPORT.md Structure

This is a **cumulative changelog** of all documentation syncs. Each sync adds a new entry at the TOP of the file (newest first).

```markdown
# Documentation Sync Report

Cumulative changelog of documentation synchronizations with codebase.

---

## [2026-07-15] Real Speed Chart

**Codebase Version:** v1.9-real-speed-chart  
**Trigger:** Real telemetry speed chart implementation

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| components.md | ✅ Updated | +55 lines (1 new component) |
| flows.md | ✅ Updated | +85 lines (1 new flow) |
| SRS_v1.md | ✅ Updated | +5 requirements (LC-11 to LC-15) |
| USER_MANUAL.md | ✅ Updated | Section 5.3 rewritten |

### New Requirement IDs
- LC-11, LC-12, LC-13, LC-14, LC-15

### Files Modified
```
M  SkunkOps/atlas/components.md    (+55)
M  SkunkOps/atlas/flows.md         (+85)
M  01_requirements/SRS_v1.md       (+7, -2)
M  docs/USER_MANUAL.md             (+22, -6)
```

---

## [2026-07-13] Local Lap Detection

**Codebase Version:** v1.7-local-lap-detection  
**Trigger:** Offline lap detection feature

### Summary
...
```

### Entry Template

Each sync entry should include:

1. **Date header** — `## [YYYY-MM-DD] Feature Name`
2. **Metadata** — Version, trigger
3. **Summary table** — Artifacts updated with line counts
4. **New IDs** — Any new requirement/test IDs added
5. **Files Modified** — Git-style file list
6. **Separator** — `---` between entries

### Update Workflow

1. Read existing `docs/SYNC_REPORT.md` (if exists)
2. Prepare new entry with current sync details
3. Insert new entry after the header, before previous entries
4. Write updated file

This keeps a running history while maintaining the newest-first order for easy reading.

---

## Change Visibility Requirements (per entry)

---

## Detailed Changes

### 📁 SkunkOps/atlas/system.md

**Added:**
```diff
+ | `PitStopDetector` | Service | Detects stationary periods > 30s |
+ | Pit stop detection | LOW | 30s threshold configurable |
```

**Modified:**
```diff
- | Version Name | 1.0.0 |
+ | Version Name | 1.1.0 |
```

### 📁 SkunkOps/atlas/components.md

**Added Section: PitStopDetector**
```markdown
## PitStopDetector

| Attribute | Value |
|-----------|-------|
| Package | `com.drivingcoach.service` |
| Type | Background detector |
| Trigger | GPS speed < 5 km/h for 30s |
...
```

### 📁 01_requirements/DrivingCoach_SRS_v1.md

**Added Requirements:**
| ID | Requirement |
|----|-------------|
| SR-12 | The app SHALL detect pit stops when stationary > 30 seconds |
| SR-13 | Pit stop duration SHALL be excluded from lap time calculation |
| SR-14 | The app SHALL display pit stop count in session summary |

### 📁 docs/USER_MANUAL.md

**Added Section: 4.2.1 Pit Stops**
```markdown
### Pit Stops
If you stop in the pits during your session, the app automatically detects this...
```

**Added FAQ Entry:**
```markdown
**Q: Why doesn't my pit stop show up?**
A: Pit stops are detected when you're stationary for at least 30 seconds...
```

### 📁 05_tests/L4_SYS5_acceptance/40_SESSION_RECORDING_TESTS.md

**Added Test Cases:**
| ID | Test | Expected Result |
|----|------|-----------------|
| SR-PIT-01 | Stop in pits for 45 seconds | Pit stop detected, shown in results |
| SR-PIT-02 | Brief 15-second stop | NOT detected as pit stop |
| SR-PIT-03 | Multiple pit stops | All pit stops listed separately |

---

## Files Modified

```
M  SkunkOps/atlas/system.md                          (+15, -3)
M  SkunkOps/atlas/components.md                      (+47, -0)
M  01_requirements/DrivingCoach_SRS_v1.md            (+12, -0)
M  docs/USER_MANUAL.md                               (+23, -0)
M  05_tests/L4_SYS5_acceptance/40_SESSION_RECORDING_TESTS.md (+18, -0)
```

---

## Recommendations

- [ ] Review flow diagram in `flows.md` for pit stop detection flow
- [ ] Consider adding pit stop icon to UI (design needed)
- [ ] Backend endpoint may need update for pit stop data
```

---

## Change Visibility Requirements

**ALWAYS show:**
1. **Exact diff snippets** — what was added/removed/modified
2. **Line counts** — `+N lines, -M lines` per file
3. **New IDs** — list all new requirement IDs, test case IDs
4. **Section names** — which sections were added/modified
5. **File summary** — git-style file change list at the end

**Format guidelines:**
- Use `diff` code blocks for changes
- Use tables for structured additions (requirements, tests)
- Use `+` prefix for additions, `-` for removals
- Group changes by file, then by change type

---

## Key Patterns to Detect

### Feature Detection Signals

| Signal | Indicates |
|--------|-----------|
| New Fragment + ViewModel | New UI screen |
| New Room Entity | New data model |
| New API endpoint | New backend integration |
| New WorkManager Worker | New background job |
| New permission in Manifest | New capability |
| New Hilt Module | New DI scope/component |

### Documentation Priority

1. **HIGH**: User-facing features (affects SRS + tests)
2. **MEDIUM**: Internal architecture changes (affects Atlas)
3. **LOW**: Refactoring without behavior change (minimal doc impact)

---

## Constraints

- **Preserve existing IDs**: Don't renumber existing requirement or test IDs
- **Maintain style**: Match existing document formatting and voice
- **Version tracking**: Update version/date headers where present
- **Traceability**: Maintain links between SRS IDs and test cases
- **Conservative updates**: Only update what the code evidence supports

---

## Example Invocation

User: "I just added a new pit stop timer feature to the recording screen. Update the docs."

Agent actions:
1. Analyze `RecordingFragment.kt`, `RecordingViewModel.kt` for pit stop code
2. Check for new Room entities or API calls
3. Update `system.md` components if new service added
4. Add requirements to SRS (e.g., SR-XX: Pit stop detection)
5. Add test cases to `40_SESSION_RECORDING_TESTS.md`
6. Generate sync report

---

## File Locations Reference

```
Trillian/
├── .github/copilot/skills/    # This skill
├── 01_requirements/
│   └── DrivingCoach_SRS_v1.md # System Requirements
├── docs/
│   └── USER_MANUAL.md         # End-user documentation
├── SkunkOps/atlas/
│   ├── system.md              # System overview
│   ├── components.md          # Component details
│   ├── flows.md               # Flow diagrams
│   └── failure-patterns.md    # Known issues
├── 05_tests/
│   ├── L1_SWE4_unit/          # Unit test references
│   ├── L2_SWE5_integration/   # Integration test references
│   ├── L3_SWE6_qualification/ # E2E test references (future)
│   ├── L4_SYS5_acceptance/    # Human acceptance checklists
│   │   └── *_TESTS.md
│   ├── infra/scripts/         # Test automation scripts
│   ├── reports/               # Generated test reports
│   ├── Test_Strategy.md       # Test strategy document
│   └── test_strategy_execution_instructions.md  # Agent reference
├── app/src/main/java/         # Android app code
└── backend/                   # Node.js backend
```

---

## Creating User Manual from Scratch

If `docs/USER_MANUAL.md` doesn't exist, create it by:

1. Extracting user-facing features from SRS requirements
2. Mapping each feature to step-by-step instructions
3. Writing in friendly, non-technical language
4. Organizing by user workflow (not by technical component)
5. Including troubleshooting based on `failure-patterns.md`
