package com.bmw.drivingcoach.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao
import com.bmw.drivingcoach.data.db.dao.LapDao
import com.bmw.drivingcoach.data.db.dao.SessionDao
import com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity
import com.bmw.drivingcoach.data.db.entity.LapEntity
import com.bmw.drivingcoach.data.db.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        LapEntity::class,
        CoachingInsightEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class BMWDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun lapDao(): LapDao
    abstract fun coachingInsightDao(): CoachingInsightDao

    companion object {
        const val DATABASE_NAME = "bmw_driving_coach.db"
    }
}
