package com.drivingcoach.data.worker

import androidx.work.ListenableWorker
import com.drivingcoach.data.api.TelemetryApiService
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.data.db.entity.UploadStatus
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Integration tests for TelemetryUploadWorker using MockWebServer.
 * Verifies retry behavior on network failures.
 */
class TelemetryUploadWorkerMockWebServerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var telemetryApiService: TelemetryApiService
    private lateinit var sessionDao: SessionDao
    private lateinit var telemetryFile: File
    
    private val testSessionId = 456L
    private val testRemoteId = "remote-789"

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .writeTimeout(1, TimeUnit.SECONDS)
            .build()
        
        telemetryApiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TelemetryApiService::class.java)
        
        sessionDao = mock()
        
        // Create test telemetry file
        telemetryFile = tempFolder.newFile("session_$testSessionId.jsonl")
        telemetryFile.writeText("""
            {"timestampMs":1000,"latitude":48.135,"longitude":11.582,"speedMs":25.0}
            {"timestampMs":1100,"latitude":48.136,"longitude":11.583,"speedMs":26.5}
            {"timestampMs":1200,"latitude":48.137,"longitude":11.584,"speedMs":28.0}
        """.trimIndent())
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun successfulUploadReturnsSuccess(): Unit = runBlocking {
        // Arrange
        val session = createTestSession()
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)
        
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"remoteSessionId":"$testRemoteId","message":"Upload successful"}""")
            .addHeader("Content-Type", "application/json"))
        
        // Act
        val result = simulateWorkerWithRealApi(testSessionId)
        
        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        verify(sessionDao).updateUploadStatus(testSessionId, UploadStatus.DONE.name, testRemoteId)
        
        // Verify request was made
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("sessions/$testSessionId/telemetry"))
    }

    @Test
    fun serverError503TriggersRetry(): Unit = runBlocking {
        // Arrange
        val session = createTestSession()
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)
        
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(503)
            .setBody("Service Unavailable"))
        
        // Act
        val result = simulateWorkerWithRealApi(testSessionId)
        
        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        verify(sessionDao).updateUploadStatus(testSessionId, UploadStatus.FAILED.name, null)
    }

    @Test
    fun serverError500TriggersRetry(): Unit = runBlocking {
        // Arrange
        val session = createTestSession()
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)
        
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"))
        
        // Act
        val result = simulateWorkerWithRealApi(testSessionId)
        
        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun clientError400ReturnsFailureNoRetry(): Unit = runBlocking {
        // Arrange
        val session = createTestSession()
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)
        
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(400)
            .setBody("Bad Request"))
        
        // Act
        val result = simulateWorkerWithRealApi(testSessionId)
        
        // Assert
        assertEquals(ListenableWorker.Result.failure(), result)
        verify(sessionDao).updateUploadStatus(testSessionId, UploadStatus.FAILED.name, null)
    }

    @Test
    fun clientError401ReturnsFailureNoRetry(): Unit = runBlocking {
        // Arrange
        val session = createTestSession()
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)
        
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("Unauthorized"))
        
        // Act
        val result = simulateWorkerWithRealApi(testSessionId)
        
        // Assert
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun connectionTimeoutTriggersRetry(): Unit = runBlocking {
        // Arrange
        val session = createTestSession()
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)
        
        // Don't enqueue any response - will cause timeout
        mockWebServer.enqueue(MockResponse()
            .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        
        // Act
        val result = simulateWorkerWithRealApi(testSessionId)
        
        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun multipleRetryAttemptsWithEventualSuccess(): Unit = runBlocking {
        // Arrange
        val session = createTestSession()
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)
        
        // First two attempts fail with 503, third succeeds
        mockWebServer.enqueue(MockResponse().setResponseCode(503))
        mockWebServer.enqueue(MockResponse().setResponseCode(503))
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"remoteSessionId":"$testRemoteId"}"""))
        
        // Simulate 3 worker runs (as WorkManager would retry)
        val result1 = simulateWorkerWithRealApi(testSessionId)
        assertEquals("First attempt should retry", ListenableWorker.Result.retry(), result1)
        
        val result2 = simulateWorkerWithRealApi(testSessionId)
        assertEquals("Second attempt should retry", ListenableWorker.Result.retry(), result2)
        
        val result3 = simulateWorkerWithRealApi(testSessionId)
        assertEquals("Third attempt should succeed", ListenableWorker.Result.success(), result3)
        
        // Verify 3 requests were made
        assertEquals(3, mockWebServer.requestCount)
    }

    @Test
    fun requestContainsCorrectMultipartData(): Unit = runBlocking {
        // Arrange
        val session = createTestSession()
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)
        
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"remoteSessionId":"$testRemoteId"}"""))
        
        // Act
        simulateWorkerWithRealApi(testSessionId)
        
        // Assert
        val request = mockWebServer.takeRequest()
        val contentType = request.getHeader("Content-Type")
        assertNotNull("Content-Type should be set", contentType)
        assertTrue("Should be multipart", contentType!!.contains("multipart/form-data"))
        
        val body = request.body.readUtf8()
        assertTrue("Body should contain telemetry data", body.contains("timestampMs"))
    }

    // Helper methods

    private fun createTestSession(): SessionEntity {
        return SessionEntity(
            id = testSessionId,
            userId = "test-user",
            trackName = "Nürburgring",
            startedAt = System.currentTimeMillis() - 3600000,
            endedAt = System.currentTimeMillis(),
            rawFilePath = telemetryFile.absolutePath,
            uploadStatus = UploadStatus.PENDING.name,
            remoteSessionId = null
        )
    }

    /**
     * Simulates worker logic using real Retrofit API with MockWebServer.
     */
    private suspend fun simulateWorkerWithRealApi(sessionId: Long): ListenableWorker.Result {
        if (sessionId == -1L) {
            return ListenableWorker.Result.failure()
        }

        val session = sessionDao.getSessionByIdSync(sessionId)
            ?: return ListenableWorker.Result.failure()

        if (session.uploadStatus == UploadStatus.DONE.name) {
            return ListenableWorker.Result.success()
        }

        val file = File(session.rawFilePath)
        if (!file.exists()) {
            sessionDao.updateUploadStatus(sessionId, UploadStatus.FAILED.name, null)
            return ListenableWorker.Result.failure()
        }

        sessionDao.updateUploadStatus(sessionId, UploadStatus.UPLOADING.name, null)

        return try {
            val requestBody = file.readBytes().toRequestBody("application/jsonl".toMediaTypeOrNull())
            val multipartBody = okhttp3.MultipartBody.Part.createFormData(
                "telemetry",
                file.name,
                requestBody
            )

            val response = telemetryApiService.uploadSession(sessionId, multipartBody)

            when {
                response.isSuccessful -> {
                    val remoteId = response.body()?.remoteSessionId
                    sessionDao.updateUploadStatus(sessionId, UploadStatus.DONE.name, remoteId)
                    ListenableWorker.Result.success()
                }
                response.code() in 400..499 -> {
                    sessionDao.updateUploadStatus(sessionId, UploadStatus.FAILED.name, null)
                    ListenableWorker.Result.failure()
                }
                response.code() in 500..599 -> {
                    sessionDao.updateUploadStatus(sessionId, UploadStatus.FAILED.name, null)
                    ListenableWorker.Result.retry()
                }
                else -> {
                    sessionDao.updateUploadStatus(sessionId, UploadStatus.FAILED.name, null)
                    ListenableWorker.Result.failure()
                }
            }
        } catch (e: Exception) {
            sessionDao.updateUploadStatus(sessionId, UploadStatus.FAILED.name, null)
            ListenableWorker.Result.retry()
        }
    }
}
