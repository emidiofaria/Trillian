package com.drivingcoach.ui.splash

import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject

/**
 * Reports the instant the branded loading screen actually reached the glass.
 *
 * On Android 12+ (and through the androidx compat layer below it) the OS draws its own
 * splash window — the emblem alone on black — and keeps it until the activity's first frame
 * is composited. Only then does the branded screen become visible. SRS UI-02 is a budget for
 * *that* screen, so it has to be measured from that instant.
 *
 * This exists as a separate object because the two halves live in different places:
 * [com.drivingcoach.ui.MainActivity] owns the system splash and learns when it exits, while
 * [SplashViewModel] owns the display budget.
 *
 * Scoped per activity rather than per process. A `@Singleton` would leak the first launch's
 * timestamp into every later one — harmless in production, but instrumented tests launch
 * dozens of activities in a single process and would silently lose the hold entirely.
 */
@ActivityRetainedScoped
class SplashVisibilitySignal @Inject constructor() {

    private val visible = CompletableDeferred<Long>()

    /**
     * Records the handoff. Idempotent — later calls are ignored, so a configuration change
     * cannot restart an introduction the user has already begun reading.
     */
    fun markVisible(at: Long = System.currentTimeMillis()) {
        visible.complete(at)
    }

    /** Suspends until the handoff is reported; returns immediately if it already was. */
    suspend fun awaitVisible(): Long = visible.await()
}
