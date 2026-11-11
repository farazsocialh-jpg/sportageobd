package com.sportage.companion.viewmodel

import androidx.lifecycle.ViewModel
import com.sportage.companion.data.model.ConnectionStatus
import com.sportage.companion.data.model.LiveTelemetry
import com.sportage.companion.data.mqtt.CompanionDataRepository
import kotlinx.coroutines.flow.StateFlow

data class DashboardUiState(
    val telemetry: LiveTelemetry = LiveTelemetry(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTING,
    val isDriving: Boolean = false
)

class DashboardViewModel(
    private val repo: CompanionDataRepository
) : ViewModel() {
    val telemetry: StateFlow<LiveTelemetry> = repo.telemetry
    val connectionStatus: StateFlow<ConnectionStatus> = repo.connectionStatus
    val isDriving: StateFlow<Boolean> = repo.isDriving
}