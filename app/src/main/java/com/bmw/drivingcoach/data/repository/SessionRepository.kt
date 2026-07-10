package com.bmw.drivingcoach.data.repository

import com.bmw.drivingcoach.data.api.ApiService
import com.bmw.drivingcoach.data.api.dto.SessionDetailDto
import com.bmw.drivingcoach.data.api.dto.SessionDto
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao
import com.bmw.drivingcoach.data.db.dao.LapDao
import com.bmw.drivingcoach.data.db.dao.SessionDao
import com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity
import com.bmw.drivingcoach.data.db.entity.LapEntity
import com.bmw.drivingcoach.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class SessionResult<out T> {
    data class Success<T>(val data: T) : SessionResult<T>()
    data class Error(val message: String, val code: Int? = null) : SessionResult<Nothing>()
}

@Singleton
class SessionRepository @Inject constructor(
    private val apiService: ApiService,
    private val sessionDao: SessionDao,
    private val lapDao: LapDao,
    private val coachingInsightDao: CoachingInsightDao
) {
    // Room is the single source of truth
    fun getSessionById(sessionId: Long): Flow<SessionEntity?> {
        return sessionDao.getSessionById(sessionId)
    }

    fun getLapsForSession(sessionId: Long): Flow<List<LapEntity>> {
        return lapDao.getLapsForSession(sessionId)
    }

    fun getInsightsForSession(sessionId: Long): Flow<List<CoachingInsightEntity>> {
        return coachingInsightDao.getInsightsForSession(sessionId)
    }

    fun getAllSessionsForUser(userId: String): Flow<List<SessionEntity>> {
        return sessionDao.getAllSessionsForUser(userId)
    }

    suspend fun fetchSessions(): SessionResult<List<SessionDto>> {
        return try {
            val response = apiService.getSessions()
            if (response.isSuccessful && response.body() != null) {
                val sessions = response.body()!!
                // Sync to Room (could be implemented for offline support)
                SessionResult.Success(sessions)
            } else {
                SessionResult.Error(
                    message = response.errorBody()?.string() ?: "Failed to fetch sessions",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            SessionResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun fetchSessionDetail(remoteId: String): SessionResult<SessionDetailDto> {
        return try {
            val response = apiService.getSession(remoteId)
            if (response.isSuccessful && response.body() != null) {
                val detail = response.body()!!
                // Sync laps and insights to Room
                syncSessionDetail(detail)
                SessionResult.Success(detail)
            } else {
                SessionResult.Error(
                    message = response.errorBody()?.string() ?: "Failed to fetch session",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            SessionResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun uploadSession(session: SessionEntity, filePath: String): SessionResult<String> {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return SessionResult.Error("Telemetry file not found")
            }

            val sessionIdBody = session.id.toString().toRequestBody("text/plain".toMediaType())
            val trackNameBody = session.trackName.toRequestBody("text/plain".toMediaType())
            val startedAtBody = session.startedAt.toString().toRequestBody("text/plain".toMediaType())
            val endedAtBody = (session.endedAt ?: System.currentTimeMillis()).toString()
                .toRequestBody("text/plain".toMediaType())

            val filePart = MultipartBody.Part.createFormData(
                "file",
                file.name,
                file.asRequestBody("application/octet-stream".toMediaType())
            )

            // Build optional startLine params if present
            val startLineLat1Body = session.startLineLat1?.toString()
                ?.toRequestBody("text/plain".toMediaType())
            val startLineLng1Body = session.startLineLng1?.toString()
                ?.toRequestBody("text/plain".toMediaType())
            val startLineLat2Body = session.startLineLat2?.toString()
                ?.toRequestBody("text/plain".toMediaType())
            val startLineLng2Body = session.startLineLng2?.toString()
                ?.toRequestBody("text/plain".toMediaType())

            val response = apiService.uploadTelemetry(
                sessionId = sessionIdBody,
                trackName = trackNameBody,
                startedAt = startedAtBody,
                endedAt = endedAtBody,
                file = filePart,
                startLineLat1 = startLineLat1Body,
                startLineLng1 = startLineLng1Body,
                startLineLat2 = startLineLat2Body,
                startLineLng2 = startLineLng2Body
            )

            if (response.isSuccessful && response.body() != null) {
                val uploadResponse = response.body()!!
                
                // Update session with remote ID and status
                sessionDao.updateUploadStatus(
                    id = session.id,
                    status = "DONE",
                    remoteId = uploadResponse.sessionId
                )
                
                SessionResult.Success(uploadResponse.sessionId)
            } else {
                // Mark as failed
                sessionDao.updateUploadStatus(
                    id = session.id,
                    status = "FAILED",
                    remoteId = null
                )
                
                SessionResult.Error(
                    message = response.errorBody()?.string() ?: "Upload failed",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            // Mark as failed
            sessionDao.updateUploadStatus(
                id = session.id,
                status = "FAILED",
                remoteId = null
            )
            SessionResult.Error(e.message ?: "Network error")
        }
    }

    private suspend fun syncSessionDetail(detail: SessionDetailDto) {
        // Find local session by remote ID
        // For now, we just sync laps and insights if there's a matching local session
        // This would need to be enhanced for full offline support
        
        // Convert DTOs to entities and insert
        // Note: This is a simplified sync - in production you'd want proper conflict resolution
    }

    suspend fun createSession(session: SessionEntity): Long {
        return sessionDao.insertSession(session)
    }

    suspend fun updateSessionEndTime(sessionId: Long, endedAt: Long) {
        sessionDao.updateSessionEndTime(sessionId, endedAt)
    }

    suspend fun insertLaps(laps: List<LapEntity>) {
        lapDao.insertLaps(laps)
    }

    suspend fun insertInsights(insights: List<CoachingInsightEntity>) {
        coachingInsightDao.insertInsights(insights)
    }
}
