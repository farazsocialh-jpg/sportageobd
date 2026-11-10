package com.sportage.obd.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * OLED Distraction-Free Stealth Night Panel for Kia Sportage cockpit.
 *
 * Implements Module 3:
 * - True pitch black background (#000000) for zero OLED light spill during night driving.
 * - Tabular giant numeric speed (km/h) display with fontFeatureSettings = "tnum".
 * - Minimalist coolant over-temperature warning LED (pulsing red only when coolant > 105°C).
 * - Compact active warnings / DTC alert status banner.
 * - 64dp minimum touch target exit control.
 */
@Composable
fun StealthNightPanel(
    speed: Int?,
    speedUnit: String = "km/h",
    coolantC: Int? = null,
    coolantThresholdC: Float = 105f,
    speedAlertThreshold: Int = 100,
    speedLimitDeltaKmh: Int? = null,
    activeAlerts: List<String> = emptyList(),
    onExit: () -> Unit = {},
    onDismiss: () -> Unit = onExit,
    modifier: Modifier = Modifier
) {
    val isOverheating = coolantC != null && coolantC >= coolantThresholdC
    val currentSpeed = speed ?: 0

    // Pulsing alert animation for critical warnings
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

    // Calculate speed delta relative to speed limit threshold if not explicitly passed
    val effectiveDelta = speedLimitDeltaKmh ?: if (speedAlertThreshold > 0 && speed != null) {
        currentSpeed - speedAlertThreshold
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar: Minimalist Coolant Warning LED & Dedicated 64dp Exit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Coolant Warning LED Indicator
                Surface(
                    color = if (isOverheating) Color(0x33FF1744) else Color(0x111E293B),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isOverheating) Color(0xFFFF1744).copy(alpha = alertAlpha) else Color(0x22475569)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Subtle status LED dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isOverheating) Color(0xFFFF1744).copy(alpha = alertAlpha) else Color(0xFF00E676),
                                    shape = CircleShape
                                )
                        )
                        if (isOverheating) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Coolant Overheating Alert",
                                tint = Color(0xFFFF1744).copy(alpha = alertAlpha),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (isOverheating) "COOLANT OVERHEAT ${coolantC}°C" else "${coolantC ?: "--"}°C",
                            color = if (isOverheating) Color(0xFFFF1744) else Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                }

                // Dedicated Exit Stealth Button (64dp minimum touch target for driver usability)
                FilledTonalButton(
                    onClick = {
                        onExit()
                        onDismiss()
                    },
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

            // 2. Central Focus: Giant Tabular Numeric Speed Display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = speed?.toString() ?: "--",
                    color = Color(0xFFFFB300), // Classic aviation night-cockpit amber
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

            // 3. Bottom Bar: Active Alerts and Speed Limit Delta
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active alerts banner if any exist
                if (activeAlerts.isNotEmpty()) {
                    Surface(
                        color = Color(0x33FF1744),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF1744).copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF1744),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = activeAlerts.first(),
                                color = Color(0xFFFF8A80),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (effectiveDelta != null) {
                    val isOverspeed = effectiveDelta > 0
                    val deltaColor = if (isOverspeed) Color(0xFFFF5252) else Color(0xFF4ADE80)
                    val sign = if (isOverspeed) "+" else ""

                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, deltaColor.copy(alpha = 0.4f))
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
                                text = "${sign}${effectiveDelta} ${speedUnit}",
                                color = deltaColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                    }
                } else {
                    Text(
                        text = "STEALTH COCKPIT ACTIVE · OLED BLACKOUT",
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
