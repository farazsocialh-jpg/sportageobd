package com.sportage.companion.ui.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sportage.companion.data.model.ConnectionStatus

@Composable
fun ConnectionBadge(status: ConnectionStatus) {
    val (label, color) = when (status) {
        ConnectionStatus.CONNECTING -> "Connecting to Broker" to MaterialTheme.colorScheme.tertiary
        ConnectionStatus.LIVE_ENGINE_RUNNING -> "Live (Engine Running)" to MaterialTheme.colorScheme.primary
        ConnectionStatus.STANDBY_IGNITION_ON -> "Standby (Ignition On)" to MaterialTheme.colorScheme.secondary
        ConnectionStatus.OFFLINE_STALE -> "Offline / Stale" to MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color)
    )
}