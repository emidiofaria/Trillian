package com.drivingcoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.drivingcoach.data.db.entity.CoachingInsightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachingInsightDao {

    @Insert
    suspend fun insertInsights(insights: List<CoachingInsightEntity>)

    @Insert
    suspend fun insertInsight(insight: CoachingInsightEntity): Long

    @Query("SELECT * FROM coaching_insights WHERE sessionId = :sessionId ORDER BY generatedAt DESC")
    fun getInsightsForSession(sessionId: Long): Flow<List<CoachingInsightEntity>>

    @Query("DELETE FROM coaching_insights WHERE sessionId = :sessionId")
    suspend fun deleteInsightsForSession(sessionId: Long)

    /**
     * Removes only the insights this device generated for itself.
     *
     * Insights that came from the backend or an AI pass are the user's paid-for
     * content and cannot be regenerated locally, so a local recalculation must never
     * take them with it (OC-09, OC-10). That is the whole reason this exists next to
     * [deleteInsightsForSession] rather than replacing it.
     */
    @Query("DELETE FROM coaching_insights WHERE sessionId = :sessionId AND isLocalOnly = 1")
    suspend fun deleteLocalInsightsForSession(sessionId: Long)
}
