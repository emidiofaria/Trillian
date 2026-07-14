package com.drivingcoach.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "laps",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class LapEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val lapNumber: Int,
    val startTs: Long,
    val endTs: Long,
    val durationMs: Long,
    val sector1Ms: Long = 0L,
    val sector2Ms: Long = 0L,
    val sector3Ms: Long = 0L,
    val isBestLap: Boolean = false,
    val isLocalOnly: Boolean = true  // True = detected locally, False = processed by backend
)
