package com.madhusudhan.biosphereai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.madhusudhan.biosphereai.ui.LoggedAlert

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertHistorySheet(alerts: List<LoggedAlert>, onClear: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Alerts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (alerts.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
            }
            if (alerts.isEmpty()) {
                MutedText("No alerts yet. Lows, highs and predicted lows or highs will appear here.", Modifier.padding(vertical = 16.dp))
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(alerts, key = { it.id }) { a ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusBar(alertColor(a.kind))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.title, style = MaterialTheme.typography.titleSmall)
                            MutedText("${a.detail} · ${a.valueText}")
                        }
                        Text(a.clock, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
