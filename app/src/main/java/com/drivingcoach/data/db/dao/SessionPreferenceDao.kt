package com.drivingcoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drivingcoach.data.db.entity.SessionPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionPreferenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(preference: SessionPreferenceEntity): Long

    @Query("SELECT * FROM session_preferences WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getPreference(sessionId: Long): SessionPreferenceEntity?

    @Query("SELECT * FROM session_preferences WHERE sessionId = :sessionId LIMIT 1")
    fun getPreferenceFlow(sessionId: Long): Flow<SessionPreferenceEntity?>

    @Query("DELETE FROM session_preferences WHERE sessionId = :sessionId")
    suspend fun deletePreference(sessionId: Long)
}
