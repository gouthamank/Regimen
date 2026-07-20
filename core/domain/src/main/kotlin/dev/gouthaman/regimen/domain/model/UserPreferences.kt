package dev.gouthaman.regimen.domain.model

/** User-facing settings; weight/distance are stored canonically, [weightUnit]/[distanceUnit] are display-only. */
data class UserPreferences(
    val weightUnit: UnitSystem = UnitSystem.METRIC,
    val distanceUnit: UnitSystem = UnitSystem.METRIC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val restDefaultSec: Int = 90,
    val restChimeEnabled: Boolean = true,
    val maxWorkoutDuration: MaxWorkoutDuration = MaxWorkoutDuration.FOUR_HOURS,
    val onboarded: Boolean = false,
)
