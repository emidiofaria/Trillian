# Trillian Chronicles — Paper #5
## "Professionalizing the Hobby: An Aerospace-like Test Framework for a Go-Kart App"

**Series:** The Fully AI-Driven Software Development Experiment  
**Issue:** #5 — The Quality Infrastructure  
**Date:** August 2026  
**Author:** The Human Who Still Hasn't Touched the Keyboard (but has opinions about test levels)

---

> *"Why would you build aerospace-grade test infrastructure for a go-kart timing app?"*
>
> *Because if it's worth building, it's worth building right. Also, I've seen what happens when you don't.*

---

## Prologue: Ghosts of Missions Past

Years ago, I worked in the space/aerospace industry. The standard was **DO-178C** — the aviation industry's way of saying: "Prove your software works. Prove it again. Document everything." It was rigorous. It was exhausting. It was also *right*.

Fast forward to 2026. I'm building a go-kart driving coach app. The stakes are laughably lower. And yet.

**Here's a secret:** I want to put this app on **Google Play**. Free. For everyone. Which means strangers will download it — strangers with phones I've never tested, doing things I never imagined, leaving one-star reviews if it crashes once.

If Trillian goes public, it needs to be bulletproof.

Papers #1-4 asked: *Can AI build an app?* Paper #5 asks: **Can AI build the infrastructure that makes apps reliable?**

> **How you build matters more than what you build.**

### The Rules (For Those Just Joining Us)

**Rule 1: Never Touch the Code.** Prompts only. No IDE.  
**Rule 2: Spec-Driven.** Every feature traces to a requirement.  
**Rule 3: Test-Driven.** Tests are the immune system.  
**Bonus: V-Model.** Requirements define tests before code exists.

The test infrastructure was built under these same rules. Still zero lines of human code.

---

## Chapter 1: The Problem — Ad-Hoc Testing

Before this infrastructure, running tests meant: multiple commands, manual emulator management, scattered HTML reports, and no consolidated view. Every test run was an adventure.

The moment you want to run tests before every merge, know if a change broke something, or sleep peacefully before a Play Store release — you need *infrastructure*.

---

## Chapter 2: ASPICE Test Levels — Standing on Standards

### The Lineage

In aerospace, **DO-178C** defines *Design Assurance Levels* (**DAL**) for software. In automotive, **ISO 26262** establishes an equivalent safety classification with **ASIL** levels, while **Automotive SPICE (ASPICE)** evaluates process capability—with *ASPICE* deriving directly from **ISO/IEC 15504** (now the **ISO/IEC 33000** series).

The core idea: different types of testing verify different types of correctness. These map to a **V-Model**:

```
Requirements (SRS)  ←───────→  L4: System Qualification (SYS.5)
        │                              ↑
        ▼                              │
Architecture        ←───────→  L3: SW Qualification (SWE.6)
        │                              ↑
        ▼                              │
Detailed Design     ←───────→  L2: Integration Test (SWE.5)
        │                              ↑
        ▼                              │
Implementation      ←───────→  L1: Unit Verification (SWE.4)
```

You design top-down. You verify bottom-up. This isn't bureaucracy — it's *compound confidence*.

### Trillian's Test Levels

| Level | ASPICE | What It Verifies | Execution |
|-------|--------|------------------|-----------|
| **L1** | SWE.4 | Business logic: lap detection, coaching, geo math | JVM (6 seconds) |
| **L2** | SWE.5 | Android components: services, Room DB, UI | Emulator |
| **L3** | SWE.6 | Complete flows with simulated GPS | Emulator + data |
| **L4** | SYS.5 | Real-world validation | Human at track |

The folder structure carries these references: `L1_SWE4_unit/`, `L2_SWE5_integration/`, etc. When someone asks "what does L2 mean?" — the answer is in the folder name.

---

## Chapter 3: The Emulator Factory

Running Android tests requires a device. Emulators are the answer — but painful to manage manually.

The solution: **six shell scripts** turning chaos into one command.

| Script | Purpose |
|--------|---------|
| `setup-emulator.sh` | Install AVD (idempotent, KVM detection) |
| `start-emulator.sh` | Launch headless with boot detection |
| `stop-emulator.sh` | Graceful shutdown |
| `run-all-tests.sh` | Master orchestrator: L1 + L2 + report |
| `generate-report.sh` | ASPICE-aligned markdown report |

**Before:** 7 commands, 3 waits, 2 HTML reports to cross-reference.

**After:**
```bash
./05_tests/infra/scripts/run-all-tests.sh --start-emulator --stop-emulator
```

One command. ~3 minutes. Coffee-compatible. Even AI likes structured automation.

---

## Chapter 4: Teaching the AI — The Test Level Gate

A problem emerged:

> **Human:** "Run the tests."  
> **AI:** *Runs L1 only. Ignores L2.*

The AI guessed wrong because "tests" is ambiguous.

**Solution:** The SW_dev agent now has a **Test Level Selection Gate**:

| Request | Behavior |
|---------|----------|
| "Run tests" (ambiguous) | **ASK:** L1, L2, or both? |
| "Run L1" (explicit) | **EXECUTE** directly |
| "Run L2" (explicit) | **EXECUTE** with emulator |

The agent doesn't guess — it asks. This is the meta-insight: **AI doesn't just write code — it learns process**.

---

## Chapter 5: The Payoff — AI Built This Too

```bash
./05_tests/infra/scripts/run-all-tests.sh --start-emulator --stop-emulator

# ✅ L1_SWE4_unit: 92/92 passed (6.3s)
# ✅ L2_SWE5_integration: 4/4 passed (10.7s)
# Report: 05_tests/reports/TEST_REPORT_2026-07-22_22-30-07.md
```

When someone asks "is the app tested?" — the answer is a link, not a story.

**And here's the thesis:** The test infrastructure — 6 scripts, 3,700+ lines, folder structure, agent updates — was **built by AI** under the same rules as the app.

| Artifact | Lines | Author |
|----------|-------|--------|
| All 6 scripts | ~1,400 | AI |
| `Test_Strategy.md` | 352 | AI |
| Agent updates | 60+ | AI |

The human provided requirements and approval. The AI provided implementation.

**The experiment isn't just "can AI build an app?" It's "can AI build the *capability* to build apps reliably?"**

Answer: Yes.

---

## The Scoreboard

| Metric | Value |
|--------|-------|
| Test Levels Implemented | 4 (L1-L4, ASPICE-aligned) |
| Automation Scripts | 6 |
| Shell Script Lines | ~1,400 |
| L1 Unit Tests | 92 (all passing) |
| L2 Integration Tests | 4 (skipped pending fixes) |
| Test Execution Time | ~17 seconds (L1 + L2) |
| Report Generation | Automatic (markdown) |
| Agent Updates | SW_dev Test Level Selection Gate |
| Human Code Written | **Still 0** |

---

## Lessons Learned

### Lesson 16: Infrastructure is a Feature

Users don't see test scripts. But they *experience* the confidence those things create. Infrastructure that enables quality is as valuable as the features it protects.

### Lesson 17: Standards Exist for a Reason

ISO 15504. ASPICE. DO-178C. These aren't bureaucratic overhead — they're *distilled wisdom*. Stand on shoulders. Adapt standards to your context.

### Lesson 18: AI Writes Process, Not Just Code

The test infrastructure was AI-generated. The agent learned new behaviors. The experiment proved that AI can build *capability*, not just *output*. Teach the AI your process — it will implement it faster than you can type.

---

## Epilogue: The Factory is Open

Paper #1 built an app. Paper #2 found bugs. Paper #3 fixed bugs. Paper #4 validated at the track. Paper #5 built the infrastructure that makes all of that *reliable*.

92 unit tests. 6 automation scripts. One command. Zero human code.

And somewhere in the back of my mind, there's a ghost from a space mission years ago, nodding quietly. Different domain. Different stakes. Same discipline.

> **The process is the product.**

Trillian doesn't hope. Trillian verifies.

---

*📄 Next Paper: I don't know yet*  
*🔗 Repo: `05_AI_DIY/Trillian`*  
*🏭 Test Factory: `05_tests/infra/scripts/`*  
*📊 Tests: 92 L1 + 4 L2 = 96*  
*🚀 Play Store: Coming*  
*⌨️ Human Code: Still zero.*
