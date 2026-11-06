package com.sportage.obd.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.launcher.LauncherSettings
import com.sportage.obd.launcher.PinnedApp
import com.sportage.obd.obd.HealthReport
import com.sportage.obd.ui.theme.AmberWarning
import com.sportage.obd.ui.theme.CriticalRed
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.TealAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The unified live-data strip above the home map.
 *
 * Phase 4's premise is that a driver should not have to change pages to answer "is the car fine,
 * where am I going, and what is playing". This strip answers the first part from data the app
 * already has — health score, stored faults, the running trip and the active driver profile — and
 * puts the driver's pinned apps one tap away.
 */
/** Thin adapter so the launcher screen does not have to know the health model's shape. */
@Composable
fun HomeOverview(
    healthReport: HealthReport?,
    dtcCodes: Int,
    tripDistanceKm: Double,
    tripSeconds: Long,
    profileName: String,
    settings: LauncherSettings,
    onNavigatePage: (Int) -> Unit,
    onLaunchPinned: (PinnedApp) -> Unit,
    onSetDriveMode: (Boolean) -> Unit
) {
    HomeOverviewStrip(
        healthScore = healthReport?.score,
        healthGrade = healthReport?.grade?.name,
        dtcCount = dtcCodes,
        tripDistanceKm = tripDistanceKm,
        tripMinutes = tripSeconds / 60,
        profileName = profileName,
        pinnedApps = settings.pinnedApps,
        onLaunchPinned = onLaunchPinned,
        onOpenDiagnostics = { onNavigatePage(2) },
        onOpenProfile = { onNavigatePage(5) },
        onToggleDriveMode = { onSetDriveMode(true) }
    )
}

@Composable
fun HomeOverviewStrip(
    healthScore: Int?,
    healthGrade: String?,
    dtcCount: Int,
    tripDistanceKm: Double,
    tripMinutes: Long,
    profileName: String,
    pinnedApps: List<PinnedApp>,
    onLaunchPinned: (PinnedApp) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenProfile: () -> Unit,
    onToggleDriveMode: () -> Unit
) {
    val context = LocalContext.current
    var icons by remember(pinnedApps) { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    LaunchedEffect(pinnedApps) {
        icons = withContext(Dispatchers.IO) {
            runCatching { loadPinnedAppIcons(context, pinnedApps) }.getOrDefault(emptyMap())
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OverviewChip(
            label = "VEHICLE HEALTH",
            value = when {
                healthScore == null -> "—"
                else -> "$healthScore / 100" + (healthGrade?.let { " · $it" } ?: "")
            },
            accent = when {
                healthScore == null -> SecondaryText
                healthScore >= 85 -> TealAccent
                healthScore >= 65 -> AmberWarning
                else -> CriticalRed
            },
            onClick = onOpenDiagnostics
        )

        OverviewChip(
            label = "FAULTS",
            value = if (dtcCount == 0) "None stored" else "$dtcCount stored",
            accent = if (dtcCount == 0) TealAccent else CriticalRed,
            onClick = onOpenDiagnostics
        )

        OverviewChip(
            label = "THIS TRIP",
            value = String.format("%.1f km · %d min", tripDistanceKm, tripMinutes),
            accent = ElectricCyan,
            onClick = null
        )

        OverviewChip(
            label = "DRIVER",
            value = profileName,
            accent = Color(0xFF8B5CF6),
            onClick = onOpenProfile
        )

        Surface(
            modifier = Modifier
                .height(40.dp)
                .clickable { onToggleDriveMode() },
            color = Color(0xFF0E1621),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "DRIVE MODE",
                    color = ElectricCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        pinnedApps.forEach { pin ->
            val bitmap = icons[pin.packageName + "/" + pin.activityName]
            Surface(
                modifier = Modifier
                    .height(40.dp)
                    .clickable { onLaunchPinned(pin) },
                color = Color(0xFF0E1621),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = pin.label,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = null,
                            tint = SecondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        pin.label,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(78.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewChip(
    label: String,
    value: String,
    accent: Color,
    onClick: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .height(40.dp)
            .let { if (onClick != null) it.clickable { onClick() } else it },
        color = Color(0xFF0E1621),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, color = SecondaryText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Text(
                value,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
