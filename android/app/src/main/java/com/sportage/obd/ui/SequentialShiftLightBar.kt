package com.sportage.obd.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText

/**
 * GT / F1 Sequential Shift Light Bar for Kia Sportage (Nu 2.0L MPI Gasoline).
 *
 * Implements Module 3:
 * Embeds a 12-segment GT LED shift light bar along the cockpit:
 * - Segments 1–4 (1,500 – 3,500 RPM): Smooth Green LEDs (Low-mid torque build)
 * - Segments 5–8 (3,500 – 5,000 RPM): Vivid Amber LEDs (Peak torque 4,000 RPM plateau)
 * - Segments 9–11 (5,000 – 6,100 RPM): Intense Red LEDs (Peak 154 hp @ 6,200 RPM approach)
 * - Segment 12 (6,200+ RPM Redline): High-frequency pulsing Crimson/White flash prompting an upshift
 */
@Composable
fun SequentialShiftLightBar(
    rpm: Int?,
    modifier: Modifier = Modifier,
    maxRpm: Int = 6800,
    showRpmBadge: Boolean = true
) {
    val currentRpm = rpm ?: 0
    val isRedline = currentRpm >= 6200

    // High-frequency shift prompt flash for Segment 12 (6,200+ RPM)
    val infiniteTransition = rememberInfiniteTransition(label = "shift_light_flash")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 110, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flash_alpha"
    )

    Surface(
        color = DarkSurface(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 12-Segment Sequential Shift LED Array
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (segmentIndex in 1..12) {
                    val segmentThresholdRpm = when (segmentIndex) {
                        // Segments 1–4: 1,500 – 3,500 RPM
                        1 -> 1500
                        2 -> 2167
                        3 -> 2833
                        4 -> 3500
                        // Segments 5–8: 3,500 – 5,000 RPM
                        5 -> 3875
                        6 -> 4250
                        7 -> 4625
                        8 -> 5000
                        // Segments 9–11: 5,000 – 6,100 RPM
                        9 -> 5367
                        10 -> 5733
                        11 -> 6100
                        // Segment 12: 6,200+ RPM Redline
                        12 -> 6200
                        else -> 6200
                    }

                    val isActive = currentRpm >= segmentThresholdRpm

                    val segmentColor = when (segmentIndex) {
                        in 1..4 -> Color(0xFF00E676) // Smooth Green
                        in 5..8 -> Color(0xFFFFB300) // Vivid Amber
                        in 9..11 -> Color(0xFFFF1744) // Intense Red
                        12 -> if (isRedline && flashAlpha > 0.55f) Color(0xFFFFFFFF) else Color(0xFFFF1744) // Pulsing Crimson/White
                        else -> Color(0xFF00E676)
                    }

                    ShiftLightSegment(
                        isActive = isActive,
                        isFlashing = segmentIndex == 12 && isRedline,
                        activeColor = segmentColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (showRpmBadge) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1500",
                        color = SecondaryText(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Tabular Centered RPM Status
                    Text(
                        text = if (isRedline) "SHIFT UP" else "${currentRpm} RPM",
                        color = if (isRedline) Color(0xFFFF1744) else PrimaryText(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )

                    Text(
                        text = "6200+",
                        color = if (isRedline) Color(0xFFFF1744) else SecondaryText(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Individual LED segment with lens reflection and radial bloom shading.
 */
@Composable
private fun ShiftLightSegment(
    isActive: Boolean,
    isFlashing: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val unlitColor = Color(0xFF0F172A)
    val unlitBorder = Color(0xFF334155)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isActive) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isFlashing) 0.85f else 0.40f),
                            activeColor,
                            activeColor.copy(alpha = 0.85f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            unlitColor
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                color = if (isActive) activeColor else unlitBorder,
                shape = RoundedCornerShape(4.dp)
            )
    )
}
