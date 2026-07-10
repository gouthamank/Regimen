package dev.gouthaman.regimen.ui.routines

/**
 * Shared-bounds key linking a Routines row's card to the Routine Editor screen's root container
 * for that same routine, so Editor expands from the tapped card when editing an existing routine.
 */
internal fun routineRowTransitionKey(routineId: Long) = "routine-row-$routineId"

/**
 * Shared-bounds key linking the Routines tab's "New routine" FAB to the Routine Editor screen's
 * root container, so Editor expands from the FAB when creating a new routine. One FAB is visible
 * at a time, so — unlike [routineRowTransitionKey] — this needs no per-routine suffix.
 */
internal const val routineCreateFabTransitionKey = "routine-create-fab"
