package com.madhusudhan.biosphereai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madhusudhan.biosphereai.algo.SafetyLevel
import com.madhusudhan.biosphereai.data.BpResponse
import com.madhusudhan.biosphereai.data.Remote
import com.madhusudhan.biosphereai.sim.Cohort
import com.madhusudhan.biosphereai.ui.AlertKind
import com.madhusudhan.biosphereai.ui.AppSettings
import com.madhusudhan.biosphereai.ui.GlucoseStatus
import com.madhusudhan.biosphereai.ui.UiState
import com.madhusudhan.biosphereai.ui.components.ChartLegend
import com.madhusudhan.biosphereai.ui.components.GlucoseChart
import com.madhusudhan.biosphereai.ui.components.MutedText
import com.madhusudhan.biosphereai.ui.components.SectionCard
import com.madhusudhan.biosphereai.ui.components.StatTile
import com.madhusudhan.biosphereai.ui.components.StatusBar
import com.madhusudhan.biosphereai.ui.components.Tag
import com.madhusudhan.biosphereai.ui.components.alertColor
import com.madhusudhan.biosphereai.ui.components.formatGlucose
import com.madhusudhan.biosphereai.ui.components.formatSigned
import com.madhusudhan.biosphereai.ui.components.statusColor
import com.madhusudhan.biosphereai.ui.theme.CardNumber
import com.madhusudhan.biosphereai.ui.theme.HeroNumber
import com.madhusudhan.biosphereai.ui.theme.LocalBioColors
import kotlin.math.roundToInt

@Composable
fun GlucoseScreen(
    state: UiState,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CurrentReadingCard(state)
        if (state.alert.kind != AlertKind.NONE) AlertBanner(state)
        ForecastCard(state, onUpdateSettings)
        SafetyCard(state)
        BloodPressureCard(state)
        ChartCard(state)
        TodayRow(state)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CurrentReadingCard(state: UiState) {
    val color = statusColor(state.status)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(statusLabel(state.status), color)
                Spacer(Modifier.weight(1f))
                MutedText("Reading at ${state.current?.let { state.clock(it.minute) } ?: "--:--"}")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatGlucose(state.current?.value), style = HeroNumber, color = color)
                Spacer(Modifier.width(10.dp))
                Text(state.trend.symbol, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("mg/dL", style = MaterialTheme.typography.titleMedium)
                    state.ratePerMin?.let { MutedText("${formatSigned(it)} /min") }
                }
            }
            MutedText(state.trend.label)
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
    val color = alertColor(state.alert.kind)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusBar(color)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(state.alert.title, style = MaterialTheme.typography.titleMedium)
                MutedText(state.alert.detail)
            }
        }
    }
}

// ---------------------------------------------------------------- Forecasts

@Composable
private fun ForecastCard(state: UiState, onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit) {
    val c = LocalBioColors.current
    val cloudOn = state.settings.cloudForecast
    SectionCard(
        title = "Glucose forecast",
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MutedText("Cloud model")
                Spacer(Modifier.width(8.dp))
                Switch(checked = cloudOn, onCheckedChange = { on -> onUpdateSettings { it.copy(cloudForecast = on) } })
            }
        },
    ) {
        Row {
            Spacer(Modifier.weight(1.4f))
            MutedText("30 min", Modifier.weight(1f))
            MutedText("60 min", Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        ForecastRow(
            name = "Edge model",
            tag = "On device",
            color = c.forecast,
            at30 = state.forecast30,
            at60 = state.forecast60,
            current = state.current?.value,
        )
        if (cloudOn) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            when (val f = state.cloudForecast) {
                is Remote.Ready -> {
                    ForecastRow("LatentODE", "Cloud", c.cloudForecast, f.value.horizon30m, f.value.horizon60m, state.current?.value)
                    val ci = f.value.confidenceInterval95
                    if (ci.size == 2) {
                        MutedText(
                            "60 min range (95%): ${ci[0].roundToInt()} to ${ci[1].roundToInt()} mg/dL · " +
                                "${"%.1f".format(f.value.inferenceTimeMs)} ms",
                            Modifier.padding(top = 4.dp),
                        )
                    }
                }
                is Remote.Failed -> CloudMessage("LatentODE unavailable: ${f.message}. The on-device forecast still works offline.")
                Remote.Loading, null -> CloudMessage("Contacting the cloud model...")
            }
        }
    }
}

@Composable
private fun ForecastRow(name: String, tag: String, color: Color, at30: Double?, at60: Double?, current: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1.4f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Tag(tag, color)
        }
        ForecastValue(at30, current, color, Modifier.weight(1f))
        ForecastValue(at60, current, color, Modifier.weight(1f))
    }
}

@Composable
private fun ForecastValue(value: Double?, current: Double?, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(formatGlucose(value), style = CardNumber, color = color)
        if (value != null && current != null) MutedText(formatSigned(value - current, 0))
    }
}

@Composable
private fun CloudMessage(text: String) {
    MutedText(text, Modifier.padding(vertical = 6.dp))
}

// ---------------------------------------------------------------- Safety (CBF)

@Composable
private fun SafetyCard(state: UiState) {
    val c = LocalBioColors.current
    SectionCard(title = "Insulin safety filter") {
        if (state.settings.cohort != Cohort.T1D) {
            MutedText("Not used for this patient: type 2, no insulin pump.")
            return@SectionCard
        }
        if (!state.settings.safetyFilter) {
            Text("Off. The pump delivers every requested dose.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        val safety = state.safety
        if (safety == null) {
            MutedText("Waiting for readings")
            return@SectionCard
        }
        val pct = (safety.deliveryFraction * 100).roundToInt()
        val (label, color) = when (safety.level) {
            SafetyLevel.CLEAR -> "Normal delivery" to c.inRange
            SafetyLevel.THROTTLED -> "Reduced delivery" to c.high
            SafetyLevel.SUSPENDED -> "Insulin suspended" to c.low
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = color)
            Spacer(Modifier.weight(1f))
            Text("$pct%", style = CardNumber)
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
        Spacer(Modifier.height(10.dp))
        Row {
            LabeledValue("Projected in 25 min", "${formatGlucose(safety.projectedGlucose)} mg/dL", Modifier.weight(1f))
            LabeledValue("Barrier h", "%.0f".format(safety.barrier), Modifier.weight(1f))
            LabeledValue("Floor", "75 mg/dL", Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        MutedText("Control barrier function: insulin is scaled down as glucose heads toward the floor.")
    }
}

// ---------------------------------------------------------------- Blood pressure

@Composable
private fun BloodPressureCard(state: UiState) {
    SectionCard(title = "Blood pressure", trailing = { Tag("Experimental") }) {
        when (val bp = state.bp) {
            is Remote.Ready -> BpContent(bp.value, state.heartRate)
            is Remote.Failed -> MutedText("Estimate unavailable: ${bp.message}")
            Remote.Loading, null -> MutedText("Estimating...")
        }
        Spacer(Modifier.height(8.dp))
        MutedText("Cuffless estimate from the simulated heart rate and glucose. Not a cuff measurement.")
    }
}

@Composable
private fun BpContent(bp: BpResponse, heartRate: Double?) {
    val color = bpColor(bp.status)
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${bp.systolic.roundToInt()}/${bp.diastolic.roundToInt()}", style = CardNumber)
        Spacer(Modifier.width(6.dp))
        MutedText(bp.unit, Modifier.padding(bottom = 4.dp))
        Spacer(Modifier.weight(1f))
        Tag(bp.status.replace('_', ' '), color, Modifier.padding(bottom = 6.dp))
    }
    Spacer(Modifier.height(8.dp))
    Row {
        LabeledValue("Mean arterial", "${bp.meanArterialPressure.roundToInt()} mmHg", Modifier.weight(1f))
        LabeledValue("Heart rate", heartRate?.let { "${it.roundToInt()} bpm" } ?: "---", Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun bpColor(status: String): Color {
    val c = LocalBioColors.current
    return when (status) {
        "NORMAL" -> c.inRange
        "ELEVATED", "STAGE_1" -> c.high
        else -> c.low
    }
}

@Composable
private fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        MutedText(label)
        Text(value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Start)
    }
}

// ---------------------------------------------------------------- Chart and overview

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
