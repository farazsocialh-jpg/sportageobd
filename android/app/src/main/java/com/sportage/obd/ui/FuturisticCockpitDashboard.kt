package com.sportage.obd.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.VehicleTelemetry
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun FuturisticCockpitDashboard(
    telemetry: VehicleTelemetry,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF06070B), Color(0xFF0B111E)))
            )
            .padding(12.dp)
            .testTag("dashboard_futuristic_cockpit")
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Left media dial
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0E1320)),
                contentAlignment = Alignment.Center
            ) {
                MediaDial()
            }
            // Center road
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF070B12)),
                contentAlignment = Alignment.Center
            ) {
                HighwayView(telemetry.speedKmh)
            }
            // Right speedometer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0E1320)),
                contentAlignment = Alignment.Center
            ) {
                NeonSpeedometer(telemetry)
            }
        }
        // bottom dock
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf("Music", "Browser", "Apps", "Bluetooth", "Theme").forEach { label ->
                Text(label, color = Color(0xFF00E5FF), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MediaDial() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(Color(0xFF00E5FF).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("Now playing", color = Color(0xFFEAF5FA), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Artist", color = Color(0xFF94A3B8), fontSize = 11.sp)
        Row {
            IconButton(onClick = {}) { Icon(Icons.Default.SkipPrevious, "Prev", tint = Color(0xFF94A3B8)) }
            IconButton(onClick = {}) { Icon(Icons.Default.PlayArrow, "Play", tint = Color(0xFF00E5FF)) }
            IconButton(onClick = {}) { Icon(Icons.Default.SkipNext, "Next", tint = Color(0xFF94A3B8)) }
        }
    }
}

@Composable
private fun HighwayView(speedKmh: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // horizon
        drawLine(Color(0xFF1A2A3A), Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 2f)
        // speed lines
        val count = (speedKmh / 20).coerceIn(2, 12)
        for (i in 0 until count) {
            val y = h * 0.55f + i * 8f
            drawLine(
                color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                start = Offset(w * 0.45f, y),
                end = Offset(w * 0.55f, y),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )
        }
        // car icon (simple rect)
        drawRect(
            color = Color(0xFF3A4D63),
            topLeft = Offset(w * 0.46f, h * 0.6f),
            size = androidx.compose.ui.geometry.Size(w * 0.08f, h * 0.12f)
        )
    }
}

@Composable
private fun NeonSpeedometer(telemetry: VehicleTelemetry) {
    val speedFraction by animateFloatAsState(
        targetValue = (telemetry.speedKmh.toFloat() / 260f).coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "speed"
    )
    val rpmFraction = (telemetry.rpm.toFloat() / 8000f).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 12.dp.toPx()
            // outer rpm arc (gradient approximated by magenta near end)
            drawArc(
                color = Color(0xFFFF2D78).copy(alpha = 0.85f),
                startAngle = 135f,
                sweepAngle = 270f * rpmFraction,
                useCenter = false,
                topLeft = Offset(centre.x - radius, centre.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
            // track
            drawArc(
                color = Color(0xFF1A2430),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(centre.x - radius, centre.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
            // inner speed arc
            val innerRadius = radius - 18.dp.toPx()
            drawArc(
                color = Color(0xFF00E5FF),
                startAngle = 135f,
                sweepAngle = 270f * speedFraction,
                useCenter = false,
                topLeft = Offset(centre.x - innerRadius, centre.y - innerRadius),
                size = androidx.compose.ui.geometry.Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                telemetry.speedKmh.toString(),
                color = Color(0xFFEAF5FA),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            Text("km/h", color = Color(0xFF94A3B8), fontSize = 12.sp)
            Text("${telemetry.rpm} rpm", color = Color(0xFFFF2D78), fontSize = 12.sp)
        }
    }
}