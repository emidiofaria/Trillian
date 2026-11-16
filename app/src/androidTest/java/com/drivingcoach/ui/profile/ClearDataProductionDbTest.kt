package com.drivingcoach.ui.profile

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.drivingcoach.data.db.DrivingCoachDatabase
import com.drivingcoach.data.db.entity.TrophyEntity
import com.drivingcoach.data.profile.DriverProfileStore
import com.drivingcoach.lap.LapDiagnosticsWriter
import com.drivingcoach.testing.ProgressionSeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * L2 (ASPICE SWE.5) for SRS DR-07 against a database configured like production.
 *
 * The Hilt test database allows main-thread queries, which is exactly what hid Clear data
 * failing on every device: Room's `clearAllTables()` throws on the main thread. This test
 * builds its own database *without* that allowance and drives the real ViewModel.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class ClearDataProductionDbTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var database: DrivingCoachDatabase
    private lateinit var telemetryDir: File
    private lateinit var prefsFile: File
    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, DrivingCoachDatabase::class.java).build()
        telemetryDir = File(context.cacheDir, "clear_data_test_${System.nanoTime()}")
        prefsFile = File(context.cacheDir, "clear_data_test_${System.nanoTime()}.preferences_pb")
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        telemetryDir.deleteRecursively()
        prefsFile.delete()
    }

    @Test
    fun clearDataWipesEverythingOnAProductionConfiguredDatabase() = runBlocking {
        val seed = ProgressionSeed(database.sessionDao(), database.lapDao(), telemetryDir)
        val sessionId = seed.session(startedAt = 1_700_000_000_000L, lapTimesMs = ProgressionSeed.STEADY_MINUTES)
        val telemetry = File(database.sessionDao().getAllRawFilePaths().single())
        val sidecar = File(telemetry.parentFile, telemetry.name + LapDiagnosticsWriter.SUFFIX).apply { writeText("{}") }
        database.trophyDao().insertAll(
            listOf(TrophyEntity(sessionId = sessionId, type = "FINISHER", trackKey = "t", value = 1, awardedAt = 1L))
        )

        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { prefsFile }
        dataStore.edit { it[stringPreferencesKey("marker")] = "set" }

        val vm = ProfileViewModel(
            sessionDao = database.sessionDao(),
            lapDao = database.lapDao(),
            trophyDao = database.trophyDao(),
            database = database,
            dataStore = dataStore,
            driverProfileStore = DriverProfileStore(dataStore, Dispatchers.IO),
            ioDispatcher = Dispatchers.IO
        )

        val firstEvent = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        vm.clearUserData()
        val event = withTimeout(10_000) { firstEvent.await() }

        assertEquals(ProfileEvent.NavigateToOnboarding, event)
        assertTrue("sessions remain", database.sessionDao().getAllSessionsOldestFirst().isEmpty())
        assertTrue("trophies remain", database.trophyDao().getAll().isEmpty())
        assertFalse("telemetry remains", telemetry.exists())
        assertFalse("lap diagnostics sidecar remains", sidecar.exists())
        assertTrue("preferences remain", dataStore.data.first().asMap().isEmpty())
    }
}
