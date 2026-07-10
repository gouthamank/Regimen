package dev.gouthaman.regimen.ui.measurements

/**
 * Shared-bounds key linking a Measurements row's card to the Measurement Detail screen's root
 * container for that same measurement type, so Detail expands from the tapped card.
 */
internal fun measurementRowTransitionKey(typeId: Long) = "measurement-row-$typeId"

/**
 * Shared-element key for the "Add entry" FAB, present (identically) on both Measurements and
 * Measurement Detail. Without this, the FAB has no identity across the transition and just gets
 * dragged along by the row card's container transform as it resizes to the full screen — tagging
 * it directly keeps it visually anchored in place instead. One FAB is visible per screen at a
 * time, so this key needs no per-type suffix (unlike [measurementRowTransitionKey]).
 */
internal const val measurementFabTransitionKey = "measurement-fab"
