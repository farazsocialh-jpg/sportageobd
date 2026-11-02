package com.sportage.obd.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.ui.theme.*

data class AndroidRadioPreset(val freq: Float, val name: String)

val ANDROID_RADIO_PRESETS = listOf(
    AndroidRadioPreset(91.7f, "QBS Radio"),
    AndroidRadioPreset(94.0f, "Al Rayyan"),
    AndroidRadioPreset(97.5f, "Doha Hit"),
    AndroidRadioPreset(101.4f, "Sportage FM"),
    AndroidRadioPreset(104.8f, "BBC World"),
    AndroidRadioPreset(107.6f, "Oryx FM")
)

@Composable
fun CarLauncherScreen(
    connectionState: ConnectionState,
    speed: Int?,
    rpm: Int?,
    coolant: Int?,
    voltage: Float?,
    gear: Int,
    onConnectClick: () -> Unit
) {
    val context = LocalContext.current
    var speedUnit by remember { mutableStateOf("km/h") }
    val displaySpeed = if (speed != null) {
        if (speedUnit == "mph") (speed * 0.621371).toInt() else speed
    } else null

    // Audio & Radio State
    var audioSource by remember { mutableStateOf("RADIO") } // "LOCAL_MUSIC" or "RADIO"
    var isMediaPlaying by remember { mutableStateOf(true) }
    var radioFreq by remember { mutableStateOf(101.4f) }
    var radioStationName by remember { mutableStateOf("Sportage Drive FM") }
    var localTrackTitle by remember { mutableStateOf("Local MP3 / Audio Stream") }
    var localTrackArtist by remember { mutableStateOf("Android Music Player") }

    fun sendMediaKey(keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {}
    }

    fun launchLocalMusicApp() {
        val candidates = listOf(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
            Intent(Intent.ACTION_VIEW).apply { type = "audio/*" },
            Intent("android.intent.action.MUSIC_PLAYER")
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    fun launchHeadUnitRadioApp() {
        val radioPackages = listOf(
            "com.ts.main.radio",
            "com.syu.radio",
            "com.microntek.radio",
            "com.android.fmradio"
        )
        for (pkg in radioPackages) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        // Fallback generic music intent
        try {
            val generic = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(generic)
        } catch (_: Exception) {}
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // --- 1. LEFT SLIM IN-CAR DOCK (Optimized for 1024x600 & 1280x720 Head Units) ---
        Column(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(Color(0xFF090D14))
                .border(width = 1.dp, color = Color(0xFF1E293B))
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Home
                IconButton(
                    onClick = { },
                    modifier = Modifier
                        .size(42.dp)
                        .background(ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = ElectricCyan, modifier = Modifier.size(20.dp))
                }

                // Waze External
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("waze://"))
                        try { context.startActivity(intent) } catch (_: Exception) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul")))
                        }
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = "Waze", tint = Color(0xFF33CCFF), modifier = Modifier.size(20.dp))
                }

                // FM Radio Dock Button
                IconButton(
                    onClick = {
                        audioSource = "RADIO"
                        launchHeadUnitRadioApp()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (audioSource == "RADIO") ElectricCyan.copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Icon(Icons.Default.Radio, contentDescription = "FM Radio", tint = if (audioSource == "RADIO") ElectricCyan else Color.Gray, modifier = Modifier.size(20.dp))
                }

                // Local Music Dock Button
                IconButton(
                    onClick = {
                        audioSource = "LOCAL_MUSIC"
                        launchLocalMusicApp()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (audioSource == "LOCAL_MUSIC") Color(0xFF10B981).copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = "Local Music", tint = if (audioSource == "LOCAL_MUSIC") Color(0xFF10B981) else Color.Gray, modifier = Modifier.size(20.dp))
                }

                // Phone
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "Phone", tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                }
            }

            // Bottom Bluetooth Pairing Button
            IconButton(
                onClick = onConnectClick,
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (connectionState is ConnectionState.Connected) TealAccent.copy(alpha = 0.25f)
                        else Color(0xFF1E293B),
                        RoundedCornerShape(10.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth",
                    tint = if (connectionState is ConnectionState.Connected) TealAccent else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // --- 2. MAIN COCKPIT AREA: DUAL DISPLAYS + BOTTOM AUDIO BAR ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // TOP: DUAL-DISPLAY SPLIT COCKPIT (Left: Speed/Gauges | Right: Live Waze Map)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // DISPLAY 1 (LEFT 50%): AUTHORITATIVE SPEEDOMETER & REAL OBD2 GAUGES
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Status Bar inside Display 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "KIA SPORTAGE 2.0 MPI AWD",
                                color = ElectricCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Gear
                                Text(
                                    text = if (gear == 0) "P / N" else "D$gear",
                                    color = TealAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .background(Color(0xFF0E1722), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )

                                // Speed Unit Switch
                                Text(
                                    text = speedUnit.uppercase(),
                                    color = ElectricCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                        .clickable { speedUnit = if (speedUnit == "km/h") "mph" else "km/h" }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Large Glanceable Speedometer Centerpiece
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = displaySpeed?.toString() ?: "--",
                                color = PrimaryText,
                                fontSize = 68.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 68.sp
                            )

                            Text(
                                text = speedUnit.uppercase(),
                                color = ElectricCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            // Sweeping RPM Bar
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.width(220.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "RPM: ${rpm ?: "--"}",
                                    color = SecondaryText,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "6500 MAX",
                                    color = CriticalRed,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            LinearProgressIndicator(
                                progress = { ((rpm ?: 0) / 7000f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .width(220.dp)
                                    .height(6.dp),
                                color = if ((rpm ?: 0) > 6000) CriticalRed else ElectricCyan,
                                trackColor = Color(0xFF0F1722)
                            )
                        }

                        // 4 Real OBD2 Telemetry Gauges at Bottom of Display 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Coolant
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1722)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("COOLANT", color = SecondaryText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = if (coolant != null) "$coolant°C" else "--",
                                        color = PrimaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Battery Voltage
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1722)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("BATTERY", color = SecondaryText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = if (voltage != null) "${"%.1f".format(voltage)}V" else "--",
                                        color = TealAccent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Connection Status
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1722)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("STATUS", color = SecondaryText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = when (connectionState) {
                                            is ConnectionState.Connected -> "LIVE"
                                            is ConnectionState.Connecting -> "PAIR"
                                            else -> "STANDBY"
                                        },
                                        color = if (connectionState is ConnectionState.Connected) TealAccent else Color.Gray,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                // DISPLAY 2 (RIGHT 50%): DEDICATED LIVE WAZE NAVIGATION
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Embedded Live Map View via WebView
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.setGeolocationEnabled(true)
                                    webViewClient = WebViewClient()
                                    loadUrl("https://embed.waze.com/iframe?zoom=15&lat=25.2854&lon=51.5310&ct=livemap&pin=1")
                                }
                            }
                        )

                        // Bottom Trip Status Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0E1722))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "WAZE LIVE TRAFFIC",
                                color = PrimaryText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            Text(
                                text = "GPS ACTIVE",
                                color = TealAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // BOTTOM BAR: UNIVERSAL IN-CAR AUDIO DOCK (LOCAL MUSIC & FM RADIO TUNER)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                color = Color(0xFF080C14),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Source Switcher Tabs
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF0A0E17), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Local Music Tab
                        Surface(
                            modifier = Modifier.clickable { audioSource = "LOCAL_MUSIC" },
                            color = if (audioSource == "LOCAL_MUSIC") ElectricCyan else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (audioSource == "LOCAL_MUSIC") Color(0xFF05070B) else Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Local Music",
                                    color = if (audioSource == "LOCAL_MUSIC") Color(0xFF05070B) else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // FM Radio Tab
                        Surface(
                            modifier = Modifier.clickable { audioSource = "RADIO" },
                            color = if (audioSource == "RADIO") ElectricCyan else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Radio,
                                    contentDescription = null,
                                    tint = if (audioSource == "RADIO") Color(0xFF05070B) else Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "FM Radio",
                                    color = if (audioSource == "RADIO") Color(0xFF05070B) else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // 2. Playback Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (audioSource == "LOCAL_MUSIC") {
                                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                } else {
                                    radioFreq = (radioFreq - 0.1f).coerceAtLeast(87.5f)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = {
                                isMediaPlaying = !isMediaPlaying
                                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .background(ElectricCyan, RoundedCornerShape(17.dp))
                        ) {
                            Icon(
                                if (isMediaPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color(0xFF05070B),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (audioSource == "LOCAL_MUSIC") {
                                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                                } else {
                                    radioFreq = (radioFreq + 0.1f).coerceAtMost(108.0f)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                        }
                    }

                    // 3. Center Info Panel (Local Music Track vs FM Radio Station)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .clickable {
                                if (audioSource == "LOCAL_MUSIC") launchLocalMusicApp() else launchHeadUnitRadioApp()
                            },
                        color = Color(0xFF0A0E17),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        if (audioSource == "RADIO") {
                            // FM Radio Station Display
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFF0B2133),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "${"%.1f".format(radioFreq)} MHz",
                                            color = ElectricCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = radioStationName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "FM1 · Driving Stereo Broadcast",
                                            color = SecondaryText,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                // Quick presets
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ANDROID_RADIO_PRESETS.take(4).forEach { preset ->
                                        Surface(
                                            modifier = Modifier.clickable {
                                                radioFreq = preset.freq
                                                radioStationName = preset.name
                                            },
                                            color = if (Math.abs(radioFreq - preset.freq) < 0.15f) ElectricCyan else Color(0xFF1E293B),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "%.1f".format(preset.freq),
                                                color = if (Math.abs(radioFreq - preset.freq) < 0.15f) Color(0xFF05070B) else Color.LightGray,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Local Music Track Display
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Column {
                                        Text(
                                            text = localTrackTitle,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "$localTrackArtist · Local Storage / USB",
                                            color = SecondaryText,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Text(
                                    text = "OPEN APP",
                                    color = Color(0xFF10B981),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .background(Color(0xFF09291D), RoundedCornerShape(4.dp))
                                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
