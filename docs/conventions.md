# Regimen - Coding Conventions

Detailed, narrowly-scoped conventions referenced from `CLAUDE.md`. `CLAUDE.md` itself only points
here rather than restating the detail - read the relevant section below when a task actually
touches that area.

## Strings

All user-facing text (labels, button text, dialog copy, content descriptions, Snackbar/Toast
messages) goes in `res/values/strings.xml` - never a hardcoded string literal in a Composable. Use
`stringResource()` / `pluralStringResource()`; use `<plurals>` for anything a count drives ("N
reps", "N-week streak"), not manual singular/plural branching. Naming is `screen_element` (e.g.
`routines_delete_dialog_title`) - each screen gets its own keys even when the English text is
identical to another screen's, *except* enum-value display names (e.g. `UnitSystem`, `ThemeMode`
labels), which are genuinely one canonical name shown in multiple places and should share a single
resource.

`stringResource`/`pluralStringResource` only work inside `@Composable` functions, so a ViewModel
must never pre-format display text itself - if it needs to show a count, a unit, or a conditional
fallback (e.g. "Quick workout" when a routine name is null), expose the raw or structured data in
UI state (see `PersonalRecordValue`, `WeightValue`, `routineName: String?` for the pattern) and do
the actual string formatting in the Composable at render time. A lambda parameter that calls a
now-`@Composable` formatter (e.g. an enum's `.label()`) must itself be typed
`@Composable (T) -> String`, not a plain `(T) -> String`.

`UnitConverter`/`SessionFormat`/`MeasurementFormat`/`ExerciseLabels` are the shared formatters for
this - `UnitConverter` (`:core:domain`) `.weightLabel`/`.distanceLabel` return a `UnitLabel` enum,
resolved to text via `:core:common-ui`'s `UnitLabelText.kt`'s `UnitLabel.text()`. Exempt from all
this: date/time `SimpleDateFormat` patterns, purely numeric formatters (mm:ss, elapsed-time), and
punctuation separators ("·", "×") - none of that is translatable prose.

## Screen shell

Every pushed/top-level screen (a `composable<Route>` destination - not a bottom sheet or dialog)
wires up the same structural scaffold; don't improvise a bare `TopAppBar` with no scroll behavior
or a full-bleed `Column` with no width cap just because the task at hand is about a screen's
content/dialogs/ViewModel wiring rather than its shell. Read `LocalRegimenWindowInfo.current`
(`:core:designsystem`'s `adaptive/WindowAdaptive.kt`) and cap content to
`WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp`, centered, when
`windowInfo.posture == RegimenPosture.BookOrExpanded` - see `SettingsScreen`/
`ExerciseLibraryScreen`/`ExerciseDetailScreen`/`MeasurementDetailScreen` for the exact pattern.
Pick a top bar the same way those screens do: `MediumTopAppBar` +
`TopAppBarDefaults.exitUntilCollapsedScrollBehavior()` for an ordinary screen or a pushed detail
screen; a plain `TopAppBar` + `TopAppBarDefaults.enterAlwaysScrollBehavior()` only when the title
slot holds something interactive (e.g. Exercise Library's search field) rather than static text.
Wire the chosen `scrollBehavior` into the `Scaffold`'s modifier via
`.nestedScroll(scrollBehavior.nestedScrollConnection)` - alongside any `sharedBounds(...)`
shared-element transition modifier, not in place of it. This convention has no test coverage and
is easy to silently omit when a new screen is built or reviewed piecemeal (e.g. only diffing the
parts of a sibling screen relevant to whatever's being discussed) - when building or reviewing a
new screen, diff its full file against the closest existing comparable screen's *entire* file, not
just the fragment under active discussion.
