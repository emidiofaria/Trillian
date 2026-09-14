# Bug Report: User-Defined Track Name Not Saved to Session

| Field | Value |
|-------|-------|
| **Application** | Driving Coach |
| **Package** | `com.drivingcoach` |
| **Severity** | Medium |
| **Priority** | Medium |
| **Status** | Open |
| **Reported Date** | 2026-07-16 |
| **Environment** | Android (all devices) |
| **Affected Screen** | Home, Session Result, Profile |
| **Affected Components** | Navigation, `RecordingFragment`, `TrackSetupFragment` |

---

## Summary

When starting a new session, the user enters a track name in the dialog (e.g., "Circuito de Braga"), but after recording completes, all sessions show the generic name **"Track Session"** instead of the user-provided name.

---

## Steps to Reproduce

1. Launch the Driving Coach app
2. Tap the FAB to start a new session
3. Enter a track name in the dialog (e.g., "Circuito de Braga")
4. Tap "Start"
5. Complete the track setup (set start/finish line)
6. Start and complete a recording
7. Return to Home screen
8. Check the session name in `RECENT SESSIONS`

---

## Expected Behavior

- The session should display the user-provided track name ("Circuito de Braga")
- The track name should persist in the database
- The track name should appear in Session Result, Profile, and share cards

## Actual Behavior

- All sessions display **"Track Session"** as the track name
- The user-provided name is lost during navigation
- This affects all views that display the track name

---

## Impact

- Users cannot distinguish between sessions by track
- Session history becomes confusing with repeated "Track Session" names
- Share cards display generic track name instead of actual track
- User experience significantly degraded
