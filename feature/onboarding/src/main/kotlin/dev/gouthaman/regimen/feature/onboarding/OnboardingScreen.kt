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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.common.text
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.adaptive.RegimenWindowInfo
import dev.gouthaman.regimen.designsystem.component.ThemeModeSelector
import dev.gouthaman.regimen.designsystem.component.UnitSystemSelector
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.UserPreferences
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3
private val PageSpacing = 24.dp

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val signInState by viewModel.signInState.collectAsStateWithLifecycle()
    val windowInfo = LocalRegimenWindowInfo.current
    OnboardingScreen(
        prefs = prefs,
        signInState = signInState,
        windowInfo = windowInfo,
        onWeightUnitChange = viewModel::setWeightUnit,
        onDistanceUnitChange = viewModel::setDistanceUnit,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onSignIn = viewModel::signIn,
        onFinish = { viewModel.finish() },
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    prefs: UserPreferences,
    signInState: OnboardingSignInState,
    windowInfo: RegimenWindowInfo,
    onWeightUnitChange: (UnitSystem) -> Unit,
    onDistanceUnitChange: (UnitSystem) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onSignIn: () -> Unit,
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

            1 -> AppearancePage(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor,
                onThemeModeChange = onThemeModeChange,
                onDynamicColorChange = onDynamicColorChange,
            )

            else -> SignInPage(
                state = signInState,
                onSignIn = onSignIn,
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

        PagerDots(current = pagerState.currentPage)

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
                PagerDots(current = pagerState.currentPage)
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

@Composable
private fun SignInPage(
    state: OnboardingSignInState,
    onSignIn: () -> Unit,
) {
    OnboardingPage(
        title = stringResource(R.string.onboarding_signin_title),
        subtitle = stringResource(R.string.onboarding_signin_subtitle),
    ) {
        val account = state.account
        if (account == null) {
            Button(onClick = onSignIn, enabled = state.isSignInAvailable && !state.isSigningIn) {
                if (state.isSigningIn) {
                    ButtonProgressIndicator()
                } else {
                    Text(stringResource(R.string.onboarding_signin_button))
                }
            }

            if (!state.isSignInAvailable) {
                Text(
                    stringResource(R.string.onboarding_signin_play_services_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            state.errorReason?.let {
                Text(
                    it.text(),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            Text(
                stringResource(
                    R.string.onboarding_signin_signed_in_message,
                    account.displayName ?: account.email.orEmpty(),
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Replaces a button's label with a spinner sized to sit on the same content baseline, matching
 * the button's own content color so it looks native to filled and text buttons alike. Mirrors
 * `:feature:account`'s `AccountScreen.kt` helper of the same shape. */
@Composable
private fun ButtonProgressIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = LocalContentColor.current,
    )
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

@Composable
private fun PagerDots(current: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PAGE_COUNT) { index ->
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
