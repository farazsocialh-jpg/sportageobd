package com.sportage.obd.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sportage.obd.MainActivity
import com.sportage.obd.bluetooth.BleTransport
import com.sportage.obd.bluetooth.BluetoothDeviceInfo
import com.sportage.obd.bluetooth.BluetoothTransport
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.bluetooth.ElmTransport
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.obd.FreezeFrame
import com.sportage.obd.obd.J1979Pid
import com.sportage.obd.obd.J1979Reading
import com.sportage.obd.obd.Mode06Parser
import com.sportage.obd.obd.MonitorTestResult
import com.sportage.obd.obd.PidDecoders
import com.sportage.obd.obd.ReadinessState
import com.sportage.obd.obd.UdsDecoding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

/**
 * Data model for live vehicle telemetry collected by ObdService.
 */
data class ObdTelemetry(
    val speedKmh: Int? = null,
    val rpm: Int? = null,
    val coolantTempC: Int? = null,
    val intakeTempC: Int? = null,
    val fuelLevelPct: Int? = null,
    val throttlePct: Float? = null,
    val engineLoadPct: Float? = null,
    val batteryVoltage: Float? = null,
    val mafGs: Float? = null,
    val shortTermFuelTrimPct: Float? = null,
    val longTermFuelTrimPct: Float? = null,
    val timingAdvanceDeg: Float? = null,
    val fuelEconomyL100: Float? = null,
    val fuelUsedLiters: Float = 0f,
    val estimatedGear: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sampleRateHz: Float = 0f
)

/**
 * ObdService: Android Foreground Service with type 'connectedDevice'.
 * Runs continuously in the background, decoupled from Activity lifecycle,
 * maintaining an active link (Bluetooth Classic SPP or BLE) with ELM327
 * adapters and streaming vehicle telemetry.
 */
class ObdService : Service() {

    companion object {
        const val CHANNEL_ID = "sportage_obd_service_channel"
        const val ALERT_CHANNEL_ID = "sportage_obd_alerts_channel"
        const val NOTIFICATION_ID = 2026
        const val ALERT_NOTIFICATION_ID = 2027
        const val ACTION_START = "com.sportage.obd.action.START_SERVICE"
        const val ACTION_STOP = "com.sportage.obd.action.STOP_SERVICE"
        const val ACTION_PAUSE_POLLING = "com.sportage.obd.action.PAUSE_POLLING"
        const val ACTION_RESUME_POLLING = "com.sportage.obd.action.RESUME_POLLING"

        private const val PREFS_NAME = "sportage_obd_service"
        private const val KEY_LAST_DEVICE = "last_adapter_address"
        private const val KEY_LAST_DEVICE_IS_BLE = "last_adapter_is_ble"
        private const val KEY_CSV_AUTOSTART = "csv_autostart"

        /**
         * Extended requests are paced to one every 400 ms. Mode 06 frames are multi-frame and slow
         * on cheap adapters, so a faster cadence would visibly eat into the 10-18 Hz telemetry
         * stream for data that only changes once per drive cycle.
         */
        private const val EXTENDED_POLL_INTERVAL_MS = 400L

        /** Upper bound for a user-triggered Mode 06 pass, so the UI cannot stall on a busy bus. */
        private const val MAX_MONITOR_SCAN_TIDS = 12

        /** True while the foreground service is alive, so a restarted UI can re-attach to it. */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val binder = ObdServiceBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var transport: ElmTransport = BluetoothTransport()
    private var useBle = false
    private var pollingJob: Job? = null
    private var reconnectJob: Job? = null

    // Created lazily in onCreate: constructing these as field initializers runs during
    // service instantiation, BEFORE Android attaches the service Context. That crashed the
    // app with "Unable to create service" on some devices (getSharedPreferences /
    // ToneGenerator with a dead context).
    private var alertEngine: AlertEngine? = null
    private var csvLogger: CsvLogger? = null

    // Target Bluetooth device to maintain connection with
    private var activeDevice: BluetoothDevice? = null

    // Telemetry and connection state
    private val _telemetry = MutableStateFlow(ObdTelemetry())
    val telemetry: StateFlow<ObdTelemetry> = _telemetry.asStateFlow()

    private val _isPollingPaused = MutableStateFlow(false)
    val isPollingPaused: StateFlow<Boolean> = _isPollingPaused.asStateFlow()

    private val _dtcList = MutableStateFlow<List<DtcItem>>(emptyList())
    val dtcList: StateFlow<List<DtcItem>> = _dtcList.asStateFlow()

    private val _pendingDtcList = MutableStateFlow<List<DtcItem>>(emptyList())
    val pendingDtcList: StateFlow<List<DtcItem>> = _pendingDtcList.asStateFlow()

    private val _permanentDtcList = MutableStateFlow<List<DtcItem>>(emptyList())
    val permanentDtcList: StateFlow<List<DtcItem>> = _permanentDtcList.asStateFlow()

    private val _freezeFrame = MutableStateFlow<FreezeFrame?>(null)
    val freezeFrame: StateFlow<FreezeFrame?> = _freezeFrame.asStateFlow()

    private val _readiness = MutableStateFlow<ReadinessState?>(null)
    val readiness: StateFlow<ReadinessState?> = _readiness.asStateFlow()

    private val _vin = MutableStateFlow<String?>(null)
    val vin: StateFlow<String?> = _vin.asStateFlow()

    private val _supportedPids = MutableStateFlow<Set<String>>(emptySet())
    val supportedPids: StateFlow<Set<String>> = _supportedPids.asStateFlow()

    private val _isCsvLogging = MutableStateFlow(false)
    val isCsvLogging: StateFlow<Boolean> = _isCsvLogging.asStateFlow()

    private val _monitorTests = MutableStateFlow<List<MonitorTestResult>>(emptyList())

    /** Latest Mode 06 on-board monitor results, rotated slowly inside the polling loop. */
    val monitorTests: StateFlow<List<MonitorTestResult>> = _monitorTests.asStateFlow()

    private val _monitorTestsUpdatedAt = MutableStateFlow(0L)
    val monitorTestsUpdatedAt: StateFlow<Long> = _monitorTestsUpdatedAt.asStateFlow()

    private val _j1979Readings = MutableStateFlow<List<J1979Reading>>(emptyList())

    /** Live parameters read over SAE J1979-2 (`0xF400 + PID`) when the vehicle supports it. */
    val j1979Readings: StateFlow<List<J1979Reading>> = _j1979Readings.asStateFlow()

    val activeAlerts: StateFlow<List<ActiveAlert>>
        get() = alertEngine?.activeAlerts ?: MutableStateFlow<List<ActiveAlert>>(emptyList())

    val csvLogFile: java.io.File? get() = csvLogger?.activeFile

    val connectionState: StateFlow<ConnectionState>
        get() = transport.connectionState

    // Diagnostic / frequency tracking
    private var packetCount = 0
    private var lastHzCalculationTime = System.currentTimeMillis()
    private var currentHz = 0f

    // Trip fuel accounting
    private var fuelUsedLiters = 0f
    private var lastFuelSampleAtMs = 0L

    // Critical-alert notifications are throttled: the polling loop runs at ~30 Hz and used to
    // re-post the same warning on every iteration.
    private var lastAlertNotificationAtMs = 0L

    // --- Extended diagnostics (Mode 06 + J1979-2) ---
    // Everything below is issued from the polling loop, never from a second coroutine: the
    // transport is one socket with no request/response serialisation, so a concurrent caller
    // would interleave its reply with the telemetry stream and hand both sides wrong answers.
    private val monitorTestTids = linkedSetOf<Int>()          // advertised by the ECU via 06 00
    private val unsupportedMonitorTids = mutableSetOf<Int>()   // asked once, never again
    private val unsupportedJ1979Dids = mutableSetOf<String>()
    private var extendedCursor = 0
    private var j1979Cursor = 0
    private var lastExtendedPollAtMs = 0L

    @Volatile
    private var monitorScanRequested = false

    inner class ObdServiceBinder : Binder() {
        fun getService(): ObdService = this@ObdService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        // Nothing in here may throw: an exception during service creation kills the whole
        // app with "Unable to create service". Every step is guarded; failures degrade
        // gracefully instead of crashing.
        try {
            alertEngine = AlertEngine(this)
        } catch (e: Exception) {
            android.util.Log.e("ObdService", "AlertEngine init failed - alerts disabled", e)
            alertEngine = null
        }
        try {
            csvLogger = CsvLogger(this)
        } catch (e: Exception) {
            android.util.Log.e("ObdService", "CsvLogger init failed - logging disabled", e)
            csvLogger = null
        }
        try {
            createNotificationChannels()
        } catch (e: Exception) {
            android.util.Log.e("ObdService", "notification channels failed", e)
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification("OBD Service Initializing..."))
        } catch (e: Exception) {
            // Some OEM ROMs throw from inside startForeground (their notification pipeline
            // touches android.net.Uri). The service still works without being foregrounded.
            android.util.Log.e("ObdService", "startForeground failed - continuing anyway", e)
        }
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent == null) {
                // START_STICKY restart after the process was killed: nothing to resume, so restore the
                // adapter the driver was last connected to.
                restoreLastDevice()
                return START_STICKY
            }
            when (intent.action) {
                ACTION_STOP -> {
                    stopPolling()
                    transport.disconnect()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                ACTION_PAUSE_POLLING -> {
                    pausePolling()
                }
                ACTION_RESUME_POLLING -> {
                    resumePolling()
                }
                ACTION_START -> {
                    updateNotification("Ready for Bluetooth link")
                }
            }
        } catch (e: Exception) {
            // Same rule as onCreate: never let a command crash the process.
            android.util.Log.e("ObdService", "onStartCommand error", e)
        }
        return START_STICKY
    }

    /**
     * Connect to target Bluetooth OBD adapter with automatic reconnection.
     * [ble] selects the transport: BLE GATT for vgate-style adapters,
     * classic SPP otherwise.
     */
    fun connectToDevice(device: BluetoothDevice, ble: Boolean = false) {
        activeDevice = device
        useBle = ble
        reconnectJob?.cancel()
        stopPolling()
        // A new session means a (possibly) different car: never carry the previous vehicle's
        // monitor results, capability guesses or "unsupported" verdicts across a connect.
        resetExtendedDiagnostics()

        // Release the previous link before swapping transports. An SPP adapter accepts a single
        // client, so a socket left open here made every later connect fail with "socket busy".
        try {
            transport.disconnect()
        } catch (_: Exception) {}

        transport = if (ble) BleTransport(this) else BluetoothTransport()
        serviceScope.launch {
            // transport.connect() can throw (permission revoked mid-dialog, adapter unplugged);
            // an uncaught exception in this launch would terminate the process. Everything
            // after the connect is also guarded: a failed init/polling start must surface as
            // a connection error, never as a crash.
            val success = try {
                transport.connect(device)
            } catch (e: SecurityException) {
                updateNotification("Bluetooth permission is required to connect")
                false
            } catch (e: Exception) {
                updateNotification("Connection failed: ${e.localizedMessage ?: "Bluetooth error"}")
                false
            }
            if (success) {
                try {
                    rememberDevice(device)
                } catch (_: Exception) {}
                updateNotification("Connected to ${BluetoothDeviceInfo.displayName(this@ObdService, device)} · Polling active")
                initializeElm327()
                discoverVehicleCapabilities()
                // CSV auto-start is optional; polling is not. Both used to be indented under the
                // same `if`, so a driver who never enabled CSV logging got a connected adapter
                // with no telemetry at all.
                if (prefs().getBoolean(KEY_CSV_AUTOSTART, false)) startCsvLogging()
                startPollingLoop()
            } else {
                updateNotification("Connection failed. Retrying...")
                scheduleReconnect()
            }
        }
    }

    fun disconnect() {
        activeDevice = null
        forgetDevice()
        reconnectJob?.cancel()
        stopPolling()
        transport.disconnect()
        resetExtendedDiagnostics()
        updateNotification("Disconnected")
    }

    /**
     * Asks the polling loop for a full Mode 06 pass at its next safe slot (at most 400 ms away).
     * Deliberately not a `suspend` call that talks to the transport itself — see the note on
     * [monitorTestTids] about concurrent commands on a single socket.
     */
    fun requestMonitorTestScan() {
        monitorScanRequested = true
    }

    fun pausePolling() {
        _isPollingPaused.value = true
        updateNotification("Polling paused (Ignition off mode)")
    }

    fun resumePolling() {
        _isPollingPaused.value = false
        if (pollingJob == null || pollingJob?.isActive == false) {
            startPollingLoop()
        }
        updateNotification("Streaming vehicle telemetry")
    }

    suspend fun sendRawCommand(command: String): String {
        return transport.sendCommand(command)
    }

    suspend fun scanDtcCodes(): List<DtcItem> {
        return try {
            val response = transport.sendCommand("03")
            val parsed = PidDecoders.decodeDtcCodes(response)
            _dtcList.value = parsed
            parsed
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Mode 07: pending (not-yet-confirmed) trouble codes. */
    suspend fun scanPendingDtcs(): List<DtcItem> {
        return try {
            val response = transport.sendCommand("07")
            val parsed = PidDecoders.decodeDtcCodes(response)
            _pendingDtcList.value = parsed
            parsed
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Mode 0A: permanent codes that survive a Mode 04 clear. */
    suspend fun scanPermanentDtcs(): List<DtcItem> {
        return try {
            val response = transport.sendCommand("0A")
            val parsed = PidDecoders.decodeDtcCodes(response)
            _permanentDtcList.value = parsed
            parsed
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Mode 02: freeze frame stored with the last confirmed fault. */
    suspend fun readFreezeFrame(): FreezeFrame? {
        return try {
            val response = transport.sendCommand("02")
            val parsed = PidDecoders.decodeFreezeFrame(response)
            if (parsed.items.isNotEmpty()) {
                _freezeFrame.value = parsed
                parsed
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /** Mode 01 PID 01: MIL status and readiness monitors. */
    suspend fun readReadiness(): ReadinessState? {
        return try {
            val response = transport.sendCommand("0101")
            val parsed = PidDecoders.decodeReadiness(response)
            if (parsed != null) _readiness.value = parsed
            parsed
        } catch (e: Exception) {
            null
        }
    }

    /** Mode 09 PID 02: vehicle identification number. */
    suspend fun readVin(): String? {
        return try {
            val response = transport.sendCommand("0902")
            val vin = PidDecoders.decodeVin(response)
            if (vin != null) _vin.value = vin
            vin
        } catch (e: Exception) {
            null
        }
    }

    suspend fun clearDtcCodes(): Boolean {
        return try {
            val response = transport.sendCommand("04")
            if (response.contains("OK") || response.contains("44")) {
                _dtcList.value = emptyList()
                _pendingDtcList.value = emptyList()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // --- Warning thresholds ---

    fun updateAlertThresholds(thresholds: AlertThresholds) {
        alertEngine?.updateThresholds(thresholds)
    }

    fun alertThresholds(): AlertThresholds = alertEngine?.currentThresholds() ?: AlertThresholds()

    // --- CSV logging ---

    fun startCsvLogging(): Boolean {
        val file = csvLogger?.start() ?: return false
        _isCsvLogging.value = true
        return file.exists()
    }

    fun stopCsvLogging() {
        csvLogger?.stop()
        _isCsvLogging.value = false
    }

    fun setCsvAutostart(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_CSV_AUTOSTART, enabled).apply()
    }

    fun csvAutostart(): Boolean = prefs().getBoolean(KEY_CSV_AUTOSTART, false)

    private suspend fun initializeElm327() {
        try {
            transport.sendCommand("ATZ")      // Reset ELM327
            delay(150)
            transport.sendCommand("ATE0")     // Echo off
            delay(50)
            transport.sendCommand("ATL0")     // Linefeeds off
            delay(50)
            transport.sendCommand("ATS0")     // Spaces off
            delay(50)
            transport.sendCommand("ATH0")     // Headers off
            delay(50)
            transport.sendCommand("ATSP0")    // Auto protocol detect (matches CAN 11/500 for Sportage)
            delay(100)
        } catch (_: Exception) {}
    }

    /**
     * Queries the ECU for its supported PID bitmaps (0100/0120/0140),
     * the VIN and the current readiness state right after connecting, so
     * the UI can adapt instead of polling unsupported PIDs forever.
     */
    private suspend fun discoverVehicleCapabilities() {
        val supported = mutableSetOf<String>()
        try {
            for (base in listOf(0x00, 0x20, 0x40)) {
                val cmd = "01" + String.format("%02X", base)
                val resp = withTimeoutOrNull(2000) { transport.sendCommand(cmd) }
                supported += PidDecoders.decodeSupportedPids(resp ?: "", base)
            }
            if (supported.isNotEmpty()) _supportedPids.value = supported
        } catch (_: Exception) {}

        try {
            readVin()
        } catch (_: Exception) {}
        try {
            readReadiness()
        } catch (_: Exception) {}
        discoverMonitorTestIds()
    }

    /**
     * Reads the `06 00` supported-test bitmap. A vehicle that does not implement Mode 06 answers
     * "NO DATA" and the rotation falls back to [Mode06Parser.COMMON_TEST_IDS].
     */
    private suspend fun discoverMonitorTestIds() {
        try {
            val response = withTimeoutOrNull(2000) { transport.sendCommand("0600") } ?: return
            val tids = Mode06Parser.parseSupportedTids(response)
            if (tids.isNotEmpty()) {
                monitorTestTids.clear()
                monitorTestTids.addAll(tids)
            }
        } catch (_: Exception) {}
    }

    private fun resetExtendedDiagnostics() {
        monitorTestTids.clear()
        unsupportedMonitorTids.clear()
        unsupportedJ1979Dids.clear()
        monitorScanRequested = false
        extendedCursor = 0
        j1979Cursor = 0
        lastExtendedPollAtMs = 0L
        _monitorTests.value = emptyList()
        _monitorTestsUpdatedAt.value = 0L
        _j1979Readings.value = emptyList()
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            // Independent cycle counter for the auxiliary PID rotation. Deliberately
            // separate from packetCount: packetCount is reset every second by the
            // Hz calculation below, which previously caused the aux PID rotation
            // (packetCount / 2) % 6 to restart from coolant on every reset and, on
            // slower adapters, never reach the later PIDs (fuel, throttle, load,
            // voltage).
            var auxPidCycle = 0
            lastFuelSampleAtMs = System.currentTimeMillis()

            while (isActive) {
                if (_isPollingPaused.value) {
                    delay(500)
                    continue
                }

                try {
                    // 1. Engine RPM (PID 010C) - high priority
                    val rpmResp = transport.sendCommand("010C")
                    val rpm = PidDecoders.decodeRpm(rpmResp)

                    // 2. Vehicle Speed (PID 010D) - high priority
                    val spdResp = transport.sendCommand("010D")
                    val speed = PidDecoders.decodeSpeed(spdResp)

                    // Measure sampling frequency
                    packetCount += 2
                    val now = System.currentTimeMillis()
                    if (now - lastHzCalculationTime >= 1000) {
                        currentHz = (packetCount * 1000f) / (now - lastHzCalculationTime)
                        packetCount = 0
                        lastHzCalculationTime = now
                    }

                    // Estimate gear based on speed and RPM for Kia Sportage 6-speed automatic
                    val gear = estimateGear(speed ?: 0, rpm ?: 0)

                    // Lower-frequency secondary PIDs (rotation independent of
                    // the Hz sample counter; only polls PIDs the ECU supports)
                    var coolant = _telemetry.value.coolantTempC
                    var intake = _telemetry.value.intakeTempC
                    var fuel = _telemetry.value.fuelLevelPct
                    var throttle = _telemetry.value.throttlePct
                    var load = _telemetry.value.engineLoadPct
                    var voltage = _telemetry.value.batteryVoltage
                    var maf = _telemetry.value.mafGs
                    var stft = _telemetry.value.shortTermFuelTrimPct
                    var ltft = _telemetry.value.longTermFuelTrimPct
                    var timing = _telemetry.value.timingAdvanceDeg

                    val supported = _supportedPids.value

                    fun pidSupported(pid: String) = supported.isEmpty() || supported.contains(pid)

                    when (auxPidCycle % 10) {
                        0 -> if (pidSupported("05")) {
                            val resp = transport.sendCommand("0105")
                            coolant = PidDecoders.decodeCoolantTemp(resp) ?: coolant
                        }
                        1 -> if (pidSupported("0F")) {
                            val resp = transport.sendCommand("010F")
                            intake = PidDecoders.decodeIntakeTemp(resp) ?: intake
                        }
                        2 -> if (pidSupported("2F")) {
                            val resp = transport.sendCommand("012F")
                            fuel = PidDecoders.decodeFuelLevel(resp) ?: fuel
                        }
                        3 -> if (pidSupported("11")) {
                            val resp = transport.sendCommand("0111")
                            throttle = PidDecoders.decodeThrottle(resp) ?: throttle
                        }
                        4 -> if (pidSupported("04")) {
                            val resp = transport.sendCommand("0104")
                            load = PidDecoders.decodeLoad(resp) ?: load
                        }
                        5 -> {
                            val resp = transport.sendCommand("ATRV")
                            voltage = PidDecoders.decodeBatteryVoltage(resp) ?: voltage
                        }
                        6 -> if (pidSupported("10")) {
                            val resp = transport.sendCommand("0110")
                            maf = PidDecoders.decodeMaf(resp) ?: maf
                        }
                        7 -> if (pidSupported("06")) {
                            val resp = transport.sendCommand("0106")
                            stft = PidDecoders.decodeShortTermFuelTrim(resp) ?: stft
                        }
                        8 -> if (pidSupported("07")) {
                            val resp = transport.sendCommand("0107")
                            ltft = PidDecoders.decodeLongTermFuelTrim(resp) ?: ltft
                        }
                        9 -> if (pidSupported("0E")) {
                            val resp = transport.sendCommand("010E")
                            timing = PidDecoders.decodeTimingAdvance(resp) ?: timing
                        }
                    }
                    auxPidCycle = (auxPidCycle + 1) % 10

                    // Fuel economy from MAF: stoich AFR 14.7, gasoline ~0.745 g/ml.
                    // L/h = maf(g/s) * 3.6 / (14.7 * 0.745); L/100km = L/h / kmh * 100.
                    var economy: Float? = null
                    if (maf != null && speed != null && speed > 3) {
                        val litersPerHour = maf * 3.6f / (14.7f * 0.745f)
                        economy = litersPerHour / speed * 100f
                        val dtSec = (now - lastFuelSampleAtMs) / 1000f
                        if (dtSec in 0f..30f) {
                            fuelUsedLiters += litersPerHour * dtSec / 3600f
                        }
                    }
                    lastFuelSampleAtMs = now

                    val newTelemetry = ObdTelemetry(
                        speedKmh = speed,
                        rpm = rpm,
                        coolantTempC = coolant,
                        intakeTempC = intake,
                        fuelLevelPct = fuel,
                        throttlePct = throttle,
                        engineLoadPct = load,
                        batteryVoltage = voltage,
                        mafGs = maf,
                        shortTermFuelTrimPct = stft,
                        longTermFuelTrimPct = ltft,
                        timingAdvanceDeg = timing,
                        fuelEconomyL100 = economy,
                        fuelUsedLiters = fuelUsedLiters,
                        estimatedGear = gear,
                        timestamp = now,
                        sampleRateHz = currentHz
                    )

                    _telemetry.value = newTelemetry
                    alertEngine?.evaluate(newTelemetry)
                    val criticalAlert = alertEngine?.activeAlerts?.value?.firstOrNull { it.severity == AlertSeverity.CRITICAL }
                    if (criticalAlert != null && now - lastAlertNotificationAtMs >= 60_000L) {
                        lastAlertNotificationAtMs = now
                        postAlertNotification(criticalAlert)
                    } else if (criticalAlert == null) {
                        lastAlertNotificationAtMs = 0L
                    }
                    csvLogger?.log(newTelemetry)

                    // Mode 06 and J1979-2 share the socket with the fast PIDs, so they get one
                    // slot every 400 ms instead of their own coroutine.
                    pollExtendedDiagnostics(now)

                    delay(30) // Fast cycle rate
                } catch (e: Exception) {
                    if (isActive && activeDevice != null) {
                        scheduleReconnect()
                        break
                    }
                }
            }
        }
    }

    // --- Extended diagnostics polling (Mode 06 / J1979-2) ---

    /**
     * Runs at most one extended request per slot, alternating between the Mode 06 monitors and the
     * J1979-2 parameters. A user-triggered refresh takes the whole slot so it completes in one go.
     */
    private suspend fun pollExtendedDiagnostics(now: Long) {
        if (now - lastExtendedPollAtMs < EXTENDED_POLL_INTERVAL_MS) return
        lastExtendedPollAtMs = now
        try {
            if (monitorScanRequested) {
                monitorScanRequested = false
                scanAllMonitorTests()
                return
            }
            if (extendedCursor % 2 == 0) pollNextMonitorTest() else pollNextJ1979Pid(now)
            extendedCursor++
        } catch (_: Exception) {
            // An unsupported or malformed extended response must never take the telemetry loop down.
        }
    }

    /**
     * TIDs the ECU advertised, minus the ones that turned out not to answer, falling back to the
     * curated probe list when the vehicle does not publish a `06 00` bitmap.
     */
    private fun pollableMonitorTids(): List<Int> =
        monitorTestTids.ifEmpty { Mode06Parser.COMMON_TEST_IDS }.filterNot { it in unsupportedMonitorTids }

    private suspend fun pollNextMonitorTest() {
        val tids = pollableMonitorTids()
        if (tids.isEmpty()) return
        val tid = tids[(extendedCursor / 2) % tids.size]
        val results = queryMonitorTest(tid) ?: return
        mergeMonitorTests(results)
    }

    /**
     * @return the parsed results for [tid], or null when the ECU does not implement it (the TID is
     *         then cached so it is never requested again in this session).
     */
    private suspend fun queryMonitorTest(tid: Int): List<MonitorTestResult>? {
        val response = transport.sendCommand("06%02X".format(tid))
        if (Mode06Parser.isNoData(response) || UdsDecoding.normalize(response).startsWith("7F")) {
            unsupportedMonitorTids += tid
            return null
        }
        // An empty parse still means "supported but nothing reported right now", so the values are
        // merged as-is rather than marking the TID unsupported.
        return Mode06Parser.parse(response)
    }

    /** Replaces every existing result carrying the same TID, keeping the other monitors intact. */
    private fun mergeMonitorTests(results: List<MonitorTestResult>) {
        val touched = results.map { it.tid }.toSet()
        if (touched.isEmpty()) return
        val merged = (_monitorTests.value.filterNot { it.tid in touched } + results)
            .sortedWith(compareBy({ it.tid }, { it.cid }))
        _monitorTests.value = merged
        _monitorTestsUpdatedAt.value = System.currentTimeMillis()
    }

    /** Full pass used by the "Scan monitors" button; bounded so a slow bus cannot stall the UI. */
    private suspend fun scanAllMonitorTests() {
        val tids = pollableMonitorTids().take(MAX_MONITOR_SCAN_TIDS)
        val findings = ArrayList<MonitorTestResult>()
        val queried = mutableSetOf<Int>()
        for (tid in tids) {
            queried += tid
            queryMonitorTest(tid)?.let { findings += it }
        }
        // Only the TIDs just asked about are replaced; monitors that were not part of this pass (for
        // example beyond the cap) keep the values read earlier in the session.
        val merged = (_monitorTests.value.filterNot { it.tid in queried } + findings)
            .sortedWith(compareBy({ it.tid }, { it.cid }))
        _monitorTests.value = merged
        // Stamped even when nothing answered: the UI leaves its "scanning" state on this value, and
        // a vehicle with no Mode 06 support would otherwise spin forever.
        _monitorTestsUpdatedAt.value = System.currentTimeMillis()
    }

    private suspend fun pollNextJ1979Pid(now: Long) {
        val available = J1979Pid.entries.filterNot { it.did in unsupportedJ1979Dids }
        if (available.isEmpty()) return
        val target = available[j1979Cursor % available.size]
        j1979Cursor++

        val response = transport.sendCommand(target.command)
        if (Mode06Parser.isNoData(response) || UdsDecoding.normalize(response).startsWith("7F")) {
            // Pre-J1979-2 vehicles simply do not implement these DIDs; stop asking.
            unsupportedJ1979Dids += target.did
            _j1979Readings.value = _j1979Readings.value.filterNot { it.pid == target }
            return
        }
        val payload = UdsDecoding.payload(response, 0x22, target.did) ?: return
        val value = target.decode(payload) ?: return
        _j1979Readings.value = (
            _j1979Readings.value.filterNot { it.pid == target } + J1979Reading(target, value, now)
            ).sortedBy { it.pid.ordinal }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun scheduleReconnect() {
        stopPolling()
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            var attempt = 0
            val target = activeDevice ?: return@launch
            while (isActive && activeDevice != null) {
                attempt++
                val backoffSec = min(16, 1 shl (attempt - 1))
                updateNotification("Reconnecting in ${backoffSec}s (Attempt $attempt)...")
                delay(backoffSec * 1000L)

                if (!isActive || activeDevice == null) break

                updateNotification("Reconnecting to ${BluetoothDeviceInfo.displayName(this@ObdService, target)}...")
                val ok = try {
                    transport.connect(target)
                } catch (_: SecurityException) {
                    false
                } catch (_: Exception) {
                    false
                }
                if (ok) {
                    updateNotification("Connected to ${BluetoothDeviceInfo.displayName(this@ObdService, target)}")
                    initializeElm327()
                    discoverVehicleCapabilities()
                    startPollingLoop()
                    break
                }
            }
        }
    }

    private fun estimateGear(speedKmh: Int, rpm: Int): Int {
        if (speedKmh <= 2 || rpm < 600) return 0 // P / N
        val ratio = rpm.toFloat() / speedKmh.toFloat()
        return when {
            ratio > 80f -> 1
            ratio in 55f..80f -> 2
            ratio in 38f..55f -> 3
            ratio in 28f..38f -> 4
            ratio in 22f..28f -> 5
            else -> 6
        }
    }

    private fun rememberDevice(device: BluetoothDevice) {
        val address = BluetoothDeviceInfo.address(this, device) ?: return
        prefs().edit()
            .putString(KEY_LAST_DEVICE, address)
            .putBoolean(KEY_LAST_DEVICE_IS_BLE, useBle)
            .apply()
    }

    private fun forgetDevice() {
        prefs().edit()
            .remove(KEY_LAST_DEVICE)
            .remove(KEY_LAST_DEVICE_IS_BLE)
            .apply()
    }

    private fun prefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Rebuilds the link after a sticky restart using the adapter MAC persisted on the last
     * successful connect. Silent no-op when Bluetooth is off, permission is missing, or the
     * address is unknown.
     */
    private fun restoreLastDevice() {
        if (activeDevice != null) return
        val address = prefs().getString(KEY_LAST_DEVICE, null) ?: return
        val ble = prefs().getBoolean(KEY_LAST_DEVICE_IS_BLE, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            updateNotification("Bluetooth permission required to restore link")
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) {
            updateNotification("Bluetooth is off · waiting for adapter")
            return
        }
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (_: Exception) {
            forgetDevice()
            return
        }
        updateNotification("Restoring link to ${BluetoothDeviceInfo.displayName(this, device)}...")
        connectToDevice(device, ble)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Sportage OBD Live Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps ELM327 OBD-II streaming active in background"
                setShowBadge(false)
            }
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Vehicle Warning Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Threshold warnings such as engine overheating"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(alertChannel)
        }
    }

    private fun postAlertNotification(alert: ActiveAlert) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Vehicle Warning")
            .setContentText(alert.message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sportage OBD-II Intelligence")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        // Some head-unit ROMs throw from notify() (missing POST_NOTIFICATIONS handling,
        // broken NotificationManager); a status update must never kill the service.
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopPolling()
        reconnectJob?.cancel()
        serviceScope.cancel()
        transport.disconnect()
    }
}
