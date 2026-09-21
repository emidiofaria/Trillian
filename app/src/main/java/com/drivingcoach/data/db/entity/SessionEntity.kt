package com.drivingcoach.data.db.entity

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
    val remoteSessionId: String? = null,
    /**
     * The catalogue circuit this session was driven on, if one was selected.
     *
     * Kept so that re-detecting laps later can reapply the same priors the live
     * detection used, and so a session cannot silently be re-analysed against a
     * different circuit than the one it happened on.
     */
    val trackId: String? = null,
    // Start/finish line coordinates (two GPS points defining the line)
    val startLineLat1: Double? = null,
    val startLineLng1: Double? = null,
    val startLineLat2: Double? = null,
    val startLineLng2: Double? = null
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
