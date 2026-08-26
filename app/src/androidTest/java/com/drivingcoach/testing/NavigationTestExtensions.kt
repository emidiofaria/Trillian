package com.drivingcoach.testing

import androidx.annotation.IdRes
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import com.drivingcoach.R
import com.drivingcoach.ui.MainActivity

/**
 * Polls [condition] until it holds or [timeoutMs] elapses.
 *
 * Preferred over a fixed sleep: a fixed sleep either wastes time or races the device, whereas
 * polling fails loudly with the supplied [message] and is stable on a loaded emulator.
 */
fun awaitUntil(
    message: String,
    timeoutMs: Long = 10_000L,
    pollMs: Long = 50L,
    condition: () -> Boolean
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(pollMs)
    }
    throw AssertionError("Timed out after ${timeoutMs}ms waiting for: $message")
}

/** Reads the currently displayed navigation destination from the host activity. */
@IdRes
fun ActivityScenario<MainActivity>.currentDestinationId(): Int {
    var destination = 0
    onActivity { activity ->
        val navHost = activity.supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        destination = navHost.navController.currentDestination?.id ?: 0
    }
    return destination
}

/** Blocks until the app has navigated away from the loading screen. */
fun ActivityScenario<MainActivity>.awaitStartupResolved(timeoutMs: Long = 15_000L): Int {
    awaitUntil("startup to navigate away from the splash screen", timeoutMs) {
        val current = currentDestinationId()
        current != 0 && current != R.id.splashFragment
    }
    return currentDestinationId()
}
