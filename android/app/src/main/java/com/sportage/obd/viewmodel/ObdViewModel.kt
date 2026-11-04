package com.sportage.obd.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.repository.ObdRepository
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

    fun connectDevice(device: BluetoothDevice) {
        repository.connectDevice(device)
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun togglePausePolling() {
        val next = !repository.isPollingPaused.value
        repository.togglePausePolling(next)
    }

    fun setDemoMode(enable: Boolean) {
        repository.setDemoMode(enable)
    }

    fun scanDtcCodes() {
        viewModelScope.launch {
            _isScanningDtc.value = true
            try {
                repository.scanDtcCodes()
            } finally {
                _isScanningDtc.value = false
            }
        }
    }

    fun clearDtcCodes() {
        viewModelScope.launch {
            repository.clearDtcCodes()
        }
    }

    suspend fun sendRawCommand(cmd: String): String {
        return repository.sendRawCommand(cmd)
    }

    override fun onCleared() {
        super.onCleared()
        repository.unbindService()
    }
}
