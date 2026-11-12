package com.madhusudhan.biosphereai.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.madhusudhan.biosphereai.sim.Cohort
import com.madhusudhan.biosphereai.sim.Personas
import com.madhusudhan.biosphereai.ui.AppSettings
import com.madhusudhan.biosphereai.ui.SimSpeed
import com.madhusudhan.biosphereai.ui.UiState
import com.madhusudhan.biosphereai.ui.components.MutedText
import com.madhusudhan.biosphereai.ui.components.SectionCard
import com.madhusudhan.biosphereai.ui.theme.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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
                Text(
                    cohort.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
                Personas.all.filter { it.cohort == cohort }.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = s.personaId == p.id, role = Role.RadioButton) {
                                onUpdate { it.copy(personaId = p.id) }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.personaId == p.id, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("${p.name}, ${p.age}", style = MaterialTheme.typography.bodyLarge)
                            MutedText(p.summary)
                        }
                    }
                }
            }
            MutedText("Fictional people. Switching patient restarts the simulation.", Modifier.padding(top = 4.dp))
        }

        SectionCard(title = "Appearance") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = s.themeMode == mode,
                        onClick = { onUpdate { it.copy(themeMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(mode.label) }
                }
            }
            Spacer(Modifier.height(4.dp))
            SwitchRow("Pure black", "True black background in dark mode, for OLED screens", s.pureBlack) { on ->
                onUpdate { it.copy(pureBlack = on) }
            }
        }

        SectionCard(title = "Notifications") {
            SwitchRow("Glucose alerts", "Notify on lows, highs and predicted lows or highs", s.notifications) { on ->
                onUpdate { it.copy(notifications = on) }
            }
            if (s.notifications && !state.notificationsAllowed) {
                val context = LocalContext.current
                MutedText("Notifications are blocked for this app in Android settings.")
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }) { Text("Open notification settings") }
            }
            MutedText("Alerts are raised while the app is running. Every alert is also kept in the bell menu.")
        }

        SectionCard(title = "Cloud models") {
            SwitchRow("LatentODE forecast", "Second 30/60 min forecast from the server, next to the on-device one", s.cloudForecast) { on ->
                onUpdate { it.copy(cloudForecast = on) }
            }
            SwitchRow("Simulate server failure", "Testing aid: every server request fails", s.simulateServerFailure) { on ->
                onUpdate { it.copy(simulateServerFailure = on) }
            }
            MutedText("Data source: ${state.dataSource}")
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
            MutedText(s.speed.detail)
            Spacer(Modifier.height(8.dp))
            SwitchRow("Daily routine", "${s.persona.name}'s usual meals and activity", s.autoMeals) { on ->
                onUpdate { it.copy(autoMeals = on) }
            }
            if (s.cohort == Cohort.T1D) {
                SwitchRow("Insulin safety filter", "Cuts insulin when a low is projected (control barrier function)", s.safetyFilter) { on ->
                    onUpdate { it.copy(safetyFilter = on) }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Restart simulation") }
        }

        SectionCard(title = "Alert thresholds") {
            ThresholdSlider("Low alert", s.lowAlert, 60f..90f, 5) { v -> onUpdate { it.copy(lowAlert = v) } }
            ThresholdSlider("High alert", s.highAlert, 140f..300f, 31) { v -> onUpdate { it.copy(highAlert = v) } }
            MutedText("Urgent low is fixed at 55 mg/dL.")
        }

        SectionCard(title = "About") {
            Text(
                "BioSphere is a research prototype. All glucose values come from a simulated patient " +
                    "(modified Bergman minimal model). It is not a medical device, is not connected to any " +
                    "sensor, and must not be used for treatment decisions.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            MutedText(
                "On device: edge glucose forecaster (30/60 min), control barrier insulin safety filter, " +
                    "meal response estimate and CGM metrics (TIR, CV, GMI). Cloud: LatentODE forecast, " +
                    "cuffless blood pressure estimate, meal and activity analysis. Not affiliated with any " +
                    "CGM manufacturer. Typeface: Manrope (SIL Open Font License).",
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            MutedText(subtitle)
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
