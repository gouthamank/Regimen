package dev.gouthaman.regimen.data.local.migration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.seed.BuiltInData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        ).addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build()
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
        ).addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build()
        helper.closeWhenFinished(db)
        db.openHelper.writableDatabase
    }

    @Test
    fun migrate7To8_addsNullableEndReason() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL(
                "INSERT INTO workouts (id, startTime, endTime, note, routineId, workoutStatus, pausedAt, accumulatedPausedMs, restTimeEndAt, restTotalSec, restWorkoutExerciseId) " +
                        "VALUES (1, 1000, 2000, NULL, NULL, 'COMPLETE', NULL, 0, NULL, NULL, NULL)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        val cursor = migrated.query("SELECT endReason FROM workouts WHERE id = 1")
        cursor.use {
            it.moveToFirst()
            assertEquals(null, it.getString(0))
        }

        migrated.close()
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RegimenDatabase::class.java,
            TEST_DB,
        ).addMigrations(MIGRATION_7_8, MIGRATION_8_9).build()
        helper.closeWhenFinished(db)
        db.openHelper.writableDatabase
    }

    /**
     * Exercises every FK remap path in one pass: a built-in exercise (must land on
     * [BuiltInData.stableId], not a random UUID), a custom exercise, a built-in + custom
     * measurement type, a routine + routine_exercise, a workout (with a routineId FK and a
     * `restWorkoutExerciseId` forward-reference to one of its own workout_exercises rows) plus
     * its workout_exercise/set_entry/cardio_entry, and a body_metric.
     */
    @Test
    fun migrate8To9_remapsEveryIdToAUuidStringPreservingRelationships() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                "INSERT INTO exercises (id, name, type, muscleGroup, equipment, isCustom) " +
                        "VALUES (1, 'Bench Press', 'STRENGTH', 'CHEST', 'BARBELL', 0)",
            )
            execSQL(
                "INSERT INTO exercises (id, name, type, muscleGroup, equipment, isCustom) " +
                        "VALUES (2, 'My Custom Move', 'STRENGTH', 'CHEST', 'BARBELL', 1)",
            )
            execSQL(
                "INSERT INTO measurement_types (id, name, unit, isBuiltIn) " +
                        "VALUES (1, 'Bodyweight', 'kg', 1)",
            )
            execSQL(
                "INSERT INTO measurement_types (id, name, unit, isBuiltIn) " +
                        "VALUES (2, 'Waist', 'cm', 0)",
            )
            execSQL(
                "INSERT INTO body_metrics (id, measurementTypeId, date, value) " +
                        "VALUES (1, 2, 1000, 80.0)",
            )
            execSQL("INSERT INTO routines (id, name, position) VALUES (1, 'Push Day', 0)")
            execSQL(
                "INSERT INTO routine_exercises (id, routineId, exerciseId, position, targetSets, targetReps, targetRestSec, supersetGroupId) " +
                        "VALUES (1, 1, 1, 0, 3, 8, 90, NULL)",
            )
            execSQL(
                "INSERT INTO workouts (id, startTime, endTime, note, routineId, workoutStatus, endReason, pausedAt, accumulatedPausedMs, restTimeEndAt, restTotalSec, restWorkoutExerciseId) " +
                        "VALUES (1, 1000, NULL, NULL, 1, 'IN_REST_TIME', NULL, NULL, 0, 5000, 90, 1)",
            )
            execSQL(
                "INSERT INTO workout_exercises (id, workoutId, exerciseId, position, isSkipped, isDone, supersetGroupId) " +
                        "VALUES (1, 1, 1, 0, 0, 0, NULL)",
            )
            execSQL(
                "INSERT INTO set_entries (id, workoutExerciseId, setNumber, weightKg, reps, isComplete) " +
                        "VALUES (1, 1, 1, 100.0, 5, 1)",
            )
            execSQL(
                "INSERT INTO cardio_entries (id, workoutExerciseId, durationSec, distanceMeters) " +
                        "VALUES (1, 1, 600, 2000.0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9)

        val benchPressId = BuiltInData.stableId("exercise:Bench Press")
        val bodyweightId = BuiltInData.stableId("measurementType:Bodyweight")

        migrated.query("SELECT id FROM exercises WHERE name = 'Bench Press'").use {
            it.moveToFirst()
            assertEquals(benchPressId, it.getString(0))
        }
        val customExerciseId =
            migrated.query("SELECT id FROM exercises WHERE name = 'My Custom Move'")
                .use { it.moveToFirst(); it.getString(0) }
        assertTrue(customExerciseId.isNotEmpty() && customExerciseId != benchPressId)

        migrated.query("SELECT id FROM measurement_types WHERE name = 'Bodyweight'").use {
            it.moveToFirst()
            assertEquals(bodyweightId, it.getString(0))
        }

        val routineId = migrated.query("SELECT id FROM routines WHERE name = 'Push Day'")
            .use { it.moveToFirst(); it.getString(0) }

        migrated.query(
            "SELECT routineId, exerciseId FROM routine_exercises",
        ).use {
            it.moveToFirst()
            assertEquals(routineId, it.getString(0))
            assertEquals(benchPressId, it.getString(1))
        }

        val workoutId = migrated.query("SELECT id FROM workouts").use {
            it.moveToFirst(); it.getString(0)
        }
        val workoutExerciseId = migrated.query("SELECT id FROM workout_exercises").use {
            it.moveToFirst(); it.getString(0)
        }

        migrated.query(
            "SELECT routineId, restWorkoutExerciseId FROM workouts WHERE id = ?",
            arrayOf(workoutId),
        ).use {
            it.moveToFirst()
            assertEquals(routineId, it.getString(0))
            // The forward-reference (old value 1) resolves to this same workout's own
            // workout_exercises row once the patch pass runs.
            assertEquals(workoutExerciseId, it.getString(1))
        }

        migrated.query(
            "SELECT workoutId, exerciseId FROM workout_exercises WHERE id = ?",
            arrayOf(workoutExerciseId),
        ).use {
            it.moveToFirst()
            assertEquals(workoutId, it.getString(0))
            assertEquals(benchPressId, it.getString(1))
        }

        migrated.query(
            "SELECT workoutExerciseId, weightKg FROM set_entries",
        ).use {
            it.moveToFirst()
            assertEquals(workoutExerciseId, it.getString(0))
            assertEquals(100.0, it.getDouble(1), 0.0)
        }

        migrated.query(
            "SELECT workoutExerciseId, distanceMeters FROM cardio_entries",
        ).use {
            it.moveToFirst()
            assertEquals(workoutExerciseId, it.getString(0))
            assertEquals(2000.0, it.getDouble(1), 0.0)
        }

        migrated.query("SELECT measurementTypeId, value FROM body_metrics").use {
            it.moveToFirst()
            assertTrue(it.getString(0).isNotEmpty() && it.getString(0) != bodyweightId)
            assertEquals(80.0, it.getDouble(1), 0.0)
        }

        migrated.close()
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RegimenDatabase::class.java,
            TEST_DB,
        ).build()
        helper.closeWhenFinished(db)
        db.openHelper.writableDatabase
    }
}
