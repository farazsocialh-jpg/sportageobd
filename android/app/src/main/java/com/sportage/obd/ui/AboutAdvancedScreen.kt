@Composable
fun AboutAdvancedScreen(
    onBack: () -> Unit,
    viewModel: ObdViewModel? = null,
    isDemoMode: Boolean = false,
    onToggleDemoMode: (Boolean) -> Unit = {},
    onResetPreferences: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    // State
    var demoMode by remember { mutableStateOf(isDemoMode) }
    var appVersion by remember { mutableStateOf("1.3.3") }

    // Get version from package
    try {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
        appVersion = "Version ${packageInfo.versionName} (${packageInfo.versionCode})"
    } catch (e: Exception) {
        appVersion = "Version 1.3.3"
    }

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
            Text(text = "About & Advanced", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
        }

        // Application information card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Application Information", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Text(appVersion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("Sportage OBD Dashboard", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Text("Package: com.sportage.obd", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Bench test / Demo mode
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Bench Test / Demo Mode", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Simulate live data without OBD connection", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Switch(
                        checked = demoMode,
                        onCheckedChange = { newValue -> {
                            demoMode = newValue
                            onToggleDemoMode(newValue)
                        } }
                    )
                }

                Text("Useful for indoor testing and demonstrations without connecting to a vehicle.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Developer tools
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).padding(top = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Developer Tools", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                // List of developer tools
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DevToolOption(
                        label = "Dump Current Telemetry",
                        description: "Print current OBD sensor readings to terminal",
                        onSelect: { /* TODO: dump telemetry */ }
                    )
                    DevToolOption(
                        label: "Clear DTC Codes",
                        description: "Clear all Diagnostic Trouble Codes",
                        onSelect: { /* TODO: clear DTCs */ }
                    )
                    DevToolOption(
                        label: "Reset Trip Computer",
                        description: "Reset trip distance and fuel calculations",
                        onSelect: { /* TODO: reset trip */ }
                    )
                    DevToolOption(
                        label: "Force Bluetooth Re-scan",
                        description: "Re-scan for available OBD adapters",
                        onSelect: { /* TODO: force re-scan */ }
                    )
                    Divider(modifier = Modifier.height(8.dp), color = DarkSurfaceElevated())
                    OutlinedButton(
                        onClick = onResetPreferences,
                        style = OutlinedButtonStyle.copy(
                            colors = OutlinedButtonDefaults.colors(containedColor = DarkSurfaceElevated())
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Reset All Preferences", color = WarningAmber(), fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.Delete, contentDescription = null, tint = WarningAmber(), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

// Developer tool option
@Composable
fun DevToolOption(
    label: String,
    description: String,
    onSelect: () -> Unit
) {
    OutlinedButton(
        onClick = onSelect,
        style = OutlinedButtonStyle.copy(
            colors = OutlinedButtonDefaults.colors(containedColor = DarkSurfaceElevated())
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal)
            Spacer(Modifier.width(8.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}