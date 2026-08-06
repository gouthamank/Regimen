package dev.gouthaman.regimen.domain.model

/** One completed workout's contribution to a heart-rate trend. */
data class HeartRateTrendEntry(
    val workoutId: String,
    val startTime: Long,
    val durationMillis: Long,
    val avgBpm: Int,
)

/** [routineId] null = the synthetic "all routines combined" row; [routineName] is then also null. */
data class HeartRateTrendRow(
    val routineId: String?,
    val routineName: String?,
    val trend: List<Float>,
    val entryCount: Int,
)
