package dev.gouthaman.regimen.domain.model

import dev.gouthaman.regimen.data.local.entity.Exercise

/**
 * True if [query] matches this exercise's name, or its type/muscle-group/equipment tags — so
 * e.g. searching "cardio" surfaces all cardio exercises, not just ones with "cardio" in the name.
 * Shared by the Exercise Library search and the Exercise Picker sheet.
 */
fun Exercise.matchesSearch(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    fun tag(rawName: String) = rawName.replace('_', ' ')
    return name.contains(q, ignoreCase = true) ||
        tag(type.name).contains(q, ignoreCase = true) ||
        tag(muscleGroup.name).contains(q, ignoreCase = true) ||
        tag(equipment.name).contains(q, ignoreCase = true)
}
