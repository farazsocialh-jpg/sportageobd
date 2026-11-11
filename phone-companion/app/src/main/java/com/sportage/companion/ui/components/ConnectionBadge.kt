package com.sportage.companion.ui.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sportage.companion.data.model.ConnectionState

@Composable
fun ConnectionBadge(status: ConnectionState, live: Boolean) {
    val (label, color) = when {
        live -> "Live" to MaterialTheme.colorScheme.primary
        status == ConnectionState.CONNECTED -> "Connected (waiting for car)" to MaterialTheme.colorScheme.tertiary
        status == ConnectionState.CONNECTING -> "Connecting to Broker" to MaterialTheme.colorScheme.tertiary
        status == ConnectionState.AUTH_FAILED -> "Auth Failed" to MaterialTheme.colorScheme.error
        else -> "Car Offline / Parked" to MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color)
    )
}