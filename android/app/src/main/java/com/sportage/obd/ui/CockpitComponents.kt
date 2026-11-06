package com.sportage.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.testTag

@Composable
fun CockpitStatusStrip(connectionState: ConnectionState, isDemoMode: Boolean, onOpenBluetoothClick: () -> Unit) {
    var time by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { time = Date(); delay(30_000) } }
    val connected = connectionState is ConnectionState.Connected
    val busy = connectionState is ConnectionState.Connecting
    val status = when {
        isDemoMode -> "Demo"
        busy -> "Connecting"
        connected -> "Connected"
        connectionState is ConnectionState.Error -> "Reconnect"
        else -> "Connect"
    }
    Row(
        Modifier.testTag("dashboard_header").fillMaxWidth().background(DarkBackground).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Sportage", style = MaterialTheme.typography.titleMedium, color = PrimaryText)
            Text(SimpleDateFormat("EEE, d MMM  ·  HH:mm", Locale.ENGLISH).format(time),
                style = MaterialTheme.typography.labelSmall, color = SecondaryText)
        }
        FilledTonalButton(
            onClick = onOpenBluetoothClick, modifier = Modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = DarkSurfaceElevated,
                contentColor = if (isDemoMode) AmberWarning else if (connected) TealAccent else ElectricCyan
            ), contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ElectricCyan)
            else Icon(Icons.Default.Bluetooth, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(status, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private data class CockpitDestination(val page: Int, val label: String, val icon: ImageVector)
private val destinations = listOf(
    CockpitDestination(0, "Home", Icons.Default.Home),
    CockpitDestination(1, "Gauges", Icons.Default.Speed),
    CockpitDestination(6, "Sensors", Icons.Default.Sensors),
    CockpitDestination(2, "Diagnostics", Icons.Default.Warning),
    CockpitDestination(3, "Trip", Icons.Default.Route),
    CockpitDestination(4, "Media", Icons.Default.Radio),
    CockpitDestination(-1, "Apps", Icons.Default.Apps),
    CockpitDestination(5, "Setup", Icons.Default.Settings)
)

@Composable
fun CockpitNavigation(activePage: Int, onNavigate: (Int) -> Unit, onOpenApps: () -> Unit, rail: Boolean) {
    fun select(page: Int) { if (page == -1) onOpenApps() else onNavigate(page) }
    if (rail) {
        NavigationRail(
            modifier = Modifier.width(88.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            containerColor = DarkBackground, windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            Spacer(Modifier.height(12.dp))
            destinations.forEach { item ->
                NavigationRailItem(
                    modifier = Modifier.heightIn(min = 64.dp), selected = activePage == item.page,
                    onClick = { select(item.page) },
                    icon = { Icon(item.icon, item.label, Modifier.size(22.dp)) },
                    label = { Text(item.label, fontSize = 11.sp, maxLines = 1) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = ElectricCyan, selectedTextColor = ElectricCyan,
                        indicatorColor = DarkSurfaceElevated,
                        unselectedIconColor = SecondaryText, unselectedTextColor = SecondaryText
                    )
                )
            }
        }
    } else {
        var expanded by remember { mutableStateOf(false) }
        NavigationBar(containerColor = DarkBackground, windowInsets = WindowInsets(0, 0, 0, 0)) {
            destinations.filter { it.page in listOf(0, 1, 3, 4) }.forEach { item ->
                NavigationBarItem(
                    selected = activePage == item.page, onClick = { select(item.page) },
                    icon = { Icon(item.icon, item.label, Modifier.size(22.dp)) },
                    label = { Text(item.label, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElectricCyan, selectedTextColor = ElectricCyan,
                        indicatorColor = DarkSurfaceElevated,
                        unselectedIconColor = SecondaryText, unselectedTextColor = SecondaryText
                    )
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(Modifier.height(80.dp)) {
                    NavigationBarItem(
                        selected = activePage in listOf(2, 5, 6), onClick = { expanded = true },
                        icon = { Icon(Icons.Default.MoreHoriz, "More", Modifier.size(22.dp)) },
                        label = { Text("More", maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricCyan, selectedTextColor = ElectricCyan,
                            indicatorColor = DarkSurfaceElevated,
                            unselectedIconColor = SecondaryText, unselectedTextColor = SecondaryText
                        )
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    destinations.filter { it.page in listOf(6, 2, -1, 5) }.forEach { item ->
                        DropdownMenuItem(text = { Text(item.label) },
                            leadingIcon = { Icon(item.icon, null) },
                            onClick = { expanded = false; select(item.page) })
                    }
                }
            }
        }
    }
}

@Composable
fun CockpitTelemetryPane(
    speed: Int?, speedUnit: String, onToggleSpeedUnit: () -> Unit,
    rpm: Int?, coolant: Int?, voltage: Float?, load: Float?, throttle: Float?,
    gear: Int, connectionState: ConnectionState, measuredHz: Float?, isDemoMode: Boolean
) {
    val connected = connectionState is ConnectionState.Connected
    Surface(color = DarkSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 550.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                    .padding(if (compact) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Vehicle", style = MaterialTheme.typography.titleMedium, color = PrimaryText)
                        Text(
                            when {
                                isDemoMode -> "Demo data"
                                connected && measuredHz != null -> "Live data · ${"%.1f".format(measuredHz)} Hz"
                                connected -> "Connected"
                                else -> "Connect Bluetooth for live data"
                            }, color = if (isDemoMode) AmberWarning else SecondaryText,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Text(if (gear == 0 || (speed ?: 0) < 5) "P / N" else "D$gear · est.",
                        color = SecondaryText, style = MaterialTheme.typography.labelLarge)
                }
                if (compact) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { CockpitSpeed(speed, speedUnit, onToggleSpeedUnit, 64) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CockpitRpm(rpm)
                            CockpitReading("Coolant", coolant?.let { "$it°C" } ?: "--", Modifier.fillMaxWidth(),
                                if ((coolant ?: 0) > 105) CriticalRed else PrimaryText)
                            CockpitReading("Battery", voltage?.let { "%.1f V".format(it) } ?: "--", Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    CockpitSpeed(speed, speedUnit, onToggleSpeedUnit, 88)
                    CockpitRpm(rpm)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CockpitReading("Coolant", coolant?.let { "$it°C" } ?: "--", Modifier.weight(1f),
                            if ((coolant ?: 0) > 105) CriticalRed else PrimaryText)
                        CockpitReading("Battery", voltage?.let { "%.1f V".format(it) } ?: "--", Modifier.weight(1f))
                    }
                }
                HorizontalDivider(color = DarkSurfaceElevated, modifier = Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CockpitReading("Engine load", load?.let { "%.0f%%".format(it) } ?: "--", Modifier.weight(1f))
                    CockpitReading("Throttle", throttle?.let { "%.0f%%".format(it) } ?: "--", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CockpitSpeed(speed: Int?, unit: String, toggle: () -> Unit, size: Int) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val reading = speed?.toString() ?: "--"
            val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            var fittedSize by remember(reading.length, maxWidth, fontScale, size) {
                mutableFloatStateOf(size.toFloat())
            }
            Text(reading, color = PrimaryText, fontSize = fittedSize.sp,
                lineHeight = (fittedSize * 1.1f).sp,
                fontWeight = FontWeight.Light, style = TextStyle(fontFeatureSettings = "tnum", letterSpacing = (-2).sp),
                maxLines = 1,
                onTextLayout = { result ->
                    if (result.didOverflowWidth && fittedSize > 20f) fittedSize = (fittedSize * 0.9f).coerceAtLeast(20f)
                })
        }
        TextButton(onClick = toggle, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(unit, color = ElectricCyan, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CockpitRpm(rpm: Int?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Engine speed", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
        Text("${rpm?.toString() ?: "--"} rpm", style = MaterialTheme.typography.labelLarge, color = PrimaryText)
        LinearProgressIndicator(
            progress = { ((rpm ?: 0) / 7000f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = if ((rpm ?: 0) > 6500) CriticalRed else ElectricCyan, trackColor = DarkSurfaceElevated)
    }
}

@Composable
private fun CockpitReading(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = PrimaryText) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = SecondaryText,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1)
    }
}
