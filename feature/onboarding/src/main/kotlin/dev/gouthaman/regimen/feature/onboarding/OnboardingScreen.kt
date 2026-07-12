package dev.gouthaman.regimen.feature.onboarding

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.designsystem.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.RegimenPosture
import dev.gouthaman.regimen.designsystem.RegimenWindowInfo
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.UserPreferences
import kotlinx.coroutines.launch
import dev.gouthaman.regimen.common.R as CommonR

private const val PAGE_COUNT = 2
private val PageSpacing = 24.dp

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val windowInfo = LocalRegimenWindowInfo.current
    OnboardingScreen(
        prefs = prefs,
        windowInfo = windowInfo,
        onWeightUnitChange = viewModel::setWeightUnit,
        onDistanceUnitChange = viewModel::setDistanceUnit,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onFinish = { viewModel.finish() },
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    prefs: UserPreferences,
    windowInfo: RegimenWindowInfo,
    onWeightUnitChange: (UnitSystem) -> Unit,
    onDistanceUnitChange: (UnitSystem) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val onLastPage = pagerState.currentPage == PAGE_COUNT - 1

    val pagerContent: @Composable (Int) -> Unit = { page ->
        when (page) {
            0 -> UnitsPage(
                weightUnit = prefs.weightUnit,
                distanceUnit = prefs.distanceUnit,
                onWeightUnitChange = onWeightUnitChange,
                onDistanceUnitChange = onDistanceUnitChange,
            )

            else -> AppearancePage(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor,
                onThemeModeChange = onThemeModeChange,
                onDynamicColorChange = onDynamicColorChange,
            )
        }
    }
    val onNextOrFinish: () -> Unit = {
        if (onLastPage) onFinish()
        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (windowInfo.posture) {
            RegimenPosture.Compact -> LinearOnboardingLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(24.dp),
                pagerState = pagerState,
                pagerContent = pagerContent,
                onLastPage = onLastPage,
                onNextOrFinish = onNextOrFinish,
                onFinish = onFinish,
            )

            RegimenPosture.BookOrExpanded -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                LinearOnboardingLayout(
                    modifier = Modifier
                        .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                        .fillMaxHeight()
                        .padding(24.dp),
                    pagerState = pagerState,
                    pagerContent = pagerContent,
                    onLastPage = onLastPage,
                    onNextOrFinish = onNextOrFinish,
                    onFinish = onFinish,
                )
            }

            RegimenPosture.Tabletop -> TabletopOnboardingLayout(
                pagerState = pagerState,
                pagerContent = pagerContent,
                onLastPage = onLastPage,
                onNextOrFinish = onNextOrFinish,
                onFinish = onFinish,
            )
        }
    }
}

/** Compact and book/expanded postures share this single-column layout; only the outer modifier
 *  (full-bleed vs. width-capped and centered) differs between them. */
@Composable
private fun LinearOnboardingLayout(
    modifier: Modifier,
    pagerState: PagerState,
    pagerContent: @Composable (Int) -> Unit,
    onLastPage: Boolean,
    onNextOrFinish: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(modifier = modifier) {
        // Skip is always available, per the spec (onboarding is optional).
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_skip)) }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            pageSpacing = PageSpacing,
        ) { page -> pagerContent(page) }

        PagerDots(current = pagerState.currentPage, count = PAGE_COUNT)

        Button(
            onClick = onNextOrFinish,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text(stringResource(if (onLastPage) R.string.onboarding_get_started else R.string.onboarding_next))
        }
    }
}

/** Tabletop posture: content lives in the top pane, above the hinge; navigation controls sit
 *  in the bottom pane, like a laptop keyboard deck, so nothing renders across the hinge. */
@Composable
private fun TabletopOnboardingLayout(
    pagerState: PagerState,
    pagerContent: @Composable (Int) -> Unit,
    onLastPage: Boolean,
    onNextOrFinish: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_skip)) }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    pageSpacing = PageSpacing,
                ) { page -> pagerContent(page) }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PagerDots(current = pagerState.currentPage, count = PAGE_COUNT)
                Button(
                    onClick = onNextOrFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Text(stringResource(if (onLastPage) R.string.onboarding_get_started else R.string.onboarding_next))
                }
            }
        }
    }
}

@Composable
private fun UnitsPage(
    weightUnit: UnitSystem,
    distanceUnit: UnitSystem,
    onWeightUnitChange: (UnitSystem) -> Unit,
    onDistanceUnitChange: (UnitSystem) -> Unit,
) {
    OnboardingPage(
        title = stringResource(R.string.onboarding_units_title),
        subtitle = stringResource(R.string.onboarding_units_subtitle),
    ) {
        Text(
            stringResource(R.string.onboarding_weight_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        UnitSystemSelector(weightUnit, onWeightUnitChange, weightLabels = true)
        Text(
            stringResource(R.string.onboarding_distance_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        UnitSystemSelector(distanceUnit, onDistanceUnitChange, weightLabels = false)
    }
}

@Composable
private fun AppearancePage(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    OnboardingPage(
        title = stringResource(R.string.onboarding_appearance_title),
        subtitle = stringResource(R.string.onboarding_appearance_subtitle),
    ) {
        ThemeModeSelector(themeMode, onThemeModeChange)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.onboarding_dynamic_color_label),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.onboarding_dynamic_color_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
            }
        }
    }
}

/** Shared layout for an onboarding page: centered title + subtitle above a control block. */
@Composable
private fun OnboardingPage(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitSystemSelector(
    selected: UnitSystem,
    onChange: (UnitSystem) -> Unit,
    weightLabels: Boolean,
) {
    val options = UnitSystem.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(
                    stringResource(
                        if (weightLabels) {
                            when (option) {
                                UnitSystem.METRIC -> CommonR.string.unit_system_metric_weight
                                UnitSystem.IMPERIAL -> CommonR.string.unit_system_imperial_weight
                            }
                        } else {
                            when (option) {
                                UnitSystem.METRIC -> CommonR.string.unit_system_metric_distance
                                UnitSystem.IMPERIAL -> CommonR.string.unit_system_imperial_distance
                            }
                        },
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(selected: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val options = ThemeMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(
                    stringResource(
                        when (option) {
                            ThemeMode.LIGHT -> CommonR.string.theme_mode_light
                            ThemeMode.DARK -> CommonR.string.theme_mode_dark
                            ThemeMode.SYSTEM -> CommonR.string.theme_mode_system
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun PagerDots(current: Int, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) 24.dp else 8.dp, label = "dotWidth")
            val color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
