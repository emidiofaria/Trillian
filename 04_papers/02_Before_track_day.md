# Trillian Chronicles — Paper #2
## "Before the Track Day: Four Bugs, Zero Code Typed, and a yellow helmet Made of Pixels"

**Series:** The Fully AI-Driven Software Development Experiment  
**Issue:** #2 — The Pre-Season Shakedown  
**Date:** July 2026  
**Author:** The Human Who Still Hasn't Touched the Keyboard (it's getting weird)

---

> *"The app builds. The tests pass. What could possibly go wrong?"*
>
> *Famous last words. Spoken 0.3 seconds before the emulator said: 'No laps detected.'*

---

## Prologue: The Confidence Before the Storm

Paper #1 ended on a high note. The app compiled. Sixty-one tests passed. The human was feeling smug. The AI was feeling... well, AIs don't feel things, but if they did, it would've been a concerning amount of confidence.

We were ready for the track. Or so we thought.

This paper documents the *twenty-four hours before the actual track day* — a brutal shakedown where the app met reality, reality won several rounds, and four incidents were filed, triaged, and fixed. All without a single line of human-written code.

Spoiler: It worked. Eventually. After some... extensive prompting.

---

## TL;DR: Six Lessons the Emulator Taught Us

Before you read about the bugs that almost ruined track day, here's what they taught us. These are the lessons that survive long after the stack traces are forgotten.

### Lesson 1: AI Builds What You Specify, Not What You Need

The speed chart showed a beautiful sine wave. It technically fulfilled the requirement: *"show a speed chart."* The AI even left a helpful comment: `// In a real app, this would come from telemetry samples`. But nobody questioned it until a human with racing knowledge asked: *"Wait, what does this chart actually show?"*

X-axis was time (useless for comparing corners). Data was synthetic (useless for actual analysis). The AI implemented the spec faithfully — including its gaps.

**The rule:** AI implements your request literally. It doesn't challenge the request. Domain expertise isn't optional; it's the difference between a feature that exists and a feature that's useful. A chart that "shows speed" isn't the same as a chart that helps you go faster.

### Lesson 2: Numbers Have Context

200 meters is a sensible threshold — for a 3km circuit. Copy it to a 400m kart track and suddenly you're filtering out *half the lap*. The algorithm was waiting for drivers to reach a distance that was physically impossible.

**The rule:** Always ask: *"Where did this number come from, and does it still make sense here?"* Configuration values aren't universal truths. They're assumptions encoded as constants.

### Lesson 3: Debug Data Should Be Self-Contained

Diagnosing the first two bugs required three data sources: telemetry file, Room database, and session metadata. Three places. One diagnosis. This is how systems work against you.

**The rule:** Add context to artifacts. A file that explains itself is worth ten that require archaeology. Future you will thank present you. So will anyone in support.

### Lesson 4: Primary Content First

Users came to see their lap times. The UI showed them a 180dp processing card instead. When offline — the *entire point* of local detection — this card stayed forever, blocking the only thing anyone cared about.

**The rule:** Show users what they want. Put status, loading states, and secondary information *below* or *dismissible*. Never between the user and their data.

### Lesson 5: Placeholders Are Forever (Unless You Replace Them)

That blue circle was committed months ago. It survived multiple feature branches, made it into release APKs, and sat there patiently waiting for someone to notice it wasn't actually a helmet. Nobody did. Until the day before track day.

**The rule:** Replace placeholders immediately after the real asset exists. They have a way of becoming permanent. "Temporary" is the most permanent state in software.

### Lesson 6: Race Conditions Don't Care About Your Architecture

The service-emits-state-ViewModel-observes pattern looks beautiful on a whiteboard. In practice, if the service calls `stopSelf()` mid-emission, the observer never receives the message. The newspaper is already in the fire by the time you reach for it.

**The rule:** Don't rely on async observation for critical operations triggered by teardown. Call it directly or don't call it at all.
---

## The Evidence: Four Bugs, One Table

Here's what broke. The lessons above came from these. Full root cause analyses live in `03_incidents/` for the morbidly curious.

| # | What Broke | Root Cause | Fix | Lesson |
|---|------------|------------|-----|--------|
| 02 | Lap detection never ran | Service called `stopSelf()` before ViewModel observed `Idle` state — race condition | Call `processLapsLocally()` directly, don't wait for state | #1 |
| 03 | Zero crossings on kart track | 200m threshold = 50% of a 400m track. Impossible to satisfy. | Change `MIN_DISTANCE_FROM_START_M` from 200 to 50 | #2 |
| 04 | Debugging required 3 data sources | Telemetry file had no session context — start line stored separately in Room | Add header line to JSONL with session metadata + start line | #3 |
| 05 | Giant card blocked lap times | UI designed "online-first" — status card assumed temporary, but offline = permanent | Replace 180dp card with 40dp dismissible status bar | #4 |
| — | Blue circle instead of helmet | Placeholder drawable never replaced with actual app icon | Change `bg_logo_placeholder` → `@mipmap/ic_launcher` | #5 |
| — | Speed chart showed fake data | AI implemented "speed chart" literally — sine wave, time-based X-axis, no real telemetry | Real telemetry, distance-based X-axis, downsampling dialog | #6 |

*Full RCAs with evidence, timelines, and code analysis: `03_incidents/02-05/`*

---

## The Scoreboard

| Metric | Value |
|--------|-------|
| Incidents filed | 5 |
| RCAs completed | 4 (blue circle didn't need one) |
| SRS requirements | 117 (all aligned with code) |
| Lines of code typed by human | 0 |
| Tests passing | All |
| APKs built | 7 |
| Times the human almost opened the IDE | At least 3 |
| Track day readiness | ✅ Yes |

---

## Epilogue: Ready for Tarmac

It's the night before the track day. The app is installed. The helmet icon — the *actual* helmet, not a blue circle — glows on the phone screen. The emulator has proven that lap detection works: right threshold, self-contained files, UI that doesn't hide what matters.

Paper #1 asked: *"Can AI build a production app without human code intervention?"*

Paper #2 answers: *"Yes — and when bugs appear, it can debug them too. With documentation."*

Four bugs. Four fixes. Six lessons. Zero lines of human code. The spec is intact — all 117 requirements still trace to working code. The tests pass. The incidents are filed with full root cause analysis, evidence chains, and prevention recommendations that will outlive the bugs themselves.

Tomorrow: real GPS. Real tires. Real data. The validation that matters.

And if something breaks?

There will be a Paper #3.

---

*📄 Next Paper: "Trillian Chronicles #3 — Track Day" (expected: August 2026)*  
*🔗 Repo: `05_AI_DIY/Trillian`*  
*🐛 Incidents: `03_incidents/02-05`*  
*🏎️ Track: TBD (but it will have corners)*  
*☕ Coffee Consumed: Significant*
