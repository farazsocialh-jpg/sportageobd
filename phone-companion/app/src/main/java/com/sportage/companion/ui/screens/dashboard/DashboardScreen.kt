package com.sportage.companion.ui.screens.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sportage.companion.ui.components.ConnectionBadge
import com.sportage.companion.ui.components.DialGauge
import com.sportage.companion.viewmodel.DashboardViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = koinViewModel()) {
    val telemetry by viewModel.telemetry.collectAsState()
    val status by viewModel.connectionStatus.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Live Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        ConnectionBadge(status)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DialGauge("Speed", telemetry.speedKmh.toFloat(), 220f, "km/h")
            DialGauge("RPM", telemetry.rpm.toFloat(), 8000f, "")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DialGauge("Coolant", telemetry.coolantTempC.toFloat(), 130f, "°C")
            DialGauge("Fuel", telemetry.fuelLevelPct.toFloat(), 100f, "%")
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Battery: ${"%.2f".format(telemetry.batteryVoltage)} V")
                Text("Updated: ${telemetry.timestamp}")
            }
        }
    }
}