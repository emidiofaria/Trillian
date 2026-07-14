package com.drivingcoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.drivingcoach.data.db.entity.LapEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LapDao {

    @Insert
    suspend fun insertLaps(laps: List<LapEntity>)

    @Insert
    suspend fun insertLap(lap: LapEntity): Long

    @Query("SELECT * FROM laps WHERE id = :lapId")
    suspend fun getLapById(lapId: Long): LapEntity?

    @Query("SELECT * FROM laps WHERE sessionId = :sessionId ORDER BY lapNumber ASC")
    fun getLapsForSession(sessionId: Long): Flow<List<LapEntity>>

    @Query("SELECT * FROM laps WHERE sessionId = :sessionId AND isBestLap = 1 LIMIT 1")
    fun getBestLap(sessionId: Long): Flow<LapEntity?>

    @Query("UPDATE laps SET isBestLap = 0 WHERE sessionId = :sessionId")
    suspend fun clearBestLap(sessionId: Long)

    @Query("UPDATE laps SET isBestLap = 1 WHERE id = :lapId")
    suspend fun setBestLap(lapId: Long)

    @Query("SELECT * FROM laps WHERE sessionId = :sessionId ORDER BY durationMs ASC LIMIT 1")
    suspend fun getFastestLap(sessionId: Long): LapEntity?
}
