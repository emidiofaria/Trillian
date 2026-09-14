# Session Results Tests

> **Purpose:** Validate the Session Result screen including all four tabs (Laps, Coach, Chart, Analysis), lap comparison, and data presentation.

---

## Test Environment

| Item | Required State | Check |
|------|----------------|-------|
| Session | Completed with 3+ laps, COMPLETE status | ☐ |
| Coaching | AI insights generated | ☐ |
| Device | App installed, user logged in | ☐ |

---

## Test Suite: RES — Results Screen

### RES-01: Session Result Screen Layout

**Objective:** Verify Session Result screen displays correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Complete a session or open existing | Session Result screen | ☐ |
| 2 | Observe toolbar | Back button, share icon | ☐ |
| 3 | Observe header | Track name visible | ☐ |
| 4 | Observe best lap time | Large format in header | ☐ |
| 5 | Observe TabLayout | 4 tabs visible | ☐ |
| 6 | Tab labels | "LAPS", "COACH", "CHART", "ANALYSIS" | ☐ |
| 7 | Default tab | LAPS selected | ☐ |
| 8 | ViewPager2 area | Content visible below tabs | ☐ |

**Requirement Coverage:** Session Result layout requirements

---

### RES-02: Tab Navigation

**Objective:** Verify tabs switch correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On Session Result screen | LAPS tab active | ☐ |
| 2 | Tap "COACH" tab | Coach content appears | ☐ |
| 3 | Tab indicator moves | Visual feedback | ☐ |
| 4 | Tap "CHART" tab | Chart content appears | ☐ |
| 5 | Swipe left | Returns to COACH | ☐ |
| 6 | Swipe left again | Returns to LAPS | ☐ |
| 7 | Tab states persist | Returning shows same content | ☐ |

---

## Test Suite: LAP — Laps Tab

### LAP-01: Lap List Display

**Objective:** Verify lap list shows all laps correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On LAPS tab | Lap list visible | ☐ |
| 2 | Count lap cards | Matches detected lap count | ☐ |
| 3 | Lap order | Sorted by lap time (fastest first) | ☐ |
| 4 | Each card shows | Lap number | ☐ |
| 5 | Each card shows | Lap time (M:SS.mmm format) | ☐ |
| 6 | Each card shows | Sector times (S1, S2, S3) | ☐ |
| 7 | Scroll list | All laps accessible | ☐ |

**Requirement Coverage:** LC-01, LC-02

---

### LAP-02: Best Lap Styling

**Objective:** Verify best lap has distinctive styling.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Identify best lap card | First card (fastest time) | ☐ |
| 2 | Left border color | Gold (#C9A227) | ☐ |
| 3 | Border width | ~3dp visible accent | ☐ |
| 4 | "BEST" badge/chip | Visible on card | ☐ |
| 5 | Badge color | Gold or contrasting | ☐ |
| 6 | Only one card has styling | Other cards normal | ☐ |

**Requirement Coverage:** LC-03

---

### LAP-03: Delta Badge Colors

**Objective:** Verify delta badges show correct colors.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Find a lap slower than best | Delta > 0 | ☐ |
| 2 | Delta badge color | RED (#E74C3C) | ☐ |
| 3 | Delta format | "+X.XXXs" (e.g., "+1.234s") | ☐ |
| 4 | Find fastest lap | Delta = 0 | ☐ |
| 5 | No delta badge | Or shows "—" | ☐ |
| 6 | Verify precision | Always 3 decimal places | ☐ |

**Requirement Coverage:** LC-04, LC-05

---

### LAP-04: Lap Card Tap Navigation

**Objective:** Verify tapping lap navigates to detail.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Tap a lap card (not best) | Navigation occurs | ☐ |
| 2 | Lap Detail screen appears | Detail view shown | ☐ |
| 3 | Correct lap displayed | Selected lap data | ☐ |
| 4 | Back button | Returns to Session Result | ☐ |
| 5 | Tap best lap card | Lap Detail for best | ☐ |

**Requirement Coverage:** LC-06

---

## Test Suite: DET — Lap Detail Screen

### DET-01: Lap Detail Layout

**Objective:** Verify Lap Detail screen layout.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open Lap Detail | Screen displayed | ☐ |
| 2 | Toolbar title | "Lap N vs Best Lap" | ☐ |
| 3 | Two-column header | Selected lap | Best lap | ☐ |
| 4 | Selected lap time | Large format | ☐ |
| 5 | Best lap time | Large format | ☐ |
| 6 | Total delta shown | Colored appropriately | ☐ |

**Requirement Coverage:** LC-07

---

### DET-02: Sector Comparison

**Objective:** Verify sector-by-sector comparison.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Observe sector rows | 3 rows (S1, S2, S3) | ☐ |
| 2 | Each row shows | Sector label ("Sector 1", etc.) | ☐ |
| 3 | Each row shows | Selected lap sector time | ☐ |
| 4 | Each row shows | Best lap sector time | ☐ |
| 5 | Each row shows | Delta badge (+/- time) | ☐ |
| 6 | Delta colors | Green if faster, red if slower | ☐ |
| 7 | Performance bar | Visual relative indication | ☐ |

**Requirement Coverage:** LC-08

---

### DET-03: Monospace Formatting

**Objective:** Verify times use monospace font.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Observe sector times | Monospace font | ☐ |
| 2 | Numbers align vertically | Equal character width | ☐ |
| 3 | Consistent formatting | All times same format | ☐ |

**Requirement Coverage:** LC-07, LC-08

---

## Test Suite: COACH — Coach Tab

### COACH-01: Consistency Score Display

**Objective:** Verify consistency score is shown correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Tap COACH tab | Coach content shown | ☐ |
| 2 | Find summary card | Top of content area | ☐ |
| 3 | Consistency score | Large percentage (e.g., "87.3%") | ☐ |
| 4 | Subtitle | "across N laps" | ☐ |
| 5 | Score range | 0-100% (clamped) | ☐ |
| 6 | Score format | 1 decimal place | ☐ |

**Requirement Coverage:** AI-13, DP-05

---

### COACH-02: Coaching Insight Cards

**Objective:** Verify coaching insights display correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | On COACH tab | Content loaded | ☐ |
| 2 | Insight cards visible | 3-5 cards | ☐ |
| 3 | Each card has headline | H3 style, bold | ☐ |
| 4 | Each card has detail | Body style | ☐ |
| 5 | Headline length | ≤8 words typically | ☐ |
| 6 | Detail provides context | Actionable advice | ☐ |
| 7 | Cards scrollable | If more than screen height | ☐ |

**Requirement Coverage:** AI-05, AI-06, AI-11

---

### COACH-03: Processing In Progress UI

**Objective:** Verify UI while processing is not complete.

**Precondition:** Session with processingStatus != COMPLETE.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open session still processing | Session Result shown | ☐ |
| 2 | Tap COACH tab | Processing card visible | ☐ |
| 3 | Card shows | "Processing your session..." | ☐ |
| 4 | Progress steps visible | Uploading, Detecting, Coaching | ☐ |
| 5 | Step indicators | ○ pending, ● in progress, ✓ done | ☐ |
| 6 | Steps update | As status changes | ☐ |
| 7 | On completion | Coaching insights replace card | ☐ |

**Requirement Coverage:** AI-12

---

### COACH-04: Failed Processing UI

**Objective:** Verify UI when processing fails.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open session with processingStatus=FAILED | Session Result shown | ☐ |
| 2 | Tap COACH tab | Error state visible | ☐ |
| 3 | "RETRY ANALYSIS" button | Visible | ☐ |
| 4 | Tap RETRY button | Re-enqueues upload worker | ☐ |
| 5 | Processing restarts | Status updates | ☐ |

**Requirement Coverage:** AI-14, TU-13

---

## Test Suite: CHART — Chart Tab

### CHART-01: Speed Chart Display

**Objective:** Verify speed chart renders correctly.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Tap CHART tab | Chart area visible | ☐ |
| 2 | Chart renders | No crash | ☐ |
| 3 | X-axis | Lap progress (distance or time) | ☐ |
| 4 | Y-axis | Speed in km/h | ☐ |
| 5 | Y-axis label | "km/h" or similar | ☐ |
| 6 | Grid lines | Visible at ~20% opacity | ☐ |
| 7 | Background | Dark theme | ☐ |

**Requirement Coverage:** LC-09

---

### CHART-02: Lap Line Colors

**Objective:** Verify lap lines have correct colors.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Observe chart lines | Multiple lines visible | ☐ |
| 2 | Best lap line | brand blue (#1C69D4) | ☐ |
| 3 | Other lap lines | Grey (#444444) | ☐ |
| 4 | Best lap distinguishable | Clearly stands out | ☐ |
| 5 | Legend visible | "Best Lap" (blue), "Other laps" (grey) | ☐ |

**Requirement Coverage:** LC-09

---

### CHART-03: Speed Unit Conversion

**Objective:** Verify speed displayed in km/h.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Observe chart Y-axis values | Speed numbers | ☐ |
| 2 | Expected range | 0-200 km/h (typical) | ☐ |
| 3 | Not m/s values | Would be 0-60 range | ☐ |
| 4 | Peak speed reasonable | Matches driving speed | ☐ |

**Requirement Coverage:** LC-10

---

### CHART-04: Chart Interactions

**Objective:** Verify chart interaction behaviors.

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Pinch to zoom | Chart zooms | ☐ |
| 2 | Pan/drag | Chart scrolls | ☐ |
| 3 | Double-tap | Resets zoom (or no-op) | ☐ |
| 4 | Touch data point | May show value tooltip | ☐ |

---

## Test Suite: ANA — Analysis Tab

> **Requirements:** AS-01 to AS-17. The maths is covered by L1 and the wiring by L2; these
> checks exist for the things only a human at a circuit can judge — whether the drawn track
> actually looks like the track, and whether the corners the app found are the corners the
> driver drove.

### ANA-01: Analysis Tab Layout

**Objective:** Verify the ANALYSIS tab is reachable and complete (AS-01, AS-02).

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open a completed session | Session Result screen | ☐ |
| 2 | Swipe/tap to the 4th tab | Tab labelled "ANALYSIS" opens | ☐ |
| 3 | Observe top card | Distance, duration, max speed, avg speed, best lap all populated | ☐ |
| 4 | Compare distance to the circuit length × laps | Within ~5% | ☐ |
| 5 | Compare best lap to the LAPS tab | Identical value | ☐ |

### ANA-02: Track Map Fidelity

**Objective:** Verify the drawn map matches the real circuit (AS-04, AS-05).

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Observe the track outline | Recognisably the circuit you drove | ☐ |
| 2 | Observe the line colour | Blue in the slow corners, green down the straights | ☐ |
| 3 | Observe red sections | Located where you actually braked | ☐ |
| 4 | Observe the gold marker | On the start/finish line | ☐ |
| 5 | Put the phone in flight mode and reopen the tab | Map still draws, identically | ☐ |

### ANA-03: Corner Detection Plausibility

**Objective:** Verify detected corners correspond to real corners (AS-08, AS-09).

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Count the T-markers on the map | Comparable to the circuit's corner count | ☐ |
| 2 | Check each corner's direction | Matches the real turn direction | ☐ |
| 3 | Check apex speeds | Plausible for the corner (slowest hairpin = lowest number) | ☐ |
| 4 | Record any corner the app missed or invented | Note in the box below | ☐ |

### ANA-04: Braking Zones

**Objective:** Verify braking figures are plausible (AS-10, AS-12).

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Observe the braking list | One entry per heavy braking point you remember | ☐ |
| 2 | Check peak g values | Typically 0.1–1.0 g for a road car on a circuit | ☐ |
| 3 | Check the corner association | Each zone names the corner it leads into | ☐ |

### ANA-05: Reference Lap Selection

**Objective:** Verify lap selection changes the analysis (AS-06, AS-07).

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open the tab | Best lap chip is already selected | ☐ |
| 2 | Read the line above the map | Names the selected lap | ☐ |
| 3 | Tap a different lap chip | Map, corners and braking all redraw | ☐ |
| 4 | Read the line above the map again | Names the newly selected lap | ☐ |
| 5 | Compare a slow lap to the best lap | Apex speeds differ in the expected direction | ☐ |

### ANA-06: Degraded Sessions

**Objective:** Verify honest behaviour with missing or unusable data (AS-16, AS-17).

| Step | Action | Expected Result | Pass/Fail |
|------|--------|-----------------|-----------|
| 1 | Open a session whose telemetry file was deleted | "…no longer on this device" message, no crash | ☐ |
| 2 | Open a very short recording (< 2 samples) | "too little telemetry to analyse" | ☐ |
| 3 | Open a session with no detected laps | Analysis shown, labelled "whole session" | ☐ |
| 4 | Open a stationary recording (phone left on a bench) | No corners invented | ☐ |

---

## Session Results Tests Summary

| Test ID | Test Name | Status |
|---------|-----------|--------|
| RES-01 | Session Result Screen Layout | ☐ Pass ☐ Fail |
| RES-02 | Tab Navigation | ☐ Pass ☐ Fail |
| LAP-01 | Lap List Display | ☐ Pass ☐ Fail |
| LAP-02 | Best Lap Styling | ☐ Pass ☐ Fail |
| LAP-03 | Delta Badge Colors | ☐ Pass ☐ Fail |
| LAP-04 | Lap Card Tap Navigation | ☐ Pass ☐ Fail |
| DET-01 | Lap Detail Layout | ☐ Pass ☐ Fail |
| DET-02 | Sector Comparison | ☐ Pass ☐ Fail |
| DET-03 | Monospace Formatting | ☐ Pass ☐ Fail |
| COACH-01 | Consistency Score Display | ☐ Pass ☐ Fail |
| COACH-02 | Coaching Insight Cards | ☐ Pass ☐ Fail |
| COACH-03 | Processing In Progress UI | ☐ Pass ☐ Fail |
| COACH-04 | Failed Processing UI | ☐ Pass ☐ Fail |
| CHART-01 | Speed Chart Display | ☐ Pass ☐ Fail |
| CHART-02 | Lap Line Colors | ☐ Pass ☐ Fail |
| CHART-03 | Speed Unit Conversion | ☐ Pass ☐ Fail |
| CHART-04 | Chart Interactions | ☐ Pass ☐ Fail |
| ANA-01 | Analysis Tab Layout | ☐ Pass ☐ Fail |
| ANA-02 | Track Map Fidelity | ☐ Pass ☐ Fail |
| ANA-03 | Corner Detection Plausibility | ☐ Pass ☐ Fail |
| ANA-04 | Braking Zones | ☐ Pass ☐ Fail |
| ANA-05 | Reference Lap Selection | ☐ Pass ☐ Fail |
| ANA-06 | Degraded Sessions | ☐ Pass ☐ Fail |

---

**Tester:** ___________________ **Date:** ___________________

**Notes:**
```




```

---

*Document ID: SAT-RES-001 | Version: 1.0 | Date: 2026-05-06*
