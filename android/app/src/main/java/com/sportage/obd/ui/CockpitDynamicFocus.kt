package com.sportage.obd.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.sportage.obd.ui.theme.CriticalRed
import com.sportage.obd.ui.theme.WarningAmber

/**
 * Speed-dependent glanceability engine.
 *
 * - At 0 km/h (idle): full density, all telemetry at 100% opacity.
 * - Above 60 km/h (highway): secondary cards fade to 35%, primary indicators scale +25%.
 */
object CockpitDynamicFocus {
    const val HIGHWAY_SPEED_THRESHOLD_KMH = 60

    fun secondaryTelemetryAlpha(speedKmh: Int?): Float =
        if ((speedKmh ?: 0) > HIGHWAY_SPEED_THRESHOLD_KMH) 0.35f else 1f

    fun secondaryCardAlpha(speedKmh: Int?): Float = secondaryTelemetryAlpha(speedKmh)

    fun dynamicTelemetryAlpha(speedKmh: Int?): Float = secondaryTelemetryAlpha(speedKmh)

    fun primaryIndicatorScale(speedKmh: Int?): Float =
        if ((speedKmh ?: 0) > HIGHWAY_SPEED_THRESHOLD_KMH) 1.25f else 1f

    fun speedDependentScale(speedKmh: Int?): Float = primaryIndicatorScale(speedKmh)

    fun isHighwayCruise(speedKmh: Int?): Boolean =
        (speedKmh ?: 0) > HIGHWAY_SPEED_THRESHOLD_KMH

    fun isIdle(speedKmh: Int?): Boolean = (speedKmh ?: 0) <= 0
}

@Composable
fun DynamicCockpitFocus(
    speedKmh: Int?,
    modifier: Modifier = Modifier,
    content: @Composable (secondaryAlpha: Float, primaryScale: Float) -> Unit
) {
    val secondaryAlpha by animateFloatAsState(
        targetValue = CockpitDynamicFocus.secondaryTelemetryAlpha(speedKmh),
        animationSpec = tween(300),
        label = "secondary_alpha"
    )
    val primaryScale by animateFloatAsState(
        targetValue = CockpitDynamicFocus.primaryIndicatorScale(speedKmh),
        animationSpec = tween(300),
        label = "primary_scale"
    )
    Box(modifier = modifier.testTag("dynamic_cockpit_focus")) {
        content(secondaryAlpha, primaryScale)
    }
}

/**
 * Peripheral halo color engine.
 *
 * Coolant:
 *  - 75-95 C: neutral
 *  - 96-104 C: amber
 *  - 105+ C: pulsing crimson
 *
 * RPM redline (> 6000) also drives crimson.
 */
fun peripheralHaloColor(coolantC: Int?, rpm: Int? = null): Color {
    val coolant = coolantC ?: 0
    val redline = (rpm ?: 0) >= 6000
    return when {
        coolant >= 105 || redline -> Color(0xFFFF1744)
        coolant in 96..104 -> Color(0xFFFFB300)
        else -> Color(0xFF94A3B8)
    }
}

fun coolantHaloColor(coolantC: Int?): Color = peripheralHaloColor(coolantC, null)

fun rpmHaloColor(rpm: Int?): Color =
    if ((rpm ?: 0) >= 6000) Color(0xFFFF1744) else Color(0xFF94A3B8)

@Composable
fun PeripheralAlertHalo(
    coolantC: Int?,
    rpm: Int? = null,
    modifier: Modifier = Modifier,
    pulse: Boolean = true
) {
    val color = peripheralHaloColor(coolantC, rpm)
    val isCritical = color == CriticalRed()
    Canvas(modifier = modifier.fillMaxSize().testTag("peripheral_alert_halo")) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = if (isCritical) 0.55f else 0.22f), Color.Transparent),
                center = center,
                radius = size.minDimension / 2f
            ),
            radius = size.minDimension / 2f,
            center = center
        )
    }
}
@Composable
fun CockpitDynamicFocus(
    speedKmh: Int?,
    modifier: Modifier = Modifier,
    content: @Composable (secondaryAlpha: Float, primaryScale: Float) -> Unit
) = DynamicCockpitFocus(speedKmh = speedKmh, modifier = modifier, content = content)
