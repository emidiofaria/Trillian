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
}
