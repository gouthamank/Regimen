package dev.gouthaman.regimen.domain.model

import java.time.LocalDate

/** A personal record: heaviest weight (kg) lifted for an exercise, or — for exercises logged
 * without a weight (bodyweight) — the most reps in a single set. Exactly one of [bestWeightKg]
 * / [bestReps] is set. */
data class PersonalRecord(
    val exerciseId: Long,
    val exerciseName: String,
    val bestWeightKg: Double? = null,
    val bestReps: Int? = null,
)

/** Number of workouts in the week beginning [weekStart] (Monday). */
data class WeekCount(
    val weekStart: LocalDate,
    val count: Int,
)
