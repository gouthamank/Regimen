package dev.gouthaman.regimen.domain.model

/** This-week/this-month activity summary shown on the Home dashboard. Volume is canonical (kg). */
data class HomeSummary(
    val workoutsThisWeek: Int,
    val volumeKgThisWeek: Double,
    val durationMillisThisWeek: Long,
    /** Consecutive weeks (incl. current) with at least one workout. */
    val weekStreak: Int,
    val workoutsThisMonth: Int,
    val volumeKgThisMonth: Double,
    val durationMillisThisMonth: Long,
    /** Sum of pulled Health Connect active-calories across the period's workouts. Null means the
     * period has at least one workout but none has calorie data yet (Health Connect hasn't synced
     * it, or the backfill job hasn't run) - distinct from a genuine 0, which only occurs when the
     * period itself has no workouts. A workout with a biometrics row but no calories value
     * contributes 0 to the sum, same as volume treats bodyweight-only sets as 0 load. */
    val caloriesKcalThisWeek: Double?,
    val caloriesKcalThisMonth: Double?,
)
