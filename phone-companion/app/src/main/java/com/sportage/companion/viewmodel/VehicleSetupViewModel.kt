package com.sportage.companion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportage.companion.data.auth.AuthRepository
import com.sportage.companion.data.vehicle.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VehicleSetupUiState(
    val vehicleId: String = "",
    val displayName: String = "Sportage",
    val error: String? = null,
    val paired: Boolean = false,
    val qrPayload: String? = null
)

class VehicleSetupViewModel(
    private val vehicleRepo: VehicleRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(VehicleSetupUiState())
    val state: StateFlow<VehicleSetupUiState> = _state.asStateFlow()

    val pairedVehicle = vehicleRepo.pairedVehicle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onVehicleIdChange(v: String) { _state.value = _state.value.copy(vehicleId = v) }
    fun onDisplayNameChange(v: String) { _state.value = _state.value.copy(displayName = v) }

    fun pair() {
        val v = _state.value.vehicleId.trim()
        if (v.isBlank()) {
            _state.value = _state.value.copy(error = "Vehicle ID is required")
            return
        }
        viewModelScope.launch {
            val uid = authRepo.currentUser.first()?.uid
            if (uid.isNullOrBlank()) {
                _state.value = _state.value.copy(error = "Not authenticated")
                return@launch
            }
            vehicleRepo.pair(uid, v, _state.value.displayName.trim().ifBlank { "Sportage" })
            val payload = buildQrPayload(uid, v)
            _state.value = _state.value.copy(paired = true, error = null, qrPayload = payload)
        }
    }

    fun generatePairing() {
        viewModelScope.launch {
            val uid = authRepo.currentUser.first()?.uid
            if (uid.isNullOrBlank()) {
                _state.value = _state.value.copy(error = "Not authenticated")
                return@launch
            }
            val vid = "sportage_${uid.take(6)}_${(100000..999999).random()}"
            vehicleRepo.pair(uid, vid, _state.value.displayName.trim().ifBlank { "Sportage" })
            val payload = buildQrPayload(uid, vid)
            _state.value = _state.value.copy(vehicleId = vid, paired = true, error = null, qrPayload = payload)
        }
    }

    private fun buildQrPayload(uid: String, vid: String): String =
        org.json.JSONObject().apply {
            put("vehicleId", vid)
            put("userId", uid)
            put("mqttUser", com.sportage.companion.data.mqtt.MqttConfig.USERNAME)
            put("mqttPass", com.sportage.companion.data.mqtt.MqttConfig.PASSWORD)
        }.toString()

    fun unpair() {
        viewModelScope.launch {
            vehicleRepo.unpair()
            _state.value = VehicleSetupUiState()
        }
    }
}