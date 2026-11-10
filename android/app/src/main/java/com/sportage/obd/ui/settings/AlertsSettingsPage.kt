package com.sportage.obd.ui.settings

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sportage.obd.service.AlertThresholds
import com.sportage.obd.ui.theme.AmberWarning
import com.sportage.obd.ui.theme.CriticalRed
import com.sportage.obd.viewmodel.ObdViewModel

/**
 * Alerts & Notifications Settings Subpage:
 * - Coolant temperature threshold (90°C - 130°C)
 * - Battery voltage low threshold (9.0V - 14.0V)
 * - Engine RPM redline limit (3000 - 7500 RPM)
 * - Vehicle speed limit threshold (60 - 220 km/h)
 * - Audible warning beeps toggle and test chime
 */
@Composable
fun AlertsSettingsPage(
    viewModel: ObdViewModel?,
    speedUnit: String = "km/h",
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var thresholds by remember {
        mutableStateOf(viewModel?.alertThresholds() ?: AlertThresholds())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsTopBar(
            title = "Alerts & Notifications",
            subtitle = "Configure safety thresholds, redline warning, and audio beeps",
            onNavigateBack = onNavigateBack
        )

        // 1. ENGINE & POWERTRAIN WARNING THRESHOLDS
        SettingsSectionHeader(title = "Engine & Powertrain Thresholds")

        SettingsStepperItem(
            title = "Coolant Overheat Limit",
            valueText = "${thresholds.coolantMaxC}°C",
            description = "Triggers high-priority visual alert and tone when coolant reaches this limit",
            valueColor = CriticalRed(),
            onMinus = {
                val updated = thresholds.copy(coolantMaxC = (thresholds.coolantMaxC - 5).coerceAtLeast(90))
                thresholds = updated
                viewModel?.updateAlertThresholds(updated)
            },
            onPlus = {
                val updated = thresholds.copy(coolantMaxC = (thresholds.coolantMaxC + 5).coerceAtMost(130))
                thresholds = updated
                viewModel?.updateAlertThresholds(updated)
            }
        )

        SettingsStepperItem(
            title = "Low Battery Voltage Limit",
            valueText = "%.1f V".format(thresholds.batteryMinV),
            description = "Warns of alternator failure or weak 12V battery under load",
            valueColor = AmberWarning(),
            onMinus = {
                val updated = thresholds.copy(batteryMinV = (thresholds.batteryMinV - 0.5f).coerceAtLeast(9.0f))
                thresholds = updated
                viewModel?.updateAlertThresholds(updated)
            },
            onPlus = {
                val updated = thresholds.copy(batteryMinV = (thresholds.batteryMinV + 0.5f).coerceAtMost(14.0f))
                thresholds = updated
                viewModel?.updateAlertThresholds(updated)
            }
        )

        SettingsStepperItem(
            title = "Engine RPM Redline Limit",
            valueText = "${thresholds.rpmMax} RPM",
            description = "Flashes tachometer gauge red when engine speed reaches this threshold",
            valueColor = AmberWarning(),
            onMinus = {
                val updated = thresholds.copy(rpmMax = (thresholds.rpmMax - 250).coerceAtLeast(3000))
                thresholds = updated
                viewModel?.updateAlertThresholds(updated)
            },
            onPlus = {
                val updated = thresholds.copy(rpmMax = (thresholds.rpmMax + 250).coerceAtMost(7500))
                thresholds = updated
                viewModel?.updateAlertThresholds(updated)
            }
        )

        // 2. SPEED LIMIT & ROAD SAFETY
        SettingsSectionHeader(title = "Speed Limit Warning")

        SettingsStepperItem(
            title = "Highway Speed Limit Warning",
            valueText = "${thresholds.speedMaxKmh} $speedUnit",
            description = "Alerts driver when exceeding posted highway speed limit",
            valueColor = AmberWarning(),
            onMinus = {
                val updated = thresholds.copy(speedMaxKmh = (thresholds.speedMaxKmh - 5).coerceAtLeast(60))
                thresholds = updated
                viewModel?.updateAlertThresholds(updated)
            },
            onPlus = {
                val updated = thresholds.copy(speedMaxKmh = (thresholds.speedMaxKmh + 5).coerceAtMost(220))
                thresholds = updated
                viewModel?.updateAlertThresholds(updated)
            }
        )

        // 3. AUDIBLE WARNINGS
        SettingsSectionHeader(title = "Audible Warnings & Sound")

        SettingsSwitchItem(
            title = "Audible Warning Beeps",
            description = "Play warning chime through car speakers when any critical limit is crossed",
            checked = thresholds.audibleAlerts,
            onCheckedChange = { enabled ->
                val updated = thresholds.copy(audibleAlerts = enabled)
                thresholds = updated
                viewModel?.updateAlertThresholds(updated)
            },
            activeColor = AmberWarning(),
            icon = Icons.Default.VolumeUp
        )

        SettingsActionItem(
            title = "Test Warning Chime",
            description = "Play sample notification beep to check head unit volume and speaker output",
            buttonLabel = "Play Chime",
            icon = Icons.Default.NotificationsActive,
            onClick = {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
                } catch (_: Exception) {}
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
