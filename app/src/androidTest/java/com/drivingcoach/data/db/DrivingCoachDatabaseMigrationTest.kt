package com.drivingcoach.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The track library arrives in a build where the destructive migration fallback was
 * removed, which means a broken migration no longer fails quietly by wiping the
 * database - it crashes on launch. That is the right behaviour, and this test is
 * what makes it safe to choose.
 *
 * It also checks the thing the schema alone cannot: that a driver upgrading from
 * version 4 still has their sessions afterwards.
 */
@RunWith(AndroidJUnit4::class)
class DrivingCoachDatabaseMigrationTest {

    private val databaseName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DrivingCoachDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate4To5_keepsExistingSessions() {
        helper.createDatabase(databaseName, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO sessions (
                    id, userId, trackName, startedAt, endedAt, rawFilePath,
                    uploadStatus, processingStatus, remoteSessionId,
                    startLineLat1, startLineLng1, startLineLat2, startLineLng2
                ) VALUES (
                    1, 'local', 'Baltar 2', 1000, 2000, '/tmp/telemetry.jsonl',
                    'PENDING', 'COMPLETE', NULL,
                    41.187838, -8.395666, 41.187770, -8.395761
                )
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(
            databaseName, 5, true, DrivingCoachDatabase.MIGRATION_4_5
        )

        db.query("SELECT trackName, trackId, startLineLat1 FROM sessions WHERE id = 1").use { cursor ->
            assertTrue("the session recorded before the upgrade should still be there", cursor.moveToFirst())
            assertEquals("Baltar 2", cursor.getString(0))
            assertTrue("a session driven before track selection existed has no circuit", cursor.isNull(1))
            assertEquals(41.187838, cursor.getDouble(2), 0.000001)
        }
    }

    @Test
    fun migrate4To5_createsAnEmptyTracksTable() {
        helper.createDatabase(databaseName, 4).close()

        val db = helper.runMigrationsAndValidate(
            databaseName, 5, true, DrivingCoachDatabase.MIGRATION_4_5
        )

        db.query("SELECT COUNT(*) FROM tracks").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "bundled circuits are read from assets, so the table starts empty",
                0, cursor.getInt(0)
            )
        }
    }

    /**
     * The whole chain matters as much as the last link: an install from a much older
     * build has to arrive at version 5 too, and there is no longer a fallback to
     * catch it if it does not.
     */
    @Test
    fun migrateAllTheWayFrom1() {
        helper.createDatabase(databaseName, 1).close()

        val db = helper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            DrivingCoachDatabase.MIGRATION_1_2,
            DrivingCoachDatabase.MIGRATION_2_3,
            DrivingCoachDatabase.MIGRATION_3_4,
            DrivingCoachDatabase.MIGRATION_4_5
        )

        db.query("SELECT COUNT(*) FROM tracks").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
    }
}
