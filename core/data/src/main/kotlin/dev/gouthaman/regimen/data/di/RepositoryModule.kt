package dev.gouthaman.regimen.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gouthaman.regimen.data.prefs.PreferencesRepositoryImpl
import dev.gouthaman.regimen.data.repository.ExerciseRepositoryImpl
import dev.gouthaman.regimen.data.repository.MeasurementRepositoryImpl
import dev.gouthaman.regimen.data.repository.RoutineRepositoryImpl
import dev.gouthaman.regimen.data.repository.WorkoutBiometricsRepositoryImpl
import dev.gouthaman.regimen.data.repository.WorkoutRepositoryImpl
import dev.gouthaman.regimen.domain.repository.ExerciseRepository
import dev.gouthaman.regimen.domain.repository.MeasurementRepository
import dev.gouthaman.regimen.domain.repository.PreferencesRepository
import dev.gouthaman.regimen.domain.repository.RoutineRepository
import dev.gouthaman.regimen.domain.repository.WorkoutBiometricsRepository
import dev.gouthaman.regimen.domain.repository.WorkoutRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindRoutineRepository(impl: RoutineRepositoryImpl): RoutineRepository

    @Binds
    @Singleton
    abstract fun bindMeasurementRepository(impl: MeasurementRepositoryImpl): MeasurementRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutBiometricsRepository(impl: WorkoutBiometricsRepositoryImpl): WorkoutBiometricsRepository
}
