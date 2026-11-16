package com.drivingcoach.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A trophy earned in one session (SRS PG-21).
 *
 * [type] is a [com.drivingcoach.progression.TrophyType] name. At most one row per
 * session and type, so re-awarding a session is a no-op. Rows go with their session.
 */
@Entity(
    tableName = "trophies",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "type"], unique = true)
    ]
)
data class TrophyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val type: String,
    /** Track the trophy was won on, as [com.drivingcoach.progression.TrackKey]. */
    val trackKey: String,
    /** Lap time in ms for Personal Best and Track Master, five-lap stdDev in ms for Consistency, distance in m for Finisher. */
    val value: Long,
    val awardedAt: Long
)
