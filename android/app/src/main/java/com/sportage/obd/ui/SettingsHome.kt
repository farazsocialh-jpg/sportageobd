@Composable
fun SettingsHome(
    onCategoryClick: (String) -> Unit,
    viewModel: ObdViewModel? = null,
    isDark: Boolean = true
) {
    val context = LocalContext.current

    // Define the 8 settings categories with icons, titles, and descriptions
    val settingsCategories = remember {
        listOf(
            SettingsCategory(
                "Appearance & Themes",
                "Themes, colours and display options",
                Icons.Default.Palette,
                "appearance"
            ),
            SettingsCategory(
                "Dashboard Customisation",
                "Layouts and widget arrangement",
                Icons.Default.Dashboard,
                "dashboard"
            ),
            SettingsCategory(
                "OBD & Connectivity",
                "Bluetooth and connection settings",
                Icons.Default.Bluetooth,
                "connectivity"
            ),
            SettingsCategory(
                "Vehicle & Driving",
                "Units and vehicle preferences",
                Icons.Default.DirectionsCar,
                "vehicle"
            ),
            SettingsCategory(
                "Alerts & Notifications",
                "Vehicle warnings and sounds",
                Icons.Default.Notifications,
                "alerts"
            ),
            SettingsCategory(
                "Navigation & Launcher",
                "Maps and launcher settings",
                Icons.Default.Navigation,
                "navigation"
            ),
            SettingsCategory(
                "Data & Privacy",
                "Logging and data management",
                Icons.Default.Storage,
                "data"
            ),
            SettingsCategory(
                "About & Advanced",
                "Application and advanced tools",
                Icons.Default.Info,
                "advanced"
            )
        )
    }

    MaterialTheme.colorScheme // ensure colors are available

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header section
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                text = "Settings",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }

        // Categories list
        foreach (category in settingsCategories) {
            SettingsCategoryItem(
                title = category.title,
                description = category.description,
                icon = category.icon,
                onClick = { onCategoryClick(category.route) }
            )
        }
    }
}

// Data class for settings categories
data class SettingsCategory(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String
)

// Reusable settings category item component
@Composable
fun SettingsCategoryItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    var ripple by remember { mutableStateOf(true) }

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clickable(
                interactionSource = remember { InteractionSource.none() },
                indication =remember { RippleIndication(confirmPress = true) }
            ),
        enabled = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 12.dp)
            )

            // Title and description
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Start,
                horizontalAlignment = Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Navigation arrow
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Navigate to $title settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}