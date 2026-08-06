package dev.gouthaman.regimen.domain.model

/** One completed workout's contribution to a biometric trend. [avgBpm]/[activeCaloriesKcal] are
 * independently nullable - a workout needs at least one to be included at all. [routineName] is
 * null for a freeform workout. */
data class BiometricTrendEntry(
    val workoutId: String,
    val startTime: Long,
    val durationMillis: Long,
    val avgBpm: Int?,
    val activeCaloriesKcal: Double?,
    val routineName: String?,
)

/** [routineId] null = the synthetic "all routines combined" row; [routineName] is then also null. */
data class BiometricTrendRow(
    val routineId: String?,
    val routineName: String?,
    val avgBpmTrend: List<Float>,
    val caloriesTrend: List<Float>,
)
