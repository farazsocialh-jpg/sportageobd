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
import androidx.activity.viewModels
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.viewmodel.ObdViewModel
import com.sportage.obd.ui.AppDrawerDialog
import com.sportage.obd.ui.BluetoothDialog
import com.sportage.obd.ui.CarLauncherScreen
import com.sportage.obd.ui.TerminalLog
import com.sportage.obd.ui.theme.SportageObdTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.sportage.obd.bluetooth.BluetoothDeviceInfo

class MainActivity : ComponentActivity(), LocationListener {

    private val viewModel: ObdViewModel by viewModels()
    private var locationManager: LocationManager? = null

    // Active Navigation Page (0: Home, 1: Gauges & Graphs, 2: DTC Diagnostics, 3: Trip, 4: Media, 5: Settings)
    private var activePage by mutableStateOf(0)

    // Bluetooth Dialog State
    private var showBluetoothDialog by mutableStateOf(false)
    private var showAppDrawer by mutableStateOf(false)
    private var pairedDevicesList = mutableStateListOf<BluetoothDevice>()
    private val terminalLogs = mutableStateListOf<TerminalLog>()

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
                if (showAppDrawer) {
                    showAppDrawer = false
                } else if (showBluetoothDialog) {
                    showBluetoothDialog = false
                } else if (activePage != 0) {
                    activePage = 0
                }
                // When on home (activePage == 0), intentionally absorb Back press
            }
        })

        setContent {
            val connState by viewModel.connectionState.collectAsState()
            val demoMode by viewModel.isDemoMode.collectAsState()
            val pollingPaused by viewModel.isPollingPaused.collectAsState()
            val dtcs by viewModel.dtcList.collectAsState()
            val isScanning by viewModel.isScanningDtc.collectAsState()
            val speedHist by viewModel.speedHistory.collectAsState()
            val rpmHist by viewModel.rpmHistory.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.telemetry.collect { t ->
                    liveSpeed = t.speedKmh
                    liveRpm = t.rpm
                    liveCoolant = t.coolantTempC
                    liveIntakeTemp = t.intakeTempC
                    liveFuelLevel = t.fuelLevelPct
                    liveThrottle = t.throttlePct
                    liveLoad = t.engineLoadPct
                    liveVoltage = t.batteryVoltage
                    liveGear = t.estimatedGear
                    measuredHz = t.sampleRateHz.takeIf { hz -> hz > 0f }
                }
            }

            LaunchedEffect(Unit) {
                viewModel.connectionState.collect { state ->
                    when (state) {
                        is ConnectionState.Connected ->
                            logTerminal("INFO", "LINK ACTIVE: ${state.deviceName} \u00b7 ${state.protocol}")
                        is ConnectionState.Error -> logTerminal("ERR", "Adapter error: ${state.message}")
                        ConnectionState.Connecting -> logTerminal("INFO", "Opening RFCOMM socket...")
                        ConnectionState.Disconnected -> logTerminal("INFO", "Adapter link closed.")
                    }
                }
            }

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
                    speedHistory = speedHist,
                    rpmHistory = rpmHist,
                    currentLat = currentLat,
                    currentLon = currentLon,
                    activePage = activePage,
                    onNavigatePage = { activePage = it },
                    onOpenBluetoothClick = {
                        refreshPairedDevices()
                        showBluetoothDialog = true
                    },
                    dtcCodes = dtcs,
                    isScanningDtc = isScanning,
                    onScanDtc = { scanDtcCodes() },
                    onClearDtc = { clearDtcCodes() },
                    tripDistanceKm = tripDistanceKm,
                    tripDurationSeconds = tripDurationSeconds,
                    tripMaxSpeed = tripMaxSpeed,
                    tripAvgSpeed = tripAvgSpeed,
                    onResetTrip = { resetTrip() },
                    onSetDefaultLauncher = { promptSetDefaultLauncher() },
                    isDemoMode = demoMode,
                    onToggleDemoMode = { toggleDemoMode(it) },
                    isPollingPaused = pollingPaused,
                    onTogglePausePolling = { togglePausePolling() },
                    onOpenAppDrawer = { showAppDrawer = true }
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
                    isDemoMode = demoMode,
                    onToggleDemoMode = { toggleDemoMode(it) }
                )

                // All Installed Applications Drawer Modal
                AppDrawerDialog(
                    isOpen = showAppDrawer,
                    onClose = { showAppDrawer = false }
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
        if (viewModel.isDemoMode.value) {
            viewModel.setDemoMode(false)
        }
        logTerminal("INFO", "Connecting to ${BluetoothDeviceInfo.label(this, device)} through the background OBD service...")
        // Starts (if needed) and binds ObdService, which owns the RFCOMM socket and polling loop,
        // so telemetry keeps streaming after this activity is backgrounded or destroyed.
        viewModel.connectDevice(device)
    }

    private fun disconnectDevice() {
        viewModel.disconnect()
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
                if (viewModel.isConnected()) {
                    logTerminal("RX", viewModel.sendRawCommand(command))
                } else {
                    logTerminal("ERR", "Socket is not connected. Connect an adapter first.")
                }
            } catch (e: Exception) {
                logTerminal("ERR", e.message ?: "Command timeout or communication error")
            }
        }
    }

    private fun scanDtcCodes() {
        if (!viewModel.isConnected() && !viewModel.isDemoMode.value) {
            Toast.makeText(this, "Connect to OBD adapter before scanning DTCs", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            logTerminal("TX", "03 (Read Stored Diagnostic Trouble Codes)")
            val found = viewModel.scanDtcNow()
            if (found.isEmpty()) {
                Toast.makeText(this@MainActivity, "No Diagnostic Trouble Codes detected. ECU clean.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "Found ${found.size} Diagnostic Trouble Code(s)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun clearDtcCodes() {
        if (!viewModel.isConnected() && !viewModel.isDemoMode.value) {
            Toast.makeText(this, "Connect to OBD adapter before clearing DTCs", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            logTerminal("TX", "04 (Clear Diagnostic Trouble Codes)")
            if (viewModel.clearDtcNow()) {
                Toast.makeText(this@MainActivity, "ECU Codes Cleared. Readiness monitors reset.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "Clear codes failed: adapter did not acknowledge", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleDemoMode(enabled: Boolean) {
        viewModel.setDemoMode(enabled)
        if (enabled) {
            logTerminal("INFO", "Bench demo mode enabled: virtual Sportage ECU streaming.")
        } else {
            logTerminal("INFO", "Bench demo mode disabled.")
            liveSpeed = null
            liveRpm = null
            liveCoolant = null
            liveVoltage = null
            measuredHz = null
        }
    }

    private fun togglePausePolling() {
        val paused = viewModel.togglePausePolling()
        val message = if (paused) "OBD Polling Paused (Ignition Off Mode)" else "OBD Polling Resumed"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

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
        // ObdService owns the RFCOMM socket and the polling loop, so the link and telemetry
        // survive this activity being backgrounded or destroyed.
        locationManager?.removeUpdates(this)
    }
}
