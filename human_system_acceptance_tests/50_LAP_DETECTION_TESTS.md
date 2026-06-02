# Lap Detection Tests

> **Purpose:** Validate server-side lap detection including start/finish line crossing, sector computation, and best lap identification. **These tests require actual driving across a defined start/finish line.**

---

## Test Environment

| Item | Required State | Check |
|------|----------------|-------|
| Backend | Running, accessible from track | ☐ |
| Track | Clear start/finish line location | ☐ |
| Course | Minimum 200m from start to furthest point | ☐ |
| Laps | Ability to complete 3+ laps | ☐ |
| Lap time | >20 seconds per lap | ☐ |

---

## Test Suite: LD — Lap Detection

### LD-01: Basic Lap Detection

**Objective:** Verify backend detects laps when crossing start/finish line.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Set start/finish line at track start | Line defined | ☐ |
| 2 | Start recording | Recording active | ☐ |
| 3 | Drive out lap (away from start) | First 10+ seconds | ☐ |
| 4 | Complete lap 1 (cross line) | Pass start/finish | ☐ |
| 5 | Complete lap 2 (cross line) | Pass start/finish | ☐ |
| 6 | Complete lap 3 (cross line) | Pass start/finish | ☐ |
| 7 | Stop recording | Session ends | ☐ |
| 8 | Wait for processing | Polling status | ☐ |
| 9 | Processing completes | Status = COMPLETE | ☐ |
| 10 | Laps tab shows | 3 laps detected | ☐ |

**Requirement Coverage:** LD-01, LD-02, LD-04

---

### LD-02: Out-Lap Ignored (10-Second Skip)

**Objective:** Verify first 10 seconds of session are ignored.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Set start/finish line | Line defined | ☐ |
| 2 | Start recording AT the start line | On the line | ☐ |
| 3 | Wait 5 seconds stationary | On the line | ☐ |
| 4 | Cross line at ~6 seconds | Should be ignored | ☐ |
| 5 | Drive away, complete valid laps | 2+ laps | ☐ |
| 6 | Stop and process | Processing completes | ☐ |
| 7 | Check lap count | No false lap from early crossing | ☐ |

**Requirement Coverage:** LD-03

---

### LD-03: Minimum Lap Time Guard (20 seconds)

**Objective:** Verify crossings under 20 seconds are ignored.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Set up very short loop | Small area | ☐ |
| 2 | Start recording | Recording active | ☐ |
| 3 | Complete a "lap" in ~15 seconds | Cross line quickly | ☐ |
| 4 | Complete another in ~15 seconds | Cross again | ☐ |
| 5 | Complete valid laps (>20s each) | Normal laps | ☐ |
| 6 | Stop and process | Processing completes | ☐ |
| 7 | Check lap data | Short crossings ignored | ☐ |

**Requirement Coverage:** LD-05

---

### LD-04: Last Incomplete Lap Discarded

**Objective:** Verify incomplete lap (no final crossing) is not counted.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording | Recording active | ☐ |
| 2 | Complete lap 1, 2, 3 | Cross line 3 times | ☐ |
| 3 | Start lap 4 | Drive halfway | ☐ |
| 4 | Stop recording mid-lap | Before crossing line | ☐ |
| 5 | Wait for processing | Status = COMPLETE | ☐ |
| 6 | Check lap count | 3 laps (not 4) | ☐ |
| 7 | No partial lap data | Only complete laps | ☐ |

**Requirement Coverage:** LD-06

---

### LD-05: Insufficient Samples Handling

**Objective:** Verify very short session fails gracefully.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording | Recording active | ☐ |
| 2 | Stop after 3 seconds | Very short session | ☐ |
| 3 | Wait for processing | Processing runs | ☐ |
| 4 | Check status | processingStatus = FAILED | ☐ |
| 5 | Error indication | "Insufficient samples" or similar | ☐ |

**Requirement Coverage:** LD-07

---

### LD-06: Fewer Than 2 Laps Handling

**Objective:** Verify session with <2 laps fails appropriately.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Start recording | Recording active | ☐ |
| 2 | Complete only 1 lap | Single crossing | ☐ |
| 3 | Stop recording | Session ends | ☐ |
| 4 | Wait for processing | Processing runs | ☐ |
| 5 | Check status | processingStatus = FAILED | ☐ |
| 6 | Laps tab | Shows error/no laps | ☐ |
| 7 | User notification | Clear message about minimum laps | ☐ |

**Requirement Coverage:** LD-08

---

### LD-07: Sector Computation

**Objective:** Verify each lap is divided into 3 sectors.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Complete a valid session (3+ laps) | Laps detected | ☐ |
| 2 | Open LAPS tab | Lap cards visible | ☐ |
| 3 | Tap on a lap card | Lap Detail screen | ☐ |
| 4 | Observe sector times | S1, S2, S3 shown | ☐ |
| 5 | Sector 1 + Sector 2 + Sector 3 | = Total lap time | ☐ |
| 6 | Verify for multiple laps | All laps have sectors | ☐ |

**Requirement Coverage:** LD-09

---

### LD-08: Best Lap Identification

**Objective:** Verify fastest lap is correctly identified.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Complete session with varied lap times | Intentionally vary pace | ☐ |
| 2 | Note fastest lap subjectively | Know which was fastest | ☐ |
| 3 | Open LAPS tab | Lap list shown | ☐ |
| 4 | Identify best lap card | Gold border accent | ☐ |
| 5 | "BEST" badge visible | On fastest lap | ☐ |
| 6 | Verify lap time | Lowest durationMs | ☐ |
| 7 | Exactly one best lap | No ties shown as best | ☐ |

**Requirement Coverage:** LD-10

---

### LD-09: Processing Status Progression

**Objective:** Verify status flows correctly through stages.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Complete and stop recording | Session created | ☐ |
| 2 | Observe initial status | PENDING | ☐ |
| 3 | Observe during upload | UPLOADING (brief) | ☐ |
| 4 | Observe during detection | PROCESSING or DETECTING_LAPS | ☐ |
| 5 | Observe after laps detected | LAPS_DONE (may be brief) | ☐ |
| 6 | Final status | COMPLETE | ☐ |
| 7 | Steps indicator | Shows progression ○ → ● → ✓ | ☐ |

**Requirement Coverage:** LD-11, AI-12

---

### LD-10: Processing Time Performance

**Objective:** Verify processing completes within acceptable time.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Complete session with 10 laps | ~20 minute session | ☐ |
| 2 | Stop and note timestamp | T0 | ☐ |
| 3 | Wait for COMPLETE status | Polling... | ☐ |
| 4 | Note completion timestamp | T1 | ☐ |
| 5 | Total processing time | T1 - T0 < 60 seconds | ☐ |

**Requirement Coverage:** NF-05

---

## Test Suite: LD-CROSS — Crossing Algorithm

### LD-CROSS-01: Perpendicular Crossing

**Objective:** Verify lap detected when crossing line at 90°.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Set line across straight section | Perpendicular to track | ☐ |
| 2 | Drive through line straight on | 90° approach | ☐ |
| 3 | Complete multiple laps | Consistent approach | ☐ |
| 4 | Verify all laps detected | Correct count | ☐ |

---

### LD-CROSS-02: Angled Crossing

**Objective:** Verify lap detected when crossing line at angle.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Set line at angle to track direction | 45° orientation | ☐ |
| 2 | Cross line at typical track angle | Racing line | ☐ |
| 3 | Complete multiple laps | Natural racing line | ☐ |
| 4 | Verify all laps detected | Correct count | ☐ |

---

### LD-CROSS-03: Line at Turn Entry/Exit

**Objective:** Verify detection works when line is at a corner.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Set line at a turn entry | Curved section | ☐ |
| 2 | Drive through, changing direction | Turning while crossing | ☐ |
| 3 | Complete multiple laps | Varying lines | ☐ |
| 4 | Verify all laps detected | Correct count | ☐ |

---

### LD-CROSS-04: Near-Miss Handling

**Objective:** Verify close passes without crossing don't count as laps.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Set start/finish line | Line defined | ☐ |
| 2 | On one lap, pass very close but don't cross | Miss line by 1-2m | ☐ |
| 3 | Cross normally on other laps | Valid crossings | ☐ |
| 4 | Check lap count | Near-miss not counted | ☐ |

---

## Lap Detection Tests Summary

| Test ID | Test Name | Status |
|---------|-----------|--------|
| LD-01 | Basic Lap Detection | ☐ Pass ☐ Fail |
| LD-02 | Out-Lap Ignored (10-Second Skip) | ☐ Pass ☐ Fail |
| LD-03 | Minimum Lap Time Guard (20 seconds) | ☐ Pass ☐ Fail |
| LD-04 | Last Incomplete Lap Discarded | ☐ Pass ☐ Fail |
| LD-05 | Insufficient Samples Handling | ☐ Pass ☐ Fail |
| LD-06 | Fewer Than 2 Laps Handling | ☐ Pass ☐ Fail |
| LD-07 | Sector Computation | ☐ Pass ☐ Fail |
| LD-08 | Best Lap Identification | ☐ Pass ☐ Fail |
| LD-09 | Processing Status Progression | ☐ Pass ☐ Fail |
| LD-10 | Processing Time Performance | ☐ Pass ☐ Fail |
| LD-CROSS-01 | Perpendicular Crossing | ☐ Pass ☐ Fail |
| LD-CROSS-02 | Angled Crossing | ☐ Pass ☐ Fail |
| LD-CROSS-03 | Line at Turn Entry/Exit | ☐ Pass ☐ Fail |
| LD-CROSS-04 | Near-Miss Handling | ☐ Pass ☐ Fail |

---

**Tester:** ___________________ **Date:** ___________________

**Track Configuration:** ___________________ 

**Approximate Lap Distance:** ___________________ 

**Notes:**
```




```

---

*Document ID: SAT-LD-001 | Version: 1.0 | Date: 2026-05-06*
