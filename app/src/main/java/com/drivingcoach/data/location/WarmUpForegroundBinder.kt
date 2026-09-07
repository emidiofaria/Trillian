package com.drivingcoach.data.location

import android.app.Activity
import android.app.Application
import android.os.Bundle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Releases the GNSS chip when the app leaves the foreground (SRS TS-18).
 *
 * ### Why the warm-up is no longer stopped by a screen
 *
 * [LocationWarmUp] used to be stopped by `HomeFragment.onStop()`. That made the single
 * navigation the feature exists to serve — Home → Track Setup — its own stop condition: the
 * user waited out the cold fix in the paddock, saw the badge turn green, walked to the line,
 * and was met with "Acquiring GPS…" and a disabled Capture button anyway (Incident 12).
 *
 * The bound is therefore expressed against the user's *task*. This class owns one of the
 * three stop conditions; the others are recording starting (`TelemetryForegroundService`)
 * and [WarmUpTimings.idleCeilingMs] as a backstop.
 *
 * ### Why a started-activity count rather than `ProcessLifecycleOwner`
 *
 * `ProcessLifecycleOwner` is the idiomatic answer and was tried first. It does not dispatch
 * `ON_STOP` under `ActivityScenario`, so the guarantee below could not be proven by an
 * instrumented test — and an unverifiable guarantee is precisely how Incident 12 reached
 * human acceptance testing. Counting started activities behaves the same way in production
 * and is observable under test, so the property stays provable.
 *
 * ### Configuration changes
 *
 * A naive counter would see a rotation as a background/foreground pair and release the chip,
 * reintroducing this incident in a form that only appears when the user happens to rotate the
 * phone on the walk to the line. [Activity.isChangingConfigurations] separates the two cases,
 * and `warmUpSurvivesAConfigurationChange` guards it.
 *
 * This is a privacy bound before it is a battery one: high-accuracy location must not be held
 * while the user cannot see that it is being held.
 */
@Singleton
class WarmUpForegroundBinder @Inject constructor(
    private val warmUp: LocationWarmUp
) {

    private var bound = false
    private var startedActivities = 0

    private val callbacks = object : Application.ActivityLifecycleCallbacks {

        override fun onActivityStarted(activity: Activity) {
            startedActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            // A rotation stops the Activity only to rebuild it immediately. Treating that as
            // "the user left" would throw away the warm fix mid-walk.
            if (activity.isChangingConfigurations) return

            startedActivities--
            if (startedActivities <= 0) {
                startedActivities = 0
                warmUp.stop()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    /**
     * Registers the foreground watcher exactly once.
     *
     * Idempotent because the host Activity is recreated on every configuration change, and a
     * fresh registration per rotation would leave a growing pile of callbacks.
     */
    fun bind(application: Application) {
        if (bound) return
        bound = true
        application.registerActivityLifecycleCallbacks(callbacks)
    }
}
