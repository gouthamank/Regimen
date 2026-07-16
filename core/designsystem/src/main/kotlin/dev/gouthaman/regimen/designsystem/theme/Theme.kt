package dev.gouthaman.regimen.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.gouthaman.regimen.domain.model.ThemeMode

// Fixed roles are theme-invariant by design, so both schemes share the same Fixed values.
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    primaryFixed = Purple90,
    primaryFixedDim = Purple80,
    onPrimaryFixed = Purple10,
    onPrimaryFixedVariant = Purple30,
    secondaryFixed = PurpleGrey90,
    secondaryFixedDim = PurpleGrey80,
    onSecondaryFixed = PurpleGrey10,
    onSecondaryFixedVariant = PurpleGrey30,
    tertiaryFixed = Pink90,
    tertiaryFixedDim = Pink80,
    onTertiaryFixed = Pink10,
    onTertiaryFixedVariant = Pink30,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    primaryFixed = Purple90,
    primaryFixedDim = Purple80,
    onPrimaryFixed = Purple10,
    onPrimaryFixedVariant = Purple30,
    secondaryFixed = PurpleGrey90,
    secondaryFixedDim = PurpleGrey80,
    onSecondaryFixed = PurpleGrey10,
    onSecondaryFixedVariant = PurpleGrey30,
    tertiaryFixed = Pink90,
    tertiaryFixedDim = Pink80,
    onTertiaryFixed = Pink10,
    onTertiaryFixedVariant = Pink30,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RegimenTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Keep system-bar icon appearance in sync with the *app* theme, not the OS setting -
    // enableEdgeToEdge()'s default auto style keys off system dark mode, so an in-app override
    // would otherwise leave dark-on-dark (or light-on-light) bar icons.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            // In 3-button nav mode, enableEdgeToEdge() paints a fixed translucent scrim behind
            // the system nav bar instead of the app's own bottom-bar color (gesture nav is
            // already transparent there, letting NavigationBar's surfaceContainer show through).
            // Match it explicitly so both modes look the same.
            window.navigationBarColor = colorScheme.surfaceContainer.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    // Expressive theme: adopts Material 3 Expressive's motion scheme (springier, more
    // characterful transitions) while keeping our existing color scheme and typography.
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        content = content
    )
}
