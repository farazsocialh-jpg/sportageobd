package com.sportage.obd.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
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
        Modifier.fillMaxWidth().background(DarkBackground()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Sportage", style = MaterialTheme.typography.titleMedium, color = PrimaryText())
            Text(SimpleDateFormat("EEE, d MMM  ·  HH:mm", Locale.ENGLISH).format(time),
                style = MaterialTheme.typography.labelSmall, color = SecondaryText())
        }
        FilledTonalButton(
            onClick = onOpenBluetoothClick, modifier = Modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = DarkSurfaceElevated(),
                contentColor = if (isDemoMode) AmberWarning() else if (connected) TealAccent() else ElectricCyan()
            ), contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ElectricCyan())
            else if (isDemoMode) Icon(Icons.Default.PhoneAndroid, null, Modifier.size(18.dp))
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
    CockpitDestination(7, "Boards", Icons.Default.Dashboard),
    CockpitDestination(6, "Sensors", Icons.Default.Sensors),
    CockpitDestination(2, "Diagnostics", Icons.Default.Warning),
    CockpitDestination(9, "Advanced", Icons.Default.Insights),
    CockpitDestination(3, "Trip", Icons.Default.Route),
    CockpitDestination(8, "Fuel", Icons.Default.LocalGasStation),
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
            containerColor = DarkBackground(), windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            Spacer(Modifier.height(12.dp))
            destinations.forEach { item ->
                NavigationRailItem(
                    modifier = Modifier.heightIn(min = 64.dp), selected = activePage == item.page,
                    onClick = { select(item.page) },
                    icon = { Icon(item.icon, item.label, Modifier.size(22.dp)) },
                    label = { Text(item.label, fontSize = 11.sp, maxLines = 1) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = ElectricCyan(), selectedTextColor = ElectricCyan(),
                        indicatorColor = DarkSurfaceElevated(),
                        unselectedIconColor = SecondaryText(), unselectedTextColor = SecondaryText()
                    )
                )
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            destinations.take(5).forEach { item ->
                IconButton(onClick = { select(item.page) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        item.icon, item.label,
                        tint = if (activePage == item.page) ElectricCyan() else SecondaryText(),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

fun gearLabel(transmissionGear: Int?, estimatedGear: Int): String = when (transmissionGear) {
    -2 -> "P"
    -1 -> "R"
    0 -> "N"
    in 1..10 -> "D$transmissionGear"
    else -> if (estimatedGear in 1..10) "D$estimatedGear · est." else "P / N"
}

@Composable
fun CockpitTelemetryPane(
    speed: Int?, speedUnit: String, onToggleSpeedUnit: () -> Unit,
    rpm: Int?, coolant: Int?, voltage: Float?, load: Float?, throttle: Float?,
    intake: Int?, fuel: Int?,
    gear: Int, transmissionGear: Int?,
    weatherTempC: String?, weatherText: String?,
    connectionState: ConnectionState, measuredHz: Float?, isDemoMode: Boolean
) {
    val connected = connectionState is ConnectionState.Connected
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            clock = fmt.format(Date())
            delay(10_000)
        }
    }
    Surface(color = DarkSurface(), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 550.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                    .padding(if (compact) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Timer, "Live readings", tint = if (isDemoMode) AmberWarning() else ElectricCyan(), modifier = Modifier.size(18.dp))
                            Text("Vehicle", style = MaterialTheme.typography.titleMedium, color = PrimaryText())
                        }
                        Text(
                            when {
                                isDemoMode -> "Demo data"
                                connected && measuredHz != null -> "Live data · ${"%.1f".format(measuredHz)} Hz"
                                connected -> "Connected"
                                else -> "Connect Bluetooth for live data"
                            }, color = if (isDemoMode) AmberWarning() else SecondaryText(),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Text(gearLabel(transmissionGear, gear), color = SecondaryText(), style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CockpitDial(
                        speed = speed, speedUnit = speedUnit, rpm = rpm, onToggleSpeedUnit = onToggleSpeedUnit,
                        modifier = Modifier.weight(1.25f).fillMaxWidth()
                    )
                    Column(
                        Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Clock", color = SecondaryText(), style = MaterialTheme.typography.labelMedium)
                            Text(clock, color = PrimaryText(), fontSize = 44.sp, fontWeight = FontWeight.Light,
                                style = TextStyle(fontFeatureSettings = "tnum"))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Weather", color = SecondaryText(), style = MaterialTheme.typography.labelMedium)
                            Text(weatherTempC ?: "--", color = PrimaryText(), fontSize = 30.sp, fontWeight = FontWeight.Light)
                            Text(weatherText ?: "Open-Meteo", color = SecondaryText(),
                                style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
                HorizontalDivider(color = DarkSurfaceElevated(), modifier = Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CockpitReading("Coolant", coolant?.let { "$it°C" } ?: "--", Modifier.weight(1f),
                        if ((coolant ?: 0) > 105) CriticalRed() else PrimaryText())
                    CockpitReading("Battery", voltage?.let { "%.1f V".format(it) } ?: "--", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CockpitReading("Engine load", load?.let { "%.0f%%".format(it) } ?: "--", Modifier.weight(1f))
                    CockpitReading("Throttle", throttle?.let { "%.0f%%".format(it) } ?: "--", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CockpitReading("Intake air", intake?.let { "$it°C" } ?: "--", Modifier.weight(1f))
                    CockpitReading("Fuel level", fuel?.let { "$it%" } ?: "--", Modifier.weight(1f))
                }
            }
        }
    }
}
