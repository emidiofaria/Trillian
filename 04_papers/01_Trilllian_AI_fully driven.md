# Trillian Chronicles — Paper #1
## "I Swear I Didn't Write a Single Line: Building a phone only Driving Coach Entirely Through AI"

**Series:** The Fully AI-Driven Software Development Experiment  
**Issue:** #1 — The Beginning  
**Date:** June 2026  
**Author:** The Human Who Promised Not to Touch the Keyboard (for code, anyway)

---

> *"Can AI write a production-grade app from scratch — spec, code, tests, ops — without a human touching the code?"*
>
> *Great question. Nobody knows yet. That's kind of the point.*

---

## Prologue: A Name, A Mission, A Slightly Unhinged Idea

Every great experiment needs a name. This one is called **Trillian**.

Why Trillian? Because it sounds cool, it's a bit mysterious, and naming your side project after something sci-fi is basically mandatory at this point. Bonus: it doubles as "Trace McMillan" — fitting for an app that traces you around a racing circuit.

Because what follows is, frankly, a little rad.

The premise: **Build a complete, real-world Android application — with a Node.js backend, a PostgreSQL database, Firebase authentication, Azure cloud hosting, and an AI coaching engine — without the human developer writing a single line of code.**

Not "minimal code". Not "mostly AI-generated with a few tweaks". **Zero. Lines. Of. Code.** **NO IDE installed!**

The human's role is architect, product owner, test oracle, and — when things inevitably go sideways — incident commander. The AI is the developer, the tester, the documenter. The keyboard is for prompts only.

**Main Objective:** prove that with AI, we will move to **next SW System development abstraction layer**.

1. Machine Code to Assembly Language (1940s–1950s)
2. Assembly to High-Level Languages (1950s–1980s)
3. High-Level Languages to Managed / Abstract Frameworks (1990s–Present)
(...)

Welcome to the Trillian Chronicles!

---

## Chapter 1: What Are We Actually Building?

Before we get into the experiment, let's talk about what Trillian *is*.

**Phone only Driving Coach** is a track day companion app. Picture this: you show up at a racing circuit with your car and your phone. You finger-draw a start/finish line on a map like you're signing a very sporty cheque. You press record. The app silently captures GPS coordinates and IMU data (accelerometer, gyroscope) at 10Hz throughout your session. You drive. You have fun. You maybe spin once. We don't judge — the AI coach will, but *we* don't.

After the session, the telemetry uploads to the cloud. The backend — a Node.js/TypeScript service running on Azure — crunches through your GPS track, finds every time you crossed that start/finish line, and reconstructs your lap times. Then it hands everything to **Anthropic Claude** (yes, an AI coaching the users of an AI-built app — the recursion is intentional), which generates personalised feedback:

> *"You lost 0.4 seconds braking into Turn 3. Your throttle application in Sector 2 is inconsistent. Your best lap was lap 7 — you were 0.2s faster on the exit of the hairpin."*

Think of it as **Strava for track days, with a very opinionated AI in the passenger seat**. V1 is post-session only — no real-time coaching, because a push notification saying "BRAKE NOW" at 150km/h is less a feature and more a lawsuit. Future versions could grow into a full social motorsport network. For now: drive, upload, get roasted by an AI.

The technical stack reads like a modern cloud engineer's wish list:

| Layer       | Technology                                                |
|:------------|:----------------------------------------------------------|
| Android App | Kotlin, min SDK 26, Hilt DI, Room, WorkManager, Retrofit  |
| Backend     | Node.js 20, TypeScript 5.x, Express                       |
| Cloud       | Microsoft Azure (App Service + Blob Storage + PostgreSQL) |
| Auth        | Firebase Authentication                                   |
| AI Coach    | Anthropic Claude (claude-sonnet-4)                        |

117 requirements. 10 architecture decisions locked before a single prompt was sent to a code generator. Two full test suites. 135 human system acceptance test cases, written to be executed at a real track day.

It's not a toy. It's a real system. And it was built — entirely — by humans "driving" AI.

---

## Chapter 2: The Rules of Engagement (or, How to Build Software with Your Hands Tied)

The experiment has three rules. They're simple. They're strict. They will occasionally make you want to cry.

**Rule 1: Never touch the code.** The human interacts only through AI. No sneaky edits to a Kotlin file. No "just this once" fix in TypeScript at 11pm. If it's broken, you write a prompt, file an incident, or invoke an agent. The keyboard is for thinking out loud to a machine — not for patching things manually like a normal person.

**Rule 2: Spec-Driven Development.** Nothing gets built without a specification first. The System Requirements Specification (SRS) is the constitution. Every feature, every API endpoint, every database field traces back to a numbered requirement. `UM-01` says registration requires email + password + display name. If the code doesn't implement `UM-01`, the code is wrong — not the spec. The spec is always right. The spec is basically a deity.

**Rule 3: Test-Driven Development.** Tests are not optional, not an afterthought, and definitely not something you "get around to later." Unit tests and integration tests are part of every implementation phase. If something breaks without a test catching it first, that's a process failure, not just a bug. The test suite is the immune system.

**Bonus Rule: V-Model.** The classic systems engineering model, dusted off from the 1980s aerospace playbook — requirements define acceptance tests at the top; design defines integration tests in the middle; code defines unit tests at the bottom. All 135 human system acceptance tests for this app were derived directly from the SRS *before the app existed*. That's not accidental — it's the methodology. It's also slightly terrifying.

Taken together, these rules are, in essence, a **wager**: can rigorous engineering discipline applied *through* AI produce reliable software? Can you get aerospace-grade SDLC quality from a chat interface and a well-written spec? We are about to find out.

---

## Chapter 3: The Players

No experiment runs on a single mind. This one used three distinct AI roles, each with a job description that would look great on a business card:

**The Architect** — Claude.ai (Sonnet): Handed a napkin-sketch product idea and told to turn it into a 117-requirement SRS. The human wrote the vision in plain English; the AI wrote the specification with numbered requirements, locked architecture decisions, and enough edge case coverage to make a QA engineer emotional. It thought of things the human hadn't — like what happens when the Firebase token expires mid-upload at 200km/h. Nobody asked it to think that hard. It did anyway.

**The Builder** — GitHub CLI with Opus 4.5: The engine room. Fed the SRS plus a detailed implementation plan (also AI-generated, naturally), it built the Android app phase by phase — scaffold, database layer, telemetry service, backend, UI screens, API client, test suite — across 10 structured phases. Output: ~50 Kotlin files, ~30 XML layouts, ~15 TypeScript files, 61 automated tests. Nobody typed a single `fun ` or `class ` by hand.

**The Doctor** — RCA Agent (custom `.github/agents/`): The incident specialist. When things break, this agent steps in with evidence-first reasoning — no "probably" or "in my experience", only "the log shows" and "the stack trace indicates". It is rigorous to the point of being slightly pedantic. That is a feature, not a bug, when you're debugging a `SecurityException` at 8am on a Saturday. 
Possible soon  will have a child: "Provide Fix Agent" (custom `.github/agents/`): Agent that will help us provide fix for incidents. most complex agent, will implement the code, runs tests and judge if we can merge the fix.

---

## Chapter 4: The First Incident — Android 12 Would Like a Word

The app was built. It ran on the emulator. The features were there. The team (one human and a rotating cast of language models) felt quietly smug about this.

Then came the first real test: actually running the recording on a physical device.

**Crash. Immediate. Spectacular. Predictable in hindsight, as all crashes are.**

```
java.lang.SecurityException: To use the sampling rate of 0 microseconds,
app needs to declare the normal permission HIGH_SAMPLING_RATE_SENSORS.
```

Android 12 introduced a rule: if your app samples sensors faster than 200Hz, you must declare `HIGH_SAMPLING_RATE_SENSORS` in the manifest. The IMU was being sampled at maximum rate (0 microseconds = "give me everything the hardware has, I don't care, I'm a track day app"). The manifest had no such permission. The phone said no, firmly, in the form of a `SecurityException`.

The human's instinct — forged by twenty years of muscle memory — was to open the manifest file and add one line. Three seconds. Done. Coffee still warm.

**Rule 1 says no. Put the keyboard down.**

So instead: incident report filed in `03_incidents/`, RCA agent invoked, root cause confirmed with evidence, fix prompt written, fix applied by the AI, build verified. The whole loop took considerably longer than three seconds.

But here's the thing. What it *produced* was: a documented incident with root cause, evidence chain, fix, and a prevention note — all traceable back to the SRS non-functional requirement that the app must sample at minimum 10Hz. Six months from now, when someone asks "why is `HIGH_SAMPLING_RATE_SENSORS` in the manifest?", the answer is sitting in the repo with its full paper trail.

The three-second fix would have left nothing. The process-compliant fix left institutional memory.

*That is the whole trade. Slower in the moment, richer forever.*

---

## Chapter 5: Scoreboard — End of Sprint Zero

As of June 2026, here's the honest state of the mission:

| Dimension                    | Status                                |
|:-----------------------------|:--------------------------------------|
| SRS (117 requirements)       | ✅ Approved, locked                   |
| Android App Build            | ✅ Builds successfully                |
| Backend Build                | ✅ Builds successfully                |
| Unit Tests (61)              | ✅ Passing                            |
| Human Acceptance Tests (135) | ✅ Documented, ready for execution    |
| Real device testing          | ⚠️ One crash found, one crash fixed |
| Track day validation         | 🔜 Pending                          |
| Backend on Azure             | 🔜 Pending deployment               |

The app runs. The features are there. The overall verdict was "surprisingly good" — which is a genuinely surprising thing to say about a codebase no human ever typed. Or maybe not surprising at all. The spec was precise. The plan was structured. The AI followed instructions.

The lesson from Sprint Zero, stated plainly: **AI code quality is a direct reflection of spec quality**. Vague prompt → vague result. Clear, complete, well-reasoned spec → something you can actually run, test, and demo. The AI is not magic. It's a very fast, very literal contractor. Write good requirements, get good code.

---

## Chapter 6: The Questions We're Here to Answer

This paper is the first in a bi-monthly series. Each issue tracks the experiment's progress — what worked, what blew up, what was learned. The open questions driving the whole adventure:

1. **Can a fully spec-driven, AI-implemented project stay quality without a human ever patching the code?**
2. **Does the V-Model survive contact with AI-generated code?** Early sign: surprisingly yes — because spec-driven AI code is paradoxically more traceable than a lot of human-written code.
3. **How far can AI agents carry operations?** RCA, incident triage, fix generation — can the human stay out of the repo even when the production system is on fire at 2am?
4. **What does this process actually cost?** Not in euros — in time, in prompt iteration, in cognitive effort. Is it faster than traditional development? Probably not. Is it more disciplined? Possibly. Is it more fun? Absolutely.
5. **Is this the future, or an elaborate way to make simple things hard?**

Honest answer right now: *we don't know*. The app builds. One real crash was caught and fixed without human code intervention. The system tests are waiting for a track day with tarmac and tyre squeal. There is a lot of road left in this experiment.

But what we can already say: a complete, working application — real architecture, real tests, real operational tooling — was built entirely from a specification document, by AI, with a human who never touched the source. That's not nothing. That is, in fact, the entire point.

---

## Epilogue: Mostly Working

The Trillian project's current official verdict on fully AI-driven software development: **"Mostly working."**

That's a better grade than most side projects get. We'll take it. For now.

Paper #2 will cover the first real track day — GPS accuracy in the wild, lap detection quality on actual tarmac, and whether the AI coaching feedback is genuinely useful or just very confidently wrong. Given what's been delivered so far, the bet is on "surprisingly useful." We have been surprised before. Pleasantly.

Until then: write the spec, trust the process, and for the love of all things engineering —

**Do. Not. Touch. The. Code.**

---

*📄 Next Paper: "Trillian Chronicles #2 — The Track Day" (expected: July 2026)*  
*🔗 Repo: `05_AI_DIY/Trillian`*  
*⚙️ Stack: Kotlin · TypeScript · Node.js · Firebase · Azure · Anthropic Claude*  
*📋 SRS: `01_requirements/DrivingCoach_SRS_v1.md` — 117 requirements, 10 architecture decisions*