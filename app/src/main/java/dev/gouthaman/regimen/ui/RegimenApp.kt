package dev.gouthaman.regimen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.navigation.HomeRoute
import dev.gouthaman.regimen.navigation.WorkoutSummaryRoute
import dev.gouthaman.regimen.ui.navigation.RegimenNavHost
import dev.gouthaman.regimen.ui.navigation.TopLevelDestination
import dev.gouthaman.regimen.ui.navigation.navigateToTab
import dev.gouthaman.regimen.ui.navigation.topLevelDestinations
import kotlinx.coroutines.launch

@Composable
fun RegimenApp(
    viewModel: RegimenAppViewModel = hiltViewModel(),
    deepLinkWorkoutId: Long? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val inProgressWorkoutId by viewModel.inProgressWorkoutId.collectAsStateWithLifecycle()
    val windowInfo = LocalRegimenWindowInfo.current

    // Tracks which bottom tab stays highlighted. Routes live in one flat NavHost (no per-tab
    // nested graphs), so a pushed detail screen (e.g. Session Detail) has no graph-level tie back
    // to its tab - and restoreState can land back on that detail screen rather than the tab root,
    // so this can't be inferred from the resolved destination alone. Set eagerly at the point of
    // intent (see onNavigateToTab / navigateToTab call sites); the listener below only fills in
    // non-explicit cases (cold start, popping back to a tab's own root).
    //
    // Saved as an index into topLevelDestinations via rememberSaveable, not remember or the route
    // object itself - plain `remember` was lost on rotation (Activity recreation), resetting this
    // to null with no tab highlighted after rotating on a non-top-level screen like Session
    // Detail. The listener's immediate re-add callback only matches top-level destinations
    // directly, so it couldn't recover the value either.
    var activeTabIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val activeTabRoute: Any? = activeTabIndex?.let { topLevelDestinations.getOrNull(it)?.route }
    val onNavigateToTab: (Any) -> Unit = { route ->
        activeTabIndex = topLevelDestinations.indexOfFirst { it.route == route }.takeIf { it >= 0 }
    }

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val matched =
                topLevelDestinations.indexOfFirst { destination.hasRoute(it.route::class) }
            if (matched >= 0) {
                activeTabIndex = matched
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    val activeWorkoutSheetState = rememberActiveWorkoutSheetState()
    val coroutineScope = rememberCoroutineScope()

    // activeWorkoutSheetState is created once for the whole session (not per workout) - reset it
    // to Collapsed the moment a new workout starts being tracked, so it can't inherit whatever
    // expand/collapse state a *previous* workout left it in. Keyed on the id itself (not just
    // "non-null") so this fires once per new workout, not on every recomposition while one's
    // already in progress - which would otherwise undo the user's own drag/tap mid-session.
    LaunchedEffect(inProgressWorkoutId) {
        if (inProgressWorkoutId != null) activeWorkoutSheetState.resetToCollapsed()
    }

    // Tapping the in-progress/rest-complete notification (MainActivity's EXTRA_WORKOUT_ID) lands
    // here the same way the sheet's own tap-to-expand does - anchored under Home regardless of
    // the current tab. Expanding is idempotent (a no-op if already expanded), so no guard is
    // needed for re-tapping the notification while the sheet's already open.
    LaunchedEffect(deepLinkWorkoutId) {
        if (deepLinkWorkoutId != null) {
            onNavigateToTab(HomeRoute)
            navController.navigateToTab(HomeRoute)
            activeWorkoutSheetState.expand()
            onDeepLinkConsumed()
        }
    }

    // Requested here (app launch, right after onboarding gates past MainActivity) rather than
    // inside Active Workout, so it's always resolved (granted or denied) well before a workout
    // can ever start - the system dialog is modal, so the user can't reach Home/Start Workout
    // while it's showing. This closes a real race (item 11 of the Active Workout punch list):
    // asking from inside Active Workout meant the foreground service's first startForeground()
    // call - fired the instant the workout's DB row is written, before Compose even navigates
    // there - could beat the permission grant, silently suppressing that first notification with
    // nothing to retroactively re-post it.
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    NavigationSuiteScaffold(
        layoutType = windowInfo.posture.toNavigationSuiteType(),
        // NavigationRail defaults to colorScheme.surface, NavigationBar to surfaceContainer - the
        // same tone a MediumTopAppBar collapses to on scroll. Pinned to surfaceContainer here so
        // the rail (BookOrExpanded) matches that tone too, not just the bottom bar (Compact/Tabletop).
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationSuiteItems = {
            topLevelDestinations.forEach { dest ->
                val selected = activeTabRoute == dest.route
                item(
                    selected = selected,
                    onClick = {
                        // Collapse first - a tab switch should always reveal the tapped tab's
                        // content, not leave an expanded workout sitting on top of it.
                        coroutineScope.launch { activeWorkoutSheetState.collapse() }
                        onTabSelected(
                            navController,
                            dest,
                            alreadySelected = selected,
                            onNavigateToTab,
                        )
                    },
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) },
                )
            }
        },
    ) {
        // NavigationSuiteScaffold doesn't pad the content pane for the bottom system-bar inset
        // (only the nav bar/rail/drawer consume insets for themselves), so this pane does it
        // directly - otherwise content sits flush against the gesture-nav area.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            // A `when` previously calling RegimenNavHost from two branches (bare vs. wrapped in
            // Box/Column) tore down and recomposed the whole nav-host subtree on every posture
            // change, orphaning/restoring-stale every rememberSaveable underneath - the actual
            // cause of sheets spuriously closing/reopening when rotating in and out of Tabletop
            // (see "Modal/dialog dismissed on rotation fix" below). Only the width-cap Modifier
            // value varies by posture now, not the composable structure.
            //
            // Compact and Tabletop both keep the bottom NavigationBar, but the window behind it
            // isn't guaranteed phone-narrow: Tabletop can be genuinely wide (confirmed via a
            // half-opened, 90°-hinge AVD state at ~852dp) since isTabletop overrides the
            // width-based Rail decision. Cap and center content in both cases rather than
            // stretching edge-to-edge under phone-like chrome. The cap is the same Medium-width
            // breakpoint classify() uses to decide "promote to BookOrExpanded"
            // (androidx.window.core.layout.WindowSizeClass) - not arbitrary, and a no-op for the
            // common Compact case since a normal phone is already narrower than this.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                val widthCapModifier = if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier
                        .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                        .fillMaxHeight()
                }
                // RegimenNavHost and ActiveWorkoutSheet overlap here (a Box, not a Column) so the
                // sheet can grow to cover the NavHost's content when expanded - confined to this
                // content pane specifically (not a sibling of the whole NavigationSuiteScaffold)
                // so it never draws over the nav bar/rail, which NavigationSuiteScaffold reserves
                // as separate space outside this Column entirely. Matches how the old NavHost-
                // pushed Active Workout screen behaved too - it never covered the nav bar either.
                Box(modifier = widthCapModifier) {
                    // Reserves the collapsed banner's own footprint at the bottom of every
                    // screen's content, rather than letting the banner float over whatever's
                    // already there - it's a sibling Box overlay (needed so it can grow to cover
                    // the whole pane when Expanded), which has no other way to participate in
                    // NavHost's layout the way a real Scaffold bottomBar would. Animated in step
                    // with the sheet's own mount/unmount transition below, since covering/
                    // uncovering that space instantly while the banner fades in/out over several
                    // frames would read as a mismatched jump.
                    val bottomInset by animateDpAsState(
                        targetValue = if (inProgressWorkoutId != null) CollapsedHeight else 0.dp,
                        animationSpec = tween(220),
                        label = "activeWorkoutSheetBottomInset",
                    )
                    RegimenNavHost(
                        navController = navController,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = bottomInset),
                        onNavigateToTab = onNavigateToTab,
                        onWorkoutStarted = {
                            // Anchor under Home regardless of which tab this was triggered from
                            // (Home's own "Start workout" button, or History's "Repeat" action) -
                            // a no-op tab switch if already on Home (launchSingleTop).
                            onNavigateToTab(HomeRoute)
                            navController.navigateToTab(HomeRoute)
                            coroutineScope.launch { activeWorkoutSheetState.expand() }
                        },
                    )
                    // AnimatedVisibility's content composes both while visible and while animating
                    // out after visible flips false - it needs a concrete workoutId for that exit
                    // frame too, a moment after inProgressWorkoutId has already gone null. This is
                    // purely cosmetic (which id to animate out with), unlike the sticky value this
                    // replaced - onFinished/onDiscarded no longer depend on it for correctness.
                    var lastWorkoutId by remember { mutableStateOf<Long?>(null) }
                    if (inProgressWorkoutId != null) lastWorkoutId = inProgressWorkoutId
                    val workoutIdToAnimate = lastWorkoutId

                    // Fully qualified: the Column two levels up (this content lambda's own
                    // enclosing scope) makes ColumnScope.AnimatedVisibility an equally-reachable
                    // implicit-receiver candidate alongside the plain top-level overload actually
                    // wanted here, which the compiler can't disambiguate on its own.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = inProgressWorkoutId != null,
                        // Grows/shrinks from the bottom edge, matching the collapsed banner's dock
                        // position - a workout starting or ending reads as the banner growing up
                        // out of/sinking back into the bottom edge, not just fading in place. If the
                        // sheet is Expanded when a workout ends (Discard), this shrinks the whole
                        // full-screen content down and away instead of it just vanishing.
                        enter = fadeIn(tween(220)) + expandVertically(
                            animationSpec = tween(220),
                            expandFrom = Alignment.Bottom,
                        ),
                        exit = fadeOut(tween(220)) + shrinkVertically(
                            animationSpec = tween(220),
                            shrinkTowards = Alignment.Bottom,
                        ),
                    ) {
                        if (workoutIdToAnimate != null) {
                            ActiveWorkoutSheet(
                                workoutId = workoutIdToAnimate,
                                state = activeWorkoutSheetState,
                                navController = navController,
                                // Fired directly from the sheet's own Finish/Discard confirm
                                // dialogs, not reactively off the DB write - navigation doesn't
                                // need to wait for that write to land, and this composable owns the
                                // NavController (unlike the sheet, which is why it wasn't safe to
                                // have the sheet call navController.navigate() itself). See
                                // ActiveWorkoutSheet's doc.
                                onFinished = { finishedWorkoutId ->
                                    navController.navigate(WorkoutSummaryRoute(finishedWorkoutId))
                                },
                                onDiscarded = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Regimen's own posture buckets drive the nav layout choice for now - kept as its own named
 *  mapping (rather than inlined) so a future override (e.g. a user-facing settings toggle) has
 *  one obvious seam to hook into. Tabletop stays on the bottom bar since it's already anchored
 *  to the physical bottom edge, landing in the reachable pane below the hinge - notably, this
 *  matches what `NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo` does by default too. */
private fun RegimenPosture.toNavigationSuiteType(): NavigationSuiteType = when (this) {
    RegimenPosture.Compact, RegimenPosture.Tabletop -> NavigationSuiteType.NavigationBar
    RegimenPosture.BookOrExpanded -> NavigationSuiteType.NavigationRail
}

/**
 * Re-tapping the already-selected tab pops it back to its root; tapping a different tab
 * navigates there, saving/restoring each tab's own back-stack state.
 */
private fun onTabSelected(
    navController: NavHostController,
    dest: TopLevelDestination,
    alreadySelected: Boolean,
    onNavigateToTab: (Any) -> Unit,
) {
    if (alreadySelected) {
        navController.popBackStack(dest.route, inclusive = false)
    } else {
        onNavigateToTab(dest.route)
        navController.navigateToTab(dest.route)
    }
}

