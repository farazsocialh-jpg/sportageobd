package com.sportage.obd.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.app.Application
import com.sportage.obd.DashboardTheme
import com.sportage.obd.VehicleTelemetry
import com.sportage.obd.viewmodel.DashboardViewModel

@Composable
fun MainDashboardContainer(viewModel: DashboardViewModel? = null) {
    val context = LocalContext.current
    val resolvedViewModel = viewModel ?: remember { DashboardViewModel(context.applicationContext as Application) }
    val telemetry by resolvedViewModel.telemetry.collectAsState()
    val theme by resolvedViewModel.currentTheme.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF090A0F))) {
        AnimatedContent(
            targetState = theme,
            transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
            label = "ThemeSwitcher"
        ) { targetTheme ->
            when (targetTheme) {
                DashboardTheme.ORIGINAL -> OriginalHomeScreen(telemetry)
                DashboardTheme.MODULAR_CARD -> ModularCardDashboard(telemetry)
                DashboardTheme.FUTURISTIC_COCKPIT -> FuturisticCockpitDashboard(telemetry)
            }
        }

        ThemePickerModal(
            currentTheme = theme,
            onThemeSelected = { resolvedViewModel.setTheme(it) }
        )
    }
}

@Composable
fun OriginalHomeScreen(
    telemetry: VehicleTelemetry,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0E)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Speed ${telemetry.speedKmh} km/h · RPM ${telemetry.rpm} · ${telemetry.currentGear}",
            color = Color(0xFFEAF5FA)
        )
    }
}

@Composable
fun ThemePickerModal(
    currentTheme: DashboardTheme,
    onThemeSelected: (DashboardTheme) -> Unit
) {
    val (show, setShow) = remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        TextButton(onClick = { setShow(true) }) {
            Text("Palette", color = Color(0xFF00E5FF))
        }
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { setShow(false) },
            title = { Text("Choose dashboard") },
            text = {
                androidx.compose.foundation.layout.Column {
                    DashboardTheme.entries.forEach { theme ->
                        TextButton(onClick = {
                            onThemeSelected(theme)
                            setShow(false)
                        }) {
                            Text(
                                text = theme.title + if (theme == currentTheme) "  ✓" else "",
                                color = if (theme == currentTheme) Color(0xFF00E5FF) else Color(0xFFEAF5FA)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { setShow(false) }) { Text("Close") }
            }
        )
    }
}