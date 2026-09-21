package com.drivingcoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drivingcoach.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    /** Most recently driven first: the circuit you were at last week is the one you want. */
    @Query("SELECT * FROM tracks ORDER BY lastUsedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY lastUsedAt DESC, createdAt DESC")
    suspend fun getAll(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TrackEntity?

    @Query("UPDATE tracks SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun markUsed(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE tracks SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: String)
}
