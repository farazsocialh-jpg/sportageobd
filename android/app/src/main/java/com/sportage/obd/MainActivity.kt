package com.sportage.obd

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sportage.obd.bluetooth.BluetoothTransport
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.obd.PidDecoders
import com.sportage.obd.ui.CarLauncherScreen
import com.sportage.obd.ui.theme.SportageObdTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val transport = BluetoothTransport()

    // Live Telemetry state
    private var liveSpeed by mutableStateOf<Int?>(null)
    private var liveRpm by mutableStateOf<Int?>(null)
    private var liveCoolant by mutableStateOf<Int?>(null)
    private var liveVoltage by mutableStateOf<Float?>(null)
    private var liveGear by mutableStateOf<Int>(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startBluetoothPairing()
        } else {
            Toast.makeText(this, "Bluetooth permissions required for OBD2 communication", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val connState by transport.connectionState.collectAsState()

            SportageObdTheme {
                CarLauncherScreen(
                    connectionState = connState,
                    speed = liveSpeed,
                    rpm = liveRpm,
                    coolant = liveCoolant,
                    voltage = liveVoltage,
                    gear = liveGear,
                    onConnectClick = { checkAndRequestPermissions() }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startBluetoothPairing()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startBluetoothPairing() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }

        // Find paired ELM327 device
        val paired = adapter.bondedDevices.firstOrNull { device ->
            val name = device.name?.lowercase() ?: ""
            name.contains("obd") || name.contains("elm") || name.contains("vgate") || name.contains("carista")
        } ?: adapter.bondedDevices.firstOrNull()

        if (paired == null) {
            Toast.makeText(this, "No paired ELM327 adapter found. Please pair in Android Bluetooth Settings first.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            val connected = transport.connect(paired)
            if (connected) {
                // Initialize ELM327
                try {
                    transport.sendCommand("ATZ")
                    delay(300)
                    transport.sendCommand("ATE0")
                    transport.sendCommand("ATL0")
                    transport.sendCommand("ATSP0")
                    val voltRes = transport.sendCommand("ATRV")
                    liveVoltage = PidDecoders.decodeBatteryVoltage(voltRes)

                    // Start continuous high-speed polling loop
                    startContinuousPolling()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Init error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startContinuousPolling() {
        lifecycleScope.launch {
            while (isActive && transport.connectionState.value is ConnectionState.Connected) {
                try {
                    // Query Speed (010D)
                    val speedRes = transport.sendCommand("010D", 1000)
                    liveSpeed = PidDecoders.decodeSpeed(speedRes)

                    // Query RPM (010C)
                    val rpmRes = transport.sendCommand("010C", 1000)
                    liveRpm = PidDecoders.decodeRpm(rpmRes)

                    // Calculate gear
                    val s = liveSpeed ?: 0
                    val r = liveRpm ?: 0
                    if (s < 2) {
                        liveGear = 0
                    } else {
                        val ratio = if (s > 0) r / s else 0
                        liveGear = when {
                            ratio > 65 -> 1
                            ratio > 45 -> 2
                            ratio > 32 -> 3
                            ratio > 24 -> 4
                            ratio > 18 -> 5
                            else -> 6
                        }
                    }

                    // Query Coolant (0105) every few frames
                    val coolantRes = transport.sendCommand("0105", 1000)
                    liveCoolant = PidDecoders.decodeCoolantTemp(coolantRes)

                    delay(100) // 10Hz sampling
                } catch (_: Exception) {
                    delay(300)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        transport.disconnect()
    }
}
