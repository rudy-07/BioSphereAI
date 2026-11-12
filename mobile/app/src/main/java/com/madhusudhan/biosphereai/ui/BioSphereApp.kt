package com.madhusudhan.biosphereai.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.madhusudhan.biosphereai.R
import com.madhusudhan.biosphereai.ui.components.AlertHistorySheet
import com.madhusudhan.biosphereai.ui.components.Tag
import com.madhusudhan.biosphereai.ui.screens.EventsScreen
import com.madhusudhan.biosphereai.ui.screens.GlucoseScreen
import com.madhusudhan.biosphereai.ui.screens.InsightsScreen
import com.madhusudhan.biosphereai.ui.screens.SettingsScreen
import com.madhusudhan.biosphereai.ui.theme.BioSphereAITheme
import com.madhusudhan.biosphereai.ui.theme.isDarkTheme

private enum class Tab(val title: String, @param:DrawableRes val icon: Int) {
    GLUCOSE("Glucose", R.drawable.ic_tab_glucose),
    EVENTS("Log", R.drawable.ic_tab_events),
    INSIGHTS("Insights", R.drawable.ic_tab_insights),
    SETTINGS("Settings", R.drawable.ic_tab_settings),
}

@Composable
fun BioSphereApp(viewModel: GlucoseViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dark = isDarkTheme(state.settings.themeMode)
    SystemBarsFor(dark)
    BioSphereAITheme(mode = state.settings.themeMode, pureBlack = state.settings.pureBlack) {
        AppScaffold(state, viewModel)
    }
}

/** Keeps status-bar icons readable when the app theme differs from the system theme. */
@Composable
private fun SystemBarsFor(dark: Boolean) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    LaunchedEffect(dark) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

@Composable
private fun AppScaffold(state: UiState, viewModel: GlucoseViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.GLUCOSE) }
    var showAlerts by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopBar(state) {
                showAlerts = true
                viewModel.markAlertsRead()
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(t.title) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding).fillMaxSize()
        when (tab) {
            Tab.GLUCOSE -> GlucoseScreen(state, onUpdateSettings = viewModel::updateSettings, modifier = content)
            Tab.EVENTS -> EventsScreen(state, viewModel, content)
            Tab.INSIGHTS -> InsightsScreen(state, content)
            Tab.SETTINGS -> SettingsScreen(state, viewModel::updateSettings, viewModel::resetSimulation, content)
        }
    }

    if (showAlerts) {
        AlertHistorySheet(
            alerts = state.alertLog,
            onClear = viewModel::clearAlertLog,
            onDismiss = { showAlerts = false },
        )
    }
}

@Composable
private fun TopBar(state: UiState, onOpenAlerts: () -> Unit) {
    Column(
        Modifier
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BioSphere", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Tag("SIMULATED")
            Spacer(Modifier.weight(1f))
            Text(
                state.clock(state.nowMinute),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onOpenAlerts) {
                BadgedBox(badge = {
                    if (state.unreadAlerts > 0) Badge { Text(if (state.unreadAlerts > 9) "9+" else "${state.unreadAlerts}") }
                }) {
                    Icon(
                        painterResource(R.drawable.ic_bell),
                        contentDescription = "Alert history",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        val p = state.settings.persona
        Text(
            "${p.name}, ${p.age} · ${state.settings.cohort.title} · virtual patient, not for medical use",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
