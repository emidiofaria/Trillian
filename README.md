# Trillian

### *"I Swear I Didn't Write a Single Line"*

> An Android Driving Coach built entirely with an AI-first approach — spec, code, tests, ops, docs — with ZERO human-written code.  using specification-driven development, Test Driven Development and a V-model-inspired verification process. 

[![Build](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Tests](https://img.shields.io/badge/tests-322%20passing-brightgreen)]()
[![Human Code](https://img.shields.io/badge/human%20code-0%20lines-blueviolet)]()
[![Requirements](https://img.shields.io/badge/SRS-245%20requirements-blue)]()
[![SDLC](https://img.shields.io/badge/SDLC-AI--First-orange)]()

---

## The Audacious Premise

> *"Can AI handle the entire software development lifecycle — requirements, design, implementation, testing, operations, documentation, incident response — without a human touching the code?"*
>
> *Great question. We're finding out.*

This is **Trillian** — named after the Hitchhiker's Guide character, because naming your experimental AI project after sci-fi is basically mandatory. It also works as "Trace McMillan" — fitting for an app that traces you around a racing circuit.

**The experiment:** Build a production-grade Android application with a Node.js backend, PostgreSQL database, Firebase auth, Azure cloud hosting, and an AI coaching engine — **with zero lines of human-written code**. Not "mostly AI". Not "AI-assisted". **Zero. Lines.** No IDE installed. The keyboard is for prompts only and human-on-the-wheel engineering capacity.

**The goal:** Prove we've reached the next software abstraction layer:

| Era | Abstraction | Enabler |
|-----|-------------|---------|
| 1940s | Machine Code → Assembly | Assembler |
| 1960s | Assembly → High-Level Languages | Compiler |
| 1990s | HLL → Managed Frameworks | Virtual Machine + GC |
| **2026** | **Frameworks → Specification-Driven AI** | **LLM + Structured Process** |

Welcome to the experiment.

---

## What is Trillian?

**Trillian** is a track day companion app. Picture this:

1. You show up at a racing circuit with your car and your phone
2. You finger-draw a start/finish line on a map (like signing a very sporty cheque)
3. You press record and drive
4. The app captures GPS + IMU telemetry at 10Hz
5. After the session, data uploads to the cloud
6. **AI analyzes your laps** and generates personalized coaching:

> *"You lost 0.4s braking into Turn 3. Your throttle application in Sector 2 is inconsistent. Lap 7 was your best — 0.2s faster on the hairpin exit."*

Think of it as **Strava for track days, with a very opinionated AI in the passenger seat**.

After a session you get four tabs: **Laps** (times and deltas), **Coach**
(what to fix next), **Chart** (speed traces) and **Analysis** — a track
engineer's debrief with a rendered map of the circuit coloured by speed, braking
zones marked in red, numbered corners with apex speeds, and per-zone g-forces.
All of it computed on the phone, so it works with no signal at the track.

V1 is post-session coaching only — no real-time alerts, because a push notification saying "BRAKE NOW" at 150km/h is less a feature and more a lawsuit.

**On scope:** steps 5 and 6 above describe the finished system. V1 as it stands
is deliberately **offline-first** — recording, lap detection and the whole
Analysis debrief run on-device with no server. The cloud upload, hosted AI
coaching and cross-device history are **deferred to V2**, and the 55
requirements that depend on a backend are labelled as such in every test report
rather than quietly counted as untested.

---

## The 3½ Rules

This experiment has rules. They're simple. They're strict. They occasionally make you want to cry.

### Rule 1: Never Touch the Code 🚫⌨️

The human interacts only through AI. No sneaky edits to a Kotlin file. No "just this once" fix at 11pm. If it's broken, you write a prompt, file an incident, or invoke an agent.

**The keyboard is for thinking out loud to a machine — not for patching things manually like a normal person.**

### Rule 2: Spec-Driven Development 📋

Nothing gets built without a specification first. The [System Requirements Specification](01_requirements/DrivingCoach_SRS_v1.md) is the constitution. Every feature, every API endpoint, every database field traces back to a numbered requirement.

`UM-01` says registration requires email + password + display name. If the code doesn't implement `UM-01`, the code is wrong — not the spec.

**The spec is always right. The spec is basically a deity.**

### Rule 3: Test-Driven Development ✅

Tests are not optional, not an afterthought, and definitely not "something you get around to later." Unit tests and integration tests are part of every implementation phase.

**The test suite is the immune system.**

### Bonus Rule (½): V-Model 🔺

The classic aerospace systems engineering model from the 1980s:
- Requirements define acceptance tests (top)
- Design defines integration tests (middle)
- Code defines unit tests (bottom)

All **133 human system acceptance tests** were derived from the SRS *before the app existed*. That's the methodology.

---

## The Stack

| Layer | Technology |
|-------|------------|
| **Android App** | Kotlin, SDK 26+, Hilt DI, Room, WorkManager, Retrofit |
| **Backend** | Node.js 20, TypeScript 5.x, Express |
| **Cloud** | Microsoft Azure (App Service + Blob Storage + PostgreSQL) |
| **Auth** | Firebase Authentication |
| **AI Coach** | Anthropic Claude (claude-sonnet-4) |

245 requirements. 10 architecture decisions. Two full test suites. 133 human acceptance test cases ready for a real track day.

**It's not a toy. It's a real system. And it was built entirely by humans "driving" AI.**

---

## The AI Team

No experiment runs on a single mind. This one uses five distinct AI roles:

### 🏗️ The Architect — Claude Sonnet

Handed a napkin-sketch product idea and told to turn it into a 117-requirement SRS. The human wrote the vision in plain English; the AI wrote the specification with numbered requirements, locked architecture decisions, and enough edge case coverage to make a QA engineer emotional.

*It thought of things the human hadn't — like what happens when the Firebase token expires mid-upload at 200km/h.*

### 👷 The Builder — GitHub CLI + Opus 4.5

The engine room. Fed the SRS plus a detailed implementation plan, it built the app phase by phase — scaffold, database layer, telemetry service, backend, UI, API client, test suite — across 10 structured phases.

*Output: ~50 Kotlin files, ~30 XML layouts, ~15 TypeScript files, 84+ automated tests.*

### 🔍 RCA Agent — The Doctor

**Location:** [`.github/agents/RCA_agent.md`](.github/agents/RCA_agent.md)

The incident specialist. Evidence-first reasoning — no "probably" or "in my experience", only "the log shows" and "the stack trace indicates". Rigorous to the point of being slightly pedantic.

```
FORBIDDEN: "The crash was probably caused by X"
REQUIRED:  "Log line Y at timestamp Z shows X occurred before crash"
```

*That is a feature, not a bug, when you're debugging a SecurityException at 8am on a Saturday.*

### 🛠️ SW_dev Agent — The Senior Developer

**Location:** [`.github/agents/SW_dev_agent.md`](.github/agents/SW_dev_agent.md)

Plan-first methodology. Before ANY implementation:
1. Loads system context (Atlas, SRS, User Manual) — **mandatory bootstrap**
2. Creates a structured plan
3. Presents plan to user for validation
4. **Obtains explicit approval before proceeding**
5. Implements, tests, documents

*Behaves like a senior production engineer, not a code generator.*

### 📚 Docs Sync — The Librarian

**Location:** [`.github/skills/trillian-docs-sync.md`](.github/skills/trillian-docs-sync.md)

Keeps documentation synchronized with code changes. Updates:
- Atlas architecture docs (`SkunkOps/atlas/`)
- Requirements (`01_requirements/`)
- User Manual (`docs/USER_MANUAL.md`)
- Acceptance tests (`05_tests/L4_SYS5_acceptance/`)

*Because documentation that doesn't match the code is worse than no documentation.*

---

## The Workflow

Every improvement — defect or feature — flows through the same pipeline:

```
┌─────────────┐     ┌─────────────┐
│  🐛 Defect  │     │  ✨ Feature │
└──────┬──────┘     └──────┬──────┘
       │                   │
       ▼                   │
┌─────────────┐            │
│  RCA Agent  │            │
│  Investigate│            │
│  Diagnose   │            │
│  Document   │            │
└──────┬──────┘            │
       │                   │
       └─────────┬─────────┘
                 ▼
┌─────────────────────────────────┐
│  SW_dev Agent                   │
│                                 │
│  1. Load context (Atlas, SRS)  │
│  2. Discuss idea with user      │
│  3. Propose plan                │
│  4. 👤 USER APPROVES            │
│  5. Implement                   │
│  6. Write tests                 │
│  7. Update docs (trillian-sync) │
│  8. Build & verify              │
└─────────────────────────────────┘
                 ▼
           ✅ Done (traceable)
```

**The human's role:** System Architect / Product Owner. Decides, approves, validates.

---

## Current Status

| Dimension | Status |
|-----------|--------|
| SRS (245 requirements) | ✅ Approved, locked |
| Android App Build | ✅ Builds successfully |
| Backend Build | ✅ Builds successfully |
| L1 unit tests | ✅ 262 / 262 passing |
| L2 integration tests | ⚠️ 60 / 89 — 29 sit behind class-level `@Ignore` |
| Requirements coverage (V1) | ⚠️ 68 / 190 claimed by an automated test (36%) |
| Scope deferred to V2 | 55 requirements need a backend that V1 does not build |
| Human Acceptance Tests (133) | ✅ Documented, ready for execution |
| Emulator validation | ✅ Laps detected, coaching works |
| Real device testing | ✅ One crash found, one crash fixed |
| Track day validation | 🔜 Pending |
| Backend on Azure | 🔜 Pending deployment |
| Human code written | **0 lines** |

The two ⚠️ rows are deliberate. Every release ships a generated
[HTML test report](05_tests/Test_Strategy.md#7-test-evidence-and-releases)
alongside its APK, and that report is built to make gaps *harder* to ignore than
to state — it counts `@Test` in the Kotlin source rather than trusting the run,
which is precisely how those 29 silent tests were found. A number that flatters
us is worth less than one we can defend.

---

## Project Structure

```
Trillian/
├── .github/
│   ├── agents/           # AI agent definitions (RCA, SW_dev)
│   └── skills/           # AI skills (docs-sync)
├── 00_prompts/           # Prompt engineering artifacts
├── 01_requirements/      # SRS and architecture decisions
├── 02_dev_process_evidences/  # Development process logs
├── 03_incidents/         # RCA reports with evidence chains
├── 04_papers/            # The Trillian Chronicles 📜
├── app/                  # Android application (Kotlin)
├── backend/              # Node.js/TypeScript API
├── docs/                 # User manual, sync reports
├── 05_tests/             # Test strategy, L4 checklists, infra scripts,
│                         #   coverage-map.tsv and scope-map.tsv
├── releases/             # v<version>-<slug>/ — APK + its test report + notes
└── SkunkOps/atlas/       # Living architecture documentation
```

---

## The Trillian Chronicles 📜

This experiment is documented in a bi-monthly paper series:

| Paper | Title | Status |
|-------|-------|--------|
| #1 | [I Swear I Didn't Write a Single Line](04_papers/01_Trilllian_AI_fully%20driven.md) | ✅ Published |
| #2 | [Before the Track Day: Four Bugs, Zero Code Typed](04_papers/02_Before_track_day.md) | ✅ Published |
| #3 | [Coaching the Coach](04_papers/03_Coaching_the_Coach.md) | ✅ Published |
| #4 | The Track Day | 🔜 Coming August 2026 |

*Read the papers. They're the real story.*

---

## Getting Started

### Prerequisites

- Android Studio (for building, not for coding — we don't do that here)
- JDK 17+
- Node.js 20+
- Firebase project (for auth)

### Build the Android App

```bash
cd Trillian
./gradlew assembleDebug
```

### Build the Backend

```bash
cd backend
npm install
npm run build
```

### Run Tests

```bash
# Everything: both levels, a Markdown + HTML report, and an offer to
# package a release. This is the documented path.
./05_tests/infra/scripts/run-all-tests.sh --start-emulator --stop-emulator

# L1 only (~30s, JVM, no device needed)
./05_tests/infra/scripts/run-all-tests.sh --level L1

# Backend tests
cd backend && npm test
```

Each run writes `05_tests/reports/RUN_<timestamp>/`, containing `TEST_REPORT.md`
and a self-contained `TEST_REPORT.html` that opens offline — no JavaScript, no
network, readable from a USB stick in a paddock with no signal.

See [Test_Strategy.md](05_tests/Test_Strategy.md) for the ASPICE level
definitions and what each level can and cannot prove.

---

## The Human's Role

| Human Does | AI Does |
|------------|---------|
| Defines the vision | Has full system knowledge |
| Brings ideas or reports defects | Proposes aligned solutions |
| Refines scope in discussion | Implements what was approved |
| **Approves the plan** | Writes tests |
| Validates the result | Updates documentation |

The approval gate is critical. No wasted implementation. No "I built the wrong thing."

---

## Lessons Learned (So Far)

1. **AI code quality = spec quality.** Vague prompt → vague result. Clear spec → working code.
2. **Sentinel values are silent lies.** Zero can mean "no data" or "actual zero." Guard accordingly.
3. **TODO comments are bugs you haven't filed yet.** Track them or watch them become incidents.
4. **The best bugs are found before production.** The emulator is cheaper than the track.
5. **Process structure forces quality.** When discovery + dev + test + docs happen in one loop, nothing gets skipped.

---

## FAQ

**Q: Did a human really write zero code?**
A: Zero. Lines. The human wrote prompts, requirements, and incident reports. The keyboard was for thinking out loud to AI, not for typing `fun` or `class`.

**Q: Is this faster than traditional development?**
A: Probably not. Is it more disciplined? Possibly. Is it more fun? Absolutely.

**Q: Why "Trillian"?**
A: Hitchhiker's Guide to the Galaxy. Also works as "Trace McMillan" for a tracing app. We're not sorry.

**Q: What happens when something breaks?**
A: Incident report → RCA Agent investigates → SW_dev Agent fixes → Tests pass → Docs updated. All traceable. No panic-patching at 2am.

---

## License

MIT — because even experiments deserve freedom.

---

<div align="center">

**Do. Not. Touch. The. Code.**

*The Trillian Chronicles — 2026*

</div>

