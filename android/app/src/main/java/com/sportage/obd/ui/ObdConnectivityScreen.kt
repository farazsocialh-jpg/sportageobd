@Composable
fun ObdConnectivityScreen(
    onBack: () -> Unit,
    viewModel: ObdViewModel? = null,
    isConnected: Boolean = false,
    connectionState: com.sportage.obd.bluetooth.ConnectionState = com.sportage.obd.bluetooth.ConnectionState.Disconnected,
    onConnectClicked: (BluetoothDevice?, Boolean) -> Unit = { },
    onRefreshClicked: () -> Unit = { },
    onToggleAutoConnect: (Boolean) -> Unit = { },
    onTogglePollingPause: () -> Unit = { }
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    // State
    var showPairing by remember { mutableStateOf(false) }
    var selectedAdapter by remember { mutableStateOf<BluetoothDevice?>(null) }
    var autoConnect by remember { mutableStateOf(viewModel?.isPollingPaused ?: false false) }
    var pollingPaused by remember { mutableStateOf(viewModel?.isPollingPaused ?: false) }

    // Load persisted preferences
    val uiPrefs = context.getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE)
    autoConnect = uiPrefs.getBoolean("autoConnect", false)
    pollingPaused = uiPrefs.getBoolean("pollingPaused", false)

    MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
            .horizontalPadding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Back button row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
                colors = ButtonDefaults.buttonColors(containedColor = DarkSurfaceElevated())
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp))
            }
            Text(text = "OBD & Connectivity", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
        }

        // Connection status card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Connection Status", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (isConnected) "Connected" : "Disconnected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConnected) ElectricCyan() else WarningAmber()
                    )
                    Button(
                        onClick = { /* TODO: connect/disconnect */ },
                        modifier = Modifier.size(80.dp),
                        colors = ButtonDefaults.buttonColors(containedColor = ElectricCyan())
                    ) {
                        Text(isConnected ? "Disconnect" : "Connect", color = OnAccent(), fontWeight = FontWeight.Bold)
                    }
                }

                // Bluetooth adapter selection
                Text("Bluetooth Adapter", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = selectedAdapter?.let { device -> device.name ?: "Unknown" } ?: "No adapter selected",
                    onValueChange = { /* TODO */ },
                    label = { Text("Adapter") },
                    leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                    readOnly = true,
                    colors = TextFieldDefaults.colors(
                        backgroundColor = DarkSurfaceElevated(),
                        focusedOutlineColor = ElectricCyan(),
                        unfocusedOutlineColor = DarkSurfaceElevated()
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Paired devices list
                Text("Paired Devices", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (isLandscape) {
                    // Landscape: scrollable list
                    OutlinedTextField(
                        value = "See paired devices in portrait mode",
                        onValueChange = { },
                        label = { Text("") },
                        readOnly = true,
                        colors = TextFieldDefaults.colors(backgroundColor = DarkSurfaceElevated()),
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                } else {
                    // Portrait: list of devices
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Placeholder for paired devices
                        Text("No paired devices yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = { showPairing = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containedColor = ElectricCyan())
                        ) {
                            Text("Pair New Device", color = ElectricCyan(), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Automatic connection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Auto-connect on start", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = autoConnect,
                        onCheckedChange = { newValue -> {
                            autoConnect = newValue
                            onToggleAutoConnect(newValue)
                            uiPrefs.edit().putBoolean("autoConnect", autoConnect).apply()
                        } }
                    )
                }

                // OBD polling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("OBD Polling", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = pollingPaused,
                        onCheckedChange = { newValue -> {
                            pollingPaused = newValue
                            onTogglePollingPause()
                            uiPrefs.edit().putBoolean("pollingPaused", pollingPaused).apply()
                        } }
                    )
                }
            }
        }

        // Connection preferences card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).padding(top = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Connection Preferences", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Reconnect if dropped", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = true, onCheckedChange = { }) // TODO: persist
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Wait for ignition", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = false, onCheckedChange = { }) // TODO: persist
                }
            }
        }

        // Adapter management card
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Adapter Management", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                OutlinedTextField(
                    value = "ELM327 USB/Bluetooth",
                    onValueChange = { },
                    label = { Text("Current adapter") },
                    leadingIcon = { Icon(Icons.Default.DeviceHub, contentDescription = null) },
                    readOnly = true,
                    colors = TextFieldDefaults.colors(
                        backgroundColor = DarkSurfaceElevated(),
                        focusedOutlineColor = ElectricCyan(),
                        unfocusedOutlineColor = DarkSurfaceElevated()
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { onRefreshClicked() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containedColor = ElectricCyan())
                ) {
                    Text("Refresh Adapters", color = ElectricCyan(), fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = "No adapters managed",
                    onValueChange = { },
                    label = { Text("") },
                    readOnly = true,
                    colors = TextFieldDefaults.colors(backgroundColor = DarkSurfaceElevated()),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}