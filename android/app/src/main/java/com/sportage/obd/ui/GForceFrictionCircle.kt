package com.sportage.obd.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import kotlin.math.hypot
import kotlin.math.max

/**
 * Single historical G-sample for the phosphorescent heat trail.
 */
data class GPoint(
    val latG: Float,
    val lonG: Float
)

/**
 * Real-Time G-Force "Friction Circle" (G-G Diagram) Widget.
 *
 * Visualizes dynamic chassis kinematics (braking, acceleration, cornering grip):
 * - Uses device IMU sensor listeners (`Sensor.TYPE_LINEAR_ACCELERATION` with fallback to `Sensor.TYPE_ACCELEROMETER`).
 * - Displays 0.25G, 0.50G, 0.75G, and 1.00G concentric adherence rings.
 * - Glowing focus pip with decaying 20-frame phosphorescent heat trail.
 * - Real-time lateral/longitudinal numerical gauges and peak combined G memory.
 */
@Composable
fun GForceFrictionCircle(
    modifier: Modifier = Modifier,
    maxScaleG: Float = 1.0f
) {
    val context = LocalContext.current
    var currentLatG by remember { mutableFloatStateOf(0f) }
    var currentLonG by remember { mutableFloatStateOf(0f) }
    var peakCombinedG by remember { mutableFloatStateOf(0f) }

    // 20-frame phosphorescent heat trail ring buffer
    val trail = remember { mutableStateListOf<GPoint>() }

    // Lifecycle-aware IMU Sensor Registration
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val linearAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                // Convert m/s^2 to G (1G = 9.80665 m/s^2)
                val standardGravity = SensorManager.GRAVITY_EARTH
                val rawLat = (event.values[0] / standardGravity).coerceIn(-1.5f, 1.5f)
                val rawLon = (event.values[1] / standardGravity).coerceIn(-1.5f, 1.5f)

                // Low-pass filter (alpha = 0.25) to smooth road chatter
                currentLatG = (currentLatG * 0.75f) + (rawLat * 0.25f)
                currentLonG = (currentLonG * 0.75f) + (rawLon * 0.25f)

                val currentCombined = hypot(currentLatG, currentLonG)
                if (currentCombined > peakCombinedG) {
                    peakCombinedG = currentCombined
                }

                // Append to 20-frame decay trail
                trail.add(GPoint(currentLatG, currentLonG))
                if (trail.size > 20) {
                    trail.removeAt(0)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        linearAccel?.let {
            sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    Surface(
        color = DarkSurface(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Title & Peak G Tracker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "G-FORCE FRICTION CIRCLE",
                        color = PrimaryText(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Real-time Chassis Adherence",
                        color = SecondaryText(),
                        fontSize = 10.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "PEAK: ${"%.2f".format(peakCombinedG)}G",
                        color = Color(0xFFFFB300),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )

                    OutlinedButton(
                        onClick = { peakCombinedG = 0f },
                        modifier = Modifier.height(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryText())
                    ) {
                        Text("Reset", fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Canvas G-G Diagram
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                val circleSize = minOf(maxWidth, maxHeight)

                Box(
                    modifier = Modifier.size(circleSize),
                    contentAlignment = Alignment.Center
                ) {
                    // Resolve theme colors outside the draw scope: composables cannot run there.
                    val trailColor = ElectricCyan()
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = (size.width / 2f) - 12.dp.toPx()

                        // 1. Concentric G Rings (0.25G, 0.50G, 0.75G, 1.00G)
                        val rings = listOf(0.25f, 0.50f, 0.75f, 1.00f)
                        for (ringG in rings) {
                            val r = maxRadius * (ringG / maxScaleG)
                            drawCircle(
                                color = if (ringG == 1.0f) Color(0xFF334155) else Color(0xFF1E293B),
                                radius = r,
                                center = center,
                                style = Stroke(
                                    width = if (ringG == 1.0f) 1.5.dp.toPx() else 1.dp.toPx(),
                                    pathEffect = if (ringG < 1.0f) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
                                )
                            )
                        }

                        // 2. Crosshairs (Braking / Accel, Left / Right)
                        drawLine(
                            color = Color(0xFF1E293B),
                            start = Offset(center.x, center.y - maxRadius),
                            end = Offset(center.x, center.y + maxRadius),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color(0xFF1E293B),
                            start = Offset(center.x - maxRadius, center.y),
                            end = Offset(center.x + maxRadius, center.y),
                            strokeWidth = 1.dp.toPx()
                        )

                        // 3. Phosphorescent Decaying Heat Trail (20 frames)
                        val trailSize = trail.size
                        for (i in 0 until trailSize) {
                            val pt = trail[i]
                            val alpha = ((i + 1).toFloat() / trailSize) * 0.70f
                            val trailRadius = 3.dp.toPx() * ((i + 1).toFloat() / trailSize)

                            val ptX = center.x + (pt.latG / maxScaleG * maxRadius).coerceIn(-maxRadius, maxRadius)
                            // Positive longitudinal G (accel) goes down, negative (braking) goes up
                            val ptY = center.y - (pt.lonG / maxScaleG * maxRadius).coerceIn(-maxRadius, maxRadius)

                            drawCircle(
                                color = trailColor.copy(alpha = alpha),
                                radius = trailRadius,
                                center = Offset(ptX, ptY)
                            )
                        }

                        // 4. Instantaneous Glowing Focus Ball
                        val focusX = center.x + (currentLatG / maxScaleG * maxRadius).coerceIn(-maxRadius, maxRadius)
                        val focusY = center.y - (currentLonG / maxScaleG * maxRadius).coerceIn(-maxRadius, maxRadius)
                        val focusOffset = Offset(focusX, focusY)

                        // Outer Radial Glow Halo
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    trailColor.copy(alpha = 0.60f),
                                    Color.Transparent
                                ),
                                center = focusOffset,
                                radius = 14.dp.toPx()
                            ),
                            radius = 14.dp.toPx(),
                            center = focusOffset
                        )

                        // Core Pip
                        drawCircle(
                            color = Color.White,
                            radius = 4.5.dp.toPx(),
                            center = focusOffset
                        )
                    }

                    // Directional HUD Labels
                    Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        Text("BRAKE", color = Color(0xFF64748B), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter))
                        Text("ACCEL", color = Color(0xFF64748B), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter))
                        Text("L", color = Color(0xFF64748B), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart))
                        Text("R", color = Color(0xFF64748B), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterEnd))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Real-Time Numerical Readings (Tabular Numeric Format)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GReadoutTile(
                    label = "LATERAL",
                    value = "${if (currentLatG >= 0) "+" else ""}${"%.2f".format(currentLatG)}G"
                )
                GReadoutTile(
                    label = "LONGITUDINAL",
                    value = "${if (currentLonG >= 0) "+" else ""}${"%.2f".format(currentLonG)}G"
                )
                GReadoutTile(
                    label = "COMBINED",
                    value = "${"%.2f".format(hypot(currentLatG, currentLonG))}G"
                )
            }
        }
    }
}

@Composable
private fun GReadoutTile(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = SecondaryText(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = PrimaryText(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}
