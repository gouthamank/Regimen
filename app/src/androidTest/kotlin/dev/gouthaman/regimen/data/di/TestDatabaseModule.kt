package dev.gouthaman.regimen.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.dao.ExerciseDao
import dev.gouthaman.regimen.data.local.dao.MeasurementDao
import dev.gouthaman.regimen.data.local.dao.RoutineDao
import dev.gouthaman.regimen.data.local.dao.SyncTombstoneDao
import dev.gouthaman.regimen.data.local.dao.WorkoutBiometricsDao
import dev.gouthaman.regimen.data.local.dao.WorkoutDao
import javax.inject.Singleton

/** Replaces [DatabaseModule] for instrumentation tests with an in-memory Room instance - a clean
 * slate every run instead of whatever's actually persisted on the test device, same in-memory
 * pattern `:core:data`'s `WorkoutDaoTest` already uses for its own Room tests. No migrations
 * needed since an in-memory DB is always created fresh at the current schema version. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RegimenDatabase =
        Room.inMemoryDatabaseBuilder(context, RegimenDatabase::class.java).build()

    @Provides
    fun provideExerciseDao(db: RegimenDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideRoutineDao(db: RegimenDatabase): RoutineDao = db.routineDao()

    @Provides
    fun provideWorkoutDao(db: RegimenDatabase): WorkoutDao = db.workoutDao()

    @Provides
    fun provideMeasurementDao(db: RegimenDatabase): MeasurementDao = db.measurementDao()

    @Provides
    fun provideSyncTombstoneDao(db: RegimenDatabase): SyncTombstoneDao = db.syncTombstoneDao()

    @Provides
    fun provideWorkoutBiometricsDao(db: RegimenDatabase): WorkoutBiometricsDao =
        db.workoutBiometricsDao()
}
