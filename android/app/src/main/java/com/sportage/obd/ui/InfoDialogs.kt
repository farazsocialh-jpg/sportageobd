package com.sportage.obd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText

/**
 * The privacy policy shown in-app.
 *
 * The text is not hard-coded: PRIVACY.md at the repository root is copied into assets at build time
 * (see app/build.gradle.kts, task `copyPrivacyPolicy`), so the policy the driver reads and the
 * policy on the public URL can never diverge.
 */
@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var policy by remember { mutableStateOf("Loading privacy policy…") }

    LaunchedEffect(Unit) {
        policy = runCatching {
            context.assets.open("privacy_policy.md").bufferedReader().use { it.readText() }
        }.getOrElse {
            "The bundled privacy policy could not be read. The published copy is at " +
                "https://github.com/farazsocialh-jpg/sportageobd/blob/main/PRIVACY.md"
        }
    }

    InfoDialogShell(
        title = "PRIVACY POLICY",
        onDismiss = onDismiss,
        body = { ScrollableBody(policy) }
    )
}

/** Concise operational help, mirroring docs/USER_GUIDE.md. */
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    val text = """
        GETTING CONNECTED
        • Plug the ELM327 adapter into the OBD-II port, ignition ON.
        • Pair it once in Android Bluetooth settings (PIN 1234, sometimes 0000).
        • Tap the Bluetooth icon, pick the adapter, wait for "LINK ACTIVE".
        • If the socket is refused, the app retries with insecure RFCOMM and then the
          legacy channel-1 reflection path automatically.

        NO ADAPTER?
        Settings → Bench Test / Demo Simulation drives every gauge with simulated data.

        GAUGES
        Speed and RPM poll continuously; coolant, intake, load, throttle and fuel rotate.
        The sampling rate (Hz) is measured, not assumed.

        DIAGNOSTICS
        Scan reads stored (Mode 03), pending (Mode 07) and permanent (Mode 0A) codes, plus
        freeze frame, readiness monitors and — where supported — Mode 06 monitor test margins.
        Clearing codes also resets readiness monitors, so do not clear right before an
        emissions test.

        HEALTH SCORE
        A bounded 0–100 estimate from battery voltage, coolant, fuel trims, stored codes and
        readiness. Every deduction is listed. It is guidance, not a diagnosis.

        TRIPS
        A trip records automatically while the adapter is connected: distance, duration, max
        and average speed, 0–100 km/h time, fuel used (if the car reports it) and an eco score.
        Trip history exports to CSV from the Trip page.

        LAUNCHER
        Set as default home in Settings. The app drawer lists everything installed, so the
        driver can never get trapped in the cockpit.

        TROUBLESHOOTING
        • "Permission denied" — grant Bluetooth and location in Android app settings.
        • No telemetry — check the adapter LED and that polling is not paused.
        • Adapter drains the battery when parked — turn on Pause OBD Polling.
    """.trimIndent()

    InfoDialogShell(
        title = "HELP & QUICK START",
        onDismiss = onDismiss,
        body = { ScrollableBody(text) }
    )
}

@Composable
private fun InfoDialogShell(
    title: String,
    onDismiss: () -> Unit,
    body: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0E1722)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = SecondaryText, fontFamily = FontFamily.Monospace)
                    }
                }
                body()
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                ) {
                    Text(
                        "Done",
                        color = Color(0xFF05070B),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrollableBody(text: String) {
    Text(
        text = text,
        color = PrimaryText,
        fontSize = 11.sp,
        lineHeight = 17.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
    )
}
