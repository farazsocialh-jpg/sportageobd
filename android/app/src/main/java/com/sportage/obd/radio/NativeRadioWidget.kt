package com.sportage.obd.radio

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NativeRadioWidget(
    controller: RadioHardwareController,
    modifier: Modifier = Modifier
) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    DisposableEffect(controller) {
        controller.register()
        onDispose { controller.unregister() }
    }

    val presets = remember { mutableStateListOf<Int>().apply {
        repeat(6) { add(0) }
    } }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF12141A),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("NATIVE RADIO", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)

            // Frequency readout
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isFm = state.band.name.startsWith("FM")
                Text(
                    text = if (isFm) String.format("%.1f", state.frequencyKHz / 1000f) else state.frequencyKHz.toString(),
                    color = Color(0xFF00E5FF),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (isFm) "MHz" else "kHz",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = state.band.name,
                    color = Color(0xFF00E676),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // RDS marquee
            Text(
                text = state.stationName ?: state.rdsRadioText ?: "No RDS data",
                color = Color(0xFFE2E8F0),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().basicMarquee()
            )

            // Controls
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioControlButton("SEEK -", Modifier.weight(1f)) { controller.seekDown() }
                RadioControlButton("TUNE", Modifier.weight(1f)) { /* manual tune dialog hook */ }
                RadioControlButton("SEEK +", Modifier.weight(1f)) { controller.seekUp() }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioControlButton("BAND: ${state.band.name}", Modifier.weight(1f)) { controller.toggleBand() }
                RadioControlButton(if (state.isPoweredOn) "POWER ON" else "POWER OFF", Modifier.weight(1f)) { controller.togglePower() }
            }

            // Presets
            Text("Presets (tap to tune, long-press to save)", color = Color(0xFF94A3B8), fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(6) { index ->
                    val presetFreq = presets[index]
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .size(height = 40.dp, width = 0.dp)
                            .combinedClickable(
                                onClick = {
                                    if (presetFreq > 0) controller.tuneFrequency(presetFreq)
                                },
                                onLongClick = {
                                    presets[index] = state.frequencyKHz
                                    savedMessage = "Saved P${index + 1}"
                                }
                            ),
                        color = if (presetFreq > 0) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF1F2937),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (presetFreq > 0) Color(0xFF00E676) else Color(0xFF374151))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("P${index + 1}", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            savedMessage?.let { msg ->
                Text(msg, color = Color(0xFF00E676), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RadioControlButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        color = Color(0xFF1F2937),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
