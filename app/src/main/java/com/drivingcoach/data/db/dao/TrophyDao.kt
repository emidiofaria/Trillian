package com.drivingcoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drivingcoach.data.db.entity.TrophyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrophyDao {

    /** Ignores a trophy the session already holds, which keeps awarding idempotent. */
    /** Row ids in input order; a trophy the session already holds comes back as -1. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(trophies: List<TrophyEntity>): List<Long>

    @Query("SELECT * FROM trophies ORDER BY awardedAt ASC, id ASC")
    fun observeAll(): Flow<List<TrophyEntity>>

    @Query("SELECT * FROM trophies ORDER BY awardedAt ASC, id ASC")
    suspend fun getAll(): List<TrophyEntity>

    @Query("SELECT * FROM trophies WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getForSession(sessionId: Long): List<TrophyEntity>
}
