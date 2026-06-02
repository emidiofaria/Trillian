package com.bmw.drivingcoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bmw.drivingcoach.data.db.entity.SessionEntity
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

    @Query("SELECT * FROM sessions WHERE uploadStatus = 'PENDING' OR uploadStatus = 'FAILED'")
    suspend fun getPendingUploadSessions(): List<SessionEntity>
    
    @Query("SELECT * FROM sessions WHERE uploadStatus = 'PENDING' AND startedAt < :beforeTimestamp")
    suspend fun getStaleUploadSessions(beforeTimestamp: Long): List<SessionEntity>

    @Query("UPDATE sessions SET uploadStatus = :status, remoteSessionId = :remoteId WHERE id = :id")
    suspend fun updateUploadStatus(id: Long, status: String, remoteId: String? = null)
    
    @Query("UPDATE sessions SET processingStatus = :status WHERE id = :id")
    suspend fun updateProcessingStatus(id: Long, status: String)

    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun updateSessionEndTime(id: Long, endedAt: Long)
}
