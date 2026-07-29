package dev.gouthaman.regimen.domain.model

import java.time.LocalDate

/** A personal record: heaviest weight (kg) for weighted exercises, or most reps in a set for
 * bodyweight ones. Exactly one of [bestWeightKg] / [bestReps] is set. */
data class PersonalRecord(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: MuscleGroup,
    val bestWeightKg: Double? = null,
    val bestReps: Int? = null,
)

/** Number of workouts in the week beginning [weekStart] (Monday). */
data class WeekCount(
    val weekStart: LocalDate,
    val count: Int,
)
