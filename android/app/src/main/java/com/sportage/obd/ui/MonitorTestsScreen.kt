package com.sportage.obd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.obd.J1979Reading
import com.sportage.obd.obd.Mode06Parser
import com.sportage.obd.obd.MonitorTestResult
import com.sportage.obd.ui.theme.AmberWarning
import com.sportage.obd.ui.theme.CriticalRed
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.TealAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen Mode 06 results view.
 *
 * Mode 06 is the only OBD mode that says *how close* a component is to failing its self-test, so the
 * layout is built around the pass window rather than the raw number: every row shows where the ECU's
 * own measurement sits between its own minimum and maximum, and how much headroom is left.
 */
@Composable
fun MonitorTestsScreen(
    results: List<MonitorTestResult>,
    updatedAt: Long,
    j1979Readings: List<J1979Reading>,
    isConnected: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var requestedAt by remember { mutableStateOf(0L) }
    val isScanning = requestedAt != 0L && updatedAt < requestedAt

    val failing = results.count { !it.passed }
    val tight = results.count { it.passed && it.margin < 0.15 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---- header ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ON-BOARD MONITOR TESTS",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "SAE J1979 MODE 06 · SELF-TEST MARGINS · J1979-2 LIVE VALUES",
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        requestedAt = System.currentTimeMillis()
                        onRefresh()
                    },
                    enabled = isConnected && !isScanning,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color(0xFF05070B),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Scanning…", color = Color(0xFF05070B), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF05070B), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Scan now", color = Color(0xFF05070B), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close monitor tests", tint = SecondaryText)
                }
            }
        }

        // ---- summary ----
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF090D14),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryStat("MONITORS", results.size.toString(), Color.White)
                SummaryStat("PASSING", (results.size - failing).toString(), TealAccent)
                SummaryStat("NEAR LIMIT", tight.toString(), AmberWarning)
                SummaryStat("FAILING", failing.toString(), CriticalRed)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (updatedAt > 0) "QUERIED " + timeOfDay(updatedAt) else "NOT QUERIED YET",
                        color = SecondaryText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (isConnected) "LINK ACTIVE" else "ADAPTER DISCONNECTED",
                        color = if (isConnected) TealAccent else AmberWarning,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionHeader("ON-BOARD MONITOR TESTS (06)") }

            if (results.isEmpty()) {
                item {
                    EmptyMonitorState(
                        title = if (isConnected) "No Mode 06 Results Yet" else "Adapter Not Connected",
                        subtitle = if (isConnected) {
                            "This vehicle may not implement Mode 06, or the ECU has not completed a " +
                                "drive cycle yet. Monitors populate once the engine has run its self-tests."
                        } else {
                            "Connect the ELM327 adapter to read the ECU's on-board monitor results."
                        }
                    )
                }
            } else {
                items(results) { result -> MonitorTestCard(result) }
                if (Mode06Parser.marginal(results).isNotEmpty()) {
                    item {
                        Text(
                            text = "Monitors above marked near limit are still passing, but the ECU's " +
                                "own measurement is close to the edge. That is usually the earliest " +
                                "warning available before a code is stored.",
                            color = SecondaryText,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            item { SectionHeader("J1979-2 (OBDonUDS) LIVE PARAMETERS") }
            if (j1979Readings.isEmpty()) {
                item {
                    EmptyMonitorState(
                        title = "J1979-2 Not Reported",
                        subtitle = "This ECU answers the classic Mode 01 PIDs only. J1979-2 " +
                            "(DID 0xF400 + PID) is standardised from model year 2021 onward."
                    )
                }
            } else {
                items(j1979Readings.chunked(2)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { reading ->
                            J1979Tile(reading, Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            item {
                Text(
                    text = "Values are reported by the ECU, not computed by the app. A monitor that " +
                        "reports FAILING with no stored code usually means the test has not completed " +
                        "yet — drive a full cycle before treating it as a fault.",
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(label, color = SecondaryText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = ElectricCyan,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun MonitorTestCard(result: MonitorTestResult) {
    val accent = when (result.severity) {
        0 -> TealAccent
        1 -> AmberWarning
        else -> CriticalRed
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0A0F17),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.monitor,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = when {
                        !result.passed -> "FAILING"
                        result.margin < 0.15 -> "NEAR LIMIT"
                        result.margin < 0.45 -> "TIGHTENING"
                        else -> "PASSING"
                    },
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            MarginBar(result, accent)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "VALUE " + formatMeasure(result.scaledValue, result.scale),
                    color = accent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "WINDOW " + formatMeasure(result.scaledMinLimit, result.scale) +
                        " – " + formatMeasure(result.scaledMaxLimit, result.scale),
                    color = SecondaryText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "MARGIN ${(result.margin * 100).toInt()}%",
                    color = SecondaryText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = result.label + " · UAS 0x%02X".format(result.unitAndScale),
                color = SecondaryText,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * The pass window drawn to scale: the marker is the ECU's reported value, the shaded band is the
 * middle half of the window. A marker sitting near either end is a monitor about to stop passing.
 */
@Composable
private fun MarginBar(result: MonitorTestResult, accent: Color) {
    val span = (result.maxLimit - result.minLimit).takeIf { it > 0.0 } ?: 1.0
    val fraction = ((result.value - result.minLimit) / span).coerceIn(0.0, 1.0).toFloat()

    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = Color(0xFF1E293B), cornerRadius = radius)
        drawRoundRect(
            color = TealAccent.copy(alpha = 0.10f),
            topLeft = Offset(size.width * 0.25f, 0f),
            size = Size(size.width * 0.5f, size.height),
            cornerRadius = radius
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.22f),
            size = Size(size.width * fraction, size.height),
            cornerRadius = radius
        )
        drawCircle(
            color = accent,
            radius = size.height / 2f * 0.85f,
            center = Offset(size.width * fraction, size.height / 2f)
        )
    }
}

@Composable
private fun EmptyMonitorState(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = SecondaryText,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun J1979Tile(reading: J1979Reading, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFF0A0F17),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(reading.pid.label, color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("DID " + reading.pid.did, color = Color(0xFF3F5A6B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    reading.formatted,
                    color = ElectricCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(3.dp))
                Text(reading.pid.unit, color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** Keeps the decimal places the ECU's UAS scale implies, so 0.98 is not shown as 1. */
private fun formatMeasure(value: Double, scale: Double): String =
    if (scale < 1.0) String.format(Locale.US, "%.2f", value) else String.format(Locale.US, "%.0f", value)

private fun timeOfDay(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))
