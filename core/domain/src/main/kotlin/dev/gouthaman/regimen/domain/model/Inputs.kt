package dev.gouthaman.regimen.domain.model

/** A single exercise line while building/editing a routine. */
data class ExerciseSpec(
    val exerciseId: String,
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val targetRestSec: Int = 90,
)
