package com.drivingcoach.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores user preferences for how coaching insights are displayed per session.
 * 
 * When AI coaching arrives for a session that already has local insights,
 * the user chooses whether to keep local or view AI. This entity persists
 * that choice so it's remembered when returning to the session.
 */
@Entity(
    tableName = "session_preferences",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId", unique = true)]
)
data class SessionPreferenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    /**
     * User's coaching preference for this session.
     * @see CoachingPreference
     */
    val coachingPreference: String = CoachingPreference.LOCAL.name
)

/**
 * User's preference for which coaching insights to display.
 */
enum class CoachingPreference {
    /** Show locally-generated offline insights */
    LOCAL,
    /** Show AI-generated backend insights */
    AI
}
