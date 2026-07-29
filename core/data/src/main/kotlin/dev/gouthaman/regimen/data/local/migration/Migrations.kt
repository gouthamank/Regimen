package dev.gouthaman.regimen.data.local.migration

import androidx.room.migration.Migration

/**
 * v4 -> v5: drops `workouts.preEditEndTime` (unused now - editing a past session no longer
 * touches `endTime`/`preEditEndTime`, see :feature:history's EditWorkoutViewModel).
 * `ALTER TABLE ... DROP COLUMN` isn't reliable across Android's SQLite versions, so this
 * rebuilds the table instead: new shape, copy surviving columns, drop old, rename.
 */
val MIGRATION_4_5 = Migration(4, 5) { db ->
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `workouts_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `startTime` INTEGER NOT NULL,
            `endTime` INTEGER,
            `note` TEXT,
            `routineId` INTEGER,
            `pausedAt` INTEGER,
            `accumulatedPausedMs` INTEGER NOT NULL,
            FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
        )
        """.trimIndent()
    )
    db.execSQL(
        """
        INSERT INTO `workouts_new`
            (`id`, `startTime`, `endTime`, `note`, `routineId`, `pausedAt`, `accumulatedPausedMs`)
        SELECT `id`, `startTime`, `endTime`, `note`, `routineId`, `pausedAt`, `accumulatedPausedMs`
        FROM `workouts`
        """.trimIndent()
    )
    db.execSQL("DROP TABLE `workouts`")
    db.execSQL("ALTER TABLE `workouts_new` RENAME TO `workouts`")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_routineId` ON `workouts` (`routineId`)")
}

/**
 * v5 -> v6: adds an explicit [dev.gouthaman.regimen.domain.model.WorkoutStatus] column plus the
 * rest-countdown columns (`restTimeEndAt`/`restTotalSec`/`restWorkoutExerciseId`), replacing ad hoc
 * inference of session state from `pausedAt`/`endTime` nullability. Rebuilds the table (same
 * reasoning as MIGRATION_4_5 - adding columns alone would be a plain ALTER TABLE, but this also
 * needs to backfill `workoutStatus` from existing rows in one pass).
 */
val MIGRATION_5_6 = Migration(5, 6) { db ->
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `workouts_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `startTime` INTEGER NOT NULL,
            `endTime` INTEGER,
            `note` TEXT,
            `routineId` INTEGER,
            `workoutStatus` TEXT NOT NULL,
            `pausedAt` INTEGER,
            `accumulatedPausedMs` INTEGER NOT NULL,
            `restTimeEndAt` INTEGER,
            `restTotalSec` INTEGER,
            `restWorkoutExerciseId` INTEGER,
            FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
        )
        """.trimIndent()
    )
    db.execSQL(
        """
        INSERT INTO `workouts_new`
            (`id`, `startTime`, `endTime`, `note`, `routineId`, `workoutStatus`, `pausedAt`, `accumulatedPausedMs`, `restTimeEndAt`, `restTotalSec`, `restWorkoutExerciseId`)
        SELECT `id`, `startTime`, `endTime`, `note`, `routineId`,
            CASE
                WHEN `endTime` IS NOT NULL THEN 'COMPLETE'
                WHEN `pausedAt` IS NOT NULL THEN 'PAUSED'
                ELSE 'IN_PROGRESS'
            END,
            `pausedAt`, `accumulatedPausedMs`, NULL, NULL, NULL
        FROM `workouts`
        """.trimIndent()
    )
    db.execSQL("DROP TABLE `workouts`")
    db.execSQL("ALTER TABLE `workouts_new` RENAME TO `workouts`")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_routineId` ON `workouts` (`routineId`)")
}

/**
 * v6 -> v7: adds `workout_exercises.isDone` (per-exercise completion, alongside `isSkipped`).
 * A plain `ADD COLUMN` suffices here - unlike MIGRATION_4_5/5_6, nothing on this table is being
 * renamed or dropped, and `ADD COLUMN` (unlike `DROP`/`RENAME COLUMN`) is reliable across
 * Android's bundled SQLite versions.
 */
val MIGRATION_6_7 = Migration(6, 7) { db ->
    db.execSQL("ALTER TABLE `workout_exercises` ADD COLUMN `isDone` INTEGER NOT NULL DEFAULT 0")
}

/**
 * v7 -> v8: adds `workouts.endReason` (nullable, [dev.gouthaman.regimen.domain.model.WorkoutEndReason]
 * as string) - distinguishes a manual Finish from an auto-end triggered by the max-workout-time
 * safety net. Plain `ADD COLUMN`, same reasoning as MIGRATION_6_7.
 */
val MIGRATION_7_8 = Migration(7, 8) { db ->
    db.execSQL("ALTER TABLE `workouts` ADD COLUMN `endReason` TEXT")
}

/**
 * v8 -> v9: swaps every entity's autoincrement `Long` primary key (and every FK column pointing at
 * one) for a client-generated UUID `String`. Two offline devices can independently generate the
 * same next autoincrement id; they can't independently generate the same UUID.
 *
 * Rebuilds every table (new UUID-keyed shape, not a plain `ALTER TABLE`) and walks existing rows
 * in FK dependency order - parents first (`exercises`/`routines`/`measurement_types`), then their
 * children (`routine_exercises`/`workouts`/`body_metrics`), then `workout_exercises`, then
 * `set_entries`/`cardio_entries` - generating a UUID per old row and remapping every FK column via
 * an old-`Long`-id -> new-UUID map for that entity, built while that entity's own table is copied.
 *
 * `workouts.restWorkoutExerciseId` is the one forward reference (a workout pointing at one of its
 * own not-yet-migrated `workout_exercises` rows) - it's copied over as `NULL` in the initial
 * `workouts` pass and patched in after `workout_exercises` (and its id map) exist.
 *
 * Built-in (seed) rows are the one exception to "fresh random UUID per row": `exercises` rows
 * with `isCustom = 0` and the `measurement_types` row with `isBuiltIn = 1` are remapped to
 * [BuiltInData.stableId]'s deterministic, name-derived id instead of a random one, so an
 * upgrading install's "Bench Press" (etc.) ends up with the exact same id a fresh install's seed
 * would assign it.
 */
val MIGRATION_8_9 = Migration(8, 9) { db ->
    val exerciseIdMap = migrateExercises8To9(db)
    val measurementTypeIdMap = migrateMeasurementTypes8To9(db)
    val routineIdMap = migrateRoutines8To9(db)
    migrateRoutineExercises8To9(db, routineIdMap, exerciseIdMap)
    val (workoutIdMap, pendingRest) = migrateWorkouts8To9(db, routineIdMap)
    migrateBodyMetrics8To9(db, measurementTypeIdMap)
    val workoutExerciseIdMap = migrateWorkoutExercises8To9(db, workoutIdMap, exerciseIdMap)
    patchWorkoutsRestWorkoutExerciseId8To9(db, pendingRest, workoutExerciseIdMap)
    migrateSetEntries8To9(db, workoutExerciseIdMap)
    migrateCardioEntries8To9(db, workoutExerciseIdMap)
}
