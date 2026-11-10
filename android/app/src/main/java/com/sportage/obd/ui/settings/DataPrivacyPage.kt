package com.sportage.obd.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sportage.obd.ui.theme.TealAccent
import com.sportage.obd.viewmodel.ObdViewModel

/**
 * Data & Privacy Settings Subpage:
 * - CSV telemetry sensor logging toggle
 * - Auto-start logging on adapter connection
 * - Export and share live CSV drive log
 * - Privacy Policy viewer
 * - Local offline data guarantee
 */
@Composable
fun DataPrivacyPage(
    viewModel: ObdViewModel?,
    onOpenPrivacyPolicy: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val csvLogging = viewModel?.isCsvLogging?.collectAsState()?.value ?: false
    var csvAuto by remember { mutableStateOf(viewModel?.csvAutostart() ?: false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsTopBar(
            title = "Data & Privacy",
            subtitle = "Manage drive telemetry logging, export records, and inspect data safety",
            onNavigateBack = onNavigateBack
        )

        // 1. CSV TELEMETRY LOGGING
        SettingsSectionHeader(title = "CSV Drive Telemetry Logging")

        SettingsSwitchItem(
            title = "CSV Sensor Telemetry Logging",
            description = "Record RPM, speed, coolant, throttle, battery, and O2 readings to a local CSV file",
            checked = csvLogging,
            onCheckedChange = { enable ->
                if (enable) viewModel?.startCsvLogging() else viewModel?.stopCsvLogging()
            },
            activeColor = TealAccent(),
            icon = Icons.Default.Storage
        )

        SettingsSwitchItem(
            title = "Auto-Start With OBD Connection",
            description = "Automatically opens a new drive log file as soon as the Bluetooth dongle connects",
            checked = csvAuto,
            onCheckedChange = { enable ->
                csvAuto = enable
                viewModel?.setCsvAutostart(enable)
            },
            activeColor = TealAccent(),
            icon = Icons.Default.Sync
        )

        // 2. EXPORT & SHARE LOGS
        SettingsSectionHeader(title = "Export & Share Drive Data")

        SettingsActionItem(
            title = "Share Active CSV Log",
            description = "Export raw sensor telemetry log to Google Drive, email, or USB storage",
            buttonLabel = "Share Log",
            icon = Icons.Default.Share,
            onClick = {
                val file = viewModel?.csvLogFile()
                if (file != null && file.exists()) {
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".fileprovider",
                            file
                        )
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Share OBD Log").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not share log: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "No active log file. Start logging first.", Toast.LENGTH_SHORT).show()
                }
            }
        )

        SettingsActionItem(
            title = "Export Diagnostic Inspection Report",
            description = "Generates text summary of detected DTCs, freeze frames, and Mode \$06 test results",
            buttonLabel = "Export Report",
            icon = Icons.Default.Description,
            onClick = {
                val report = viewModel?.buildDiagnosticReport() ?: "No diagnostic data available."
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, report)
                    putExtra(Intent.EXTRA_SUBJECT, "Kia Sportage OBD Diagnostic Report")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(send, "Share Diagnostic Report").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        )

        // 3. PRIVACY PRACTICES & POLICY
        SettingsSectionHeader(title = "Privacy & Data Practices")

        SettingsActionItem(
            title = "View Privacy Policy",
            description = "Detailed statement of zero personal tracking, local storage, and GDPR / CCPA compliance",
            buttonLabel = "Open Policy",
            icon = Icons.Default.PrivacyTip,
            onClick = onOpenPrivacyPolicy
        )

        SettingsValueItem(
            title = "Telemetry Storage Mode",
            valueText = "100% Offline Local Device",
            description = "No location coordinates or engine diagnostic data are transmitted to cloud servers without user intent",
            icon = Icons.Default.PrivacyTip
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
