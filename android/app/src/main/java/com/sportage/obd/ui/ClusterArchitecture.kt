package com.sportage.obd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.WarningAmber

/**
 * Four distinct cluster structural layouts (not just color palettes).
 */
sealed interface ClusterArchitecture {
    val id: String
    val title: String
    val description: String

    data object ClassicChronograph : ClusterArchitecture {
        override val id = "classic-chronograph"
        override val title = "Classic Chronograph"
        override val description = "Dual analog dials with photorealistic radial bezels, physical-style needles, and central MFD."
    }

    data object TrackGtMotorsport : ClusterArchitecture {
        override val id = "track-gt-motorsport"
        override val title = "Track GT Motorsport"
        override val description = "Giant central digital tachometer with prominent gear indicator, top sequential LED shift light bar, and G-force friction circle."
    }

    data object NavigatorGlass : ClusterArchitecture {
        override val id = "navigator-glass"
        override val title = "Navigator Glass"
        override val description = "Full-screen 3D rotating map (heading-up) with floating semi-transparent speed and RPM glass cards in lower corners."
    }

    data object MinimalCyberSlate : ClusterArchitecture {
        override val id = "minimal-cyber-slate"
        override val title = "Minimal Cyber Slate"
        override val description = "Clean digital layout with zero circular dials, floating linear ribbon arcs and oversized tabular numeric speed typography."
    }

    companion object {
        val all: List<ClusterArchitecture> = listOf(
            ClassicChronograph, TrackGtMotorsport, NavigatorGlass, MinimalCyberSlate
        )

        fun fromId(id: String?): ClusterArchitecture =
            all.firstOrNull { it.id == id } ?: ClassicChronograph
    }
}

@Composable
fun ClassicChronographCluster(
    speed: Int? = null,
    speedUnit: String = "km/h",
    rpm: Int? = null,
    modifier: Modifier = Modifier
) {
    Surface(color = DarkSurface(), modifier = modifier.fillMaxSize().background(Color.Black).testTag("cluster_classic_chronograph")) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CLASSIC CHRONOGRAPH", color = SecondaryText(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("${speed ?: "--"} $speedUnit", color = ElectricCyan(), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text("${rpm ?: "--"} rpm", color = WarningAmber(), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("MFD", color = SecondaryText(), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun TrackGtMotorsportCluster(
    speed: Int? = null,
    speedUnit: String = "km/h",
    rpm: Int? = null,
    gear: Int = 0,
    modifier: Modifier = Modifier
) {
    Surface(color = DarkSurface(), modifier = modifier.fillMaxSize().background(Color.Black).testTag("cluster_track_gt_motorsport")) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SHIFT LIGHT", color = WarningAmber(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).background(Color.DarkGray))
            Spacer(Modifier.height(12.dp))
            Text("${rpm ?: "--"}", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black)
            Text("RPM", color = SecondaryText(), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text("D$gear", color = ElectricCyan(), fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text("${speed ?: "--"} $speedUnit", color = PrimaryText(), fontSize = 20.sp)
        }
    }
}

@Composable
fun NavigatorGlassCluster(
    speed: Int? = null,
    speedUnit: String = "km/h",
    rpm: Int? = null,
    modifier: Modifier = Modifier
) {
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("cluster_navigator_glass")) {
        Text("MAP (heading-up)", color = SecondaryText(), modifier = Modifier.align(Alignment.Center), fontSize = 14.sp)
        Surface(
            color = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
        ) {
            Text("${speed ?: "--"} $speedUnit", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp))
        }
        Surface(
            color = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
        ) {
            Text("${rpm ?: "--"} rpm", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
fun MinimalCyberSlateCluster(
    speed: Int? = null,
    speedUnit: String = "km/h",
    rpm: Int? = null,
    modifier: Modifier = Modifier
) {
    Surface(color = Color(0xFF0A0F14), modifier = modifier.fillMaxSize().testTag("cluster_minimal_cyber_slate")) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${speed ?: "--"}", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center)
            Text(speedUnit.uppercase(), color = ElectricCyan(), fontSize = 14.sp, letterSpacing = 4.sp)
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                drawRect(color = Color(0xFF00E5FF))
            }
            Spacer(Modifier.height(8.dp))
            Text("${rpm ?: "--"} rpm", color = SecondaryText(), fontSize = 16.sp)
        }
    }
}

@Composable
fun ClusterArchitectureHost(
    architecture: ClusterArchitecture,
    speed: Int? = null,
    speedUnit: String = "km/h",
    rpm: Int? = null,
    gear: Int = 0,
    modifier: Modifier = Modifier
) {
    when (architecture) {
        ClusterArchitecture.ClassicChronograph -> ClassicChronographCluster(speed, speedUnit, rpm, modifier)
        ClusterArchitecture.TrackGtMotorsport -> TrackGtMotorsportCluster(speed, speedUnit, rpm, gear, modifier)
        ClusterArchitecture.NavigatorGlass -> NavigatorGlassCluster(speed, speedUnit, rpm, modifier)
        ClusterArchitecture.MinimalCyberSlate -> MinimalCyberSlateCluster(speed, speedUnit, rpm, modifier)
    }
}