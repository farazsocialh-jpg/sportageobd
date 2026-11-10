package com.sportage.obd.map

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Data model for the vehicle's last recorded parking anchor.
 */
data class ParkedLocation(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float = 0f,
    val accuracyMeters: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * "Find My Sportage" Last-Parked GPS Anchor Persistence Store.
 *
 * Automatically captures GPS coordinates when:
 * 1. Engine shuts down (RPM drops to 0 and battery voltage drops).
 * 2. OBD Bluetooth adapter disconnects while previously driving.
 *
 * Persists location to secure local preferences and builds direct intent deep links
 * for Google Maps / Waze pedestrian and driving return navigation.
 */
class LastParkedLocationStore(
    private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _lastParkedLocation = MutableStateFlow<ParkedLocation?>(loadFromPrefs())
    val lastParkedLocation: StateFlow<ParkedLocation?> = _lastParkedLocation.asStateFlow()

    companion object {
        private const val PREFS_NAME = "sportage_last_parked_loc"
        private const val KEY_LAT = "parked_lat"
        private const val KEY_LON = "parked_lon"
        private const val KEY_BEARING = "parked_bearing"
        private const val KEY_ACCURACY = "parked_accuracy"
        private const val KEY_TIMESTAMP = "parked_timestamp"
    }

    private fun loadFromPrefs(): ParkedLocation? {
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) return null
        val lat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAT, 0L))
        val lon = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LON, 0L))
        if (lat == 0.0 && lon == 0.0) return null

        val bearing = prefs.getFloat(KEY_BEARING, 0f)
        val accuracy = prefs.getFloat(KEY_ACCURACY, 0f)
        val time = prefs.getLong(KEY_TIMESTAMP, 0L)

        return ParkedLocation(
            latitude = lat,
            longitude = lon,
            bearing = bearing,
            accuracyMeters = accuracy,
            timestamp = time
        )
    }

    /**
     * Saves the vehicle's current position as the last parked location.
     */
    fun saveParkedLocation(
        lat: Double,
        lon: Double,
        bearing: Float = 0f,
        accuracy: Float = 0f,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (lat == 0.0 && lon == 0.0) return

        val location = ParkedLocation(
            latitude = lat,
            longitude = lon,
            bearing = bearing,
            accuracyMeters = accuracy,
            timestamp = timestamp
        )

        prefs.edit()
            .putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(lat))
            .putLong(KEY_LON, java.lang.Double.doubleToRawLongBits(lon))
            .putFloat(KEY_BEARING, bearing)
            .putFloat(KEY_ACCURACY, accuracy)
            .putLong(KEY_TIMESTAMP, timestamp)
            .apply()

        _lastParkedLocation.value = location
    }

    /**
     * Clears stored parking location anchor.
     */
    fun clearParkedLocation() {
        prefs.edit().clear().apply()
        _lastParkedLocation.value = null
    }

    /**
     * Builds an Android Intent to launch Google Maps or Waze with direct directions
     * to the parked Sportage.
     *
     * @param parked The parked coordinates.
     * @param preferWaze If true, attempts to launch Waze deep link; otherwise Google Maps.
     */
    fun buildDirectionsIntent(parked: ParkedLocation, preferWaze: Boolean = false): Intent {
        val lat = parked.latitude
        val lon = parked.longitude

        if (preferWaze) {
            val wazeUri = Uri.parse("waze://?ll=$lat,$lon&navigate=yes")
            val wazeIntent = Intent(Intent.ACTION_VIEW, wazeUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (wazeIntent.resolveActivity(context.packageManager) != null) {
                return wazeIntent
            }
        }

        // Standard Google Maps pedestrian walking / navigation deep link
        val gmapsUri = Uri.parse(
            String.format(Locale.US, "google.navigation:q=%f,%f&mode=w", lat, lon)
        )
        val gmapsIntent = Intent(Intent.ACTION_VIEW, gmapsUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (gmapsIntent.resolveActivity(context.packageManager) != null) {
            return gmapsIntent
        }

        // Generic geo fallback
        val geoUri = Uri.parse(
            String.format(Locale.US, "geo:%f,%f?q=%f,%f(Parked+Sportage)", lat, lon, lat, lon)
        )
        return Intent(Intent.ACTION_VIEW, geoUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Formats human-readable elapsed time since the vehicle was parked.
     */
    fun getFormattedParkedDuration(nowMs: Long = System.currentTimeMillis()): String {
        val parked = _lastParkedLocation.value ?: return "Not parked"
        val diffMs = nowMs - parked.timestamp
        if (diffMs < 0) return "Just parked"

        val seconds = diffMs / 1000L
        val minutes = seconds / 60L
        val hours = minutes / 60L
        val days = hours / 24L

        return when {
            days > 0 -> "Parked ${days}d ago"
            hours > 0 -> "Parked ${hours}h ${minutes % 60}m ago"
            minutes > 0 -> "Parked ${minutes}m ago"
            else -> "Parked just now"
        }
    }
}
