package dev.gouthaman.regimen.common

/**
 * Every shared-bounds/shared-element key used for container-transform navigation across the app,
 * collected in one place (`:core:common-ui`, which every feature module already depends on) so
 * cross-module pairs (e.g. Progress <-> Measurements, Settings <-> Exercise Library) don't need a
 * direct module dependency just to share a string constant, and so same-module pairs (e.g.
 * Routines row <-> Routine Editor) aren't scattered across a separate file per feature.
 */

/** Links a Routines row's card to the Routine Editor screen's root container for that same
 * routine, so Editor expands from the tapped card when editing an existing routine. */
fun routineRowTransitionKey(routineId: String) = "routine-row-$routineId"

/** Links the Routines tab's "New routine" FAB to the Routine Editor screen's root container, so
 * Editor expands from the FAB when creating a new routine. One FAB is visible at a time, so -
 * unlike [routineRowTransitionKey] - this needs no per-routine suffix. */
const val routineCreateFabTransitionKey = "routine-create-fab"

/** Links a Library row's card to the Exercise Detail screen's root container for that same
 * exercise, so Detail expands from the tapped card. */
fun exerciseRowTransitionKey(exerciseId: String) = "exercise-row-$exerciseId"

/** Links Settings' "Exercise Library" row to the Exercise Library screen's root container -
 * Settings is Library's only entry point, so this is applied unconditionally. */
const val exerciseLibraryFromSettingsTransitionKey = "exercise-library-from-settings"

/** Links a Measurements row's card to the Measurement Detail screen's root container for that
 * same measurement type, so Detail expands from the tapped card. */
fun measurementRowTransitionKey(typeId: String) = "measurement-row-$typeId"

/** Shared-element key for the "Add entry" FAB, present (identically) on both Measurements and
 * Measurement Detail. Without this, the FAB has no identity across the transition and just gets
 * dragged along by the row card's container transform as it resizes to the full screen - tagging
 * it directly keeps it visually anchored in place instead. One FAB is visible per screen at a
 * time, so this key needs no per-type suffix (unlike [measurementRowTransitionKey]). */
const val measurementFabTransitionKey = "measurement-fab"

/** Links the Progress tab's "Body Measurements" link row to the Measurements screen's root
 * container, so Measurements expands from that row when opened from Progress. Opening it from
 * Home's "Log bodyweight" button instead keeps the plain slide transition, since that entry point
 * has no matching row to expand from. */
const val measurementsFromProgressTransitionKey = "measurements-from-progress"

/** Links a session's origin in History (a "recent sessions" row, or a single-session calendar day
 * cell) to Session Detail's root container for that same workout, so Detail expands from wherever
 * it was tapped instead of sliding in. Days with more than one session open a picker dialog
 * instead - dialogs run in their own window and can't participate in the shared-element
 * transform, so that path keeps the default transition. */
fun sessionRowTransitionKey(workoutId: String) = "session-row-$workoutId"
