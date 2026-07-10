package dev.gouthaman.regimen.ui.adaptive

import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.window.core.layout.WindowSizeClass

/**
 * Regimen's simplified layout classification for adaptive screens — not a 1:1 mirror of
 * [androidx.compose.material3.adaptive.Posture]; this answers "what arrangement should this
 * screen use," not "what shape is the device." Add more cases only when a screen actually
 * needs one.
 */
enum class RegimenPosture {
    Compact,
    Tabletop,
    BookOrExpanded,
}

data class RegimenWindowInfo(
    val posture: RegimenPosture,
    // Escape hatch for screens that need the raw windowSizeClass/windowPosture directly.
    val adaptiveInfo: WindowAdaptiveInfo,
)

val LocalRegimenWindowInfo = staticCompositionLocalOf<RegimenWindowInfo> {
    error("LocalRegimenWindowInfo not provided — wrap the content root in ProvideRegimenWindowInfo()")
}

@Composable
fun ProvideRegimenWindowInfo(content: @Composable () -> Unit) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val info = remember(adaptiveInfo) { RegimenWindowInfo(classify(adaptiveInfo), adaptiveInfo) }
    CompositionLocalProvider(LocalRegimenWindowInfo provides info, content = content)
}

private fun classify(info: WindowAdaptiveInfo): RegimenPosture {
    val posture = info.windowPosture
    return when {
        posture.isTabletop -> RegimenPosture.Tabletop
        posture.separatingVerticalHingeBounds.isNotEmpty() -> RegimenPosture.BookOrExpanded
        info.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            RegimenPosture.BookOrExpanded

        else -> RegimenPosture.Compact
    }
}
