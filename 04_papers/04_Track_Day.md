# Trillian Chronicles — Paper #4
## "Tarmac Truth: When the App Met Real Tyres, Real Users, and a Kart Track"

**Series:** The Fully AI-Driven Software Development Experiment  
**Issue:** #4 — The Track Day  
**Date:** August 2026  
**Author:** The Human Who Still Hasn't Touched the Keyboard (but did touch a steering wheel, finally)

---

> *"It works on the emulator."*
>
> *Famous last words. But this time? It actually worked on the track too. I'm as surprised as you are.*

---

## Prologue: Holiday Mode — Activated

There's something magical about being on holiday. The emails slow down. The meetings vanish. And suddenly, you have time to do the thing you've been promising yourself for months: actually *test* the app you built.

Papers #1 through #3 were written from a desk. Emulators. Synthetic GPX files. Virtual laps around Red Bull Ring at 5x speed while sipping coffee in an office chair. The bugs were real, but the driving was fake.

Paper #4 is different.

### The Rules (For Those Just Joining Us)

Before we get to the track, a quick reminder of the experiment's ground rules — the constraints that make this whole thing interesting:

**Rule 1: Never Touch the Code.** The human interacts only through AI. Prompts only. No IDE. No "just this once" quick fix at 11pm. If it's broken, you write a prompt — not a patch.

**Rule 2: Spec-Driven Development.** Nothing gets built without a specification first. Every feature, every endpoint, every database field traces to a numbered requirement. The SRS is the constitution.

**Rule 3: Test-Driven Development.** Tests aren't optional. They're not an afterthought. Unit and integration tests are part of every phase. The test suite is the immune system.

**Bonus Rule: V-Model.** The aerospace-era classic: requirements define acceptance tests at the top, design defines integration tests in the middle, code defines unit tests at the bottom. All tests derived *before* the code exists.

Papers #1 through #3 built and debugged under these rules. Paper #4 is where they meet reality — real tyres, real users, real consequences.

This paper was written after real tyres touched real track. After GPS signals bounced off real satellites. After three humans — used the app and immediately started requesting features.

This is the track day paper. The one where Trillian stopped being an experiment and started being a product.

---

## Chapter 1: Assembling the Test Crew

The human's pitch was simple: *"I built an app. Want to help me test it? There's go-karts."*

Nobody says no to go-karts.

**The Crew:**

| Tester | Technical Background | Driving Background | Role |
|--------|---------------------|-------------------|------|
| Friend A | Zero | Enthusiast | Guinea Pig Alpha |
| Friend B | "I can reset my router" | Occasional | Guinea Pig Beta |
| The Girlfriend | Patience with the developer | Supportive but sceptical | Quality Assurance / Moral Support |
| Me | Built this entire thing without typing code | Track day enthusiast | Product Owner / Nervous Wreck |

Three real users. One kart track. Four phones. No fallback plan.

The scenario was intentionally harsh: if Trillian could survive our three users at a noisy, dusty kart circuit — with old phones, spotty reception, and zero patience for "developer excuses" — it could survive anything.

Or it would crash spectacularly, and Paper #4 would be titled *"Post-Mortem: How I Wasted Everyone's Saturday."*

---

## Chapter 2: First Contact — GPS Lock Acquired

The kart track wasn't glamorous. A 980-metre circuit, tight corners, a strong smell of two-stroke exhaust, and a marshal who looked deeply unimpressed by four people staring at their phones instead of getting in the karts.

**Session 1: The Setup**

The users opened the app. The home screen appeared. The yellow helmet icon (I'm a Senna fan) — the *real* one, not the blue placeholder — glowed back at them.

Step 1: Enter track name. *"Kartódromo cabo do mundo"* — typed with a hint of local pride.

Step 2: Set the defined point A and point B at finish line. The line appeared.

Step 3: Press Record.

And then... nothing broke.
Small note the best phone took 12 seconds to acquire signal and oldest one 18 seconds.

The GPS icon pulsed. The timer started. The telemetry service began collecting coordinates at 10Hz. The phones didn't crash. The app didn't freeze. The users got into their karts.

**The human, internally:** *"Okay. Don't celebrate yet. The hard part is when the data has to make sense."*

---

## Chapter 3: Laps Detected — For Real

Fifteen minutes of karting later, the first session ended.

The user pressed Stop. The recording service wound down. The telemetry file saved. The local lap detection algorithm — the same one that worked in the emulator, the same one that nearly didn't work on the kart track because of the 200m threshold issue (Paper #2, RIP) — began processing.

**Result:**

#TODO check the results page.
```
Session: Kartódromo cabo do mundo
Laps Detected: 7
Best Lap: 1.02.3s
Consistency: Within 1.8s
Top Speed: 47 km/h (it's a kart, not a Formula 1 car)
```

Laps detected. Times calculated. No crashes. No "0ms quicker than average" nonsense. No sessions vanishing into the void.

The human allowed themselves a small, private moment of relief.

The users, oblivious to the months of debugging that preceded this moment, simply said: *"Cool. Can we go again?"*

---

## Chapter 4: Rules Status Report — Did They Survive the Track?

Remember those rules? The constraints that make this experiment worth running? Here's how they held up when theory met tarmac.

| Rule | Status | The Verdict |
|------|--------|-------------|
| **#1: Never Touch Code** | ✅ **Held** | The IDE stayed closed. The temptation stayed strong. At no point did the human open Android Studio — not during setup, not during testing, not when Friend B asked "can you just add a feature real quick?" The answer was: "No. Write it down. It becomes a requirement." |
| **#2: Spec-Driven** | ✅ **Held** | Every feature request from the track day became a numbered requirement (`UX-01` through `UX-04`). Users don't file SRS items — but their feedback does. The spec grows from reality, not imagination. |
| **#3: Test-Driven** | ✅ **Validated** | 84 tests. 0 bugs at the track. The emulator lie detector (Paper #3) caught what mattered. The tests weren't just passing — they were *protecting*. |
| **Bonus: V-Model** | ✅ **Vindicated** | Acceptance tests were written before code existed. Acceptance was achieved after tyres touched tarmac. The 135 human acceptance test cases weren't just documentation — they were a prediction that came true. |

**The uncomfortable truth:** Following the rules felt slow during development. Not touching the code when you *know* the fix is one line? Painful. Writing requirements before prompting the AI? Tedious. Writing tests before features? Boring.

But at the track, with real users, on real hardware? Zero crashes. Zero data loss. Zero embarrassment.

**The rules didn't just survive. They're the reason the app did.**

---

## Chapter 5: The Feature Requests Nobody Expected (But Should Have)

Here's the thing about putting software in front of real users: they immediately find friction you never anticipated.

Within thirty minutes, three users had generated a feature backlog more insightful than most sprint planning sessions.

### Feature Request #1: "Why can't it remember track names?"

**The User Said:** *"I typed 'Kartódromo cabo do mundo' once. Now Friend B has to type it again. Why doesn't it just... suggest it?"*

**The Insight:** The app stores session history. It knows every track name ever entered. It does nothing with this knowledge. The user has to re-type track names they've already used — on the same device.

**The Fix (Proposed):** 

| Requirement ID | Description |
|----------------|-------------|
| `UX-01` | When entering a new track name, show autocomplete suggestions from previously used track names stored locally |

**Implementation Complexity:** Low. Query `SessionEntity`, extract distinct track names, populate `AutoCompleteTextView`. The data is already there. We're just not using it.

---

### Feature Request #2: "The GPS takes forever to lock"

**The User Said:** *"I opened the app and it just sat there. I had to wait like... 10 seconds for the GPS to find me. Friend A's phone took even longer."*

**The Insight:** GPS cold-start on older phones is *slow*. The app currently waits until the user presses "Record" to request location. By then, the user is already in the kart, waiting, impatient, and wondering if the app is broken.

**The Fix (Proposed by me):**

| Requirement ID | Description |
|----------------|-------------|
| `UX-02` | Begin GPS signal acquisition immediately on app launch, not when recording starts. Display GPS lock status on the home screen. |

**Implementation Complexity:** Medium. Need to request location permissions earlier, start a background location warm-up service, and add a status indicator. Battery implications need consideration — but for a track day app, users expect GPS to be aggressive.

**The User's Summary:** *"If I'm opening a driving app, I want to drive. Not stare at a loading spinner."*

Fair point.

---

### Feature Request #3: "Where's my lap time?"

**The User Said:** *"I finished a lap. Nothing happened. I had to wait until the session ended to see my times. Can't it just... tell me?"*

**The Insight:** The app silently records everything but provides *zero feedback during the session*. The user crosses the start/finish line. The app knows this. It calculates the lap time internally. It tells the user... nothing.

For a coaching app, this is a significant UX gap. Drivers want *immediate* feedback. Did I improve? Did I mess up? The dopamine hit of seeing "42.1s — Personal Best!" is why people use timing apps in the first place.

**The Fix (Proposed):**

| Requirement ID | Description |
|----------------|-------------|
| `UX-03` | Display lap time on screen immediately when a lap is completed during recording. Include visual indication for personal best within session. |

**Implementation Complexity:** Medium-High. Requires real-time lap detection during recording (currently only post-session), UI overlay during recording mode, and careful handling of GPS noise near the start/finish line. But the value is enormous.

**Girlfriend's Commentary:** *"It's like driving with a coach who takes notes but refuses to speak until you park the car."*

Ouch. But accurate.

---

### Feature Request #4: "What does this graph actually mean?"

**The User Said:** *"There's a speed graph. I can see I went fast somewhere. But where? Can I tap on it and see?"*

**The Insight:** The speed graph shows velocity over distance. It's informative in aggregate but useless for detailed analysis. The user sees a spike at 250m but has no way to know: what corner was that? What speed exactly? What were the GPS coordinates?

**The Fix (Proposed):**

| Requirement ID | Description |
|----------------|-------------|
| `UX-04` | On speed graph interaction (tap/click on a data point), display a tooltip or bottom sheet showing: coordinates (lat/long), speed (km/h), distance from start (m), and timestamp. |

**Implementation Complexity:** Medium. The data exists in the telemetry file. Need to add touch listeners to the chart, map screen coordinates to data points, and render contextual information. Standard charting library features.

**The User's Vision:** *"I want to tap the spike and see: 'Turn 3 exit — 48 km/h — 0.3s faster than average lap.' That would be sick."*

It would, indeed, be sick.

---

## Chapter 6: The Validation Moment

At the end of the afternoon, something unexpected happened.

The users didn't just tolerate the app. They *used* it. Voluntarily. Multiple sessions. They compared lap times. They argued about who was fastest. Friend B declared himself "the consistency king" because his standard deviation was lower (he conveniently ignored that his average was 3 seconds slower).

The Girlfriend, who had approached the day with polite scepticism, admitted: *"Okay, this is actually useful. I want to see my times improve."*

That's not a crash report. That's not a bug. That's product-market fit.

---

## Chapter 7: The Bug That Didn't Happen

For full transparency: we anticipated bugs. We planned for crashes. The developer had a notebook ready for incident reports. The RCA Agent was pre-loaded in a terminal, waiting...

**Bugs encountered during track day: 0**

Zero crashes. Zero data loss. Zero "unexpected behaviour." The app did exactly what Papers #1 through #3 said it would do — and this time, it did it at an actual track, with actual users, under actual conditions.

This is not because the app is perfect. It isn't. The feature requests prove there's work to do. But the *core functionality* — GPS recording, lap detection, coaching insights — worked first try.

**Why?**

Three papers of debugging before a single tyre touched track. The emulator lie detector (Paper #3) caught the bugs that mattered. The spec-driven approach meant the core requirements were solid before we ever wrote a prompt.

The lesson is uncomfortable for developers who prefer heroic debugging sessions: **boring preparation beats exciting crisis management**.

---

## The Scoreboard
#TODO check this table.
| Metric | Value |
|--------|-------|
| Track Day Status | ✅ Completed |
| Real Users | 4 (including me) |
| Sessions Recorded | 9 |
| Total Laps Detected | 47 |
| Crashes | 0 |
| Data Loss | 0 |
| Feature Requests Captured | 4 |
| Lines of Code Typed by Human | **Still 0** |
| User Satisfaction | High (they want to go back) |
| App Deletion Rate | 0% (so far) |

---

## The New Requirements

The users spoke. The spec must evolve. Here are the four new UX requirements, derived directly from track day feedback:

| ID | Title | Priority | Complexity |
|----|-------|----------|------------|
| `UX-01` | Track name autocomplete from history | High | Low |
| `UX-02` | GPS pre-acquisition on app launch | High | Medium |
| `UX-03` | Live lap time display during recording | High | Medium-High |
| `UX-04` | Interactive speed graph with data tooltips | Medium | Medium |

These will be added to the SRS and implemented in the next sprint — still without the human typing a single line of code.

---

## Lessons Learned

### Lesson 12: Real Users Find Real Problems

Emulators test functionality. Users test experience. The feature requests from thirty minutes of real-world use were more valuable than three weeks of desk-based speculation.

**The rule:** Get the app into human hands as early as you can survive the embarrassment. They'll find what you missed.

### Lesson 13: "Works on My Machine" is a Lie; "Works at the Track" is Truth

The emulator said the app worked. The track confirmed it. These are not the same statement. GPS noise, phone hardware variance, user impatience — none of these exist in synthetic tests.

**The rule:** Simulation validates logic. Reality validates product.

### Lesson 14: The Best Features Come from Frustrated Users

Nobody in a planning meeting would have said *"What if the GPS is slow on old phones?"* But Friend A's three-year-old Android made it obvious in seconds.

**The rule:** Feature discovery happens at the edges. Test with the worst hardware, the least technical users, and the most impatient audience.

### Lesson 15: Zero Bugs on Launch Day is Not Luck — It's Preparation

Three papers of debugging. Four incidents. Six lessons. Eighty-four tests. All before the track day. The result: a flawless first outing. This isn't luck. It's compounding effort.

**The rule:** The bugs you catch before launch don't become the stories you tell after launch.

---

## Epilogue: From Experiment to Product

Paper #1 asked: *Can AI build an app?*  
Paper #2 answered: *Yes, and bugs appear.*  
Paper #3 proved: *AI can debug itself.*  
Paper #4 confirms: **The app works. With real users. At a real track.**

#TODO review this section
Forty-seven laps. Nine sessions. Four feature requests. Zero crashes. And still — *still* — zero lines of human-written code.

The experiment succeeded. But something shifted on that kart track, between the smell of petrol and the sound of two-stroke engines. The app stopped being an experiment. It became something people wanted to use again.

Friend B has already asked when the next session is.

The Girlfriend suggested "maybe a proper race track next time."

The backlog has four new requirements.

And the human, sitting at the desk the next morning, opened the terminal to talk to the AI, not to fix what was broken — but to build what users asked for.

This is no longer a proof of concept. This is a product. An AI-built, spec-driven, V-Model-tested product that survived contact with reality.

Paper #5 will cover the new features — track name autocomplete, GPS pre-acquisition, live lap times, interactive graphs. The spec is already being updated. The prompts are being drafted.

But first: the victory lap.

We made it. 🏁

---

*📄 Next Paper: "Trillian Chronicles #5 — The Feature Sprint" (expected: September 2026)*  
*🔗 Repo: `05_AI_DIY/Trillian`*  
*🏎️ Track: Kartódromo cabo do munod (920m, 12 turns, smells like victory)*  
*👥 Test Crew: 4 humans, 0 developers (except the one who didn't write code)*  
*🐛 Bugs at Track: 0*  
*✨ Features Requested: 4*  
*☕ Coffee Consumed: Replaced with post-race beers*  
*⌨️ Human Code Written: Zero. But we're starting to lose count of how many times we've said that.*
