package com.sportage.obd.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.OnAccent
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText

/**
 * Navigation & Launcher Settings Subpage:
 * - Default Android Launcher configuration (System Home App)
 * - Waze navigation integration & mini-nav overlay
 * - Installed App Drawer launcher
 * - Launcher system preferences (dock visibility, screen awake, boot auto-start)
 */
@Composable
fun NavigationLauncherPage(
    onSetDefaultLauncher: () -> Unit,
    onOpenAppDrawer: () -> Unit,
    showQuickActionDock: Boolean = true,
    onSetShowQuickActionDock: (Boolean) -> Unit = {},
    keepScreenAwake: Boolean = true,
    onSetKeepScreenAwake: (Boolean) -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE) }

    var wazeMiniWindow by remember {
        mutableStateOf(uiPrefs.getBoolean("wazeMiniWindow", true))
    }
    var autoLaunchWazeOnDrive by remember {
        mutableStateOf(uiPrefs.getBoolean("autoLaunchWazeOnDrive", false))
    }
    var localKeepScreenAwake by remember(keepScreenAwake) {
        mutableStateOf(keepScreenAwake)
    }
    var localShowQuickActionDock by remember(showQuickActionDock) {
        mutableStateOf(showQuickActionDock)
    }
    var autoStartOnBoot by remember {
        mutableStateOf(uiPrefs.getBoolean("autoStartOnBoot", true))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsTopBar(
            title = "Navigation & Launcher",
            subtitle = "Configure Android default home launcher, Waze, and app drawer",
            onNavigateBack = onNavigateBack
        )

        // 1. DEFAULT ANDROID HOME LAUNCHER
        SettingsSectionHeader(title = "Default Home Launcher")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ElectricCyan().copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "SET AS DEFAULT ANDROID HOME LAUNCHER",
                            color = ElectricCyan(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Makes Sportage OBD the permanent car dashboard. Booting the head unit or pressing Home returns directly here.",
                            color = SecondaryText(),
                            fontSize = 11.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = ElectricCyan(),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Button(
                    onClick = onSetDefaultLauncher,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text("Open Android Home Settings", color = OnAccent(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // 2. WAZE & NAVIGATION INTEGRATION
        SettingsSectionHeader(title = "Waze & Navigation Integration")

        SettingsActionItem(
            title = "Launch Waze Navigation",
            description = "Open official Waze app with live crowd-sourced traffic and police reports",
            buttonLabel = "Open Waze",
            icon = Icons.Default.Navigation,
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("waze://?favorite=home")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    if (!com.sportage.obd.map.NavigationLauncher.open(context, 25.2854, 51.5310)) {
                        try {
                            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.waze")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(fallback)
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(context, "Waze or navigation app not installed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )

        SettingsSwitchItem(
            title = "Enable Waze Mini-Window Overlay",
            description = "Allows Waze picture-in-picture mini window to float beside cockpit gauges",
            checked = wazeMiniWindow,
            onCheckedChange = { enabled ->
                wazeMiniWindow = enabled
                uiPrefs.edit().putBoolean("wazeMiniWindow", enabled).apply()
            },
            icon = Icons.Default.Map
        )

        SettingsSwitchItem(
            title = "Auto-Launch Waze on Trip Start",
            description = "Launches turn-by-turn navigation automatically when vehicle begins driving",
            checked = autoLaunchWazeOnDrive,
            onCheckedChange = { enabled ->
                autoLaunchWazeOnDrive = enabled
                uiPrefs.edit().putBoolean("autoLaunchWazeOnDrive", enabled).apply()
            }
        )

        // 3. INSTALLED APP DRAWER
        SettingsSectionHeader(title = "Installed Apps Drawer")

        SettingsActionItem(
            title = "Browse Head Unit Installed Apps",
            description = "Access Spotify, YouTube Music, Radio, and all Android automotive apps",
            buttonLabel = "Open Apps Drawer",
            icon = Icons.Default.Apps,
            onClick = onOpenAppDrawer
        )

        // 4. LAUNCHER PREFERENCES
        SettingsSectionHeader(title = "Launcher Behavior & Screen")

        SettingsSwitchItem(
            title = "Quick Action Dock",
            description = "Shows the bottom navigation dock for fast access to Phone, Media, and Apps",
            checked = localShowQuickActionDock,
            onCheckedChange = { enabled ->
                localShowQuickActionDock = enabled
                uiPrefs.edit().putBoolean("showQuickActionDock", enabled).apply()
                onSetShowQuickActionDock(enabled)
            },
            icon = Icons.AutoMirrored.Filled.ViewSidebar
        )

        SettingsSwitchItem(
            title = "Keep Screen Awake While Driving",
            description = "Acquires Android WakeLock so display never sleeps or turns off while engine is running",
            checked = localKeepScreenAwake,
            onCheckedChange = { enabled ->
                localKeepScreenAwake = enabled
                uiPrefs.edit().putBoolean("keepScreenAwake", enabled).apply()
                onSetKeepScreenAwake(enabled)
            },
            icon = Icons.Default.LockOpen
        )

        SettingsSwitchItem(
            title = "Start on Head Unit Boot",
            description = "Listens to BOOT_COMPLETED to launch Sportage OBD as soon as car accessories power on",
            checked = autoStartOnBoot,
            onCheckedChange = { enabled ->
                autoStartOnBoot = enabled
                uiPrefs.edit().putBoolean("autoStartOnBoot", enabled).apply()
            },
            icon = Icons.Default.PowerSettingsNew
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
