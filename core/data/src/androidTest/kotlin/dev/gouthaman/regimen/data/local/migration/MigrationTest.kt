package dev.gouthaman.regimen.data.local.migration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gouthaman.regimen.data.local.RegimenDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RegimenDatabase::class.java,
    )

    @Test
    fun migrate5To6_backfillsWorkoutStatusFromEndTimeAndPausedAt() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                "INSERT INTO workouts (id, startTime, endTime, note, routineId, pausedAt, accumulatedPausedMs) " +
                        "VALUES (1, 1000, 2000, NULL, NULL, NULL, 0)",
            )
            execSQL(
                "INSERT INTO workouts (id, startTime, endTime, note, routineId, pausedAt, accumulatedPausedMs) " +
                        "VALUES (2, 1000, NULL, NULL, NULL, 500, 0)",
            )
            execSQL(
                "INSERT INTO workouts (id, startTime, endTime, note, routineId, pausedAt, accumulatedPausedMs) " +
                        "VALUES (3, 1000, NULL, NULL, NULL, NULL, 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        val cursor = migrated.query("SELECT id, workoutStatus FROM workouts ORDER BY id")
        val statusById = mutableMapOf<Long, String>()
        cursor.use {
            while (it.moveToNext()) {
                statusById[it.getLong(0)] = it.getString(1)
            }
        }

        assertEquals("COMPLETE", statusById[1L])
        assertEquals("PAUSED", statusById[2L])
        assertEquals("IN_PROGRESS", statusById[3L])

        migrated.close()
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RegimenDatabase::class.java,
            TEST_DB,
        ).addMigrations(MIGRATION_5_6, MIGRATION_6_7).build()
        helper.closeWhenFinished(db)
        db.openHelper.writableDatabase
    }

    @Test
    fun migrate6To7_addsIsDoneDefaultingToFalse() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO exercises (id, name, type, muscleGroup, equipment, isCustom) " +
                        "VALUES (1, 'Bench Press', 'STRENGTH', 'CHEST', 'BARBELL', 0)",
            )
            execSQL(
                "INSERT INTO workouts (id, startTime, endTime, note, routineId, workoutStatus, pausedAt, accumulatedPausedMs, restTimeEndAt, restTotalSec, restWorkoutExerciseId) " +
                        "VALUES (1, 1000, NULL, NULL, NULL, 'IN_PROGRESS', NULL, 0, NULL, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO workout_exercises (id, workoutId, exerciseId, position, isSkipped, supersetGroupId) " +
                        "VALUES (1, 1, 1, 0, 0, NULL)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)

        val cursor = migrated.query("SELECT isDone FROM workout_exercises WHERE id = 1")
        cursor.use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }

        migrated.close()
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RegimenDatabase::class.java,
            TEST_DB,
        ).addMigrations(MIGRATION_6_7).build()
        helper.closeWhenFinished(db)
        db.openHelper.writableDatabase
    }
}
