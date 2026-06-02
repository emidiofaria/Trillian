# instructions.md

Operational Intelligence System for BMW Driving Coach Android Application.

---

## System Purpose

This operational intelligence system exists to:

1. **Accelerate incident resolution** by providing structured, evidence-based Root Cause Analysis
2. **Accumulate operational knowledge** through systematic pattern extraction from resolved incidents
3. **Reduce mean-time-to-resolution (MTTR)** by matching new incidents to known failure patterns
4. **Prevent incident recurrence** by capturing systemic weaknesses and driving permanent fixes
5. **Enable continuous improvement** through human feedback incorporation

The system serves as the **operational memory** for the BMW Driving Coach Android application, encoding:
- How the system actually behaves at runtime
- How failures propagate through async boundaries
- Which failure patterns recur and how to recognize them
- What mitigations work and which permanent fixes are needed

---

## Operating Model

### Phase 1: Incident Ingestion

```
┌─────────────────────────────────────────────────────────┐
│                    INCIDENT SOURCES                      │
├─────────────────────────────────────────────────────────┤
│  • User reports (support tickets, app reviews)          │
│  • Crashlytics alerts                                   │
│  • Backend error spikes                                 │
│  • ANR reports                                          │
│  • WorkManager failure alerts                           │
│  • Custom monitoring alerts                             │
│  • Developer-reported issues                            │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                   INCIDENT TICKET                        │
├─────────────────────────────────────────────────────────┤
│  • Symptom description                                  │
│  • Timestamp / time range                               │
│  • Affected users / scope                               │
│  • App version / device info                            │
│  • Initial severity assessment                          │
└─────────────────────────────────────────────────────────┘
```

### Phase 2: Evidence Collection

The RCA engine requests and collects:

| Evidence Type | Source | Format |
|---------------|--------|--------|
| Logcat output | Device/Crashlytics | Text with timestamps |
| Stack traces | Crashlytics | Exception + frames |
| Worker state | WorkManager inspection | State enum + attempts |
| DB state | Room queries | Row data |
| Network logs | OkHttp interceptor | Request/response |
| Preferences | DataStore dump | Key-value pairs |
| File system | ADB shell | File listing |
| User context | Support ticket | Narrative |

**Evidence Completeness Check**:
Before proceeding, verify minimum evidence set:
- [ ] Symptom description
- [ ] Approximate timestamp
- [ ] App version
- [ ] At least one technical artifact (logs/trace/state)

### Phase 3: RCA Execution

```
┌─────────────────────────────────────────────────────────┐
│                    RCA ENGINE                            │
├─────────────────────────────────────────────────────────┤
│  1. Load Atlas files (system, components, flows)        │
│  2. Match against failure-patterns.md                   │
│  3. Localize to component + flow                        │
│  4. Correlate evidence temporally                       │
│  5. Generate hypotheses with confidence                 │
│  6. Determine root cause + trigger                      │
│  7. Produce structured RCA report                       │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                    RCA REPORT                            │
│  (Format defined in RCA agent.md)                       │
└─────────────────────────────────────────────────────────┘
```

### Phase 4: Human Review

```
┌─────────────────────────────────────────────────────────┐
│                 HUMAN OPERATOR REVIEW                    │
├─────────────────────────────────────────────────────────┤
│  • Verify root cause accuracy                           │
│  • Correct any misattributions                          │
│  • Add missing context                                  │
│  • Validate mitigation steps                            │
│  • Assess confidence appropriateness                    │
│  • Mark RCA as: ACCEPTED / CORRECTED / REJECTED         │
└─────────────────────────────────────────────────────────┘
```

### Phase 5: Feedback Incorporation

| Review Outcome | Action |
|----------------|--------|
| **ACCEPTED** | Increment pattern confidence; no Atlas update needed |
| **CORRECTED** | Update failure-patterns.md with corrected cause; add new signals |
| **REJECTED** | Document why RCA failed; add missing patterns/flows |

### Phase 6: Pattern Extraction

After each resolved incident, extract:

```
┌─────────────────────────────────────────────────────────┐
│                 PATTERN EXTRACTION                       │
├─────────────────────────────────────────────────────────┤
│  • Is this a new failure pattern?                       │
│    → Add to failure-patterns.md                         │
│                                                         │
│  • Does this reveal a hidden dependency?                │
│    → Update components.md                               │
│                                                         │
│  • Does this show a flow behavior not documented?       │
│    → Update flows.md                                    │
│                                                         │
│  • Were existing patterns insufficient?                 │
│    → Enhance pattern signals/causes                     │
└─────────────────────────────────────────────────────────┘
```

### Phase 7: Operational Learning

```
┌─────────────────────────────────────────────────────────┐
│                OPERATIONAL LEARNING                      │
├─────────────────────────────────────────────────────────┤
│  • Track pattern match rate                             │
│  • Track RCA acceptance rate                            │
│  • Identify gaps in Atlas coverage                      │
│  • Prioritize pattern updates by incident frequency     │
│  • Retire obsolete patterns after code fixes            │
└─────────────────────────────────────────────────────────┘
```

---

## Atlas Usage Rules

### system.md

**Purpose**: Infrastructure grounding and runtime topology awareness

**Used For**:
- Understanding tech stack constraints (Kotlin, Room, WorkManager, etc.)
- Identifying layer boundaries (UI → ViewModel → Repository → API)
- Grounding RCA in actual architecture, not assumed patterns

**When To Update**:
- Major architecture changes
- New technology adoption
- Removed components or patterns

**Update Responsibility**: Engineering team after architecture changes

---

### components.md

**Purpose**: Subsystem localization and failure domain mapping

**Used For**:
- Mapping symptoms to affected components
- Understanding component dependencies and cascade effects
- Identifying observable signals for each component
- Determining component criticality

**When To Update**:
- New component added
- Component dependencies change
- New failure modes discovered
- New observable signals identified
- Recovery behaviors change

**Update Responsibility**: 
- Engineering team after code changes
- Operations team after incident reveals undocumented behavior

---

### flows.md

**Purpose**: Execution tracing and propagation analysis

**Used For**:
- Tracing incident through execution steps
- Identifying async boundaries where delays occur
- Understanding persistence points where state is captured
- Mapping failure points to code locations

**When To Update**:
- Flow logic changes
- New async boundaries introduced
- Failure propagation differs from documented
- New persistence points added
- Retry/recovery behavior changes

**Update Responsibility**:
- Engineering team after flow changes
- Operations team after RCA reveals undocumented flow behavior

---

### failure-patterns.md

**Purpose**: Pattern matching and operational memory

**Used For**:
- Accelerating RCA by matching to known patterns
- Providing proven mitigation steps
- Guiding evidence collection based on pattern signals
- Tracking pattern confidence and frequency

**When To Update**:
- New failure pattern discovered
- Existing pattern has new signals
- Pattern root cause was misidentified
- Mitigation steps improved
- Pattern fixed permanently (retire or mark resolved)

**Update Responsibility**:
- Operations team after every significant incident
- Engineering team after implementing permanent fixes

---

### RCA agent.md

**Purpose**: Reasoning model and quality standards

**Used For**:
- Guiding RCA engine behavior
- Defining output format
- Establishing guardrails
- Component-specific investigation guides

**When To Update**:
- RCA quality standards change
- New reasoning patterns needed
- Component investigation guides need expansion
- Guardrails prove insufficient

**Update Responsibility**: RCA system maintainers

---

## Human Operator Responsibilities

### Mandatory Updates After Incidents

| Trigger | Required Update |
|---------|-----------------|
| RCA correction | Update failure-patterns.md with correct cause |
| New root cause discovered | Add new pattern to failure-patterns.md |
| Repeated incident (same pattern) | Increase pattern confidence; add any new signals |
| Flow behavior differs from docs | Update flows.md with actual behavior |
| Hidden dependency found | Update components.md dependencies |
| New signal discovered | Add to relevant component in components.md |
| Mitigation failed | Update pattern with corrected mitigation |
| Permanent fix deployed | Mark pattern as resolved; add prevention details |

### Update Format

When updating failure-patterns.md:

```markdown
## Pattern: [Name]

### Symptoms
[Add new symptoms observed]

### Signals
[Add new signals that helped diagnose]

### Likely Causes
[Correct or add causes based on RCA]

### Evidence To Check
[Add evidence sources that were useful]

### Common Triggers
[Add triggers observed in this incident]

### Mitigation
[Correct or improve mitigation steps]

### Permanent Fix
[Document fix if deployed]

### Incident History
- [Date]: [Brief description of incident matching this pattern]
```

### Update Cadence

| Update Type | Timing |
|-------------|--------|
| Pattern addition | Within 24 hours of incident resolution |
| Pattern correction | Immediately after RCA review |
| Flow update | Within 1 week of discovery |
| Component update | Within 1 week of discovery |

---

## Continuous Improvement Loop

```
┌─────────────┐
│   INCIDENT  │
└──────┬──────┘
       │
       ▼
┌─────────────┐     ┌─────────────────┐
│     RCA     │────▶│   RCA REPORT    │
└──────┬──────┘     └─────────────────┘
       │
       ▼
┌─────────────┐     ┌─────────────────┐
│   HUMAN     │────▶│   CORRECTION    │
│   REVIEW    │     │   (if needed)   │
└──────┬──────┘     └─────────────────┘
       │
       ▼
┌─────────────┐
│   PATTERN   │
│  EXTRACTION │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│    ATLAS    │
│   UPDATE    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  IMPROVED   │
│ FUTURE RCA  │
└─────────────┘
```

### Loop Metrics

| Metric | Target | Action if Below |
|--------|--------|-----------------|
| Pattern match rate | >60% | Add more patterns |
| RCA acceptance rate | >80% | Improve reasoning/patterns |
| Time to pattern match | <5 min | Improve signal documentation |
| Atlas coverage | >90% of incidents | Fill gaps in flows/components |

---

## RCA Maturity Levels

### Level 1: Static Rule-Based RCA

**Characteristics**:
- Manual lookup of failure-patterns.md
- Human-driven evidence collection
- Basic pattern matching

**Capabilities**:
- Match explicit patterns
- Follow documented investigation steps
- Produce structured reports

**Limitations**:
- No learning from corrections
- Cannot handle novel failures
- Requires exact pattern match

---

### Level 2: Pattern-Assisted Diagnosis

**Characteristics**:
- Fuzzy pattern matching based on signals
- Suggests likely patterns ranked by signal overlap
- Guides evidence collection based on pattern requirements

**Capabilities**:
- Match partial patterns
- Rank hypotheses by evidence strength
- Identify missing evidence to collect

**Limitations**:
- Cannot generate new patterns
- Limited cross-pattern reasoning
- Requires human for novel failures

---

### Level 3: Probabilistic Incident Matching

**Characteristics**:
- Learns pattern confidence from historical accuracy
- Weights signals by diagnostic power
- Considers co-occurrence of patterns

**Capabilities**:
- Probability-weighted diagnosis
- Confidence calibration from history
- Multi-pattern incident analysis

**Limitations**:
- Requires incident history
- Cannot reason about unseen patterns
- May overfit to common patterns

---

### Level 4: Suggested Mitigations

**Characteristics**:
- Recommends mitigation based on pattern + context
- Tracks mitigation success rates
- Adapts recommendations based on environment

**Capabilities**:
- Context-aware mitigation suggestions
- Mitigation effectiveness tracking
- Escalation recommendations

**Limitations**:
- Cannot generate novel mitigations
- Requires mitigation history
- May suggest outdated mitigations

---

### Level 5: Self-Improving Operational Intelligence

**Characteristics**:
- Extracts patterns from novel incidents automatically
- Updates confidence based on outcomes
- Identifies Atlas gaps proactively
- Suggests Atlas updates for human review

**Capabilities**:
- Automatic pattern discovery
- Self-calibrating confidence
- Gap identification
- Proactive Atlas maintenance suggestions

**Requirements**:
- High-quality historical incident data
- Human feedback integration
- Robust confidence estimation
- Hallucination prevention

---

## Quality Standards

### RCA Quality Checklist

| Criterion | Requirement |
|-----------|-------------|
| Evidence-backed | Every claim cites specific evidence |
| Confidence-scored | Uncertainty explicitly stated |
| Traceable | Can follow from symptom to root cause |
| Causal | Explains why, not just what |
| Actionable | Provides concrete mitigation steps |
| Preventive | Recommends permanent fixes |

### Quality Scoring

| Score | Criteria |
|-------|----------|
| **5 - Excellent** | All claims evidence-backed, clear causal chain, actionable fixes, prevention recommendations |
| **4 - Good** | Most claims backed, causal chain present, actionable mitigation |
| **3 - Acceptable** | Root cause identified with evidence, basic mitigation |
| **2 - Weak** | Root cause plausible but poorly evidenced |
| **1 - Poor** | Speculative, no clear causality |
| **0 - Unacceptable** | Hallucinated evidence or causes |

### Minimum Quality Bar

- **For closure**: Score ≥ 3
- **For Atlas update**: Score ≥ 4
- **For pattern addition**: Score ≥ 4 with human verification

---

## Guardrails

### The System MUST:

| Guardrail | Implementation |
|-----------|----------------|
| Avoid hallucinated causes | Only cite evidence present in provided artifacts |
| Distinguish symptoms from causes | Always identify causal chain |
| Avoid overconfidence | Use calibrated confidence levels |
| Preserve evidence traceability | Link every claim to source |
| Prioritize operational reality | Prefer observed behavior over documented behavior |
| Express uncertainty | Explicitly state what is unknown |
| Consider async effects | Account for propagation delays |
| Document negative evidence | Note what was not found |

### The System MUST NOT:

| Prohibition | Rationale |
|-------------|-----------|
| Invent log lines | Hallucination destroys trust |
| Assume code behavior | Code may differ from expectation |
| Blame users | Focus on systemic weaknesses |
| Claim certainty without evidence | Overconfidence leads to wrong fixes |
| Ignore contradictory evidence | Confirmation bias misses root cause |
| Skip alternative hypotheses | Single-cause attribution often wrong |
| Use production data for examples | Privacy and security |

---

## Knowledge Capture Rules

### Every Incident Must Capture:

```yaml
incident:
  id: "[unique identifier]"
  date: "[ISO date]"
  severity: "[CRITICAL/HIGH/MEDIUM/LOW]"
  
symptoms:
  user_reported: "[what user saw]"
  technical: "[error messages, states]"
  
signals:
  present:
    - signal: "[signal name]"
      value: "[observed value]"
      source: "[log/db/trace]"
  absent:
    - signal: "[expected signal not found]"
      implication: "[what this rules out]"
      
timeline:
  - time: "[timestamp]"
    event: "[what happened]"
    evidence: "[source]"
    
analysis:
  trigger: "[proximate cause]"
  root_cause: "[systemic weakness]"
  contributing_factors:
    - "[factor 1]"
    - "[factor 2]"
  confidence: "[HIGH/MEDIUM/LOW]"
  
resolution:
  mitigation: "[immediate fix]"
  permanent_fix: "[code/config change]"
  prevention: "[how to prevent recurrence]"
  
lessons:
  - "[lesson 1]"
  - "[lesson 2]"
  
detection_improvements:
  - "[new alert to add]"
  - "[new metric to track]"
  
atlas_updates:
  - file: "[which Atlas file]"
    change: "[what to update]"
```

### Knowledge Retention

| Knowledge Type | Retention | Location |
|----------------|-----------|----------|
| Failure patterns | Permanent (until code fix) | failure-patterns.md |
| Flow behaviors | Permanent (until flow change) | flows.md |
| Component signals | Permanent (until component change) | components.md |
| Incident history | Archive after 1 year | Incident database |
| RCA reports | Archive after 1 year | Incident database |

---

## Long-Term Objective

### Vision

Build a **continuously improving operational memory system** that:

1. **Remembers** every significant incident and its root cause
2. **Learns** from human corrections to improve future diagnoses
3. **Accelerates** incident resolution through pattern matching
4. **Prevents** recurrence by driving permanent fixes
5. **Evolves** as the application architecture changes

### Success Metrics

| Metric | Current | Target (1 Year) |
|--------|---------|-----------------|
| Mean time to diagnosis | - | <30 minutes |
| Pattern match rate | 0% | >70% |
| RCA acceptance rate | - | >85% |
| Incident recurrence rate | - | <10% |
| Atlas coverage | - | >95% |

### Milestones

| Milestone | Criteria |
|-----------|----------|
| **Operational** | Atlas files complete, RCA engine functional |
| **Effective** | >50% pattern match rate, >70% acceptance rate |
| **Mature** | >70% pattern match rate, <20% recurrence rate |
| **Self-Improving** | Automatic pattern suggestions, calibrated confidence |

---

## Appendix: Quick Reference

### When To Use Each Atlas File

| I need to... | Use... |
|--------------|--------|
| Understand tech stack | system.md |
| Find affected component | components.md |
| Trace execution path | flows.md |
| Match known pattern | failure-patterns.md |
| Understand RCA process | RCA agent.md |
| Understand operations | instructions.md |

### Update Triggers

| Event | Update Required |
|-------|-----------------|
| New incident type | failure-patterns.md |
| RCA was wrong | failure-patterns.md (correct) |
| Flow differs from docs | flows.md |
| New component | components.md |
| New dependency | components.md |
| Architecture change | system.md |
| Process change | instructions.md |

### Escalation Path

| Situation | Action |
|-----------|--------|
| Cannot match pattern | Document as new pattern candidate |
| Evidence insufficient | Request additional artifacts |
| Multiple equally likely causes | Document all with confidence |
| RCA repeatedly incorrect | Review Atlas accuracy |
| Novel failure mode | Escalate to engineering |
