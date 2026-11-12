package com.madhusudhan.biosphereai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.madhusudhan.biosphereai.algo.SafetyLevel
import com.madhusudhan.biosphereai.data.ActivityAnalysisResponse
import com.madhusudhan.biosphereai.data.MealAnalysisResponse
import com.madhusudhan.biosphereai.data.Remote
import com.madhusudhan.biosphereai.sim.CareEvent
import com.madhusudhan.biosphereai.sim.Cohort
import com.madhusudhan.biosphereai.sim.ExerciseEvent
import com.madhusudhan.biosphereai.sim.InsulinEvent
import com.madhusudhan.biosphereai.sim.Macros
import com.madhusudhan.biosphereai.sim.MealEvent
import com.madhusudhan.biosphereai.ui.AlertKind
import com.madhusudhan.biosphereai.ui.GlucoseViewModel
import com.madhusudhan.biosphereai.ui.UiState
import com.madhusudhan.biosphereai.ui.components.MarkerIcon
import com.madhusudhan.biosphereai.ui.components.MutedText
import com.madhusudhan.biosphereai.ui.components.SectionCard
import com.madhusudhan.biosphereai.ui.components.StatusBar
import com.madhusudhan.biosphereai.ui.components.Tag
import com.madhusudhan.biosphereai.ui.components.alertColor
import com.madhusudhan.biosphereai.ui.components.markerColorOf
import com.madhusudhan.biosphereai.ui.components.markerShapeOf
import com.madhusudhan.biosphereai.ui.theme.LocalBioColors
import kotlin.math.roundToInt

@Composable
fun EventsScreen(state: UiState, viewModel: GlucoseViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InsulinSection(state, viewModel::logInsulin)
        MealSection(state, viewModel)
        ActivitySection(state, viewModel)
        EventHistory(state)
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- Insulin and safety

@Composable
private fun InsulinSection(state: UiState, onLogInsulin: (Double) -> Unit) {
    val c = LocalBioColors.current
    SectionCard(title = "Insulin and safety") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBar(alertColor(state.alert.kind))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(state.alert.title, style = MaterialTheme.typography.titleSmall)
                MutedText(state.alert.detail)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (state.settings.cohort != Cohort.T1D) {
            MutedText("Type 2 virtual patient: no insulin pump, so insulin logging and the safety filter are not used.")
            return@SectionCard
        }

        var units by rememberSaveable { mutableFloatStateOf(2f) }
        val safety = state.safety.takeIf { state.settings.safetyFilter }
        if (safety != null) {
            val pct = (safety.deliveryFraction * 100).roundToInt()
            val color = when (safety.level) {
                SafetyLevel.CLEAR -> c.inRange
                SafetyLevel.THROTTLED -> c.high
                SafetyLevel.SUSPENDED -> c.low
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Safety filter allows", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Tag("$pct% of a dose", color)
            }
            if (pct < 100) {
                MutedText(
                    "A ${"%.1f".format(units)} U request would deliver ${"%.1f".format(units * safety.deliveryFraction)} U.",
                    Modifier.padding(top = 4.dp),
                )
            }
        } else {
            MutedText("Safety filter is off in Settings. Every dose is delivered in full.")
        }
        Spacer(Modifier.height(8.dp))
        LabeledSlider("Bolus", "%.1f U".format(units), units, { units = it }, 0.5f..15f, steps = 28)
        Button(onClick = { onLogInsulin(units.toDouble()) }, modifier = Modifier.fillMaxWidth()) { Text("Deliver bolus") }
        if (state.alert.kind in setOf(AlertKind.LOW, AlertKind.URGENT_LOW, AlertKind.LOW_SOON)) {
            MutedText("Glucose is low or falling. Treat with fast carbs rather than insulin.", Modifier.padding(top = 6.dp))
        }
    }
}

// ---------------------------------------------------------------- Meal

@Composable
private fun MealSection(state: UiState, viewModel: GlucoseViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableFloatStateOf(45f) }
    var withBolus by rememberSaveable { mutableStateOf(true) }
    val isT1d = state.settings.cohort == Cohort.T1D
    val analysis = state.mealAnalysis
    val ready = (analysis as? Remote.Ready)?.value

    LaunchedEffect(ready) {
        if (ready != null) carbs = ready.carbsG.toFloat().coerceIn(0f, MAX_CARBS)
    }

    SectionCard(title = "Log a meal") {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it.take(120)
                if (analysis != null) viewModel.clearMealAnalysis()
            },
            label = { Text("Describe the meal and portion") },
            placeholder = { Text("e.g. chicken salad and a banana") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.analyzeMeal(query) },
            enabled = query.isNotBlank() && analysis != Remote.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Analyze meal") }

        when (analysis) {
            Remote.Loading -> {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                MutedText("Analyzing ingredients and portion...", Modifier.padding(top = 6.dp))
            }
            is Remote.Failed -> MutedText(
                "Analysis unavailable: ${analysis.message}. Enter the carbs yourself below.",
                Modifier.padding(top = 10.dp),
            )
            is Remote.Ready -> {
                Spacer(Modifier.height(12.dp))
                MealAnalysisCard(analysis.value)
            }
            null -> Unit
        }

        Spacer(Modifier.height(12.dp))
        LabeledSlider("Carbohydrates", "${carbs.roundToInt()} g", carbs, { carbs = it }, 0f..MAX_CARBS, steps = 39)

        viewModel.predictMeal(carbs.toDouble())?.let { p ->
            val c = LocalBioColors.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MutedText("On-device estimate without insulin")
                    Text(
                        "Peak ${p.predictedPeak.roundToInt()} mg/dL (+${p.spike.roundToInt()})",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (p.isHighExcursion) c.high else c.inRange,
                    )
                }
                Tag("On device")
            }
        }

        val suggestedBolus = ((carbs / CARB_RATIO) * 2).roundToInt() / 2.0
        if (isT1d) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Add meal bolus", style = MaterialTheme.typography.bodyLarge)
                    MutedText(
                        "$suggestedBolus U at 1 U per ${CARB_RATIO.toInt()} g" +
                            if (state.settings.safetyFilter) ", safety filter may reduce it" else "",
                    )
                }
                Switch(checked = withBolus, onCheckedChange = { withBolus = it })
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val macros = ready?.let { Macros(it.proteinG, it.fatG, it.fiberG, it.glycemicLoad) }
                viewModel.logMeal(carbs.toDouble(), query.trim(), if (isT1d && withBolus) suggestedBolus else null, macros)
                query = ""
            },
            enabled = carbs > 0f,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Log meal now") }
    }
}

@Composable
private fun MealAnalysisCard(a: MealAnalysisResponse) {
    val c = LocalBioColors.current
    val loadColor = when (a.glycemicLoad) {
        "LOW" -> c.inRange
        "HIGH" -> c.low
        else -> c.high
    }
    SectionCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Meal analysis", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Tag("${a.glycemicLoad.lowercase().replaceFirstChar { it.uppercase() }} glycemic load", loadColor)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Macro("Carbs", a.carbsG, Modifier.weight(1f))
            Macro("Protein", a.proteinG, Modifier.weight(1f))
            Macro("Fat", a.fatG, Modifier.weight(1f))
            Macro("Fiber", a.fiberG, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Column(Modifier.weight(1f)) {
                MutedText("Expected rise")
                Text("+${a.expectedGlucoseDelta.roundToInt()} mg/dL", style = MaterialTheme.typography.titleSmall)
            }
            Column(Modifier.weight(1f)) {
                MutedText("Peak after")
                Text("${a.peakTimeMinutes} min", style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(a.clinicalSummary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Macro(label: String, grams: Double, modifier: Modifier) {
    Column(modifier) {
        MutedText(label)
        Text("${grams.roundToInt()} g", style = MaterialTheme.typography.titleSmall)
    }
}

// ---------------------------------------------------------------- Activity

private data class Intensity(val label: String, val api: String, val simLevel: Double)

private val intensities = listOf(
    Intensity("Low", "LOW", 0.7),
    Intensity("Moderate", "MODERATE", 1.0),
    Intensity("High", "HIGH", 1.5),
)
private val activityTypes = listOf("Walking", "Running", "Cycling", "Swimming", "Strength", "Yoga", "HIIT")

@Composable
private fun ActivitySection(state: UiState, viewModel: GlucoseViewModel) {
    var type by rememberSaveable { mutableStateOf(activityTypes.first()) }
    var minutes by rememberSaveable { mutableFloatStateOf(30f) }
    var intensityIndex by rememberSaveable { mutableIntStateOf(1) }
    val analysis = state.activityAnalysis
    val clear = { if (analysis != null) viewModel.clearActivityAnalysis() }

    SectionCard(title = "Log activity") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            activityTypes.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t; clear() }, label = { Text(t) })
            }
        }
        Spacer(Modifier.height(4.dp))
        LabeledSlider("Duration", "${minutes.roundToInt()} min", minutes, { minutes = it; clear() }, 5f..90f, steps = 16)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            intensities.forEachIndexed { i, level ->
                FilterChip(selected = intensityIndex == i, onClick = { intensityIndex = i; clear() }, label = { Text(level.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.analyzeActivity(type, minutes.roundToInt(), intensities[intensityIndex].api) },
            enabled = analysis != Remote.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Analyze impact") }

        when (analysis) {
            Remote.Loading -> {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            is Remote.Failed -> MutedText("Analysis unavailable: ${analysis.message}", Modifier.padding(top = 10.dp))
            is Remote.Ready -> {
                Spacer(Modifier.height(12.dp))
                ActivityAnalysisCard(analysis.value)
            }
            null -> Unit
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.logExercise(minutes.toDouble(), intensities[intensityIndex].simLevel, type) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start activity now") }
    }
}

@Composable
private fun ActivityAnalysisCard(a: ActivityAnalysisResponse) {
    val c = LocalBioColors.current
    val riskColor = when (a.riskOfHypo) {
        "LOW" -> c.inRange
        "HIGH" -> c.low
        else -> c.high
    }
    SectionCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Row {
            Column(Modifier.weight(1f)) {
                MutedText("Expected drop")
                Text("${a.estimatedGlucoseDrop.roundToInt()} mg/dL", style = MaterialTheme.typography.titleSmall)
            }
            Column(Modifier.weight(1f)) {
                MutedText("Insulin sensitivity")
                Text("x${"%.2f".format(a.insulinSensitivityMultiplier)}", style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Risk of low afterwards", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Tag(a.riskOfHypo.lowercase().replaceFirstChar { it.uppercase() }, riskColor)
        }
    }
}

// ---------------------------------------------------------------- Shared

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(valueText, style = MaterialTheme.typography.titleMedium)
    }
    Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
}

@Composable
private fun EventHistory(state: UiState) {
    val c = LocalBioColors.current
    val recent = state.events.filter { it.minute >= state.nowMinute - 24 * 60 }.sortedByDescending { it.minute }
    SectionCard(title = "Last 24 hours") {
        if (recent.isEmpty()) Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium)
        recent.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                MarkerIcon(markerShapeOf(e), markerColorOf(e, c))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title(e), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    detail(e)?.let { MutedText(it) }
                }
                Text(state.clock(e.minute), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun title(e: CareEvent) = when (e) {
    is MealEvent -> "${e.label} · ${e.carbs.roundToInt()} g carbs"
    is InsulinEvent -> "Insulin · ${"%.1f".format(e.deliveredUnits)} U"
    is ExerciseEvent -> "${e.activity} · ${e.durationMin.roundToInt()} min"
}

private fun detail(e: CareEvent): String? = when (e) {
    is InsulinEvent -> if (e.deliveredUnits < e.requestedUnits) {
        "Requested ${"%.1f".format(e.requestedUnits)} U, reduced by the safety filter"
    } else null
    is ExerciseEvent -> when {
        e.intensity < 0.9 -> "Low intensity"
        e.intensity < 1.2 -> "Moderate intensity"
        else -> "High intensity"
    }
    is MealEvent -> e.macros?.let {
        "Protein ${it.proteinG.roundToInt()} g · fat ${it.fatG.roundToInt()} g · fiber ${it.fiberG.roundToInt()} g · " +
            "${it.glycemicLoad.lowercase()} glycemic load"
    }
}

private const val CARB_RATIO = 11.0
private const val MAX_CARBS = 200f
