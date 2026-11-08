package com.sportage.obd.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.ui.theme.*

data class TerminalLog(
    val direction: String, // "TX", "RX", "INFO", "ERR"
    val text: String,
    val time: String
)

@SuppressLint("MissingPermission")
@Composable
fun BluetoothDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    connectionState: ConnectionState,
    pairedDevices: List<BluetoothDevice>,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshDevices: () -> Unit,
    onSendCommand: (String) -> Unit,
    terminalLogs: List<TerminalLog>,
    isDemoMode: Boolean,
    onToggleDemoMode: (Boolean) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Devices, 1: Terminal, 2: Help
    var commandInput by remember { mutableStateOf("") }

    val discoveredDevices = remember { mutableStateListOf<BluetoothDevice>() }
    var isScanningNearby by remember { mutableStateOf(false) }

    fun startNearbyScan() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) {
                discoveredDevices.clear()
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
                adapter.startDiscovery()
                isScanningNearby = true
            }
        } catch (_: Exception) {}
    }

    DisposableEffect(isOpen) {
        if (!isOpen) return@DisposableEffect onDispose {}

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (device != null && !device.name.isNullOrBlank()) {
                            val addr = device.address
                            if (discoveredDevices.none { it.address == addr } && pairedDevices.none { it.address == addr }) {
                                discoveredDevices.add(device)
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        isScanningNearby = true
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        isScanningNearby = false
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)

        // Trigger discovery automatically if paired devices is empty or error occurred
        if (pairedDevices.isEmpty() || connectionState is ConnectionState.Error) {
            startNearbyScan()
        }

        onDispose {
            try {
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            color = Color(0xFF0A0F17),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = TealAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = TealAccent,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "BLUETOOTH OBD2 INTERFACE",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = when (connectionState) {
                                    is ConnectionState.Connected -> "ACTIVE: ${(connectionState as ConnectionState.Connected).deviceName} (${(connectionState as ConnectionState.Connected).address})"
                                    is ConnectionState.Connecting -> "ESTABLISHING RFCOMM SERIAL LINK..."
                                    is ConnectionState.Error -> "ERROR: ${(connectionState as ConnectionState.Error).message}"
                                    else -> if (isDemoMode) "DEMO BENCH TESTING ACTIVE" else "NOT CONNECTED · SELECT ADAPTER"
                                },
                                color = when (connectionState) {
                                    is ConnectionState.Connected -> TealAccent
                                    is ConnectionState.Connecting -> ElectricCyan
                                    is ConnectionState.Error -> CriticalRed
                                    else -> SecondaryText
                                },
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Segmented Tabs: DEVICES | TERMINAL | TROUBLESHOOTING
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF06090F), RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("PAIRED ADAPTERS", "RAW TERMINAL", "TROUBLESHOOTING").forEachIndexed { index, tabTitle ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = index },
                            color = if (selectedTab == index) ElectricCyan else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = tabTitle,
                                color = if (selectedTab == index) Color(0xFF05070B) else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .padding(vertical = 6.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // TAB 0: DEVICES & DISCOVERY
                if (selectedTab == 0) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Error banner if connection failed
                        if (connectionState is ConnectionState.Error) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                color = CriticalRed.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CriticalRed.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CriticalRed, modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ADAPTER CONNECTION FAILED",
                                            color = CriticalRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = (connectionState as ConnectionState.Error).message + " · Select an adapter below to pair and connect.",
                                            color = Color.LightGray,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Button(
                                        onClick = { startNearbyScan() },
                                        colors = ButtonDefaults.buttonColors(containerColor = CriticalRed),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Scan", fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }

                        // Toolbar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DEVICES: ${pairedDevices.size} PAIRED · ${discoveredDevices.size} DISCOVERED",
                                color = SecondaryText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { startNearbyScan() },
                                    enabled = !isScanningNearby,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    if (isScanningNearby) {
                                        CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Scanning...", fontSize = 10.sp, color = ElectricCyan, fontFamily = FontFamily.Monospace)
                                    } else {
                                        Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(12.dp), tint = ElectricCyan)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Scan Nearby", fontSize = 10.sp, color = ElectricCyan, fontFamily = FontFamily.Monospace)
                                    }
                                }

                                Button(
                                    onClick = onRefreshDevices,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.LightGray)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Refresh", fontSize = 10.sp, color = Color.LightGray, fontFamily = FontFamily.Monospace)
                                }

                                Button(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            })
                                        } catch (_: Exception) {}
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("System Settings", fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Section: Paired Devices
                            if (pairedDevices.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "PAIRED BLUETOOTH ADAPTERS",
                                        color = SecondaryText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                items(pairedDevices) { device ->
                                    val devName = device.name ?: "Unknown Bluetooth Device"
                                    val isObd = devName.lowercase().contains("obd") ||
                                            devName.lowercase().contains("elm") ||
                                            devName.lowercase().contains("vgate") ||
                                            devName.lowercase().contains("carista")

                                    val isCurrentConnected = connectionState is ConnectionState.Connected &&
                                            (connectionState as ConnectionState.Connected).address == device.address

                                    Surface(
                                        color = if (isCurrentConnected) Color(0xFF0E2822) else Color(0xFF0E1520),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCurrentConnected) TealAccent else if (isObd) ElectricCyan.copy(alpha = 0.5f) else Color(0xFF1E293B)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    color = if (isCurrentConnected) TealAccent.copy(alpha = 0.2f) else Color(0xFF1E293B),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DirectionsCar,
                                                        contentDescription = null,
                                                        tint = if (isCurrentConnected) TealAccent else if (isObd) ElectricCyan else Color.Gray,
                                                        modifier = Modifier
                                                            .padding(8.dp)
                                                            .size(20.dp)
                                                    )
                                                }

                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text(
                                                            text = devName,
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        if (isObd) {
                                                            Text(
                                                                text = "ELM327 MATCH",
                                                                color = ElectricCyan,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = FontFamily.Monospace,
                                                                modifier = Modifier
                                                                    .background(ElectricCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                                )
                                                        }
                                                    }
                                                    Text(
                                                        text = "MAC: ${device.address}",
                                                        color = SecondaryText,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }

                                            if (isCurrentConnected) {
                                                Button(
                                                    onClick = onDisconnect,
                                                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRed),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Disconnect", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                                }
                                            } else {
                                                Button(
                                                    onClick = { onConnectDevice(device) },
                                                    enabled = connectionState !is ConnectionState.Connecting,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isObd) ElectricCyan else Color(0xFF1E293B)
                                                    ),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = if (connectionState is ConnectionState.Connecting) "Connecting..." else "Connect",
                                                        color = if (isObd) Color(0xFF05070B) else Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Section: Discovered Nearby Devices
                            if (discoveredDevices.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "NEARBY DISCOVERED ADAPTERS (TAP TO PAIR & CONNECT)",
                                        color = ElectricCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                items(discoveredDevices) { device ->
                                    val devName = device.name ?: "Nearby Bluetooth Device"
                                    val isObd = devName.lowercase().contains("obd") ||
                                            devName.lowercase().contains("elm") ||
                                            devName.lowercase().contains("vgate") ||
                                            devName.lowercase().contains("carista")

                                    Surface(
                                        color = Color(0xFF101826),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, if (isObd) ElectricCyan.copy(alpha = 0.6f) else Color(0xFF1E293B))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    color = Color(0xFF1E293B),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.BluetoothSearching,
                                                        contentDescription = null,
                                                        tint = if (isObd) ElectricCyan else Color.LightGray,
                                                        modifier = Modifier
                                                            .padding(8.dp)
                                                            .size(20.dp)
                                                    )
                                                }

                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text(
                                                            text = devName,
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        Text(
                                                            text = "NEARBY",
                                                            color = AmberWarning,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace,
                                                            modifier = Modifier
                                                                .background(AmberWarning.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "MAC: ${device.address}",
                                                        color = SecondaryText,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    try {
                                                        device.createBond()
                                                    } catch (_: Exception) {}
                                                    onConnectDevice(device)
                                                },
                                                enabled = connectionState !is ConnectionState.Connecting,
                                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Pair & Connect",
                                                    color = Color(0xFF05070B),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Empty state if both are empty
                            if (pairedDevices.isEmpty() && discoveredDevices.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0E1520), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            if (isScanningNearby) {
                                                CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(36.dp))
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text("Scanning for Nearby Bluetooth Adapters...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                                Text("Ensure adapter is plugged into OBD2 port with vehicle ignition ON.", color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                            } else {
                                                Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("No OBD2 Adapters Detected", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Plug ELM327 into the vehicle's OBD2 port and tap Scan Nearby.", color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Button(
                                                    onClick = { startNearbyScan() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Scan for Adapters", color = Color(0xFF05070B), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Action Row
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0E1520), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Bench / Demo Simulation:", color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Switch(
                                    checked = isDemoMode,
                                    onCheckedChange = onToggleDemoMode,
                                    colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan, checkedTrackColor = ElectricCyan.copy(alpha = 0.4f))
                                )
                            }

                            if (connectionState is ConnectionState.Connected) {
                                Text(
                                    text = "ISO 15765-4 CAN 11/500K · 10Hz POLLING",
                                    color = TealAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // TAB 1: RAW TERMINAL
                if (selectedTab == 1) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LIVE ELM327 AT / OBD COMMAND CONSOLE",
                            color = SecondaryText,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick commands row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("ATZ", "ATRV", "0100", "010C", "010D", "03").forEach { cmd ->
                                Surface(
                                    modifier = Modifier.clickable { onSendCommand(cmd) },
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = cmd,
                                        color = ElectricCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Terminal Log Output Box
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            color = Color(0xFF05070B),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                reverseLayout = true
                            ) {
                                items(terminalLogs.reversed()) { log ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = log.time,
                                            color = Color.DarkGray,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = log.direction,
                                            color = when (log.direction) {
                                                "TX" -> ElectricCyan
                                                "RX" -> TealAccent
                                                "ERR" -> CriticalRed
                                                else -> Color.Gray
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = log.text,
                                            color = Color.LightGray,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Command input row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = commandInput,
                                onValueChange = { commandInput = it.uppercase() },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Send OBD command (e.g. 010C or ATRV)...", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = Color(0xFF1E293B),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray
                                )
                            )

                            Button(
                                onClick = {
                                    if (commandInput.isNotBlank()) {
                                        onSendCommand(commandInput.trim())
                                        commandInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Send", color = Color(0xFF05070B), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                // TAB 2: TROUBLESHOOTING
                if (selectedTab == 2) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF0E1520), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Text("KIA SPORTAGE OBD-II CONNECTION GUIDE", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(8.dp))

                        listOf(
                            "1. OBD-II Port Location" to "On Kia Sportage 2.0 MPI, the 16-pin DLC port is located below the driver's dashboard near the hood release latch.",
                            "2. Ignition Required" to "Turn car ignition to 'ON' (or start engine). An unpowered ECU will not respond to PID queries.",
                            "3. Initial Pairing" to "In Android Settings > Bluetooth, pair with 'OBDII', 'V-LINK', or 'ELM327'. Typical PIN is 1234 or 0000.",
                            "4. Chinese Head Unit Pairing" to "On Android Head Units (TS10, UIS7862, MTK), ensure head unit Bluetooth PIN in factory settings matches the adapter (1234).",
                            "5. Supported Protocol" to "Kia Sportage MPI AWD communicates over ISO 15765-4 CAN (11-bit ID, 500 kbaud). The launcher automatically initializes this protocol."
                        ).forEach { (title, desc) ->
                            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text(desc, color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}
