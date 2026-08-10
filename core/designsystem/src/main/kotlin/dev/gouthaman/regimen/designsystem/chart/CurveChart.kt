package dev.gouthaman.regimen.designsystem.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Like [LineChart], but the trend renders as a smooth curve through [points] instead of straight
 * segments - a separate component rather than a [LineChart] mode, since a smoothed curve can
 * overshoot between points, which only makes sense for dense, continuous data (e.g. a heart-rate
 * trace), not [LineChart]'s sparser discrete series.
 */
@Composable
fun CurveChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    valueFormatter: (Float) -> String = ::defaultCurveValueFormatter,
) {
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(points) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, animationSpec = tween(650, easing = LinearOutSlowInEasing))
    }

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
            drawCurveAxisChart(
                points = points,
                line = lineColor,
                fill = fillColor,
                strokeWidthPx = 3.dp.toPx(),
                dotRadiusPx = 4.dp.toPx(),
                gridColor = gridColor,
                labelStyle = labelStyle,
                textMeasurer = textMeasurer,
                valueFormatter = valueFormatter,
                reveal = reveal.value,
            )
        }
    }
}

private fun defaultCurveValueFormatter(value: Float): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/** Cubic-Bezier approximation of a Catmull-Rom spline through [offsets], clamping the neighbor
 * lookup at both ends. */
private fun catmullRomPath(offsets: List<Offset>): Path {
    val path = Path()
    path.moveTo(offsets.first().x, offsets.first().y)
    for (i in 0 until offsets.size - 1) {
        val p0 = offsets.getOrElse(i - 1) { offsets[i] }
        val p1 = offsets[i]
        val p2 = offsets[i + 1]
        val p3 = offsets.getOrElse(i + 2) { offsets[i + 1] }
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
    return path
}

private fun DrawScope.drawCurveSparkline(
    points: List<Float>,
    line: Color,
    fill: Color?,
    strokeWidthPx: Float,
    dotRadiusPx: Float,
    topPad: Float,
    bottomPad: Float,
    domainMin: Float,
    domainMax: Float,
    reveal: Float,
) {
    if (points.isEmpty() || reveal <= 0f) return

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
        drawCircle(
            line,
            radius = dotRadiusPx,
            center = Offset(xAt(0), yAt(points[0])),
            alpha = reveal,
        )
        return
    }

    val offsets = points.mapIndexed { i, v -> Offset(xAt(i), yAt(v)) }
    val curve = catmullRomPath(offsets)

    clipRect(right = w * reveal) {
        if (fill != null) {
            val bottomLine = yAt(domainMin)
            val area = Path().apply {
                addPath(curve)
                lineTo(offsets.last().x, bottomLine)
                lineTo(offsets.first().x, bottomLine)
                close()
            }
            drawPath(area, fill)
        }

        drawPath(curve, line, style = Stroke(width = strokeWidthPx))

        drawCircle(line, radius = dotRadiusPx, center = offsets.first())
        drawCircle(line, radius = dotRadiusPx, center = offsets.last())
    }
}

private fun DrawScope.drawCurveAxisChart(
    points: List<Float>,
    line: Color,
    fill: Color?,
    strokeWidthPx: Float,
    dotRadiusPx: Float,
    gridColor: Color,
    labelStyle: TextStyle,
    textMeasurer: TextMeasurer,
    valueFormatter: (Float) -> String,
    reveal: Float,
) {
    if (points.isEmpty()) return

    val domainMin = points.min()
    val domainMax = points.max()
    val flat = domainMax == domainMin

    val gap = 4.dp.toPx()
    val gridStroke = 1.dp.toPx()
    val fadedGrid = gridColor.copy(alpha = gridColor.alpha * reveal)
    val fadedLabelStyle =
        labelStyle.copy(color = labelStyle.color.copy(alpha = labelStyle.color.alpha * reveal))

    if (flat) {
        val label = textMeasurer.measure(valueFormatter(domainMax), fadedLabelStyle)
        val topPad = dotRadiusPx + strokeWidthPx + label.size.height + gap
        val bottomPad = topPad
        val midY = topPad + (size.height - topPad - bottomPad) / 2f
        drawLine(fadedGrid, Offset(0f, midY), Offset(size.width, midY), strokeWidth = gridStroke)
        drawText(label, topLeft = Offset(0f, midY - label.size.height - gap))
        drawCurveSparkline(
            points, line, fill, strokeWidthPx, dotRadiusPx,
            topPad = topPad, bottomPad = bottomPad, domainMin = domainMin, domainMax = domainMax,
            reveal = reveal,
        )
        return
    }

    val maxLabel = textMeasurer.measure(valueFormatter(domainMax), fadedLabelStyle)
    val minLabel = textMeasurer.measure(valueFormatter(domainMin), fadedLabelStyle)
    val labelHeight = maxOf(maxLabel.size.height, minLabel.size.height)
    val topPad = dotRadiusPx + strokeWidthPx + labelHeight + gap
    val bottomPad = topPad

    drawLine(fadedGrid, Offset(0f, topPad), Offset(size.width, topPad), strokeWidth = gridStroke)
    drawLine(
        fadedGrid,
        Offset(0f, size.height - bottomPad),
        Offset(size.width, size.height - bottomPad),
        strokeWidth = gridStroke,
    )
    drawText(maxLabel, topLeft = Offset(0f, topPad - maxLabel.size.height - gap))
    drawText(minLabel, topLeft = Offset(0f, size.height - bottomPad + gap))

    drawCurveSparkline(
        points, line, fill, strokeWidthPx, dotRadiusPx,
        topPad = topPad, bottomPad = bottomPad, domainMin = domainMin, domainMax = domainMax,
        reveal = reveal,
    )
}
