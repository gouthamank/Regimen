package dev.gouthaman.regimen.ui.exercise

/**
 * Shared-bounds key linking a Library row's card to the Exercise Detail screen's root
 * container for that same exercise, so Detail expands from the tapped card.
 */
internal fun exerciseRowTransitionKey(exerciseId: Long) = "exercise-row-$exerciseId"
