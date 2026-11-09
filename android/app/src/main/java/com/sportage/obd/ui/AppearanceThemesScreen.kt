@Composable
fun AppearanceThemesScreen(
    onBack: () -> Unit,
    viewModel: ObdViewModel? = null,
    isDark: Boolean = true,
    onThemeChanged: (String) -> Unit = {},
    onAccentChanged: (String?) -> Unit = {},
    onDensityChanged: (String?) -> Unit = {},
    onGaugeStyleChanged: (String?) -> Unit = {},
    onFontSizeChanged: (Float) -> Unit = {},
    onModeChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    // State from viewModel or defaults
    var themeId by remember { mutableStateOf(viewModel?.themeId ?: "cockpit-dark") }
    var accentOverride by remember { mutableStateOf(viewModel?.accentOverride ?: null) }
    var densityOverride by remember { mutableStateOf(viewModel?.densityOverride ?: null) }
    var gaugeStyleOverride by remember { mutableStateOf(viewModel?.gaugeStyleOverride ?: null) }
    var fontScale by remember { mutableStateOf(viewModel?.fontScale ?: 1.0f) }
    var autoNightDim by remember { mutableStateOf(true) }
    var nightDimLevel by remember { mutableStateOf(0.55f) }

    // Load UI prefs
    val uiPrefs = context.getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE)
    autoNightDim = uiPrefs.getBoolean("autoNightDim", true)
    nightDimLevel = uiPrefs.getFloat("nightDimLevel", 0.55f)

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
            Text(text = "Appearance & Themes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
        }

        // Theme Selection Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Theme & Colors", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                // Theme presets
                Text("Select Theme", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Theme options grid
                if (isLandscape) {
                    // Landscape: 2-column grid
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        foreach (theme in builtInThemes()) {
                            ThemeOptionCard(
                                themeId = theme.id,
                                title = theme.title,
                                isSelected = themeId == theme.id,
                                onSelect = { themeId = it.id; onThemeChanged(theme.id) }
                            )
                        }
                    }
                } else {
                    // Portrait: single column
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        foreach (theme in builtInThemes()) {
                            ThemeOptionCard(
                                themeId = theme.id,
                                title = theme.title,
                                isSelected = themeId == theme.id,
                                onSelect = { themeId = it.id; onThemeChanged(theme.id) }
                            )
                        }
                    }
                }

                // Accent colour
                ThemeAccentCard(
                    currentAccent = accentOverride,
                    onAccentSelected = { accentOverride = it; onAccentChanged(it) }
                )

                // Density
                ThemeDensityCard(
                    currentDensity = densityOverride,
                    onDensitySelected = { densityOverride = it; onDensityChanged(it) }
                )

                // Gauge style
                ThemeGaugeStyleCard(
                    currentGaugeStyle = gaugeStyleOverride,
                    onGaugeStyleSelected = { gaugeStyleOverride = it; onGaugeStyleChanged(it) }
                )

                // Font size
                ThemeFontSizeCard(
                    currentFontScale = fontScale,
                    onFontSizeChanged = { fontScale = it; onFontSizeChanged(it) }
                )

                // Light/Dark/Automatic mode
                ThemeModeCard(
                    isAuto = autoNightDim,
                    onAutoSelected = { autoNightDim = !autoNightDim; uiPrefs.edit().putBoolean("autoNightDim", autoNightDim).apply(); onModeChanged(if (autoNightDim) "auto" else if (isDark) "dark" else "light") },
                    onDarkSelected = { autoNightDim = false; themeId = "cockpit-dark"; onModeChanged("dark") },
                    onLightSelected = { autoNightDim = false; themeId = "cockpit-light"; onModeChanged("light") }
                )
            }
        }
    }
}

// Helper: built-in theme list
private fun builtInThemes(): List<ThemePreset> {
    return listOf(
        ThemePreset(id = "cockpit-dark", title = "Dark"),
        ThemePreset(id = "cockpit-light", title = "Light"),
        ThemePreset(id = "cockpit-amber", title = "Amber"),
        ThemePreset(id = "cockpit-blue", title = "Blue"),
        ThemePreset(id = "cockpit-forest", title = "Forest")
    )
}

// Data class for theme preset
data class ThemePreset(
    val id: String,
    val title: String
)

// Theme option card component
@Composable
fun ThemeOptionCard(
    themeId: String,
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

// Accent colour card
@Composable
fun ThemeAccentCard(
    currentAccent: String?,
    onAccentSelected: (String?) -> Unit
) {
    var accentInput by remember { mutableStateOf(currentAccent ?: "") }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Accent Colour", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = accentInput,
                onValueChange = { accentInput = it },
                label = { Text("Accent colour hex") },
                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                trailingIcon = {
                    if (accentInput.isNotEmpty()) {
                        Button(
                            onClick = { onAccentSelected(accentInput) },
                            modifier = Modifier.size(32.dp),
                            colors = ButtonDefaults.buttonColors(containedColor = ElectricCyan())
                        ) {
                            Text("Apply", color = OnAccent(), fontWeight = FontWeight.Bold)
                        }
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
        }
    }
}

// Density card
@Composable
fun ThemeDensityCard(
    currentDensity: String?,
    onDensitySelected: (String?) -> Unit
) {
    var densityInput by remember { mutableStateOf(currentDensity ?: "default") }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Display Density", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = densityInput,
                onValueChange = { densityInput = it },
                label = { Text("Density multiplier") },
                leadingIcon = { Icon(Icons.Default.FormatSize, contentDescription = null) },
                trailingIcon = {
                    if (densityInput.isNotEmpty()) {
                        Button(
                            onClick = { onDensitySelected(densityInput) },
                            modifier = Modifier.size(32.dp),
                            colors = ButtonDefaults.buttonColors(containedColor = ElectricCyan())
                        ) {
                            Text("Apply", color = ElectricCyan(), fontWeight = FontWeight.Bold)
                        }
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
        }
    }
}

// Gauge style card
@Composable
fun ThemeGaugeStyleCard(
    currentGaugeStyle: String?,
    onGaugeStyleSelected: (String?) -> Unit
) {
    var gaugeInput by remember { mutableStateOf(currentGaugeStyle ?: "classic") }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Gauge Style", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = gaugeInput,
                onValueChange = { gaugeInput = it },
                label = { Text("Gauge style") },
                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                trailingIcon = {
                    if (gaugeInput.isNotEmpty()) {
                        Button(
                            onClick = { onGaugeStyleSelected(gaugeInput) },
                            modifier = Modifier.size(32.dp),
                            colors = ButtonDefaults.buttonColors(containedColor = ElectricCyan())
                        ) {
                            Text("Apply", color = ElectricCyan(), fontWeight = FontWeight.Bold)
                        }
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
        }
    }
}

// Font size card
@Composable
fun ThemeFontSizeCard(
    currentFontScale: Float,
    onFontSizeChanged: (Float) -> Unit
) {
    var fontSizeInput by remember { mutableStateOf(currentFontScale) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Font Size", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Small", style = MaterialTheme.typography.bodyMedium, contentDescription = null)
                Slider(
                    value = fontSizeInput,
                    onValueChange = { fontSizeInput = it.coerceIn(0.85f, 1.5f); onFontSizeChanged(it) },
                    steps = 3,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan(),
                        activeTrackColor = ElectricCyan().copy(alpha = 0.5f)
                    )
                )
                Text("Large", style = MaterialTheme.typography.bodyMedium, contentDescription = null)
            }
        }
    }
}

// Mode card
@Composable
fun ThemeModeCard(
    isAuto: Boolean,
    onAutoSelected: () -> Unit,
    onDarkSelected: () -> Unit,
    onLightSelected: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Mode", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ModeOption(
                    label = "Auto",
                    isSelected = isAuto,
                    onSelect = onAutoSelected
                )
                ModeOption(
                    label = "Dark",
                    isSelected = !isAuto,
                    onSelect = onDarkSelected
                )
                ModeOption(
                    label = "Light",
                    isSelected = !isAuto,
                    onSelect = onLightSelected
                )
            }
        }
    }
}

@Composable
fun ModeOption(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val background = if (isSelected) ElectricCyan() else DarkSurface()
    val textColor = if (isSelected) OnAccent() else PrimaryText()

    Box(
        modifier = Modifier
            .size(80.dp)
            .background(background)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxSize()
        )
    }
}