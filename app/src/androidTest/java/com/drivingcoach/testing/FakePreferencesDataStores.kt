package com.drivingcoach.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * A [DataStore] whose read never completes.
 *
 * Models the pathological disk-I/O case that SRS UI-03 and UI-04 exist to defend against:
 * the app must still reach a usable screen, and the main thread must stay responsive while
 * the read is outstanding.
 */
class StallingPreferencesDataStore : DataStore<Preferences> {

    override val data: Flow<Preferences> = flow {
        awaitCancellation()
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences = throw UnsupportedOperationException("Reads only in tests")
}

/**
 * A [DataStore] that immediately emits a fixed set of preferences.
 *
 * Lets a test pin the startup destination without touching the device's real preference
 * file, so instrumented runs cannot leak state into each other.
 */
class SeededPreferencesDataStore(
    private val preferences: Preferences = emptyPreferences()
) : DataStore<Preferences> {

    override val data: Flow<Preferences> = flowOf(preferences)

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences = transform(preferences)
}
