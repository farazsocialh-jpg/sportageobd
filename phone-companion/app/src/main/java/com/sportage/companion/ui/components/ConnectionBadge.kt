package com.sportage.companion.ui.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sportage.companion.data.model.ConnectionState

@Composable
fun ConnectionBadge(status: ConnectionState) {
    val (label, color) = when (status) {
        ConnectionState.CONNECTING -> "Connecting to Broker" to MaterialTheme.colorScheme.tertiary
        ConnectionState.CONNECTED -> "Connected" to MaterialTheme.colorScheme.primary
        ConnectionState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.error
        ConnectionState.AUTH_FAILED -> "Auth Failed" to MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color)
    )
}