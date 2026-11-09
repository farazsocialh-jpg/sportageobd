@Composable
fun VehicleDrivingScreen(
    onBack: () -> Unit,
    viewModel: ObdViewModel? = null,
    currentSpeedUnit: String = "km/h",
    currentDistanceUnit: String = "km",
    onSpeedUnitChanged: (String) -> Unit = {},
    onDistanceUnitChanged: (String) -> Unit = {},
    onVehicleConfigChanged: (String) -> Unit = {},
    onDrivingPreferenceChanged: (String, Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    // State
    var speedUnit by remember { mutableStateOf(currentSpeedUnit) }
    var distanceUnit by remember { mutableStateOf(currentDistanceUnit) }

    // Load preferences
    val uiPrefs = context.getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE)
    speedUnit = uiPrefs.getString("speedUnit", "km/h") ?: "km/h"
    distanceUnit = uiPrefs.getString("distanceUnit", "km") ?: "km"

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
            Text(text = "Vehicle & Driving", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
        }

        // Speed & Distance Units
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Units", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                // Speed unit selection
                Text("Speed Unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SpeedUnitOption("km/h", speedUnit == "km/h", onSpeedUnitChanged)
                        SpeedUnitOption("mph", speedUnit == "mph", onSpeedUnitChanged)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SpeedUnitOption("km/h", speedUnit == "km/h", onSpeedUnitChanged)
                        SpeedUnitOption("mph", speedUnit == "mph", onSpeedUnitChanged)
                    }
                }

                // Distance unit selection
                Text("Distance Unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DistanceUnitOption("km", distanceUnit == "km", onDistanceUnitChanged)
                        DistanceUnitOption("mi", distanceUnit == "mi", onDistanceUnitChanged)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DistanceUnitOption("km", distanceUnit == "km", onDistanceUnitChanged)
                        DistanceUnitOption("mi", distanceUnit == "mi", onDistanceUnitChanged)
                    }
                }
            }
        }

        // Vehicle Configuration
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Vehicle Configuration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                OutlinedTextField(
                    value = viewModel?.vehicleIdentity?.value?.vin ?: "No VIN detected",
                    onValueChange = { },
                    label = { Text("Vehicle VIN") },
                    readOnly = true,
                    colors = TextFieldDefaults.colors(
                        backgroundColor = DarkSurfaceElevated(),
                        focusedOutlineColor = ElectricCyan(),
                        unfocusedOutlineColor = DarkSurfaceElevated()
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Vehicle type
                OutlinedTextField(
                    value = "Sportage 2.0 MPI",
                    onValueChange = { },
                    label = { Text("Vehicle Type") },
                    readOnly = true,
                    colors = TextFieldDefaults.colors(
                        backgroundColor = DarkSurfaceElevated(),
                        focusedOutlineColor = ElectricCyan(),
                        unfocusedOutlineColor = DarkSurfaceElevated()
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                // Engine configuration
                OutlinedTextField(
                    value = "4-cylinder petrol",
                    onValueChange = { },
                    label = { Text("Engine Config") },
                    readOnly = true,
                    colors = TextFieldDefaults.colors(
                        backgroundColor = DarkSurfaceElevated(),
                        focusedOutlineColor = ElectricCyan(),
                        unfocusedOutlineColor = DarkSurfaceElevated()
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                }
            }
        }

        // Driving Preferences
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).padding(top = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Driving Preferences", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Eco Mode", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = false, onCheckedChange = { }) // TODO: persist
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Shift Point Alert", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = false, onCheckedChange = { }) // TODO: persist
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Start/Stop System", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = true, onCheckedChange = { }) // TODO: persist
                }
            }
        }
    }
}

// Speed unit option
@Composable
fun SpeedUnitOption(
    label: String,
    isSelected: Boolean,
    onSelect: (String) -> Unit
) {
    val background = if (isSelected) ElectricCyan() else DarkSurface()
    val textColor = if (isSelected) OnAccent() else PrimaryText()

    Box(
        modifier = Modifier
            .size(72.dp)
            .background(background)
            .clip(RoundedCornerShape(6.dp))
            .padding(2.dp)
            .clickable(onClick = { onSelect(label) })
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// Distance unit option
@Composable
fun DistanceUnitOption(
    label: String,
    isSelected: Boolean,
    onSelect: (String) -> Unit
) {
    val background = if (isSelected) ElectricCyan() else DarkSurface()
    val textColor = if (isSelected) OnAccent() else PrimaryText()

    Box(
        modifier = Modifier
            .size(72.dp)
            .background(background)
            .clip(RoundedCornerShape(6.dp))
            .padding(2.dp)
            .clickable(onClick = { onSelect(label) })
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxSize()
        )
    }
}