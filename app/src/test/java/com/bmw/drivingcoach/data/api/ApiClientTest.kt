package com.bmw.drivingcoach.data.api

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.bmw.drivingcoach.data.api.dto.AuthResponse
import com.bmw.drivingcoach.data.api.dto.UploadResponse
import com.bmw.drivingcoach.data.api.dto.UserDto
import com.bmw.drivingcoach.data.repository.AuthRepository
import com.bmw.drivingcoach.data.repository.AuthResult
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class ApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var fakeDataStore: FakeDataStore
    private lateinit var authEventBus: AuthEventBus
    private lateinit var authRepository: AuthRepository

    private val gson = Gson()

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        fakeDataStore = FakeDataStore()
        authEventBus = AuthEventBus()

        val authInterceptor = AuthInterceptor(fakeDataStore, authEventBus)

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)
        authRepository = AuthRepository(apiService, fakeDataStore)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `login success stores token in DataStore`() = runTest {
        // Given
        val authResponse = AuthResponse(token = "test_jwt_token", userId = "user123")
        val userResponse = UserDto(id = "user123", email = "test@example.com", displayName = "Test User")
        
        // Enqueue login response
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(gson.toJson(authResponse)))
        
        // Enqueue getMe response (called after login)
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(gson.toJson(userResponse)))

        // When
        val result = authRepository.login("test@example.com", "password123")

        // Then
        assertTrue(result is AuthResult.Success)
        
        val storedToken = fakeDataStore.getValue(AuthRepository.KEY_JWT)
        assertEquals("test_jwt_token", storedToken)
        
        val storedUserId = fakeDataStore.getValue(AuthRepository.KEY_USER_ID)
        assertEquals("user123", storedUserId)
    }

    @Test
    fun `401 on any request emits session expired via AuthEventBus`() = runTest {
        // Given - set a token first
        fakeDataStore.setValue(AuthInterceptor.KEY_JWT, "old_token")

        // Enqueue 401 response
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        // Collect events
        var sessionExpiredEmitted = false
        val job = this.launch {
            authEventBus.events.collect { event ->
                if (event is AuthEvent.SessionExpired) {
                    sessionExpiredEmitted = true
                }
            }
        }

        // When
        try {
            apiService.getMe()
        } catch (e: Exception) {
            // Ignore - we're testing the interceptor behavior
        }

        // Give time for event emission
        delay(100)
        job.cancel()

        // Then
        assertTrue("Session expired event should be emitted", sessionExpiredEmitted)
        
        // Token should be cleared
        val storedToken = fakeDataStore.getValue(AuthInterceptor.KEY_JWT)
        assertNull("Token should be cleared after 401", storedToken)
    }

    @Test
    fun `register success stores token`() = runTest {
        // Given
        val authResponse = AuthResponse(token = "new_user_token", userId = "new_user_123")
        
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(gson.toJson(authResponse)))

        // When
        val result = authRepository.register("new@example.com", "password123", "New User")

        // Then
        assertTrue(result is AuthResult.Success)
        
        val storedToken = fakeDataStore.getValue(AuthRepository.KEY_JWT)
        assertEquals("new_user_token", storedToken)
    }

    @Test
    fun `login failure returns error`() = runTest {
        // Given
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("""{"error": "Invalid credentials"}"""))

        // When
        val result = authRepository.login("wrong@example.com", "wrongpassword")

        // Then
        assertTrue(result is AuthResult.Error)
        assertEquals(401, (result as AuthResult.Error).code)
    }

    @Test
    fun `uploadTelemetry sends correct multipart fields`() = runTest {
        // Given - set a token first
        fakeDataStore.setValue(AuthInterceptor.KEY_JWT, "test_token")

        val uploadResponse = UploadResponse(sessionId = "remote_session_123", status = "PROCESSING")
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(201)
            .setBody(gson.toJson(uploadResponse)))

        // Create a temp file for testing
        val tempFile = File.createTempFile("test_telemetry", ".jsonl")
        tempFile.writeText("""{"timestampMs":1234567890}""")

        try {
            // When
            val sessionIdBody = "123".toRequestBody("text/plain".toMediaType())
            val trackNameBody = "Test Track".toRequestBody("text/plain".toMediaType())
            val startedAtBody = "1234567890000".toRequestBody("text/plain".toMediaType())
            val endedAtBody = "1234570000000".toRequestBody("text/plain".toMediaType())
            val filePart = MultipartBody.Part.createFormData(
                "file",
                tempFile.name,
                tempFile.asRequestBody("application/octet-stream".toMediaType())
            )

            val response = apiService.uploadTelemetry(
                sessionId = sessionIdBody,
                trackName = trackNameBody,
                startedAt = startedAtBody,
                endedAt = endedAtBody,
                file = filePart
            )

            // Then
            assertTrue(response.isSuccessful)
            assertEquals("remote_session_123", response.body()?.sessionId)

            // Verify the request
            val request = mockWebServer.takeRequest()
            assertEquals("POST", request.method)
            assertTrue(request.path?.contains("telemetry/upload") == true)
            assertTrue(request.headers["Authorization"]?.contains("Bearer test_token") == true)
            
            val body = request.body.readUtf8()
            assertTrue("Request should contain sessionId", body.contains("123"))
            assertTrue("Request should contain trackName", body.contains("Test Track"))
        } finally {
            tempFile.delete()
        }
    }
}

/**
 * Simple fake DataStore implementation for testing using a map
 */
class FakeDataStore : DataStore<Preferences> {
    private val storage = mutableMapOf<Preferences.Key<*>, Any?>()
    private val prefsFlow = MutableStateFlow(createPreferences())

    override val data: Flow<Preferences> = prefsFlow

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val currentPrefs = prefsFlow.value
        val newPrefs = transform(currentPrefs)
        
        // Extract values from the new preferences
        newPrefs.asMap().forEach { (key, value) ->
            @Suppress("UNCHECKED_CAST")
            storage[key as Preferences.Key<Any>] = value
        }
        
        // Check for removals - keys in current that aren't in new
        val currentKeys = storage.keys.toList()
        val newKeys = newPrefs.asMap().keys
        currentKeys.forEach { key ->
            if (key !in newKeys) {
                storage.remove(key)
            }
        }
        
        prefsFlow.value = createPreferences()
        return prefsFlow.value
    }

    fun <T> setValue(key: Preferences.Key<T>, value: T) {
        @Suppress("UNCHECKED_CAST")
        storage[key as Preferences.Key<Any>] = value
        prefsFlow.value = createPreferences()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getValue(key: Preferences.Key<T>): T? = storage[key] as? T

    private fun createPreferences(): Preferences {
        val pairs = storage.entries.mapNotNull { (key, value) ->
            if (value != null) {
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is String -> (key as Preferences.Key<String>) to value
                    is Int -> (key as Preferences.Key<Int>) to value
                    is Long -> (key as Preferences.Key<Long>) to value
                    is Boolean -> (key as Preferences.Key<Boolean>) to value
                    is Float -> (key as Preferences.Key<Float>) to value
                    is Double -> (key as Preferences.Key<Double>) to value
                    else -> null
                }
            } else null
        }
        return preferencesOf(*pairs.toTypedArray())
    }
}
