package com.sportage.obd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.launcher.DriverProfile
import com.sportage.obd.launcher.LauncherSettings
import com.sportage.obd.launcher.PageId
import com.sportage.obd.launcher.Units
import com.sportage.obd.ui.theme.AmberWarning
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.TealAccent

/**
 * Phase 3/4 controls: cockpit layout, automation rules, driver profiles and backup.
 *
 * Everything writes through [onUpdate] immediately — a head unit has no "apply" step and a driver
 * who adjusts the grid in a car park should not lose it when the ignition drops.
 */
@Composable
fun CockpitSettingsCard(
    settings: LauncherSettings,
    onUpdate: (LauncherSettings) -> Unit,
    profiles: List<DriverProfile>,
    activeProfile: String,
    onSelectProfile: (DriverProfile) -> Unit,
    onSaveProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onToggleUnits: () -> Unit
) {
    var showSaveProfile by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF090D14),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "COCKPIT LAYOUT, AUTOMATION & DRIVERS",
                color = ElectricCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            // ---- app drawer ----
            StepperRow(
                title = "App drawer columns",
                subtitle = "Grid width used by the Apps screen",
                value = settings.drawerColumns.toString(),
                onDec = { onUpdate(settings.copy(drawerColumns = settings.drawerColumns - 1)) },
                onInc = { onUpdate(settings.copy(drawerColumns = settings.drawerColumns + 1)) }
            )
            StepperRow(
                title = "App icon size",
                subtitle = "Larger icons are easier to hit on a moving head unit",
                value = "${settings.drawerIconDp}dp",
                onDec = { onUpdate(settings.copy(drawerIconDp = settings.drawerIconDp - 4)) },
                onInc = { onUpdate(settings.copy(drawerIconDp = settings.drawerIconDp + 4)) }
            )

            // ---- page visibility ----
            Text("VISIBLE PAGES", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            PageId.entries.forEach { page ->
                val isHome = page == PageId.HOME
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            page.label + if (isHome) " (always shown)" else "",
                            color = if (isHome) SecondaryText else Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Switch(
                        checked = settings.isVisible(page),
                        enabled = !isHome,
                        onCheckedChange = { visible ->
                            val hidden = settings.hiddenPages.toMutableSet()
                            if (visible) hidden.remove(page) else hidden.add(page)
                            onUpdate(settings.copy(hiddenPages = hidden))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TealAccent,
                            checkedTrackColor = TealAccent.copy(alpha = 0.4f)
                        )
                    )
                }
            }

            // ---- automation ----
            Text("AUTOMATION", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Drive Mode", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(
                        "Enter automatically above ${settings.driveModeEnterKmh} ${settings.units.label}",
                        color = SecondaryText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Switch(
                    checked = settings.driveModeEnabled,
                    onCheckedChange = { onUpdate(settings.copy(driveModeEnabled = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = TealAccent, checkedTrackColor = TealAccent.copy(alpha = 0.4f))
                )
            }
            if (settings.driveModeEnabled) {
                StepperRow(
                    title = "Drive Mode threshold",
                    subtitle = "Speed must hold for 3 seconds; leaving it takes 12",
                    value = "${settings.driveModeEnterKmh} km/h",
                    onDec = { onUpdate(settings.copy(driveModeEnterKmh = settings.driveModeEnterKmh - 5)) },
                    onInc = { onUpdate(settings.copy(driveModeEnterKmh = settings.driveModeEnterKmh + 5)) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pause when parked", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(
                        "Stops polling after ${settings.autoPauseMinutes} minutes at a standstill",
                        color = SecondaryText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Switch(
                    checked = settings.autoPauseEnabled,
                    onCheckedChange = { onUpdate(settings.copy(autoPauseEnabled = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = TealAccent, checkedTrackColor = TealAccent.copy(alpha = 0.4f))
                )
            }
            if (settings.autoPauseEnabled) {
                StepperRow(
                    title = "Parked for",
                    subtitle = "A manual resume always overrides this rule",
                    value = "${settings.autoPauseMinutes} min",
                    onDec = { onUpdate(settings.copy(autoPauseMinutes = settings.autoPauseMinutes - 1)) },
                    onInc = { onUpdate(settings.copy(autoPauseMinutes = settings.autoPauseMinutes + 1)) }
                )
            }

            // ---- pinned apps ----
            Text("DOCK & HOME SHORTCUTS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            if (settings.pinnedApps.isEmpty()) {
                Text(
                    "Open the app drawer and tap the pin on any app to put it on the dock and the home strip.",
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                settings.pinnedApps.forEach { pin ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pin.label, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        IconButton(
                            onClick = { onUpdate(settings.copy(pinnedApps = settings.pinnedApps - pin)) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove ${pin.label} pin", tint = AmberWarning, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // ---- profiles ----
            Text("DRIVER PROFILES", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsProfileChip("Default", activeProfile == "Default") {
                    onSelectProfile(DriverProfile(name = "Default"))
                }.Render()
                profiles.forEach { profile ->
                    SettingsProfileChip(profile.name, activeProfile.equals(profile.name, ignoreCase = true)) {
                        onSelectProfile(profile)
                    }.Render()
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        profileName = ""
                        showSaveProfile = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Save current as profile", color = ElectricCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                if (activeProfile != "Default") {
                    Button(
                        onClick = { onDeleteProfile(activeProfile) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Delete \"$activeProfile\"", color = AmberWarning, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Text(
                "Units are part of a profile: ${settings.units.label}",
                color = SecondaryText,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            TextButton(onClick = onToggleUnits) {
                Text(
                    if (settings.units == Units.KMH) "Switch to mph" else "Switch to km/h",
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // ---- backup ----
            Text("SETTINGS BACKUP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(
                "Exports layout, automation and profiles as a small text file. Trips stay in their own " +
                    "CSV files. There is no cloud account: the file is yours to keep.",
                color = SecondaryText,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBackup,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Export settings", color = Color(0xFF05070B), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Button(
                    onClick = onRestore,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Import settings", color = ElectricCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    if (showSaveProfile) {
        AlertDialog(
            onDismissRequest = { showSaveProfile = false },
            title = { Text("Save driver profile", fontFamily = FontFamily.Monospace, color = Color.White) },
            text = {
                Column {
                    Text(
                        "Stores the current units, Drive Mode threshold, visible pages and default page.",
                        color = SecondaryText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it.take(24) },
                        label = { Text("Profile name", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveProfile = false
                        if (profileName.isNotBlank()) onSaveProfile(profileName.trim())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                ) {
                    Text("Save", color = Color(0xFF05070B), fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showSaveProfile = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Text("Cancel", color = Color.LightGray, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = Color(0xFF0F1722)
        )
    }
}

/** Small holder so the profile chips can be built in a list without a nested composable scope. */
private class SettingsProfileChip(
    val name: String,
    val selected: Boolean,
    val onClick: () -> Unit
) {
    @Composable
    fun Render() {
        Surface(
            modifier = Modifier.clickable { onClick() },
            color = if (selected) ElectricCyan.copy(alpha = 0.18f) else Color(0xFF131D2A),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (selected) ElectricCyan else Color(0xFF1E293B))
        ) {
            Text(
                name,
                color = if (selected) ElectricCyan else SecondaryText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun StepperRow(
    title: String,
    subtitle: String,
    value: String,
    onDec: () -> Unit,
    onInc: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(subtitle, color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDec, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $title", tint = ElectricCyan, modifier = Modifier.size(16.dp))
            }
            Text(
                value,
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(64.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = onInc, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Increase $title", tint = ElectricCyan, modifier = Modifier.size(16.dp))
            }
        }
    }
}
