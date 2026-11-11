package com.sportage.phonecompanion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportage.phonecompanion.model.ParkedLocation
import com.sportage.phonecompanion.model.Telemetry
import com.sportage.phonecompanion.mqtt.PhoneMqttSubscriber
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CompanionViewModel(
    private val subscriber: PhoneMqttSubscriber = PhoneMqttSubscriber()
) : ViewModel() {

    val telemetry: StateFlow<Telemetry> = subscriber.telemetry
    val parkedLocation: StateFlow<ParkedLocation?> = subscriber.parkedLocation
    val connected: StateFlow<Boolean> = subscriber.connected
    val isDriving: StateFlow<Boolean> = subscriber.isDriving

    val parkedDurationText: StateFlow<String> = parkedLocation.map { loc ->
        if (loc == null) "No parked location yet"
        else {
            val diffMs = System.currentTimeMillis() - loc.timestamp
            val minutes = diffMs / 60_000L
            val hours = minutes / 60L
            when {
                hours > 0 -> "Parked ${hours}h ${minutes % 60}m ago"
                minutes > 0 -> "Parked ${minutes}m ago"
                else -> "Parked just now"
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "No parked location yet")

    init {
        subscriber.connect()
    }

    override fun onCleared() {
        super.onCleared()
        subscriber.disconnect()
    }
}