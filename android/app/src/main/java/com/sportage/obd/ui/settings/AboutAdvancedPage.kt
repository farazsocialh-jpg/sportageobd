package com.sportage.obd.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.CriticalRed
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText

/**
 * About & Advanced Settings Subpage:
 * - Bench test / Demo simulation mode
 * - Developer diagnostics & raw ELM327 tools
 * - Application metadata (Package ID, architecture, build tag)
 * - Version & platform information (targetSdk 35 edge-to-edge)
 * - Reset all preferences with safety dialog
 */
@Composable
fun AboutAdvancedPage(
    isDemoMode: Boolean,
    onToggleDemoMode: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onResetPreferences: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsTopBar(
            title = "About & Advanced",
            subtitle = "Simulation testing, developer tools, app specs, and factory reset",
            onNavigateBack = onNavigateBack
        )

        // 1. BENCH TEST & DEMO SIMULATION
        SettingsSectionHeader(title = "Simulation & Bench Testing")

        SettingsSwitchItem(
            title = "Bench Test / Demo Simulation",
            description = "Simulates real-time RPM, vehicle speed, coolant temp, and fuel level without a physical car",
            checked = isDemoMode,
            onCheckedChange = onToggleDemoMode,
            icon = Icons.Default.Science
        )

        // 2. DEVELOPER TOOLS
        SettingsSectionHeader(title = "Developer Tools")

        SettingsValueItem(
            title = "OBD-II Command Parser",
            valueText = "ELM327 v1.5 / v2.1 Compatible",
            description = "Supports standard Mode \$01, Mode \$02, Mode \$03, Mode \$06, and Mode \$09 PIDs",
            icon = Icons.Default.Terminal
        )

        SettingsValueItem(
            title = "ISO-TP Multi-Frame Engine",
            valueText = "Active (Flow Control 00)",
            description = "Assembles 4KB+ VIN and calibration data over CAN bus framing",
            icon = Icons.Default.Build
        )

        // 3. APPLICATION INFORMATION
        SettingsSectionHeader(title = "Application Information")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkSurfaceElevated())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Application Name", color = SecondaryText(), fontSize = 12.sp)
                    Text("Sportage OBD Launcher", color = PrimaryText(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Package Identifier", color = SecondaryText(), fontSize = 12.sp)
                    Text("com.sportage.obd", color = PrimaryText(), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Engine Profile", color = SecondaryText(), fontSize = 12.sp)
                    Text("Kia Nu 2.0L MPI Gasoline", color = ElectricCyan(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Release Build Tag", color = SecondaryText(), fontSize = 12.sp)
                    Text("2026-11-09.r", color = PrimaryText(), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // 4. VERSION & PLATFORM
        SettingsSectionHeader(title = "Version & Runtime")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkSurfaceElevated())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("App Version", color = SecondaryText(), fontSize = 12.sp)
                    Text("1.2.0 (Build 20261109)", color = PrimaryText(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Android Target SDK", color = SecondaryText(), fontSize = 12.sp)
                    Text("API 35 (Android 15 Edge-to-Edge)", color = PrimaryText(), fontSize = 12.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("UI Framework", color = SecondaryText(), fontSize = 12.sp)
                    Text("Jetpack Compose & Material 3", color = PrimaryText(), fontSize = 12.sp)
                }
            }
        }

        // 5. RESET PREFERENCES
        SettingsSectionHeader(title = "Reset Preferences")

        SettingsActionItem(
            title = "Reset All Preferences to Defaults",
            description = "Restores all UI themes, alert limits, layout slots, and unit options to original state",
            buttonLabel = "Reset All",
            icon = Icons.Default.RestartAlt,
            isDestructive = true,
            onClick = { showResetDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text("Reset All Preferences?", color = PrimaryText(), fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will restore all visual themes, speed warning limits, widget layouts, and connection settings back to factory defaults.",
                    color = SecondaryText(),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val prefs = context.getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()
                        val fuel = context.getSharedPreferences("sportage_fuel_settings", Context.MODE_PRIVATE)
                        fuel.edit().clear().apply()
                        showResetDialog = false
                        onResetPreferences?.invoke()
                        Toast.makeText(context, "Preferences reset to factory defaults", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRed())
                ) {
                    Text("Reset", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = SecondaryText())
                }
            },
            containerColor = DarkSurface(),
            shape = RoundedCornerShape(12.dp)
        )
    }
}
