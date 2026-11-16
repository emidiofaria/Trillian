package com.drivingcoach.data.profile

/**
 * The one session owner on this device (SRS DP-01, single-user MVP).
 *
 * Until v3.08 the recorder wrote `"default_user"` while the Profile screen read
 * `"demo_user"`, so Profile showed 0 sessions for every driver (atlas FP-HARDCODED-ID-DRIFT).
 * Every writer and reader of `sessions.userId` must use this constant. The value is the one
 * the recorder has always written, so every existing recording stays visible with no
 * migration.
 */
object LocalUser {
    const val ID = "default_user"
}
