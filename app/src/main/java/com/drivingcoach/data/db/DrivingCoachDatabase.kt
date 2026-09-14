package com.drivingcoach.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.drivingcoach.data.db.dao.CoachingInsightDao
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.dao.SessionPreferenceDao
import com.drivingcoach.data.db.entity.CoachingInsightEntity
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.data.db.entity.SessionPreferenceEntity

@Database(
    entities = [
        SessionEntity::class,
        LapEntity::class,
        CoachingInsightEntity::class,
        SessionPreferenceEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class DrivingCoachDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun lapDao(): LapDao
    abstract fun coachingInsightDao(): CoachingInsightDao
    abstract fun sessionPreferenceDao(): SessionPreferenceDao

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

        /**
         * Migration from version 2 to 3: Add isLocalOnly flag to laps table.
         * This flag indicates whether a lap was detected locally (true) or 
         * processed by the backend with full sector timing (false).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE laps ADD COLUMN isLocalOnly INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * Migration from version 3 to 4: Add offline coaching support.
         * - Add isLocalOnly flag to coaching_insights table
         * - Create session_preferences table for user coaching preference
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add isLocalOnly to coaching_insights
                database.execSQL("ALTER TABLE coaching_insights ADD COLUMN isLocalOnly INTEGER NOT NULL DEFAULT 0")
                
                // Create session_preferences table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS session_preferences (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        coachingPreference TEXT NOT NULL DEFAULT 'LOCAL',
                        FOREIGN KEY (sessionId) REFERENCES sessions(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_session_preferences_sessionId ON session_preferences(sessionId)")
            }
        }
    }
}
