package com.sportage.obd.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.DarkBackground
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.TealAccent

/**
 * First-run wizard.
 *
 * Deliberately four short screens, not a carousel with hidden content: a driver installs this in a
 * car park, and anything longer gets skipped. Each step has exactly one idea, and the privacy
 * decision is an explicit, off-by-default switch rather than a pre-ticked box.
 */
@Composable
fun OnboardingScreen(
    onFinish: (diagnosticsEnabled: Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var diagnosticsEnabled by remember { mutableStateOf(false) }
    val lastStep = 3

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        StepDots(step = step, total = lastStep + 1)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                0 -> OnboardingPage(
                    icon = Icons.Default.DirectionsCar,
                    eyebrow = "WELCOME",
                    title = "Your dashboard and your launcher, in one app",
                    body = "SportageOBD turns the screen into a car cockpit: live RPM, speed, " +
                        "temperatures and fault codes from your OBD-II adapter. The same app is " +
                        "also a home-screen launcher, so the car boots straight into the gauges " +
                        "instead of a phone home screen.\n\nYou do not need the launcher to use " +
                        "the diagnostics, and you do not need an adapter to use the launcher."
                )

                1 -> OnboardingPage(
                    icon = Icons.Default.Bluetooth,
                    eyebrow = "STEP 1 · ADAPTER",
                    title = "Pair your ELM327 adapter",
                    body = "1. Plug the adapter into the car's OBD-II port (under the dash, driver's " +
                        "side).\n2. Turn the ignition to ON — the adapter's LED should light up.\n" +
                        "3. Open Android Bluetooth settings and pair it. The usual PIN is 1234, " +
                        "sometimes 0000.\n4. Come back here and pick the adapter in the Bluetooth " +
                        "dialog.\n\nNo adapter yet? Turn on Bench Demo in Settings to explore every " +
                        "screen with simulated data."
                )

                2 -> OnboardingPage(
                    icon = Icons.Default.Shield,
                    eyebrow = "STEP 2 · PERMISSIONS",
                    title = "What the app asks for, and why",
                    body = "Bluetooth — to talk to the adapter.\n" +
                        "Location — for GPS speed and the map pane. The car's own speed PID is " +
                        "used whenever it is available.\n" +
                        "Notifications — to show the ongoing connection status.\n" +
                        "Installed apps — so the launcher's app drawer can list them.\n\n" +
                        "Nothing here is used for advertising, and the app works offline.",
                    action = {
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "Grant permissions",
                                color = Color(0xFF05070B),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                )

                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OnboardingPage(
                        icon = Icons.Default.Shield,
                        eyebrow = "STEP 3 · PRIVACY",
                        title = "Your data stays in the car",
                        body = "Vehicle data, trip history and fault scans are stored on this " +
                            "device. There is no account and no vendor server.\n\n" +
                            "Anonymous diagnostics (crash reports and a few usage events) are " +
                            "optional and off unless you turn them on. They never include your " +
                            "VIN, location or adapter details, and you can switch them off again " +
                            "at any time in Settings."
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF090D14),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Share anonymous diagnostics",
                                    color = PrimaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    "Optional. Off by default.",
                                    color = SecondaryText,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Switch(
                                checked = diagnosticsEnabled,
                                onCheckedChange = { diagnosticsEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = TealAccent,
                                    checkedTrackColor = TealAccent.copy(alpha = 0.4f)
                                )
                            )
                        }
                    }

                    TextButton(onClick = onOpenPrivacyPolicy) {
                        Text(
                            "Read the full privacy policy",
                            color = ElectricCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                TextButton(onClick = { step-- }) {
                    Text("Back", color = SecondaryText, fontFamily = FontFamily.Monospace)
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            Button(
                onClick = {
                    if (step < lastStep) step++ else onFinish(diagnosticsEnabled)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(
                    imageVector = if (step < lastStep) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF05070B),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (step < lastStep) "Continue" else "Start driving",
                    color = Color(0xFF05070B),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    icon: ImageVector,
    eyebrow: String,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(44.dp))
        Text(
            text = eyebrow,
            color = TealAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = title,
            color = PrimaryText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            color = SecondaryText,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        action?.invoke()
    }
}

@Composable
private fun StepDots(step: Int, total: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .height(3.dp)
                    .width(if (index == step) 28.dp else 14.dp)
                    .background(
                        if (index == step) ElectricCyan else Color(0xFF1E293B),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}
