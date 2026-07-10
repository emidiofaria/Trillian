package com.drivingcoach.data.api

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val authEventBus: AuthEventBus
) : Interceptor {

    companion object {
        val KEY_JWT = stringPreferencesKey("jwt_token")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth header for login and register endpoints
        val path = originalRequest.url.encodedPath
        if (path.contains("auth/login") || path.contains("auth/register")) {
            return chain.proceed(originalRequest)
        }

        // Get token from DataStore (blocking for interceptor)
        val token = runBlocking {
            try {
                dataStore.data.first()[KEY_JWT]
            } catch (e: Exception) {
                null
            }
        }

        val request = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        // Handle 401 Unauthorized
        if (response.code == 401) {
            // Clear token and emit session expired event
            runBlocking {
                try {
                    dataStore.updateData { prefs ->
                        prefs.toMutablePreferences().apply {
                            remove(KEY_JWT)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore DataStore errors during cleanup
                }
            }
            authEventBus.emitSessionExpired()
        }

        return response
    }
}
