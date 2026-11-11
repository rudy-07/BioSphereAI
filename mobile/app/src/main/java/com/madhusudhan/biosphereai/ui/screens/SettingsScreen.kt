package com.madhusudhan.biosphereai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.madhusudhan.biosphereai.sim.Cohort
import com.madhusudhan.biosphereai.ui.AppSettings
import com.madhusudhan.biosphereai.ui.SimSpeed
import com.madhusudhan.biosphereai.ui.UiState
import com.madhusudhan.biosphereai.ui.components.SectionCard
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: UiState,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = state.settings
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "Virtual patient") {
            Cohort.entries.forEach { cohort ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = s.cohort == cohort, role = Role.RadioButton) {
                            onUpdate { it.copy(cohort = cohort) }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.cohort == cohort, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(cohort.title, style = MaterialTheme.typography.bodyLarge)
                        Text(cohort.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "Switching patient restarts the simulation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Simulation clock") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimSpeed.entries.forEach { speed ->
                    FilterChip(
                        selected = s.speed == speed,
                        onClick = { onUpdate { it.copy(speed = speed) } },
                        label = { Text(speed.label) },
                    )
                }
            }
            Text(s.speed.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SwitchRow("Daily routine", "Breakfast, lunch, dinner and an afternoon walk", s.autoMeals) { on ->
                onUpdate { it.copy(autoMeals = on) }
            }
            if (s.cohort == Cohort.T1D) {
                SwitchRow("Insulin safety filter", "Cuts insulin when a low is projected (barrier function)", s.safetyFilter) { on ->
                    onUpdate { it.copy(safetyFilter = on) }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Restart simulation") }
        }

        SectionCard(title = "Alert thresholds") {
            ThresholdSlider("Low alert", s.lowAlert, 60f..90f, 5) { v -> onUpdate { it.copy(lowAlert = v) } }
            ThresholdSlider("High alert", s.highAlert, 140f..300f, 31) { v -> onUpdate { it.copy(highAlert = v) } }
            Text(
                "Urgent low is fixed at 55 mg/dL.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "About") {
            Text(
                "BioSphereAI is a research prototype. All glucose values come from a simulated patient " +
                    "(modified Bergman minimal model). It is not a medical device, is not connected to any " +
                    "sensor, and must not be used for treatment decisions.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "On-device algorithms: edge glucose forecaster (30/60 min), control-barrier insulin safety " +
                    "filter, meal response predictor and standard CGM metrics (TIR, CV, GMI). " +
                    "Not affiliated with any CGM manufacturer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Double) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("${value.roundToInt()} mg/dL", style = MaterialTheme.typography.titleMedium)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange((it / 5f).roundToInt() * 5.0) },
        valueRange = range,
        steps = steps,
    )
}
