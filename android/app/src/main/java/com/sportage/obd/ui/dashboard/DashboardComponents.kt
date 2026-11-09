package com.sportage.obd.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.TealAccent
import com.sportage.obd.ui.theme.WarningAmber
import kotlin.math.min

/** Consistent rounded card used by the widget dashboard and the car/status panels. */
@Composable
fun DashboardPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = DarkSurface(),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceElevated())
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            content = content
        )
    }
}

/** Small labelled reading, e.g. "COOLANT 89°C". */
@Composable
fun DashboardStat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = PrimaryText()) {
    Column(modifier) {
        Text(
            label.uppercase(),
            color = SecondaryText(),
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            value,
            color = valueColor,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Top-down car silhouette shared by both themes. Purely decorative: it reacts to the
 * already-live gear/connection values but never holds state of its own.
 */
@Composable
fun CarGraphic(data: DashboardData, modifier: Modifier = Modifier) {
    val bodyColor = DarkSurfaceElevated()
    val roofColor = com.sportage.obd.ui.theme.DarkBackground()
    val accent = ElectricCyan()
    val wheelColor = Color(0xFF11161A)
    val detailColor = SecondaryText()
    val warning = WarningAmber()

    Box(modifier.testTag("dashboard_car_graphic")) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 24.dp)) {
            val carWidth = min(size.width * 0.72f, size.height * 0.5f)
            val carHeight = size.height * 0.94f
            val left = (size.width - carWidth) / 2f
            val top = (size.height - carHeight) / 2f
            val right = left + carWidth
            val bottom = top + carHeight
            val wheelWidth = carWidth * 0.10f
            val wheelHeight = carHeight * 0.16f

            // Tyres first, so the body overlaps them cleanly.
            listOf(
                Offset(left - wheelWidth * 0.5f, top + carHeight * 0.13f),
                Offset(right - wheelWidth * 0.5f, top + carHeight * 0.13f),
                Offset(left - wheelWidth * 0.5f, bottom - carHeight * 0.29f),
                Offset(right - wheelWidth * 0.5f, bottom - carHeight * 0.29f)
            ).forEach { corner ->
                drawRoundRect(
                    color = wheelColor,
                    topLeft = corner,
                    size = Size(wheelWidth, wheelHeight),
                    cornerRadius = CornerRadius(wheelWidth * 0.35f)
                )
            }

            // Body.
            drawRoundRect(
                color = bodyColor,
                topLeft = Offset(left, top),
                size = Size(carWidth, carHeight),
                cornerRadius = CornerRadius(carWidth * 0.26f)
            )

            // Cabin / greenhouse.
            drawRoundRect(
                color = roofColor,
                topLeft = Offset(left + carWidth * 0.13f, top + carHeight * 0.22f),
                size = Size(carWidth * 0.74f, carHeight * 0.42f),
                cornerRadius = CornerRadius(carWidth * 0.16f)
            )

            // Windscreen and rear glass as thin accent strokes.
            val glassStroke = 1.5.dp.toPx()
            drawLine(
                accent.copy(alpha = 0.55f),
                Offset(left + carWidth * 0.20f, top + carHeight * 0.25f),
                Offset(right - carWidth * 0.20f, top + carHeight * 0.25f),
                strokeWidth = glassStroke
            )
            drawLine(
                accent.copy(alpha = 0.35f),
                Offset(left + carWidth * 0.20f, top + carHeight * 0.60f),
                Offset(right - carWidth * 0.20f, top + carHeight * 0.60f),
                strokeWidth = glassStroke
            )

            // Centre spine.
            drawLine(
                accent.copy(alpha = 0.35f),
                Offset(size.width / 2f, top + carHeight * 0.66f),
                Offset(size.width / 2f, bottom - carHeight * 0.05f),
                strokeWidth = 2.dp.toPx()
            )

            // Headlights and tail lights.
            val lightHeight = carHeight * 0.025f
            val lightWidth = carWidth * 0.17f
            val lightColor = if (data.connected || data.isDemoMode) accent else detailColor.copy(alpha = 0.45f)
            drawRoundRect(
                color = lightColor,
                topLeft = Offset(left + carWidth * 0.17f, top + carHeight * 0.03f),
                size = Size(lightWidth, lightHeight),
                cornerRadius = CornerRadius(lightHeight / 2f)
            )
            drawRoundRect(
                color = lightColor,
                topLeft = Offset(right - carWidth * 0.17f - lightWidth, top + carHeight * 0.03f),
                size = Size(lightWidth, lightHeight),
                cornerRadius = CornerRadius(lightHeight / 2f)
            )
            drawRoundRect(
                color = warning.copy(alpha = 0.85f),
                topLeft = Offset(left + carWidth * 0.17f, bottom - carHeight * 0.055f),
                size = Size(lightWidth, lightHeight),
                cornerRadius = CornerRadius(lightHeight / 2f)
            )
            drawRoundRect(
                color = warning.copy(alpha = 0.85f),
                topLeft = Offset(right - carWidth * 0.17f - lightWidth, bottom - carHeight * 0.055f),
                size = Size(lightWidth, lightHeight),
                cornerRadius = CornerRadius(lightHeight / 2f)
            )

            // Soft outline keeps the silhouette readable on light themes.
            drawRoundRect(
                color = accent.copy(alpha = 0.30f),
                topLeft = Offset(left, top),
                size = Size(carWidth, carHeight),
                cornerRadius = CornerRadius(carWidth * 0.26f),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Column(Modifier.align(Alignment.TopStart)) {
            Text("VEHICLE", color = SecondaryText(), fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Text(data.gearText, color = PrimaryText(), style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
            Text(if (data.isDemoMode) "DEMO" else "OBD", color = if (data.isDemoMode) WarningAmber() else TealAccent(),
                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(data.connectionLabel, color = SecondaryText(), fontSize = 10.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Text(
            "SPORTAGE",
            color = SecondaryText().copy(alpha = 0.7f),
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Five-icon quick dock shared by both layout themes: music, navigation, phone, all apps
 * and OBD Bluetooth. Deliberately fixed at five targets so it matches the launcher
 * reference and stays thumb-reachable on 1024x600 head units.
 */
@Composable
fun DashboardDock(actions: DashboardActions, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("dashboard_dock"),
        color = DarkSurface(),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockButton(Icons.Default.Headphones, "Open music app", actions.onOpenMusic)
            DockButton(Icons.Default.Navigation, "Open navigation app", actions.onOpenNavigation)
            DockButton(Icons.Default.Phone, "Open phone dialer", actions.onOpenPhone)
            DockButton(Icons.Default.Apps, "Open installed apps", actions.onOpenApps)
            DockButton(Icons.Default.Bluetooth, "OBD Bluetooth", actions.onOpenBluetooth)
        }
    }
}

@Composable
private fun DockButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(icon, contentDescription = description, tint = ElectricCyan(), modifier = Modifier.size(24.dp))
    }
}
