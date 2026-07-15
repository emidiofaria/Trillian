# Documentation Sync Report

Cumulative changelog of documentation synchronizations with codebase.

---

## [2026-07-16] Track Name Navigation Fix

**Codebase Version:** v2.3-trackname-fix  
**Trigger:** RCA #08 track name lost in navigation

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| nav_graph.xml | ✅ Updated | +trackName args to 3 destinations |
| HomeFragment.kt | ✅ Updated | Pass trackName in navigation |
| TrackSetupFragment.kt | ✅ Updated | Receive + forward trackName |
| RecordingFragment.kt | ✅ Updated | Use args.trackName |
| 08_RCA_track_name_lost_in_navigation.md | ✅ Updated | Resolution status |

### Bug Fixed

| Before | After |
|--------|-------|
| All sessions named "Track Session" | Sessions use user-entered track name |

### Files Modified

```
M  app/src/main/res/navigation/nav_graph.xml         (+12)
M  app/src/main/java/.../home/HomeFragment.kt        (+1, -1)
M  app/src/main/java/.../tracksetup/TrackSetupFragment.kt (+4, -2)
M  app/src/main/java/.../recording/RecordingFragment.kt (+1, -1)
M  03_incidents/08_.../08_RCA_track_name_lost_in_navigation.md (+25)
```

---

## [2026-07-16] Coaching Bug Fix + Top Speed + Session Visibility

**Codebase Version:** v2.2-session-fix  
**Trigger:** RCA #06 invalid sector coaching + session visibility bug

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| SRS_v1.md | ✅ Updated | +10 requirements (OC-01 to OC-10), DP-01 clarified |
| components.md | ✅ Updated | Offline Coaching Engine fully rewritten |
| 06_RCA_invalid_sector_coaching.md | ✅ Updated | Resolution status added |

### New Requirement IDs

| ID | Description |
|----|-------------|
| OC-01 | Offline coaching generates local insights after lap detection |
| OC-02 | Produce 3-4 insights: Best Lap, Top Speed, Consistency, Sector Focus |
| OC-03 | Best Lap shows time delta vs average |
| OC-04 | Suppress sector detail when sector*Ms = 0 |
| OC-05 | Top Speed reads telemetry JSONL |
| OC-06 | GPS noise filter: reject >350 km/h |
| OC-07 | Consistency says "within" not "vary by" |
| OC-08 | Sector Focus shows "Coming Soon" upsell |
| OC-09 | Local insights stored with source="LOCAL" |
| OC-10 | Backend insights replace local insights |

### Bug Fixes

| Bug | Root Cause | Fix |
|-----|------------|-----|
| "0ms quicker in Sector 1" | No guard for zero sectors | `areSectorsAvailable()` check |
| Sessions not appearing | userId mismatch (`demo_user` vs `default_user`) | Removed userId filter |

### Files Modified

```
M  01_requirements/DrivingCoach_SRS_v1.md           (+18)
M  SkunkOps/atlas/components.md                    (+25, -15)
M  03_incidents/06_coaching_incidents/06_RCA_invalid_sector_coaching.md (+28)
```

---

## [2026-07-15] Real Speed Chart

**Codebase Version:** v1.9-real-speed-chart  
**Trigger:** Real telemetry speed chart implementation (distance-based X-axis)

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| components.md | ✅ Updated | +55 lines (TelemetryChartProcessor) |
| flows.md | ✅ Updated | +85 lines (Chart Data Loading flow) |
| SRS_v1.md | ✅ Updated | +5 requirements (LC-11 to LC-15) |
| USER_MANUAL.md | ✅ Updated | Section 5.3 rewritten |

### New Requirement IDs

| ID | Description |
|----|-------------|
| LC-11 | X-axis = distance in meters (haversine) |
| LC-12 | Real telemetry data from JSONL |
| LC-13 | Warning for >10 laps |
| LC-14 | Fast/Detailed processing dialog |
| LC-15 | Fast mode = ~100 points/lap |

### Files Modified

```
M  SkunkOps/atlas/components.md         (+55)
M  SkunkOps/atlas/flows.md              (+85)
M  01_requirements/SRS_v1.md            (+7, -2)
M  docs/USER_MANUAL.md                  (+22, -6)
```

---

## [2026-07-13] Local Lap Detection

**Codebase Version:** v1.7-local-lap-detection  
**Trigger:** Offline lap detection feature + incident fixes

### Summary

| Artifact | Status | Changes |
|----------|--------|---------|
| system.md | ✅ Updated | +LocalLapDetector component |
| components.md | ✅ Updated | +LocalLapDetector spec |
| flows.md | ✅ Updated | +Local Lap Detection flow |
| SRS_v1.md | ✅ Updated | +9 requirements (LD-20 to LD-28, TC-06a) |
| USER_MANUAL.md | ✅ Updated | Offline mode, troubleshooting |

### New Requirement IDs

| ID | Description |
|----|-------------|
| LD-20 | Local detection runs immediately after stop |
| LD-21 | Same algorithm as server |
| LD-22 | 20,000ms minimum lap time |
| LD-23 | 50m minimum distance (kart-compatible) |
| LD-24 | `isLocalOnly=true` flag |
| LD-25 | Server results overwrite local |
| LD-26 | "No laps detected" message |
| LD-27 | "📶 Offline" indicator |
| LD-28 | Start line from header or entity |
| TC-06a | Telemetry file header format |

### Related Incidents

- `03_incidents/02_lap_detection_not_triggering/`
- `03_incidents/03_200m_threshold_too_large/`
- `03_incidents/04_telemetry_header_missing/`
- `03_incidents/05_status_banner_blocks_data/`

### Files Modified

```
M  SkunkOps/atlas/system.md
M  SkunkOps/atlas/components.md
M  SkunkOps/atlas/flows.md
M  01_requirements/DrivingCoach_SRS_v1.md
M  docs/USER_MANUAL.md
```

---
