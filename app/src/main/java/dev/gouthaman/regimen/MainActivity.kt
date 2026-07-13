package dev.gouthaman.regimen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
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

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until prefs load, so we never flash onboarding at an already-onboarded
        // user or render before the theme is known.
        splashScreen.setKeepOnScreenCondition { !viewModel.uiState.value.loaded }

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
                        else -> RegimenApp()
                    }
                }
            }
        }
    }
}
