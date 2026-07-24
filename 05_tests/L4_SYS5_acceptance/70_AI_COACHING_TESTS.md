# AI Coaching Tests

> **Purpose:** Validate the AI coaching feedback feature including prompt quality, response parsing, and insight relevance.

---

## Test Environment

| Item | Required State | Check |
|------|----------------|-------|
| Backend | Running with valid ANTHROPIC_API_KEY | ☐ |
| Session | Completed with 3+ laps, varied lap times | ☐ |
| Processing | Status = COMPLETE | ☐ |

---

## Test Suite: AI — AI Coaching Generation

### AI-01: Coaching Trigger After Lap Detection

**Objective:** Verify AI coaching is triggered after laps are detected.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Complete session with 3+ laps | Recording stopped | ☐ |
| 2 | Wait for upload | Status = UPLOADING | ☐ |
| 3 | Wait for lap detection | Status = LAPS_DONE | ☐ |
| 4 | Wait for coaching | Status progresses | ☐ |
| 5 | Final status | COMPLETE | ☐ |
| 6 | COACH tab has content | Insights visible | ☐ |

**Requirement Coverage:** AI-01

---

### AI-02: Coaching Insight Count

**Objective:** Verify 3-5 coaching insights are generated.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open completed session | Session Result screen | ☐ |
| 2 | Tap COACH tab | Coaching content shown | ☐ |
| 3 | Count insight cards | At least 3 cards | ☐ |
| 4 | Count insight cards | At most 5 cards | ☐ |
| 5 | Typically | Exactly 4 cards (per AI-05) | ☐ |

**Requirement Coverage:** AI-05, AI-08

---

### AI-03: Insight Headline Quality

**Objective:** Verify headlines are concise and actionable.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Read each insight headline | Headline text | ☐ |
| 2 | Headline word count | ≤8 words each | ☐ |
| 3 | Headline length | ≤100 characters | ☐ |
| 4 | Headline content | Actionable advice | ☐ |
| 5 | Examples | "Brake later into T1", "Smoother throttle" | ☐ |

**Requirement Coverage:** AI-05, AI-06, AI-08

---

### AI-04: Insight Detail Quality

**Objective:** Verify detail text provides useful context.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Read each insight detail | Detail text | ☐ |
| 2 | Detail word count | ≤35 words each | ☐ |
| 3 | Detail length | ≤300 characters | ☐ |
| 4 | References specific data | Lap numbers, sectors, times | ☐ |
| 5 | Provides actionable advice | How to improve | ☐ |

**Requirement Coverage:** AI-04, AI-06, AI-08

---

### AI-05: Insights Reference Session Data

**Objective:** Verify insights reference actual session data.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Read insight details | Note references | ☐ |
| 2 | Check for lap numbers | "Lap 3", "Lap 2", etc. | ☐ |
| 3 | Check for sector numbers | "Sector 1", "S2", etc. | ☐ |
| 4 | Check for time values | "0.3s improvement", etc. | ☐ |
| 5 | References match session | Correct lap count, etc. | ☐ |

**Requirement Coverage:** AI-03, AI-04

---

### AI-06: Consistency Score Accuracy

**Objective:** Verify consistency score formula is correct.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Note lap times from LAPS tab | All lap times | ☐ |
| 2 | Calculate mean (μ) | Average lap time | ☐ |
| 3 | Calculate std dev (σ) | Standard deviation | ☐ |
| 4 | Calculate (1 - σ/μ) × 100 | Expected score | ☐ |
| 5 | Compare to displayed score | Should match (±0.1) | ☐ |
| 6 | Score range | 0-100% (clamped) | ☐ |

**Formula:** `consistency = (1 − σ / μ) × 100`, clamped to [0, 100]

**Requirement Coverage:** AI-13

---

### AI-07: Coaching Without Enough Laps

**Objective:** Verify behavior with minimum laps (2).

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Complete session with exactly 2 laps | Minimum valid | ☐ |
| 2 | Wait for processing | Status = COMPLETE | ☐ |
| 3 | Open COACH tab | Content shown | ☐ |
| 4 | Insights generated | May have limited context | ☐ |
| 5 | No crash or error | Graceful handling | ☐ |

**Requirement Coverage:** Related to LD-08

---

### AI-08: Coaching API Failure Handling

**Objective:** Verify session still completes if AI fails.

**Note:** This test may require simulating API failure (mock or invalid key).

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | (Setup: temporarily invalid API key) | Backend configured | ☐ |
| 2 | Complete session | Recording stopped | ☐ |
| 3 | Wait for processing | Lap detection runs | ☐ |
| 4 | AI coaching fails | Error logged | ☐ |
| 5 | Session status | Still reaches COMPLETE | ☐ |
| 6 | COACH tab | May show "No coaching available" | ☐ |
| 7 | LAPS tab | Still has lap data | ☐ |

**Requirement Coverage:** AI-10

---

### AI-09: Coaching Insights Persistence

**Objective:** Verify coaching insights are stored and persist.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | View coaching insights | Insights displayed | ☐ |
| 2 | Close app completely | Force stop | ☐ |
| 3 | Reopen app | Logged in | ☐ |
| 4 | Open same session | Session Result screen | ☐ |
| 5 | Tap COACH tab | Same insights displayed | ☐ |
| 6 | Content identical | Persisted in DB | ☐ |

**Requirement Coverage:** AI-09

---

### AI-10: Multiple Sessions Different Insights

**Objective:** Verify different sessions get unique insights.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Complete Session A | Coaching generated | ☐ |
| 2 | Note Session A insights | Record headlines | ☐ |
| 3 | Complete Session B | Different lap profile | ☐ |
| 4 | Note Session B insights | Record headlines | ☐ |
| 5 | Compare insights | Different advice | ☐ |
| 6 | Context-specific | References correct session | ☐ |

---

## Test Suite: AI-QUAL — Coaching Quality

### AI-QUAL-01: Driving Coach Persona

**Objective:** Verify AI acts as a motorsport driving coach.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Read all coaching insights | Full content review | ☐ |
| 2 | Tone is professional | Coach-like language | ☐ |
| 3 | Racing terminology used | Braking, apex, throttle, line | ☐ |
| 4 | Constructive feedback | Encouraging, not critical | ☐ |
| 5 | No irrelevant content | Focused on driving | ☐ |

**Requirement Coverage:** AI-04

---

### AI-QUAL-02: Actionable Advice

**Objective:** Verify insights are actionable by the driver.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Read each insight | Understand advice | ☐ |
| 2 | Advice is actionable | Driver can implement | ☐ |
| 3 | Specific suggestions | "Brake later", not "Be better" | ☐ |
| 4 | References locations | Specific corners, sectors | ☐ |
| 5 | Measurable goals | Time improvements mentioned | ☐ |

---

### AI-QUAL-03: No Hallucinations

**Objective:** Verify AI doesn't make up data not in session.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Read insights referencing lap numbers | Note lap numbers mentioned | ☐ |
| 2 | Verify lap numbers exist | Within session lap count | ☐ |
| 3 | Read sector references | Note sectors mentioned | ☐ |
| 4 | Verify sectors are 1, 2, or 3 | Only valid sectors | ☐ |
| 5 | Time references plausible | Match actual lap times | ☐ |

---

### AI-QUAL-04: Variety in Insights

**Objective:** Verify insights cover different aspects.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Read all 4 insights | Full review | ☐ |
| 2 | Topics are varied | Not all same advice | ☐ |
| 3 | Different sectors addressed | If applicable | ☐ |
| 4 | Different techniques | Braking, throttle, line, etc. | ☐ |
| 5 | Comprehensive coverage | Well-rounded advice | ☐ |

---

## AI Coaching Tests Summary

| Test ID | Test Name | Status |
|---------|-----------|--------|
| AI-01 | Coaching Trigger After Lap Detection | ☐ Pass ☐ Fail |
| AI-02 | Coaching Insight Count | ☐ Pass ☐ Fail |
| AI-03 | Insight Headline Quality | ☐ Pass ☐ Fail |
| AI-04 | Insight Detail Quality | ☐ Pass ☐ Fail |
| AI-05 | Insights Reference Session Data | ☐ Pass ☐ Fail |
| AI-06 | Consistency Score Accuracy | ☐ Pass ☐ Fail |
| AI-07 | Coaching Without Enough Laps | ☐ Pass ☐ Fail |
| AI-08 | Coaching API Failure Handling | ☐ Pass ☐ Fail |
| AI-09 | Coaching Insights Persistence | ☐ Pass ☐ Fail |
| AI-10 | Multiple Sessions Different Insights | ☐ Pass ☐ Fail |
| AI-QUAL-01 | Driving Coach Persona | ☐ Pass ☐ Fail |
| AI-QUAL-02 | Actionable Advice | ☐ Pass ☐ Fail |
| AI-QUAL-03 | No Hallucinations | ☐ Pass ☐ Fail |
| AI-QUAL-04 | Variety in Insights | ☐ Pass ☐ Fail |

---

**Tester:** ___________________ **Date:** ___________________

**Notes:**
```




```

---

*Document ID: SAT-AI-001 | Version: 1.0 | Date: 2026-05-06*
