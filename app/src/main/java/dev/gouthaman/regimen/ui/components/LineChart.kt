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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A minimal line chart plotting [points] (already sorted by x, e.g. chronological). Self-contained
 * on [Canvas] — no third-party charting dependency — so it can be shared by Body Measurements
 * (trend), Progress (frequency), and Home (frequency + bodyweight).
 *
 * X is treated as evenly spaced by index (dates aren't laid out to scale in v1). Y is scaled to
 * the value range and always labeled with a top/bottom gridline so the scale reads on its own
 * rather than only relative to itself — without this, a flat series is indistinguishable from any
 * other flat series regardless of its actual value (including all-zero). A single point renders as
 * a lone dot.
 *
 * [zeroBaseline] pins the bottom of the Y range to 0 instead of the data minimum. Set this for
 * count-style data (e.g. workouts/week): otherwise a steady "1 every week" reads as a flat line at
 * the very bottom of the chart (identical to all-zero), and a single week that bumps to 2 renders
 * as a full-height spike instead of the minor blip it is.
 */
@Composable
fun LineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    zeroBaseline: Boolean = false,
    valueFormatter: (Float) -> String = ::defaultValueFormatter,
) {
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()
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
            drawAxisChart(
                points = points,
                line = lineColor,
                fill = fillColor,
                strokeWidthPx = 3.dp.toPx(),
                dotRadiusPx = 4.dp.toPx(),
                zeroBaseline = zeroBaseline,
                gridColor = gridColor,
                labelStyle = labelStyle,
                textMeasurer = textMeasurer,
                valueFormatter = valueFormatter,
            )
        }
    }
}

/**
 * Compact inline trend used in list rows. No fill, thinner stroke, fixed small height, and no
 * axis/gridlines — it's a glanceable indicator next to a value that's already shown as text.
 */
@Composable
fun Sparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier.height(height)) {
        drawSparkline(
            points,
            lineColor,
            fill = null,
            strokeWidthPx = 2.dp.toPx(),
            dotRadiusPx = 2.5.dp.toPx(),
        )
    }
}

private fun defaultValueFormatter(value: Float): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/** Plain trend line/fill with no axis — shared core used by [drawAxisChart]. */
private fun DrawScope.drawSparkline(
    points: List<Float>,
    line: Color,
    fill: Color?,
    strokeWidthPx: Float,
    dotRadiusPx: Float,
    topPad: Float = dotRadiusPx + strokeWidthPx,
    bottomPad: Float = dotRadiusPx + strokeWidthPx,
    domainMin: Float = points.minOrNull() ?: 0f,
    domainMax: Float = points.maxOrNull() ?: 0f,
) {
    if (points.isEmpty()) return

    val w = size.width
    val h = size.height
    val pad = dotRadiusPx + strokeWidthPx
    val range = domainMax - domainMin
    val usableW = (w - pad * 2).coerceAtLeast(1f)
    val usableH = (h - topPad - bottomPad).coerceAtLeast(1f)

    fun xAt(index: Int): Float =
        if (points.size == 1) w / 2f else pad + usableW * index / (points.size - 1)

    fun yAt(value: Float): Float =
        if (range == 0f) topPad + usableH / 2f else topPad + usableH * (1f - (value - domainMin) / range)

    if (points.size == 1) {
        drawCircle(line, radius = dotRadiusPx, center = Offset(xAt(0), yAt(points[0])))
        return
    }

    val offsets = points.mapIndexed { i, v -> Offset(xAt(i), yAt(v)) }

    if (fill != null) {
        val bottomLine = yAt(domainMin)
        val area = Path().apply {
            moveTo(offsets.first().x, bottomLine)
            offsets.forEach { lineTo(it.x, it.y) }
            lineTo(offsets.last().x, bottomLine)
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

/** Trend line/fill plus top/bottom gridlines and value labels so the Y scale is legible on its own. */
private fun DrawScope.drawAxisChart(
    points: List<Float>,
    line: Color,
    fill: Color?,
    strokeWidthPx: Float,
    dotRadiusPx: Float,
    zeroBaseline: Boolean,
    gridColor: Color,
    labelStyle: TextStyle,
    textMeasurer: TextMeasurer,
    valueFormatter: (Float) -> String,
) {
    if (points.isEmpty()) return

    val dataMin = points.min()
    val dataMax = points.max()
    val domainMin = if (zeroBaseline) minOf(0f, dataMin) else dataMin
    val domainMax = if (zeroBaseline) maxOf(0f, dataMax) else dataMax
    val flat = domainMax == domainMin

    val gap = 4.dp.toPx()
    val gridStroke = 1.dp.toPx()

    if (flat) {
        // A single value spans the whole domain: one gridline/label at the vertical middle.
        val label = textMeasurer.measure(valueFormatter(domainMax), labelStyle)
        val topPad = dotRadiusPx + strokeWidthPx + label.size.height + gap
        val bottomPad = topPad
        val midY = topPad + (size.height - topPad - bottomPad) / 2f
        drawLine(gridColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = gridStroke)
        drawText(label, topLeft = Offset(0f, midY - label.size.height - gap))
        drawSparkline(
            points, line, fill, strokeWidthPx, dotRadiusPx,
            topPad = topPad, bottomPad = bottomPad, domainMin = domainMin, domainMax = domainMax,
        )
        return
    }

    val maxLabel = textMeasurer.measure(valueFormatter(domainMax), labelStyle)
    val minLabel = textMeasurer.measure(valueFormatter(domainMin), labelStyle)
    val labelHeight = maxOf(maxLabel.size.height, minLabel.size.height)
    val topPad = dotRadiusPx + strokeWidthPx + labelHeight + gap
    val bottomPad = topPad

    drawLine(gridColor, Offset(0f, topPad), Offset(size.width, topPad), strokeWidth = gridStroke)
    drawLine(
        gridColor,
        Offset(0f, size.height - bottomPad),
        Offset(size.width, size.height - bottomPad),
        strokeWidth = gridStroke,
    )
    drawText(maxLabel, topLeft = Offset(0f, topPad - maxLabel.size.height - gap))
    drawText(minLabel, topLeft = Offset(0f, size.height - bottomPad + gap))

    drawSparkline(
        points, line, fill, strokeWidthPx, dotRadiusPx,
        topPad = topPad, bottomPad = bottomPad, domainMin = domainMin, domainMax = domainMax,
    )
}
