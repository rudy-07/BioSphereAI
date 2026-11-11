package com.madhusudhan.biosphereai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madhusudhan.biosphereai.algo.SafetyLevel
import com.madhusudhan.biosphereai.sim.Cohort
import com.madhusudhan.biosphereai.ui.AlertKind
import com.madhusudhan.biosphereai.ui.GlucoseStatus
import com.madhusudhan.biosphereai.ui.UiState
import com.madhusudhan.biosphereai.ui.components.ChartLegend
import com.madhusudhan.biosphereai.ui.components.GlucoseChart
import com.madhusudhan.biosphereai.ui.components.SectionCard
import com.madhusudhan.biosphereai.ui.components.StatTile
import com.madhusudhan.biosphereai.ui.components.formatGlucose
import com.madhusudhan.biosphereai.ui.components.formatSigned
import com.madhusudhan.biosphereai.ui.components.statusColor
import com.madhusudhan.biosphereai.ui.theme.HeroNumber
import com.madhusudhan.biosphereai.ui.theme.LocalBioColors
import kotlin.math.roundToInt

@Composable
fun GlucoseScreen(state: UiState, modifier: Modifier = Modifier) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CurrentReadingCard(state)
        AlertBanner(state)
        ForecastRow(state)
        if (state.settings.cohort == Cohort.T1D) SafetyFilterCard(state)
        ChartCard(state)
        TodayRow(state)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CurrentReadingCard(state: UiState) {
    val color = statusColor(state.status)
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text(
                statusLabel(state.status),
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatGlucose(state.current?.value), style = HeroNumber, color = color)
                Spacer(Modifier.width(12.dp))
                Text(state.trend.symbol, fontSize = 52.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("mg/dL", style = MaterialTheme.typography.titleMedium)
                    state.ratePerMin?.let {
                        Text(
                            "${formatSigned(it)} /min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "${state.trend.label} · reading at ${state.current?.let { state.clock(it.minute) } ?: "--:--"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun statusLabel(status: GlucoseStatus) = when (status) {
    GlucoseStatus.URGENT_LOW -> "URGENT LOW"
    GlucoseStatus.LOW -> "LOW"
    GlucoseStatus.HIGH -> "HIGH"
    GlucoseStatus.IN_RANGE -> "IN RANGE"
}

@Composable
private fun AlertBanner(state: UiState) {
    val c = LocalBioColors.current
    val alert = state.alert
    val color = when (alert.kind) {
        AlertKind.URGENT_LOW -> c.urgentLow
        AlertKind.LOW, AlertKind.LOW_SOON -> c.low
        AlertKind.HIGH, AlertKind.HIGH_SOON -> c.high
        AlertKind.NONE -> c.inRange
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 6.dp, height = 36.dp)
                    .padding(0.dp),
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(width = 6.dp, height = 36.dp)) {
                    drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(alert.title, style = MaterialTheme.typography.titleMedium)
                Text(alert.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ForecastRow(state: UiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        ForecastTile("In 30 min", state.forecast30, state.current?.value, Modifier.weight(1f))
        ForecastTile("In 60 min", state.forecast60, state.current?.value, Modifier.weight(1f))
    }
}

@Composable
private fun ForecastTile(label: String, value: Double?, current: Double?, modifier: Modifier) {
    val c = LocalBioColors.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text("Forecast · $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatGlucose(value), style = MaterialTheme.typography.headlineMedium, color = c.forecast, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                if (value != null && current != null) {
                    Text(
                        formatSigned(value - current, 0),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyFilterCard(state: UiState) {
    val c = LocalBioColors.current
    val enabled = state.settings.safetyFilter
    val safety = state.safety
    SectionCard(title = "Insulin safety filter") {
        if (!enabled) {
            Text("Off — the pump delivers every requested dose.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        if (safety == null) {
            Text("Waiting for readings", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        val pct = (safety.deliveryFraction * 100).roundToInt()
        val (label, color) = when (safety.level) {
            SafetyLevel.CLEAR -> "Normal delivery" to c.inRange
            SafetyLevel.THROTTLED -> "Reduced delivery" to c.high
            SafetyLevel.SUSPENDED -> "Insulin suspended" to c.low
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("$pct %", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { safety.deliveryFraction.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Projected in 25 min: ${formatGlucose(safety.projectedGlucose)} mg/dL (barrier floor 75)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChartCard(state: UiState) {
    var window by rememberSaveable { mutableIntStateOf(3) }
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3, 6, 12, 24).forEach { h ->
                FilterChip(selected = window == h, onClick = { window = h }, label = { Text("${h}h") })
            }
        }
        Spacer(Modifier.height(8.dp))
        GlucoseChart(
            readings = state.readings,
            trajectory = state.trajectory,
            events = state.events,
            nowMinute = state.nowMinute,
            windowHours = window,
            lowLine = state.settings.lowAlert,
            highLine = state.settings.highAlert,
            clock = state::clock,
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
        Spacer(Modifier.height(8.dp))
        ChartLegend()
    }
}

@Composable
private fun TodayRow(state: UiState) {
    val m = state.metrics24h ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Time in range", "${m.timeInRangePct.roundToInt()}%", Modifier.weight(1f), "last 24 h")
        StatTile("Average", "${m.meanGlucose.roundToInt()}", Modifier.weight(1f), "mg/dL")
        StatTile("GMI", "%.1f%%".format(m.gmiPct), Modifier.weight(1f), "est. A1C")
    }
}
