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
