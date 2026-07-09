package dev.gouthaman.regimen.ui.onboarding

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gouthaman.regimen.data.prefs.UserPreferences
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 2

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    OnboardingScreen(
        prefs = prefs,
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

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
        ) {
            // Skip is always available, per the spec (onboarding is optional).
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinish) { Text("Skip") }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
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

            PagerDots(current = pagerState.currentPage, count = PAGE_COUNT)

            Button(
                onClick = {
                    if (onLastPage) onFinish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(if (onLastPage) "Get started" else "Next")
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
        title = "Welcome to Regimen",
        subtitle = "Track your workouts, all on your device. First, which units do you use?",
    ) {
        Text(
            "Weight",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        UnitSystemSelector(weightUnit, onWeightUnitChange, weightLabels = true)
        Text(
            "Distance",
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
        title = "Make it yours",
        subtitle = "Choose a theme. You can change any of this later in Profile.",
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
                    Text("Dynamic color", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Use colors from your wallpaper",
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
                    if (weightLabels) {
                        when (option) {
                            UnitSystem.METRIC -> "Metric (kg)"
                            UnitSystem.IMPERIAL -> "Imperial (lb)"
                        }
                    } else {
                        when (option) {
                            UnitSystem.METRIC -> "Metric (km)"
                            UnitSystem.IMPERIAL -> "Imperial (mi)"
                        }
                    },
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
                    when (option) {
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.SYSTEM -> "System"
                    },
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
