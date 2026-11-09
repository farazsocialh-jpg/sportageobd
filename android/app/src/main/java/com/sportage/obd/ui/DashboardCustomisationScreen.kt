@Composable
fun DashboardCustomisationScreen(
    onBack: () -> Unit,
    viewModel: ObdViewModel? = null,
    currentDashboardLayout: String = DashboardLayoutTheme.DEFAULT_ID,
    onLayoutChanged: (String) -> Unit = {},
    onWidgetSelected: (String) -> Unit = {},
    onWidgetPositionChanged: (Int, Int) -> Unit = {},
    onWidgetSizeChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    // State
    var selectedLayout by remember { mutableStateOf(currentDashboardLayout) }
    var widgetSelection by remember { mutableStateOf("") }
    var widgetX by remember { mutableStateOf(0) }
    var widgetY by remember { mutableStateOf(0) }
    var widgetSize by remember { mutableStateOf(1) }

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
            Text(text = "Dashboard Customisation", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
        }

        // Layout selection
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Layout", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                // Layout options
                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        foreach (layout in dashboardLayouts()) {
                            LayoutOptionCard(
                                layoutId = layout.id,
                                title = layout.title,
                                isSelected = selectedLayout == layout.id,
                                onSelect = { selectedLayout = layout.id; onLayoutChanged(layout.id) }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        foreach (layout in dashboardLayouts()) {
                            LayoutOptionCard(
                                layoutId = layout.id,
                                title = layout.title,
                                isSelected = selectedLayout == layout.id,
                                onSelect = { selectedLayout = layout.id; onLayoutChanged(layout.id) }
                            )
                        }
                    }
                }

                // Map and cockpit arrangement
                Text("Map & Cockpit Arrangement", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Map prominence:", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = 0.5f.coerceIn(0.3f, 0.7f),
                        onValueChange = { /* TODO: implement arrangement change */ },
                        steps = 4,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Widget selection
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Widget Selection", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                OutlinedTextField(
                    value = widgetSelection,
                    onValueChange = { widgetSelection = it },
                    label = { Text("Select widget") },
                    leadingIcon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    trailingIcon = {
                        if (widgetSelection.isNotEmpty()) {
                            Button("Select", onClick = { /* TODO */ })
                        } else {
                            Box(Modifier.size(32.dp))
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        backgroundColor = DarkSurfaceElevated(),
                        focusedOutlineColor = ElectricCyan(),
                        unfocusedOutlineColor = DarkSurfaceElevated()
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Widget positioning
                if (widgetSelection.isNotEmpty()) {
                    Text("Widget Position", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("X: $widgetX", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = widgetX.toFloat().coerceIn(0f, 3f),
                            onValueChange = { widgetX = it.roundToInt(); onWidgetPositionChanged(it, widgetY) },
                            steps = 3,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Y: $widgetY", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = widgetY.toFloat().coerceIn(0f, 3f),
                            onValueChange = { widgetY = it.roundToInt(); onWidgetPositionChanged(widgetX, it) },
                            steps = 3,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Size: $widgetSize", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = widgetSize.toFloat().coerceIn(1f, 3f),
                            onValueChange = { widgetSize = it.roundToInt(); onWidgetSizeChanged(it) },
                            steps = 2,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// Helper functions
private fun dashboardLayouts(): List<DashboardLayoutPreset> {
    return listOf(
        DashboardLayoutPreset(id = DashboardLayoutTheme.DEFAULT_ID, title = "Default"),
        DashboardLayoutPreset(id = "layout-compact", title = "Compact"),
        DashboardLayoutPreset(id = "layout-expanded", title = "Expanded"),
        DashboardLayoutPreset(id = "layout-minimal", title = "Minimal")
    )
}

private fun dashboardLayoutPresetFromId(id: String): DashboardLayoutPreset {
    return when (id) {
        DashboardLayoutTheme.DEFAULT_ID -> DashboardLayoutPreset(id = DashboardLayoutTheme.DEFAULT_ID, title = "Default")
        "layout-compact" -> DashboardLayoutPreset(id = "layout-compact", title = "Compact")
        "layout-expanded" -> DashboardLayoutPreset(id = "layout-expanded", title = "Expanded")
        "layout-minimal" -> DashboardLayoutPreset(id = "layout-minimal", title = "Minimal")
        else -> DashboardLayoutPreset(id = DashboardLayoutTheme.DEFAULT_ID, title = "Default")
    }
}

// Data classes
data class DashboardLayoutPreset(
    val id: String,
    val title: String
)

// Layout option card
@Composable
fun LayoutOptionCard(
    layoutId: String,
    title: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val backgroundColor = if (isSelected) ElectricCyan() else DarkSurface()
    val textColor = if (isSelected) OnAccent() else PrimaryText()

    Box(
        modifier = Modifier
            .size(80.dp)
            .background(backgroundColor)
            .clip(RoundedCornerShape(8.dp))
            .padding(4.dp)
            .clickable(onClick = onSelect)
    ) {
        Text(
            text = title,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxSize()
        )
    }
}