package dev.gouthaman.regimen.domain.model

/** Whether an exercise is resistance training or cardio. Cardio is session-only (never in routines). */
enum class ExerciseType { STRENGTH, CARDIO }

enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE, FULL_BODY, CARDIO, OTHER
}

enum class Equipment {
    BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, KETTLEBELL, CARDIO_MACHINE, OTHER
}

/** Display unit system. Weight and cardio distance are stored canonically (kg, meters). */
enum class UnitSystem { METRIC, IMPERIAL }

enum class ThemeMode { LIGHT, DARK, SYSTEM }
