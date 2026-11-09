package com.sportage.obd.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.DarkBackground
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.GaugeFill
import com.sportage.obd.ui.theme.GaugeTrack
import com.sportage.obd.ui.theme.OnAccent
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.WarningAmber
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Theme A — circular gauge dashboard.
 *
 * Landscape (head units): circular music player left, car graphic centre, large circular
 * speedometer right, five-icon dock along the bottom.
 * Portrait: the speedometer dominates the top, car and music sit side by side below it.
 */
@Composable
fun CircularDashboardTheme(
    data: DashboardData,
    actions: DashboardActions,
    media: DashboardMedia,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().testTag("dashboard_circular_theme")
    ) {
        val landscape = maxWidth >= maxHeight
        if (landscape) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularMusicPlayer(media, Modifier.weight(1f).fillMaxHeight())
                    CarGraphicPanel(data, Modifier.weight(1.15f).fillMaxHeight())
                    CircularSpeedometer(data, actions, Modifier.weight(1f).fillMaxHeight())
                }
                DashboardDock(actions)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.weight(1.2f).fillMaxWidth().padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularSpeedometer(
                        data = data,
                        actions = actions,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)
                    )
                }
                Row(
                    modifier = Modifier.weight(0.9f).fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularMusicPlayer(media, Modifier.weight(1f).fillMaxHeight())
                    CarGraphicPanel(data, Modifier.weight(1f).fillMaxHeight())
                }
                DashboardDock(actions)
            }
        }
    }
}

@Composable
private fun CircularSpeedometer(
    data: DashboardData,
    actions: DashboardActions,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("circular_speedometer"),
        color = DarkSurface(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceElevated())
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val diameter = minOf(maxWidth, maxHeight)
            val speedFont = (diameter.value * 0.27f).coerceIn(26f, 76f)
            val maxSpeed = if (data.speedUnit == "mph") 140f else 220f
            val speedColor = ElectricCyan()
            val trackColor = GaugeTrack()
            val fillColor = GaugeFill()
            val rpmColor = WarningAmber()
            val needleColor = PrimaryText()
            val hubColor = DarkBackground()
            val tickColor = SecondaryText().copy(alpha = 0.55f)
            val speedFraction = ((data.speed ?: 0).toFloat() / maxSpeed).coerceIn(0f, 1f)
            val rpmFraction = ((data.rpm ?: 0).toFloat() / 7000f).coerceIn(0f, 1f)

            Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val startAngle = 135f
                    val sweepAngle = 270f
                    val outerStroke = 12.dp.toPx()
                    val inset = outerStroke / 2f + 2.dp.toPx()
                    val radius = (size.minDimension / 2f) - inset
                    val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
                    val topLeft = Offset((size.width - radius * 2f) / 2f, (size.height - radius * 2f) / 2f)
                    val centre = Offset(size.width / 2f, size.height / 2f)

                    // Outer speed track.
                    drawArc(
                        color = trackColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = outerStroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = fillColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * speedFraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = outerStroke, cap = StrokeCap.Round)
                    )

                    // Inner RPM ring.
                    val rpmInset = inset + outerStroke + 7.dp.toPx()
                    val rpmRadius = (size.minDimension / 2f) - rpmInset
                    val rpmSize = androidx.compose.ui.geometry.Size(rpmRadius * 2f, rpmRadius * 2f)
                    val rpmTopLeft = Offset((size.width - rpmRadius * 2f) / 2f, (size.height - rpmRadius * 2f) / 2f)
                    drawArc(
                        color = trackColor.copy(alpha = 0.7f),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = rpmTopLeft,
                        size = rpmSize,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = rpmColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * rpmFraction,
                        useCenter = false,
                        topLeft = rpmTopLeft,
                        size = rpmSize,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Major ticks around the outer track.
                    for (i in 0..10) {
                        val angle = Math.toRadians((startAngle + sweepAngle * i / 10f).toDouble())
                        val tickOuter = radius
                        val tickInner = radius - if (i % 5 == 0) 15.dp.toPx() else 8.dp.toPx()
                        drawLine(
                            color = tickColor,
                            start = centre + Offset(
                                (cos(angle) * tickInner).toFloat(),
                                (sin(angle) * tickInner).toFloat()
                            ),
                            end = centre + Offset(
                                (cos(angle) * tickOuter).toFloat(),
                                (sin(angle) * tickOuter).toFloat()
                            ),
                            strokeWidth = if (i % 5 == 0) 3.dp.toPx() else 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Speed needle.
                    val needleAngle = Math.toRadians((startAngle + sweepAngle * speedFraction).toDouble())
                    val needleLength = radius * 0.78f
                    drawLine(
                        color = needleColor,
                        start = centre,
                        end = centre + Offset(
                            (cos(needleAngle) * needleLength).toFloat(),
                            (sin(needleAngle) * needleLength).toFloat()
                        ),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(speedColor, radius = 6.dp.toPx(), center = centre)
                    drawCircle(hubColor, radius = 2.5.dp.toPx(), center = centre)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        data.speedText,
                        color = speedColor,
                        fontSize = speedFont.sp,
                        lineHeight = (speedFont * 1.05f).sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 1
                    )
                    TextButton(
                        onClick = actions.onToggleSpeedUnit,
                        modifier = Modifier.testTag("circular_speed_unit")
                    ) {
                        Text(data.speedUnit, color = PrimaryText(), style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        data.rpm?.let { "$it rpm" } ?: "-- rpm",
                        color = rpmColor,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                    Text(
                        data.gearText,
                        color = SecondaryText(),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
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
private fun CircularMusicPlayer(media: DashboardMedia, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("circular_music_player"),
        color = DarkSurface(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceElevated())
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(ElectricCyan().copy(alpha = 0.28f), DarkSurfaceElevated(), DarkBackground())
                            )
                        )
                        .border(2.dp, ElectricCyan().copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = ElectricCyan(),
                        modifier = Modifier.fillMaxSize(0.32f)
                    )
                }
            }
            Text(
                media.nowPlaying?.title ?: "No music session",
                color = PrimaryText(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                media.nowPlaying?.artist ?: media.nowPlaying?.appName ?: "Tap play to send media keys",
                color = SecondaryText(),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = media.onPrevious, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Previous track", tint = SecondaryText())
                }
                IconButton(
                    onClick = media.onPlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .background(ElectricCyan(), CircleShape)
                        .testTag("circular_music_play")
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
}
