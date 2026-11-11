package com.sportage.companion.data.mqtt

import com.sportage.companion.data.model.ConnectionStatus
import com.sportage.companion.data.model.LiveTelemetry
import com.sportage.companion.data.model.ParkedLocation
import com.sportage.companion.data.vehicle.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CompanionDataRepository(
    private val vehicleRepo: VehicleRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _telemetry = MutableStateFlow(LiveTelemetry())
    val telemetry: StateFlow<LiveTelemetry> = _telemetry.asStateFlow()

    private val _parkedLocation = MutableStateFlow<ParkedLocation?>(null)
    val parkedLocation: StateFlow<ParkedLocation?> = _parkedLocation.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTING)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _isDriving = MutableStateFlow(false)
    val isDriving: StateFlow<Boolean> = _isDriving.asStateFlow()

    private var mqtt: MqttManager? = null

    init {
        scope.launch {
            vehicleRepo.pairedVehicle.collect { vehicle ->
                mqtt?.disconnect()
                mqtt = null
                if (vehicle == null) {
                    _connectionStatus.value = ConnectionStatus.OFFLINE_STALE
                } else {
                    _connectionStatus.value = ConnectionStatus.CONNECTING
                    val m = MqttManager(vehicle, scope)
                    mqtt = m
                    m.connect()
                    scope.launch { m.telemetry.collect { _telemetry.value = it } }
                    scope.launch { m.connectionStatus.collect { _connectionStatus.value = it } }
                    scope.launch { m.isDriving.collect { _isDriving.value = it } }
                    scope.launch { m.parkedLocation.collect { loc -> if (loc != null) _parkedLocation.value = loc } }
                }
            }
        }
    }

    fun shutdown() { mqtt?.disconnect() }
}