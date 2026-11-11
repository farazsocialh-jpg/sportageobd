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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.VehicleTelemetry
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun ModularCardDashboard(
    telemetry: VehicleTelemetry,
    modifier: Modifier = Modifier
) {
    val bg = Color(0xFF0A0B0E)
    val card = Color(0xFF14161D)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .padding(12.dp)
            .testTag("dashboard_modular_card")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left panel 55%
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(card)
                    .padding(12.dp)
            ) {
                RoadSpeedCard(telemetry)
            }
            // Right panel 45%
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(card)
                        .padding(12.dp)
                ) {
                    FlipClockWidget()
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(card)
                        .padding(12.dp)
                ) {
                    MediaCardWidget()
                }
            }
        }
    }
}

@Composable
private fun RoadSpeedCard(telemetry: VehicleTelemetry) {
    var scroll by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(telemetry.speedKmh) {
        while (true) {
            scroll = (scroll + max(1f, telemetry.speedKmh / 8f)) % 200f
            delay(16)
        }
    }
    val animatedSpeed by animateFloatAsState(
        targetValue = telemetry.speedKmh.toFloat(),
        animationSpec = tween(300),
        label = "speed"
    )
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // road perspective lines
            for (i in 0..8) {
                val y = h * 0.55f + i * 18f + (scroll % 18f)
                drawLine(
                    color = Color(0xFF2A3A4F),
                    start = Offset(w * 0.45f, y),
                    end = Offset(w * 0.55f, y),
                    strokeWidth = 2f
                )
            }
            // side lines
            drawLine(Color(0xFF3A4D63), Offset(w * 0.35f, h), Offset(w * 0.47f, h * 0.45f), 3f)
            drawLine(Color(0xFF3A4D63), Offset(w * 0.65f, h), Offset(w * 0.53f, h * 0.45f), 3f)
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = animatedSpeed.toInt().toString(),
                color = Color(0xFFEAF5FA),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "km/h",
                color = Color(0xFF94A3B8),
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun FlipClockWidget() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val ampm = SimpleDateFormat("a", Locale.getDefault()).format(now)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            time.forEach { ch ->
                if (ch == ':') {
                    Text(":", color = Color(0xFFEAF5FA), fontSize = 44.sp, fontWeight = FontWeight.Bold)
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1116)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(ch.toString(), color = Color(0xFFEAF5FA), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(ampm, color = Color(0xFF94A3B8), fontSize = 12.sp)
    }
}

@Composable
private fun MediaCardWidget() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Track title", color = Color(0xFFEAF5FA), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Artist name", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = {}) { Icon(Icons.Default.SkipPrevious, "Prev", tint = Color(0xFF94A3B8)) }
            IconButton(onClick = {}) { Icon(Icons.Default.PlayArrow, "Play", tint = Color(0xFF00E5FF)) }
            IconButton(onClick = {}) { Icon(Icons.Default.SkipNext, "Next", tint = Color(0xFF94A3B8)) }
        }
    }
}