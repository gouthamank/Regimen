package dev.gouthaman.regimen.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.gouthaman.regimen.data.local.dao.ExerciseDao
import dev.gouthaman.regimen.data.local.dao.MeasurementDao
import dev.gouthaman.regimen.data.local.dao.RoutineDao
import dev.gouthaman.regimen.data.local.dao.WorkoutDao
import dev.gouthaman.regimen.data.local.entity.BodyMetric
import dev.gouthaman.regimen.data.local.entity.CardioEntry
import dev.gouthaman.regimen.data.local.entity.Exercise
import dev.gouthaman.regimen.data.local.entity.MeasurementType
import dev.gouthaman.regimen.data.local.entity.Routine
import dev.gouthaman.regimen.data.local.entity.RoutineExercise
import dev.gouthaman.regimen.data.local.entity.SetEntry
import dev.gouthaman.regimen.data.local.entity.Workout
import dev.gouthaman.regimen.data.local.entity.WorkoutExercise

@Database(
    entities = [
        Exercise::class,
        Routine::class,
        RoutineExercise::class,
        Workout::class,
        WorkoutExercise::class,
        SetEntry::class,
        CardioEntry::class,
        MeasurementType::class,
        BodyMetric::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class RegimenDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun measurementDao(): MeasurementDao

    companion object {
        const val NAME = "regimen.db"
    }
}
