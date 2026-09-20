package com.drivingcoach

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tripwires for the Google Play "does not collect any user data" declaration.
 *
 * That declaration is a statement to Google about what the shipped app does, and
 * these tests are what keep it honest. The app records precise location and
 * inertial data continuously; the only reason it does not *collect* any of it,
 * in Play's sense, is that none of it is ever transmitted.
 *
 * Two independent mechanisms guarantee that (NF-20):
 *
 *  1. [BuildConfig.UPLOAD_ENABLED] is false, so nothing is ever enqueued.
 *  2. The release manifest declares no INTERNET permission, so the process
 *     cannot open a socket even if something were enqueued. That half is
 *     enforced by the `--target play` preflight in package-release.sh, because
 *     the debug variant used for instrumentation *does* hold INTERNET and so
 *     cannot assert its absence.
 *
 * If a change here fails, the fix is not to update the expected value. It is to
 * update the Data Safety form in the Play Console in the same commit, and to
 * add the prominent disclosure that transmitting location requires.
 */
class DataSafetyPolicyTest {

    @Test
    fun `telemetry upload stays disabled`() {
        assertFalse(
            "BuildConfig.UPLOAD_ENABLED is true. The Play Data Safety declaration " +
                "says this app collects no user data, which stops being true the " +
                "moment sessions are uploaded. Update the declaration before " +
                "enabling this.",
            BuildConfig.UPLOAD_ENABLED
        )
    }
}
