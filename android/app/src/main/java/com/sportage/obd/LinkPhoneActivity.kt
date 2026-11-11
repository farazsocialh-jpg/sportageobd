package com.sportage.obd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sportage.obd.mqtt.MqttLinkManager
import com.sportage.obd.ui.theme.SportageObdTheme

class LinkPhoneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val linkManager = MqttLinkManager(this)
        setContent {
            SportageObdTheme(themeId = "cockpit-dark") {
                LinkPhoneScreen(linkManager)
            }
        }
    }
}

@Composable
private fun LinkPhoneScreen(linkManager: MqttLinkManager) {
    var vehicleId by remember { mutableStateOf(linkManager.vehicleId ?: "") }
    var saved by remember { mutableStateOf(linkManager.isLinked()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Link Phone Companion", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = vehicleId,
            onValueChange = { vehicleId = it; saved = false },
            label = { Text("Vehicle ID") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                linkManager.savePairing(vehicleId)
                saved = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save pairing") }
        if (saved) {
            Spacer(Modifier.height(8.dp))
            Text("Linked. Telemetry publishes to scoped topics.", color = MaterialTheme.colorScheme.primary)
        }
    }
}