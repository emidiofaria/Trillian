package com.drivingcoach.data.profile

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.drivingcoach.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single owner of the V1 local driver identity (SRS DR-01 … DR-08).
 *
 * V1 ships without a backend, so there is no account, no token and no server-side user.
 * What the app has instead is a name the driver typed once, kept on the device. This class
 * is the only place that knows where that name lives.
 *
 * **The name is cosmetic.** It is displayed on the Profile screen and reduced to avatar
 * initials, and it is used for nothing else. Session ownership deliberately continues to use
 * the hardcoded `demo_user` id (SRS DP-01, single-user MVP), because keying rows to a
 * user-editable string would orphan a driver's entire history the moment they corrected a
 * typo in their own name.
 *
 * Completion is tracked by its own [KEY_PROFILE_COMPLETE] flag rather than inferred from
 * "is the name non-blank?". The two are not equivalent: an interrupted rename can briefly
 * leave the name absent, and inferring from it would bounce an established driver back to
 * first-run setup — with their sessions apparently gone. One extra boolean buys immunity
 * from that whole class of bug.
 */
@Singleton
class DriverProfileStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * Outcome of validating a candidate driver name.
     *
     * Modelled as a type rather than a bare `Boolean` so the UI can explain *why* a name was
     * rejected instead of leaving the driver staring at a disabled button.
     */
    sealed interface NameValidation {
        /** [value] is the trimmed, storable form — callers should persist this, not the raw input. */
        data class Valid(val value: String) : NameValidation
        object TooShort : NameValidation
        object TooLong : NameValidation
    }

    /**
     * Reads the stored profile.
     *
     * Returns `null` when no profile has been completed. Any read failure is also reported as
     * `null`: a driver who cannot be identified is sent to the (idempotent) naming screen,
     * which is recoverable, rather than into Home with a blank identity.
     */
    suspend fun readProfile(): DriverProfile? = withContext(ioDispatcher) {
        val preferences = runCatching { dataStore.data.first() }
            .onFailure { Log.w(TAG, "could not read driver profile", it) }
            .getOrNull() ?: return@withContext null

        profileFrom(preferences)
    }

    /**
     * Derives the profile from an already-loaded snapshot.
     *
     * Startup reads the preferences exactly once and needs the driver name out of that same
     * snapshot; going back to [readProfile] there would cost a second DataStore read on the
     * cold-start critical path (SRS UI-04) for data already in hand.
     */
    fun profileFrom(preferences: Preferences): DriverProfile? {
        val complete = preferences[KEY_PROFILE_COMPLETE] ?: false
        val name = preferences[KEY_DRIVER_NAME]

        return if (!complete || name.isNullOrBlank()) null else DriverProfile(name)
    }

    /** True when a driver has completed first-run naming. Drives the startup destination. */
    suspend fun isProfileComplete(): Boolean = readProfile() != null

    /**
     * Persists [name] and marks the profile complete.
     *
     * Both keys are written in a single `edit` transaction, so the store can never end up
     * holding a completion flag without the name it is supposed to guarantee.
     */
    suspend fun saveName(name: String): NameValidation {
        val validation = validate(name)
        if (validation !is NameValidation.Valid) return validation

        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                preferences[KEY_DRIVER_NAME] = validation.value
                preferences[KEY_PROFILE_COMPLETE] = true
            }
        }

        return validation
    }

    /**
     * Validates a candidate name against SRS UM-04 (2 … 100 characters).
     *
     * The bound is applied to the *trimmed* value, so a screen full of spaces is short, not
     * long — which is both true and the more useful thing to tell the driver.
     */
    fun validate(name: String): NameValidation {
        val trimmed = name.trim()
        return when {
            trimmed.length < MIN_NAME_LENGTH -> NameValidation.TooShort
            trimmed.length > MAX_NAME_LENGTH -> NameValidation.TooLong
            else -> NameValidation.Valid(trimmed)
        }
    }

    /**
     * Reduces a display name to at most two avatar initials.
     *
     * Operates on Unicode code points rather than `Char`, so a name beginning with an
     * emoji or any astral-plane character yields that character instead of half of its
     * surrogate pair.
     */
    fun initialsOf(name: String): String {
        val parts = name.trim().split(WHITESPACE).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].firstCodePoints(2).uppercase()
            else -> (parts.first().firstCodePoints(1) + parts.last().firstCodePoints(1)).uppercase()
        }
    }

    private fun String.firstCodePoints(count: Int): String {
        var index = 0
        var taken = 0
        while (index < length && taken < count) {
            index += Character.charCount(codePointAt(index))
            taken++
        }
        return substring(0, index)
    }

    companion object {
        private const val TAG = "DriverProfileStore"

        private val WHITESPACE = Regex("\\s+")

        /** SRS UM-04 — reused verbatim so V2's Firebase display name keeps the same bounds. */
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 100

        /**
         * Shared with the pre-existing profile storage so an already-populated name survives
         * the move to this class.
         */
        val KEY_DRIVER_NAME = stringPreferencesKey("user_name")

        val KEY_PROFILE_COMPLETE = booleanPreferencesKey("driver_profile_complete")
    }
}

/** The V1 local driver identity. Display-only — never an ownership key. */
data class DriverProfile(val displayName: String)
