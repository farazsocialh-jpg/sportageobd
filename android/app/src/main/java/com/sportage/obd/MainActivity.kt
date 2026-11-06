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
import com.sportage.obd.diagnostics.AppPreferences
import com.sportage.obd.diagnostics.Diagnostics
import com.sportage.obd.obd.HealthInput
import com.sportage.obd.obd.HealthReport
import com.sportage.obd.obd.HealthScorer
import com.sportage.obd.trips.TripRecorder
import com.sportage.obd.trips.TripStore
import com.sportage.obd.trips.TripSummary
import com.sportage.obd.launcher.AutoPauseController
import com.sportage.obd.launcher.DriverProfile
import com.sportage.obd.launcher.DriveModeController
import com.sportage.obd.launcher.KeyValueStore
import com.sportage.obd.launcher.LauncherSettings
import com.sportage.obd.launcher.PageId
import com.sportage.obd.launcher.PinnedApp
import com.sportage.obd.launcher.SettingsRepository
import com.sportage.obd.ui.AppDrawerDialog
import com.sportage.obd.ui.BluetoothDialog
import com.sportage.obd.ui.CarLauncherScreen
import com.sportage.obd.ui.HelpDialog
import com.sportage.obd.ui.OnboardingScreen
import com.sportage.obd.ui.PrivacyPolicyDialog
import com.sportage.obd.ui.TerminalLog
import com.sportage.obd.ui.theme.SportageObdTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        const val APP_BUILD_TAG = "2026-11-05.d"
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

    // Single-flight lock for adapter connect requests (see connectToDevice).
    @Volatile
    private var connectInFlight = false

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

    // Auto night dimming (persisted)
    private val uiPrefs by lazy { getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE) }
    private var autoNightDim by mutableStateOf(true)
    private var nightDimLevel by mutableStateOf(0.55f)

    // Phase 3/4: cockpit layout, automation and driver profiles. Persisted as one versioned text
    // document so the backup file and SharedPreferences share a single code path.
    private lateinit var settingsRepo: SettingsRepository
    private var launcherSettings by mutableStateOf(LauncherSettings())
    private var driverProfiles by mutableStateOf<List<DriverProfile>>(emptyList())
    private var activeProfile by mutableStateOf(SettingsRepository.DEFAULT_PROFILE)
    private val driveModeController = DriveModeController()
    private val autoPauseController = AutoPauseController()
    private var driveModeActive by mutableStateOf(false)
    private var autoPausedByRule by mutableStateOf(false)

    // Governance / onboarding state. Diagnostics consent is stored in AppPreferences so it can be
    // read before any analytics SDK initialises (see SportageObdApp).
    private lateinit var appPrefs: AppPreferences
    private var onboardingCompleted by mutableStateOf(false)
    private var diagnosticsEnabled by mutableStateOf(false)
    private var showPrivacyPolicy by mutableStateOf(false)
    private var showHelp by mutableStateOf(false)

    // Vehicle health estimate, refreshed from live telemetry + stored codes.
    private var healthReport by mutableStateOf<HealthReport?>(null)

    // Automatic trip logging (Room was traded for a CSV store: see trips/TripStore.kt).
    private lateinit var tripStore: TripStore
    private val tripRecorder = TripRecorder()
    private var trips by mutableStateOf<List<TripSummary>>(emptyList())

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

        settingsRepo = SettingsRepository(object : KeyValueStore {
            override fun getString(key: String, default: String?): String? = uiPrefs.getString(key, default)
            override fun putString(key: String, value: String) {
                uiPrefs.edit().putString(key, value).apply()
            }
        })
        launcherSettings = settingsRepo.load().also { loaded ->
            driveModeController.configure(loaded.driveModeEnterKmh, loaded.driveModeExitKmh)
            autoPauseController.configure(loaded.autoPauseMinutes)
        }
        driverProfiles = settingsRepo.profiles()
        activeProfile = settingsRepo.activeProfile()
        activePage = launcherSettings.defaultPage.index

        appPrefs = AppPreferences(this)
        onboardingCompleted = appPrefs.onboardingCompleted
        diagnosticsEnabled = appPrefs.diagnosticsEnabled
        tripStore = TripStore(TripStore.tripFile(File(filesDir, "trips")))
        runCatching { trips = tripStore.load().sortedByDescending { it.startedAtMs } }

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

                    // Health estimate: recommendations in Safety/Health separation. Only live
                    // sensor values plus stored codes, never anything transmitted off-device.
                    healthReport = healthReportFor(t)

                    // Automatic trip logging: the recorder ignores adapter pauses but keeps the
                    // clock, so a paused polling session does not invent or lose distance.
                    if (tripRecorder.isActive) {
                        val fuelRateLph = t.fuelEconomyL100?.let { economy ->
                            val speed = t.speedKmh
                            if (speed != null && speed > 5) economy * speed / 100f else null
                        }
                        tripRecorder.onSample(t.speedKmh, t.timestamp, fuelRateLph)
                    }

                    // Automation rules run off the same samples, so they never need a second timer.
                    // The controller owns the state, so a manual tap and the speed rule cannot
                    // fight: switching automation off disables auto-entry only, it must not tear
                    // down a Drive Mode the driver just opened by hand.
                    val nextDriveMode = driveModeController.update(
                        speedKmh = t.speedKmh,
                        nowMs = t.timestamp,
                        allowAutoEntry = launcherSettings.driveModeEnabled
                    )
                    if (nextDriveMode != driveModeActive) driveModeActive = nextDriveMode

                    if (launcherSettings.autoPauseEnabled) {
                        val parked = autoPauseController.update(t.speedKmh, t.timestamp)
                        if (parked && !autoPausedByRule) {
                            autoPausedByRule = true
                            if (!pollingPaused) togglePausePolling()
                        } else if (!parked && autoPausedByRule) {
                            // Back in motion: undo only the pause this rule applied, never a manual one.
                            autoPausedByRule = false
                            if (pollingPaused) togglePausePolling()
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                viewModel.connectionState.collect { state ->
                    when (state) {
                        is ConnectionState.Connected -> {
                            logTerminal("INFO", "LINK ACTIVE: ${state.deviceName} \u00b7 ${state.protocol}")
                            Diagnostics.log(Diagnostics.Event.OBD_CONNECT_SUCCESS)
                            if (!tripRecorder.isActive) tripRecorder.start()
                        }
                        is ConnectionState.Error -> {
                            logTerminal("ERR", "Adapter error: ${state.message}")
                            Diagnostics.log(Diagnostics.Event.OBD_CONNECT_FAILURE)
                            finishActiveTrip()
                        }
                        is ConnectionState.Connecting -> logTerminal("INFO", "Opening RFCOMM socket to ${state.address ?: "adapter"}...")
                        ConnectionState.Disconnected -> {
                            logTerminal("INFO", "Adapter link closed.")
                            finishActiveTrip()
                        }
                    }
                }
            }

            SportageObdTheme {
                val isNight = isSystemInDarkTheme()
                Box(modifier = Modifier.fillMaxSize()) {
                if (!onboardingCompleted) {
                    OnboardingScreen(
                        onFinish = { shareDiagnostics ->
                            appPrefs.onboardingCompleted = true
                            appPrefs.privacyAcknowledged = true
                            appPrefs.diagnosticsEnabled = shareDiagnostics
                            Diagnostics.setEnabled(this@MainActivity, shareDiagnostics)
                            diagnosticsEnabled = shareDiagnostics
                            onboardingCompleted = true
                            Diagnostics.log(Diagnostics.Event.ONBOARDING_COMPLETED)
                            // The wizard explained the permissions; ask for them now rather than
                            // at first launch, so every prompt has visible context.
                            checkAndRequestPermissions()
                        },
                        onRequestPermissions = { checkAndRequestPermissions() },
                        onOpenPrivacyPolicy = { showPrivacyPolicy = true }
                    )
                } else {
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
                        activePage = activePage,
                        onNavigatePage = { page ->
                            activePage = page
                            Diagnostics.log(Diagnostics.Event.PAGE_VIEW, mapOf("page" to page))
                        },
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
                        },
                        diagnosticsEnabled = diagnosticsEnabled,
                        onSetDiagnosticsEnabled = { enabled ->
                            diagnosticsEnabled = enabled
                            Diagnostics.setEnabled(this@MainActivity, enabled)
                        },
                        onOpenPrivacyPolicy = { showPrivacyPolicy = true },
                        onOpenHelp = { showHelp = true },
                        healthReport = healthReport,
                        trips = trips,
                        onExportTrips = { shareTripsCsv() },
                        settings = launcherSettings,
                        onUpdateSettings = { updateLauncherSettings(it) },
                        profiles = driverProfiles,
                        activeProfile = activeProfile,
                        onSelectProfile = { profile -> applyProfile(profile) },
                        onSaveProfile = { name -> saveCurrentAsProfile(name) },
                        onDeleteProfile = { name -> deleteProfile(name) },
                        onBackupSettings = { exportSettings() },
                        onRestoreSettings = { importSettings() },
                        onPinApp = { pin -> pinApp(pin) },
                        onLaunchPinned = { pin -> launchPinned(pin) },
                        driveModeActive = driveModeActive,
                        onSetDriveMode = { active ->
                            if (active) driveModeController.enterManually() else driveModeController.exitManually()
                            driveModeActive = active
                        },
                        onClearTrips = { clearTrips() }
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
                    onClose = { showAppDrawer = false },
                    columns = launcherSettings.drawerColumns,
                    iconDp = launcherSettings.drawerIconDp,
                    onPinApp = { app ->
                        pinApp(PinnedApp(app.packageName, app.activityName, app.label))
                    }
                )

                if (showPrivacyPolicy) {
                    PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
                }

                if (showHelp) {
                    HelpDialog(onDismiss = { showHelp = false })
                }
            }
        }
    }

    /** Builds the health score from the latest telemetry and the codes currently stored in the ECU. */
    private fun healthReportFor(t: com.sportage.obd.service.ObdTelemetry): HealthReport {
        val codes = viewModel.dtcList.value
        return HealthScorer.score(
            HealthInput(
                coolantTempC = t.coolantTempC,
                batteryVoltage = t.batteryVoltage,
                shortTermFuelTrimPct = t.shortTermFuelTrimPct,
                longTermFuelTrimPct = t.longTermFuelTrimPct,
                engineLoadPct = t.engineLoadPct,
                milOn = codes.isNotEmpty(),
                criticalDtcCount = codes.count { it.severity == com.sportage.obd.obd.DtcSeverity.CRITICAL },
                warningDtcCount = codes.count { it.severity != com.sportage.obd.obd.DtcSeverity.CRITICAL },
                incompleteMonitorCount = viewModel.readiness.value?.let { readiness ->
                    (readiness.sparkIgnitionMonitors + readiness.compressionIgnitionMonitors)
                        .count { !it.complete }
                } ?: 0
            )
        )
    }

    /** Persists the trip that just ended, if it recorded anything worth keeping. */
    private fun finishActiveTrip() {
        val summary = tripRecorder.finish() ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { runCatching { tripStore.append(summary) } }
            trips = (listOf(summary) + trips).sortedByDescending { it.startedAtMs }
            Diagnostics.log(
                Diagnostics.Event.TRIP_RECORDED,
                mapOf("distance_km" to summary.distanceKm.toInt(), "minutes" to summary.durationMinutes)
            )
        }
    }

    private fun clearTrips() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { runCatching { tripStore.clear() } }
            trips = emptyList()
        }
    }

    /** Writes the whole trip history to a shareable CSV through the existing FileProvider. */
    private fun shareTripsCsv() {
        try {
            val exportDir = File(cacheDir, "exports").apply { mkdirs() }
            val exportFile = File(exportDir, "sportage_trips.csv")
            exportFile.writeText(tripStore.exportCsv(trips))
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                exportFile
            )
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "SportageOBD trip history")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Export trips"))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not export trips: ${e.message}", Toast.LENGTH_SHORT).show()
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
        if (connectInFlight) {
            logTerminal("INFO", "A connection attempt is already in progress - please wait.")
            return
        }
        connectInFlight = true
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
        } finally {
            // Give the state flow a moment to reach Connecting so the dialog disables its
            // buttons, then release the single-flight lock. A slow adapter no longer allows
            // a second tap to stack another connect on top of the first.
            lifecycleScope.launch {
                delay(1200)
                connectInFlight = false
            }
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
            Diagnostics.log(
                Diagnostics.Event.DTC_SCAN_COMPLETED,
                mapOf("code_count" to found.size, "mil_on" to found.isNotEmpty())
            )
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

    // ----------------------------------------------------------------- cockpit settings & Phase 4

    private fun updateLauncherSettings(next: LauncherSettings) {
        val sanitized = next.sanitized()
        launcherSettings = sanitized
        settingsRepo.save(sanitized)
        driveModeController.configure(sanitized.driveModeEnterKmh, sanitized.driveModeExitKmh)
        autoPauseController.configure(sanitized.autoPauseMinutes)
    }

    private fun applyProfile(profile: DriverProfile) {
        val next = profile.applyTo(launcherSettings)
        updateLauncherSettings(next)
        activeProfile = profile.name
        settingsRepo.setActiveProfile(profile.name, next)
        // A profile may change which pages exist and which one the cockpit opens on.
        val current = PageId.fromIndex(activePage) ?: PageId.HOME
        if (!next.isVisible(current)) activePage = next.defaultPage.index
    }

    private fun saveCurrentAsProfile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        driverProfiles = settingsRepo.upsertProfile(DriverProfile.fromSettings(trimmed, launcherSettings))
        activeProfile = trimmed
        settingsRepo.setActiveProfile(trimmed, launcherSettings)
        Toast.makeText(this, "Profile \"$trimmed\" saved", Toast.LENGTH_SHORT).show()
    }

    private fun deleteProfile(name: String) {
        driverProfiles = settingsRepo.deleteProfile(name)
        if (activeProfile.equals(name, ignoreCase = true)) {
            activeProfile = SettingsRepository.DEFAULT_PROFILE
            settingsRepo.setActiveProfile(activeProfile, launcherSettings)
        }
        Toast.makeText(this, "Profile \"$name\" deleted", Toast.LENGTH_SHORT).show()
    }

    private fun pinApp(pin: PinnedApp) {
        val alreadyPinned = launcherSettings.pinnedApps.any {
            it.packageName == pin.packageName && it.activityName == pin.activityName
        }
        when {
            alreadyPinned -> Toast.makeText(this, "${pin.label} is already pinned", Toast.LENGTH_SHORT).show()
            launcherSettings.pinnedApps.size >= LauncherSettings.MAX_PINS ->
                Toast.makeText(this, "Dock is full (${LauncherSettings.MAX_PINS} pins) - remove one first", Toast.LENGTH_LONG).show()
            else -> {
                updateLauncherSettings(launcherSettings.copy(pinnedApps = launcherSettings.pinnedApps + pin))
                Toast.makeText(this, "Pinned ${pin.label} to the dock", Toast.LENGTH_SHORT).show()
                showAppDrawer = false
            }
        }
    }

    private fun launchPinned(pin: PinnedApp) {
        val intent = packageManager.getLaunchIntentForPackage(pin.packageName) ?: run {
            Toast.makeText(this, "${pin.label} is no longer installed", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open ${pin.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Backup uses the system file picker (Storage Access Framework) rather than a share sheet:
     * the driver picks a folder on a USB stick or the head unit's own storage, and no storage
     * permission is requested.
     */
    private val createSettingsBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val ok = runCatching {
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(settingsRepo.backup().toByteArray())
            } ?: error("could not open destination")
        }.isSuccess
        Toast.makeText(
            this,
            if (ok) "Cockpit settings exported" else "Export failed - the file could not be written",
            Toast.LENGTH_LONG
        ).show()
    }

    private val openSettingsBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val text = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) {
            Toast.makeText(this, "Import failed - the file could not be read", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        if (settingsRepo.restore(text)) {
            launcherSettings = settingsRepo.load()
            driverProfiles = settingsRepo.profiles()
            activeProfile = settingsRepo.activeProfile()
            activePage = launcherSettings.defaultPage.index
            Toast.makeText(this, "Cockpit settings imported", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Not a Sportage cockpit backup file", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportSettings() {
        createSettingsBackup.launch("sportage-cockpit-${System.currentTimeMillis() / 1000}.txt")
    }

    private fun importSettings() {
        openSettingsBackup.launch(arrayOf("text/plain", "*/*"))
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
