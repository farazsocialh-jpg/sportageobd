package com.sportage.companion.data.vehicle

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sportage.companion.data.model.VehicleProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vehicleDataStore by preferencesDataStore(name = "vehicle_prefs")

class VehicleRepository(private val context: Context) {

    private val KEY_USER_ID = stringPreferencesKey("user_id")
    private val KEY_VEHICLE_ID = stringPreferencesKey("vehicle_id")
    private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")

    val pairedVehicle: Flow<VehicleProfile?> = context.vehicleDataStore.data.map { prefs ->
        val uid = prefs[KEY_USER_ID]
        val vid = prefs[KEY_VEHICLE_ID]
        if (uid.isNullOrBlank() || vid.isNullOrBlank()) null
        else VehicleProfile(uid, vid, prefs[KEY_DISPLAY_NAME] ?: "Sportage")
    }

    suspend fun pair(userId: String, vehicleId: String, displayName: String = "Sportage") {
        context.vehicleDataStore.edit { prefs ->
            prefs[KEY_USER_ID] = userId
            prefs[KEY_VEHICLE_ID] = vehicleId
            prefs[KEY_DISPLAY_NAME] = displayName
        }
    }

    suspend fun unpair() {
        context.vehicleDataStore.edit { it.clear() }
    }
}