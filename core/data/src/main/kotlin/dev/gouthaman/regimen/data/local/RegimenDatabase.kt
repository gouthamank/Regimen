package dev.gouthaman.regimen.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.gouthaman.regimen.data.local.dao.ExerciseDao
import dev.gouthaman.regimen.data.local.dao.MeasurementDao
import dev.gouthaman.regimen.data.local.dao.RoutineDao
import dev.gouthaman.regimen.data.local.dao.SyncTombstoneDao
import dev.gouthaman.regimen.data.local.dao.WorkoutBiometricsDao
import dev.gouthaman.regimen.data.local.dao.WorkoutDao
import dev.gouthaman.regimen.data.local.entity.BodyMetricEntity
import dev.gouthaman.regimen.data.local.entity.CardioEntryEntity
import dev.gouthaman.regimen.data.local.entity.ExerciseEntity
import dev.gouthaman.regimen.data.local.entity.MeasurementTypeEntity
import dev.gouthaman.regimen.data.local.entity.RoutineEntity
import dev.gouthaman.regimen.data.local.entity.RoutineExerciseEntity
import dev.gouthaman.regimen.data.local.entity.SetEntryEntity
import dev.gouthaman.regimen.data.local.entity.SyncTombstoneEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutBiometricsEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutExerciseEntity

@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        SetEntryEntity::class,
        CardioEntryEntity::class,
        MeasurementTypeEntity::class,
        BodyMetricEntity::class,
        SyncTombstoneEntity::class,
        WorkoutBiometricsEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RegimenDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun syncTombstoneDao(): SyncTombstoneDao
    abstract fun workoutBiometricsDao(): WorkoutBiometricsDao

    companion object {
        const val NAME = "regimen.db"
    }
}
