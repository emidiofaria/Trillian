package com.bmw.drivingcoach.ui.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmw.drivingcoach.data.db.BMWDatabase
import com.bmw.drivingcoach.data.db.dao.LapDao
import com.bmw.drivingcoach.data.db.dao.SessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfile(
    val displayName: String,
    val email: String,
    val initials: String
)

data class UserStats(
    val totalSessions: Int,
    val totalLaps: Int,
    val bestLapMs: Long?
)

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val stats: UserStats = UserStats(0, 0, null),
    val error: String? = null
)

sealed class ProfileEvent {
    object NavigateToLogin : ProfileEvent()
    data class ShowError(val message: String) : ProfileEvent()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val lapDao: LapDao,
    private val database: BMWDatabase,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    // TODO: Replace with actual user ID from auth
    private val currentUserId = "demo_user"

    companion object {
        val KEY_JWT = stringPreferencesKey("jwt_token")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_USER_EMAIL = stringPreferencesKey("user_email")
    }

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                // Load user profile from DataStore
                val prefs = dataStore.data.first()
                val displayName = prefs[KEY_USER_NAME] ?: "Demo User"
                val email = prefs[KEY_USER_EMAIL] ?: "demo@bmw.com"
                val initials = getInitials(displayName)

                val profile = UserProfile(
                    displayName = displayName,
                    email = email,
                    initials = initials
                )

                // Load aggregate stats
                val sessions = sessionDao.getAllSessionsForUser(currentUserId).first()
                var totalLaps = 0
                var bestLapMs: Long? = null

                for (session in sessions) {
                    val laps = lapDao.getLapsForSession(session.id).first()
                    totalLaps += laps.size
                    val sessionBest = laps.minByOrNull { it.durationMs }
                    if (sessionBest != null && (bestLapMs == null || sessionBest.durationMs < bestLapMs)) {
                        bestLapMs = sessionBest.durationMs
                    }
                }

                val stats = UserStats(
                    totalSessions = sessions.size,
                    totalLaps = totalLaps,
                    bestLapMs = bestLapMs
                )

                _uiState.value = ProfileUiState(
                    isLoading = false,
                    profile = profile,
                    stats = stats
                )
            } catch (e: Exception) {
                _uiState.value = ProfileUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load profile"
                )
            }
        }
    }

    private fun getInitials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts.first().first()}${parts.last().first()}".uppercase()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                // Clear JWT and user data from DataStore
                dataStore.edit { prefs ->
                    prefs.remove(KEY_JWT)
                    prefs.remove(KEY_USER_NAME)
                    prefs.remove(KEY_USER_EMAIL)
                }

                // Clear Room database
                database.clearAllTables()

                // Navigate to login
                _events.emit(ProfileEvent.NavigateToLogin)
            } catch (e: Exception) {
                _events.emit(ProfileEvent.ShowError("Failed to sign out: ${e.message}"))
            }
        }
    }
}
