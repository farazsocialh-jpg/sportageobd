package com.sportage.companion.viewmodel

import androidx.lifecycle.ViewModel
import com.sportage.companion.data.model.ConnectionState
import com.sportage.companion.data.model.LiveTelemetry
import com.sportage.companion.data.mqtt.CompanionDataRepository
import kotlinx.coroutines.flow.StateFlow

class DashboardViewModel(
    private val repo: CompanionDataRepository
) : ViewModel() {
    val telemetry: StateFlow<LiveTelemetry> = repo.telemetry
    val connectionState: StateFlow<ConnectionState> = repo.connectionState
    val isDriving: StateFlow<Boolean> = repo.isDriving
}