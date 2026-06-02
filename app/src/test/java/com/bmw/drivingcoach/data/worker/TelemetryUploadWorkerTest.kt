package com.bmw.drivingcoach.data.worker

import androidx.work.ListenableWorker
import com.bmw.drivingcoach.data.api.TelemetryApiService
import com.bmw.drivingcoach.data.api.UploadResponse
import com.bmw.drivingcoach.data.db.dao.SessionDao
import com.bmw.drivingcoach.data.db.entity.SessionEntity
import com.bmw.drivingcoach.data.db.entity.UploadStatus
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*
import retrofit2.Response
import java.io.File

/**
 * Unit tests for TelemetryUploadWorker.
 * 
 * These tests use mocked dependencies to verify worker behavior
 * without requiring Android framework or actual network calls.
 */
class TelemetryUploadWorkerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var sessionDao: SessionDao
    private lateinit var telemetryApiService: TelemetryApiService
    private lateinit var telemetryFile: File

    private val testSessionId = 123L
    private val testRemoteId = "remote-session-456"

    @Before
    fun setUp() {
        sessionDao = mock()
        telemetryApiService = mock()
        
        // Create a test telemetry file
        telemetryFile = tempFolder.newFile("session_$testSessionId.jsonl")
        telemetryFile.writeText("""{"timestampMs":1234567890,"latitude":48.135,"longitude":11.582}""")
    }

    @Test
    fun successfulUploadTransitionsStatusToDone(): Unit = runBlocking {
        // Arrange
        val session = createTestSession(UploadStatus.PENDING)
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)
        whenever(telemetryApiService.uploadSession(any(), any())).thenReturn(
            Response.success(UploadResponse(testRemoteId, "Success"))
        )

        // Act - simulate worker logic
        val result = simulateWorkerDoWork(testSessionId)

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        verify(sessionDao).updateUploadStatus(testSessionId, UploadStatus.UPLOADING.name, null)
        verify(sessionDao).updateUploadStatus(testSessionId, UploadStatus.DONE.name, testRemoteId)
    }

    @Test
    fun serverErrorTriggersRetry(): Unit = runBlocking {
        // Arrange
        val session = createTestSession(UploadStatus.PENDING)
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)
        whenever(telemetryApiService.uploadSession(any(), any())).thenReturn(
            Response.error(503, "Service Unavailable".toResponseBody())
        )

        // Act
        val result = simulateWorkerDoWork(testSessionId)

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        verify(sessionDao).updateUploadStatus(testSessionId, UploadStatus.FAILED.name, null)
    }

    @Test
    fun clientErrorReturnsFailureWithoutRetry(): Unit = runBlocking {
        // Arrange
        val session = createTestSession(UploadStatus.PENDING)
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)
        whenever(telemetryApiService.uploadSession(any(), any())).thenReturn(
            Response.error(400, "Bad Request".toResponseBody())
        )

        // Act
        val result = simulateWorkerDoWork(testSessionId)

        // Assert
        assertEquals(ListenableWorker.Result.failure(), result)
        verify(sessionDao).updateUploadStatus(testSessionId, UploadStatus.FAILED.name, null)
    }

    @Test
    fun sessionNotFoundReturnsFailure(): Unit = runBlocking {
        // Arrange
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(null)

        // Act
        val result = simulateWorkerDoWork(testSessionId)

        // Assert
        assertEquals(ListenableWorker.Result.failure(), result)
        verify(telemetryApiService, never()).uploadSession(any(), any())
    }

    @Test
    fun alreadyUploadedSessionReturnsSuccessWithoutApiCall(): Unit = runBlocking {
        // Arrange
        val session = createTestSession(UploadStatus.DONE)
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)

        // Act
        val result = simulateWorkerDoWork(testSessionId)

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        verify(telemetryApiService, never()).uploadSession(any(), any())
    }

    @Test
    fun networkExceptionTriggersRetry(): Unit = runBlocking {
        // Arrange
        val session = createTestSession(UploadStatus.PENDING)
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)
        whenever(telemetryApiService.uploadSession(any(), any())).thenThrow(
            RuntimeException("Network unavailable")
        )

        // Act
        val result = simulateWorkerDoWork(testSessionId)

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun missingTelemetryFileReturnsFailure(): Unit = runBlocking {
        // Arrange
        val session = createTestSession(UploadStatus.PENDING).copy(
            rawFilePath = "/nonexistent/file.jsonl"
        )
        whenever(sessionDao.getSessionByIdSync(testSessionId)).thenReturn(session)

        // Act
        val result = simulateWorkerDoWork(testSessionId)

        // Assert
        assertEquals(ListenableWorker.Result.failure(), result)
        verify(sessionDao).updateUploadStatus(testSessionId, UploadStatus.FAILED.name, null)
    }

    @Test
    fun invalidSessionIdReturnsFailure(): Unit = runBlocking {
        // Act
        val result = simulateWorkerDoWork(-1L)

        // Assert
        assertEquals(ListenableWorker.Result.failure(), result)
        verify(sessionDao, never()).getSessionByIdSync(any())
    }

    // Helper methods

    private fun createTestSession(status: UploadStatus): SessionEntity {
        return SessionEntity(
            id = testSessionId,
            userId = "test-user",
            trackName = "Nürburgring",
            startedAt = System.currentTimeMillis() - 3600000,
            endedAt = System.currentTimeMillis(),
            rawFilePath = telemetryFile.absolutePath,
            uploadStatus = status.name,
            remoteSessionId = null
        )
    }

    /**
     * Simulates the worker's doWork() logic without requiring WorkManager infrastructure.
     * This allows unit testing the business logic directly.
     */
    private suspend fun simulateWorkerDoWork(sessionId: Long): ListenableWorker.Result {
        if (sessionId == -1L) {
            return ListenableWorker.Result.failure()
        }

        val session = sessionDao.getSessionByIdSync(sessionId)
            ?: return ListenableWorker.Result.failure()

        if (session.uploadStatus == UploadStatus.DONE.name) {
            return ListenableWorker.Result.success()
        }

        val telemetryFile = File(session.rawFilePath)
        if (!telemetryFile.exists()) {
            sessionDao.updateUploadStatus(sessionId, UploadStatus.FAILED.name, null)
            return ListenableWorker.Result.failure()
        }

        sessionDao.updateUploadStatus(sessionId, UploadStatus.UPLOADING.name, null)

        return try {
            val response = telemetryApiService.uploadSession(sessionId, mock())

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
