package com.drivingcoach.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.drivingcoach.data.db.dao.CoachingInsightDao
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.entity.CoachingInsightEntity
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        LapEntity::class,
        CoachingInsightEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class DrivingCoachDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun lapDao(): LapDao
    abstract fun coachingInsightDao(): CoachingInsightDao

    companion object {
        const val DATABASE_NAME = "driving_coach.db"

        /**
         * Migration from version 1 to 2: Add start/finish line coordinates.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sessions ADD COLUMN startLineLat1 REAL")
                database.execSQL("ALTER TABLE sessions ADD COLUMN startLineLng1 REAL")
                database.execSQL("ALTER TABLE sessions ADD COLUMN startLineLat2 REAL")
                database.execSQL("ALTER TABLE sessions ADD COLUMN startLineLng2 REAL")
            }
        }
    }
}
