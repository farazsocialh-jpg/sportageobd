package com.sportage.obd.viewmodel

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sportage.obd.DashboardTheme
import com.sportage.obd.VehicleTelemetry
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.service.ObdTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Application.dashboardThemeDataStore: DataStore<Preferences> by preferencesDataStore(name = "dashboard_theme_prefs")

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val obdViewModel = ObdViewModel(application)

    private val _currentTheme = MutableStateFlow(DashboardTheme.ORIGINAL)
    val currentTheme: StateFlow<DashboardTheme> = _currentTheme

    val telemetry: StateFlow<VehicleTelemetry> = combine(
        obdViewModel.telemetry,
        obdViewModel.connectionState
    ) { telemetry: ObdTelemetry, connection: ConnectionState ->
        VehicleTelemetry(
            speedKmh = telemetry.speedKmh ?: 0,
            rpm = telemetry.rpm ?: 0,
            coolantTempC = telemetry.coolantTempC ?: 0,
            fuelLevelPercent = (telemetry.fuelLevelPct ?: 0).toFloat(),
            batteryVoltage = telemetry.batteryVoltage ?: 0f,
            currentGear = gearLabel(telemetry.transmissionGear, telemetry.estimatedGear),
            isConnected = connection is ConnectionState.Connected
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VehicleTelemetry()
    )

    init {
        viewModelScope.launch {
            val stored = getApplication<Application>().dashboardThemeDataStore.data
                .map { prefs -> prefs[THEME_KEY] }
                .first()
            _currentTheme.value = DashboardTheme.fromId(stored)
        }
    }

    fun setTheme(theme: DashboardTheme) {
        _currentTheme.value = theme
        viewModelScope.launch {
            getApplication<Application>().dashboardThemeDataStore.edit { prefs ->
                prefs[THEME_KEY] = theme.id
            }
        }
    }

    companion object {
        private val THEME_KEY = stringPreferencesKey("dashboard_theme")
    }
}

private fun gearLabel(transmissionGear: Int?, estimatedGear: Int): String {
    return when (transmissionGear) {
        -2 -> "P"
        -1 -> "R"
        0 -> "N"
        in 1..10 -> "D$transmissionGear"
        else -> if (estimatedGear > 0) "D$estimatedGear" else "P"
    }
}