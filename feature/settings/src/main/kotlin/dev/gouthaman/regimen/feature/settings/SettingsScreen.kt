package dev.gouthaman.regimen.feature.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.common.accountFromSettingsTransitionKey
import dev.gouthaman.regimen.common.exerciseLibraryFromSettingsTransitionKey
import dev.gouthaman.regimen.common.healthConnectFromSettingsTransitionKey
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.component.EnumDropdown
import dev.gouthaman.regimen.designsystem.component.SectionHeader
import dev.gouthaman.regimen.designsystem.component.ThemeModeSelector
import dev.gouthaman.regimen.designsystem.component.UnitSystemSelector
import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.model.MaxWorkoutDuration
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.UserPreferences
import kotlin.math.roundToInt

/** Bounds for the rest-timer default slider (seconds). */
private const val REST_MIN_SEC = 30
private const val REST_MAX_SEC = 300
private const val REST_STEP_SEC = 15

@Composable
fun SettingsScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    onOpenExerciseLibrary: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenAccount: () -> Unit = {},
    onOpenHealthConnect: () -> Unit = {},
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()

    SettingsScreen(
        prefs = prefs,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        modifier = modifier,
        onWeightUnitChange = viewModel::setWeightUnit,
        onDistanceUnitChange = viewModel::setDistanceUnit,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onRestDefaultChange = viewModel::setRestDefaultSec,
        onRestChimeEnabledChange = viewModel::setRestChimeEnabled,
        onMaxWorkoutDurationChange = viewModel::setMaxWorkoutDuration,
        onOpenExerciseLibrary = onOpenExerciseLibrary,
        account = account,
        onOpenAccount = onOpenAccount,
        onOpenHealthConnect = onOpenHealthConnect,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SettingsScreen(
    prefs: UserPreferences,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    onWeightUnitChange: (UnitSystem) -> Unit = {},
    onDistanceUnitChange: (UnitSystem) -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    onRestDefaultChange: (Int) -> Unit = {},
    onRestChimeEnabledChange: (Boolean) -> Unit = {},
    onMaxWorkoutDurationChange: (MaxWorkoutDuration) -> Unit = {},
    onOpenExerciseLibrary: () -> Unit = {},
    account: AuthAccount? = null,
    onOpenAccount: () -> Unit = {},
    onOpenHealthConnect: () -> Unit = {},
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
                SectionHeader(
                    stringResource(R.string.settings_preferences_header),
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )

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
                    content = { Text(stringResource(R.string.settings_dynamic_color_label)) },
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
                    content = { Text(stringResource(R.string.settings_rest_timer_sound_headline)) },
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

                SettingRow(
                    headline = stringResource(R.string.settings_max_workout_time_headline),
                    supporting = stringResource(R.string.settings_max_workout_time_description),
                ) {
                    EnumDropdown(
                        label = stringResource(R.string.settings_max_workout_time_headline),
                        options = MaxWorkoutDuration.entries,
                        selected = prefs.maxWorkoutDuration,
                        optionLabel = { it.label() },
                        onSelect = onMaxWorkoutDurationChange,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                HorizontalDivider()
                SectionHeader(
                    stringResource(R.string.settings_library_data_header),
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )

                NavRow(
                    headline = stringResource(R.string.settings_exercise_library_headline),
                    supporting = stringResource(R.string.settings_exercise_library_description),
                    icon = Icons.Filled.FitnessCenter,
                    enabled = true,
                    onClick = onOpenExerciseLibrary,
                    modifier = with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = exerciseLibraryFromSettingsTransitionKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    },
                )

                NavRow(
                    headline = stringResource(R.string.settings_account_headline),
                    supporting = account?.email
                        ?: stringResource(R.string.settings_account_signed_out),
                    icon = Icons.Filled.AccountCircle,
                    enabled = true,
                    onClick = onOpenAccount,
                    modifier = with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = accountFromSettingsTransitionKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    },
                )

                NavRow(
                    headline = stringResource(R.string.settings_health_connect_headline),
                    supporting = stringResource(R.string.settings_health_connect_description),
                    icon = Icons.Filled.MonitorHeart,
                    enabled = true,
                    onClick = onOpenHealthConnect,
                    modifier = with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = healthConnectFromSettingsTransitionKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    },
                )
            }
        }
    }
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
    modifier: Modifier = Modifier,
) {
    ListItem(
        content = { Text(headline) },
        supportingContent = { Text(supporting) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            if (enabled) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        },
        enabled = enabled,
        modifier = modifier.then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

@Composable
private fun MaxWorkoutDuration.label(): String = when (this) {
    MaxWorkoutDuration.OFF -> stringResource(R.string.max_workout_time_off)
    MaxWorkoutDuration.FOUR_HOURS -> stringResource(R.string.max_workout_time_4h)
    MaxWorkoutDuration.SIX_HOURS -> stringResource(R.string.max_workout_time_6h)
    MaxWorkoutDuration.EIGHT_HOURS -> stringResource(R.string.max_workout_time_8h)
}

/** mm:ss, e.g. 90 -> "1:30". */
private fun formatDuration(totalSec: Int): String {
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}
