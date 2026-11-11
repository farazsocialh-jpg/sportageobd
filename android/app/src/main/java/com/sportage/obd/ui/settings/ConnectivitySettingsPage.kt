package com.sportage.obd.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.ui.theme.AmberWarning
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.OnAccent
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.TealAccent
import com.sportage.obd.viewmodel.ObdViewModel
import kotlinx.coroutines.launch

/**
 * OBD & Connectivity Settings Subpage:
 * - Bluetooth adapter status & direct pairing modal launch
 * - Android system Bluetooth settings shortcut
 * - Automatic connection on app startup
 * - OBD polling toggle (Engine Off battery protection)
 * - Connection preferences (Polling rate, auto-reconnect, protocol)
 * - Adapter management (forget adapter, inspect protocol)
 */
@Composable
fun ConnectivitySettingsPage(
    isPollingPaused: Boolean,
    onTogglePausePolling: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenBluetoothDialog: () -> Unit = {},
    viewModel: ObdViewModel?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiPrefs = remember { context.getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE) }
    val connState = viewModel?.connectionState?.collectAsState()?.value ?: ConnectionState.Disconnected

    var autoConnect by remember {
        mutableStateOf(uiPrefs.getBoolean("autoConnectAdapter", true))
    }
    var autoReconnectOnDrop by remember {
        mutableStateOf(uiPrefs.getBoolean("autoReconnectOnDrop", true))
    }
    var pollingRate by remember {
        mutableStateOf(uiPrefs.getString("pollingRate", "Fast CAN (20-25 Hz)") ?: "Fast CAN (20-25 Hz)")
    }
    var forcedProtocol by remember {
        mutableStateOf(uiPrefs.getString("forcedProtocol", "Auto-Detect Protocol") ?: "Auto-Detect Protocol")
    }
    var connectionTimeout by remember {
        mutableStateOf(uiPrefs.getString("connectionTimeout", "10 seconds") ?: "10 seconds")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsTopBar(
            title = "OBD & Connectivity",
            subtitle = "Manage ELM327 Bluetooth dongles, polling rates, and protocol settings",
            onNavigateBack = onNavigateBack
        )

        // MQTT / CLOUD SYNC
        SettingsSectionHeader(title = "MQTT / Cloud Sync")
        SettingsActionItem(
            title = "MQTT / Adafruit IO Settings",
            description = "Configure broker host, credentials, vehicle ID, and phone pairing",
            buttonLabel = "Open MQTT Settings",
            icon = Icons.Default.Settings,
            onClick = {
                context.startActivity(Intent(context, com.sportage.obd.LinkPhoneActivity::class.java))
            }
        )

        // 1. BLUETOOTH ADAPTER STATUS
        SettingsSectionHeader(title = "Bluetooth Adapter Status")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (connState is ConnectionState.Connected) TealAccent().copy(alpha = 0.6f) else DarkSurfaceElevated())
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = when (connState) {
                                is ConnectionState.Connected -> "CONNECTED · ${connState.deviceName}"
                                is ConnectionState.Connecting -> "CONNECTING TO ADAPTER..."
                                is ConnectionState.Error -> "ADAPTER ERROR: ${connState.message}"
                                ConnectionState.Disconnected -> "NO ADAPTER CONNECTED"
                            },
                            color = when (connState) {
                                is ConnectionState.Connected -> TealAccent()
                                is ConnectionState.Connecting -> AmberWarning()
                                is ConnectionState.Error -> AmberWarning()
                                ConnectionState.Disconnected -> SecondaryText()
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (connState) {
                                is ConnectionState.Connected -> "Active Link: ${connState.protocol} · Address: ${connState.address}"
                                else -> "Pair with ELM327, V-LINK, or OBDII Bluetooth adapter (Classic SPP or BLE)"
                            },
                            color = SecondaryText(),
                            fontSize = 11.sp
                        )
                    }

                    Icon(
                        imageVector = if (connState is ConnectionState.Connected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (connState is ConnectionState.Connected) TealAccent() else SecondaryText(),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenBluetoothSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).widthIn(min = 128.dp).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = OnAccent(), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("System BT Settings", color = OnAccent(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (connState is ConnectionState.Connected) {
                        Button(
                            onClick = { viewModel?.disconnect() },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = AmberWarning(), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Disconnect", color = AmberWarning(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        SettingsActionItem(
            title = "In-App OBD Scanner & Terminal",
            description = "Select paired ELM327 / BLE adapter or send raw AT commands",
            buttonLabel = "Open Scanner",
            icon = Icons.Default.BluetoothConnected,
            onClick = onOpenBluetoothDialog
        )

        SettingsActionItem(
            title = "Android Bluetooth System Settings",
            description = "Pair new ELM327 adapters or view system devices",
            buttonLabel = "Open Settings",
            icon = Icons.Default.Bluetooth,
            onClick = onOpenBluetoothSettings
        )

        // 2. AUTOMATIC CONNECTION
        SettingsSectionHeader(title = "Automatic Connection")

        SettingsSwitchItem(
            title = "Auto-Connect on App Launch",
            description = "Automatically opens RFCOMM / BLE connection to the last paired adapter on boot",
            checked = autoConnect,
            onCheckedChange = { enabled ->
                autoConnect = enabled
                uiPrefs.edit().putBoolean("autoConnectAdapter", enabled).apply()
            },
            icon = Icons.Default.Sync
        )

        SettingsSwitchItem(
            title = "Auto-Reconnect on Signal Loss",
            description = "Periodically attempts reconnection if the vehicle ignition powers cycle or dongle reboots",
            checked = autoReconnectOnDrop,
            onCheckedChange = { enabled ->
                autoReconnectOnDrop = enabled
                uiPrefs.edit().putBoolean("autoReconnectOnDrop", enabled).apply()
            },
            icon = Icons.Default.Refresh
        )

        // 3. OBD POLLING
        SettingsSectionHeader(title = "OBD Polling & Power Management")

        SettingsSwitchItem(
            title = "Pause OBD Polling (Engine Off)",
            description = "Halts continuous PID requests when ignition is turned off to prevent car battery drain",
            checked = isPollingPaused,
            onCheckedChange = { onTogglePausePolling() },
            activeColor = AmberWarning(),
            icon = Icons.Default.PowerSettingsNew
        )

        val pollingRates = listOf(
            "Fast CAN (20-25 Hz)",
            "Standard (10-15 Hz)",
            "Battery Saver (4-6 Hz)"
        )
        SettingsValueItem(
            title = "OBD PID Query Frequency",
            valueText = pollingRate,
            description = "Controls how rapidly engine speed, RPM, and live sensors are queried over the bus",
            icon = Icons.Default.Speed,
            onClick = {
                val currentIdx = pollingRates.indexOf(pollingRate)
                val next = pollingRates[(currentIdx + 1) % pollingRates.size]
                pollingRate = next
                uiPrefs.edit().putString("pollingRate", next).apply()
            }
        )

        // 4. CONNECTION PREFERENCES
        SettingsSectionHeader(title = "Connection Preferences")

        val protocols = listOf(
            "Auto-Detect Protocol",
            "ISO 15765-4 CAN (11-bit, 500k)",
            "ISO 15765-4 CAN (29-bit, 500k)",
            "ISO 9141-2 / KWP2000"
        )
        SettingsValueItem(
            title = "ELM327 Protocol Selection",
            valueText = forcedProtocol,
            description = "Kia Sportage Nu 2.0 MPI uses standard high-speed 11-bit CAN (Auto-Detect recommended)",
            onClick = {
                val currentIdx = protocols.indexOf(forcedProtocol)
                val next = protocols[(currentIdx + 1) % protocols.size]
                forcedProtocol = next
                uiPrefs.edit().putString("forcedProtocol", next).apply()
                val atCmd = when (next) {
                    "ISO 15765-4 CAN (11-bit, 500k)" -> "ATSP6"
                    "ISO 15765-4 CAN (29-bit, 500k)" -> "ATSP7"
                    "ISO 9141-2 / KWP2000" -> "ATSP3"
                    else -> "ATSP0"
                }
                if (viewModel?.isConnected() == true) {
                    scope.launch {
                        runCatching { viewModel.sendRawCommand(atCmd) }
                    }
                }
            }
        )

        val timeouts = listOf("5 seconds", "10 seconds", "15 seconds", "25 seconds")
        SettingsValueItem(
            title = "Handshake Timeout",
            valueText = connectionTimeout,
            description = "Maximum duration to wait for ELM327 AT response before reporting error",
            icon = Icons.Default.HourglassBottom,
            onClick = {
                val currentIdx = timeouts.indexOf(connectionTimeout)
                val next = timeouts[(currentIdx + 1) % timeouts.size]
                connectionTimeout = next
                uiPrefs.edit().putString("connectionTimeout", next).apply()
            }
        )

        // 5. ADAPTER MANAGEMENT
        SettingsSectionHeader(title = "Adapter Management")

        SettingsActionItem(
            title = "Clear Stored Adapter Preference",
            description = "Clears the remembered Bluetooth MAC address so the app will prompt for device selection",
            buttonLabel = "Clear Stored Adapter",
            icon = Icons.Default.Delete,
            isDestructive = true,
            onClick = {
                viewModel?.forgetSavedAdapter()
                uiPrefs.edit().remove("last_connected_device_address").remove("last_connected_device_name").apply()
                android.widget.Toast.makeText(context, "Stored OBD adapter cleared", android.widget.Toast.LENGTH_SHORT).show()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
