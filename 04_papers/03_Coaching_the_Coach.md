# Trillian Chronicles — Paper #3
## "Coaching the Coach: When the AI Gave Advice It Didn't Have Data For"

**Series:** The Fully AI-Driven Software Development Experiment  
**Issue:** #3 — The Dress Rehearsal  
**Date:** July 2026  
**Author:** The Human Who Still Hasn't Touched the Keyboard (but came dangerously close at 1am)

---

> *"You nailed Sector 1 — 0ms quicker than average."*
>
> *The AI coach said this with complete confidence. The sectors didn't exist. Zero milliseconds is technically accurate. It's also completely useless.*

---

## Prologue: The Virtual Track Day

Before you risk real tyres on real tarmac, you simulate.

The plan was simple: run a virtual session at Red Bull Ring using the Android Emulator. Three laps. GPS coordinates streaming at 1Hz from a simplified GPX file. The app would detect laps, generate coaching insights, and prove — once and for all — that everything worked before the actual track day.

Spoiler: everything did not work. But we found out *before* wasting petrol.

### The Emulator's Dirty Secret

Here's something Android developers eventually learn the hard way: **the emulator ignores your GPX timestamps.**

You craft a beautiful 10Hz GPX file with microsecond-precision `<time>` tags. The emulator looks at it, shrugs, and injects coordinates at ~1 point per second anyway. Your 60-second lap takes 10 minutes. Your top speed calculations spike to 342 km/h because the gaps between points are massive.

| File Type | Real Lap Time | Emulator @ 1x | Reality Check |
|-----------|---------------|---------------|---------------|
| 10Hz GPX  | ~65 sec       | ~660 sec      | ❌ 10x slower |
| 1Hz GPX   | ~65 sec       | ~65 sec       | ✅ Real-time |

The solution: downsample to 1Hz. Match the emulator's injection rate. Accept that your speed calculations will be... creative.

```bash
python3 gpx_simulator.py simplify redbullring_10hz.gpx -n 10 -o redbullring_1hz.gpx
```

Three laps of Red Bull Ring. 397 GPS points. About 80 seconds at 5x playback. Fast enough to test. Accurate enough to validate lap detection.

The simulation ran. Laps were detected. The Coach tab appeared.

And then the AI said something strange.

---

## Chapter 1: "0ms Quicker" — The Meaningless Insight

The session ended. The user opened the Coach tab. The app proudly displayed:

> **"Lap 2 Was Your Fastest"**  
> *"You nailed Sector 1 — 0ms quicker than average. Try to replicate that next session."*

Zero milliseconds. The AI coach was praising a sector improvement that... didn't exist.

**RCA Time.**

The evidence trail was short but damning:

1. **Local lap detection** works offline — great! But it doesn't calculate sectors. The backend does that after upload.
2. **`LapEntity.sector*Ms`** is stored as `Long` — non-nullable. When sectors aren't calculated, the code stores `0L`.
3. **`OfflineCoachingEngine`** calculates average sector times. Average of `[0, 0, 0]` is `0`. Best sector minus average equals `0 - 0 = 0`.
4. **The insight text** dutifully reports: "0ms quicker than average."

Technically correct. Semantically absurd.

```kotlin
// What the code did
val avgS1 = laps.map { it.sector1Ms }.average()  // = 0.0
val gainS1 = avgS1 - bestLap.sector1Ms           // = 0.0 - 0 = 0
// "You nailed Sector 1 — 0ms quicker!" 🎉
```

The AI built exactly what the spec described. The spec didn't describe what happens when sectors don't exist.

### The Fix: Guard the Data

New rule: if all sectors are zero, *don't talk about sectors.*

```kotlin
private fun areSectorsAvailable(laps: List<LapEntity>): Boolean {
    return laps.all { it.sector1Ms > 0 && it.sector2Ms > 0 && it.sector3Ms > 0 }
}
```

When sectors are unavailable:
- Best Lap insight shows "**2.3s ahead of average**" (no sector mention)
- Sector Focus shows "**Sector Analysis Coming Soon**" (upsell, not lies)
- No more "0ms quicker" anywhere

The meaningless became motivating.

---

## Chapter 2: The GPS Spike (Bonus Discovery)

While analysing the telemetry file, a wild data point appeared:

```
Max speed: 342.6 m/s
```

That's **1,233 km/h**. In a road car. At Red Bull Ring. Either someone strapped a jet engine to their Miata, or the GPS had a bad day.

The 1Hz simplification created gaps. Large distance jumps over 1-second intervals mean calculated speeds spike when the car is moving fast. This is expected behaviour — but we shouldn't *report* it as real.

**The Fix: Speed Cap**

```kotlin
val MAX_PLAUSIBLE_SPEED_MS = 97.2  // 350 km/h — generous but sane
```

Any reading above 350 km/h is filtered as GPS noise. The coaching engine now ignores implausible velocities instead of proudly announcing "🚀 Top Speed: 1233 km/h — Hit on Lap 2."

---

## Chapter 3: The Session That Vanished

With the coaching fix deployed, we ran another simulation. Lap detection worked. Coaching insights were generated. The user navigated to the Home screen to admire their Recent Sessions.

**The list was empty.**

Three sessions recorded. Three sessions in the database. Zero sessions visible.

**RCA Time (Again).**

The culprit was beautifully stupid:

| Location | User ID |
|----------|---------|
| `RecordingViewModel` (creates session) | `"default_user"` |
| `HomeViewModel` (queries sessions) | `"demo_user"` |

The session was stored with one user ID. The query filtered by a different user ID. The database returned zero rows. The UI showed "No sessions yet."

Two hardcoded strings. One typo. A feature that "worked" in isolation but failed the moment data flowed between screens.

**The Fix: Remove the Filter**

For a single-user MVP, the user ID filter is unnecessary complexity. Removed.

```kotlin
// Before
sessionDao.getAllSessionsForUser("demo_user")  // 0 results

// After
sessionDao.getAllSessions()  // All sessions visible
```

Sessions appeared. The user stopped wondering if the app was gaslighting them.

---

## Chapter 4: The Track Name That Forgot Itself

Final bug. The user starts a new session. The dialog appears. They type "**Circuito de Braga**" with pride. They set up the start/finish line. They record three laps. They check Recent Sessions.

> **Track Session**  
> *15 Jul 2026 · 3 laps*

"Circuito de Braga" vanished. Every session was named "Track Session."

**The Evidence:**

```kotlin
// HomeFragment: captures "Circuito de Braga" ✅
val trackName = editText.text.toString().trim()
viewModel.startNewSession(trackName)

// HomeViewModel: emits navigation event ✅
HomeEvent.NavigateToTrackSetup(trackName)

// nav_graph.xml: navigation action... 
<action android:id="@+id/action_home_to_track_setup" />
// ❌ NO trackName ARGUMENT

// RecordingFragment: falls back to hardcoded value ❌
trackName = "Track Session"  // TODO: Get from nav args
```

The TODO comment was right there. Mocking us. For months.

**The Fix: Thread the Argument**

```xml
<!-- nav_graph.xml -->
<argument android:name="trackName" app:argType="string" />
```

```kotlin
// HomeFragment
actionHomeToTrackSetup(event.trackName)

// TrackSetupFragment
args.trackName  // received, forward to Recording

// RecordingFragment
args.trackName  // finally used instead of "Track Session"
```

Track names now survive the navigation gauntlet. "Circuito de Braga" persists. The TODO is gone. The developer who wrote it would be embarrassed, but the developer who wrote it was an AI, and AIs don't get embarrassed.

---

## Chapter 5: The Feature Nobody Asked For (But Everyone Wanted)

While fixing the coaching engine, an idea emerged: **Top Speed.**

The data was already there. Every telemetry sample includes `speedMs`. The coaching engine was already reading the file for other purposes. Adding a "🚀 Top Speed: 247 km/h — Hit on Lap 3" insight was... easy.

| Insight | Source | Value |
|---------|--------|-------|
| Best Lap | Room DB | "2.3s ahead of average" |
| **Top Speed** | Telemetry JSONL | "247 km/h on Lap 3" |
| Consistency | Calculated stdDev | "Within 1.2s of each other" |
| Sector Focus | Conditional | "Coming Soon" (upsell) |

The spec didn't require it. The RCA didn't demand it. But every driver wants to know their top speed. Now they do.

This is the upside of reading your own data during a bugfix. Sometimes you find features hiding in plain sight.

---

## Chapter 6: The AI Development Loop

This session revealed something bigger than individual bugs. It revealed a *workflow*.

### Two Entry Points, One Process

Every improvement starts the same way: either something broke (defect) or something's missing (feature). Both flow through the same pipeline.

```
┌─────────────────┐     ┌─────────────────┐
│  🐛 Defect      │     │  ✨ Feature     │
│  (Something     │     │  (Something     │
│   broke)        │     │   new)          │
└────────┬────────┘     └────────┬────────┘
         │                       │
         ▼                       │
┌─────────────────┐              │
│  RCA Agent      │              │
│  - Investigate  │              │
│  - Diagnose     │              │
│  - Document     │              │
└────────┬────────┘              │
         │                       │
         └───────────┬───────────┘
                     ▼
┌─────────────────────────────────────────┐
│  SW_dev Agent (Senior Developer)        │
│                                         │
│  READS BEFORE ACTING:                   │
│  - Atlas (system architecture docs)     │
│  - Requirements (SRS)                   │
│  - Codebase (patterns, conventions)     │
│                                         │
│  WORKFLOW:                              │
│  1. Discuss idea/fix with user          │
│  2. Propose plan                        │
│  3. 👤 USER APPROVES                    │
│  4. Implement                           │
│  5. Write tests                         │
│  6. Update docs (trillian-docs-sync)    │
│  7. Build & verify                      │
└─────────────────────────────────────────┘
                     ▼
           ✅ Done (traceable)
```

### What is Atlas?

Atlas is the system's living architecture documentation — the agent reads it *before* proposing changes:

| Document | Purpose |
|----------|---------|
| `system.md` | Tech stack, runtime components, dependencies |
| `components.md` | Every component: inputs, outputs, failure modes |
| `flows.md` | Data flows, user journeys, async boundaries |
| `failure-patterns.md` | Known issues, mitigations, evidence checklist |

When the agent proposes a fix, it's not just syntactically correct — it's *architecturally aligned*. It knows where things belong.

### The Human Role: System Architect / Product Owner

The human doesn't type code. The human *decides*.

| Human Does | Agent Does |
|------------|------------|
| Defines the vision | Has full system knowledge |
| Brings ideas or reports defects | Proposes aligned solutions |
| Refines scope in discussion | Implements what was approved |
| **Approves the plan** | Writes tests |
| Validates the result | Updates documentation |

The approval gate is critical. No wasted implementation. No "I built the wrong thing."

### Agile, But Structured

```
User Input → Discuss → Plan → [APPROVE] → Implement → Test → Document → Done
    ↑                                                                    │
    └────────────────────────────────────────────────────────────────────┘
                        (next increment)
```

Each loop is **small** (one defect or feature), **complete** (tests + docs included), **traceable** (linked to requirements), and **reliable** (same process every time).

### The Key Insight

> **"Discovery is not a phase — it's embedded in development."**

Traditional waterfall: Discovery → Design → Dev → Test → Docs *(sequential, docs always late)*

AI-Driven: Discovery + Design + Dev + Test + Docs = **one conversation**

The process structure *forces* quality. You can't skip the plan — the agent asks for approval. You can't skip the tests — they're part of the workflow. You can't skip the docs — `trillian-docs-sync` runs before "done."

This isn't magic. It's discipline, enforced by structure.

---

## The Scoreboard

| Metric | Value |
|--------|-------|
| RCAs completed | 2 (#06, #08) |
| Bugs fixed | 4 (0ms coaching, session visibility, track name, consistency wording) |
| Features added | 1 (Top Speed insight) |
| Tests written | 11 new (30 total for OfflineCoachingEngine) |
| Tests passing | **84** |
| APKs built | 3 (v2.1, v2.2, v2.3) |
| Lines of code typed by human | **0** |
| Virtual laps at Red Bull Ring | ~15 |
| Peak simulated speed | 1,233 km/h (filtered) |
| Actual peak speed | ~247 km/h (realistic) |

---

## Lessons Learned

### Lesson 7: Sentinel Values Are Silent Lies

Zero is a valid number. Zero is also "we don't have data." If you use zero for both, your code will eventually say something true that is also meaningless.

**The rule:** Guard sentinel values. Check before you calculate. Never generate insights from placeholder data.

### Lesson 8: Hardcoded Values Are Configuration Waiting to Happen

`"demo_user"` and `"default_user"` both looked reasonable in isolation. Together, they created an invisible filter that hid all sessions.

**The rule:** If two components share a value, make it one source of truth. Constants don't stay constant across codebases.

### Lesson 9: TODO Comments Are Bug Reports You Haven't Filed Yet

The `// TODO: Get from nav args` comment was honest. It was also ignored for months. The bug wasn't discovered until track day prep.

**The rule:** TODOs are technical debt. Track them. Schedule them. Or watch them become incidents.

### Lesson 10: The Best Bugs Are Found Before Production

Four bugs. All found on the emulator. None found at the track. The virtual session cost zero petrol, zero tyre wear, and exactly one late night.

**The rule:** Simulate before you validate. Debugging on the emulator is annoying. Debugging at the track with a helmet on is worse.

### Lesson 11: The Process Structure Forces Quality

This session used two agents: RCA investigates, SW_dev implements. The human approves the plan before any code is written. Documentation updates are part of "done," not an afterthought.

**The rule:** When discovery, development, testing, and documentation happen in one loop — nothing gets skipped. The workflow enforces discipline that willpower alone cannot.

---

## The Documentation Trail

Everything is traceable. Everything is documented. The process survives even when the bugs don't.

| Artifact | Location |
|----------|----------|
| RCA #06: Invalid Sector Coaching | `03_incidents/06_coaching_incidents/` |
| RCA #08: Track Name Lost | `03_incidents/08_track_name_not_saved/` |
| New Requirements (OC-01 to OC-10) | `01_requirements/DrivingCoach_SRS_v1.md` |
| Component Update | `atlas/components.md` |
| Sync Report | `docs/SYNC_REPORT.md` |
| GPX Simulator Guide | `test_018_GPX_simulator/` |

---

## Epilogue: Ready (For Real This Time)

It's past midnight. Three APKs sit in the `releases/` folder. The emulator is quiet. The bugs are squashed.

v2.1 fixed the coaching engine. v2.2 fixed session visibility. v2.3 fixed track names. Each version was built, tested, documented, and deployed without the human typing a single character of Kotlin or XML.

The virtual track day succeeded. Red Bull Ring was conquered at 5x speed, in an emulator, on a desk.

But emulators don't have kerbs. They don't have elevation changes. They don't have gravel traps. They don't have that moment when you brake too late and wonder if the phone — velcroed to the dashboard — will survive the off.

Paper #1 asked if AI could build an app.  
Paper #2 asked if AI could debug it.  
Paper #3 proved that AI can simulate, coach, and fix itself.

Four bugs. Four fixes. One accidental feature. Eighty-four tests passing. And still: **zero lines of human code.**

But simulation isn't validation. The emulator said "works." The track will say "prove it."

Paper #4 is the real test. Real tyres. Real GPS. Real stakes.

And if something breaks at 200 km/h?

There will be a very interesting Paper #5.

---

*📄 Next Paper: "Trillian Chronicles #4 — The Track Day" (expected: August 2026)*  
*🔗 Repo: `05_AI_DIY/Trillian`*  
*🐛 RCAs: `03_incidents/06-08`*  
*🏎️ Virtual Track: Red Bull Ring (emulated, 1Hz, 5x speed)*  
*🚀 Top Speed Achieved: 247 km/h (simulated) / 1,233 km/h (filtered as nonsense)*  
*☕ Coffee Consumed: Excessive*  
*⌨️ Human Code Written: Still zero. Still smug about it.*
