package com.drivingcoach.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * A [DataStore] that keeps writes in memory and replays them to subsequent readers.
 *
 * Needed wherever a test drives a screen that *writes* preferences and then asserts on what
 * a later read sees — first-run driver naming, for instance, where the whole point is that
 * the name survives. [SeededPreferencesDataStore] discards writes, so it cannot express that.
 */
class MutablePreferencesDataStore(
    initial: Preferences = emptyPreferences()
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)

    /** The current snapshot, for direct assertions without collecting the flow. */
    val current: Preferences get() = state.value

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
