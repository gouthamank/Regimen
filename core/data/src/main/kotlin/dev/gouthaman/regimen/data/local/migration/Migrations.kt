package dev.gouthaman.regimen.data.local.migration

import androidx.room.migration.Migration

/**
 * v4 -> v5: drops `workouts.preEditEndTime` (no longer touched by session editing).
 * Rebuilds the table since `DROP COLUMN` isn't reliable across Android's SQLite versions.
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
 * v5 -> v6: adds an explicit [dev.gouthaman.regimen.domain.model.WorkoutStatus] column plus
 * rest-countdown columns, replacing inference of session state from `pausedAt`/`endTime`
 * nullability. Rebuilds the table to backfill `workoutStatus` from existing rows in one pass.
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
 * Plain `ADD COLUMN` - reliable across Android's SQLite versions, unlike drop/rename.
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
 * v8 -> v9: swaps every entity's autoincrement `Long` primary key (and FK columns pointing at one)
 * for a client-generated UUID `String` - offline devices can't independently generate the same
 * next autoincrement id, but can't collide on UUIDs either.
 *
 * Rebuilds every table in FK dependency order, remapping each old id to a new UUID as it goes.
 * `workouts.restWorkoutExerciseId` is the one forward reference, so it's copied as `NULL` and
 * patched in after `workout_exercises` exists. Built-in `exercises`/`measurement_types` rows get
 * [BuiltInData.stableId]'s deterministic id instead of a random one, so upgrades match fresh installs.
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

/**
 * v9 -> v10: adds `isDirty` (sync push filters on it) and `lastModifiedAt` to every synced entity.
 * Existing rows backfill `isDirty = 1` so pre-existing history is eligible on first sync, and
 * `lastModifiedAt` to this migration's run time uniformly rather than each row's real timestamp.
 */
val MIGRATION_9_10 = Migration(9, 10) { db ->
    val now = System.currentTimeMillis()
    for (table in listOf(
        "exercises", "routines", "routine_exercises", "workouts", "workout_exercises",
        "set_entries", "cardio_entries", "measurement_types", "body_metrics",
    )) {
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `isDirty` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `lastModifiedAt` INTEGER NOT NULL DEFAULT $now")
    }
}

/**
 * v10 -> v11: adds `sync_tombstones`, a pending-deletion record for the sync push job to read -
 * Room's cascade deletes leave no other trace a row existed, so without this the push job can't
 * know a locally-deleted row's Firestore copy needs deleting too.
 */
val MIGRATION_10_11 = Migration(10, 11) { db ->
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_tombstones` (
            `entityType` TEXT NOT NULL,
            `entityId` TEXT NOT NULL,
            `parentId` TEXT,
            `grandparentId` TEXT,
            `deletedAt` INTEGER NOT NULL,
            PRIMARY KEY(`entityType`, `entityId`)
        )
        """.trimIndent()
    )
}

/**
 * v11 -> v12: adds `workout_exercises.notes` (per-exercise note, alongside the workout-wide
 * note). Plain `ADD COLUMN`, same reasoning as MIGRATION_6_7.
 */
val MIGRATION_11_12 = Migration(11, 12) { db ->
    db.execSQL("ALTER TABLE `workout_exercises` ADD COLUMN `notes` TEXT")
}

/**
 * v12 -> v13: adds `workout_biometrics`, biometric data associated with each workout
 * Pulled in from Health Connect, not populated by the user or the app
 */
val MIGRATION_12_13 = Migration(12, 13) { db ->
    val now = System.currentTimeMillis()
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `workout_biometrics` (
            `id` TEXT PRIMARY KEY NOT NULL,
            `workoutId` TEXT NOT NULL,
            `avgBpm` INTEGER,
            `maxBpm` INTEGER,
            `activeCaloriesKcal` REAL,
            `sourcePackageName` TEXT,
            `fetchedAt` INTEGER NOT NULL,
            `isDirty` INTEGER NOT NULL DEFAULT 1,
            `lastModifiedAt` INTEGER NOT NULL DEFAULT $now,
            FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        ) 
        """.trimIndent()
    )
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_biometrics_workoutId` ON `workout_biometrics` (`workoutId`)")
}

/** Every migration, oldest first - the one place a newly added migration needs to be registered. */
val ALL_MIGRATIONS: List<Migration> = listOf(
    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
)

/** Every migration needed to reach the current version starting from [version] - lets a test
 * validate a fully-migrated file without hardcoding which later migrations to add on top. */
fun migrationsFrom(version: Int): Array<Migration> =
    ALL_MIGRATIONS.filter { it.startVersion >= version }.toTypedArray()
