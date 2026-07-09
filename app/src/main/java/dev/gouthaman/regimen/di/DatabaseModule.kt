package dev.gouthaman.regimen.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.dao.ExerciseDao
import dev.gouthaman.regimen.data.local.dao.MeasurementDao
import dev.gouthaman.regimen.data.local.dao.RoutineDao
import dev.gouthaman.regimen.data.local.dao.WorkoutDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RegimenDatabase =
        Room.databaseBuilder(context, RegimenDatabase::class.java, RegimenDatabase.NAME)
            // Pre-release: no hand-written migrations — recreate the DB on any schema change.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideExerciseDao(db: RegimenDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideRoutineDao(db: RegimenDatabase): RoutineDao = db.routineDao()

    @Provides
    fun provideWorkoutDao(db: RegimenDatabase): WorkoutDao = db.workoutDao()

    @Provides
    fun provideMeasurementDao(db: RegimenDatabase): MeasurementDao = db.measurementDao()
}
