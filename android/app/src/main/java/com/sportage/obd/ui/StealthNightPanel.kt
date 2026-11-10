package com.sportage.obd.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * OLED "Stealth Cockpit" Night Panel.
 *
 * Inspired by classic aviation blackouts and Swedish night cockpit ergonomics:
 * - Dims the entire head unit / phone display to true pitch black (`#000000`).
 * - Completely silences background navigation maps, dials, busy telemetry grids, and borders.
 * - Renders only 3 safety-critical, glow-dimmed elements in amber and red:
 *     1. Large tabular speed display (km/h)
 *     2. Minimalist coolant over-temperature warning indicator (> 105°C)
 *     3. Active speed limit / cruise speed delta
 */
@Composable
fun StealthNightPanel(
    speed: Int?,
    speedUnit: String,
    coolantC: Int?,
    speedAlertThreshold: Int,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOverheating = coolantC != null && coolantC >= 105
    val currentSpeed = speed ?: 0

    // Pulsing alert for coolant warning if active
    val infiniteTransition = rememberInfiniteTransition(label = "stealth_overheat_pulse")
    val alertAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alert_alpha"
    )

    // Calculate speed delta relative to speed limit threshold (e.g. 100 km/h)
    val speedDelta = if (speedAlertThreshold > 0 && speed != null) {
        currentSpeed - speedAlertThreshold
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar: Minimalist Coolant Over-Temperature Indicator & Exit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Coolant Status Indicator
                Surface(
                    color = if (isOverheating) Color(0x33FF1744) else Color(0x111E293B),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isOverheating) Color(0xFFFF1744).copy(alpha = alertAlpha) else Color(0x22475569)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isOverheating) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Coolant Overheating Alert",
                                tint = Color(0xFFFF1744).copy(alpha = alertAlpha),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (isOverheating) "COOLANT CRITICAL ${coolantC}°C" else "${coolantC ?: "--"}°C",
                            color = if (isOverheating) Color(0xFFFF1744) else Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                }

                // Dedicated Exit Stealth Button (64dp minimum touch target)
                FilledTonalButton(
                    onClick = onExit,
                    modifier = Modifier.heightIn(min = 64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFF94A3B8)
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Stealth Mode", Modifier.size(20.dp))
                    Text(
                        text = "  EXIT STEALTH",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // 2. Central Focus: Tabular Giant Speed Display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Night Amber / Deep Orange glow coloration
                Text(
                    text = speed?.toString() ?: "--",
                    color = Color(0xFFFFB300), // Classic night-cockpit amber
                    fontSize = 128.sp,
                    lineHeight = 128.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = speedUnit.uppercase(Locale.US),
                    color = Color(0xFFB45309), // Dim amber secondary
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 4.sp
                )
            }

            // 3. Bottom Bar: Active Cruise / Speed-Limit Delta Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (speedDelta != null) {
                    val isOverspeed = speedDelta > 0
                    val deltaColor = if (isOverspeed) Color(0xFFFF5252) else Color(0xFF4ADE80)
                    val sign = if (isOverspeed) "+" else ""

                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, deltaColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "LIMIT ${speedAlertThreshold} ${speedUnit}",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = "${sign}${speedDelta} ${speedUnit}",
                                color = deltaColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                    }
                } else {
                    Text(
                        text = "STEALTH COCKPIT ACTIVE · TAP EXIT TO RESTORE",
                        color = Color(0xFF475569),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
