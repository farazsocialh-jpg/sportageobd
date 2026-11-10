package com.sportage.obd.ui.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.boards.BoardLayout
import com.sportage.obd.ui.boards.BoardWidgetType
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText

import com.sportage.obd.ui.dashboard.DashboardLayoutCard
import com.sportage.obd.ui.dashboard.DashboardLayoutTheme

/**
 * Dashboard Customisation Settings Subpage:
 * - Dashboard primary layout (Cockpit + Map, Full Cluster, Custom Board, Split HUD)
 * - Map and cockpit spatial arrangement (Balanced, Map Dominant, Gauges Dominant)
 * - Widget selection (toggle widgets from the BoardWidgetType catalog)
 * - Widget positioning (reorder slot arrangement)
 * - Widget size / grid density (Compact 2x2, Standard 3x2, Expanded)
 */
@Composable
fun DashboardCustomisationPage(
    dashboardLayoutId: String = DashboardLayoutTheme.DEFAULT_ID,
    onSetDashboardLayoutId: (String) -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE) }

    var dashboardLayout by remember {
        mutableStateOf(uiPrefs.getString("dashboardLayout", "Split Map & Cockpit") ?: "Split Map & Cockpit")
    }
    var mapArrangement by remember {
        mutableStateOf(uiPrefs.getString("mapArrangement", "Balanced (50/50)") ?: "Balanced (50/50)")
    }
    var widgetSize by remember {
        mutableStateOf(uiPrefs.getString("widgetSize", "Standard (3x2)") ?: "Standard (3x2)")
    }
    var showMiniGaugesOnMap by remember {
        mutableStateOf(uiPrefs.getBoolean("showMiniGaugesOnMap", true))
    }

    // Active widgets on the custom board
    var boardLayout by remember { mutableStateOf(BoardLayout.load(uiPrefs)) }
    val currentWidgets = boardLayout.boards.firstOrNull() ?: BoardLayout.DEFAULT.boards.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsTopBar(
            title = "Dashboard Customisation",
            subtitle = "Arrange cockpit gauges, map windows, and telemetry widgets",
            onNavigateBack = onNavigateBack
        )

        // 1. PRIMARY DASHBOARD LAYOUT
        SettingsSectionHeader(title = "Primary Dashboard Layout")

        DashboardLayoutCard(
            selectedId = dashboardLayoutId,
            onSelect = { onSetDashboardLayoutId(it.id) }
        )

        val layouts = listOf(
            "Split Map & Cockpit",
            "Full-Screen Gauges",
            "Swipeable Custom Boards",
            "Minimalist Highway HUD"
        )
        SettingsValueItem(
            title = "Active Layout Mode",
            valueText = dashboardLayout,
            description = "Determines how gauges and map are arranged on the primary drive screen",
            icon = Icons.Default.Dashboard,
            onClick = {
                val currentIdx = layouts.indexOf(dashboardLayout)
                val next = layouts[(currentIdx + 1) % layouts.size]
                dashboardLayout = next
                uiPrefs.edit().putString("dashboardLayout", next).apply()
            }
        )

        // 2. MAP & COCKPIT ARRANGEMENT
        SettingsSectionHeader(title = "Map & Cockpit Arrangement")

        val arrangements = listOf(
            "Balanced (50/50)",
            "Map Dominant (65/35)",
            "Gauges Dominant (35/65)"
        )
        SettingsValueItem(
            title = "Split View Ratio",
            valueText = mapArrangement,
            description = "Adjusts screen real-estate allocation between GPS map and live telemetry",
            icon = Icons.Default.Map,
            onClick = {
                val currentIdx = arrangements.indexOf(mapArrangement)
                val next = arrangements[(currentIdx + 1) % arrangements.size]
                mapArrangement = next
                uiPrefs.edit().putString("mapArrangement", next).apply()
            }
        )

        SettingsSwitchItem(
            title = "Show Mini Gauges on Map",
            description = "Overlays transparent speed and RPM pills on the navigation map window",
            checked = showMiniGaugesOnMap,
            onCheckedChange = { enabled ->
                showMiniGaugesOnMap = enabled
                uiPrefs.edit().putBoolean("showMiniGaugesOnMap", enabled).apply()
            },
            icon = Icons.Default.Layers
        )

        // 3. WIDGET SIZE & DENSITY
        SettingsSectionHeader(title = "Widget Size & Density")

        val widgetSizes = listOf(
            "Compact (2x2 Grid)",
            "Standard (3x2 Grid)",
            "Expanded (Single Column)"
        )
        SettingsValueItem(
            title = "Board Widget Grid Scale",
            valueText = widgetSize,
            description = "Controls touch target sizes and cards density on the custom dashboard board",
            icon = Icons.Default.ViewCarousel,
            onClick = {
                val currentIdx = widgetSizes.indexOf(widgetSize)
                val next = widgetSizes[(currentIdx + 1) % widgetSizes.size]
                widgetSize = next
                uiPrefs.edit().putString("widgetSize", next).apply()
            }
        )

        // 4. WIDGET SELECTION & POSITIONING
        SettingsSectionHeader(title = "Active Widgets & Positioning")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkSurfaceElevated())
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "BOARD WIDGET ORDER (${currentWidgets.size} ACTIVE)",
                    color = ElectricCyan(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "Use arrows to reorder slots or tap the remove icon to unpin a sensor widget",
                    color = SecondaryText(),
                    fontSize = 11.sp
                )

                currentWidgets.forEachIndexed { index, widget ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DarkSurfaceElevated(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${index + 1}.",
                                    color = ElectricCyan(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = widget.title,
                                    color = PrimaryText(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val mutable = currentWidgets.toMutableList()
                                            val item = mutable.removeAt(index)
                                            mutable.add(index - 1, item)
                                            val updated = BoardLayout(listOf(mutable))
                                            boardLayout = updated
                                            BoardLayout.save(uiPrefs, updated)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = "Move earlier",
                                            tint = SecondaryText(),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                if (index < currentWidgets.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val mutable = currentWidgets.toMutableList()
                                            val item = mutable.removeAt(index)
                                            mutable.add(index + 1, item)
                                            val updated = BoardLayout(listOf(mutable))
                                            boardLayout = updated
                                            BoardLayout.save(uiPrefs, updated)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Move later",
                                            tint = SecondaryText(),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        if (currentWidgets.size > 2) {
                                            val mutable = currentWidgets.toMutableList()
                                            mutable.removeAt(index)
                                            val updated = BoardLayout(listOf(mutable))
                                            boardLayout = updated
                                            BoardLayout.save(uiPrefs, updated)
                                        }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove widget",
                                        tint = SecondaryText(),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Add unpinned widget button if space available
                val availableToAdd = BoardWidgetType.entries.filter { it !in currentWidgets }
                if (availableToAdd.isNotEmpty()) {
                    SettingsActionItem(
                        title = "Add Sensor Widget",
                        description = "Pin ${availableToAdd.first().title} to the custom dashboard",
                        buttonLabel = "Add Widget",
                        icon = Icons.Default.Add,
                        onClick = {
                            val updated = BoardLayout(listOf(currentWidgets + availableToAdd.first()))
                            boardLayout = updated
                            BoardLayout.save(uiPrefs, updated)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
