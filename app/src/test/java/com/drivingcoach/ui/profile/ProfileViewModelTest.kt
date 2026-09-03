package com.drivingcoach.ui.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.drivingcoach.R
import com.drivingcoach.data.db.DrivingCoachDatabase
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.profile.DriverProfileStore
import kotlinx.coroutines.Dispatchers
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
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

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
    private lateinit var database: DrivingCoachDatabase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionDao = mock()
        lapDao = mock()
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

    private fun viewModel(dataStore: DataStore<Preferences>): ProfileViewModel {
        // The Profile screen loads stats on construction; keep those reads empty and quiet
        // so each test observes only the action under test.
        whenever(sessionDao.getAllSessionsForUser("demo_user")).thenReturn(flowOf(emptyList()))

        return ProfileViewModel(
            sessionDao = sessionDao,
            lapDao = lapDao,
            database = database,
            dataStore = dataStore,
            driverProfileStore = DriverProfileStore(dataStore, testDispatcher),
            ioDispatcher = testDispatcher
        )
    }

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
}
