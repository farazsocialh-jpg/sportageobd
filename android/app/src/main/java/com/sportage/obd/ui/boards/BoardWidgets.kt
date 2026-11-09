package com.sportage.obd.ui.boards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.map.HybridLocationMap
import com.sportage.obd.ui.theme.CriticalRed
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One widget card on a board. When [editing] is true the card exposes configure,
 * reorder and remove controls, so a driver can arrange the board freely.
 * [config] supplies the user's title/unit/threshold/format overrides.
 */
@Composable
fun BoardWidget(
    type: BoardWidgetType,
    data: BoardData,
    editing: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    config: WidgetConfig = WidgetConfig(),
    onRemove: () -> Unit = {},
    onMoveLeft: () -> Unit = {},
    onMoveRight: () -> Unit = {},
    onConfigure: () -> Unit = {},
    onDuplicate: () -> Unit = {}
) {
    val resolved = remember(config) { config.normalized() }
    Surface(
        modifier = Modifier.testTag("board_widget_${type.id}").fillMaxWidth().heightIn(min = 96.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = themeWidgetShape()
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (resolved.titleOverride ?: type.title).uppercase(Locale.ENGLISH),
                    color = SecondaryText(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (editing) {
                    if (canMoveLeft) {
                        IconButton(onMoveLeft, Modifier.size(28.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Move left", tint = SecondaryText(), modifier = Modifier.size(18.dp))
                        }
                    }
                    if (canMoveRight) {
                        IconButton(onMoveRight, Modifier.size(28.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Move right", tint = SecondaryText(), modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onConfigure, Modifier.testTag("widget_configure_${type.id}").size(28.dp)) {
                        Icon(Icons.Default.Edit, "Configure ${type.title}", tint = ElectricCyan(), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onDuplicate, Modifier.testTag("widget_duplicate_${type.id}").size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, "Duplicate ${type.title}", tint = SecondaryText(), modifier = Modifier.size(15.dp))
                    }
                    IconButton(onRemove, Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Remove widget", tint = CriticalRed(), modifier = Modifier.size(18.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) { WidgetBody(type, data, resolved) }
        }
    }
}

@Composable
private fun themeWidgetShape() = RoundedCornerShape(14.dp)

/** Warning/critical colours from config thresholds with the widget's defaults as fallback. */
@Composable
private fun readingColor(
    value: Float?,
    config: WidgetConfig,
    warnAt: Float? = null,
    criticalAt: Float? = null,
    lowWarnBelow: Float? = null
): Color {
    if (value == null) return PrimaryText()
    val warn = config.warnThreshold ?: warnAt
    val critical = config.criticalThreshold ?: criticalAt
    if (critical != null && value >= critical) return CriticalRed()
    if (warn != null && value >= warn) return WarningAmber()
    if (lowWarnBelow != null && value <= lowWarnBelow) return WarningAmber()
    return PrimaryText()
}

@Composable
private fun WidgetBody(type: BoardWidgetType, data: BoardData, config: WidgetConfig) {
    when (type) {
        BoardWidgetType.SPEED -> BigValue(
            config.format(data.speed), config.unitOverride ?: data.speedUnit,
            readingColor(data.speed?.toFloat(), config, warnAt = 140f, criticalAt = 160f)
        )
        BoardWidgetType.RPM -> BigValue(
            config.format(data.rpm), config.unitOverride ?: "rpm",
            readingColor(data.rpm?.toFloat(), config, warnAt = 5000f, criticalAt = 6500f)
        )
        BoardWidgetType.COOLANT -> BigValue(
            config.format(data.coolant), config.unitOverride ?: "°C",
            readingColor(data.coolant?.toFloat(), config, warnAt = 100f, criticalAt = 110f)
        )
        BoardWidgetType.INTAKE_TEMP -> BigValue(config.format(data.intakeTemp), config.unitOverride ?: "°C")
        BoardWidgetType.FUEL -> BigValue(
            config.format(data.fuelLevel), config.unitOverride ?: "% fuel",
            readingColor(data.fuelLevel?.toFloat(), config, lowWarnBelow = 15f)
        )
        BoardWidgetType.THROTTLE -> BigValue(config.format(data.throttle), config.unitOverride ?: "% throttle")
        BoardWidgetType.LOAD -> BigValue(config.format(data.load), config.unitOverride ?: "% load")
        BoardWidgetType.VOLTAGE -> BigValue(
            config.format(data.voltage), config.unitOverride ?: "volts",
            readingColor(data.voltage, config, lowWarnBelow = 11.5f)
        )
        BoardWidgetType.GEAR -> BigValue(if (data.gear == 0) "P/N" else "D${data.gear}", config.unitOverride ?: "gear")
        BoardWidgetType.MAF -> BigValue(config.format(data.mafGs), config.unitOverride ?: "g/s air")
        BoardWidgetType.TIMING -> BigValue(config.format(data.timingAdvanceDeg), config.unitOverride ?: "°BTDC")
        BoardWidgetType.MAP -> Box(Modifier.fillMaxWidth().height(210.dp)) {
            HybridLocationMap(data.currentLat, data.currentLon, 0)
        }
        BoardWidgetType.CLOCK -> ClockWidget()
        BoardWidgetType.GPS -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BigValue("%.4f".format(data.currentLat), "latitude")
            BigValue("%.4f".format(data.currentLon), "longitude")
            data.gpsStatus?.let { Text(it, color = SecondaryText(), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        BoardWidgetType.TRIP_DISTANCE -> BigValue(config.format(data.tripDistanceKm), config.unitOverride ?: "km trip")
        BoardWidgetType.TRIP_DURATION -> BigValue(formatDuration(data.tripDurationSeconds), config.unitOverride ?: "duration")
        BoardWidgetType.CONNECTION -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Bolt, null, tint = if (data.connectionOk) ElectricCyan() else CriticalRed(), modifier = Modifier.size(20.dp))
            Text(data.connectionLabel, color = PrimaryText(), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2)
        }
        BoardWidgetType.SPEED_GRAPH -> HistoryGraph(data.speedHistory, 200, "Speed · ${config.unitOverride ?: data.speedUnit}")
        BoardWidgetType.RPM_GRAPH -> HistoryGraph(data.rpmHistory, 7000, "RPM")
    }
}

@Composable
private fun BigValue(value: String, unit: String, color: Color = PrimaryText()) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, color = color, fontSize = 34.sp, fontWeight = FontWeight.Light, maxLines = 1)
        Text(unit, color = SecondaryText(), fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
    }
}

@Composable
private fun ClockWidget() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    Column {
        Text(SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(now)),
            color = PrimaryText(), fontSize = 34.sp, fontWeight = FontWeight.Light)
        Text(SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(Date(now)), color = SecondaryText(), fontSize = 11.sp)
    }
}

@Composable
private fun HistoryGraph(history: List<Pair<Long, Int>>, maxValue: Int, label: String) {
    val lineColor = ElectricCyan()
    val shadeColor = MaterialTheme.colorScheme.background
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = SecondaryText(), fontSize = 10.sp)
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            Canvas(Modifier.fillMaxSize().background(shadeColor, RoundedCornerShape(8.dp))) {
                if (history.size < 2) return@Canvas
                val path = Path()
                history.forEachIndexed { idx, pair ->
                    val x = size.width * (idx.toFloat() / (history.size - 1))
                    val y = size.height - (size.height * (pair.second.coerceIn(0, maxValue) / maxValue.toFloat()))
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = lineColor, style = Stroke(width = 2f, cap = StrokeCap.Round))
                // Draw a leading dot so a two-point history still reads as a live trend.
                val last = history.last()
                drawCircle(lineColor, radius = 3f, center = Offset(size.width, size.height * (1f - last.second.coerceIn(0, maxValue) / maxValue.toFloat())))
            }
        }
    }
}

internal fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "%dh %02dm".format(h, m) else "%dm".format(m)
}
