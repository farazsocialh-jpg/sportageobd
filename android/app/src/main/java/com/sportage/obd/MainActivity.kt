package com.sportage.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sportage.obd.bluetooth.BluetoothTransport
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.obd.DtcSeverity
import com.sportage.obd.obd.PidDecoders
import com.sportage.obd.ui.BluetoothDialog
import com.sportage.obd.ui.CarLauncherScreen
import com.sportage.obd.ui.TerminalLog
import com.sportage.obd.ui.theme.SportageObdTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), LocationListener {

    private val transport = BluetoothTransport()
    private var locationManager: LocationManager? = null
    private var pollingJob: Job? = null
    private var demoJob: Job? = null

    // Active Navigation Page (0: Home, 1: Gauges & Graphs, 2: DTC Diagnostics, 3: Trip, 4: Media, 5: Settings)
    private var activePage by mutableStateOf(0)

    // Bluetooth Dialog State
    private var showBluetoothDialog by mutableStateOf(false)
    private var pairedDevicesList = mutableStateListOf<BluetoothDevice>()
    private val terminalLogs = mutableStateListOf<TerminalLog>()
    private var isDemoMode by mutableStateOf(false)

    // Live Telemetry state (authoritative values)
    private var liveSpeed by mutableStateOf<Int?>(null)
    private var liveRpm by mutableStateOf<Int?>(null)
    private var liveCoolant by mutableStateOf<Int?>(null)
    private var liveIntakeTemp by mutableStateOf<Int?>(null)
    private var liveFuelLevel by mutableStateOf<Int?>(null)
    private var liveThrottle by mutableStateOf<Float?>(null)
    private var liveLoad by mutableStateOf<Float?>(null)
    private var liveVoltage by mutableStateOf<Float?>(null)
    private var liveGear by mutableStateOf<Int>(0)
    private var measuredHz by mutableStateOf<Float?>(null)

    // Real-time speed & RPM history for live graphing
    private val speedHistory = mutableStateListOf<Pair<Long, Int>>()
    private val rpmHistory = mutableStateListOf<Pair<Long, Int>>()

    // DTC Diagnostics State
    private val dtcCodes = mutableStateListOf<DtcItem>()
    private var isScanningDtc by mutableStateOf(false)

    // Trip Computer State
    private var tripDistanceKm by mutableStateOf(0.0)
    private var tripStartTime by mutableStateOf(System.currentTimeMillis())
    private var tripDurationSeconds by mutableStateOf(0L)
    private var tripMaxSpeed by mutableStateOf(0)
    private var tripAvgSpeed by mutableStateOf(0)
    private var isTripActive by mutableStateOf(true)

    // Live GPS Location
    private var currentLat by mutableStateOf(25.2854)
    private var currentLon by mutableStateOf(51.5310)
    private var currentGpsSpeed by mutableStateOf<Int?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val btGranted = permissions.entries
            .filter { it.key.contains("BLUETOOTH") }
            .all { it.value }
        if (btGranted) {
            refreshPairedDevices()
        }
        initLocationTracking()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        checkAndRequestPermissions()
        initLocationTracking()
        refreshPairedDevices()
        startTripTicker()

        // Handle Back button: Return to Home if on secondary page; do not exit launcher on Home
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showBluetoothDialog) {
                    showBluetoothDialog = false
                } else if (activePage != 0) {
                    activePage = 0
                }
                // When on home (activePage == 0), intentionally absorb Back press
            }
        })

        setContent {
            val connState by transport.connectionState.collectAsState()

            SportageObdTheme {
                CarLauncherScreen(
                    connectionState = connState,
                    measuredHz = measuredHz,
                    speed = liveSpeed,
                    rpm = liveRpm,
                    coolant = liveCoolant,
                    intakeTemp = liveIntakeTemp,
                    fuelLevel = liveFuelLevel,
                    throttle = liveThrottle,
                    load = liveLoad,
                    voltage = liveVoltage,
                    gear = liveGear,
                    speedHistory = speedHistory,
                    rpmHistory = rpmHistory,
                    currentLat = currentLat,
                    currentLon = currentLon,
                    activePage = activePage,
                    onNavigatePage = { activePage = it },
                    onOpenBluetoothClick = {
                        refreshPairedDevices()
                        showBluetoothDialog = true
                    },
                    dtcCodes = dtcCodes,
                    isScanningDtc = isScanningDtc,
                    onScanDtc = { scanDtcCodes() },
                    onClearDtc = { clearDtcCodes() },
                    tripDistanceKm = tripDistanceKm,
                    tripDurationSeconds = tripDurationSeconds,
                    tripMaxSpeed = tripMaxSpeed,
                    tripAvgSpeed = tripAvgSpeed,
                    onResetTrip = { resetTrip() },
                    onSetDefaultLauncher = { promptSetDefaultLauncher() },
                    isDemoMode = isDemoMode,
                    onToggleDemoMode = { toggleDemoMode(it) }
                )

                // Bluetooth Connection & Terminal Modal
                BluetoothDialog(
                    isOpen = showBluetoothDialog,
                    onClose = { showBluetoothDialog = false },
                    connectionState = connState,
                    pairedDevices = pairedDevicesList,
                    onConnectDevice = { connectToDevice(it) },
                    onDisconnect = { disconnectDevice() },
                    onRefreshDevices = { refreshPairedDevices() },
                    onSendCommand = { sendTerminalCommand(it) },
                    terminalLogs = terminalLogs,
                    isDemoMode = isDemoMode,
                    onToggleDemoMode = { toggleDemoMode(it) }
                )
            }
        }
    }

    private fun logTerminal(direction: String, text: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        terminalLogs.add(TerminalLog(direction, text, timeStr))
        if (terminalLogs.size > 200) {
            terminalLogs.removeAt(0)
        }
    }

    private fun promptSetDefaultLauncher() {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS)
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(fallback)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open Home Settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshPairedDevices() {
        pairedDevicesList.clear()
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) {
                pairedDevicesList.addAll(adapter.bondedDevices)
            }
        } catch (_: Exception) {}
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (isDemoMode) {
            toggleDemoMode(false)
        }
        lifecycleScope.launch {
            logTerminal("INFO", "Initiating connection to ${device.name ?: "Device"} (${device.address})...")
            val connected = transport.connect(device)
            if (connected) {
                logTerminal("INFO", "RFCOMM Socket connected. Initializing ELM327 protocol...")
                try {
                    sendTerminalCommand("ATZ")
                    delay(400)
                    sendTerminalCommand("ATE0") // Echo off
                    sendTerminalCommand("ATL0") // Linefeeds off
                    sendTerminalCommand("ATH0") // Headers off
                    sendTerminalCommand("ATSP0") // Auto protocol search
                    val voltRes = transport.sendCommand("ATRV")
                    logTerminal("RX", "ATRV -> $voltRes")
                    liveVoltage = PidDecoders.decodeBatteryVoltage(voltRes)

                    // Discover supported PIDs
                    val pidRes = transport.sendCommand("0100")
                    logTerminal("RX", "0100 (Supported PIDs) -> $pidRes")

                    startContinuousPolling()
                } catch (e: Exception) {
                    logTerminal("ERR", "Init failure: ${e.message}")
                    Toast.makeText(this@MainActivity, "Init error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                logTerminal("ERR", "Failed to connect to ${device.address}")
            }
        }
    }

    private fun disconnectDevice() {
        pollingJob?.cancel()
        pollingJob = null
        transport.disconnect()
        logTerminal("INFO", "Disconnected from adapter.")
        liveSpeed = null
        liveRpm = null
        liveCoolant = null
        liveVoltage = null
        measuredHz = null
    }

    private fun sendTerminalCommand(command: String) {
        lifecycleScope.launch {
            logTerminal("TX", command)
            try {
                if (transport.connectionState.value is ConnectionState.Connected) {
                    val response = transport.sendCommand(command)
                    logTerminal("RX", response)
                } else {
                    logTerminal("ERR", "Socket is not connected. Connect an adapter first.")
                }
            } catch (e: Exception) {
                logTerminal("ERR", e.message ?: "Command timeout or communication error")
            }
        }
    }

    private fun startContinuousPolling() {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            var loopCount = 0
            var lastFpsTime = System.currentTimeMillis()
            var framesInWindow = 0

            while (isActive && transport.connectionState.value is ConnectionState.Connected) {
                try {
                    val now = System.currentTimeMillis()

                    // Measure actual polling rate
                    framesInWindow++
                    val elapsedWindow = now - lastFpsTime
                    if (elapsedWindow >= 1000) {
                        measuredHz = (framesInWindow * 1000f) / elapsedWindow
                        framesInWindow = 0
                        lastFpsTime = now
                    }

                    // 1. Speed (010D)
                    val speedRes = transport.sendCommand("010D", 600)
                    val speed = PidDecoders.decodeSpeed(speedRes)
                    if (speed != null) {
                        liveSpeed = speed
                        speedHistory.add(Pair(now, speed))
                        if (speedHistory.size > 80) speedHistory.removeAt(0)
                        if (speed > tripMaxSpeed) tripMaxSpeed = speed
                    }

                    // 2. RPM (010C)
                    val rpmRes = transport.sendCommand("010C", 600)
                    val rpm = PidDecoders.decodeRpm(rpmRes)
                    if (rpm != null) {
                        liveRpm = rpm
                        rpmHistory.add(Pair(now, rpm))
                        if (rpmHistory.size > 80) rpmHistory.removeAt(0)
                    }

                    // Gear calculation (estimated ratio, only when rolling)
                    val s = liveSpeed ?: 0
                    val r = liveRpm ?: 0
                    if (s < 5) {
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

                    // 3. Low-frequency queries (Throttle, Load, Coolant, Voltage) every 5-10 cycles
                    if (loopCount % 5 == 0) {
                        val throttleRes = transport.sendCommand("0111", 600)
                        liveThrottle = PidDecoders.decodeThrottle(throttleRes)

                        val loadRes = transport.sendCommand("0104", 600)
                        liveLoad = PidDecoders.decodeLoad(loadRes)
                    }

                    if (loopCount % 10 == 0) {
                        val coolantRes = transport.sendCommand("0105", 600)
                        liveCoolant = PidDecoders.decodeCoolantTemp(coolantRes)

                        val intakeRes = transport.sendCommand("010F", 600)
                        liveIntakeTemp = PidDecoders.decodeIntakeTemp(intakeRes)

                        val fuelRes = transport.sendCommand("012F", 600)
                        liveFuelLevel = PidDecoders.decodeFuelLevel(fuelRes)

                        val voltRes = transport.sendCommand("ATRV", 600)
                        liveVoltage = PidDecoders.decodeBatteryVoltage(voltRes)
                    }

                    loopCount++
                    delay(30) // Gentle throttle to maintain fast response while preventing bus saturation
                } catch (_: Exception) {
                    delay(250)
                }
            }
        }
    }

    private fun scanDtcCodes() {
        if (isDemoMode) {
            // Provide realistic sample DTC in demo mode
            isScanningDtc = true
            lifecycleScope.launch {
                delay(1200)
                dtcCodes.clear()
                dtcCodes.add(
                    DtcItem(
                        code = "P0133",
                        system = "Powertrain / Engine",
                        description = "O2 Sensor Circuit Slow Response (Bank 1 Sensor 1)",
                        severity = DtcSeverity.WARNING,
                        probableCause = "Exhaust gas leak near manifold, contaminated heated O2 sensor element"
                    )
                )
                isScanningDtc = false
                Toast.makeText(this@MainActivity, "Scan complete: 1 DTC found", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (transport.connectionState.value !is ConnectionState.Connected) {
            Toast.makeText(this, "Connect to OBD adapter before scanning DTCs", Toast.LENGTH_SHORT).show()
            return
        }

        isScanningDtc = true
        lifecycleScope.launch {
            try {
                logTerminal("TX", "03 (Read Stored Diagnostic Trouble Codes)")
                val res = transport.sendCommand("03", 3000)
                logTerminal("RX", res)
                val decoded = PidDecoders.decodeDtcCodes(res)
                dtcCodes.clear()
                dtcCodes.addAll(decoded)

                if (dtcCodes.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No Diagnostic Trouble Codes detected. ECU clean.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Found ${dtcCodes.size} Diagnostic Trouble Code(s)", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "DTC Scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isScanningDtc = false
            }
        }
    }

    private fun clearDtcCodes() {
        if (isDemoMode) {
            dtcCodes.clear()
            Toast.makeText(this, "Fault codes cleared successfully", Toast.LENGTH_SHORT).show()
            return
        }

        if (transport.connectionState.value !is ConnectionState.Connected) {
            Toast.makeText(this, "Connect to OBD adapter before clearing DTCs", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                logTerminal("TX", "04 (Clear Diagnostic Trouble Codes)")
                val res = transport.sendCommand("04", 3000)
                logTerminal("RX", res)
                dtcCodes.clear()
                Toast.makeText(this@MainActivity, "ECU Codes Cleared. Readiness monitors reset.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Clear codes failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleDemoMode(enabled: Boolean) {
        isDemoMode = enabled
        if (enabled) {
            disconnectDevice()
            startDemoSimulation()
        } else {
            demoJob?.cancel()
            demoJob = null
            liveSpeed = null
            liveRpm = null
            liveCoolant = null
            liveVoltage = null
            measuredHz = null
        }
    }

    private fun startDemoSimulation() {
        demoJob?.cancel()
        demoJob = lifecycleScope.launch {
            var simSpeed = 45
            var simRpm = 1850
            var speedDelta = 1
            measuredHz = 8.3f
            liveCoolant = 89
            liveIntakeTemp = 28
            liveFuelLevel = 65
            liveVoltage = 14.1f
            liveLoad = 24.5f
            liveThrottle = 19.0f

            while (isActive && isDemoMode) {
                simSpeed += speedDelta
                if (simSpeed >= 95) speedDelta = -1
                if (simSpeed <= 25) speedDelta = 1

                simRpm = (simSpeed * 35) + (if (simSpeed % 2 == 0) 80 else -60)
                liveSpeed = simSpeed
                liveRpm = simRpm

                val now = System.currentTimeMillis()
                speedHistory.add(Pair(now, simSpeed))
                if (speedHistory.size > 80) speedHistory.removeAt(0)
                rpmHistory.add(Pair(now, simRpm))
                if (rpmHistory.size > 80) rpmHistory.removeAt(0)

                liveGear = when {
                    simSpeed < 20 -> 1
                    simSpeed < 38 -> 2
                    simSpeed < 55 -> 3
                    simSpeed < 72 -> 4
                    simSpeed < 88 -> 5
                    else -> 6
                }

                if (simSpeed > tripMaxSpeed) tripMaxSpeed = simSpeed

                delay(120)
            }
        }
    }

    private fun startTripTicker() {
        lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                if (isTripActive) {
                    tripDurationSeconds = (System.currentTimeMillis() - tripStartTime) / 1000
                    val currentSpd = liveSpeed ?: currentGpsSpeed ?: 0
                    if (currentSpd > 0) {
                        // distance = speed * time
                        tripDistanceKm += (currentSpd.toDouble() / 3600.0)
                        if (tripDurationSeconds > 0) {
                            tripAvgSpeed = ((tripDistanceKm / (tripDurationSeconds / 3600.0))).toInt()
                        }
                    }
                }
            }
        }
    }

    private fun resetTrip() {
        tripDistanceKm = 0.0
        tripStartTime = System.currentTimeMillis()
        tripDurationSeconds = 0L
        tripMaxSpeed = liveSpeed ?: 0
        tripAvgSpeed = 0
        Toast.makeText(this, "Trip computer reset", Toast.LENGTH_SHORT).show()
    }

    private fun initLocationTracking() {
        try {
            val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                val lm = locationManager ?: return
                val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val best = lastGps ?: lastNet
                if (best != null) {
                    currentLat = best.latitude
                    currentLon = best.longitude
                }

                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, this)
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 10f, this)
                }
            }
        } catch (_: SecurityException) {} catch (_: Exception) {}
    }

    override fun onLocationChanged(location: Location) {
        currentLat = location.latitude
        currentLon = location.longitude
        if (location.hasSpeed()) {
            currentGpsSpeed = (location.speed * 3.6f).toInt()
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            refreshPairedDevices()
            initLocationTracking()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        demoJob?.cancel()
        transport.disconnect()
    }
}
