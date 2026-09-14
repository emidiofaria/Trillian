# Bug Report: Completed Sessions Do Not Appear in Recent Sessions

| Field | Value |
|-------|-------|
| **Application** | Driving Coach |
| **Package** | `com.drivingcoach` |
| **Severity** | High |
| **Priority** | High |
| **Status** | Open |
| **Reported Date** | 2026-07-16 |
| **Environment** | Android emulator |
| **Affected Screen** | Home screen / Recent Sessions |
| **Affected Components** | `HomeFragment`, `HomeViewModel`, `SessionDao`, session persistence flow |

---

## Summary

When running the app on the Android emulator, completed recording sessions are not shown in the `RECENT SESSIONS` section on the Home screen.

This blocks the main post-session workflow because the user cannot reopen a recorded session from the Home screen to inspect lap results, charts, or coaching insights.

---

## Steps to Reproduce

1. Launch the Driving Coach app on an Android emulator.
2. Start a new session from the Home screen.
3. Complete or stop the recording session.
4. Return to the Home screen.
5. Check the `RECENT SESSIONS` list.

---

## Expected Behavior

- The completed session should appear under `RECENT SESSIONS` immediately after the user returns to the Home screen.
- The session row should include the track name, date, best lap if available, lap count, and consistency score.
- Tapping the session row should navigate to the Session Result screen for that session.
- If lap detection fails, the session should still appear with placeholders such as `-` for best lap and `0 laps`.

## Actual Behavior

- The completed session does not appear in `RECENT SESSIONS`.
- The Home screen behaves as if no session history is available.
- The user has no visible entry point to reopen the completed session from the Home screen.

---

## Impact

- Users may believe their session was lost even if telemetry or database records exist.
- Session Result, Chart, and Coach screens become difficult or impossible to revisit from normal navigation.
- Emulator validation of end-to-end recording is blocked because session persistence cannot be confirmed from the UI.
- This can hide downstream issues in lap detection, upload, and coaching because the recorded session is not discoverable.
