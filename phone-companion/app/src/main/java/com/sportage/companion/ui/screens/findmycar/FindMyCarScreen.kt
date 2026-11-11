package com.sportage.companion.ui.screens.findmycar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.sportage.companion.viewmodel.FindMyCarViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun FindMyCarScreen(viewModel: FindMyCarViewModel = koinViewModel()) {
    val parked by viewModel.parkedLocation.collectAsState()
    val isDriving by viewModel.isDriving.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Find My Car", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(if (isDriving) "Status: driving" else "Status: parked", style = MaterialTheme.typography.bodyMedium)
        Text(viewModel.parkedDurationText(parked), style = MaterialTheme.typography.bodyMedium)

        if (parked != null) {
            val loc = parked!!
            val latLng = LatLng(loc.latitude, loc.longitude)
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(latLng, 17f)
            }
            Card(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                ) {
                    Marker(
                        state = rememberMarkerState(position = latLng),
                        title = "Parked Sportage",
                        snippet = "Accuracy ${"%.1f".format(loc.accuracy)} m"
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Parked at: ${loc.formattedTime()}")
            Text("GPS accuracy: ${"%.1f".format(loc.accuracy)} m")
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val uri = Uri.parse("google.navigation:q=${loc.latitude},${loc.longitude}&mode=w")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(Parked+Car)"))
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Walk to Car") }
        } else {
            Card(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No parked location received yet.")
                    Text("The car publishes its last GPS fix when the engine shuts off.")
                }
            }
        }
    }
}