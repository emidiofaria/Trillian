package com.bmw.drivingcoach.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.bmw.drivingcoach.data.api.TelemetryApiService
import com.bmw.drivingcoach.data.db.dao.SessionDao
import com.bmw.drivingcoach.data.db.entity.UploadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class TelemetryUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionDao: SessionDao,
    private val telemetryApiService: TelemetryApiService
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "TelemetryUploadWorker"
        const val KEY_SESSION_ID = "session_id"
        const val TAG_TELEMETRY_UPLOAD = "telemetry_upload"
        
        private const val INITIAL_BACKOFF_SECONDS = 10L
        private const val MAX_RETRIES = 5

        /**
         * Creates a OneTimeWorkRequest for uploading telemetry data.
         */
        fun buildRequest(sessionId: Long): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putLong(KEY_SESSION_ID, sessionId)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return OneTimeWorkRequestBuilder<TelemetryUploadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    INITIAL_BACKOFF_SECONDS,
                    TimeUnit.SECONDS
                )
                .addTag(TAG_TELEMETRY_UPLOAD)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        
        if (sessionId == -1L) {
            Log.e(TAG, "Invalid session ID in worker input")
            return Result.failure()
        }

        Log.i(TAG, "Starting upload for session: $sessionId, attempt: $runAttemptCount")

        // Check max retries
        if (runAttemptCount > MAX_RETRIES) {
            Log.e(TAG, "Max retries exceeded for session: $sessionId")
            updateStatus(sessionId, UploadStatus.FAILED)
            return Result.failure()
        }

        // Load session from database
        val session = sessionDao.getSessionByIdSync(sessionId)
        if (session == null) {
            Log.e(TAG, "Session not found: $sessionId")
            return Result.failure()
        }

        // Check if already uploaded
        if (session.uploadStatus == UploadStatus.DONE.name) {
            Log.i(TAG, "Session already uploaded: $sessionId")
            return Result.success()
        }

        // Get telemetry file
        val telemetryFile = File(session.rawFilePath)
        if (!telemetryFile.exists()) {
            Log.e(TAG, "Telemetry file not found: ${session.rawFilePath}")
            updateStatus(sessionId, UploadStatus.FAILED)
            return Result.failure()
        }

        // Update status to UPLOADING
        updateStatus(sessionId, UploadStatus.UPLOADING)

        return try {
            // Create multipart request
            val requestBody = telemetryFile.asRequestBody("application/jsonl".toMediaTypeOrNull())
            val multipartBody = MultipartBody.Part.createFormData(
                "telemetry",
                telemetryFile.name,
                requestBody
            )

            // Make API call
            val response = telemetryApiService.uploadSession(sessionId, multipartBody)

            when {
                response.isSuccessful -> {
                    val uploadResponse = response.body()
                    val remoteId = uploadResponse?.remoteSessionId
                    
                    Log.i(TAG, "Upload successful for session: $sessionId, remoteId: $remoteId")
                    sessionDao.updateUploadStatus(sessionId, UploadStatus.DONE.name, remoteId)
                    Result.success()
                }
                response.code() in 400..499 -> {
                    // Client error - don't retry
                    Log.e(TAG, "Client error ${response.code()} for session: $sessionId")
                    updateStatus(sessionId, UploadStatus.FAILED)
                    Result.failure()
                }
                response.code() in 500..599 -> {
                    // Server error - retry
                    Log.w(TAG, "Server error ${response.code()} for session: $sessionId, will retry")
                    updateStatus(sessionId, UploadStatus.FAILED)
                    Result.retry()
                }
                else -> {
                    Log.e(TAG, "Unexpected response ${response.code()} for session: $sessionId")
                    updateStatus(sessionId, UploadStatus.FAILED)
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error uploading session: $sessionId", e)
            updateStatus(sessionId, UploadStatus.FAILED)
            // Network errors should be retried
            Result.retry()
        }
    }

    private suspend fun updateStatus(sessionId: Long, status: UploadStatus) {
        try {
            sessionDao.updateUploadStatus(sessionId, status.name, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update status for session: $sessionId", e)
        }
    }
}
