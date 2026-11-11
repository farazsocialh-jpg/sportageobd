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
    private val _telemetry = MutableStateFlow(LiveTelemetry())
    val telemetry: StateFlow<LiveTelemetry> = _telemetry.asStateFlow()
    val connectionState: StateFlow<ConnectionState> = repo.connectionState
    val isDriving: StateFlow<Boolean> = repo.isDriving

    private val _isLive = MutableStateFlow(false)
    val isLive: StateFlow<Boolean> = _isLive.asStateFlow()

    init {
        viewModelScope.launch {
            repo.telemetry.collect { incoming ->
                _telemetry.value = incoming
            }
        }
        viewModelScope.launch {
            while (isActive) {
                val last = repo.lastTelemetryAt.value
                _isLive.value = last > 0L && (System.currentTimeMillis() - last) < 5_000L
                delay(1_000L)
            }
        }
    }
}