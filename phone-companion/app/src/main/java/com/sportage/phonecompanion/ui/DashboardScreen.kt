package com.sportage.phonecompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sportage.phonecompanion.model.Telemetry
import com.sportage.phonecompanion.viewmodel.CompanionViewModel

@Composable
fun DashboardScreen(viewModel: CompanionViewModel) {
    val telemetry by viewModel.telemetry.collectAsState()
    val connected by viewModel.connected.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Live Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (connected) "MQTT: connected" else "MQTT: disconnected",
            style = MaterialTheme.typography.bodyMedium,
            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        TelemetryCard("Speed", "${telemetry.speedKmh} km/h")
        TelemetryCard("RPM", "${telemetry.rpm}")
        TelemetryCard("Coolant", "${telemetry.coolantTempC} °C")
        TelemetryCard("Intake", "${telemetry.intakeTempC} °C")
        TelemetryCard("Fuel", "${telemetry.fuelLevelPct} %")
        TelemetryCard("Throttle", "${"%.1f".format(telemetry.throttlePct)} %")
        TelemetryCard("Engine Load", "${"%.1f".format(telemetry.engineLoadPct)} %")
        TelemetryCard("Battery", "${"%.2f".format(telemetry.batteryVoltage)} V")
    }
}

@Composable
private fun TelemetryCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}