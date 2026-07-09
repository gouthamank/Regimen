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

/**
 * Selectable historical-data window for charts (Progress frequency chart, Measurement trend).
 * [weeks] is null for [ALL] (no cutoff — callers resolve the actual span from the data).
 */
enum class HistoryRange(val label: String, val weeks: Int?) {
    FOUR_WEEKS("4w", 4),
    THREE_MONTHS("3m", 13),
    ONE_YEAR("1y", 52),
    ALL("All", null),
}

private const val MILLIS_PER_WEEK = 7L * 24 * 60 * 60 * 1000

/** Millis cutoff (entries at/after this are in range), or null for [HistoryRange.ALL]. */
fun HistoryRange.cutoffMillis(nowMillis: Long = System.currentTimeMillis()): Long? =
    weeks?.let { nowMillis - it * MILLIS_PER_WEEK }
