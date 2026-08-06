package dev.gouthaman.regimen.domain.model

/** Biometrics associated with a particular workout */
data class WorkoutBiometrics(
    val id: String,
    val workoutId: String,
    val avgBpm: Int? = null,
    val maxBpm: Int? = null,
    val activeCaloriesKcal: Double? = null,
    val sourcePackageName: String? = null,
    val fetchedAt: Long,
)