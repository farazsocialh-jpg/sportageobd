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

    private var demoJob: Job? = null
    private var serviceCollectorJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? ObdService.ObdServiceBinder
            obdService = binder?.getService()
            isBound = true

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
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            obdService = null
            isBound = false
            serviceCollectorJob?.cancel()
        }
    }

    init {
        startAndBindService()
    }

    fun startAndBindService() {
        val intent = Intent(context, ObdService::class.java).apply {
            action = ObdService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (_: Exception) {}
            isBound = false
        }
    }

    fun connectDevice(device: BluetoothDevice) {
        if (_isDemoMode.value) {
            setDemoMode(false)
        }
        obdService?.connectToDevice(device)
    }

    fun disconnect() {
        obdService?.disconnect()
        if (_isDemoMode.value) {
            setDemoMode(false)
        }
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
            _connectionState.value = ConnectionState.Connected(
                deviceName = "Virtual Bench ECU (2026 Sportage)",
                address = "BENCH-MPI-AWD",
                protocol = "ISO 15765-4 (CAN 11/500)"
            )
            startDemoSimulation()
        } else {
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
            true
        } else {
            obdService?.clearDtcCodes() ?: false
        }
    }

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
                    estimatedGear = gear,
                    timestamp = System.currentTimeMillis(),
                    sampleRateHz = 16.5f
                )
                delay(60)
            }
        }
    }
}
