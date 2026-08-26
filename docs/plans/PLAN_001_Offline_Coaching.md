# Implementation Plan: Offline Coaching Features

> **Plan ID:** PLAN-001  
> **Created:** 2026-08-04  
> **Status:** Draft  
> **Priority:** High  
> **Estimated Effort:** 3-4 weeks

---

## 1. Executive Summary

Replace the cloud-based AI coaching (Claude API) with a **rule-based, on-device coaching system** that analyzes telemetry data locally and provides actionable driving feedback — all without internet connectivity or ongoing API costs.

---

## 2. Problem Statement

### Current State
- AI coaching requires backend server + Claude API
- Each coaching analysis costs money (API tokens)
- Users must wait for upload + processing
- No coaching available offline (common at tracks)

### Desired State
- Coaching works instantly after recording stops
- Zero ongoing costs (no API calls)
- Works completely offline
- Provides actionable, data-driven feedback

---

## 3. Feature Description

### 3.1 What Is Offline Coaching?

A **rule-based analysis engine** that examines the user's telemetry data and generates driving tips based on detected patterns. Instead of AI writing custom prose, we use:

1. **Pattern Detection Algorithms** — Analyze GPS/speed data to find specific behaviors
2. **Pre-written Tip Templates** — Human-authored coaching advice for each pattern
3. **Parameterized Feedback** — Tips include specific numbers from the user's data

### 3.2 User Experience

```
┌─────────────────────────────────────────────────────────────┐
│  AFTER RECORDING STOPS                                      │
│                                                             │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐   │
│  │   Record    │     │   Analyze   │     │   Display   │   │
│  │   Stops     │ ──► │   Locally   │ ──► │   Tips      │   │
│  │             │     │  (2-5 sec)  │     │             │   │
│  └─────────────┘     └─────────────┘     └─────────────┘   │
│                                                             │
│  ● No upload needed                                         │
│  ● No internet required                                     │
│  ● Instant results                                          │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 Example Coaching Output

```
┌────────────────────────────────────────────────────────────┐
│  🏁 COACHING INSIGHTS                                      │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  📍 CONSISTENCY                                            │
│  Your braking point at Turn 3 varies by 18 meters          │
│  between laps. Consistent braking = consistent times.      │
│  Focus on picking a visual marker.                         │
│                                                            │
│  ────────────────────────────────────────────────────────  │
│                                                            │
│  🚀 CORNER EXIT                                            │
│  Turn 5 exit speed: 78 km/h (avg) vs 86 km/h (best lap)   │
│  You're losing 0.4s here. Try earlier throttle application.│
│                                                            │
│  ────────────────────────────────────────────────────────  │
│                                                            │
│  ⭐ STRENGTH                                               │
│  Your Sector 1 is consistently within 0.2s — great         │
│  consistency! This is your strongest section.              │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## 4. Technical Design

### 4.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  TELEMETRY FILE (.jsonl)                                        │
│         │                                                       │
│         ▼                                                       │
│  ┌─────────────────┐                                            │
│  │ TelemetryParser │  Parse GPS points, speed, timestamps       │
│  └────────┬────────┘                                            │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────┐                                            │
│  │  LapSegmenter   │  Split data by lap (using LocalLapDetector)│
│  └────────┬────────┘                                            │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              PATTERN DETECTORS                          │    │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐     │    │
│  │  │   Braking    │ │   Corner     │ │ Consistency  │     │    │
│  │  │   Analyzer   │ │   Analyzer   │ │   Analyzer   │     │    │
│  │  └──────────────┘ └──────────────┘ └──────────────┘     │    │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐     │    │
│  │  │   Sector     │ │   Speed      │ │   Strength   │     │    │
│  │  │   Analyzer   │ │   Analyzer   │ │   Finder     │     │    │
│  │  └──────────────┘ └──────────────┘ └──────────────┘     │    │
│  └─────────────────────────────────────────────────────────┘    │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────┐                                            │
│  │  TipGenerator   │  Match patterns to tip templates           │
│  └────────┬────────┘                                            │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────┐                                            │
│  │  CoachingResult │  List<CoachingInsight> for UI              │
│  └─────────────────┘                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 Pattern Detectors

| Detector | What It Finds | Data Used |
|----------|---------------|-----------|
| `BrakingAnalyzer` | Braking points, consistency, late/early braking | Speed drops > 10 km/h |
| `CornerAnalyzer` | Corner entry/exit speeds, apex speeds | Local speed minimums |
| `ConsistencyAnalyzer` | Lap-to-lap variation at key points | Position + speed variance |
| `SectorAnalyzer` | Best/worst sectors, time deltas | Track split into 3 sectors |
| `SpeedAnalyzer` | Top speed, average speed, speed scrubbing | Raw speed data |
| `StrengthFinder` | What the driver does well | Low variance = strength |

### 4.3 Key Classes

```kotlin
// Domain models
data class AnalysisPoint(
    val lapIndex: Int,
    val distanceFromStart: Double,  // meters
    val speed: Double,              // km/h
    val timestamp: Long,
    val lat: Double,
    val lon: Double
)

data class DetectedPattern(
    val type: PatternType,
    val location: Double,           // distance from start
    val severity: Severity,         // LOW, MEDIUM, HIGH
    val parameters: Map<String, Any> // e.g., "variance" -> 18.5
)

enum class PatternType {
    INCONSISTENT_BRAKING,
    EARLY_BRAKING,
    LATE_BRAKING,
    SLOW_CORNER_EXIT,
    SPEED_SCRUBBING,
    INCONSISTENT_LINE,
    WEAK_SECTOR,
    STRONG_SECTOR,
    GOOD_CONSISTENCY
}

data class CoachingInsight(
    val category: InsightCategory,  // CONSISTENCY, SPEED, BRAKING, STRENGTH
    val title: String,
    val message: String,
    val priority: Int               // 1 = most important
)
```

### 4.4 Tip Template System

```kotlin
// Tip templates with placeholders
object TipTemplates {
    val INCONSISTENT_BRAKING = TipTemplate(
        category = InsightCategory.CONSISTENCY,
        title = "Braking Consistency",
        template = "Your braking point at {location} varies by {variance}m between laps. " +
                   "Consistent braking = consistent times. Pick a visual marker and hit it every lap."
    )
    
    val SLOW_CORNER_EXIT = TipTemplate(
        category = InsightCategory.SPEED,
        title = "Corner Exit Speed",
        template = "Corner at {location}: exit speed {avg_speed} km/h vs {best_speed} km/h on your best lap. " +
                   "You're losing ~{time_loss}s here. Try earlier throttle application."
    )
    
    val STRONG_SECTOR = TipTemplate(
        category = InsightCategory.STRENGTH,
        title = "Your Strength",
        template = "Your {sector} is consistently within {variance}s — great consistency! " +
                   "This is your strongest section."
    )
    
    // ... 15-20 more templates
}
```

### 4.5 File Structure

```
app/src/main/java/com/drivingcoach/
├── domain/
│   └── coaching/
│       ├── CoachingEngine.kt           # Main orchestrator
│       ├── model/
│       │   ├── AnalysisPoint.kt
│       │   ├── DetectedPattern.kt
│       │   ├── CoachingInsight.kt
│       │   └── PatternType.kt
│       ├── analyzer/
│       │   ├── PatternAnalyzer.kt      # Interface
│       │   ├── BrakingAnalyzer.kt
│       │   ├── CornerAnalyzer.kt
│       │   ├── ConsistencyAnalyzer.kt
│       │   ├── SectorAnalyzer.kt
│       │   └── StrengthFinder.kt
│       ├── tips/
│       │   ├── TipTemplate.kt
│       │   ├── TipTemplates.kt         # All templates
│       │   └── TipGenerator.kt
│       └── util/
│           ├── TelemetryParser.kt
│           └── LapSegmenter.kt
```

---

## 5. Implementation Tasks

### Phase 1: Core Infrastructure (Week 1)

| Task | Description | Est. Hours |
|------|-------------|------------|
| 1.1 | Create domain models (`AnalysisPoint`, `DetectedPattern`, `CoachingInsight`) | 2h |
| 1.2 | Implement `TelemetryParser` — read JSONL, output `List<AnalysisPoint>` | 4h |
| 1.3 | Implement `LapSegmenter` — split points by lap using existing `LocalLapDetector` | 3h |
| 1.4 | Create `PatternAnalyzer` interface and base class | 2h |
| 1.5 | Write unit tests for parser and segmenter | 3h |

### Phase 2: Pattern Analyzers (Week 2)

| Task | Description | Est. Hours |
|------|-------------|------------|
| 2.1 | Implement `BrakingAnalyzer` — detect braking zones, measure consistency | 6h |
| 2.2 | Implement `CornerAnalyzer` — detect corners, measure entry/exit speeds | 6h |
| 2.3 | Implement `ConsistencyAnalyzer` — lap-to-lap variance at key points | 4h |
| 2.4 | Implement `SectorAnalyzer` — divide track, compare sector times | 4h |
| 2.5 | Implement `StrengthFinder` — identify what driver does well | 3h |
| 2.6 | Write unit tests for all analyzers | 6h |

### Phase 3: Tip Generation (Week 3)

| Task | Description | Est. Hours |
|------|-------------|------------|
| 3.1 | Create `TipTemplate` model and template string system | 2h |
| 3.2 | Write 20 tip templates covering all pattern types | 4h |
| 3.3 | Implement `TipGenerator` — match patterns to templates, fill placeholders | 4h |
| 3.4 | Implement `CoachingEngine` — orchestrate full analysis pipeline | 4h |
| 3.5 | Add priority/ranking logic (show most important tips first) | 2h |
| 3.6 | Write unit tests for tip generation | 4h |

### Phase 4: Integration (Week 4)

| Task | Description | Est. Hours |
|------|-------------|------------|
| 4.1 | Create `LocalCoachingRepository` — Hilt-injectable wrapper | 3h |
| 4.2 | Modify `SessionResultViewModel` to use local coaching | 4h |
| 4.3 | Update UI to show local insights (reuse existing `CoachingInsight` display) | 3h |
| 4.4 | Remove/disable cloud coaching code paths | 2h |
| 4.5 | Add loading state during analysis (~2-5 seconds) | 2h |
| 4.6 | Integration tests with real telemetry files | 6h |
| 4.7 | Documentation sync (Atlas, SRS, User Manual) | 3h |

---

## 6. Acceptance Criteria

### Functional Requirements

- [ ] Coaching insights appear within 5 seconds of recording stop
- [ ] At least 3 insights generated for any session with 3+ laps
- [ ] Insights include specific numbers from user's data
- [ ] Works completely offline (airplane mode)
- [ ] Insights persist in Room database (same as before)

### Quality Requirements

- [ ] All analyzers have >80% unit test coverage
- [ ] Analysis completes in <5 seconds for 20-lap session
- [ ] No crashes on edge cases (1 lap, GPS gaps, short laps)
- [ ] Tips are grammatically correct and actionable

### Non-Functional Requirements

- [ ] Zero network calls for coaching
- [ ] Memory usage <50MB during analysis
- [ ] Battery impact negligible

---

## 7. Risks & Mitigations

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Pattern detection accuracy | Users get wrong advice | Medium | Validate against real track data; conservative thresholds |
| Tips feel "robotic" | Lower perceived value | Medium | Invest in well-written templates; A/B test wording |
| Edge cases (short laps, GPS gaps) | Crashes or bad tips | High | Defensive coding; minimum data thresholds |
| Performance on old devices | Slow analysis | Low | Profile on low-end devices; optimize if needed |

---

## 8. Out of Scope

- Comparison between sessions ("you improved since last time")
- Track maps / corner visualization
- Machine learning models
- Cloud backup of insights
- Multi-language tip templates (English only for V1)

---

## 9. Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Analysis completion rate | >99% | No crashes during analysis |
| User views coaching tab | >60% of sessions | Firebase event (if added) |
| Insights per session | 3-6 average | Logging |
| User retention | No decrease | Compare before/after |

---

## 10. Dependencies

- Existing `LocalLapDetector` for lap boundaries
- Existing `TelemetryFileWriter` format (JSONL)
- Existing Room entities (`CoachingInsightEntity`)
- Test telemetry files for validation

---

## 11. Open Questions

1. Should we keep the cloud coaching as a "premium" option, or remove entirely?
2. What minimum number of laps should we require for analysis? (Recommend: 2)
3. Should tips reference specific corner numbers, or just "Turn at 450m"?
4. Do we want tip categories (Braking, Speed, Consistency) or flat list?

---

*Document Version: 1.0*
