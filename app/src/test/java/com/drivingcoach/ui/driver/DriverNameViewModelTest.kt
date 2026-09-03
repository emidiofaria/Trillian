package com.drivingcoach.ui.driver

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.drivingcoach.R
import com.drivingcoach.data.profile.DriverProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * L1 (SWE.4) unit tests for first-run driver naming.
 *
 * Covers SRS DR-02 (the screen exists and gates entry), DR-03 (validation) and DR-04
 * (a saved name completes the profile).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriverNameViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
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

    private class FailingWriteDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = throw IllegalStateException("disk full")
    }

    private fun viewModel(dataStore: DataStore<Preferences>) =
        DriverNameViewModel(DriverProfileStore(dataStore, testDispatcher))

    // --- Submit gating (DR-03) -----------------------------------------------------------

    @Test
    fun `the call to action starts disabled`() {
        val vm = viewModel(RecordingDataStore(emptyPreferences()))

        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun `a valid name enables the call to action`() {
        val vm = viewModel(RecordingDataStore(emptyPreferences()))

        vm.onNameChanged("Ayrton")

        assertTrue(vm.uiState.value.canSubmit)
        assertNull(vm.uiState.value.errorLabel)
    }

    @Test
    fun `a one character name keeps the call to action disabled`() {
        val vm = viewModel(RecordingDataStore(emptyPreferences()))

        vm.onNameChanged("A")

        assertFalse(vm.uiState.value.canSubmit)
        assertEquals(R.string.driver_name_error_short, vm.uiState.value.errorLabel)
    }

    /** An untouched field is not an error — do not scold before the driver has typed. */
    @Test
    fun `an empty field shows no error`() {
        val vm = viewModel(RecordingDataStore(emptyPreferences()))

        vm.onNameChanged("")

        assertFalse(vm.uiState.value.canSubmit)
        assertNull(vm.uiState.value.errorLabel)
    }

    @Test
    fun `an over long name reports the length error`() {
        val vm = viewModel(RecordingDataStore(emptyPreferences()))

        vm.onNameChanged("N".repeat(DriverProfileStore.MAX_NAME_LENGTH + 1))

        assertFalse(vm.uiState.value.canSubmit)
        assertEquals(R.string.driver_name_error_long, vm.uiState.value.errorLabel)
    }

    @Test
    fun `correcting an invalid name clears the error`() {
        val vm = viewModel(RecordingDataStore(emptyPreferences()))

        vm.onNameChanged("A")
        vm.onNameChanged("Ayrton")

        assertTrue(vm.uiState.value.canSubmit)
        assertNull(vm.uiState.value.errorLabel)
    }

    // --- Submission (DR-02, DR-04) --------------------------------------------------------

    @Test
    fun `submitting a valid name persists it and navigates home`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(emptyPreferences())
        val vm = viewModel(dataStore)

        val events = mutableListOf<DriverNameViewModel.Event>()
        val collector = launch { vm.events.collect { events += it } }

        vm.onSubmit("Ayrton Senna")
        advanceUntilIdle()

        assertEquals("Ayrton Senna", dataStore.current[DriverProfileStore.KEY_DRIVER_NAME])
        assertEquals(true, dataStore.current[DriverProfileStore.KEY_PROFILE_COMPLETE])
        assertEquals(listOf(DriverNameViewModel.Event.NavigateToHome), events)

        collector.cancel()
    }

    /**
     * The button state is a UI affordance, not the authority. Submitting an invalid name
     * directly — via the IME action, or a stale enabled state — must still be refused.
     */
    @Test
    fun `submitting an invalid name neither persists nor navigates`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(emptyPreferences())
        val vm = viewModel(dataStore)

        val events = mutableListOf<DriverNameViewModel.Event>()
        val collector = launch { vm.events.collect { events += it } }

        vm.onSubmit("A")
        advanceUntilIdle()

        assertNull(dataStore.current[DriverProfileStore.KEY_DRIVER_NAME])
        assertTrue(events.isEmpty())
        assertEquals(R.string.driver_name_error_short, vm.uiState.value.errorLabel)

        collector.cancel()
    }

    /**
     * A failed write must keep the driver on this screen. Continuing to Home would leave an
     * unnamed profile that sends them straight back here on the next launch — precisely the
     * "the app forgot me" behaviour this screen exists to end.
     */
    @Test
    fun `a failed save reports an error and stays put`() = runTest(testDispatcher) {
        val vm = viewModel(FailingWriteDataStore())

        val events = mutableListOf<DriverNameViewModel.Event>()
        val collector = launch { vm.events.collect { events += it } }

        vm.onSubmit("Ayrton")
        advanceUntilIdle()

        assertEquals(
            listOf(DriverNameViewModel.Event.ShowError(R.string.driver_name_error_save)),
            events
        )
        assertFalse(vm.uiState.value.isSaving)

        collector.cancel()
    }

    @Test
    fun `submitting trims the stored name`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(emptyPreferences())
        val vm = viewModel(dataStore)

        vm.onSubmit("   Ayrton   ")
        advanceUntilIdle()

        assertEquals("Ayrton", dataStore.current[DriverProfileStore.KEY_DRIVER_NAME])
    }

    /** Double-tapping the call to action must not enqueue a second save. */
    @Test
    fun `a second submit while saving is ignored`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(emptyPreferences())
        val vm = viewModel(dataStore)

        val events = mutableListOf<DriverNameViewModel.Event>()
        val collector = launch { vm.events.collect { events += it } }

        vm.onSubmit("Ayrton")
        vm.onSubmit("Ayrton")
        advanceUntilIdle()

        assertEquals(1, events.count { it is DriverNameViewModel.Event.NavigateToHome })

        collector.cancel()
    }

    @Test
    fun `an existing name is overwritten rather than duplicated`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(
            mutablePreferencesOf(
                DriverProfileStore.KEY_DRIVER_NAME to "Old Name",
                DriverProfileStore.KEY_PROFILE_COMPLETE to true
            )
        )
        val vm = viewModel(dataStore)

        vm.onSubmit("New Name")
        advanceUntilIdle()

        assertEquals("New Name", dataStore.current[DriverProfileStore.KEY_DRIVER_NAME])
    }
}
