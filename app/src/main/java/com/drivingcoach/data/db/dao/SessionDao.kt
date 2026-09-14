package com.drivingcoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.drivingcoach.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun getSessionById(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionByIdSync(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE userId = :userId ORDER BY startedAt DESC")
    fun getAllSessionsForUser(userId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE uploadStatus = 'PENDING' OR uploadStatus = 'FAILED'")
    suspend fun getPendingUploadSessions(): List<SessionEntity>

    /**
     * Every recorded telemetry file path, for bulk cleanup (SRS DR-07).
     *
     * Deliberately returns the stored paths rather than listing the telemetry directory:
     * clearing user data must delete only files this app recorded, never whatever else
     * happens to be sitting in that folder.
     */
    @Query("SELECT rawFilePath FROM sessions WHERE rawFilePath != ''")
    suspend fun getAllRawFilePaths(): List<String>
    
    @Query("SELECT * FROM sessions WHERE uploadStatus = 'PENDING' AND startedAt < :beforeTimestamp")
    suspend fun getStaleUploadSessions(beforeTimestamp: Long): List<SessionEntity>

    @Query("UPDATE sessions SET uploadStatus = :status, remoteSessionId = :remoteId WHERE id = :id")
    suspend fun updateUploadStatus(id: Long, status: String, remoteId: String? = null)
    
    @Query("UPDATE sessions SET processingStatus = :status WHERE id = :id")
    suspend fun updateProcessingStatus(id: Long, status: String)

    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun updateSessionEndTime(id: Long, endedAt: Long)

    @Query("UPDATE sessions SET rawFilePath = :rawFilePath WHERE id = :id")
    suspend fun updateRawFilePath(id: Long, rawFilePath: String)

    @Query("UPDATE sessions SET startLineLat1 = :lat1, startLineLng1 = :lng1, startLineLat2 = :lat2, startLineLng2 = :lng2 WHERE id = :sessionId")
    suspend fun updateStartLine(sessionId: Long, lat1: Double, lng1: Double, lat2: Double, lng2: Double)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: Long)

    @Query("UPDATE sessions SET trackName = :trackName WHERE id = :sessionId")
    suspend fun updateTrackName(sessionId: Long, trackName: String)
}
