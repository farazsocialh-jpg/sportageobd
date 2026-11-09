@Composable
fun NavigationLauncherScreen(
    onBack: () -> Unit,
    viewModel: ObdViewModel? = null,
    onSetDefaultLauncher: () -> Unit = {},
    onWazeIntegration: () -> Unit = {},
    onAppDrawerClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    // State
    var showAppDrawer by remember { mutableStateOf(false) }

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
            Text(text = "Navigation & Launcher", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
        }

        // Default Android launcher
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Default Android Launcher", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Current launcher: Sportage OBD Dashboard", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = onSetDefaultLauncher,
                        modifier = Modifier.size(80.dp),
                        colors = ButtonDefaults.buttonColors(containedColor = ElectricCyan())
                    ) {
                        Text("Set Default", color = ElectricCyan(), fontWeight = FontWeight.Bold)
                    }
                }

                Text("Make Sportage OBD the permanent home screen. Booting the head unit or pressing Home returns here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Waze integration
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Waze Integration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Waze integration enables live routing and traffic data", style = MaterialTheme.typography.bodySmall)
                    if (isWazeInstalled()) {
                        Text("Installed", style = MaterialTheme.typography.bodySmall, color = ElectricCyan())
                    } else {
                        Text("Not Installed", style = MaterialTheme.typography.bodySmall, color = WarningAmber())
                    }
                }

                Button(
                    onClick = onWazeIntegration,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containedColor = ElectricCyan())
                ) {
                    Text("Connect Waze", color = ElectricCyan(), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Installed app drawer
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Installed App Drawer", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Browse and launch all head unit installed apps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { showAppDrawer = true },
                        modifier = Modifier.size(80.dp),
                        colors = ButtonDefaults.buttonColors(containedColor = ElectricCyan())
                    ) {
                        Text("Open", color = ElectricCyan(), fontWeight = FontWeight.Bold)
                    }
                }

                Text("View all apps installed on the head unit and launch them from here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Launcher preferences
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).padding(top = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Launcher Preferences", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Home on ignition off", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = false, onCheckedChange = { }) // TODO: persist
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Show OBD stats on home", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = true, onCheckedChange = { }) // TODO: persist
                }
            }
        }
    }
}

// Check if Waze is installed
private fun isWazeInstalled(): Boolean {
    val packageManager = LocalContext.current.packageManager
    val launchIntent = packageManager.getLaunchIntentForPackage("com.waze")
    return launchIntent != null
}