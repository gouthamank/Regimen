package dev.gouthaman.regimen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.gouthaman.regimen.designsystem.adaptive.ProvideRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.theme.RegimenTheme
import dev.gouthaman.regimen.feature.onboarding.OnboardingScreen
import dev.gouthaman.regimen.ui.MainViewModel
import dev.gouthaman.regimen.ui.RegimenApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Set from onCreate/onNewIntent, consumed (and cleared) once RegimenApp navigates to it -
    // see extractWorkoutDeepLink. Compose state (not a plain var) so a warm-start onNewIntent
    // triggers recomposition/navigation without needing to recreate the Activity.
    private var pendingWorkoutDeepLink by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until prefs load, so we never flash onboarding at an already-onboarded
        // user or render before the theme is known.
        splashScreen.setKeepOnScreenCondition { !viewModel.uiState.value.loaded }

        pendingWorkoutDeepLink = extractWorkoutDeepLink(intent)

        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val prefs = uiState.prefs
            RegimenTheme(themeMode = prefs.themeMode, dynamicColor = prefs.dynamicColor) {
                ProvideRegimenWindowInfo {
                    when {
                        // Splash still covers this frame; nothing to draw yet.
                        !uiState.loaded -> Unit
                        !prefs.onboarded -> OnboardingScreen()
                        else -> RegimenApp(
                            deepLinkWorkoutId = pendingWorkoutDeepLink,
                            onDeepLinkConsumed = { pendingWorkoutDeepLink = null },
                        )
                    }
                }
            }
        }
    }

    // The activity is launchMode="singleTop" (single-Activity app, always at the top of its own
    // task), so a notification tap while already running arrives here instead of a fresh onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingWorkoutDeepLink = extractWorkoutDeepLink(intent)
    }

    private fun extractWorkoutDeepLink(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_WORKOUT_ID, -1L)?.takeIf { it != -1L }

    companion object {
        /** Deep-links a workout's in-progress/rest-complete notification tap straight into
         * Active Workout (see RegimenApp's deepLinkWorkoutId handling) instead of just opening
         * the app to whatever screen it last showed. */
        const val EXTRA_WORKOUT_ID = "dev.gouthaman.regimen.extra.WORKOUT_ID"
    }
}
