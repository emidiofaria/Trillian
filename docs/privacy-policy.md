# Privacy Policy — Trillian - Driving coach

**Last updated:** 17 September 2026
**Application:** Trillian - Driving coach (`io.github.emidiofaria.trillian`)
**Developer:** Emídio Faria

## Summary

**Trillian does not collect, transmit, or share any of your data.**

Everything the app records stays on your phone. The app has no user accounts, no
analytics, no advertising, no crash reporting, and no third-party trackers.

The release build of Trillian **does not request the Android `INTERNET`
permission at all**, so it is technically incapable of sending your data
anywhere. This is a deliberate design decision, not an oversight.

## What the app records

To measure your driving, Trillian records the following **to your device's
private storage** while a session is running:

| Data | Why | Where it goes |
|------|-----|---------------|
| Precise GPS location, speed, heading, accuracy | Lap timing, speed traces, track shape, braking analysis | Private app storage on your phone |
| Accelerometer and gyroscope readings | Recorded alongside the GPS trace for future analysis features | Private app storage on your phone |
| Session details (track name, start/end time, lap times) | Session history | Private app storage on your phone |
| A display name you type during setup | Shown in the app | Private app storage on your phone |

None of this is transmitted. There is no server.

## Location data

Trillian uses **precise location** while recording, including while the screen is
off or the app is in the background, via an Android foreground service. This is
the core function of the app: without continuous GPS, lap times and speed traces
cannot be measured.

- A persistent notification is shown whenever recording is active, so it is
  always visible that location is being used.
- Location is recorded **only** while you have explicitly started a session.
- The app does **not** request background location access (`ACCESS_BACKGROUND_LOCATION`).
- Location data is **never** sent off the device.

## Permissions

| Permission | Purpose |
|------------|---------|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Record GPS during a session |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` | Keep recording reliably while the screen is off |
| `POST_NOTIFICATIONS` | Show the recording notification (optional; recording works without it) |
| `HIGH_SAMPLING_RATE_SENSORS` | Sample the accelerometer and gyroscope at a useful rate |

## Sharing and export

The app never shares anything on its own. You can choose to export or share a
session yourself — for example by sending a telemetry file or a session image to
another app. When you do this, **you** control where it goes, and this policy no
longer covers it, because the receiving app's policy applies.

Telemetry exports contain GPS coordinates, which can reveal where you drove.
Consider this before sharing them.

## Data retention and deletion

Session data stays on your phone until you delete it. You can delete individual
sessions in the app, or remove everything at once by uninstalling Trillian or
clearing its storage in Android Settings.

Because nothing is uploaded, there is no server-side copy to request or delete.

## Backup

If you have Android Backup enabled, your app preferences (including your display
name) may be backed up to **your own** Google account, encrypted and controlled
by you. This is an Android platform feature; the developer has no access to it.
Recorded telemetry files are not included in this backup.

## Children

Trillian is not directed at children and collects no data from anyone.

## Changes

If a future version adds any form of data transmission, this policy and the
Google Play Data Safety declaration will be updated **before** that version is
released.

## Contact

Questions about this policy: open an issue at
<https://github.com/emidiofaria/Trillian/issues>.
