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
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.sportage.obd.bluetooth.BluetoothDeviceInfo
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity(), LocationListener {

    companion object {
        const val APP_BUILD_TAG = "2026-11-06.j"
    }

    private val viewModel: ObdViewModel by viewModels()
    private var locationManager: LocationManager? = null

    // Crash report from the previous run, shown as a dialog until dismissed.
    private var previousCrashReport by mutableStateOf<String?>(null)

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
    private var gpsStatus by mutableStateOf("Waiting for GPS")

    // Auto night dimming (persisted)
    private val uiPrefs by lazy { getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE) }
    private var autoNightDim by mutableStateOf(true)
    private var nightDimLevel by mutableStateOf(0.55f)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val btEntries = permissions.entries.filter { it.key.contains("BLUETOOTH") }
        val btGranted = btEntries.all { it.value }
        if (btGranted) {
            refreshPairedDevices()
        } else if (btEntries.isNotEmpty()) {
            // Silence here looked like "no adapters found"; tell the driver what actually happened.
            Toast.makeText(
                this,
                "Bluetooth permission denied - OBD adapters cannot be scanned or connected",
                Toast.LENGTH_LONG
            ).show()
        }
        initLocationTracking()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showPreviousCrashReport()

        // targetSdk 35 forces edge-to-edge on Android 15, which drew the cockpit strip underneath
        // the status bar. Opt in explicitly with transparent bars, then pad the root layout with the
        // system bar insets below.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        autoNightDim = uiPrefs.getBoolean("autoNightDim", true)
        nightDimLevel = uiPrefs.getFloat("nightDimLevel", 0.55f)

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
                        is ConnectionState.Connecting -> logTerminal("INFO", "Opening RFCOMM socket to ${state.address ?: "adapter"}...")
                        ConnectionState.Disconnected -> logTerminal("INFO", "Adapter link closed.")
                    }
                }
            }

            SportageObdTheme {
                val isNight = isSystemInDarkTheme()
                Box(modifier = Modifier.fillMaxSize()) {
                    // Inset the dashboard so the status bar / navigation bar never cover controls.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
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
                        gpsStatus = gpsStatus,
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
                        onOpenAppDrawer = { showAppDrawer = true },
                        viewModel = viewModel,
                        autoNightDim = autoNightDim,
                        nightDimLevel = nightDimLevel,
                        onSetAutoNightDim = { enabled ->
                            autoNightDim = enabled
                            uiPrefs.edit().putBoolean("autoNightDim", enabled).apply()
                        },
                        onSetNightDimLevel = { level ->
                            nightDimLevel = level.coerceIn(0.1f, 0.85f)
                            uiPrefs.edit().putFloat("nightDimLevel", nightDimLevel).apply()
                        }
                    )
                    }

                    // Night dimming overlay: darkens the whole dashboard after dark.
                    // Non-interactive, so all controls stay touchable underneath.
                    if (autoNightDim && isNight) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = nightDimLevel))
                        )
                    }
                }

                // Bluetooth Connection & Terminal Modal
                BluetoothDialog(
                    isOpen = showBluetoothDialog,
                    onClose = { showBluetoothDialog = false },
                    connectionState = connState,
                    pairedDevices = pairedDevicesList,
                    onConnectDevice = { device, ble -> connectToDevice(device, ble) },
                    onDisconnect = { disconnectDevice() },
                    onRefreshDevices = { refreshPairedDevices() },
                    onSendCommand = { sendTerminalCommand(it) },
                    terminalLogs = terminalLogs,
                    isDemoMode = demoMode,
                    onToggleDemoMode = { toggleDemoMode(it) }
                )

                // Previous-run crash report: shown immediately so the crash reason can never
                // be missed. Screenshot this dialog and send it back to get the real fix.
                previousCrashReport?.let { report ->
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { previousCrashReport = null }
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = Color(0xFF0E1722)
                        ) {
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "THE APP CRASHED LAST TIME",
                                    color = Color(0xFFFF5A5A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Screenshot this box and send it - this is the reason.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = report,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .heightIn(max = 420.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = { previousCrashReport = null },
                                    modifier = Modifier.align(Alignment.End),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF33CCFF)
                                    )
                                ) {
                                    Text("Close", color = Color(0xFF05070B), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

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

    private fun showPreviousCrashReport() {
        try {
            val file = File(filesDir, "last_crash.txt")
            if (!file.exists()) return
            val report = file.readText().trim().take(3000)
            file.renameTo(File(filesDir, "last_crash_read.txt"))
            android.util.Log.e("SportageOBD", "Previous crash report:\n$report")
            logTerminal("ERR", "=== PREVIOUS APP CRASH (build $APP_BUILD_TAG) ===")
            report.lineSequence().take(24).forEach { line ->
                logTerminal("ERR", line.take(160))
            }
            logTerminal("ERR", "=== END OF CRASH REPORT ===")
            // Show the crash reason immediately as a dialog so it cannot be missed -
            // no need to dig through the terminal tab. Root cause first: Android wraps the
            // real reason in "Caused by:" sections below the generic headline, so put the
            // deepest cause at the top of the dialog.
            val lastCause = report.lastIndexOf("Caused by:")
            previousCrashReport = if (lastCause >= 0) {
                report.substring(lastCause).trim() + "\n\n---- full trace ----\n\n" + report
            } else {
                report
            }
        } catch (e: Exception) {
            android.util.Log.w("SportageOBD", "crash report read failed", e)
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

    private fun connectToDevice(device: BluetoothDevice, ble: Boolean = false) {
        // Single-flight guard: rapid double taps (or taps on several rows while the state
        // flow has not caught up yet) used to fire several concurrent connects and pair
        // attempts at once. Only one connect may be in flight.
        if (viewModel.connectionState.value is ConnectionState.Connecting) {
            logTerminal("INFO", "A connection attempt is already in progress - please wait.")
            return
        }
        try {
            if (viewModel.isDemoMode.value) {
                viewModel.setDemoMode(false)
            }
            logTerminal("INFO", "Connecting to ${BluetoothDeviceInfo.label(this, device)}${if (ble) " via BLE GATT" else ""} through the background OBD service...")
            // Starts (if needed) and binds ObdService, which owns the transport link and polling loop,
            // so telemetry keeps streaming after this activity is backgrounded or destroyed.
            viewModel.connectDevice(device, ble)
        } catch (e: Exception) {
            // Nothing in the connect path may ever kill the process; surface it instead.
            logTerminal("ERR", "Connection request failed: ${e.localizedMessage ?: "unexpected error"}")
            Toast.makeText(this, "Connection request failed: ${e.localizedMessage ?: "unexpected error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun disconnectDevice() {
        try {
            viewModel.disconnect()
        } catch (e: Exception) {
            logTerminal("ERR", "Disconnect failed: ${e.localizedMessage ?: "unexpected error"}")
        }
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
                    if (currentSpd > tripMaxSpeed) tripMaxSpeed = currentSpd
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
                // Some head units have no NETWORK provider; approximate-only permission
                // can also reject GPS. Neither should prevent the other provider working.
                runCatching { lm.removeUpdates(this) }
                val providers = lm.getProviders(true).filter {
                    it == LocationManager.NETWORK_PROVIDER ||
                        (hasFine && it == LocationManager.GPS_PROVIDER)
                }
                gpsStatus = if (providers.isEmpty()) "Enable device location" else "Waiting for location"
                val best = providers.mapNotNull {
                    runCatching { lm.getLastKnownLocation(it) }.getOrNull()
                }.maxByOrNull { it.time }
                if (best != null) {
                    currentLat = best.latitude
                    currentLon = best.longitude
                    gpsStatus = "Last known location"
                }

                for (provider in providers) {
                    runCatching { lm.requestLocationUpdates(provider, 2000L, 5f, this) }
                }
            } else gpsStatus = "Location permission needed"
        } catch (_: SecurityException) { gpsStatus = "Location permission needed" }
        catch (_: Exception) { gpsStatus = "Waiting for location" }
    }

    override fun onLocationChanged(location: Location) {
        if (!com.sportage.obd.map.TrackerMapPolicy.valid(location.latitude, location.longitude)) return
        currentLat = location.latitude
        currentLon = location.longitude
        currentGpsSpeed = if (location.hasSpeed()) (location.speed * 3.6f).toInt() else null
        gpsStatus = (if (location.provider == LocationManager.GPS_PROVIDER) "GPS" else "Network location") +
            (if (location.hasAccuracy()) " · ±${location.accuracy.toInt()} m" else "")
    }

    override fun onProviderEnabled(provider: String) { initLocationTracking() }
    override fun onProviderDisabled(provider: String) { initLocationTracking() }
    @Deprecated("Required by LocationListener on Android 9/10")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onResume() {
        super.onResume()
        refreshPairedDevices()
        initLocationTracking()
    }

    override fun onStop() {
        runCatching { locationManager?.removeUpdates(this) }
        currentGpsSpeed = null
        gpsStatus = "Location paused"
        super.onStop()
    }

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
        runCatching { locationManager?.removeUpdates(this) }
    }
}
