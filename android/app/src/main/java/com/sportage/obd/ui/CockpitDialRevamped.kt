package com.sportage.obd.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.ui.theme.CriticalRed
import com.sportage.obd.ui.theme.DarkBackground
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.WarningAmber
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drive modes detected dynamically from engine telemetry.
 */
enum class DriveMode(val label: String, val primaryColor: Color, val accentGlow: Color) {
    ECO("ECO", Color(0xFF00E5FF), Color(0x3300E676)),
    NORMAL("NORMAL", Color(0xFFFFB300), Color(0x22FFA000)),
    SPORT("SPORT", Color(0xFFFF3D00), Color(0x55D50000)),
    CRITICAL("CRITICAL ALERT", Color(0xFFFF1744), Color(0x88FF1744))
}

/**
 * Cluster initialization needle sweep animation.
 * Sweeps gauge needles from 0 to maximum scale and returns to resting position on startup / connection.
 */
@Composable
fun rememberGaugeSweepAnimation(
    triggerKey: Any?,
    durationMs: Int = 1400
): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(triggerKey) {
        if (triggerKey != null) {
            anim.snapTo(0f)
            // Phase 1: 0 -> 1.0 (Full scale forward sweep)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = (durationMs * 0.45f).toInt(), easing = FastOutSlowInEasing)
            )
            // Phase 2: 1.0 -> 0.0 (Return to resting stop)
            anim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = (durationMs * 0.55f).toInt(), easing = FastOutSlowInEasing)
            )
        }
    }
    return anim.value
}

/**
 * High-realism native Canvas automotive cluster dial for Kia Sportage (Nu 2.0L MPI).
 *
 * Visual Features:
 * - Cluster startup needle sweep (OEM gauge check)
 * - Radial gradient metallic bezel with recessed 3D dial face
 * - Specular glass reflection arcs mimicking curved instrument lens
 * - High-contrast tick hash marks with tabular numeric fonts (`fontFeatureSettings = "tnum"`)
 * - Dynamic drive-mode accent backlighting (ECO cyan, NORMAL amber, SPORT crimson, CRITICAL pulsing red)
 * - 64dp minimum touch target for speed unit toggling
 */
@Composable
fun CockpitDialRevamped(
    speed: Int?,
    speedUnit: String,
    rpm: Int?,
    coolant: Int? = null,
    throttle: Float? = null,
    gear: Int = 0,
    transmissionGear: Int? = null,
    coolantMaxC: Float = 105f,
    connectionState: ConnectionState = ConnectionState.Disconnected,
    onToggleSpeedUnit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Startup Needle Sweep Animation
    val sweepKey = remember(connectionState is ConnectionState.Connected) {
        if (connectionState is ConnectionState.Connected) "connected" else "initial"
    }
    val sweepProgress = rememberGaugeSweepAnimation(triggerKey = sweepKey)
    val isSweeping = sweepProgress > 0.005f

    // 2. Derive Effective Speed & RPM (incorporating sweep progress during cluster check)
    val maxSpeedScale = 220f
    val maxRpmScale = 7000f

    val liveSpeedVal = speed?.toFloat() ?: 0f
    val liveRpmVal = rpm?.toFloat() ?: 0f

    val effectiveSpeedVal = if (isSweeping) maxSpeedScale * sweepProgress else liveSpeedVal
    val effectiveRpmVal = if (isSweeping) maxRpmScale * sweepProgress else liveRpmVal

    val speedFraction = (effectiveSpeedVal / maxSpeedScale).coerceIn(0f, 1f)
    val rpmFraction = (effectiveRpmVal / maxRpmScale).coerceIn(0f, 1f)

    // 3. Dynamic Drive Mode Determination
    val driveMode = when {
        coolant != null && coolant.toFloat() >= coolantMaxC -> DriveMode.CRITICAL
        (rpm ?: 0) > 3800 || (throttle ?: 0f) > 65f -> DriveMode.SPORT
        (speed ?: 0) in 30..90 && (rpm ?: 0) < 2200 && (throttle ?: 0f) < 28f -> DriveMode.ECO
        else -> DriveMode.NORMAL
    }

    // 4. Critical Alert Halo Pulsing Animation
    val infiniteTransition = rememberInfiniteTransition(label = "halo_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // 5. Gear Label Formatting
    val gearText = when (transmissionGear) {
        -2 -> "P"
        -1 -> "R"
        0 -> "N"
        in 1..10 -> "D$transmissionGear"
        else -> if (gear in 1..10) "D$gear" else "P"
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val dialSize = minOf(maxWidth, maxHeight.takeIf { it > 100.dp } ?: 280.dp)

        Box(
            modifier = Modifier.size(dialSize),
            contentAlignment = Alignment.Center
        ) {
            // Native Canvas Instrument Cluster
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.width / 2f - 4.dp.toPx()

                // A. Dynamic Ambient Backlighting Halo
                val haloColor = if (driveMode == DriveMode.CRITICAL) {
                    driveMode.accentGlow.copy(alpha = pulseAlpha)
                } else {
                    driveMode.accentGlow
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(haloColor, Color.Transparent),
                        center = center,
                        radius = outerRadius * 1.05f
                    ),
                    radius = outerRadius * 1.05f,
                    center = center
                )

                // B. Outer Brushed Metallic Bezel Ring
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF2C323B),
                            Color(0xFF141920),
                            Color(0xFF38414D),
                            Color(0xFF181E26),
                            Color(0xFF2C323B)
                        ),
                        center = center
                    ),
                    radius = outerRadius,
                    center = center,
                    style = Stroke(width = 8.dp.toPx())
                )

                // C. Recessed 3D Inner Dial Face
                val innerFaceRadius = outerRadius - 8.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0F141C),
                            Color(0xFF070A0E)
                        ),
                        center = center,
                        radius = innerFaceRadius
                    ),
                    radius = innerFaceRadius,
                    center = center
                )

                // D. Scale Arc Geometries (Sweep: 240 degrees from 150° to 390°)
                val startAngle = 150f
                val sweepAngle = 240f
                fun angleRad(frac: Float): Double = Math.toRadians((startAngle + sweepAngle * frac).toDouble())

                // Outer Arc: Speedometer Track & Active Progress
                val speedTrackRadius = innerFaceRadius - 12.dp.toPx()
                val speedStroke = 9.dp.toPx()
                val speedArcRect = Rect(
                    center = center,
                    radius = speedTrackRadius
                )

                // Track background
                drawArc(
                    color = Color(0x33475569),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = speedArcRect.topLeft,
                    size = speedArcRect.size,
                    style = Stroke(width = speedStroke, cap = StrokeCap.Round)
                )

                // Dynamic Active Speed Arc
                val activeSpeedColor = when (driveMode) {
                    DriveMode.CRITICAL -> CriticalRed()
                    DriveMode.SPORT -> Color(0xFFFF3D00)
                    DriveMode.ECO -> Color(0xFF00E5FF)
                    DriveMode.NORMAL -> Color(0xFFFFB300)
                }

                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            activeSpeedColor.copy(alpha = 0.6f),
                            activeSpeedColor
                        ),
                        center = center
                    ),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * speedFraction,
                    useCenter = false,
                    topLeft = speedArcRect.topLeft,
                    size = speedArcRect.size,
                    style = Stroke(width = speedStroke, cap = StrokeCap.Round)
                )

                // Inner Arc: Tachometer Track & Active Progress (0 - 7000 RPM)
                val rpmTrackRadius = speedTrackRadius - 16.dp.toPx()
                val rpmStroke = 5.dp.toPx()
                val rpmArcRect = Rect(center = center, radius = rpmTrackRadius)

                drawArc(
                    color = Color(0x22334155),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = rpmArcRect.topLeft,
                    size = rpmArcRect.size,
                    style = Stroke(width = rpmStroke, cap = StrokeCap.Round)
                )

                // Tachometer active arc with redline zone (6000-7000 RPM = 0.857 - 1.0)
                val rpmColor = if (effectiveRpmVal >= 6000f) CriticalRed() else Color(0xFFFF9100)
                drawArc(
                    color = rpmColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * rpmFraction,
                    useCenter = false,
                    topLeft = rpmArcRect.topLeft,
                    size = rpmArcRect.size,
                    style = Stroke(width = rpmStroke, cap = StrokeCap.Round)
                )

                // E. Speedometer & Tachometer Tick Hash Marks
                val tickCount = 11 // 0, 20, 40, ... 220
                for (i in 0..tickCount) {
                    val frac = i / tickCount.toFloat()
                    val rad = angleRad(frac)
                    val isMajor = i % 2 == 0
                    val tickLen = if (isMajor) 9.dp.toPx() else 5.dp.toPx()
                    val tickOuter = speedTrackRadius + (speedStroke / 2f) + 3.dp.toPx()
                    val tickInner = tickOuter - tickLen

                    val p1 = Offset(
                        center.x + (tickOuter * cos(rad)).toFloat(),
                        center.y + (tickOuter * sin(rad)).toFloat()
                    )
                    val p2 = Offset(
                        center.x + (tickInner * cos(rad)).toFloat(),
                        center.y + (tickInner * sin(rad)).toFloat()
                    )

                    val isLit = frac <= speedFraction
                    val tickColor = if (isLit) activeSpeedColor else Color(0xFF64748B)
                    drawLine(
                        color = tickColor,
                        start = p1,
                        end = p2,
                        strokeWidth = if (isMajor) 2.5.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // F. Mechanical Needles (Speed & RPM)
                // Speed Needle (Long, vibrant primary pointer)
                val speedAngleRad = angleRad(speedFraction)
                val speedNeedleLen = speedTrackRadius - (speedStroke / 2f) - 2.dp.toPx()
                val speedNeedleEnd = Offset(
                    center.x + (speedNeedleLen * cos(speedAngleRad)).toFloat(),
                    center.y + (speedNeedleLen * sin(speedAngleRad)).toFloat()
                )
                drawLine(
                    color = activeSpeedColor,
                    start = center,
                    end = speedNeedleEnd,
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Tachometer Needle (Shorter, secondary pointer)
                val rpmAngleRad = angleRad(rpmFraction)
                val rpmNeedleLen = rpmTrackRadius - (rpmStroke / 2f) - 2.dp.toPx()
                val rpmNeedleEnd = Offset(
                    center.x + (rpmNeedleLen * cos(rpmAngleRad)).toFloat(),
                    center.y + (rpmNeedleLen * sin(rpmAngleRad)).toFloat()
                )
                drawLine(
                    color = rpmColor.copy(alpha = 0.85f),
                    start = center,
                    end = rpmNeedleEnd,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Needle Hub Cap
                drawCircle(
                    color = Color(0xFF1E293B),
                    radius = 9.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = activeSpeedColor,
                    radius = 4.dp.toPx(),
                    center = center
                )

                // G. Glass Specular Reflection Crescent Arc
                val glassPath = Path().apply {
                    val glassTop = center.y - innerFaceRadius * 0.95f
                    val glassBottom = center.y + innerFaceRadius * 0.15f
                    moveTo(center.x - innerFaceRadius * 0.85f, center.y - innerFaceRadius * 0.2f)
                    cubicTo(
                        center.x - innerFaceRadius * 0.6f, glassTop,
                        center.x + innerFaceRadius * 0.6f, glassTop,
                        center.x + innerFaceRadius * 0.85f, center.y - innerFaceRadius * 0.2f
                    )
                    cubicTo(
                        center.x + innerFaceRadius * 0.5f, glassBottom,
                        center.x - innerFaceRadius * 0.5f, glassBottom,
                        center.x - innerFaceRadius * 0.85f, center.y - innerFaceRadius * 0.2f
                    )
                    close()
                }
                drawPath(
                    path = glassPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.02f),
                            Color.Transparent
                        )
                    )
                )
            }

            // Central Telemetry Cluster Overlay (Crisp tabular numeric fonts)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = dialSize * 0.38f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Large Tabular Digital Speed Value
                val displaySpeedNumber = if (isSweeping) {
                    effectiveSpeedVal.toInt().toString()
                } else {
                    speed?.toString() ?: "--"
                }

                Text(
                    text = displaySpeedNumber,
                    color = PrimaryText(),
                    fontSize = 54.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    textAlign = TextAlign.Center
                )

                // Speed Unit Toggle Button with >= 64dp touch target
                Box(
                    modifier = Modifier
                        .heightIn(min = 64.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleSpeedUnit
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = DarkSurfaceElevated().copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3364748B))
                    ) {
                        Text(
                            text = speedUnit.uppercase(Locale.US),
                            color = SecondaryText(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Bottom Sub-Row: Gear Badge + RPM & Drive Mode Label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Gear Badge
                    Surface(
                        color = if (gearText.startsWith("D")) activeSpeedColor.copy(alpha = 0.2f) else Color(0x33334155),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (gearText.startsWith("D")) activeSpeedColor else Color(0x6664748B)
                        )
                    ) {
                        Text(
                            text = gearText,
                            color = if (gearText.startsWith("D")) activeSpeedColor else PrimaryText(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    // Live RPM Display (tabular figures)
                    Text(
                        text = "${effectiveRpmVal.toInt()} rpm",
                        color = rpmColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )

                    // Dynamic Mode Chip
                    Surface(
                        color = driveMode.primaryColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = driveMode.label,
                            color = driveMode.primaryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
