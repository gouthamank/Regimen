package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.WorkoutBiometrics

interface WorkoutBiometricsRepository {
    suspend fun get(workoutId: String): WorkoutBiometrics?
    suspend fun upsert(biometrics: WorkoutBiometrics): String
}