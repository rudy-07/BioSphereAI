package com.madhusudhan.biosphereai.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.madhusudhan.biosphereai.R
import com.madhusudhan.biosphereai.ui.screens.EventsScreen
import com.madhusudhan.biosphereai.ui.screens.GlucoseScreen
import com.madhusudhan.biosphereai.ui.screens.InsightsScreen
import com.madhusudhan.biosphereai.ui.screens.SettingsScreen

private enum class Tab(val title: String, @param:DrawableRes val icon: Int) {
    GLUCOSE("Glucose", R.drawable.ic_tab_glucose),
    EVENTS("Events", R.drawable.ic_tab_events),
    INSIGHTS("Insights", R.drawable.ic_tab_insights),
    SETTINGS("Settings", R.drawable.ic_tab_settings),
}

@Composable
fun BioSphereApp(viewModel: GlucoseViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.GLUCOSE) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopBar(state) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding).fillMaxSize()
        when (tab) {
            Tab.GLUCOSE -> GlucoseScreen(state, content)
            Tab.EVENTS -> EventsScreen(
                state = state,
                predictMeal = viewModel::predictMeal,
                onLogMeal = viewModel::logMeal,
                onLogInsulin = viewModel::logInsulin,
                onLogExercise = viewModel::logExercise,
                modifier = content,
            )
            Tab.INSIGHTS -> InsightsScreen(state, content)
            Tab.SETTINGS -> SettingsScreen(state, viewModel::updateSettings, viewModel::resetSimulation, content)
        }
    }
}

@Composable
private fun TopBar(state: UiState) {
    Column(
        Modifier
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BioSphere", style = MaterialTheme.typography.titleLarge)
            Text("AI", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    "SIMULATED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                state.clock(state.nowMinute),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${state.settings.cohort.title} virtual patient · not for medical use",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
