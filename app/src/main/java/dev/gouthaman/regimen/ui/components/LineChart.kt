package dev.gouthaman.regimen.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A minimal line chart plotting [points] (already sorted by x, e.g. chronological). Self-contained
 * on [Canvas] — no third-party charting dependency — so it can be shared by Body Measurements
 * (trend) and later the Progress frequency chart.
 *
 * X is treated as evenly spaced by index (dates aren't laid out to scale in v1); Y is scaled to the
 * min/max of the data with a little headroom. A single point renders as a lone dot.
 */
@Composable
fun LineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            drawTrend(
                points,
                lineColor,
                fillColor,
                strokeWidthPx = 3.dp.toPx(),
                dotRadiusPx = 4.dp.toPx()
            )
        }
    }
}

/**
 * Compact inline trend used in list rows. No fill, thinner stroke, fixed small height.
 */
@Composable
fun Sparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier.height(height)) {
        drawTrend(
            points,
            lineColor,
            fill = null,
            strokeWidthPx = 2.dp.toPx(),
            dotRadiusPx = 2.5.dp.toPx()
        )
    }
}

private fun DrawScope.drawTrend(
    points: List<Float>,
    line: Color,
    fill: Color?,
    strokeWidthPx: Float,
    dotRadiusPx: Float,
) {
    if (points.isEmpty()) return

    val w = size.width
    val h = size.height
    // Inset so strokes/dots aren't clipped at the edges.
    val pad = dotRadiusPx + strokeWidthPx
    val usableW = (w - pad * 2).coerceAtLeast(1f)
    val usableH = (h - pad * 2).coerceAtLeast(1f)

    val min = points.min()
    val max = points.max()
    val range = (max - min)

    fun xAt(index: Int): Float =
        if (points.size == 1) w / 2f else pad + usableW * index / (points.size - 1)

    fun yAt(value: Float): Float =
        if (range == 0f) h / 2f else pad + usableH * (1f - (value - min) / range)

    if (points.size == 1) {
        drawCircle(line, radius = dotRadiusPx, center = Offset(xAt(0), yAt(points[0])))
        return
    }

    val offsets = points.mapIndexed { i, v -> Offset(xAt(i), yAt(v)) }

    if (fill != null) {
        val area = Path().apply {
            moveTo(offsets.first().x, h - pad)
            offsets.forEach { lineTo(it.x, it.y) }
            lineTo(offsets.last().x, h - pad)
            close()
        }
        drawPath(area, fill)
    }

    val path = Path().apply {
        moveTo(offsets.first().x, offsets.first().y)
        offsets.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, line, style = Stroke(width = strokeWidthPx))

    // Endpoint dots to anchor the eye on sparse data.
    drawCircle(line, radius = dotRadiusPx, center = offsets.first())
    drawCircle(line, radius = dotRadiusPx, center = offsets.last())
}
