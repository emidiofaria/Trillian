package com.drivingcoach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the version the About screen shows honest.
 *
 * `AboutFragment` renders [BuildConfig.VERSION_NAME] and [BuildConfig.VERSION_CODE]
 * rather than a hardcoded string, so there is exactly one place to change the
 * version and the screen cannot drift from the build. That design holds right up
 * until someone sets `versionCode` by hand, at which point the two disagree
 * silently: the About screen keeps rendering, the APK keeps installing, and the
 * number Play uses to order releases stops matching the number the driver reads
 * back when reporting a problem.
 *
 * The arithmetic asserted here is the scheme recorded in `app/build.gradle.kts`:
 * `major * 100 + minor`, which keeps codes monotonic across the whole v1.0 → v3.x
 * history (1.0 → 100, 2.8 → 208, 3.04 → 304) and leaves room for 99 minor
 * releases per major.
 *
 * This is deliberately a test of *consistency*, not of a literal version number.
 * Pinning "3.04" here would mean editing a test on every release, which trains
 * everyone to edit it without reading it.
 */
class BuildVersionTest {

    @Test
    fun `the version code is derived from the version name`() {
        val parts = BuildConfig.VERSION_NAME.split(".")
        assertEquals(
            "VERSION_NAME '${BuildConfig.VERSION_NAME}' is not major.minor, which the " +
                "version code arithmetic in app/build.gradle.kts assumes",
            2, parts.size
        )

        val major = parts[0].toIntOrNull()
        val minor = parts[1].toIntOrNull()
        assertTrue(
            "VERSION_NAME '${BuildConfig.VERSION_NAME}' has a non-numeric part",
            major != null && minor != null
        )

        assertEquals(
            "The About screen shows ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
                "but $major.$minor should give a version code of ${major!! * 100 + minor!!}. " +
                "A hand-edited versionCode is the one way these two can disagree.",
            major * 100 + minor,
            BuildConfig.VERSION_CODE
        )
    }

    @Test
    fun `the minor component leaves room for the next release`() {
        // 99 is the ceiling the major*100 scheme allows. Passing it would make a new
        // release's code collide with the next major's, and Play rejects a code it has
        // already seen - so the failure would land at upload time, after the build.
        val minor = BuildConfig.VERSION_NAME.split(".")[1].toInt()
        assertTrue(
            "Minor version $minor has reached the ceiling of the major*100 scheme. " +
                "Raise the major version rather than the minor.",
            minor < 100
        )
    }
}
