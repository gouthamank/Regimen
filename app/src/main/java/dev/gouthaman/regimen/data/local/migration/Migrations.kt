package dev.gouthaman.regimen.data.local.migration

import androidx.room.migration.Migration

/**
 * v4 -> v5: drops `workouts.preEditEndTime` (removed from [dev.gouthaman.regimen.data.local.entity.Workout] —
 * editing a past session no longer touches `endTime`/`preEditEndTime` at all, see
 * ActiveWorkoutViewModel.isEditingPastSession). SQLite's `ALTER TABLE ... DROP COLUMN` isn't
 * reliably available across the SQLite versions Android ships, so this rebuilds the table:
 * create the new shape, copy the surviving columns over, drop the old table, rename.
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
