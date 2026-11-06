package com.sportage.obd.repository

import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.obd.DtcSeverity
import com.sportage.obd.obd.FreezeFrame
import com.sportage.obd.obd.J1979Pid
import com.sportage.obd.obd.J1979Reading
import com.sportage.obd.obd.MonitorTestResult
import com.sportage.obd.obd.PidDecoders
import com.sportage.obd.obd.ReadinessState
import com.sportage.obd.service.ActiveAlert
import com.sportage.obd.service.AlertThresholds
import com.sportage.obd.service.ObdService
import com.sportage.obd.service.ObdTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * ObdRepository: Single source of truth for OBD-II vehicle state.
 * Manages foreground service binding, handles bench demo telemetry generation,
 * and exposes clean reactive StateFlows to ViewModels and UI.
 */
class ObdRepository(private val context: Context) {

    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + repositoryJob)

    private var obdService: ObdService? = null
    private var isBound = false
    private var pendingDevice: BluetoothDevice? = null
    private var pendingDeviceIsBle = false

    private val _telemetry = MutableStateFlow(ObdTelemetry())
    val telemetry: StateFlow<ObdTelemetry> = _telemetry.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isPollingPaused = MutableStateFlow(false)
    val isPollingPaused: StateFlow<Boolean> = _isPollingPaused.asStateFlow()

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

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

    private val _activeAlerts = MutableStateFlow<List<ActiveAlert>>(emptyList())
    val activeAlerts: StateFlow<List<ActiveAlert>> = _activeAlerts.asStateFlow()

    private val _isCsvLogging = MutableStateFlow(false)
    val isCsvLogging: StateFlow<Boolean> = _isCsvLogging.asStateFlow()

    private val _monitorTests = MutableStateFlow<List<MonitorTestResult>>(emptyList())
    val monitorTests: StateFlow<List<MonitorTestResult>> = _monitorTests.asStateFlow()

    private val _monitorTestsUpdatedAt = MutableStateFlow(0L)
    val monitorTestsUpdatedAt: StateFlow<Long> = _monitorTestsUpdatedAt.asStateFlow()

    private val _j1979Readings = MutableStateFlow<List<J1979Reading>>(emptyList())
    val j1979Readings: StateFlow<List<J1979Reading>> = _j1979Readings.asStateFlow()

    private var demoJob: Job? = null
    private var serviceCollectorJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? ObdService.ObdServiceBinder
            obdService = binder?.getService()
            isBound = true

            // A connect request that arrived before the binding completed is applied now.
            pendingDevice?.let { device ->
                val ble = pendingDeviceIsBle
                pendingDevice = null
                obdService?.connectToDevice(device, ble)
            }

            // Forward telemetry and states from ObdService
            serviceCollectorJob?.cancel()
            serviceCollectorJob = scope.launch {
                val s = obdService ?: return@launch
                launch {
                    s.telemetry.collect {
                        if (!_isDemoMode.value) {
                            _telemetry.value = it
                        }
                    }
                }
                launch {
                    s.connectionState.collect {
                        if (!_isDemoMode.value) {
                            _connectionState.value = it
                        }
                    }
                }
                launch {
                    s.isPollingPaused.collect {
                        _isPollingPaused.value = it
                    }
                }
                launch {
                    s.dtcList.collect {
                        if (!_isDemoMode.value) {
                            _dtcList.value = it
                        }
                    }
                }
                launch {
                    s.pendingDtcList.collect {
                        if (!_isDemoMode.value) {
                            _pendingDtcList.value = it
                        }
                    }
                }
                launch {
                    s.permanentDtcList.collect {
                        if (!_isDemoMode.value) {
                            _permanentDtcList.value = it
                        }
                    }
                }
                launch {
                    s.freezeFrame.collect {
                        if (!_isDemoMode.value) {
                            _freezeFrame.value = it
                        }
                    }
                }
                launch {
                    s.readiness.collect {
                        if (!_isDemoMode.value) {
                            _readiness.value = it
                        }
                    }
                }
                launch {
                    s.vin.collect {
                        if (!_isDemoMode.value) {
                            _vin.value = it
                        }
                    }
                }
                launch {
                    s.supportedPids.collect {
                        if (!_isDemoMode.value) {
                            _supportedPids.value = it
                        }
                    }
                }
                launch {
                    s.activeAlerts.collect {
                        if (!_isDemoMode.value) {
                            _activeAlerts.value = it
                        }
                    }
                }
                launch {
                    s.isCsvLogging.collect {
                        if (!_isDemoMode.value) {
                            _isCsvLogging.value = it
                        }
                    }
                }
                launch {
                    s.monitorTests.collect {
                        if (!_isDemoMode.value) {
                            _monitorTests.value = it
                        }
                    }
                }
                launch {
                    s.monitorTestsUpdatedAt.collect {
                        if (!_isDemoMode.value) {
                            _monitorTestsUpdatedAt.value = it
                        }
                    }
                }
                launch {
                    s.j1979Readings.collect {
                        if (!_isDemoMode.value) {
                            _j1979Readings.value = it
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            obdService = null
            isBound = false
            serviceCollectorJob?.cancel()
        }
    }

    /**
     * Starts and binds the foreground service. Called on the first connect so the service is only
     * promoted to the foreground once Bluetooth permissions have been granted.
     */
    init {
        // If the foreground service survived the activity (background streaming), re-attach to it so
        // the relaunched UI shows live telemetry instead of a disconnected cockpit.
        if (ObdService.isRunning) {
            bindService()
        }
    }

    private fun bindService() {
        val intent = Intent(context, ObdService::class.java).apply {
            action = ObdService.ACTION_START
        }
        try {
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Could not attach to OBD service: ${e.message}")
        }
    }

    fun startAndBindService() {
        val intent = Intent(context, ObdService::class.java).apply {
            action = ObdService.ACTION_START
        }
        try {
            ContextCompat.startForegroundService(context, intent)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            pendingDevice = null
            _connectionState.value = ConnectionState.Error("Background OBD service unavailable: ${e.message}")
        }
    }

    private fun stopServiceIfRunning() {
        if (!ObdService.isRunning) return
        val stopIntent = Intent(context, ObdService::class.java).apply {
            action = ObdService.ACTION_STOP
        }
        try {
            context.startService(stopIntent)
        } catch (_: Exception) {}
    }

    /** Releases the binding, collectors and coroutine scope when the UI goes away for good. */
    fun close() {
        unbindService()
        repositoryJob.cancel()
    }

    fun unbindService() {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (_: Exception) {}
            isBound = false
        }
        obdService = null
        serviceCollectorJob?.cancel()
    }

    fun connectDevice(device: BluetoothDevice, ble: Boolean = false) {
        try {
            if (_isDemoMode.value) {
                setDemoMode(false)
            }
            val service = obdService
            if (service != null) {
                service.connectToDevice(device, ble)
            } else {
                pendingDevice = device
                pendingDeviceIsBle = ble
                startAndBindService()
            }
        } catch (e: Exception) {
            // Some head-unit Bluetooth stacks throw instead of failing gracefully when the
            // RFCOMM socket cannot be opened. Surface it as an error state instead of crashing.
            _connectionState.value =
                ConnectionState.Error("Connect failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    fun disconnect() {
        obdService?.disconnect()
        if (_isDemoMode.value) {
            setDemoMode(false)
        }
        _monitorTests.value = emptyList()
        _monitorTestsUpdatedAt.value = 0L
        _j1979Readings.value = emptyList()
        // Nothing is streaming any more, so retire the foreground service and its notification;
        // the next connect restarts and rebinds it.
        stopServiceIfRunning()
        unbindService()
        _connectionState.value = ConnectionState.Disconnected
    }

    fun togglePausePolling(pause: Boolean) {
        if (pause) {
            obdService?.pausePolling()
        } else {
            obdService?.resumePolling()
        }
        _isPollingPaused.value = pause
    }

    fun setDemoMode(enable: Boolean) {
        _isDemoMode.value = enable
        demoJob?.cancel()
        if (enable) {
            obdService?.disconnect()
            stopServiceIfRunning()
            unbindService()
            _connectionState.value = ConnectionState.Connected(
                deviceName = "Virtual Bench ECU (2026 Sportage)",
                address = "BENCH-MPI-AWD",
                protocol = "ISO 15765-4 (CAN 11/500)"
            )
            startDemoSimulation()
            _monitorTests.value = demoMonitorTests()
            _monitorTestsUpdatedAt.value = System.currentTimeMillis()
            _j1979Readings.value = demoJ1979Readings()
        } else {
            _monitorTests.value = emptyList()
            _monitorTestsUpdatedAt.value = 0L
            _j1979Readings.value = emptyList()
            _connectionState.value = obdService?.connectionState?.value ?: ConnectionState.Disconnected
            _telemetry.value = obdService?.telemetry?.value ?: ObdTelemetry()
        }
    }

    suspend fun scanDtcCodes(): List<DtcItem> {
        return if (_isDemoMode.value) {
            delay(1200)
            val demoList = listOf(
                DtcItem(
                    code = "P0133",
                    system = "Powertrain / Engine",
                    description = "O2 Sensor Circuit Slow Response (Bank 1 Sensor 1)",
                    severity = DtcSeverity.WARNING,
                    probableCause = "Heated oxygen sensor aging, carbon deposits, or exhaust leak near manifold"
                ),
                DtcItem(
                    code = "P0420",
                    system = "Powertrain / Exhaust",
                    description = "Catalyst System Efficiency Below Threshold (Bank 1)",
                    severity = DtcSeverity.WARNING,
                    probableCause = "Downstream O2 sensor drift or catalytic converter substrate efficiency decline"
                )
            )
            _dtcList.value = demoList
            demoList
        } else {
            obdService?.scanDtcCodes() ?: emptyList()
        }
    }

    suspend fun clearDtcCodes(): Boolean {
        return if (_isDemoMode.value) {
            delay(800)
            _dtcList.value = emptyList()
            _pendingDtcList.value = emptyList()
            true
        } else {
            obdService?.clearDtcCodes() ?: false
        }
    }

    /** Mode 07 pending codes. */
    suspend fun scanPendingDtcs(): List<DtcItem> {
        return if (_isDemoMode.value) {
            delay(1000)
            val demoList = listOf(
                DtcItem(
                    code = "P0128",
                    system = "Powertrain / Engine",
                    description = "Coolant Thermostat Malfunction (Temperature Below Regulating)",
                    severity = DtcSeverity.ADVISORY,
                    probableCause = "Thermostat stuck open or coolant temperature sensor drift"
                )
            )
            _pendingDtcList.value = demoList
            demoList
        } else {
            obdService?.scanPendingDtcs() ?: emptyList()
        }
    }

    /** Mode 0A permanent codes. */
    suspend fun scanPermanentDtcs(): List<DtcItem> {
        return if (_isDemoMode.value) {
            delay(1000)
            val demoList = listOf(
                DtcItem(
                    code = "P0420",
                    system = "Powertrain / Exhaust",
                    description = "Catalyst System Efficiency Below Threshold (Bank 1)",
                    severity = DtcSeverity.WARNING,
                    probableCause = "Catalytic converter substrate efficiency decline"
                )
            )
            _permanentDtcList.value = demoList
            demoList
        } else {
            obdService?.scanPermanentDtcs() ?: emptyList()
        }
    }

    /** Mode 02 freeze frame. */
    suspend fun readFreezeFrame(): FreezeFrame? {
        return if (_isDemoMode.value) {
            delay(900)
            val frame = PidDecoders.decodeFreezeFrame(
                "42 04 32 42 05 87 42 0C 1A F8 42 0D 48 42 11 20 42 0F 46"
            )
            _freezeFrame.value = frame
            frame
        } else {
            obdService?.readFreezeFrame()
        }
    }

    /** Mode 01 PID 01 readiness. */
    suspend fun readReadiness(): ReadinessState? {
        return if (_isDemoMode.value) {
            delay(700)
            val state = PidDecoders.decodeReadiness("41 01 82 07 65")
            if (state != null) _readiness.value = state
            state
        } else {
            obdService?.readReadiness()
        }
    }

    /** Mode 09 PID 02 VIN. */
    suspend fun readVin(): String? {
        return if (_isDemoMode.value) {
            delay(800)
            val vin = "KNDPMCAC5N7123456"
            _vin.value = vin
            vin
        } else {
            obdService?.readVin()
        }
    }

    /** Asks the service for a fresh Mode 06 pass; the demo ECU fakes one after a short delay. */
    fun requestMonitorTestScan() {
        if (_isDemoMode.value) {
            scope.launch {
                delay(900)
                if (_isDemoMode.value) {
                    _monitorTests.value = demoMonitorTests()
                    _monitorTestsUpdatedAt.value = System.currentTimeMillis()
                }
            }
        } else {
            obdService?.requestMonitorTestScan()
        }
    }

    // --- Warning thresholds ---

    fun alertThresholds(): AlertThresholds =
        obdService?.alertThresholds() ?: AlertThresholds()

    fun updateAlertThresholds(thresholds: AlertThresholds) {
        obdService?.updateAlertThresholds(thresholds)
    }

    // --- CSV logging ---

    fun startCsvLogging(): Boolean =
        obdService?.startCsvLogging() ?: false

    fun stopCsvLogging() {
        obdService?.stopCsvLogging()
    }

    fun csvAutostart(): Boolean = obdService?.csvAutostart() ?: false

    fun setCsvAutostart(enabled: Boolean) {
        obdService?.setCsvAutostart(enabled)
    }

    /** Current log file for sharing, if a session is active. */
    fun csvLogFile(): java.io.File? = obdService?.csvLogFile

    suspend fun sendRawCommand(cmd: String): String {
        return if (_isDemoMode.value) {
            when (cmd.trim().uppercase()) {
                "ATZ" -> "ELM327 v2.2 Bench Sim"
                "0100" -> "41 00 BE 3E B8 13"
                "010C" -> "41 0C 1A F8"
                "010D" -> "41 0D 3C"
                "0105" -> "41 05 5A"
                "ATRV" -> "14.2V"
                else -> "OK"
            }
        } else {
            obdService?.sendRawCommand(cmd) ?: "Service Not Bound"
        }
    }

    /**
     * Bench Mode 06 data. Values are chosen to exercise every severity band, including one monitor
     * whose margin is all but exhausted — the case the screen exists to surface.
     */
    private fun demoMonitorTests(): List<MonitorTestResult> = listOf(
        MonitorTestResult(tid = 0x01, cid = 0x00, unitAndScale = 0x01, value = 12.0, minLimit = 0.0, maxLimit = 200.0),
        MonitorTestResult(tid = 0x02, cid = 0x00, unitAndScale = 0x01, value = 0.0, minLimit = 0.0, maxLimit = 40.0),
        MonitorTestResult(tid = 0x21, cid = 0x00, unitAndScale = 0x02, value = 0.42, minLimit = 0.0, maxLimit = 0.55),
        MonitorTestResult(tid = 0x41, cid = 0x00, unitAndScale = 0x02, value = 0.55, minLimit = 0.10, maxLimit = 1.0),
        MonitorTestResult(tid = 0x51, cid = 0x00, unitAndScale = 0x02, value = 0.98, minLimit = 0.20, maxLimit = 1.0)
    )

    private fun demoJ1979Readings(): List<J1979Reading> {
        val now = System.currentTimeMillis()
        return listOf(
            J1979Reading(J1979Pid.ENGINE_LOAD, 27.5f, now),
            J1979Reading(J1979Pid.COOLANT_TEMP, 89f, now),
            J1979Reading(J1979Pid.RPM, 2140f, now),
            J1979Reading(J1979Pid.SPEED, 62f, now),
            J1979Reading(J1979Pid.INTAKE_TEMP, 33f, now),
            J1979Reading(J1979Pid.MAF, 9.4f, now),
            J1979Reading(J1979Pid.THROTTLE, 14f, now),
            J1979Reading(J1979Pid.FUEL_LEVEL, 64f, now)
        )
    }

    private fun startDemoSimulation() {
        demoJob = scope.launch {
            var step = 0.0
            while (isActive && _isDemoMode.value) {
                step += 0.08
                val speed = ((sin(step * 0.7) + 1.0) * 45.0 + 10.0).toInt().coerceIn(0, 180)
                val rpm = ((sin(step * 1.4) + 1.0) * 1600.0 + 800.0).toInt().coerceIn(700, 6500)
                val coolant = 88 + (sin(step * 0.1) * 3).toInt()
                val intake = 32 + (sin(step * 0.05) * 2).toInt()
                val fuel = 64
                val throttle = (((sin(step * 1.4) + 1.0) * 22.0) + 8.0).toFloat().coerceIn(0f, 100f)
                val load = (((sin(step * 1.1) + 1.0) * 28.0) + 15.0).toFloat().coerceIn(0f, 100f)
                val voltage = 14.2f + (sin(step * 0.5) * 0.15).toFloat()
                val maf = (load / 100f * 60f + 2f).coerceIn(1f, 120f) // g/s estimate
                val stft = ((sin(step * 0.9) * 4.0)).toFloat()
                val ltft = 1.8f
                val timing = ((sin(step * 0.6) + 1.0) * 12.0).toFloat() // deg BTDC
                val litersPerHour = maf * 3.6f / (14.7f * 0.745f)
                val economy = if (speed > 3) litersPerHour / speed * 100f else null
                val gear = when {
                    speed < 5 -> 0
                    speed < 22 -> 1
                    speed < 42 -> 2
                    speed < 65 -> 3
                    speed < 90 -> 4
                    speed < 115 -> 5
                    else -> 6
                }

                _telemetry.value = ObdTelemetry(
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
                    estimatedGear = gear,
                    timestamp = System.currentTimeMillis(),
                    sampleRateHz = 16.5f
                )
                delay(60)
            }
        }
    }
}
