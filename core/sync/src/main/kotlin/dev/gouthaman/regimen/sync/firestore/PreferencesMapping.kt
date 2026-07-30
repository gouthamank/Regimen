package dev.gouthaman.regimen.sync.firestore

import dev.gouthaman.regimen.domain.model.UserPreferences

/** Firestore shape for the single `users/{uid}/preferences` document. Deliberately excludes
 * [UserPreferences.onboarded] - that's a per-device concept (a new device should always run its
 * own onboarding), not something that makes sense to carry across devices. */
data class PreferencesDto(
    val weightUnit: String = "",
    val distanceUnit: String = "",
    val themeMode: String = "",
    val dynamicColor: Boolean = true,
    val restDefaultSec: Int = 90,
    val restChimeEnabled: Boolean = true,
    val maxWorkoutDuration: String = "",
    val lastModifiedAt: Long = 0,
)

/** [lastModifiedAt] is passed in rather than read off [UserPreferences] itself, since it lives as
 * a separate DataStore key (`last_modified_at`) alongside the preferences, not on the domain
 * model - the same reason Room's `isDirty`/`lastModifiedAt` stay off domain models entirely. */
fun UserPreferences.toDto(lastModifiedAt: Long): PreferencesDto = PreferencesDto(
    weightUnit = weightUnit.name,
    distanceUnit = distanceUnit.name,
    themeMode = themeMode.name,
    dynamicColor = dynamicColor,
    restDefaultSec = restDefaultSec,
    restChimeEnabled = restChimeEnabled,
    maxWorkoutDuration = maxWorkoutDuration.name,
    lastModifiedAt = lastModifiedAt,
)
