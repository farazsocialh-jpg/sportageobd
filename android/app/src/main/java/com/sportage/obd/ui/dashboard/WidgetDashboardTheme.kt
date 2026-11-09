package com.sportage.obd.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.OnAccent
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.TealAccent
import com.sportage.obd.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Theme B — rectangular widget dashboard.
 *
 * Landscape: left column holds the speed widget above the car graphic; the right column
 * holds the digital clock above the media widget. Portrait folds the same four widgets
 * into a 2x2 grid. A five-icon dock stays pinned to the bottom.
 */
@Composable
fun RectangularDashboardTheme(
    data: DashboardData,
    actions: DashboardActions,
    media: DashboardMedia,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().testTag("dashboard_widgets_theme")
    ) {
        val landscape = maxWidth >= maxHeight
        Column(Modifier.fillMaxSize()) {
            if (landscape) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(0.44f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpeedWidget(data, actions, Modifier.weight(1.15f).fillMaxWidth())
                        CarGraphicPanel(data, Modifier.weight(1f).fillMaxWidth())
                    }
                    Column(
                        modifier = Modifier.weight(0.56f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DigitalClockWidget(data, Modifier.weight(0.85f).fillMaxWidth())
                        MediaWidget(media, actions, Modifier.weight(1.15f).fillMaxWidth())
                    }
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(0.85f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpeedWidget(data, actions, Modifier.weight(1f).fillMaxHeight())
                        DigitalClockWidget(data, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(
                        modifier = Modifier.weight(1.05f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CarGraphicPanel(data, Modifier.weight(0.8f).fillMaxHeight())
                        MediaWidget(media, actions, Modifier.weight(1.2f).fillMaxHeight())
                    }
                }
            }
            DashboardDock(actions)
        }
    }
}

@Composable
private fun SpeedWidget(data: DashboardData, actions: DashboardActions, modifier: Modifier = Modifier) {
    DashboardPanel(modifier.testTag("widget_speed_panel")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "SPEED",
                color = SecondaryText(),
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (data.isDemoMode) "DEMO" else if (data.connected) "LIVE · ${data.measuredHz?.let { "%.1f Hz".format(it) } ?: "OBD"}" else "OFFLINE",
                color = if (data.isDemoMode) WarningAmber() else if (data.connected) TealAccent() else SecondaryText(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            val speedFont = (maxWidth.value * 0.30f).coerceIn(34f, 86f)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    data.speedText,
                    color = ElectricCyan(),
                    fontSize = speedFont.sp,
                    lineHeight = (speedFont * 1.02f).sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                TextButton(
                    onClick = actions.onToggleSpeedUnit,
                    modifier = Modifier.padding(bottom = 6.dp).testTag("widget_speed_unit")
                ) {
                    Text(data.speedUnit, color = PrimaryText(), fontSize = 12.sp)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RPM", color = SecondaryText(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(data.rpm?.let { "$it" } ?: "--", color = WarningAmber(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(data.gearText, color = PrimaryText(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ((data.rpm ?: 0) / 7000f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = WarningAmber(),
            trackColor = DarkSurfaceElevated()
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardStat("Coolant", data.coolant?.let { "$it°C" } ?: "--", Modifier.weight(1f),
                if ((data.coolant ?: 0) > 105) WarningAmber() else PrimaryText())
            DashboardStat("Fuel", data.fuelLevel?.let { "$it%" } ?: "--", Modifier.weight(1f))
            DashboardStat("Battery", data.voltage?.let { "%.1f V".format(it) } ?: "--", Modifier.weight(1f))
        }
    }
}

@Composable
private fun DigitalClockWidget(data: DashboardData, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(5_000)
        }
    }
    val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val dateText = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)

    DashboardPanel(modifier.testTag("widget_clock_panel")) {
        Text(
            "CLOCK",
            color = SecondaryText(),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    timeText,
                    color = PrimaryText(),
                    fontSize = 46.sp,
                    lineHeight = 50.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(dateText, color = SecondaryText(), fontSize = 12.sp, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                data.weatherTempC ?: "--",
                color = PrimaryText(),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                data.weatherText ?: data.gpsStatus ?: "No GPS",
                color = SecondaryText(),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun CarGraphicPanel(data: DashboardData, modifier: Modifier = Modifier) {
    DashboardPanel(modifier) {
        CarGraphic(data, Modifier.fillMaxSize())
    }
}

@Composable
private fun MediaWidget(media: DashboardMedia, actions: DashboardActions, modifier: Modifier = Modifier) {
    DashboardPanel(modifier.testTag("widget_media_panel")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MEDIA",
                color = SecondaryText(),
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = actions.onOpenMusic, modifier = Modifier.height(32.dp)) {
                Text("OPEN APP", color = TealAccent(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ElectricCyan().copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = ElectricCyan())
            }
            Column(Modifier.weight(1f)) {
                Text(
                    media.nowPlaying?.title ?: "No music session",
                    color = PrimaryText(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    media.nowPlaying?.artist ?: media.nowPlaying?.appName ?: "Controls send media keys",
                    color = SecondaryText(),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = media.onPrevious, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.SkipPrevious, "Previous track", tint = SecondaryText())
            }
            IconButton(
                onClick = media.onPlayPause,
                modifier = Modifier
                    .size(46.dp)
                    .background(ElectricCyan(), CircleShape)
                    .testTag("widget_media_play")
            ) {
                Icon(
                    if (media.nowPlaying?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (media.nowPlaying?.isPlaying == true) "Pause" else "Play",
                    tint = OnAccent()
                )
            }
            IconButton(onClick = media.onNext, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.SkipNext, "Next track", tint = SecondaryText())
            }
        }
    }
}
