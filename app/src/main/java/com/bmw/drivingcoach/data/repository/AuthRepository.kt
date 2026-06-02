package com.bmw.drivingcoach.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bmw.drivingcoach.data.api.ApiService
import com.bmw.drivingcoach.data.api.dto.LoginRequest
import com.bmw.drivingcoach.data.api.dto.RegisterRequest
import com.bmw.drivingcoach.data.api.dto.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String, val code: Int? = null) : AuthResult<Nothing>()
}

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_JWT = stringPreferencesKey("jwt_token")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_USER_EMAIL = stringPreferencesKey("user_email")
    }

    val token: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_JWT]
    }

    val isLoggedIn: Flow<Boolean> = token.map { it != null }

    suspend fun login(email: String, password: String): AuthResult<Unit> {
        return try {
            val response = apiService.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                saveToken(authResponse.token, authResponse.userId)
                
                // Fetch user profile after login
                fetchAndSaveUserProfile()
                
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error(
                    message = response.errorBody()?.string() ?: "Login failed",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun register(email: String, password: String, displayName: String): AuthResult<Unit> {
        return try {
            val response = apiService.register(RegisterRequest(email, password, displayName))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                saveToken(authResponse.token, authResponse.userId)
                
                // Save display name and email locally
                saveUserProfile(displayName, email)
                
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error(
                    message = response.errorBody()?.string() ?: "Registration failed",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun getMe(): AuthResult<UserDto> {
        return try {
            val response = apiService.getMe()
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                saveUserProfile(user.displayName ?: "", user.email)
                AuthResult.Success(user)
            } else {
                AuthResult.Error(
                    message = response.errorBody()?.string() ?: "Failed to fetch profile",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun signOut() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_JWT)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_USER_EMAIL)
        }
    }

    private suspend fun saveToken(token: String, userId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_JWT] = token
            prefs[KEY_USER_ID] = userId
        }
    }

    private suspend fun saveUserProfile(displayName: String, email: String) {
        dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = displayName
            prefs[KEY_USER_EMAIL] = email
        }
    }

    private suspend fun fetchAndSaveUserProfile() {
        try {
            val response = apiService.getMe()
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                saveUserProfile(user.displayName ?: "", user.email)
            }
        } catch (e: Exception) {
            // Ignore profile fetch errors during login
        }
    }
}
