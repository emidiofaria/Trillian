package com.drivingcoach.data.location

import javax.inject.Qualifier

/**
 * A [kotlinx.coroutines.CoroutineScope] that lives as long as the process.
 *
 * Warm-up outlives any single screen by design, so its work cannot hang off a fragment or
 * ViewModel scope without being cancelled exactly when the user navigates towards the screen
 * that needs the fix.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
