package com.sportage.companion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportage.companion.data.model.ConnectionState
import com.sportage.companion.data.model.LiveTelemetry
import com.sportage.companion.data.mqtt.CompanionDataRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repo: CompanionDataRepository
) : ViewModel() {
    val telemetry: StateFlow<LiveTelemetry> = repo.telemetry
    val connectionState: StateFlow<ConnectionState> = repo.connectionState
    val isDriving: StateFlow<Boolean> = repo.isDriving

    private val _isLive = MutableStateFlow(false)
    val isLive: StateFlow<Boolean> = _isLive.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                val last = repo.lastTelemetryAt.value
                _isLive.value = last > 0L && (System.currentTimeMillis() - last) < 5_000L
                delay(1_000L)
            }
        }
    }
}