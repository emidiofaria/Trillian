# Incident: No Laps Detected After 4 Laps on Emulator

**Date Reported**: 2026-07-21  
**Severity**: HIGH  
**Status**: Under Investigation  
**APK Version**: DrivingCoach-v2.4-session-management.apk

---

## Description

User drove 4 laps on the Android emulator but the app did not detect any laps. The session completed normally but showed "No laps detected" on the results screen.

This is reported as a **regression** — lap detection was working before with the same emulator setup.

## Reproduction Steps

1. Build APK from Feature_delete_and_rename_session branch (with uncommitted changes)
2. Install on Android emulator
3. Enter track name and complete track setup (capture start line)
4. Start recording
5. Drive 4 laps using GPS simulation
6. Stop recording
7. **Expected**: 4 or 3 laps detected (depending on completion)
8. **Actual**: "No laps detected"

## Recent Changes

- Session management feature added (delete/rename sessions)
- Database migration 3→4 for session preferences table
- Offline coaching engine added

## Evidence Needed

- [ ] Logcat from recording session
- [ ] Room database query for session start line values
- [ ] Telemetry JSONL file from failing session
- [ ] Screenshot of emulator GPS simulation settings

## Related Incidents

- **Incident 02**: Lap detection not triggering (race condition) — FIXED
- **Incident 03**: 200m threshold too large — FIXED (now 50m)

## Notes

Preliminary investigation shows the session management changes do NOT affect the lap detection code path. Most likely causes:
1. Start line coordinates not stored in database
2. Emulator GPS simulation not configured
3. Start line geometry doesn't intersect GPS route
