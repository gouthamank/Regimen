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
    deepLinkWorkoutId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val inProgressWorkoutId by viewModel.inProgressWorkoutId.collectAsStateWithLifecycle()
    val windowInfo = LocalRegimenWindowInfo.current

    // Tracks which bottom tab stays highlighted. Can't be inferred from the resolved destination
    // alone (pushed detail screens have no graph-level tie back to their tab), so it's set eagerly
    // at the point of intent; the listener below only fills in non-explicit cases. Saved via
    // rememberSaveable (not remember) so it survives rotation.
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
    // expand/collapse state a *previous* workout left it in. lastResetWorkoutId is rememberSaveable
    // (not just a LaunchedEffect key) so a rotation - which restarts this LaunchedEffect from
    // scratch with the same still-in-progress id - doesn't misread "same workout, fresh
    // composition" as "a new workout started" and stomp the just-restored sheet state.
    var lastResetWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(inProgressWorkoutId) {
        if (inProgressWorkoutId != null && inProgressWorkoutId != lastResetWorkoutId) {
            activeWorkoutSheetState.resetToCollapsed()
        }
        lastResetWorkoutId = inProgressWorkoutId
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

    // Requested at app launch (not inside Active Workout) so it's always resolved before a
    // workout can start - otherwise the foreground service's first startForeground() call could
    // beat the permission grant, silently suppressing that first notification.
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
            // Only the width-cap Modifier value varies by posture (not composable structure) -
            // branching to different composable shapes per posture tore down the whole nav-host
            // subtree on rotation, resetting every rememberSaveable underneath. Tabletop can still
            // be wide (confirmed via a half-opened hinge state), so it's capped/centered like
            // Compact rather than stretched, using the same Medium-width breakpoint classify()
            // uses to promote to BookOrExpanded.
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
                // Overlaps (Box, not Column) so the sheet can grow to cover NavHost content when
                // expanded, confined to this content pane so it never draws over the nav bar/rail.
                Box(modifier = widthCapModifier) {
                    // Reserves the collapsed banner's footprint so content doesn't sit under it -
                    // the banner is a sibling overlay (so it can grow to cover the pane when
                    // expanded), with no other way to participate in NavHost's layout. Animated in
                    // step with the sheet's own transition so the two don't visually jump apart.
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
                    var lastWorkoutId by remember { mutableStateOf<String?>(null) }
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
                                // Fired directly from the sheet's confirm dialogs (not off the DB
                                // write) since this composable, not the sheet, owns the
                                // NavController.
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

