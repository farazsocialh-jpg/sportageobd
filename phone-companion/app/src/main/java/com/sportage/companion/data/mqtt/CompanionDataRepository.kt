package com.sportage.companion.data.mqtt

import com.sportage.companion.data.config.MqttConfigRepository
import com.sportage.companion.data.model.ConnectionState
import com.sportage.companion.data.model.LiveTelemetry
import com.sportage.companion.data.model.ParkedLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CompanionDataRepository(
    private val configRepo: MqttConfigRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _telemetry = MutableStateFlow(LiveTelemetry())
    val telemetry: StateFlow<LiveTelemetry> = _telemetry.asStateFlow()

    private val _parkedLocation = MutableStateFlow<ParkedLocation?>(null)
    val parkedLocation: StateFlow<ParkedLocation?> = _parkedLocation.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isDriving = MutableStateFlow(false)
    val isDriving: StateFlow<Boolean> = _isDriving.asStateFlow()

    private var activeJob: Job? = null
    private var activeManager: MqttManager? = null

    init {
        scope.launch {
            configRepo.profile.collect { profile ->
                activeJob?.cancel()
                activeManager?.disconnect()
                activeManager = null
                _telemetry.value = LiveTelemetry()
                _isDriving.value = false
                if (profile == null) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                } else {
                    _connectionState.value = ConnectionState.CONNECTING
                    val manager = MqttManager(profile, scope)
                    activeManager = manager
                    val job = scope.launch {
                        launch { manager.telemetry.collect { _telemetry.value = it } }
                        launch { manager.parkedLocation.collect { loc -> if (loc != null) _parkedLocation.value = loc } }
                        launch { manager.connectionState.collect { _connectionState.value = it } }
                        launch { manager.isDriving.collect { _isDriving.value = it } }
                    }
                    activeJob = job
                    manager.connect()
                }
            }
        }
    }

    fun shutdown() {
        activeManager?.disconnect()
        scope.cancel()
    }
}