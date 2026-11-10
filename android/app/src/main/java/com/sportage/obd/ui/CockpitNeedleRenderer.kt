package com.sportage.obd.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Organic stepper-motor needle physics state helper.
 * Uses a critically damped spring (dampingRatio = 0.78f, stiffness = 180f) to simulate
 * automotive mechanical stepper motors with high-frequency telemetry.
 */
@Composable
fun rememberAnimatedNeedleAngle(
    targetAngle: Float,
    dampingRatio: Float = 0.78f,
    stiffness: Float = 180f
): State<Float> {
    return animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness
        ),
        label = "stepper_needle_angle"
    )
}

/**
 * Native Canvas drawing routines for OEM-grade cockpit instrument needles.
 *
 * Implements Module 3:
 * 1. Stepper-motor animated rotation with critically damped spring (dampingRatio = 0.78f, stiffness = 180f).
 * 2. Dynamic needle drop-shadows offset radially relative to a simulated top-left cockpit light source:
 *    dx = cos(angle) * 4.dp, dy = sin(angle) * 4.dp.
 * 3. Tapered 3D blade geometry with multi-ring metallic hub cap and lit/shaded dual facets.
 * 4. Curved anti-reflective glass glare arc across the upper 40% of the dial face.
 */
object CockpitNeedleRenderer {

    /**
     * Renders a tapered 3D instrument needle with simulated light-source drop shadow.
     *
     * @param center The dial center coordinate.
     * @param angle The current rotation angle in degrees (0° = 3 o'clock).
     * @param needleLength Length from center to pointer tip.
     * @param needleColor Primary blade color (e.g. Electric Cyan, Sport Crimson, Amber).
     * @param hubRadius Radius of the center mechanical pivot cap.
     * @param shadowOffset Radial offset distance for simulated lighting (default 4.dp).
     */
    fun DrawScope.drawNeedle(
        center: Offset,
        angle: Float,
        needleLength: Float,
        needleColor: Color,
        hubRadius: Float = 12.dp.toPx(),
        shadowOffset: Dp = 4.dp
    ) {
        val angleRad = Math.toRadians(angle.toDouble())
        val shadowDistance = shadowOffset.toPx()

        // 1. Dynamic Drop Shadow: Offset radially relative to simulated top-left cockpit light source
        val shadowDx = (cos(angleRad) * shadowDistance).toFloat()
        val shadowDy = (sin(angleRad) * shadowDistance).toFloat()
        val shadowCenter = Offset(center.x + shadowDx, center.y + shadowDy)

        rotate(degrees = angle, pivot = shadowCenter) {
            val shadowPath = Path().apply {
                val baseHalf = hubRadius * 0.40f
                moveTo(shadowCenter.x, shadowCenter.y - baseHalf)
                lineTo(shadowCenter.x + needleLength, shadowCenter.y)
                lineTo(shadowCenter.x, shadowCenter.y + baseHalf)
                close()
            }
            drawPath(
                path = shadowPath,
                color = Color(0x55000000)
            )
        }

        // 2. Primary 3D Needle Blade
        rotate(degrees = angle, pivot = center) {
            val baseHalf = hubRadius * 0.38f
            val tipWidth = 1.5.dp.toPx()

            // Upper blade facet (lit facet)
            val upperFacet = Path().apply {
                moveTo(center.x, center.y - baseHalf)
                lineTo(center.x + needleLength, center.y - tipWidth / 2f)
                lineTo(center.x + needleLength, center.y)
                lineTo(center.x, center.y)
                close()
            }
            drawPath(
                path = upperFacet,
                color = needleColor
            )

            // Lower blade facet (subtly shaded facet for 3D depth)
            val lowerFacet = Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + needleLength, center.y)
                lineTo(center.x + needleLength, center.y + tipWidth / 2f)
                lineTo(center.x, center.y + baseHalf)
                close()
            }
            drawPath(
                path = lowerFacet,
                color = needleColor.copy(alpha = 0.82f)
            )

            // Center ridge highlight
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(center.x + hubRadius * 0.5f, center.y),
                end = Offset(center.x + needleLength * 0.92f, center.y),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 3. Multi-ring Metallic Needle Hub Cap
        // Outer brushed metallic bezel
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF334155),
                    Color(0xFF1E293B),
                    Color(0xFF475569),
                    Color(0xFF0F172A),
                    Color(0xFF334155)
                ),
                center = center
            ),
            radius = hubRadius,
            center = center
        )

        // Inner recessed dark cap
        drawCircle(
            color = Color(0xFF0A0E17),
            radius = hubRadius * 0.72f,
            center = center
        )

        // Center colored accent pip
        drawCircle(
            color = needleColor,
            radius = hubRadius * 0.30f,
            center = center
        )
    }

    /**
     * Draws a curved, semi-transparent anti-reflective glass glare arc across the upper 40%
     * of the dial surface, replicating real curved automotive gauge cluster lenses.
     */
    fun DrawScope.drawGlassGlareArc(
        center: Offset,
        radius: Float
    ) {
        val glassPath = Path().apply {
            val topPeak = center.y - radius * 0.96f
            val midDrop = center.y - radius * 0.20f // Upper 40% of the dial face
            val arcEdgeX = radius * 0.88f

            // Upper curved crescent boundary
            moveTo(center.x - arcEdgeX, center.y - radius * 0.35f)
            cubicTo(
                center.x - radius * 0.55f, topPeak,
                center.x + radius * 0.55f, topPeak,
                center.x + arcEdgeX, center.y - radius * 0.35f
            )

            // Lower curved specular boundary spanning upper 40%
            cubicTo(
                center.x + radius * 0.45f, midDrop,
                center.x - radius * 0.45f, midDrop,
                center.x - arcEdgeX, center.y - radius * 0.35f
            )
            close()
        }

        drawPath(
            path = glassPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.15f),
                    Color.White.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                startY = center.y - radius,
                endY = center.y - radius * 0.20f
            )
        )
    }
}

/**
 * Complete Composable demonstration gauge integrating organic stepper-motor needle physics,
 * simulated lighting drop-shadows, and anti-reflective glass lens curvature.
 */
@Composable
fun OrganicNeedleGauge(
    valueFraction: Float,
    modifier: Modifier = Modifier,
    startAngle: Float = 150f,
    sweepAngle: Float = 240f,
    needleColor: Color = Color(0xFF00E5FF),
    trackColor: Color = Color(0xFF1E293B)
) {
    val clampedFraction = valueFraction.coerceIn(0f, 1f)
    val targetAngle = startAngle + sweepAngle * clampedFraction

    // Stepper motor physics: Critically damped spring
    val animatedAngle by rememberAnimatedNeedleAngle(
        targetAngle = targetAngle,
        dampingRatio = 0.78f,
        stiffness = 180f
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val dialRadius = minOf(size.width, size.height) / 2f - 8.dp.toPx()
            val trackStroke = 8.dp.toPx()
            val arcTopLeft = Offset(center.x - dialRadius, center.y - dialRadius)
            val arcSize = Size(dialRadius * 2f, dialRadius * 2f)

            // Background Track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = trackStroke, cap = StrokeCap.Round)
            )

            // Active Progress Track
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(needleColor.copy(alpha = 0.4f), needleColor),
                    center = center
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle * clampedFraction,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = trackStroke, cap = StrokeCap.Round)
            )

            // Needle with dynamic light source shadow
            with(CockpitNeedleRenderer) {
                drawNeedle(
                    center = center,
                    angle = animatedAngle,
                    needleLength = dialRadius - trackStroke,
                    needleColor = needleColor,
                    hubRadius = 12.dp.toPx(),
                    shadowOffset = 4.dp
                )

                // Anti-reflective curved glass glare arc across upper 40%
                drawGlassGlareArc(
                    center = center,
                    radius = dialRadius
                )
            }
        }
    }
}
