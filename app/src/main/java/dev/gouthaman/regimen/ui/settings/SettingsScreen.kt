package dev.gouthaman.regimen.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.R
import dev.gouthaman.regimen.data.prefs.UserPreferences
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.ui.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.ui.adaptive.RegimenPosture
import kotlin.math.roundToInt

/** Bounds for the rest-timer default slider (seconds). */
private const val REST_MIN_SEC = 30
private const val REST_MAX_SEC = 300
private const val REST_STEP_SEC = 15

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenExerciseLibrary: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    SettingsScreen(
        prefs = prefs,
        modifier = modifier,
        onWeightUnitChange = viewModel::setWeightUnit,
        onDistanceUnitChange = viewModel::setDistanceUnit,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onRestDefaultChange = viewModel::setRestDefaultSec,
        onRestChimeEnabledChange = viewModel::setRestChimeEnabled,
        onOpenExerciseLibrary = onOpenExerciseLibrary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: UserPreferences,
    modifier: Modifier = Modifier,
    onWeightUnitChange: (UnitSystem) -> Unit = {},
    onDistanceUnitChange: (UnitSystem) -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    onRestDefaultChange: (Int) -> Unit = {},
    onRestChimeEnabledChange: (Boolean) -> Unit = {},
    onOpenExerciseLibrary: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val windowInfo = LocalRegimenWindowInfo.current
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(modifier.nestedScroll(scrollBehavior.nestedScrollConnection)),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentModifier = if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
                Modifier
                    .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                    .fillMaxSize()
            } else {
                Modifier.fillMaxSize()
            }
            Column(
                modifier = contentModifier.verticalScroll(rememberScrollState()),
            ) {
                SectionHeader(stringResource(R.string.settings_preferences_header))

                SettingRow(headline = stringResource(R.string.settings_weight_unit_headline)) {
                    UnitSystemSelector(prefs.weightUnit, onWeightUnitChange, weightLabels = true)
                }

                SettingRow(headline = stringResource(R.string.settings_distance_unit_headline)) {
                    UnitSystemSelector(
                        prefs.distanceUnit,
                        onDistanceUnitChange,
                        weightLabels = false,
                    )
                }

                SettingRow(headline = stringResource(R.string.settings_theme_headline)) {
                    ThemeModeSelector(prefs.themeMode, onThemeModeChange)
                }

                val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color_label)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (dynamicAvailable) R.string.settings_dynamic_color_description_available
                                else R.string.settings_dynamic_color_description_unavailable,
                            ),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = prefs.dynamicColor && dynamicAvailable,
                            onCheckedChange = onDynamicColorChange,
                            enabled = dynamicAvailable,
                        )
                    },
                )

                RestTimerRow(prefs.restDefaultSec, onRestDefaultChange)

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_rest_timer_sound_headline)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_rest_timer_sound_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = prefs.restChimeEnabled,
                            onCheckedChange = onRestChimeEnabledChange,
                        )
                    },
                )

                HorizontalDivider()
                SectionHeader(stringResource(R.string.settings_library_data_header))

                NavRow(
                    headline = stringResource(R.string.settings_exercise_library_headline),
                    supporting = stringResource(R.string.settings_exercise_library_description),
                    icon = Icons.Filled.FitnessCenter,
                    enabled = true,
                    onClick = onOpenExerciseLibrary,
                )
                NavRow(
                    headline = stringResource(R.string.settings_export_data_headline),
                    supporting = stringResource(R.string.settings_export_data_description),
                    icon = Icons.Filled.Upload,
                    enabled = false,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** A settings row with a headline (+ optional supporting text) above a full-width control. */
@Composable
private fun SettingRow(
    headline: String,
    supporting: String? = null,
    control: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(headline, style = MaterialTheme.typography.bodyLarge)
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.padding(top = 8.dp)) { control() }
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
                                UnitSystem.METRIC -> R.string.unit_system_metric_weight
                                UnitSystem.IMPERIAL -> R.string.unit_system_imperial_weight
                            }
                        } else {
                            when (option) {
                                UnitSystem.METRIC -> R.string.unit_system_metric_distance
                                UnitSystem.IMPERIAL -> R.string.unit_system_imperial_distance
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
                            ThemeMode.LIGHT -> R.string.theme_mode_light
                            ThemeMode.DARK -> R.string.theme_mode_dark
                            ThemeMode.SYSTEM -> R.string.theme_mode_system
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun RestTimerRow(restDefaultSec: Int, onChange: (Int) -> Unit) {
    // Track the slider position locally while dragging; persist only on release.
    var sliderValue by remember(restDefaultSec) { mutableFloatStateOf(restDefaultSec.toFloat()) }
    val steps = (REST_MAX_SEC - REST_MIN_SEC) / REST_STEP_SEC - 1

    SettingRow(
        headline = stringResource(R.string.settings_rest_timer_default_headline),
        supporting = formatDuration(sliderValue.roundToInt()),
    ) {
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChange(sliderValue.roundToInt()) },
            valueRange = REST_MIN_SEC.toFloat()..REST_MAX_SEC.toFloat(),
            steps = steps,
        )
    }
}

@Composable
private fun NavRow(
    headline: String,
    supporting: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor =
        if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            if (enabled) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        },
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

/** mm:ss, e.g. 90 -> "1:30". */
private fun formatDuration(totalSec: Int): String {
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}
