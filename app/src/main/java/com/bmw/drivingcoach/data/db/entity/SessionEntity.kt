package com.bmw.drivingcoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val trackName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val rawFilePath: String,
    val uploadStatus: String = UploadStatus.PENDING.name,
    val processingStatus: String = ProcessingStatus.PENDING.name,
    val remoteSessionId: String? = null
)

enum class UploadStatus {
    PENDING,
    UPLOADING,
    DONE,
    FAILED
}

enum class ProcessingStatus {
    PENDING,
    UPLOADING,
    DETECTING_LAPS,
    GENERATING_COACHING,
    COMPLETE,
    FAILED
}
