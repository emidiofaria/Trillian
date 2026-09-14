package com.drivingcoach.data.location

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.drivingcoach.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How long the last GPS acquisition actually took.
 *
 * Persisted rather than only logged because the measurement that matters is taken at a
 * racetrack, where nobody has a cable and logcat. Surfacing it on the About screen turns
 * "it felt like 45 seconds" into a number we can act on.
 */
data class GpsAcquisition(
    /** Subscribe → first fix of any accuracy. */
    val timeToFirstFixMs: Long?,
    /** Subscribe → first fix at or under [GpsReadiness.READY_ACCURACY_M]. */
    val timeToAccurateFixMs: Long?,
    val recordedAtMs: Long
)

@Singleton
class GpsAcquisitionMetricsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * Null until a warm-up has produced at least one fix. Read failures surface as null
     * rather than propagating: this is diagnostics, and it must never be able to take down
     * a screen that displays it.
     */
    val lastAcquisition: Flow<GpsAcquisition?> = dataStore.data
        .catch {
            Log.w(TAG, "could not read acquisition metrics", it)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { preferences ->
            val recordedAt = preferences[KEY_RECORDED_AT] ?: return@map null
            GpsAcquisition(
                timeToFirstFixMs = preferences[KEY_FIRST_FIX_MS],
                timeToAccurateFixMs = preferences[KEY_ACCURATE_FIX_MS],
                recordedAtMs = recordedAt
            )
        }

    /**
     * Records one acquisition. Non-fatal by design — losing a diagnostic is always cheaper
     * than disturbing the startup or track-setup path that produced it.
     */
    suspend fun record(
        timeToFirstFixMs: Long?,
        timeToAccurateFixMs: Long?,
        recordedAtMs: Long
    ) {
        try {
            withContext(ioDispatcher) {
                dataStore.edit { preferences ->
                    timeToFirstFixMs?.let { preferences[KEY_FIRST_FIX_MS] = it }
                    timeToAccurateFixMs?.let { preferences[KEY_ACCURATE_FIX_MS] = it }
                    preferences[KEY_RECORDED_AT] = recordedAtMs
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "could not record acquisition metrics", error)
        }
    }

    companion object {
        private const val TAG = "GpsMetrics"

        val KEY_FIRST_FIX_MS = longPreferencesKey("gps_time_to_first_fix_ms")
        val KEY_ACCURATE_FIX_MS = longPreferencesKey("gps_time_to_accurate_fix_ms")
        val KEY_RECORDED_AT = longPreferencesKey("gps_acquisition_recorded_at")
    }
}
