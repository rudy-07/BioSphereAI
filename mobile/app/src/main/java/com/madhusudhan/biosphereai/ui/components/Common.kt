package com.madhusudhan.biosphereai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.madhusudhan.biosphereai.sim.CareEvent
import com.madhusudhan.biosphereai.sim.ExerciseEvent
import com.madhusudhan.biosphereai.sim.InsulinEvent
import com.madhusudhan.biosphereai.sim.MealEvent
import com.madhusudhan.biosphereai.ui.GlucoseStatus
import com.madhusudhan.biosphereai.ui.theme.BioColors
import com.madhusudhan.biosphereai.ui.theme.LocalBioColors
import kotlin.math.roundToInt

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(12.dp))
            }
            content()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, caption: String? = null) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun statusColor(status: GlucoseStatus): Color {
    val c = LocalBioColors.current
    return when (status) {
        GlucoseStatus.URGENT_LOW -> c.urgentLow
        GlucoseStatus.LOW -> c.low
        GlucoseStatus.HIGH -> c.high
        GlucoseStatus.IN_RANGE -> c.inRange
    }
}

/** CGM convention: values outside the sensor range are shown as LOW / HIGH. */
fun formatGlucose(value: Double?): String = when {
    value == null -> "---"
    value < 40.0 -> "LOW"
    value > 400.0 -> "HIGH"
    else -> value.roundToInt().toString()
}

fun formatSigned(value: Double, decimals: Int = 1): String =
    (if (value >= 0) "+" else "−") + "%.${decimals}f".format(kotlin.math.abs(value))

enum class MarkerShape { CIRCLE, DIAMOND, TRIANGLE }

fun markerShapeOf(event: CareEvent) = when (event) {
    is MealEvent -> MarkerShape.CIRCLE
    is InsulinEvent -> MarkerShape.DIAMOND
    is ExerciseEvent -> MarkerShape.TRIANGLE
}

fun markerColorOf(event: CareEvent, colors: BioColors) = when (event) {
    is MealEvent -> colors.meal
    is InsulinEvent -> colors.insulin
    is ExerciseEvent -> colors.exercise
}

fun DrawScope.drawMarker(shape: MarkerShape, center: Offset, radius: Float, color: Color) {
    when (shape) {
        MarkerShape.CIRCLE -> drawCircle(color, radius, center)
        MarkerShape.DIAMOND -> drawPath(
            Path().apply {
                moveTo(center.x, center.y - radius * 1.2f)
                lineTo(center.x + radius * 1.2f, center.y)
                lineTo(center.x, center.y + radius * 1.2f)
                lineTo(center.x - radius * 1.2f, center.y)
                close()
            },
            color,
        )
        MarkerShape.TRIANGLE -> drawPath(
            Path().apply {
                moveTo(center.x, center.y - radius * 1.2f)
                lineTo(center.x + radius * 1.2f, center.y + radius)
                lineTo(center.x - radius * 1.2f, center.y + radius)
                close()
            },
            color,
        )
    }
}

@Composable
fun MarkerIcon(shape: MarkerShape, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(12.dp)) { drawMarker(shape, center, size.minDimension / 2.6f, color) }
}

@Composable
fun LegendItem(shape: MarkerShape?, color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (shape != null) {
            MarkerIcon(shape, color)
        } else {
            Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
                val y = size.height / 2
                drawLine(
                    color, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(),
                    pathEffect = if (dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 5f)) else null,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ChartLegend(modifier: Modifier = Modifier) {
    val c = LocalBioColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(null, c.forecast, "Forecast", dashed = true)
        LegendItem(MarkerShape.CIRCLE, c.meal, "Meal")
        LegendItem(MarkerShape.DIAMOND, c.insulin, "Insulin")
        LegendItem(MarkerShape.TRIANGLE, c.exercise, "Activity")
    }
}
