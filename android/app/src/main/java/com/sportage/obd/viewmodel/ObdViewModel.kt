package com.sportage.obd.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.obd.FreezeFrame
import com.sportage.obd.obd.ReadinessState
import com.sportage.obd.repository.ObdRepository
import com.sportage.obd.service.ActiveAlert
import com.sportage.obd.service.AlertThresholds
import com.sportage.obd.service.ObdTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ObdViewModel: Bridges the ObdRepository to Jetpack Compose UI.
 * Handles UI state derivation, graph telemetry history, trip stats,
 * and background operations cleanly decoupled from the UI.
 */
class ObdViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ObdRepository(application.applicationContext)

    val telemetry: StateFlow<ObdTelemetry> = repository.telemetry
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ObdTelemetry())

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    val isPollingPaused: StateFlow<Boolean> = repository.isPollingPaused
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isDemoMode: StateFlow<Boolean> = repository.isDemoMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dtcList: StateFlow<List<DtcItem>> = repository.dtcList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingDtcList: StateFlow<List<DtcItem>> = repository.pendingDtcList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val permanentDtcList: StateFlow<List<DtcItem>> = repository.permanentDtcList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val freezeFrame: StateFlow<FreezeFrame?> = repository.freezeFrame
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val readiness: StateFlow<ReadinessState?> = repository.readiness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val vin: StateFlow<String?> = repository.vin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val supportedPids: StateFlow<Set<String>> = repository.supportedPids
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val activeAlerts: StateFlow<List<ActiveAlert>> = repository.activeAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isCsvLogging: StateFlow<Boolean> = repository.isCsvLogging
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isScanningDtc = MutableStateFlow(false)
    val isScanningDtc: StateFlow<Boolean> = _isScanningDtc.asStateFlow()

    // Real-time telemetry history for live graphs (sliding window)
    private val _speedHistory = MutableStateFlow<List<Pair<Long, Int>>>(emptyList())
    val speedHistory: StateFlow<List<Pair<Long, Int>>> = _speedHistory.asStateFlow()

    private val _rpmHistory = MutableStateFlow<List<Pair<Long, Int>>>(emptyList())
    val rpmHistory: StateFlow<List<Pair<Long, Int>>> = _rpmHistory.asStateFlow()

    init {
        // Collect telemetry and append to history window (last 60 data points)
        viewModelScope.launch {
            repository.telemetry.collect { telem ->
                val now = telem.timestamp
                val spd = telem.speedKmh
                val rpm = telem.rpm

                if (spd != null) {
                    val updatedSpd = (_speedHistory.value + Pair(now, spd)).takeLast(60)
                    _speedHistory.value = updatedSpd
                }
                if (rpm != null) {
                    val updatedRpm = (_rpmHistory.value + Pair(now, rpm)).takeLast(60)
                    _rpmHistory.value = updatedRpm
                }
            }
        }
    }

    fun connectDevice(device: BluetoothDevice, ble: Boolean = false) {
        repository.connectDevice(device, ble)
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun togglePausePolling(): Boolean {
        val next = !repository.isPollingPaused.value
        repository.togglePausePolling(next)
        return next
    }

    fun isConnected(): Boolean = connectionState.value is ConnectionState.Connected

    fun setDemoMode(enable: Boolean) {
        repository.setDemoMode(enable)
    }

    suspend fun scanDtcNow(): List<DtcItem> {
        _isScanningDtc.value = true
        return try {
            repository.scanDtcCodes()
        } finally {
            _isScanningDtc.value = false
        }
    }

    suspend fun scanPendingDtcsNow(): List<DtcItem> = repository.scanPendingDtcs()

    suspend fun scanPermanentDtcsNow(): List<DtcItem> = repository.scanPermanentDtcs()

    suspend fun readFreezeFrameNow(): FreezeFrame? = repository.readFreezeFrame()

    suspend fun readReadinessNow(): ReadinessState? = repository.readReadiness()

    suspend fun readVinNow(): String? = repository.readVin()

    suspend fun clearDtcNow(): Boolean = repository.clearDtcCodes()

    fun alertThresholds(): AlertThresholds = repository.alertThresholds()

    fun updateAlertThresholds(thresholds: AlertThresholds) {
        repository.updateAlertThresholds(thresholds)
    }

    fun startCsvLogging() = repository.startCsvLogging()

    fun stopCsvLogging() = repository.stopCsvLogging()

    fun csvLogFile(): java.io.File? = repository.csvLogFile()

    fun csvAutostart(): Boolean = repository.csvAutostart()

    fun setCsvAutostart(enabled: Boolean) = repository.setCsvAutostart(enabled)

    suspend fun sendRawCommand(cmd: String): String {
        return repository.sendRawCommand(cmd)
    }

    override fun onCleared() {
        super.onCleared()
        // Unbinding leaves the foreground service (and the Bluetooth link) running on purpose:
        // only the UI teardown happens here.
        repository.close()
    }
}
