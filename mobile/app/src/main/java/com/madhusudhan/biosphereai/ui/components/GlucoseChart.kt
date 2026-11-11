package com.madhusudhan.biosphereai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.madhusudhan.biosphereai.sim.CareEvent
import com.madhusudhan.biosphereai.sim.Reading
import com.madhusudhan.biosphereai.ui.theme.LocalBioColors
import kotlin.math.ceil
import kotlin.math.max

/**
 * Glucose history with target band, the next hour's forecast (dashed) and a lane
 * of care-event markers. Time runs left to right; the shaded right part is the future.
 */
@Composable
fun GlucoseChart(
    readings: List<Reading>,
    trajectory: List<Pair<Int, Double>>,
    events: List<CareEvent>,
    nowMinute: Int,
    windowHours: Int,
    lowLine: Double,
    highLine: Double,
    clock: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBioColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val nowColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier) {
        val left = 34.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val top = 8.dp.toPx()
        val lane = 18.dp.toPx()
        val axis = 18.dp.toPx()
        val bottom = size.height - axis - lane

        val xStart = nowMinute - windowHours * 60
        val xEnd = nowMinute + 60
        fun x(minute: Double) = left + ((minute - xStart) / (xEnd - xStart)).toFloat() * (right - left)

        val visible = readings.filter { it.minute >= xStart }
        val maxValue = max(visible.maxOfOrNull { it.value } ?: 0.0, trajectory.maxOfOrNull { it.second } ?: 0.0)
        val yMin = 40.0
        val yMax = max(300.0, ceil((maxValue + 20.0) / 50.0) * 50.0)
        fun y(v: Double) = bottom - ((v.coerceIn(yMin, yMax) - yMin) / (yMax - yMin)).toFloat() * (bottom - top)

        // Future area and target band
        val nowX = x(nowMinute.toDouble())
        drawRect(colors.futureShade, Offset(nowX, top), Size(right - nowX, bottom - top))
        drawRect(colors.targetBand, Offset(left, y(highLine)), Size(right - left, y(lowLine) - y(highLine)))

        // Horizontal reference lines with labels
        val levels = listOf(lowLine, highLine, 250.0, yMax).distinct().filter { it <= yMax }
        for (v in levels) {
            val yy = y(v)
            drawLine(gridColor, Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
            val layout = measurer.measure(v.toInt().toString(), labelStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), yy - layout.size.height / 2f))
        }

        // Time ticks aligned to the clock
        val tickStep = when (windowHours) {
            3 -> 60
            6 -> 120
            12 -> 180
            else -> 360
        }
        val startOfDay = Math.floorMod(clockMinute(clock, xStart), 1440)
        var tick = xStart + Math.floorMod(-startOfDay, tickStep)
        while (tick <= xEnd) {
            val tx = x(tick.toDouble())
            drawLine(gridColor.copy(alpha = 0.5f), Offset(tx, top), Offset(tx, bottom), strokeWidth = 1f)
            val layout = measurer.measure(clock(tick), labelStyle)
            val lx = (tx - layout.size.width / 2f).coerceIn(left, right - layout.size.width)
            drawText(layout, topLeft = Offset(lx, size.height - layout.size.height))
            tick += tickStep
        }

        // "Now" marker
        drawLine(
            nowColor, Offset(nowX, top), Offset(nowX, bottom + lane), strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
        )

        // Readings as dots, coloured by status
        val radius = when (windowHours) {
            3 -> 3.5f
            6 -> 3f
            12 -> 2.4f
            else -> 1.9f
        }.dp.toPx()
        for (r in visible) {
            val color = when {
                r.value < 55.0 -> colors.urgentLow
                r.value < lowLine -> colors.low
                r.value > highLine -> colors.high
                else -> colors.inRange
            }
            drawCircle(color, radius, Offset(x(r.minute.toDouble()), y(r.value)))
        }

        // Forecast (dashed) from the latest reading
        val last = visible.lastOrNull()
        if (last != null && trajectory.isNotEmpty()) {
            val path = Path().apply {
                moveTo(x(last.minute.toDouble()), y(last.value))
                trajectory.forEach { (ahead, v) -> lineTo(x((last.minute + ahead).toDouble()), y(v)) }
            }
            drawPath(
                path, colors.forecast,
                style = Stroke(width = 2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
            )
        }

        // Event lane
        val laneY = bottom + lane / 2f + 2.dp.toPx()
        for (e in events) {
            if (e.minute < xStart || e.minute > xEnd) continue
            drawMarker(markerShapeOf(e), Offset(x(e.minute.toDouble()), laneY), 4.dp.toPx(), markerColorOf(e, colors))
        }
    }
}

/** Recovers minute-of-day from the clock formatter ("HH:mm"). */
private fun clockMinute(clock: (Int) -> String, minute: Int): Int {
    val (h, m) = clock(minute).split(":").map { it.toInt() }
    return h * 60 + m
}
