package dev.gouthaman.regimen.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gouthaman.regimen.data.prefs.UserPreferences
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
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
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(modifier.nestedScroll(scrollBehavior.nestedScrollConnection)),
        topBar = { MediumTopAppBar(title = { Text("Settings") }, scrollBehavior = scrollBehavior) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Preferences")

            SettingRow(headline = "Weight unit") {
                UnitSystemSelector(prefs.weightUnit, onWeightUnitChange, weightLabels = true)
            }

            SettingRow(headline = "Distance unit") {
                UnitSystemSelector(prefs.distanceUnit, onDistanceUnitChange, weightLabels = false)
            }

            SettingRow(headline = "Theme") {
                ThemeModeSelector(prefs.themeMode, onThemeModeChange)
            }

            val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ListItem(
                headlineContent = { Text("Dynamic color") },
                supportingContent = {
                    Text(
                        if (dynamicAvailable) "Use colors from your wallpaper"
                        else "Requires Android 12 or newer",
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
                headlineContent = { Text("Rest timer sound") },
                supportingContent = { Text("Chime when a rest period ends (vibration always on)") },
                trailingContent = {
                    Switch(
                        checked = prefs.restChimeEnabled,
                        onCheckedChange = onRestChimeEnabledChange,
                    )
                },
            )

            HorizontalDivider()
            SectionHeader("Library & data")

            NavRow(
                headline = "Exercise library",
                supporting = "Browse and add exercises",
                icon = Icons.Filled.FitnessCenter,
                enabled = true,
                onClick = onOpenExerciseLibrary,
            )
            NavRow(
                headline = "Export data",
                supporting = "JSON backup — planned",
                icon = Icons.Filled.Upload,
                enabled = false,
                onClick = {},
            )
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
private fun RestTimerRow(restDefaultSec: Int, onChange: (Int) -> Unit) {
    // Track the slider position locally while dragging; persist only on release.
    var sliderValue by remember(restDefaultSec) { mutableFloatStateOf(restDefaultSec.toFloat()) }
    val steps = (REST_MAX_SEC - REST_MIN_SEC) / REST_STEP_SEC - 1

    SettingRow(
        headline = "Rest timer default",
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
