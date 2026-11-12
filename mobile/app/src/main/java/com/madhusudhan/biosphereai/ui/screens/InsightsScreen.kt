package com.madhusudhan.biosphereai.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.madhusudhan.biosphereai.algo.ForecastCheck
import com.madhusudhan.biosphereai.ui.UiState
import com.madhusudhan.biosphereai.ui.components.SectionCard
import com.madhusudhan.biosphereai.ui.components.StatTile
import com.madhusudhan.biosphereai.ui.theme.LocalBioColors
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(state: UiState, modifier: Modifier = Modifier) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimeInRangesCard(state)
        StatsGrid(state)
        ForecastAccuracyCard(state)
        Spacer(Modifier.height(8.dp))
    }
}

private data class RangeBand(val label: String, val range: String, val pct: Double, val color: Color)

@Composable
private fun TimeInRangesCard(state: UiState) {
    val m = state.metrics24h ?: return
    val c = LocalBioColors.current
    val bands = listOf(
        RangeBand("Very high", ">250", m.veryHighPct, c.high),
        RangeBand("High", "181 to 250", m.highOnlyPct, c.high.copy(alpha = 0.55f)),
        RangeBand("In range", "70 to 180", m.timeInRangePct, c.inRange),
        RangeBand("Low", "54 to 69", m.lowOnlyPct, c.low.copy(alpha = 0.7f)),
        RangeBand("Very low", "<54", m.veryLowPct, c.urgentLow),
    )
    val gap = MaterialTheme.colorScheme.surface
    SectionCard(title = "Time in ranges · last 24 h") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(28.dp).height(180.dp)) {
                var y = 0f
                val gapPx = 2.dp.toPx()
                bands.forEach { b ->
                    val h = (b.pct / 100.0).toFloat() * size.height
                    if (h > 0f) {
                        drawRoundRect(b.color, Offset(0f, y), Size(size.width, h), CornerRadius(4.dp.toPx()))
                        drawRect(gap, Offset(0f, y + h - gapPx / 2), Size(size.width, gapPx))
                    }
                    y += h
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                bands.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(10.dp)) { drawCircle(b.color) }
                        Spacer(Modifier.width(8.dp))
                        Text("${b.label} (${b.range})", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${b.pct.roundToInt()}%",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (b.label == "In range") FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Common goal: over 70% in range, under 4% low, under 1% very low.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsGrid(state: UiState) {
    val m = state.metrics24h ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Average glucose", "${m.meanGlucose.roundToInt()} mg/dL", Modifier.weight(1f))
            StatTile("GMI", "%.1f%%".format(m.gmiPct), Modifier.weight(1f), "estimated A1C")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Variability (CV)", "%.0f%%".format(m.cvPct), Modifier.weight(1f),
                if (m.cvPct <= 36.0) "stable (≤36%)" else "variable (>36%)",
            )
            StatTile("Readings", "${m.readings}", Modifier.weight(1f), "every 5 min")
        }
    }
}

@Composable
private fun ForecastAccuracyCard(state: UiState) {
    SectionCard(title = "How good is the forecast?") {
        Text(
            "The on-device forecaster is scored against what actually happened over the last 24 h, " +
                "next to the simplest possible guess: \"glucose stays the same\". Lower error is better.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        listOfNotNull(state.forecastCheck30, state.forecastCheck60).forEach { ForecastRow(it) }
    }
}

@Composable
private fun ForecastRow(check: ForecastCheck) {
    val c = LocalBioColors.current
    val wins = check.forecasterMae < check.naiveMae
    Column(Modifier.padding(vertical = 6.dp)) {
        Text("${check.horizonMin}-minute forecast", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth()) {
            Text("Forecaster error", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("%.1f mg/dL".format(check.forecasterMae), style = MaterialTheme.typography.bodyMedium, color = c.forecast)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("\"Stays the same\" error", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("%.1f mg/dL".format(check.naiveMae), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            (if (wins) "Forecaster is ahead" else "Simple guess is ahead") +
                " · ${check.forecasterZoneAPct.roundToInt()}% clinically accurate (Clarke zone A) · ${check.samples} checks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
