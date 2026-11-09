package com.sportage.obd.ui.dashboard

import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.media.NowPlaying
import com.sportage.obd.ui.gearLabel

/**
 * Everything a layout theme needs, assembled once by the launcher host.
 *
 * The themes are intentionally dumb renderers: neither fetches telemetry, opens a media
 * session nor owns a clock. That keeps both layouts on exactly the same live data and
 * makes switching layouts a pure re-composition instead of a data reload.
 */
data class DashboardData(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val measuredHz: Float? = null,
    val speed: Int? = null,
    val speedUnit: String = "km/h",
    val rpm: Int? = null,
    val coolant: Int? = null,
    val intakeTemp: Int? = null,
    val fuelLevel: Int? = null,
    val throttle: Float? = null,
    val load: Float? = null,
    val voltage: Float? = null,
    val gear: Int = 0,
    val transmissionGear: Int? = null,
    val isDemoMode: Boolean = false,
    val weatherTempC: String? = null,
    val weatherText: String? = null,
    val gpsStatus: String? = null,
    val tripDistanceKm: Double = 0.0,
    val tripDurationSeconds: Long = 0L,
    val speedHistory: List<Pair<Long, Int>> = emptyList(),
    val rpmHistory: List<Pair<Long, Int>> = emptyList()
) {
    val connected: Boolean get() = connectionState is ConnectionState.Connected

    val connectionLabel: String
        get() = when (connectionState) {
            is ConnectionState.Connected -> "Connected · ${connectionState.deviceName}"
            is ConnectionState.Connecting -> "Connecting"
            is ConnectionState.Error -> "Error · reconnect"
            ConnectionState.Disconnected -> "Disconnected"
        }

    val gearText: String get() = gearLabel(transmissionGear, gear)

    val speedText: String get() = speed?.toString() ?: "--"
}

/** Navigation and media actions shared by every layout theme's dock and widgets. */
data class DashboardActions(
    val onToggleSpeedUnit: () -> Unit = {},
    val onOpenBluetooth: () -> Unit = {},
    val onOpenNavigation: () -> Unit = {},
    val onOpenMusic: () -> Unit = {},
    val onOpenPhone: () -> Unit = {},
    val onOpenApps: () -> Unit = {}
)

/**
 * One media session snapshot plus transport controls. Created once by the host so the
 * circular disc and the rectangular media widget read the same now-playing metadata.
 */
data class DashboardMedia(
    val nowPlaying: NowPlaying? = null,
    val onPrevious: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onNext: () -> Unit = {}
)
