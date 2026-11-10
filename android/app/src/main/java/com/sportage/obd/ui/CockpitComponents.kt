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
        Modifier.testTag("dashboard_header").fillMaxWidth().background(DarkBackground()).padding(horizontal = 12.dp),
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
        var expanded by remember { mutableStateOf(false) }
        NavigationBar(containerColor = DarkBackground(), windowInsets = WindowInsets(0, 0, 0, 0)) {
            destinations.filter { it.page in listOf(0, 1, 7, 3, 4) }.forEach { item ->
                NavigationBarItem(
                    selected = activePage == item.page, onClick = { select(item.page) },
                    icon = { Icon(item.icon, item.label, Modifier.size(22.dp)) },
                    label = { Text(item.label, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElectricCyan(), selectedTextColor = ElectricCyan(),
                        indicatorColor = DarkSurfaceElevated(),
                        unselectedIconColor = SecondaryText(), unselectedTextColor = SecondaryText()
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
                            selectedIconColor = ElectricCyan(), selectedTextColor = ElectricCyan(),
                            indicatorColor = DarkSurfaceElevated(),
                            unselectedIconColor = SecondaryText(), unselectedTextColor = SecondaryText()
                        )
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    destinations.filter { it.page in listOf(6, 2, 9, -1, 5, 8) }.forEach { item ->
                        DropdownMenuItem(text = { Text(item.label) },
                            leadingIcon = { Icon(item.icon, null) },
                            onClick = { expanded = false; select(item.page) })
                    }
                }
            }
        }
    }
}

/** Human label for the current transmission state. */
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
    val tokens = LocalCockpitTokens.current
    val density = tokens.spec.spacing.density
    val gaugeStyle = tokens.spec.gauge.style

    Surface(color = DarkSurface(), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 550.dp
            val panePadding = when (density) {
                ThemeDensity.COMPACT -> 8.dp
                ThemeDensity.SPACIOUS -> 20.dp
                else -> if (compact) 12.dp else 16.dp
            }
            val paneSpacing = when (density) {
                ThemeDensity.COMPACT -> 4.dp
                ThemeDensity.SPACIOUS -> 12.dp
                else -> 8.dp
            }
            val rowSpacing = when (density) {
                ThemeDensity.COMPACT -> 8.dp
                ThemeDensity.SPACIOUS -> 20.dp
                else -> 16.dp
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                    .padding(panePadding),
                verticalArrangement = Arrangement.spacedBy(paneSpacing)
            ) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Vehicle", style = MaterialTheme.typography.titleMedium, color = PrimaryText())
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
                    Text(gearLabel(transmissionGear, gear),
                        color = SecondaryText(), style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CockpitDialRevamped(
                        speed = speed,
                        speedUnit = speedUnit,
                        rpm = rpm,
                        coolant = coolant,
                        throttle = throttle,
                        gear = gear,
                        transmissionGear = transmissionGear,
                        connectionState = connectionState,
                        onToggleSpeedUnit = onToggleSpeedUnit,
                        modifier = Modifier.weight(1.3f).fillMaxWidth()
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                    CockpitReading("Coolant", coolant?.let { "$it°C" } ?: "--", Modifier.weight(1f),
                        if ((coolant ?: 0) > 105) CriticalRed() else PrimaryText())
                    CockpitReading("Battery", voltage?.let { "%.1f V".format(it) } ?: "--", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                    CockpitReading("Engine load", load?.let { "%.0f%%".format(it) } ?: "--", Modifier.weight(1f))
                    CockpitReading("Throttle", throttle?.let { "%.0f%%".format(it) } ?: "--", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                    CockpitReading("Intake air", intake?.let { "$it°C" } ?: "--", Modifier.weight(1f))
                    CockpitReading("Fuel level", fuel?.let { "$it%" } ?: "--", Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Responsive instrument cluster that adapts dynamically to the chosen [GaugeStyle]
 * (Radial, Arc, Bar, Digital, Minimal) and [ThemeDensity].
 */
@Composable
private fun CockpitDial(
    speed: Int?,
    speedUnit: String,
    rpm: Int?,
    onToggleSpeedUnit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalCockpitTokens.current
    val gaugeStyle = tokens.spec.gauge.style
    val density = tokens.spec.spacing.density

    val speedColor = ElectricCyan()
    val rpmColor = WarningAmber()
    val trackColor = DarkSurfaceElevated()
    val needleColor = PrimaryText()
    val speedValue = speed?.toFloat() ?: 0f
    val rpmValue = rpm?.toFloat() ?: 0f
    val speedFrac = (speedValue / 220f).coerceIn(0f, 1f)
    val rpmFrac = (rpmValue / 7000f).coerceIn(0f, 1f)

    val dialHeight = when (density) {
        ThemeDensity.COMPACT -> 170.dp
        ThemeDensity.SPACIOUS -> 230.dp
        else -> 205.dp
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (gaugeStyle) {
            GaugeStyle.BAR -> {
                // Horizontal Sports Bar Gauges
                Column(
                    Modifier.fillMaxWidth().height(dialHeight).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("SPEED", color = SecondaryText(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${speed ?: "--"} $speedUnit", color = speedColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.fillMaxWidth().height(16.dp).background(trackColor, RoundedCornerShape(8.dp))) {
                            Box(
                                Modifier.fillMaxHeight().fillMaxWidth(speedFrac)
                                    .background(speedColor, RoundedCornerShape(8.dp))
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TACHOMETER", color = SecondaryText(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${rpm ?: "--"} rpm", color = rpmColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.fillMaxWidth().height(14.dp).background(trackColor, RoundedCornerShape(7.dp))) {
                            val isRedline = (rpm ?: 0) >= 6000
                            Box(
                                Modifier.fillMaxHeight().fillMaxWidth(rpmFrac)
                                    .background(if (isRedline) CriticalRed() else rpmColor, RoundedCornerShape(7.dp))
                            )
                        }
                    }
                }
            }

            GaugeStyle.DIGITAL -> {
                // High-Glanceability Digital Instrument Display
                Column(
                    Modifier.fillMaxWidth().height(dialHeight).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = speed?.toString() ?: "--",
                        color = PrimaryText(),
                        fontSize = when (density) { ThemeDensity.COMPACT -> 44.sp; ThemeDensity.SPACIOUS -> 60.sp; else -> 52.sp },
                        fontWeight = FontWeight.Light,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = speedUnit.uppercase(Locale.US),
                        color = speedColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    // 12-segment digital tachometer rev band
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val activeSegments = (rpmFrac * 12).toInt().coerceIn(0, 12)
                        for (i in 0 until 12) {
                            val segColor = when {
                                i < activeSegments && i >= 10 -> CriticalRed()
                                i < activeSegments && i >= 7 -> WarningAmber()
                                i < activeSegments -> speedColor
                                else -> trackColor
                            }
                            Box(
                                Modifier.weight(1f).height(10.dp)
                                    .background(segColor, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }

            GaugeStyle.MINIMAL -> {
                // Minimalist Digital Cluster layout
                Column(
                    Modifier.fillMaxWidth().height(dialHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = speed?.toString() ?: "--",
                        color = speedColor,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = "$speedUnit · ${rpm ?: "--"} rpm",
                        color = SecondaryText(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.width(100.dp).height(3.dp)
                            .background(DarkSurfaceElevated(), RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            Modifier.fillMaxHeight().width((100 * speedFrac).dp)
                                .background(speedColor, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            GaugeStyle.ARC -> {
                // Pure Sweep Arc without mechanical needles
                Canvas(Modifier.fillMaxWidth().height(dialHeight)) {
                    val stroke = 12.dp.toPx()
                    val rpmStroke = 6.dp.toPx()
                    val inset = stroke / 2 + 3.dp.toPx()
                    val diameter = minOf(size.width, size.height) - inset * 2
                    val arcSize = Size(diameter, diameter)
                    val topLeft = Offset((size.width - diameter) / 2, inset)
                    val sweep = 240f
                    val start = 150f

                    // Speed arc
                    drawArc(
                        color = trackColor,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = speedColor,
                        startAngle = start,
                        sweepAngle = sweep * speedFrac,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )

                    // RPM arc
                    val innerInset = inset + stroke + 9.dp.toPx()
                    val innerDiameter = diameter - (innerInset - inset) * 2
                    val innerSize = Size(innerDiameter, innerDiameter)
                    val innerTopLeft = Offset((size.width - innerDiameter) / 2, innerInset)
                    drawArc(
                        color = trackColor,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = innerTopLeft,
                        size = innerSize,
                        style = Stroke(width = rpmStroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = rpmColor,
                        startAngle = start,
                        sweepAngle = sweep * rpmFrac,
                        useCenter = false,
                        topLeft = innerTopLeft,
                        size = innerSize,
                        style = Stroke(width = rpmStroke, cap = StrokeCap.Round)
                    )
                }
            }

            else -> {
                // Default / RADIAL: Traditional dial with ticks, arcs, and needle pointers
                Canvas(Modifier.fillMaxWidth().height(dialHeight)) {
                    val stroke = 10.dp.toPx()
                    val rpmStroke = 5.dp.toPx()
                    val inset = stroke / 2 + 2.dp.toPx()
                    val diameter = minOf(size.width, size.height) - inset * 2
                    val arcSize = Size(diameter, diameter)
                    val topLeft = Offset((size.width - diameter) / 2, inset)
                    val sweep = 240f
                    val start = 150f
                    val centerX = size.width / 2
                    val centerY = arcSize.height / 2 + inset
                    fun angle(frac: Float) = Math.toRadians((start + sweep * frac).toDouble())

                    // Tick marks around perimeter
                    for (i in 0..10) {
                        val tickFrac = i / 10f
                        val rad = angle(tickFrac)
                        val outerR = diameter / 2
                        val innerR = outerR - (if (i % 2 == 0) 8.dp.toPx() else 4.dp.toPx())
                        val p1 = Offset(centerX + (outerR * Math.cos(rad)).toFloat(), centerY + (outerR * Math.sin(rad)).toFloat())
                        val p2 = Offset(centerX + (innerR * Math.cos(rad)).toFloat(), centerY + (innerR * Math.sin(rad)).toFloat())
                        drawLine(color = trackColor, start = p1, end = p2, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }

                    // Speed arc (outer): 0..220
                    drawArc(color = trackColor, startAngle = start, sweepAngle = sweep, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round))
                    drawArc(color = speedColor, startAngle = start, sweepAngle = sweep * speedFrac, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round))

                    // RPM arc (inner): 0..7000
                    val innerInset = inset + stroke + 8.dp.toPx()
                    val innerDiameter = diameter - (innerInset - inset) * 2
                    val innerSize = Size(innerDiameter, innerDiameter)
                    val innerTopLeft = Offset((size.width - innerDiameter) / 2, innerInset)
                    drawArc(color = trackColor, startAngle = start, sweepAngle = sweep, useCenter = false,
                        topLeft = innerTopLeft, size = innerSize, style = Stroke(width = rpmStroke, cap = StrokeCap.Round))
                    drawArc(color = rpmColor, startAngle = start, sweepAngle = sweep * rpmFrac, useCenter = false,
                        topLeft = innerTopLeft, size = innerSize, style = Stroke(width = rpmStroke, cap = StrokeCap.Round))

                    // Needles from dial center
                    val speedAngle = angle(speedFrac)
                    val speedLen = arcSize.width / 2 - stroke
                    drawLine(needleColor, Offset(centerX, centerY),
                        Offset(centerX + (speedLen * Math.cos(speedAngle)).toFloat(),
                            centerY + (speedLen * Math.sin(speedAngle)).toFloat()),
                        strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    val rpmAngle = angle(rpmFrac)
                    val rpmLen = innerSize.width / 2
                    drawLine(rpmColor, Offset(centerX, centerY),
                        Offset(centerX + (rpmLen * Math.cos(rpmAngle)).toFloat(),
                            centerY + (rpmLen * Math.sin(rpmAngle)).toFloat()),
                        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(needleColor, radius = 4.dp.toPx(), center = Offset(centerX, centerY))
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${speed ?: "--"} $speedUnit", color = PrimaryText(), fontSize = 20.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1)
            Text("${rpm ?: "--"} rpm", color = rpmColor, fontSize = 16.sp, maxLines = 1)
        }
        TextButton(onClick = onToggleSpeedUnit, modifier = Modifier.heightIn(min = 36.dp)) {
            Text("Switch to ${if (speedUnit == "km/h") "mph" else "km/h"}", color = SecondaryText(),
                style = MaterialTheme.typography.labelSmall)
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
            Text(reading, color = PrimaryText(), fontSize = fittedSize.sp,
                lineHeight = (fittedSize * 1.1f).sp,
                fontWeight = FontWeight.Light, style = TextStyle(fontFeatureSettings = "tnum", letterSpacing = (-2).sp),
                maxLines = 1,
                onTextLayout = { result ->
                    if (result.didOverflowWidth && fittedSize > 20f) fittedSize = (fittedSize * 0.9f).coerceAtLeast(20f)
                })
        }
        TextButton(onClick = toggle, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(unit, color = ElectricCyan(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CockpitRpm(rpm: Int?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Engine speed", style = MaterialTheme.typography.labelMedium, color = SecondaryText())
        Text("${rpm?.toString() ?: "--"} rpm", style = MaterialTheme.typography.labelLarge, color = PrimaryText())
        LinearProgressIndicator(
            progress = { ((rpm ?: 0) / 7000f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = if ((rpm ?: 0) > 6500) CriticalRed() else ElectricCyan(), trackColor = DarkSurfaceElevated())
    }
}

@Composable
internal fun CockpitReading(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = PrimaryText()) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = SecondaryText(),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1)
    }
}
