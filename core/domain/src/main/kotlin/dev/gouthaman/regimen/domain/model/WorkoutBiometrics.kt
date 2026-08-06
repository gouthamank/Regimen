package dev.gouthaman.regimen.domain.model

/** Biometrics associated with a particular workout. [heartRateSeries] caches the on-demand chart's
 * downsampled points - null until that chart's been requested once. */
data class WorkoutBiometrics(
    val id: String,
    val workoutId: String,
    val avgBpm: Int? = null,
    val maxBpm: Int? = null,
    val activeCaloriesKcal: Double? = null,
    val sourcePackageName: String? = null,
    val fetchedAt: Long,
    val heartRateSeries: List<Int>? = null,
)