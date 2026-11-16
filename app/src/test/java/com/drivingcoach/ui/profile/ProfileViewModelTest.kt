package com.drivingcoach.ui.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.drivingcoach.R
import com.drivingcoach.data.db.DrivingCoachDatabase
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.dao.TrophyDao
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.data.db.entity.TrophyEntity
import com.drivingcoach.data.profile.DriverProfileStore
import com.drivingcoach.data.profile.LocalUser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.Executors

/**
 * L1 (SWE.4) unit tests for the Profile screen's identity actions.
 *
 * Covers SRS DR-06 (rename is cosmetic and does not touch recorded data) and DR-07
 * (clearing user data removes preferences, database rows *and* telemetry files).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var sessionDao: SessionDao
    private lateinit var lapDao: LapDao
    private lateinit var trophyDao: TrophyDao
    private lateinit var database: DrivingCoachDatabase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionDao = mock()
        lapDao = mock()
        trophyDao = mock()
        database = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class RecordingDataStore(initial: Preferences) : DataStore<Preferences> {
        var current: Preferences = initial
            private set

        override val data: Flow<Preferences> get() = flowOf(current)

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences {
            current = transform(current)
            return current
        }
    }

    private fun viewModel(
        dataStore: DataStore<Preferences>,
        ioDispatcher: CoroutineDispatcher = testDispatcher
    ): ProfileViewModel {
        // The Profile screen loads stats on construction; keep those reads empty and quiet
        // so each test observes only the action under test.
        stubStats()

        return ProfileViewModel(
            sessionDao = sessionDao,
            lapDao = lapDao,
            trophyDao = trophyDao,
            database = database,
            dataStore = dataStore,
            driverProfileStore = DriverProfileStore(dataStore, testDispatcher),
            ioDispatcher = ioDispatcher
        )
    }

    /**
     * Sessions are returned only for the user id the recorder writes, as Room would: a
     * Profile that asked for any other id would see nothing, which is the v3.07 bug.
     */
    private fun stubStats(
        sessions: List<SessionEntity> = emptyList(),
        laps: List<LapEntity> = emptyList(),
        trophies: List<TrophyEntity> = emptyList()
    ) {
        whenever(sessionDao.getAllSessionsForUser(any())).thenAnswer { call ->
            flowOf(sessions.filter { it.userId == call.getArgument<String>(0) })
        }
        whenever(lapDao.observeAllLaps()).thenReturn(flowOf(laps))
        whenever(trophyDao.observeAll()).thenReturn(flowOf(trophies))
    }

    private fun recordedSession(id: Long, distanceM: Double) = SessionEntity(
        id = id, userId = RECORDER_USER_ID, trackName = "Baltar", startedAt = id * 1_000L,
        rawFilePath = "", distanceM = distanceM
    )

    private fun lap(sessionId: Long, number: Int, durationMs: Long) = LapEntity(
        sessionId = sessionId, lapNumber = number, startTs = 0L, endTs = durationMs, durationMs = durationMs
    )

    private fun trophyRow(sessionId: Long, type: String) =
        TrophyEntity(sessionId = sessionId, type = type, trackKey = "name:baltar", value = 0L, awardedAt = 0L)

    private fun storedProfile(name: String) = mutablePreferencesOf(
        DriverProfileStore.KEY_DRIVER_NAME to name,
        DriverProfileStore.KEY_PROFILE_COMPLETE to true
    )

    // --- Profile loading -------------------------------------------------------------------

    @Test
    fun `the stored driver name is displayed with its initials`() = runTest(testDispatcher) {
        val vm = viewModel(RecordingDataStore(storedProfile("Ayrton Senna")))
        advanceUntilIdle()

        assertEquals("Ayrton Senna", vm.uiState.value.profile?.displayName)
        assertEquals("AS", vm.uiState.value.profile?.initials)
    }

    @Test
    fun `an unreadable profile falls back to a neutral label`() = runTest(testDispatcher) {
        val vm = viewModel(RecordingDataStore(emptyPreferences()))
        advanceUntilIdle()

        assertEquals(
            ProfileViewModel.DEFAULT_DISPLAY_NAME,
            vm.uiState.value.profile?.displayName
        )
    }

    // --- Stats (DP-01, DP-11) --------------------------------------------------------------

    @Test
    fun `the shared user id is the one the recorder has always written`() {
        // Existing recordings are keyed to this value; changing it would orphan them.
        assertEquals(RECORDER_USER_ID, LocalUser.ID)
    }

    /** Fails on v3.07, which asked for "demo_user" and showed 0 for every driver. */
    @Test
    fun `stats come from the sessions the recorder wrote`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(storedProfile("Ayrton"))
        stubStats(
            sessions = listOf(recordedSession(1, 6_400.0), recordedSession(2, 8_000.0)),
            laps = listOf(lap(1, 1, 30 * 60_000L), lap(1, 2, 30 * 60_000L), lap(2, 1, 25 * 60_000L)),
            trophies = listOf(trophyRow(1, "FINISHER"), trophyRow(2, "METRONOME"))
        )
        val fresh = ProfileViewModel(
            sessionDao, lapDao, trophyDao, database, dataStore,
            DriverProfileStore(dataStore, testDispatcher), testDispatcher
        )
        advanceUntilIdle()

        val stats = fresh.uiState.value.stats
        assertEquals(14.4, stats.drivenKm, 1e-9)
        assertEquals(85 * 60_000L, stats.drivenMs)
        assertEquals(2, stats.trophies)
    }

    @Test
    fun `the trophies stat counts level trophies only`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(storedProfile("Ayrton"))
        stubStats(
            sessions = listOf(recordedSession(1, 9_000.0)),
            trophies = listOf(
                trophyRow(1, "FINISHER"), trophyRow(1, "STEADY_HAND"),
                trophyRow(1, "FULL_STINT"), trophyRow(1, "CARBON_COPY"),
                // A pre-3.08 row before the recompute: unknown, so not counted.
                trophyRow(1, "PB_SILVER")
            )
        )
        val vm = ProfileViewModel(
            sessionDao, lapDao, trophyDao, database, dataStore,
            DriverProfileStore(dataStore, testDispatcher), testDispatcher
        )
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.stats.trophies)
    }

    @Test
    fun `sessions of another user are not counted`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(storedProfile("Ayrton"))
        stubStats(
            sessions = listOf(recordedSession(1, 5_000.0), recordedSession(2, 7_000.0).copy(userId = "demo_user")),
            laps = listOf(lap(1, 1, 60_000L), lap(2, 1, 90_000L)),
            trophies = listOf(trophyRow(2, "FINISHER"))
        )
        val vm = ProfileViewModel(
            sessionDao, lapDao, trophyDao, database, dataStore,
            DriverProfileStore(dataStore, testDispatcher), testDispatcher
        )
        advanceUntilIdle()

        val stats = vm.uiState.value.stats
        assertEquals(5.0, stats.drivenKm, 1e-9)
        assertEquals(60_000L, stats.drivenMs)
        assertEquals(0, stats.trophies)
    }

    // --- Rename (DR-06) --------------------------------------------------------------------

    @Test
    fun `renaming persists the new name and refreshes the initials`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(storedProfile("Ayrton Senna"))
        val vm = viewModel(dataStore)
        advanceUntilIdle()

        vm.renameDriver("Juan Fangio")
        advanceUntilIdle()

        assertEquals("Juan Fangio", dataStore.current[DriverProfileStore.KEY_DRIVER_NAME])
        assertEquals("Juan Fangio", vm.uiState.value.profile?.displayName)
        assertEquals("JF", vm.uiState.value.profile?.initials)
    }

    @Test
    fun `renaming reports success to the driver`() = runTest(testDispatcher) {
        val vm = viewModel(RecordingDataStore(storedProfile("Ayrton")))
        advanceUntilIdle()

        val events = mutableListOf<ProfileEvent>()
        val collector = launch { vm.events.collect { events += it } }

        vm.renameDriver("Juan Fangio")
        advanceUntilIdle()

        assertEquals(
            listOf(ProfileEvent.ShowMessage(R.string.driver_name_edit_success)),
            events
        )

        collector.cancel()
    }

    @Test
    fun `an invalid rename is rejected and leaves the stored name intact`() =
        runTest(testDispatcher) {
            val dataStore = RecordingDataStore(storedProfile("Ayrton"))
            val vm = viewModel(dataStore)
            advanceUntilIdle()

            vm.renameDriver("A")
            advanceUntilIdle()

            assertEquals("Ayrton", dataStore.current[DriverProfileStore.KEY_DRIVER_NAME])
        }

    /**
     * The whole reason the name is not an ownership key: renaming must be incapable of
     * touching recorded data.
     */
    @Test
    fun `renaming never touches sessions or the database`() = runTest(testDispatcher) {
        val vm = viewModel(RecordingDataStore(storedProfile("Ayrton")))
        advanceUntilIdle()

        vm.renameDriver("Juan Fangio")
        advanceUntilIdle()

        verify(database, never()).clearAllTables()
        verify(sessionDao, never()).getAllRawFilePaths()
    }

    // --- Clear user data (DR-07) -----------------------------------------------------------

    @Test
    fun `clearing user data deletes the recorded telemetry files`() = runTest(testDispatcher) {
        val telemetry = temporaryFolder.newFile("session_1.jsonl").apply { writeText("{}") }
        whenever(sessionDao.getAllRawFilePaths()).thenReturn(listOf(telemetry.absolutePath))

        val vm = viewModel(RecordingDataStore(storedProfile("Ayrton")))
        advanceUntilIdle()

        vm.clearUserData()
        advanceUntilIdle()

        assertFalse("telemetry file should have been deleted", telemetry.exists())
    }

    /**
     * Ordering is the contract: once the tables are cleared there is no record of which
     * files belong to this app, so the paths must be read first or the files leak forever.
     */
    @Test
    fun `file paths are read before the tables are cleared`() = runTest(testDispatcher) {
        whenever(sessionDao.getAllRawFilePaths()).thenReturn(emptyList())

        val vm = viewModel(RecordingDataStore(storedProfile("Ayrton")))
        advanceUntilIdle()

        vm.clearUserData()
        advanceUntilIdle()

        inOrder(sessionDao, database) {
            verify(sessionDao).getAllRawFilePaths()
            verify(database).clearAllTables()
        }
    }

    @Test
    fun `clearing user data clears every stored preference`() = runTest(testDispatcher) {
        whenever(sessionDao.getAllRawFilePaths()).thenReturn(emptyList())
        val dataStore = RecordingDataStore(storedProfile("Ayrton"))

        val vm = viewModel(dataStore)
        advanceUntilIdle()

        vm.clearUserData()
        advanceUntilIdle()

        assertTrue(
            "preferences should be empty after a clear",
            dataStore.current.asMap().isEmpty()
        )
    }

    @Test
    fun `clearing user data returns the driver to onboarding`() = runTest(testDispatcher) {
        whenever(sessionDao.getAllRawFilePaths()).thenReturn(emptyList())

        val vm = viewModel(RecordingDataStore(storedProfile("Ayrton")))
        advanceUntilIdle()

        val events = mutableListOf<ProfileEvent>()
        val collector = launch { vm.events.collect { events += it } }

        vm.clearUserData()
        advanceUntilIdle()

        assertEquals(listOf(ProfileEvent.NavigateToOnboarding), events)

        collector.cancel()
    }

    /**
     * A path that no longer resolves — an already-deleted file, or a directory — must not
     * abort the wipe partway through and strand the remaining data.
     */
    @Test
    fun `a missing telemetry file does not abort the clear`() = runTest(testDispatcher) {
        val present = temporaryFolder.newFile("session_2.jsonl").apply { writeText("{}") }
        val absent = File(temporaryFolder.root, "session_gone.jsonl")
        whenever(sessionDao.getAllRawFilePaths())
            .thenReturn(listOf(absent.absolutePath, present.absolutePath))

        val vm = viewModel(RecordingDataStore(storedProfile("Ayrton")))
        advanceUntilIdle()

        val events = mutableListOf<ProfileEvent>()
        val collector = launch { vm.events.collect { events += it } }

        vm.clearUserData()
        advanceUntilIdle()

        assertFalse(present.exists())
        assertEquals(listOf(ProfileEvent.NavigateToOnboarding), events)

        collector.cancel()
    }

    /**
     * Room's generated `clearAllTables()` throws on the main thread. Calling it straight from
     * viewModelScope made every Clear data fail in production while mocks and the L2 test
     * database (which allows main-thread queries) hid it.
     */
    @Test
    fun `tables are cleared off the main thread`() = runTest(testDispatcher) {
        whenever(sessionDao.getAllRawFilePaths()).thenReturn(emptyList())
        var clearedOn: String? = null
        doAnswer { clearedOn = Thread.currentThread().name; null }.whenever(database).clearAllTables()

        val executor = Executors.newSingleThreadExecutor { Thread(it, "io-test") }
        try {
            val vm = viewModel(RecordingDataStore(storedProfile("Ayrton")), executor.asCoroutineDispatcher())
            advanceUntilIdle()

            val events = mutableListOf<ProfileEvent>()
            val collector = launch { vm.events.collect { events += it } }

            vm.clearUserData()
            val deadline = System.currentTimeMillis() + 5_000
            while (events.isEmpty() && System.currentTimeMillis() < deadline) {
                advanceUntilIdle()
                Thread.sleep(10)
            }

            assertEquals(listOf(ProfileEvent.NavigateToOnboarding), events)
            assertTrue("cleared on $clearedOn", clearedOn?.startsWith("io-test") == true)
            collector.cancel()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `lap diagnostics sidecars are deleted with their telemetry`() = runTest(testDispatcher) {
        val telemetry = temporaryFolder.newFile("session_3.jsonl").apply { writeText("{}") }
        val sidecar = temporaryFolder.newFile("session_3.jsonl.lapdiag.json").apply { writeText("{}") }
        val unrelated = temporaryFolder.newFile("notes.lapdiag.json").apply { writeText("{}") }
        whenever(sessionDao.getAllRawFilePaths()).thenReturn(listOf(telemetry.absolutePath))

        val vm = viewModel(RecordingDataStore(storedProfile("Ayrton")))
        advanceUntilIdle()

        vm.clearUserData()
        advanceUntilIdle()

        assertFalse("sidecar should have been deleted", sidecar.exists())
        assertTrue("files not derived from a recorded path are left alone", unrelated.exists())
    }

    private companion object {
        /** The literal RecordingViewModel wrote before [LocalUser] existed. */
        const val RECORDER_USER_ID = "default_user"
    }
}
