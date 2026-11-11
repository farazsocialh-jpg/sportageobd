package com.sportage.companion.ui.screens.connectionsetup

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sportage.companion.ui.components.QrScanner
import com.sportage.companion.ui.components.generateQrBitmap
import com.sportage.companion.viewmodel.ConnectionSetupViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun ConnectionSetupScreen(
    viewModel: ConnectionSetupViewModel = koinViewModel(),
    onConfigured: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var scanning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("HiveMQ Connection Setup", style = MaterialTheme.typography.headlineMedium)
        Text("Configure your HiveMQ Cloud broker and vehicle ID.", style = MaterialTheme.typography.bodySmall)

        if (scanning) {
            QrScanner(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                onScanned = { payload ->
                    viewModel.applyScannedPayload(payload)
                    scanning = false
                }
            )
            OutlinedButton(onClick = { scanning = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancel scan") }
        } else {
            OutlinedButton(onClick = { scanning = true }, modifier = Modifier.fillMaxWidth()) { Text("Scan QR code") }
        }

        OutlinedTextField(value = state.brokerHost, onValueChange = viewModel::onBrokerHostChange, label = { Text("Broker host") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.brokerPort, onValueChange = viewModel::onBrokerPortChange, label = { Text("Broker port (8883)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.mqttUsername, onValueChange = viewModel::onUsernameChange, label = { Text("MQTT username") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.mqttPassword, onValueChange = viewModel::onPasswordChange, label = { Text("MQTT password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.vehicleId, onValueChange = viewModel::onVehicleIdChange, label = { Text("Vehicle ID") }, modifier = Modifier.fillMaxWidth())

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.testResult?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        if (state.testing) {
            CircularProgressIndicator()
        }

        Button(onClick = { viewModel.testConnection() }, modifier = Modifier.fillMaxWidth(), enabled = !state.testing) {
            Text("Save & Test Connection")
        }
        OutlinedButton(onClick = { if (viewModel.save()) onConfigured() }, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }

        if (state.saved) {
            Spacer(Modifier.height(8.dp))
            Text("Pairing QR for car (scan from head unit):", style = MaterialTheme.typography.titleSmall)
            val bmp = remember(state.vehicleId, state.brokerHost) { generateQrBitmap(viewModel.pairingPayload()) }
            bmp?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "Pairing QR", modifier = Modifier.fillMaxWidth().height(200.dp)) }
        }
    }
}