package com.sportage.obd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sportage.obd.ui.theme.DarkBackground
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data payload capturing post-drive trip telemetry.
 */
data class PostDriveTripSummary(
    val distanceKm: Double,
    val durationSeconds: Long,
    val avgSpeedKmh: Int,
    val maxSpeedKmh: Int,
    val fuelUsedLiters: Double,
    val avgL100: Double?,
    val instantaneousL100: Double?,
    val kmPerLiter: Double?,
    val totalFuelCostQar: Double,
    val peakRpm: Int,
    val peakCoolantC: Int,
    val smoothnessScore: Int,
    val dateMs: Long = System.currentTimeMillis()
)

/**
 * Automotive Post-Drive Trip Summary Dialog.
 *
 * Triggered automatically when engine-off is detected (RPM == 0 and battery voltage < 12.8V
 * for > 5 seconds while in DRIVING state) or manually by the driver.
 *
 * Provides complete glanceable telemetry:
 * - Distance & Duration
 * - Average and Instantaneous Fuel Economy (L/100 km & km/L)
 * - Total trip fuel cost in QAR (via FuelSettings.fuelPriceQarPerLiter)
 * - Peak RPM, Peak Coolant Temperature
 * - Driving Smoothness Score (0 - 100)
 *
 * Strict Automotive UX Constraints:
 * - Minimum 64dp touch targets for cockpit usability
 * - High-contrast automotive typography with tabular figures (`fontFeatureSettings = "tnum"`)
 */
@Composable
fun TripSummaryDialog(
    summary: PostDriveTripSummary,
    onDismiss: () -> Unit,
    onSaveToLog: () -> Unit,
    onResetTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = modifier
                .padding(16.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0A0F17))
                .border(
                    border = BorderStroke(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                ElectricCyan().copy(alpha = 0.8f),
                                Color(0xFF1E293B),
                                ElectricCyan().copy(alpha = 0.3f)
                            )
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Engine Off Badge & Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ElectricCyan().copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, ElectricCyan().copy(alpha = 0.4f)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = "Engine Off",
                                    tint = ElectricCyan(),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "TRIP COMPLETED",
                                color = PrimaryText(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Engine-off detected · Nu 2.0L MPI",
                                color = SecondaryText(),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Timestamp
                    Surface(
                        color = DarkSurfaceElevated(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(summary.dateMs)),
                            color = SecondaryText(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0x33334155))
                Spacer(Modifier.height(16.dp))

                // Primary Metrics Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Row 1: Distance & Duration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryMetricCard(
                            title = "TRIP DISTANCE",
                            value = "%.1f".format(summary.distanceKm),
                            unit = "km",
                            icon = Icons.Default.Route,
                            modifier = Modifier.weight(1f)
                        )
                        val durationMinutes = summary.durationSeconds / 60
                        val durationSecs = summary.durationSeconds % 60
                        val formattedDuration = if (durationMinutes >= 60) {
                            "${durationMinutes / 60}h ${durationMinutes % 60}m"
                        } else {
                            "${durationMinutes}m ${durationSecs}s"
                        }
                        SummaryMetricCard(
                            title = "DRIVE TIME",
                            value = formattedDuration,
                            unit = "",
                            icon = Icons.Default.Speed,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 2: Fuel Consumption & Fuel Cost
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val avgL100Text = summary.avgL100?.let { "%.1f".format(it) } ?: "--"
                        val kmPerLText = summary.kmPerLiter?.let { " · %.1f km/L".format(it) } ?: ""
                        SummaryMetricCard(
                            title = "FUEL ECONOMY",
                            value = avgL100Text,
                            unit = "L/100km$kmPerLText",
                            icon = Icons.Default.LocalGasStation,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCard(
                            title = "TOTAL FUEL COST",
                            value = "%.2f".format(summary.totalFuelCostQar),
                            unit = "QAR",
                            icon = Icons.Default.LocalGasStation,
                            modifier = Modifier.weight(1f),
                            accentColor = WarningAmber()
                        )
                    }

                    // Row 3: Powertrain Peaks (Peak RPM & Peak Coolant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryMetricCard(
                            title = "PEAK ENGINE RPM",
                            value = "${summary.peakRpm}",
                            unit = "rpm",
                            icon = Icons.Default.Speed,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCard(
                            title = "PEAK COOLANT",
                            value = "${summary.peakCoolantC}°C",
                            unit = if (summary.peakCoolantC > 105) "High Temp" else "Normal",
                            icon = Icons.Default.Thermostat,
                            modifier = Modifier.weight(1f),
                            accentColor = if (summary.peakCoolantC > 105) Color(0xFFFF1744) else ElectricCyan()
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Driving Smoothness & Efficiency Rating
                Surface(
                    color = DarkSurface(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0x33475569)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TRIP SMOOTHNESS SCORE",
                                color = SecondaryText(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${summary.smoothnessScore} / 100",
                                color = if (summary.smoothnessScore >= 80) Color(0xFF00E676) else ElectricCyan(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E293B))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(summary.smoothnessScore / 100f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                ElectricCyan(),
                                                if (summary.smoothnessScore >= 80) Color(0xFF00E676) else WarningAmber()
                                            )
                                        )
                                    )
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        val ratingDescription = when {
                            summary.smoothnessScore >= 85 -> "Optimal Eco Flow: gentle throttle gradient and steady highway cadence."
                            summary.smoothnessScore >= 70 -> "Balanced Drive: moderate acceleration with steady cruising segments."
                            else -> "Dynamic / High-Load Drive: spirited throttle transitions and rev bursts."
                        }
                        Text(
                            text = ratingDescription,
                            color = SecondaryText(),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Automotive Action Buttons (64dp touch targets)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Secondary Action: Reset Trip (64dp target)
                    OutlinedButton(
                        onClick = onResetTrip,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = SecondaryText()
                        ),
                        border = BorderStroke(1.dp, Color(0x5564748B))
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset Trip", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Primary Action: Save to Trip Log (64dp target)
                    Button(
                        onClick = onSaveToLog,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan(),
                            contentColor = Color(0xFF070B11)
                        )
                    ) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save to Log", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Dismiss Button (64dp target)
                FilledTonalButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DarkSurfaceElevated(),
                        contentColor = PrimaryText()
                    )
                ) {
                    Text("Dismiss", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = PrimaryText()
) {
    Surface(
        color = DarkSurface(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0x22475569)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SecondaryText(),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    color = SecondaryText(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    color = accentColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
                if (unit.isNotBlank()) {
                    Text(
                        text = unit,
                        color = SecondaryText(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
