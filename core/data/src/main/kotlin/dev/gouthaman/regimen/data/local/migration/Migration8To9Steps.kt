package dev.gouthaman.regimen.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import dev.gouthaman.regimen.data.local.seed.BuiltInData
import java.util.UUID

/** Per-table steps for [MIGRATION_8_9] (the `Long` id -> UUID `String` migration). Function names
 * are suffixed `8To9` so a future migration that also needs to touch (say) `exercises` again
 * doesn't collide with these. */

internal fun migrateExercises8To9(db: SupportSQLiteDatabase): Map<Long, String> {
    db.execSQL(
        """
        CREATE TABLE `exercises_new` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `name` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `muscleGroup` TEXT NOT NULL,
            `equipment` TEXT NOT NULL,
            `isCustom` INTEGER NOT NULL
        )
        """.trimIndent()
    )
    val idMap = HashMap<Long, String>()
    val insert = db.compileStatement(
        "INSERT INTO `exercises_new` (`id`, `name`, `type`, `muscleGroup`, `equipment`, `isCustom`) " +
                "VALUES (?, ?, ?, ?, ?, ?)"
    )
    db.query("SELECT `id`, `name`, `type`, `muscleGroup`, `equipment`, `isCustom` FROM `exercises`")
        .use { c ->
            while (c.moveToNext()) {
                val oldId = c.getLong(0)
                val name = c.getString(1)
                val isCustom = c.getInt(5)
                val newId = if (isCustom == 0) {
                    BuiltInData.stableId("exercise:$name")
                } else {
                    UUID.randomUUID().toString()
                }
                idMap[oldId] = newId
                insert.bindString(1, newId)
                insert.bindString(2, name)
                insert.bindString(3, c.getString(2))
                insert.bindString(4, c.getString(3))
                insert.bindString(5, c.getString(4))
                insert.bindLong(6, isCustom.toLong())
                insert.executeInsert()
                insert.clearBindings()
            }
        }
    db.execSQL("DROP TABLE `exercises`")
    db.execSQL("ALTER TABLE `exercises_new` RENAME TO `exercises`")
    return idMap
}

internal fun migrateMeasurementTypes8To9(db: SupportSQLiteDatabase): Map<Long, String> {
    db.execSQL(
        """
        CREATE TABLE `measurement_types_new` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `name` TEXT NOT NULL,
            `unit` TEXT NOT NULL,
            `isBuiltIn` INTEGER NOT NULL
        )
        """.trimIndent()
    )
    val idMap = HashMap<Long, String>()
    val insert = db.compileStatement(
        "INSERT INTO `measurement_types_new` (`id`, `name`, `unit`, `isBuiltIn`) VALUES (?, ?, ?, ?)"
    )
    db.query("SELECT `id`, `name`, `unit`, `isBuiltIn` FROM `measurement_types`").use { c ->
        while (c.moveToNext()) {
            val oldId = c.getLong(0)
            val name = c.getString(1)
            val isBuiltIn = c.getInt(3)
            val newId = if (isBuiltIn != 0) {
                BuiltInData.stableId("measurementType:$name")
            } else {
                UUID.randomUUID().toString()
            }
            idMap[oldId] = newId
            insert.bindString(1, newId)
            insert.bindString(2, name)
            insert.bindString(3, c.getString(2))
            insert.bindLong(4, isBuiltIn.toLong())
            insert.executeInsert()
            insert.clearBindings()
        }
    }
    db.execSQL("DROP TABLE `measurement_types`")
    db.execSQL("ALTER TABLE `measurement_types_new` RENAME TO `measurement_types`")
    return idMap
}

internal fun migrateRoutines8To9(db: SupportSQLiteDatabase): Map<Long, String> {
    db.execSQL(
        """
        CREATE TABLE `routines_new` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `name` TEXT NOT NULL,
            `position` INTEGER NOT NULL
        )
        """.trimIndent()
    )
    val idMap = HashMap<Long, String>()
    val insert =
        db.compileStatement("INSERT INTO `routines_new` (`id`, `name`, `position`) VALUES (?, ?, ?)")
    db.query("SELECT `id`, `name`, `position` FROM `routines`").use { c ->
        while (c.moveToNext()) {
            val oldId = c.getLong(0)
            val newId = UUID.randomUUID().toString()
            idMap[oldId] = newId
            insert.bindString(1, newId)
            insert.bindString(2, c.getString(1))
            insert.bindLong(3, c.getLong(2))
            insert.executeInsert()
            insert.clearBindings()
        }
    }
    db.execSQL("DROP TABLE `routines`")
    db.execSQL("ALTER TABLE `routines_new` RENAME TO `routines`")
    return idMap
}

/** [supersetGroupId] is reserved for unimplemented v2 superset grouping and is always `NULL` in
 * every existing row (no code path sets it) - copied through as `NULL` rather than remapped. */
internal fun migrateRoutineExercises8To9(
    db: SupportSQLiteDatabase,
    routineIdMap: Map<Long, String>,
    exerciseIdMap: Map<Long, String>,
) {
    db.execSQL(
        """
        CREATE TABLE `routine_exercises_new` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `routineId` TEXT NOT NULL,
            `exerciseId` TEXT NOT NULL,
            `position` INTEGER NOT NULL,
            `targetSets` INTEGER NOT NULL,
            `targetReps` INTEGER NOT NULL,
            `targetRestSec` INTEGER NOT NULL,
            `supersetGroupId` TEXT,
            FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    val insert = db.compileStatement(
        "INSERT INTO `routine_exercises_new` (`id`, `routineId`, `exerciseId`, `position`, " +
                "`targetSets`, `targetReps`, `targetRestSec`, `supersetGroupId`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NULL)"
    )
    db.query(
        "SELECT `id`, `routineId`, `exerciseId`, `position`, `targetSets`, `targetReps`, " +
                "`targetRestSec` FROM `routine_exercises`"
    ).use { c ->
        while (c.moveToNext()) {
            insert.bindString(1, UUID.randomUUID().toString())
            insert.bindString(2, routineIdMap.getValue(c.getLong(1)))
            insert.bindString(3, exerciseIdMap.getValue(c.getLong(2)))
            insert.bindLong(4, c.getLong(3))
            insert.bindLong(5, c.getLong(4))
            insert.bindLong(6, c.getLong(5))
            insert.bindLong(7, c.getLong(6))
            insert.executeInsert()
            insert.clearBindings()
        }
    }
    // The old table's indices share the same names Room assigns the new one (index names are
    // global in SQLite, not per-table) - DROP TABLE must run first to free those names, or a
    // "CREATE INDEX IF NOT EXISTS" here would silently no-op against the old table's index
    // instead of creating one on the new table.
    db.execSQL("DROP TABLE `routine_exercises`")
    db.execSQL("ALTER TABLE `routine_exercises_new` RENAME TO `routine_exercises`")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` " +
                "ON `routine_exercises` (`routineId`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` " +
                "ON `routine_exercises` (`exerciseId`)"
    )
}

/** `restWorkoutExerciseId` is written as `NULL` here regardless of the old value - the old
 * (still-old-`Long`) value is captured into the returned pending map (new workout id -> old
 * `restWorkoutExerciseId`) while this table still has it, and patched in afterwards by
 * [patchWorkoutsRestWorkoutExerciseId8To9] once `workout_exercises`' id map exists (see
 * [MIGRATION_8_9]'s doc comment). */
internal fun migrateWorkouts8To9(
    db: SupportSQLiteDatabase,
    routineIdMap: Map<Long, String>,
): Pair<Map<Long, String>, Map<String, Long>> {
    db.execSQL(
        """
        CREATE TABLE `workouts_new` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `startTime` INTEGER NOT NULL,
            `endTime` INTEGER,
            `note` TEXT,
            `routineId` TEXT,
            `workoutStatus` TEXT NOT NULL,
            `endReason` TEXT,
            `pausedAt` INTEGER,
            `accumulatedPausedMs` INTEGER NOT NULL,
            `restTimeEndAt` INTEGER,
            `restTotalSec` INTEGER,
            `restWorkoutExerciseId` TEXT,
            FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
        )
        """.trimIndent()
    )
    val idMap = HashMap<Long, String>()
    val pendingRest = HashMap<String, Long>()
    val insert = db.compileStatement(
        "INSERT INTO `workouts_new` (`id`, `startTime`, `endTime`, `note`, `routineId`, " +
                "`workoutStatus`, `endReason`, `pausedAt`, `accumulatedPausedMs`, `restTimeEndAt`, " +
                "`restTotalSec`, `restWorkoutExerciseId`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)"
    )
    db.query(
        "SELECT `id`, `startTime`, `endTime`, `note`, `routineId`, `workoutStatus`, `endReason`, " +
                "`pausedAt`, `accumulatedPausedMs`, `restTimeEndAt`, `restTotalSec`, " +
                "`restWorkoutExerciseId` FROM `workouts`"
    ).use { c ->
        while (c.moveToNext()) {
            val oldId = c.getLong(0)
            val newId = UUID.randomUUID().toString()
            idMap[oldId] = newId
            if (!c.isNull(11)) pendingRest[newId] = c.getLong(11)
            insert.bindString(1, newId)
            insert.bindLong(2, c.getLong(1))
            if (c.isNull(2)) insert.bindNull(3) else insert.bindLong(3, c.getLong(2))
            if (c.isNull(3)) insert.bindNull(4) else insert.bindString(4, c.getString(3))
            if (c.isNull(4)) insert.bindNull(5) else insert.bindString(
                5,
                routineIdMap.getValue(c.getLong(4))
            )
            insert.bindString(6, c.getString(5))
            if (c.isNull(6)) insert.bindNull(7) else insert.bindString(7, c.getString(6))
            if (c.isNull(7)) insert.bindNull(8) else insert.bindLong(8, c.getLong(7))
            insert.bindLong(9, c.getLong(8))
            if (c.isNull(9)) insert.bindNull(10) else insert.bindLong(10, c.getLong(9))
            if (c.isNull(10)) insert.bindNull(11) else insert.bindLong(11, c.getLong(10))
            insert.executeInsert()
            insert.clearBindings()
        }
    }
    // See migrateRoutineExercises8To9's comment - indices must be (re)created after the old table
    // (holding the same index names) is dropped, not before.
    db.execSQL("DROP TABLE `workouts`")
    db.execSQL("ALTER TABLE `workouts_new` RENAME TO `workouts`")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_workouts_routineId` ON `workouts` (`routineId`)"
    )
    return idMap to pendingRest
}

internal fun migrateBodyMetrics8To9(
    db: SupportSQLiteDatabase,
    measurementTypeIdMap: Map<Long, String>
) {
    db.execSQL(
        """
        CREATE TABLE `body_metrics_new` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `measurementTypeId` TEXT NOT NULL,
            `date` INTEGER NOT NULL,
            `value` REAL NOT NULL,
            FOREIGN KEY(`measurementTypeId`) REFERENCES `measurement_types`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    val insert = db.compileStatement(
        "INSERT INTO `body_metrics_new` (`id`, `measurementTypeId`, `date`, `value`) VALUES (?, ?, ?, ?)"
    )
    db.query("SELECT `id`, `measurementTypeId`, `date`, `value` FROM `body_metrics`").use { c ->
        while (c.moveToNext()) {
            insert.bindString(1, UUID.randomUUID().toString())
            insert.bindString(2, measurementTypeIdMap.getValue(c.getLong(1)))
            insert.bindLong(3, c.getLong(2))
            insert.bindDouble(4, c.getDouble(3))
            insert.executeInsert()
            insert.clearBindings()
        }
    }
    // See migrateRoutineExercises8To9's comment - indices must be (re)created after the old table
    // (holding the same index names) is dropped, not before.
    db.execSQL("DROP TABLE `body_metrics`")
    db.execSQL("ALTER TABLE `body_metrics_new` RENAME TO `body_metrics`")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_body_metrics_measurementTypeId` " +
                "ON `body_metrics` (`measurementTypeId`)"
    )
}

internal fun migrateWorkoutExercises8To9(
    db: SupportSQLiteDatabase,
    workoutIdMap: Map<Long, String>,
    exerciseIdMap: Map<Long, String>,
): Map<Long, String> {
    db.execSQL(
        """
        CREATE TABLE `workout_exercises_new` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `workoutId` TEXT NOT NULL,
            `exerciseId` TEXT NOT NULL,
            `position` INTEGER NOT NULL,
            `isSkipped` INTEGER NOT NULL,
            `isDone` INTEGER NOT NULL,
            `supersetGroupId` TEXT,
            FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    val idMap = HashMap<Long, String>()
    val insert = db.compileStatement(
        "INSERT INTO `workout_exercises_new` (`id`, `workoutId`, `exerciseId`, `position`, " +
                "`isSkipped`, `isDone`, `supersetGroupId`) VALUES (?, ?, ?, ?, ?, ?, NULL)"
    )
    db.query(
        "SELECT `id`, `workoutId`, `exerciseId`, `position`, `isSkipped`, `isDone` " +
                "FROM `workout_exercises`"
    ).use { c ->
        while (c.moveToNext()) {
            val oldId = c.getLong(0)
            val newId = UUID.randomUUID().toString()
            idMap[oldId] = newId
            insert.bindString(1, newId)
            insert.bindString(2, workoutIdMap.getValue(c.getLong(1)))
            insert.bindString(3, exerciseIdMap.getValue(c.getLong(2)))
            insert.bindLong(4, c.getLong(3))
            insert.bindLong(5, c.getLong(4))
            insert.bindLong(6, c.getLong(5))
            insert.executeInsert()
            insert.clearBindings()
        }
    }
    // See migrateRoutineExercises8To9's comment - indices must be (re)created after the old table
    // (holding the same index names) is dropped, not before.
    db.execSQL("DROP TABLE `workout_exercises`")
    db.execSQL("ALTER TABLE `workout_exercises_new` RENAME TO `workout_exercises`")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_workout_exercises_workoutId` " +
                "ON `workout_exercises` (`workoutId`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_workout_exercises_exerciseId` " +
                "ON `workout_exercises` (`exerciseId`)"
    )
    return idMap
}

internal fun patchWorkoutsRestWorkoutExerciseId8To9(
    db: SupportSQLiteDatabase,
    pendingRest: Map<String, Long>,
    workoutExerciseIdMap: Map<Long, String>,
) {
    val update =
        db.compileStatement("UPDATE `workouts` SET `restWorkoutExerciseId` = ? WHERE `id` = ?")
    pendingRest.forEach { (newWorkoutId, oldRestWorkoutExerciseId) ->
        // Unlike every other lookup in this migration, restWorkoutExerciseId is not a real SQLite
        // foreign key (see MIGRATION_8_9's doc comment), so it isn't protected by
        // PRAGMA foreign_keys=ON - a dangling value here (however unlikely) must not crash the
        // whole one-shot, irreversible migration. Left null rather than aborting.
        val newWorkoutExerciseId = workoutExerciseIdMap[oldRestWorkoutExerciseId] ?: return@forEach
        update.bindString(1, newWorkoutExerciseId)
        update.bindString(2, newWorkoutId)
        update.executeUpdateDelete()
        update.clearBindings()
    }
}

internal fun migrateSetEntries8To9(
    db: SupportSQLiteDatabase,
    workoutExerciseIdMap: Map<Long, String>
) {
    db.execSQL(
        """
        CREATE TABLE `set_entries_new` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `workoutExerciseId` TEXT NOT NULL,
            `setNumber` INTEGER NOT NULL,
            `weightKg` REAL,
            `reps` INTEGER,
            `isComplete` INTEGER NOT NULL,
            FOREIGN KEY(`workoutExerciseId`) REFERENCES `workout_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    val insert = db.compileStatement(
        "INSERT INTO `set_entries_new` (`id`, `workoutExerciseId`, `setNumber`, `weightKg`, " +
                "`reps`, `isComplete`) VALUES (?, ?, ?, ?, ?, ?)"
    )
    db.query(
        "SELECT `id`, `workoutExerciseId`, `setNumber`, `weightKg`, `reps`, `isComplete` " +
                "FROM `set_entries`"
    ).use { c ->
        while (c.moveToNext()) {
            insert.bindString(1, UUID.randomUUID().toString())
            insert.bindString(2, workoutExerciseIdMap.getValue(c.getLong(1)))
            insert.bindLong(3, c.getLong(2))
            if (c.isNull(3)) insert.bindNull(4) else insert.bindDouble(4, c.getDouble(3))
            if (c.isNull(4)) insert.bindNull(5) else insert.bindLong(5, c.getLong(4))
            insert.bindLong(6, c.getLong(5))
            insert.executeInsert()
            insert.clearBindings()
        }
    }
    // See migrateRoutineExercises8To9's comment - indices must be (re)created after the old table
    // (holding the same index names) is dropped, not before.
    db.execSQL("DROP TABLE `set_entries`")
    db.execSQL("ALTER TABLE `set_entries_new` RENAME TO `set_entries`")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_set_entries_workoutExerciseId` " +
                "ON `set_entries` (`workoutExerciseId`)"
    )
}

internal fun migrateCardioEntries8To9(
    db: SupportSQLiteDatabase,
    workoutExerciseIdMap: Map<Long, String>
) {
    db.execSQL(
        """
        CREATE TABLE `cardio_entries_new` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `workoutExerciseId` TEXT NOT NULL,
            `durationSec` INTEGER NOT NULL,
            `distanceMeters` REAL,
            FOREIGN KEY(`workoutExerciseId`) REFERENCES `workout_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    val insert = db.compileStatement(
        "INSERT INTO `cardio_entries_new` (`id`, `workoutExerciseId`, `durationSec`, `distanceMeters`) " +
                "VALUES (?, ?, ?, ?)"
    )
    db.query(
        "SELECT `id`, `workoutExerciseId`, `durationSec`, `distanceMeters` FROM `cardio_entries`"
    ).use { c ->
        while (c.moveToNext()) {
            insert.bindString(1, UUID.randomUUID().toString())
            insert.bindString(2, workoutExerciseIdMap.getValue(c.getLong(1)))
            insert.bindLong(3, c.getLong(2))
            if (c.isNull(3)) insert.bindNull(4) else insert.bindDouble(4, c.getDouble(3))
            insert.executeInsert()
            insert.clearBindings()
        }
    }
    // See migrateRoutineExercises8To9's comment - indices must be (re)created after the old table
    // (holding the same index names) is dropped, not before.
    db.execSQL("DROP TABLE `cardio_entries`")
    db.execSQL("ALTER TABLE `cardio_entries_new` RENAME TO `cardio_entries`")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_cardio_entries_workoutExerciseId` " +
                "ON `cardio_entries` (`workoutExerciseId`)"
    )
}
