@Composable
fun DataPrivacyScreen(
    onBack: () -> Unit,
    viewModel: ObdViewModel? = null,
    isCsvLogging: Boolean = false,
    onToggleCsvLogging: (Boolean) -> Unit = {},
    onExportData: () -> Unit = {},
    onShareLogs: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    // State
    var loggingActive by remember { mutableStateOf(isCsvLogging) }
    var autoStart by remember { mutableStateOf(viewModel?.csvAutostart() ?: false) }

    // Load preferences
    val uiPrefs = context.getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE)
    loggingActive = uiPrefs.getBoolean("csvLoggingActive", false)
    autoStart = uiPrefs.getBoolean("csvAutostart", false)

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
            Text(text = "Data & Privacy", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
        }

        // CSV Telemetry Logging card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("CSV Telemetry Logging", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Record sensor data to CSV", style = MaterialTheme.typography.bodySmall)

                    Switch(
                        checked = loggingActive,
                        onCheckedChange = { newValue -> {
                            loggingActive = newValue
                            onToggleCsvLogging(newValue)
                            uiPrefs.edit().putBoolean("csvLoggingActive", loggingActive).apply()
                        } }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Auto-start with connection", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = autoStart,
                        onCheckedChange = { newValue -> {
                            autoStart = newValue
                            uiPrefs.edit().putBoolean("csvAutostart", autoStart).apply()
                        } }
                    )
                }

                // Export data button
                Button(
                    onClick = onExportData,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containedColor = DarkSurfaceElevated())
                ) {
                    HapticFeedback.Interface
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = ElectricCyan(), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export Data", color = ElectricCyan(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Share logs card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).padding(top = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Share Logs", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Send CSV log file via share intent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Button(
                    onClick = onShareLogs,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containedColor = ElectricCyan())
                ) {
                    HapticFeedback.Interface
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = ElectricCyan(), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share Log", color = ElectricCyan(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Privacy policy
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Privacy Policy", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Review our data practices and policies", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }

                OutlinedButton(
                    onClick = { /* TODO: open privacy policy */ },
                    style = OutlinedButtonStyle.copy(
                        colors = OutlinedButtonDefaults.colors(containedColor = DarkSurfaceElevated())
                    )
                ) {
                    Text("Read Privacy Policy", color = ElectricCyan())
                }
            }
        }
    }
}