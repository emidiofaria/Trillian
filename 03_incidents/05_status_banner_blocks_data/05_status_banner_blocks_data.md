# Bug Report: Status Banner Blocks Lap Data Display

| Field | Value |
|-------|-------|
| **Application** | Driving Coach |
| **Package** | `com.drivingcoach` |
| **Severity** | Medium |
| **Priority** | Medium |
| **Status** | Resolved |
| **Reported Date** | 2026-07-14 |
| **Resolved Date** | 2026-07-14 |

---

## Summary

After a recording session ends, a large "Processing your session..." card appears on the Session Result screen. When the user is offline, this card remains visible indefinitely (upload pending/failed), blocking the view of lap times that were successfully detected locally.

---

## Steps to Reproduce

1. Start the app in offline mode (no network)
2. Complete a recording session with multiple laps
3. Stop recording
4. Navigate to Session Result screen
5. **Result**: Large processing card blocks lap data view

---

## Expected Behavior

- Lap times should be the primary content (always visible)
- Upload status should be secondary (small, non-blocking)
- User should be able to dismiss upload status if not interested

## Actual Behavior

- Large card with "Processing your session..." takes significant screen space
- Shows "Uploading ● | Detecting laps ○ | Generating coaching ○"
- Progress bar and retry button add to card height
- Lap times pushed below or hidden

---

## Visual Comparison

### Before (Problematic)
```
┌──────────────────────────────────────────┐
│        Session Result Screen             │
├──────────────────────────────────────────┤
│ ┌──────────────────────────────────────┐ │
│ │  Processing your session...          │ │
│ │  Uploading ● | Detecting ○ | Coach ○ │ │
│ │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │ │
│ │  [    RETRY ANALYSIS    ]            │ │
│ └──────────────────────────────────────┘ │
│                                          │
│ ← LAP DATA PUSHED DOWN OR HIDDEN         │
└──────────────────────────────────────────┘
```

### After (Fixed)
```
┌──────────────────────────────────────────┐
│        Session Result Screen             │
├──────────────────────────────────────────┤
│ ┌──────────────────────────────────────┐ │
│ │ ◐ Uploading session...            ✕ │ │  ← Small, dismissible
│ └──────────────────────────────────────┘ │
│                                          │
│  Best Lap: 1:23.456  🏆                  │
│  Lap 1: 1:25.123                         │
│  Lap 2: 1:23.456 ⭐                      │
│  Lap 3: 1:24.789                         │
│                                          │
│ ← LAP DATA ALWAYS VISIBLE                │
└──────────────────────────────────────────┘
```

---

## Environment

| Component | Details |
|-----------|---------|
| Affected Screen | `SessionResultFragment` |
| Layout File | `fragment_session_result.xml` |
| Condition | Offline mode or slow network |

---

## Root Cause Summary

The UI was designed with an "online-first" assumption where the processing card would be temporary. For offline users, the card becomes a permanent blocker that hides the actually useful data (locally detected lap times).

---

## Resolution

Replaced the large `MaterialCardView` with a small, single-line dismissible status bar:
- Compact design (single line with spinner, text, and X button)
- Dismissible (tap X to hide)
- Color-coded states (blue for processing, red for failed)
- Lap data always visible below

---

## Attachments

- `05_RCA_status_banner_blocks_data.md` - Full root cause analysis
