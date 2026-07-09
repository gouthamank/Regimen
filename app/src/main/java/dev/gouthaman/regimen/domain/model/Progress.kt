package dev.gouthaman.regimen.domain.model

import java.time.LocalDate

/** A personal record: heaviest weight (kg) lifted for an exercise. */
data class PersonalRecord(
    val exerciseId: Long,
    val exerciseName: String,
    val bestWeightKg: Double,
)

/** Number of workouts in the week beginning [weekStart] (Monday). */
data class WeekCount(
    val weekStart: LocalDate,
    val count: Int,
)
