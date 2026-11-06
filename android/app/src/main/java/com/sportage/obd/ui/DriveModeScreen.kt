package com.sportage.obd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.service.ActiveAlert
import com.sportage.obd.service.AlertSeverity
import com.sportage.obd.ui.theme.AmberWarning
import com.sportage.obd.ui.theme.CriticalRed
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.TealAccent
import kotlin.math.min

/**
 * Drive Mode: the cockpit reduced to what a driver may safely read at a glance.
 *
 * Entered automatically above the configured speed (or by tapping the dock button) and left by
 * tapping the close affordance — or automatically after the car has been parked for a while. All
 * page navigation, settings and diagnostics are hidden here on purpose: they are passenger tasks.
 */
@Composable
fun DriveModeScreen(
    speed: Int?,
    speedUnit: String,
    rpm: Int?,
    gear: Int,
    coolant: Int?,
    fuel: Int?,
    voltage: Float?,
    load: Float?,
    alerts: List<ActiveAlert>,
    tripDistanceKm: Double,
    tripSeconds: Long,
    onExit: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp
    val critical = alerts.firstOrNull { it.severity == AlertSeverity.CRITICAL }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (critical != null) Color(0xFF2A0808) else Color(0xFF04060A))
            // Swallows taps so the cockpit underneath cannot be operated by accident. Children of
            // this box (the exit button) are hit-tested first, so they still work.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DRIVE MODE",
                    color = ElectricCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatDistance(tripDistanceKm)} km · ${formatDuration(tripSeconds)}",
                        color = SecondaryText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(10.dp))
                    IconButton(onClick = onExit, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Leave Drive Mode", tint = SecondaryText)
                    }
                }
            }

            if (critical != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CriticalRed.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CriticalRed)
                ) {
                    Text(
                        critical.message,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Speed is the one number that must be readable at a glance.
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = speed?.toString() ?: "—",
                        color = Color.White,
                        fontSize = if (isLandscape) 132.sp else 96.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        speedUnit.uppercase(),
                        color = SecondaryText,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BigReadout("GEAR", if (gear <= 0) "N" else gear.toString(), ElectricCyan)
                        BigReadout("RPM", rpm?.toString() ?: "—", Color.White)
                    }
                    RpmBar(rpm)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SmallReadout("COOLANT", coolant?.let { "$it°C" } ?: "—", coolantColor(coolant))
                        SmallReadout("FUEL", fuel?.let { "$it%" } ?: "—", Color.White)
                        SmallReadout("BATTERY", voltage?.let { String.format("%.1fV", it) } ?: "—", batteryColor(voltage))
                        SmallReadout("LOAD", load?.let { "${it.toInt()}%" } ?: "—", TealAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigReadout(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = color, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SmallReadout(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = SecondaryText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

/** Horizontal RPM strip: redline band starts at 6500, matching the app's polling cap. */
@Composable
private fun RpmBar(rpm: Int?) {
    val fraction = ((rpm ?: 0) / 7000f).coerceIn(0f, 1f)
    Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = Color(0xFF14202C), cornerRadius = radius)
        drawRoundRect(
            color = if (fraction > 0.85f) CriticalRed else ElectricCyan,
            size = Size(size.width * fraction, size.height),
            cornerRadius = radius
        )
        // Redline marker at 6.5k of 7k.
        drawLine(
            color = CriticalRed.copy(alpha = 0.6f),
            start = Offset(size.width * 0.93f, 0f),
            end = Offset(size.width * 0.93f, size.height),
            strokeWidth = 2f
        )
    }
}

private fun coolantColor(coolant: Int?): Color = when {
    coolant == null -> SecondaryText
    coolant >= 110 -> CriticalRed
    coolant >= 100 -> AmberWarning
    else -> TealAccent
}

private fun batteryColor(voltage: Float?): Color = when {
    voltage == null -> SecondaryText
    voltage < 11.8f -> CriticalRed
    voltage < 12.4f -> AmberWarning
    else -> Color.White
}

private fun formatDistance(km: Double): String = String.format("%.1f", min(km, 9999.9))

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    return if (hours > 0) "%dh %02dm".format(hours, minutes % 60) else "%dm".format(minutes)
}
