package com.drivingcoach.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.drivingcoach.data.db.dao.CoachingInsightDao
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.dao.SessionPreferenceDao
import com.drivingcoach.data.db.dao.TrackDao
import com.drivingcoach.data.db.entity.CoachingInsightEntity
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.data.db.entity.SessionPreferenceEntity
import com.drivingcoach.data.db.entity.TrackEntity

@Database(
    entities = [
        SessionEntity::class,
        LapEntity::class,
        CoachingInsightEntity::class,
        SessionPreferenceEntity::class,
        TrackEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class DrivingCoachDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun lapDao(): LapDao
    abstract fun coachingInsightDao(): CoachingInsightDao
    abstract fun sessionPreferenceDao(): SessionPreferenceDao
    abstract fun trackDao(): TrackDao

    companion object {
        const val DATABASE_NAME = "driving_coach.db"

        /**
         * Migration from version 1 to 2: Add start/finish line coordinates.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Schema 1 was exported with these columns already present, so a
                // plain ADD COLUMN aborts with "duplicate column name". The old
                // destructive-migration fallback hid this by wiping the database;
                // that fallback is gone, so the migration must be idempotent.
                database.addColumnIfMissing("sessions", "startLineLat1", "REAL")
                database.addColumnIfMissing("sessions", "startLineLng1", "REAL")
                database.addColumnIfMissing("sessions", "startLineLat2", "REAL")
                database.addColumnIfMissing("sessions", "startLineLng2", "REAL")
            }
        }

        private fun SupportSQLiteDatabase.addColumnIfMissing(
            table: String,
            column: String,
            type: String
        ) {
            val exists = query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                    .any { it == column }
            }
            if (!exists) {
                execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $type")
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

        /**
         * Migration from version 4 to 5: the track library.
         * - Create the tracks table for circuits the driver saves
         * - Add trackId to sessions, recording which catalogue circuit a session was
         *   driven on so its priors can be reapplied when laps are re-detected
         *
         * No foreign key from sessions to tracks. A session is a record of something
         * that happened, and deleting a saved circuit must not cascade into deleting
         * the afternoon driven on it; the orphaned id simply stops resolving.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sessions ADD COLUMN trackId TEXT")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS tracks (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        location TEXT,
                        startLineLat1 REAL NOT NULL,
                        startLineLng1 REAL NOT NULL,
                        startLineLat2 REAL NOT NULL,
                        startLineLng2 REAL NOT NULL,
                        startLineSource TEXT NOT NULL,
                        startLineRecordedAt TEXT,
                        travelHeadingDeg REAL,
                        lengthM INTEGER,
                        cornerCount INTEGER,
                        fastestLapMs INTEGER,
                        slowestLapMs INTEGER,
                        centrelineSource TEXT,
                        centrelineMethod TEXT,
                        centrelineSurveyedAt TEXT,
                        centrelineSurveyedBy TEXT,
                        centrelineJson TEXT,
                        createdAt INTEGER NOT NULL,
                        lastUsedAt INTEGER
                    )
                """.trimIndent())
            }
        }
    }
}
