package com.drivingcoach.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.drivingcoach.data.profile.DriverProfileStore.NameValidation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L1 (SWE.4) unit tests for the V1 local driver identity.
 *
 * Covers SRS DR-01 (persistence), DR-03 (2 … 100 character bound, inherited from UM-04),
 * DR-04 (completion flag independent of the name) and DR-05 (avatar initials).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriverProfileStoreTest {

    private val testDispatcher = StandardTestDispatcher()

    /** DataStore stub that applies writes, so persistence can actually be observed. */
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

    /** DataStore stub whose reads fail, for the degraded-read contract. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IllegalStateException("corrupt") }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = throw UnsupportedOperationException("write not supported")
    }

    private fun store(
        dataStore: DataStore<Preferences> = RecordingDataStore(emptyPreferences())
    ) = DriverProfileStore(dataStore, testDispatcher)

    // --- Validation (DR-03) --------------------------------------------------------------

    @Test
    fun `single character is rejected`() {
        assertEquals(NameValidation.TooShort, store().validate("A"))
    }

    @Test
    fun `two characters are accepted at the lower bound`() {
        assertEquals(NameValidation.Valid("Al"), store().validate("Al"))
    }

    @Test
    fun `one hundred characters are accepted at the upper bound`() {
        val name = "N".repeat(DriverProfileStore.MAX_NAME_LENGTH)
        assertEquals(NameValidation.Valid(name), store().validate(name))
    }

    @Test
    fun `one hundred and one characters are rejected`() {
        val name = "N".repeat(DriverProfileStore.MAX_NAME_LENGTH + 1)
        assertEquals(NameValidation.TooLong, store().validate(name))
    }

    @Test
    fun `blank input is short rather than long`() {
        // Whitespace is trimmed before measuring, so a field full of spaces is empty.
        assertEquals(NameValidation.TooShort, store().validate("        "))
    }

    @Test
    fun `surrounding whitespace is trimmed before storing`() {
        assertEquals(NameValidation.Valid("Ayrton"), store().validate("  Ayrton  "))
    }

    @Test
    fun `padding does not let an over-long name through`() {
        val name = " " + "N".repeat(DriverProfileStore.MAX_NAME_LENGTH + 1) + " "
        assertEquals(NameValidation.TooLong, store().validate(name))
    }

    // --- Persistence (DR-01, DR-04) -------------------------------------------------------

    @Test
    fun `saved name survives a read back`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(emptyPreferences())
        val subject = store(dataStore)

        subject.saveName("Ayrton Senna")

        assertEquals(DriverProfile("Ayrton Senna"), subject.readProfile())
    }

    @Test
    fun `saving stores the trimmed form`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(emptyPreferences())
        val subject = store(dataStore)

        subject.saveName("   Ayrton   ")

        assertEquals("Ayrton", dataStore.current[DriverProfileStore.KEY_DRIVER_NAME])
    }

    @Test
    fun `saving marks the profile complete`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(emptyPreferences())
        val subject = store(dataStore)

        subject.saveName("Ayrton")

        assertTrue(subject.isProfileComplete())
        assertEquals(true, dataStore.current[DriverProfileStore.KEY_PROFILE_COMPLETE])
    }

    @Test
    fun `an invalid name is not persisted`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(emptyPreferences())
        val subject = store(dataStore)

        val result = subject.saveName("A")

        assertEquals(NameValidation.TooShort, result)
        assertNull(dataStore.current[DriverProfileStore.KEY_DRIVER_NAME])
        assertFalse(subject.isProfileComplete())
    }

    @Test
    fun `a fresh install has no profile`() = runTest(testDispatcher) {
        assertNull(store().readProfile())
    }

    /**
     * The reason completion is an explicit flag rather than "is the name set?". A name left
     * behind by an earlier build must not be mistaken for a completed profile.
     */
    @Test
    fun `a name without the completion flag is not a profile`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(
            mutablePreferencesOf(DriverProfileStore.KEY_DRIVER_NAME to "Ayrton")
        )

        assertNull(store(dataStore).readProfile())
    }

    @Test
    fun `a completion flag without a name is not a profile`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(
            mutablePreferencesOf(DriverProfileStore.KEY_PROFILE_COMPLETE to true)
        )

        assertNull(store(dataStore).readProfile())
    }

    @Test
    fun `a blank stored name is not a profile`() = runTest(testDispatcher) {
        val dataStore = RecordingDataStore(
            mutablePreferencesOf(
                DriverProfileStore.KEY_DRIVER_NAME to "   ",
                DriverProfileStore.KEY_PROFILE_COMPLETE to true
            )
        )

        assertNull(store(dataStore).readProfile())
    }

    /**
     * A failed read must degrade to "no profile" rather than propagating. The caller then
     * routes to the idempotent naming screen instead of crashing on startup.
     */
    @Test
    fun `an unreadable store reports no profile instead of throwing`() = runTest(testDispatcher) {
        assertNull(store(FailingDataStore()).readProfile())
    }

    // --- Initials (DR-05) ------------------------------------------------------------------

    @Test
    fun `two names yield first and last initials`() {
        assertEquals("AS", store().initialsOf("Ayrton Senna"))
    }

    @Test
    fun `three names skip the middle`() {
        assertEquals("JF", store().initialsOf("Juan Manuel Fangio"))
    }

    @Test
    fun `a single name yields its first two characters`() {
        assertEquals("AY", store().initialsOf("Ayrton"))
    }

    @Test
    fun `repeated spaces do not produce blank initials`() {
        assertEquals("AS", store().initialsOf("Ayrton    Senna"))
    }

    @Test
    fun `an empty name yields a placeholder`() {
        assertEquals("?", store().initialsOf("   "))
    }

    /**
     * Initials are taken by code point, so an emoji name yields the whole character rather
     * than half a surrogate pair — which would render as a replacement glyph.
     */
    @Test
    fun `an astral plane character is not split`() {
        val rocket = "\uD83D\uDE80"
        assertEquals(rocket, store().initialsOf(rocket))
    }
}
