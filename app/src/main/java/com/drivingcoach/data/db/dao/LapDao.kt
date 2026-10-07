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

    /**
     * Replaces one lap's sector times.
     *
     * Used to correct laps measured before the distance ruler was anchored at the
     * start/finish line (OC-31). Deliberately narrow: the lap's own start, end and
     * duration were never wrong, and rewriting the whole row would risk reverting a
     * field some other part of the app had since updated.
     */
    @Query("UPDATE laps SET sector1Ms = :sector1Ms, sector2Ms = :sector2Ms, sector3Ms = :sector3Ms WHERE id = :lapId")
    suspend fun updateSectors(lapId: Long, sector1Ms: Long, sector2Ms: Long, sector3Ms: Long)
}
