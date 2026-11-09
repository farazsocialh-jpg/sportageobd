@Composable
fun AlertsNotificationsScreen(
    onBack: () -> Unit,
    viewModel: ObdViewModel? = null,
    thresholds: com.sportage.obd.service.AlertThresholds = com.sportage.obd.service.AlertThresholds(),
    onThresholdsUpdated: (com.sportage.obd.service.AlertThresholds) -> Unit = {},
    onAudibleChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    // State - persist to SharedPreferences
    val uiPrefs = context.getSharedPreferences("sportage_obd_alerts", Context.MODE_PRIVATE)
    var coolantMaxC by remember { mutableStateOf(uiPrefs.getInt("coolantMaxC", 110)) }
    var batteryMinV by remember { mutableStateOf(uiPrefs.getFloat("batteryMinV", 11.5f)) }
    var rpmMax by remember { mutableStateOf(uiPrefs.getInt("rpmMax", 6000)) }
    var speedMaxKmh by remember { mutableStateOf(uiPrefs.getInt("speedMaxKmh", 160)) }
    var audibleAlerts by remember { mutableStateOf(uiPrefs.getBoolean("audibleAlerts", true)) }

    // Update from viewModel if available
    if (viewModel != null) {
        val storedThresholds = viewModel?.alertThresholds() ?: com.sportage.obd.service.AlertThresholds()
        coolantMaxC = storedThresholds.coolantMaxC
        batteryMinV = storedThresholds.batteryMinV
        rpmMax = storedThresholds.rpmMax
        speedMaxKmh = storedThresholds.speedMaxKmh
        audibleAlerts = storedThresholds.audibleAlerts
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
            Text(text = "Alerts & Notifications", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
        }

        // Warning thresholds card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Vehicle Warning Thresholds", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text("Audible beep + red banner when a value crosses its limit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Coolant temperature threshold
                ThresholdRow(
                    label = "Coolant Overheat Limit",
                    value = "$coolantMaxC°C",
                    onMinus = { coolantMaxC = (coolantMaxC - 5).coerceAtLeast(90); updateThresholds() },
                    onPlus = { coolantMaxC = (coolantMaxC + 5).coerceAtMost(130); updateThresholds() }
                )

                // Battery voltage threshold
                ThresholdRow(
                    label = "Battery Low Limit",
                    value = "%.1fV".format(batteryMinV),
                    onMinus = { batteryMinV = (batteryMinV - 0.5f).coerceAtLeast(9f); updateThresholds() },
                    onPlus = { batteryMinV = (batteryMinV + 0.5f).coerceAtMost(14f); updateThresholds() }
                )

                // RPM threshold
                ThresholdRow(
                    label = "RPM Redline Limit",
                    value = "${rpmMax}",
                    onMinus = { rpmMax = (rpmMax - 500).coerceAtLeast(3000); updateThresholds() },
                    onPlus = { rpmMax = (rpmMax + 500).coerceAtMost(7500); updateThresholds() }
                )

                // Speed threshold
                ThresholdRow(
                    label = "Speed Limit",
                    value = "${speedMaxKmh} km/h",
                    onMinus = { speedMaxKmh = (speedMaxKmh - 10).coerceAtLeast(60); updateThresholds() },
                    onPlus = { speedMaxKmh = (speedMaxKmh + 10).coerceAtMost(220); updateThresholds() }
                )
            }
        }

        // Audible warning settings
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).padding(top = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Audible Warning Settings", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Beep on threshold crossing", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = audibleAlerts,
                        onCheckedChange = { newValue -> {
                            audibleAlerts = newValue
                            onAudibleChanged(newValue)
                            uiPrefs.edit().putBoolean("audibleAlerts", audibleAlerts).apply()
                        } }
                    )
                }

                Text("A tone will play when any warning threshold is exceeded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Threshold row component
@Composable
fun ThresholdRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = PrimaryText())
                Text(value, style = MaterialTheme.typography.bodySmall, color = SecondaryText())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onMinus,
                    style = OutlinedButtonStyle.copy(
                        colors = OutlinedButtonDefaults.colors(containedColor = DarkSurfaceElevated())
                    )
                ) {
                    Text("−", color = WarningAmber(), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onPlus,
                    style = OutlinedButtonStyle.copy(
                        colors = OutlinedButtonDefaults.colors(containedColor = DarkSurfaceElevated())
                    )
                ) {
                    Text("+", color = ElectricCyan(), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Update thresholds function
private fun updateThresholds() {
    // This would call viewModel?.updateAlertThresholds() in a real implementation
    // For now, just log the values
    android.util.Log.d("AlertsScreen", "Thresholds updated: coolantMaxC=$coolantMaxC, batteryMinV=$batteryMinV, rpmMax=$rpmMax, speedMaxKmh=$speedMaxKmh")
}