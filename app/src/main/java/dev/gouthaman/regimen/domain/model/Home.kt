package dev.gouthaman.regimen.domain.model

/** This-week activity summary shown on the Home dashboard. Volume is canonical (kg). */
data class HomeSummary(
    val workoutsThisWeek: Int,
    val volumeKgThisWeek: Double,
    val durationMillisThisWeek: Long,
    /** Consecutive weeks (incl. current) with at least one workout. */
    val weekStreak: Int,
)
