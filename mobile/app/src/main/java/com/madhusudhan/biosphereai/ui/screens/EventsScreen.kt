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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.madhusudhan.biosphereai.algo.MealPrediction
import com.madhusudhan.biosphereai.algo.MealResponse
import com.madhusudhan.biosphereai.sim.CareEvent
import com.madhusudhan.biosphereai.sim.Cohort
import com.madhusudhan.biosphereai.sim.ExerciseEvent
import com.madhusudhan.biosphereai.sim.InsulinEvent
import com.madhusudhan.biosphereai.sim.MealEvent
import com.madhusudhan.biosphereai.ui.UiState
import com.madhusudhan.biosphereai.ui.components.MarkerIcon
import com.madhusudhan.biosphereai.ui.components.SectionCard
import com.madhusudhan.biosphereai.ui.components.markerColorOf
import com.madhusudhan.biosphereai.ui.components.markerShapeOf
import com.madhusudhan.biosphereai.ui.theme.LocalBioColors
import kotlin.math.roundToInt

private enum class EventTab(val title: String) { MEAL("Meal"), INSULIN("Insulin"), ACTIVITY("Activity") }

@Composable
fun EventsScreen(
    state: UiState,
    predictMeal: (Double) -> MealPrediction?,
    onLogMeal: (carbs: Double, label: String, bolus: Double?) -> Unit,
    onLogInsulin: (Double) -> Unit,
    onLogExercise: (duration: Double, intensity: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(EventTab.MEAL) }
    val tabs = if (state.settings.cohort == Cohort.T1D) EventTab.entries else listOf(EventTab.MEAL, EventTab.ACTIVITY)
    if (tab !in tabs) tab = EventTab.MEAL

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { t -> FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.title) }) }
        }
        when (tab) {
            EventTab.MEAL -> MealForm(state, predictMeal, onLogMeal)
            EventTab.INSULIN -> InsulinForm(state, onLogInsulin)
            EventTab.ACTIVITY -> ActivityForm(onLogExercise)
        }
        EventHistory(state)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MealForm(
    state: UiState,
    predictMeal: (Double) -> MealPrediction?,
    onLogMeal: (Double, String, Double?) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableFloatStateOf(45f) }
    var withBolus by rememberSaveable { mutableStateOf(true) }
    val isT1d = state.settings.cohort == Cohort.T1D
    val suggestedBolus = ((carbs / CARB_RATIO) * 2).roundToInt() / 2.0
    val prediction = predictMeal(carbs.toDouble())
    val c = LocalBioColors.current

    SectionCard(title = "Log a meal") {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(40) },
            label = { Text("What did you eat?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        LabeledSlider("Carbohydrates", "${carbs.roundToInt()} g", carbs, { carbs = it }, 0f..150f, steps = 29)

        if (prediction != null) {
            Spacer(Modifier.height(8.dp))
            SectionCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Predicted peak", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${prediction.predictedPeak.roundToInt()} mg/dL",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (prediction.isHighExcursion) c.high else c.inRange,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Spike", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("+${prediction.spike.roundToInt()}", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(MealResponse.coaching(prediction), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (isT1d) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Add meal bolus", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "$suggestedBolus U at 1 U per ${CARB_RATIO.toInt()} g" +
                            if (state.settings.safetyFilter) " · safety filter may reduce it" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = withBolus, onCheckedChange = { withBolus = it })
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                onLogMeal(carbs.toDouble(), label, if (isT1d && withBolus) suggestedBolus else null)
                label = ""
            },
            enabled = carbs > 0f,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Log meal now") }
    }
}

@Composable
private fun InsulinForm(state: UiState, onLogInsulin: (Double) -> Unit) {
    var units by rememberSaveable { mutableFloatStateOf(2f) }
    SectionCard(title = "Log insulin") {
        LabeledSlider("Bolus", "%.1f U".format(units), units, { units = it }, 0.5f..15f, steps = 28)
        state.safety?.takeIf { state.settings.safetyFilter }?.let {
            Text(
                "Safety filter currently allows ${(it.deliveryFraction * 100).roundToInt()} % of a dose.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onLogInsulin(units.toDouble()) }, modifier = Modifier.fillMaxWidth()) { Text("Deliver now") }
    }
}

@Composable
private fun ActivityForm(onLogExercise: (Double, Double) -> Unit) {
    var minutes by rememberSaveable { mutableFloatStateOf(15f) }
    var intensityIndex by rememberSaveable { mutableIntStateOf(1) }
    val intensities = listOf("Light" to 0.7, "Moderate" to 1.0, "Vigorous" to 1.5)
    SectionCard(title = "Log activity") {
        LabeledSlider("Duration", "${minutes.roundToInt()} min", minutes, { minutes = it }, 5f..90f, steps = 16)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            intensities.forEachIndexed { i, (name, _) ->
                FilterChip(selected = intensityIndex == i, onClick = { intensityIndex = i }, label = { Text(name) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onLogExercise(minutes.toDouble(), intensities[intensityIndex].second) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start activity now") }
    }
}

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
        if (recent.isEmpty()) {
            Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium)
        }
        recent.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                MarkerIcon(markerShapeOf(e), markerColorOf(e, c))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title(e), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    detail(e)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(state.clock(e.minute), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun title(e: CareEvent) = when (e) {
    is MealEvent -> "${e.label} · ${e.carbs.roundToInt()} g carbs"
    is InsulinEvent -> "Insulin · ${"%.1f".format(e.deliveredUnits)} U"
    is ExerciseEvent -> "Activity · ${e.durationMin.roundToInt()} min"
}

private fun detail(e: CareEvent): String? = when (e) {
    is InsulinEvent -> if (e.deliveredUnits < e.requestedUnits) {
        "Requested ${"%.1f".format(e.requestedUnits)} U — reduced by safety filter"
    } else null
    is ExerciseEvent -> when {
        e.intensity < 0.9 -> "Light"
        e.intensity < 1.2 -> "Moderate"
        else -> "Vigorous"
    }
    is MealEvent -> null
}

private const val CARB_RATIO = 11.0
