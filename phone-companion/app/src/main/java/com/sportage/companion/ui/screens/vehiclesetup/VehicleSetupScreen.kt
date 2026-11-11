package com.sportage.companion.ui.screens.vehiclesetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sportage.companion.ui.components.QrScanner
import com.sportage.companion.viewmodel.VehicleSetupViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun VehicleSetupScreen(viewModel: VehicleSetupViewModel = koinViewModel(), onPaired: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val paired by viewModel.pairedVehicle.collectAsState()
    var scanning by remember { mutableStateOf(false) }

    if (state.paired || paired != null) {
        onPaired()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Pair your vehicle", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (scanning) {
            QrScanner(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                onScanned = { value ->
                    viewModel.onVehicleIdChange(value)
                    scanning = false
                }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { scanning = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancel scan") }
        } else {
            OutlinedButton(onClick = { scanning = true }, modifier = Modifier.fillMaxWidth()) { Text("Scan QR code") }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.vehicleId,
            onValueChange = viewModel::onVehicleIdChange,
            label = { Text("Vehicle ID / VIN") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = viewModel::pair, modifier = Modifier.fillMaxWidth()) { Text("Pair vehicle") }
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}