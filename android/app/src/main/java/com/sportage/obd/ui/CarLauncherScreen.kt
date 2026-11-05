package com.sportage.obd.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.obd.DtcSeverity
import com.sportage.obd.service.ActiveAlert
import com.sportage.obd.service.AlertSeverity
import com.sportage.obd.service.AlertThresholds
import com.sportage.obd.ui.theme.*
import com.sportage.obd.viewmodel.ObdViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Desktop UA: the Waze embed only renders for desktop-class clients; the mobile UA gets an app wall. */
private const val WAZE_EMBED_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

/** Mobile Chrome UA for the local Leaflet map so tiles are served at the right scale. */
private const val MOBILE_MAP_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

/**
 * Waze live-map URL that actually renders inside a WebView. The /live-map/directions page
 * redirects to the Play Store / app wall on a mobile UA, which is what produced a blank surface.
 */
private fun wazeLiveMapUrl(lat: Double, lon: Double): String =
    "https://embed.waze.com/iframe?zoom=15&lat=$lat&lon=$lon&pin=1&ct=livemap&locale=en"

data class RadioStation(val freq: Float, val name: String, val genre: String)

val SPORTAGE_RADIO_PRESETS = listOf(
    RadioStation(91.7f, "QBS Radio", "English Hits"),
    RadioStation(94.0f, "Al Rayyan FM", "Arabic Culture"),
    RadioStation(97.5f, "Doha Hit FM", "Top 40"),
    RadioStation(101.4f, "Sportage Drive", "Automotive Stereo"),
    RadioStation(104.8f, "BBC World Service", "Global News"),
    RadioStation(107.6f, "Oryx FM", "French & Jazz")
)

/**
 * Bulletproof Waze & Navigation launcher for Android 11+ (API 30-35)
 */
fun launchWazeApp(context: Context, lat: Double? = null, lon: Double? = null, query: String? = null) {
    val pm = context.packageManager

    // 1. Direct package launch if Waze is installed
    try {
        val launchIntent = pm.getLaunchIntentForPackage("com.waze")
        if (launchIntent != null) {
            val deepLinkUri = when {
                !query.isNullOrBlank() -> Uri.parse("waze://?q=${Uri.encode(query)}&navigate=yes")
                lat != null && lon != null -> Uri.parse("waze://?ll=$lat,$lon&navigate=yes")
                else -> Uri.parse("waze://")
            }
            val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
                setPackage("com.waze")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return
            }
        }
    } catch (_: Exception) {}

    // 2. Generic waze:// URI
    try {
        val uri = when {
            !query.isNullOrBlank() -> Uri.parse("waze://?q=${Uri.encode(query)}&navigate=yes")
            lat != null && lon != null -> Uri.parse("waze://?ll=$lat,$lon&navigate=yes")
            else -> Uri.parse("waze://")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return
    } catch (_: Exception) {}

    // 3. Fallback to Google Maps navigation if available
    try {
        val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(
            if (!query.isNullOrBlank()) "google.navigation:q=${Uri.encode(query)}"
            else "geo:${lat ?: 25.2854},${lon ?: 51.5310}?z=15"
        )).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (mapsIntent.resolveActivity(pm) != null) {
            context.startActivity(mapsIntent)
            return
        }
    } catch (_: Exception) {}

    // 4. Web browser fallback with Waze Live navigation URL
    try {
        val webUri = when {
            !query.isNullOrBlank() -> Uri.parse("https://waze.com/ul?q=${Uri.encode(query)}&navigate=yes")
            lat != null && lon != null -> Uri.parse("https://waze.com/ul?ll=$lat,$lon&navigate=yes")
            else -> Uri.parse("https://waze.com/ul")
        }
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(webIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Please install Waze from Google Play Store", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun CarLauncherScreen(
    connectionState: ConnectionState,
    measuredHz: Float? = null,
    speed: Int?,
    rpm: Int?,
    coolant: Int?,
    intakeTemp: Int?,
    fuelLevel: Int?,
    throttle: Float?,
    load: Float?,
    voltage: Float?,
    gear: Int,
    speedHistory: List<Pair<Long, Int>>,
    rpmHistory: List<Pair<Long, Int>>,
    currentLat: Double = 25.2854,
    currentLon: Double = 51.5310,
    activePage: Int,
    onNavigatePage: (Int) -> Unit,
    onOpenBluetoothClick: () -> Unit,
    dtcCodes: List<DtcItem>,
    isScanningDtc: Boolean,
    onScanDtc: () -> Unit,
    onClearDtc: () -> Unit,
    tripDistanceKm: Double,
    tripDurationSeconds: Long,
    tripMaxSpeed: Int,
    tripAvgSpeed: Int,
    onResetTrip: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    isDemoMode: Boolean,
    onToggleDemoMode: (Boolean) -> Unit,
    isPollingPaused: Boolean = false,
    onTogglePausePolling: () -> Unit = {},
    onOpenAppDrawer: () -> Unit = {},
    viewModel: ObdViewModel? = null,
    autoNightDim: Boolean = true,
    nightDimLevel: Float = 0.55f,
    onSetAutoNightDim: (Boolean) -> Unit = {},
    onSetNightDimLevel: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp
    val activeAlerts = viewModel?.activeAlerts?.collectAsState()?.value ?: emptyList()

    var speedUnit by remember { mutableStateOf("km/h") }
    val displaySpeed = if (speed != null) {
        if (speedUnit == "mph") (speed * 0.621371).toInt() else speed
    } else null

    // Radio State
    var radioFreq by remember { mutableStateOf(101.4f) }
    var radioStationName by remember { mutableStateOf("Sportage Drive FM") }
    var isMediaPlaying by remember { mutableStateOf(true) }

    fun sendMediaKey(keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {}
    }

    fun launchLocalMusicApp() {
        val candidates = listOf(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
            Intent(Intent.ACTION_VIEW).apply { type = "audio/*" },
            Intent("android.intent.action.MUSIC_PLAYER")
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    fun launchHeadUnitRadioApp() {
        val radioPackages = listOf(
            "com.ts.main.radio",
            "com.syu.radio",
            "com.microntek.radio",
            "com.android.fmradio"
        )
        for (pkg in radioPackages) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        try {
            val generic = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(generic)
        } catch (_: Exception) {}
    }

    val currentTime = remember {
        derivedStateOf {
            SimpleDateFormat("HH:mm · EEE, MMM d", Locale.getDefault()).format(Date())
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isLandscape = screenWidth >= screenHeight
        val aspectRatio = if (screenHeight.value > 0) screenWidth.value / screenHeight.value else 1.7f
        val isUltraWide = isLandscape && aspectRatio >= 2.0f
        val isCompactLandscape = isLandscape && screenWidth < 900.dp

        val dockWidth = when {
            isCompactLandscape -> 52.dp
            isUltraWide -> 64.dp
            else -> 58.dp
        }

        if (isLandscape) {
            // LANDSCAPE: Adaptive Left Slim Dock + Cockpit Viewport (Head Units 1024x600, 1280x720, 1920x720 & Phone Landscape)
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(dockWidth)
                        .fillMaxHeight()
                        .background(Color(0xFF070B12))
                        .border(BorderStroke(1.dp, Color(0xFF1E293B)))
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DockButton(icon = Icons.Default.Home, label = "Home", isSelected = activePage == 0, accentColor = ElectricCyan, onClick = { onNavigatePage(0) })
                        DockButton(icon = Icons.Default.Speed, label = "Gauges", isSelected = activePage == 1, accentColor = ElectricCyan, onClick = { onNavigatePage(1) })
                        DockButton(icon = Icons.Default.WarningAmber, label = "Diag", isSelected = activePage == 2, accentColor = if (dtcCodes.isNotEmpty()) CriticalRed else AmberWarning, onClick = { onNavigatePage(2) })
                        DockButton(icon = Icons.Default.Navigation, label = "Trip", isSelected = activePage == 3, accentColor = TealAccent, onClick = { onNavigatePage(3) })
                        DockButton(icon = Icons.Default.Radio, label = "Media", isSelected = activePage == 4, accentColor = Color(0xFF8B5CF6), onClick = { onNavigatePage(4) })
                        DockButton(icon = Icons.Default.Apps, label = "Apps", isSelected = false, accentColor = ElectricCyan, onClick = onOpenAppDrawer)
                        DockButton(icon = Icons.Default.Settings, label = "Setup", isSelected = activePage == 5, accentColor = Color.LightGray, onClick = { onNavigatePage(5) })
                    }

                    IconButton(
                        onClick = onOpenBluetoothClick,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                when (connectionState) {
                                    is ConnectionState.Connected -> TealAccent.copy(alpha = 0.2f)
                                    is ConnectionState.Connecting -> ElectricCyan.copy(alpha = 0.2f)
                                    is ConnectionState.Error -> CriticalRed.copy(alpha = 0.2f)
                                    else -> if (isDemoMode) AmberWarning.copy(alpha = 0.2f) else Color(0xFF131D2A)
                                },
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                when (connectionState) {
                                    is ConnectionState.Connected -> TealAccent
                                    is ConnectionState.Connecting -> ElectricCyan
                                    is ConnectionState.Error -> CriticalRed
                                    else -> if (isDemoMode) AmberWarning else Color(0xFF1E293B)
                                },
                                RoundedCornerShape(10.dp)
                            )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "Bluetooth OBD2",
                                tint = when (connectionState) {
                                    is ConnectionState.Connected -> TealAccent
                                    is ConnectionState.Connecting -> ElectricCyan
                                    is ConnectionState.Error -> CriticalRed
                                    else -> if (isDemoMode) AmberWarning else Color.Gray
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when (connectionState) {
                                    is ConnectionState.Connected -> if (measuredHz != null) "${measuredHz.toInt()}Hz" else "OBD"
                                    is ConnectionState.Connecting -> "PAIR"
                                    else -> if (isDemoMode) "DEMO" else "BT"
                                },
                                color = when (connectionState) {
                                    is ConnectionState.Connected -> TealAccent
                                    is ConnectionState.Connecting -> ElectricCyan
                                    is ConnectionState.Error -> CriticalRed
                                    else -> if (isDemoMode) AmberWarning else Color.Gray
                                },
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Cockpit Viewport
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TopCockpitStatusStrip(
                        currentLat = currentLat,
                        currentLon = currentLon,
                        currentTime = currentTime.value,
                        connectionState = connectionState,
                        measuredHz = measuredHz,
                        isDemoMode = isDemoMode,
                        onOpenBluetoothClick = onOpenBluetoothClick
                    )

                    ActiveAlertBanner(activeAlerts)

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (activePage) {
                            0 -> PermanentHomeSplitView(
                                isLandscape = true,
                                aspectRatio = aspectRatio,
                                currentLat = currentLat,
                                currentLon = currentLon,
                                speed = displaySpeed,
                                speedUnit = speedUnit,
                                onToggleSpeedUnit = { speedUnit = if (speedUnit == "km/h") "mph" else "km/h" },
                                rpm = rpm,
                                coolant = coolant,
                                voltage = voltage,
                                load = load,
                                throttle = throttle,
                                gear = gear,
                                connectionState = connectionState,
                                measuredHz = measuredHz,
                                isDemoMode = isDemoMode,
                                onOpenFullWaze = { launchWazeApp(context, currentLat, currentLon) }
                            )
                            1 -> GaugesAndGraphsPage(
                                speed = displaySpeed,
                                speedUnit = speedUnit,
                                rpm = rpm,
                                coolant = coolant,
                                intakeTemp = intakeTemp,
                                fuelLevel = fuelLevel,
                                throttle = throttle,
                                load = load,
                                voltage = voltage,
                                gear = gear,
                                speedHistory = speedHistory,
                                rpmHistory = rpmHistory,
                                viewModel = viewModel
                            )
                            2 -> DiagnosticsPage(
                                dtcCodes = dtcCodes,
                                isScanning = isScanningDtc,
                                onScan = onScanDtc,
                                onClear = onClearDtc,
                                connectionState = connectionState,
                                viewModel = viewModel
                            )
                            3 -> TripComputerPage(
                                distanceKm = tripDistanceKm,
                                durationSeconds = tripDurationSeconds,
                                maxSpeed = tripMaxSpeed,
                                avgSpeed = tripAvgSpeed,
                                currentSpeed = displaySpeed ?: 0,
                                speedUnit = speedUnit,
                                currentLat = currentLat,
                                currentLon = currentLon,
                                onReset = onResetTrip,
                                viewModel = viewModel
                            )
                            4 -> MediaRadioPage(
                                radioFreq = radioFreq,
                                onFreqChange = { radioFreq = it },
                                radioStationName = radioStationName,
                                onStationChange = { name, freq ->
                                    radioStationName = name
                                    radioFreq = freq
                                },
                                isMediaPlaying = isMediaPlaying,
                                onTogglePlayPause = {
                                    isMediaPlaying = !isMediaPlaying
                                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                },
                                onPrev = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                                onNext = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) },
                                onLaunchNativeRadio = { launchHeadUnitRadioApp() },
                                onLaunchLocalMusic = { launchLocalMusicApp() }
                            )
                            5 -> SettingsPage(
                                speedUnit = speedUnit,
                                onToggleSpeedUnit = { speedUnit = if (speedUnit == "km/h") "mph" else "km/h" },
                                onSetDefaultLauncher = onSetDefaultLauncher,
                                isDemoMode = isDemoMode,
                                onToggleDemoMode = onToggleDemoMode,
                                onOpenBluetoothSettings = {
                                    try {
                                        context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    } catch (_: Exception) {}
                                },
                                isPollingPaused = isPollingPaused,
                                onTogglePausePolling = onTogglePausePolling,
                                onOpenAppDrawer = onOpenAppDrawer,
                                viewModel = viewModel,
                                autoNightDim = autoNightDim,
                                nightDimLevel = nightDimLevel,
                                onSetAutoNightDim = onSetAutoNightDim,
                                onSetNightDimLevel = onSetNightDimLevel
                            )
                        }
                    }
                }
            }
        } else {
            // PORTRAIT: Full-width Top Status Strip + Viewport + Bottom Navigation Bar (e.g. Samsung Galaxy S24 Ultra Phone Mount)
            Column(modifier = Modifier.fillMaxSize()) {
                TopCockpitStatusStrip(
                    currentLat = currentLat,
                    currentLon = currentLon,
                    currentTime = currentTime.value,
                    connectionState = connectionState,
                    measuredHz = measuredHz,
                    isDemoMode = isDemoMode,
                    onOpenBluetoothClick = onOpenBluetoothClick
                )

                ActiveAlertBanner(activeAlerts)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (activePage) {
                        0 -> PermanentHomeSplitView(
                            isLandscape = false,
                            aspectRatio = aspectRatio,
                            currentLat = currentLat,
                            currentLon = currentLon,
                            speed = displaySpeed,
                            speedUnit = speedUnit,
                            onToggleSpeedUnit = { speedUnit = if (speedUnit == "km/h") "mph" else "km/h" },
                            rpm = rpm,
                            coolant = coolant,
                            voltage = voltage,
                            load = load,
                            throttle = throttle,
                            gear = gear,
                            connectionState = connectionState,
                            measuredHz = measuredHz,
                            isDemoMode = isDemoMode,
                            onOpenFullWaze = { launchWazeApp(context, currentLat, currentLon) }
                        )
                        1 -> GaugesAndGraphsPage(
                            speed = displaySpeed,
                            speedUnit = speedUnit,
                            rpm = rpm,
                            coolant = coolant,
                            intakeTemp = intakeTemp,
                            fuelLevel = fuelLevel,
                            throttle = throttle,
                            load = load,
                            voltage = voltage,
                            gear = gear,
                            speedHistory = speedHistory,
                            rpmHistory = rpmHistory,
                            viewModel = viewModel
                        )
                        2 -> DiagnosticsPage(
                            dtcCodes = dtcCodes,
                            isScanning = isScanningDtc,
                            onScan = onScanDtc,
                            onClear = onClearDtc,
                            connectionState = connectionState,
                            viewModel = viewModel
                        )
                        3 -> TripComputerPage(
                            distanceKm = tripDistanceKm,
                            durationSeconds = tripDurationSeconds,
                            maxSpeed = tripMaxSpeed,
                            avgSpeed = tripAvgSpeed,
                            currentSpeed = displaySpeed ?: 0,
                            speedUnit = speedUnit,
                            currentLat = currentLat,
                            currentLon = currentLon,
                            onReset = onResetTrip,
                            viewModel = viewModel
                        )
                        4 -> MediaRadioPage(
                            radioFreq = radioFreq,
                            onFreqChange = { radioFreq = it },
                            radioStationName = radioStationName,
                            onStationChange = { name, freq ->
                                radioStationName = name
                                radioFreq = freq
                            },
                            isMediaPlaying = isMediaPlaying,
                            onTogglePlayPause = {
                                isMediaPlaying = !isMediaPlaying
                                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                            },
                            onPrev = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                            onNext = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) },
                            onLaunchNativeRadio = { launchHeadUnitRadioApp() },
                            onLaunchLocalMusic = { launchLocalMusicApp() }
                        )
                        5 -> SettingsPage(
                            speedUnit = speedUnit,
                            onToggleSpeedUnit = { speedUnit = if (speedUnit == "km/h") "mph" else "km/h" },
                            onSetDefaultLauncher = onSetDefaultLauncher,
                            isDemoMode = isDemoMode,
                            onToggleDemoMode = onToggleDemoMode,
                            onOpenBluetoothSettings = {
                                try {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (_: Exception) {}
                            },
                            isPollingPaused = isPollingPaused,
                            onTogglePausePolling = onTogglePausePolling,
                            onOpenAppDrawer = onOpenAppDrawer,
                            viewModel = viewModel,
                            autoNightDim = autoNightDim,
                            nightDimLevel = nightDimLevel,
                            onSetAutoNightDim = onSetAutoNightDim,
                            onSetNightDimLevel = onSetNightDimLevel
                        )
                    }
                }

                // Bottom Navigation Dock for Full-Width Phone Operation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .background(Color(0xFF070B12))
                        .border(BorderStroke(1.dp, Color(0xFF1E293B)))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockButton(icon = Icons.Default.Home, label = "Home", isSelected = activePage == 0, accentColor = ElectricCyan, onClick = { onNavigatePage(0) })
                    DockButton(icon = Icons.Default.Speed, label = "Gauges", isSelected = activePage == 1, accentColor = ElectricCyan, onClick = { onNavigatePage(1) })
                    DockButton(icon = Icons.Default.WarningAmber, label = "Diag", isSelected = activePage == 2, accentColor = if (dtcCodes.isNotEmpty()) CriticalRed else AmberWarning, onClick = { onNavigatePage(2) })
                    DockButton(icon = Icons.Default.Navigation, label = "Trip", isSelected = activePage == 3, accentColor = TealAccent, onClick = { onNavigatePage(3) })
                    DockButton(icon = Icons.Default.Radio, label = "Media", isSelected = activePage == 4, accentColor = Color(0xFF8B5CF6), onClick = { onNavigatePage(4) })
                    DockButton(icon = Icons.Default.Apps, label = "Apps", isSelected = false, accentColor = ElectricCyan, onClick = onOpenAppDrawer)
                    DockButton(icon = Icons.Default.Settings, label = "Setup", isSelected = activePage == 5, accentColor = Color.LightGray, onClick = { onNavigatePage(5) })
                    IconButton(
                        onClick = onOpenBluetoothClick,
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                when (connectionState) {
                                    is ConnectionState.Connected -> TealAccent.copy(alpha = 0.2f)
                                    is ConnectionState.Connecting -> ElectricCyan.copy(alpha = 0.2f)
                                    is ConnectionState.Error -> CriticalRed.copy(alpha = 0.2f)
                                    else -> if (isDemoMode) AmberWarning.copy(alpha = 0.2f) else Color(0xFF131D2A)
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                when (connectionState) {
                                    is ConnectionState.Connected -> TealAccent
                                    is ConnectionState.Connecting -> ElectricCyan
                                    is ConnectionState.Error -> CriticalRed
                                    else -> if (isDemoMode) AmberWarning else Color(0xFF1E293B)
                                },
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Bluetooth OBD2",
                            tint = when (connectionState) {
                                is ConnectionState.Connected -> TealAccent
                                is ConnectionState.Connecting -> ElectricCyan
                                is ConnectionState.Error -> CriticalRed
                                else -> if (isDemoMode) AmberWarning else Color.Gray
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// HELPER: ACTIVE WARNING BANNER (threshold alerts)
// =========================================================================
@Composable
fun ActiveAlertBanner(activeAlerts: List<ActiveAlert>) {
    if (activeAlerts.isEmpty()) return
    val critical = activeAlerts.any { it.severity == AlertSeverity.CRITICAL }
    val color = if (critical) CriticalRed else AmberWarning
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = activeAlerts.joinToString("  ·  ") { it.message },
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// =========================================================================
// HELPER: TOP COCKPIT STATUS STRIP
// =========================================================================
@Composable
fun TopCockpitStatusStrip(
    currentLat: Double,
    currentLon: Double,
    currentTime: String,
    connectionState: ConnectionState,
    measuredHz: Float?,
    isDemoMode: Boolean,
    onOpenBluetoothClick: () -> Unit,
    isPollingPaused: Boolean = false,
    onTogglePausePolling: () -> Unit = {},
    onOpenAppDrawer: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .background(Color(0xFF090D14))
            .border(BorderStroke(1.dp, Color(0xFF1E293B)))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "KIA SPORTAGE 2.0 MPI AWD",
                color = ElectricCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(text = "·", color = Color.DarkGray, fontSize = 11.sp)
            Text(
                text = currentTime,
                color = SecondaryText,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            if (isDemoMode) {
                Surface(
                    color = AmberWarning.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, AmberWarning)
                ) {
                    Text(
                        text = "⚠️ BENCH DEMO ACTIVE",
                        color = AmberWarning,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(6.dp).background(TealAccent, CircleShape))
                Text(
                    text = "GPS: ${"%.4f".format(currentLat)}°N, ${"%.4f".format(currentLon)}°E",
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Surface(
                modifier = Modifier.clickable { onOpenBluetoothClick() },
                color = when (connectionState) {
                    is ConnectionState.Connected -> TealAccent.copy(alpha = 0.15f)
                    is ConnectionState.Connecting -> ElectricCyan.copy(alpha = 0.15f)
                    is ConnectionState.Error -> CriticalRed.copy(alpha = 0.15f)
                    else -> if (isDemoMode) AmberWarning.copy(alpha = 0.15f) else Color(0xFF1E293B)
                },
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(
                    1.dp,
                    when (connectionState) {
                        is ConnectionState.Connected -> TealAccent
                        is ConnectionState.Connecting -> ElectricCyan
                        is ConnectionState.Error -> CriticalRed
                        else -> if (isDemoMode) AmberWarning else Color.Transparent
                    }
                )
            ) {
                Text(
                    text = when (connectionState) {
                        is ConnectionState.Connected -> if (measuredHz != null) "ELM327 CONNECTED (${"%.1f".format(measuredHz)}Hz)" else "ELM327 CONNECTED"
                        is ConnectionState.Connecting -> "PAIRING ELM327..."
                        is ConnectionState.Error -> "ADAPTER ERROR (TAP TO SCAN)"
                        else -> if (isDemoMode) "BENCH DEMO ACTIVE" else "TAP TO CONNECT OBD"
                    },
                    color = when (connectionState) {
                        is ConnectionState.Connected -> TealAccent
                        is ConnectionState.Connecting -> ElectricCyan
                        is ConnectionState.Error -> CriticalRed
                        else -> if (isDemoMode) AmberWarning else Color.LightGray
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Pause OBD Polling Toggle (for Engine Off / Battery Saving)
            Surface(
                modifier = Modifier.clickable { onTogglePausePolling() },
                color = if (isPollingPaused) AmberWarning.copy(alpha = 0.2f) else Color(0xFF1E293B),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, if (isPollingPaused) AmberWarning else Color.Transparent)
            ) {
                Text(
                    text = if (isPollingPaused) "OBD PAUSED (IGNITION OFF)" else "PAUSE OBD",
                    color = if (isPollingPaused) AmberWarning else Color.LightGray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // App Drawer Quick Button
            Surface(
                modifier = Modifier.clickable { onOpenAppDrawer() },
                color = ElectricCyan.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Apps, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(11.dp))
                    Text("APPS", color = ElectricCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// =========================================================================
// SUB-VIEW: PERMANENT HOME SPLIT SCREEN (Dominant Live Map + Live OBD Gauges)
// =========================================================================
// SUB-VIEW: PERMANENT HOME SPLIT SCREEN (Dominant Live Map + Live OBD Gauges)
// =========================================================================
@Composable
fun PermanentHomeSplitView(
    isLandscape: Boolean,
    aspectRatio: Float = 1.7f,
    currentLat: Double,
    currentLon: Double,
    speed: Int?,
    speedUnit: String,
    onToggleSpeedUnit: () -> Unit,
    rpm: Int?,
    coolant: Int?,
    voltage: Float?,
    load: Float?,
    throttle: Float?,
    gear: Int,
    connectionState: ConnectionState,
    measuredHz: Float?,
    isDemoMode: Boolean,
    onOpenFullWaze: () -> Unit
) {
    if (isLandscape) {
        // Landscape: Adaptive Dominant Live Map Pane (approx 58-64%) + Persistent OBD Pane (approx 36-42%)
        val mapWeight = if (aspectRatio >= 2.0f) 0.64f else 0.58f
        val obdWeight = 1.0f - mapWeight

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(mapWeight).fillMaxHeight()) {
                LiveMapSurface(
                    currentLat = currentLat,
                    currentLon = currentLon,
                    speed = speed,
                    speedUnit = speedUnit,
                    onOpenFullWaze = onOpenFullWaze
                )
            }

            Box(modifier = Modifier.weight(obdWeight).fillMaxHeight()) {
                AuthoritativeObdPane(
                    speed = speed,
                    speedUnit = speedUnit,
                    onToggleSpeedUnit = onToggleSpeedUnit,
                    rpm = rpm,
                    coolant = coolant,
                    voltage = voltage,
                    load = load,
                    throttle = throttle,
                    gear = gear,
                    connectionState = connectionState,
                    measuredHz = measuredHz,
                    isDemoMode = isDemoMode
                )
            }
        }
    } else {
        // Portrait (e.g. Samsung Galaxy S24 Ultra): Top Map (44%) + Bottom OBD Telemetry (56%)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(0.44f).fillMaxWidth()) {
                LiveMapSurface(
                    currentLat = currentLat,
                    currentLon = currentLon,
                    speed = speed,
                    speedUnit = speedUnit,
                    onOpenFullWaze = onOpenFullWaze
                )
            }

            Box(modifier = Modifier.weight(0.56f).fillMaxWidth()) {
                AuthoritativeObdPane(
                    speed = speed,
                    speedUnit = speedUnit,
                    onToggleSpeedUnit = onToggleSpeedUnit,
                    rpm = rpm,
                    coolant = coolant,
                    voltage = voltage,
                    load = load,
                    throttle = throttle,
                    gear = gear,
                    connectionState = connectionState,
                    measuredHz = measuredHz,
                    isDemoMode = isDemoMode
                )
            }
        }
    }
}

// =========================================================================
// SUB-VIEW: LIVE MAP SURFACE (Non-Reloading Dynamic Map & Full Waze Launcher)
// =========================================================================
@Composable
fun LiveMapSurface(
    currentLat: Double,
    currentLon: Double,
    speed: Int?,
    speedUnit: String,
    onOpenFullWaze: () -> Unit
) {
    var mapProvider by remember { mutableStateOf("OSM_DARK") } // "OSM_DARK" or "WAZE_EMBED"
    var currentLoadedProvider by remember { mutableStateOf<String?>(null) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var speedAlertThreshold by remember { mutableStateOf(100) } // Configurable Speed Alert: 80, 100, 120, 140, 0 (OFF)

    // Base Leaflet HTML rendered once - exposes updateVehicleLocation function for zero-flicker pan/marker
    val leafletHtml = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <script>
                // Fallback CDN so a blocked unpkg request does not leave a blank map surface
                if (!window.L) {
                    document.write('<script src="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js"><\/script>');
                }
            </script>
            <style>
                body, html, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #070B12; }
                .car-marker {
                    width: 22px; height: 22px; border-radius: 50%;
                    background: #00E5FF; border: 3px solid #FFFFFF;
                    box-shadow: 0 0 14px #00E5FF;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = null;
                var carMarker = null;
                if (typeof L === 'undefined') {
                    document.getElementById('map').innerHTML =
                        '<div style="color:#94A3B8;font:12px monospace;padding:18px;text-align:center">' +
                        'Map library unavailable - check the head unit internet connection.<br>' +
                        'GPS: ' + '$currentLat' + ', ' + '$currentLon' + '</div>';
                } else {
                map = L.map('map', { zoomControl: false, attributionControl: false })
                    .setView([$currentLat, $currentLon], 15);
                L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
                    maxZoom: 19, subdomains: 'abcd'
                }).addTo(map);
                var carIcon = L.divIcon({ className: 'car-marker', iconSize: [22, 22] });
                carMarker = L.marker([$currentLat, $currentLon], { icon: carIcon }).addTo(map);
                }

                window.updateVehicleLocation = function(lat, lon) {
                    if (map) {
                        map.panTo([lat, lon], { animate: true, duration: 1.0 });
                    }
                    if (carMarker) {
                        carMarker.setLatLng([lat, lon]);
                    }
                };
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Map Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E1722))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, tint = Color(0xFF33CCFF), modifier = Modifier.size(16.dp))
                    Text(
                        text = if (mapProvider == "OSM_DARK") "AUTOMOTIVE LIVE MAP" else "WAZE TRAFFIC EMBED",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Box(modifier = Modifier.size(6.dp).background(TealAccent, CircleShape))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Toggle Provider
                    Surface(
                        modifier = Modifier.clickable {
                            mapProvider = if (mapProvider == "OSM_DARK") "WAZE_EMBED" else "OSM_DARK"
                        },
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (mapProvider == "OSM_DARK") "Switch: Waze" else "Switch: Dark Map",
                            color = ElectricCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // OPEN FULL WAZE (Primary Non-Negotiable Action)
                    Surface(
                        modifier = Modifier.clickable { onOpenFullWaze() },
                        color = Color(0xFF33CCFF),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFF05070B), modifier = Modifier.size(11.dp))
                            Text(
                                text = "Open Full Waze",
                                color = Color(0xFF05070B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Interactive Map View Container with Overlays
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                setGeolocationEnabled(true)
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                cacheMode = WebSettings.LOAD_DEFAULT
                                allowFileAccess = true
                                allowContentAccess = true
                                userAgentString = if (mapProvider == "OSM_DARK") MOBILE_MAP_USER_AGENT else WAZE_EMBED_USER_AGENT
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                                    callback?.invoke(origin, true, false)
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (mapProvider == "OSM_DARK") mapError = null
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true && mapProvider != "OSM_DARK") {
                                        mapError = "Waze map unavailable (${error?.description ?: "network error"}). Showing offline map."
                                        mapProvider = "OSM_DARK"
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: android.webkit.WebResourceResponse?
                                ) {
                                    val status = errorResponse?.statusCode ?: 0
                                    if (request?.isForMainFrame == true && status >= 400 && mapProvider != "OSM_DARK") {
                                        mapError = "Waze map blocked (HTTP $status). Showing offline map."
                                        mapProvider = "OSM_DARK"
                                    }
                                }
                            }

                            if (mapProvider == "OSM_DARK") {
                                loadDataWithBaseURL("https://unpkg.com", leafletHtml, "text/html", "UTF-8", null)
                            } else {
                                loadUrl(wazeLiveMapUrl(currentLat, currentLon))
                            }
                            currentLoadedProvider = mapProvider
                        }
                    },
                    update = { webView ->
                        if (mapProvider != currentLoadedProvider) {
                            currentLoadedProvider = mapProvider
                            if (mapProvider == "OSM_DARK") {
                                webView.settings.userAgentString = MOBILE_MAP_USER_AGENT
                                webView.loadDataWithBaseURL("https://unpkg.com", leafletHtml, "text/html", "UTF-8", null)
                            } else {
                                webView.settings.userAgentString = WAZE_EMBED_USER_AGENT
                                webView.loadUrl(wazeLiveMapUrl(currentLat, currentLon))
                            }
                        } else if (mapProvider == "OSM_DARK") {
                            // Smooth marker update without reloading HTML or network requests!
                            webView.evaluateJavascript(
                                "if (typeof window.updateVehicleLocation === 'function') { window.updateVehicleLocation($currentLat, $currentLon); }",
                                null
                            )
                        }
                    }
                )

                // Non-blocking map notice replaced the old silent blank WebView
                mapError?.let { message ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                        color = AmberWarning.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = message,
                            color = Color(0xFF05070B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Floating Road Speed Alert Sign & Speed Badge (Top Left of Map)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Configurable Speed Alert Sign (Tap cycles threshold: 80 -> 100 -> 120 -> 140 -> OFF)
                    Surface(
                        modifier = Modifier
                            .clickable {
                                speedAlertThreshold = when (speedAlertThreshold) {
                                    80 -> 100
                                    100 -> 120
                                    120 -> 140
                                    140 -> 0
                                    else -> 80
                                }
                            },
                        color = Color.White,
                        shape = CircleShape,
                        border = BorderStroke(3.dp, CriticalRed)
                    ) {
                        Box(
                            modifier = Modifier.size(38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (speedAlertThreshold > 0) "$speedAlertThreshold" else "--",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // GPS Speed Overlay
                    Surface(
                        color = Color(0xD9080C14),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Text(
                            text = "${speed ?: "--"} $speedUnit",
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    // Overspeed Warning Pill if exceeded
                    val isOverspeed = speed != null && speedAlertThreshold > 0 && speed > speedAlertThreshold
                    if (isOverspeed) {
                        Surface(
                            color = CriticalRed,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "⚠️ SPEED ALERT!",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Floating Action Button: Open Turn-by-Turn in Waze
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clickable { onOpenFullWaze() },
                    color = Color(0xFF33CCFF),
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = Color(0xFF05070B), modifier = Modifier.size(14.dp))
                        Text(
                            text = "START IN WAZE",
                            color = Color(0xFF05070B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Bottom Trip Status Line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E1722))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GPS ACTIVE · CONTINUOUS CAR MAPPING",
                    color = SecondaryText,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${"%.4f".format(currentLat)}, ${"%.4f".format(currentLon)}",
                    color = ElectricCyan,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// =========================================================================
// SUB-VIEW: AUTHORITATIVE PRIMARY OBD TELEMETRY PANE
// =========================================================================
@Composable
fun AuthoritativeObdPane(
    speed: Int?,
    speedUnit: String,
    onToggleSpeedUnit: () -> Unit,
    rpm: Int?,
    coolant: Int?,
    voltage: Float?,
    load: Float?,
    throttle: Float?,
    gear: Int,
    connectionState: ConnectionState,
    measuredHz: Float? = null,
    isDemoMode: Boolean
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status & Gear Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "LIVE TELEMETRY",
                        color = SecondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (measuredHz != null) {
                        Text(
                            text = "(${ "%.1f".format(measuredHz) } Hz)",
                            color = TealAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Gear Badge (Indicated as calculated ratio)
                    Surface(
                        color = Color(0xFF0E1722),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (gear == 0 || (speed ?: 0) < 5) "P / N" else "D$gear (EST.)",
                            color = TealAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Speed Unit Switch
                    Surface(
                        modifier = Modifier.clickable { onToggleSpeedUnit() },
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = speedUnit.uppercase(),
                            color = ElectricCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Prominent Bench Demo Warning Banner if active
            if (isDemoMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AmberWarning.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, AmberWarning)
                ) {
                    Text(
                        text = "⚠️ BENCH DEMO ACTIVE · SIMULATED CAN TELEMETRY",
                        color = AmberWarning,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .padding(vertical = 3.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                }
            }

            // Giant Speedometer Centerpiece (Stable container width so no number jumping)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = speed?.toString() ?: "--",
                    color = Color.White,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 64.sp
                )

                Text(
                    text = speedUnit.uppercase(),
                    color = ElectricCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Sweeping RPM bar
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "RPM: ${rpm ?: "--"}", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "6500 REDLINE", color = CriticalRed, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }

                LinearProgressIndicator(
                    progress = { ((rpm ?: 0) / 7000f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(6.dp),
                    color = if ((rpm ?: 0) > 6000) CriticalRed else ElectricCyan,
                    trackColor = Color(0xFF0B1017)
                )
            }

            // 4 Essential Live Sensor Tiles at Bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Coolant Temp
                SensorMiniTile(
                    title = "COOLANT",
                    value = if (coolant != null) "$coolant°C" else "--",
                    valueColor = if ((coolant ?: 0) > 105) CriticalRed else Color.White,
                    modifier = Modifier.weight(1f)
                )

                // Battery Voltage
                SensorMiniTile(
                    title = "BATTERY",
                    value = if (voltage != null) "${"%.1f".format(voltage)}V" else "--",
                    valueColor = TealAccent,
                    modifier = Modifier.weight(1f)
                )

                // Engine Load
                SensorMiniTile(
                    title = "LOAD",
                    value = if (load != null) "${load.toInt()}%" else "--",
                    valueColor = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )

                // Connection State
                SensorMiniTile(
                    title = "STATUS",
                    value = when (connectionState) {
                        is ConnectionState.Connected -> "10Hz"
                        is ConnectionState.Connecting -> "PAIR"
                        else -> if (isDemoMode) "DEMO" else "OFF"
                    },
                    valueColor = if (connectionState is ConnectionState.Connected) TealAccent else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SensorMiniTile(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF090D14),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = SecondaryText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// =========================================================================
// SUB-VIEW: GAUGES & REAL-TIME CANVAS GRAPHS PAGE
// =========================================================================
@Composable
fun GaugesAndGraphsPage(
    speed: Int?,
    speedUnit: String,
    rpm: Int?,
    coolant: Int?,
    intakeTemp: Int?,
    fuelLevel: Int?,
    throttle: Float?,
    load: Float?,
    voltage: Float?,
    gear: Int,
    speedHistory: List<Pair<Long, Int>>,
    rpmHistory: List<Pair<Long, Int>>,
    viewModel: ObdViewModel? = null
) {
    val liveTelemetry = viewModel?.telemetry?.collectAsState()?.value
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Real-Time Canvas Line Chart: Speed & RPM over time
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REAL-TIME ENGINE TELEMETRY GRAPH (60 SECONDS)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(ElectricCyan, RoundedCornerShape(2.dp)))
                            Text(text = "Speed: ${speed ?: "--"} $speedUnit", color = ElectricCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFF59E0B), RoundedCornerShape(2.dp)))
                            Text(text = "RPM: ${rpm ?: "--"}", color = Color(0xFFF59E0B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Canvas Graph
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF070B12), RoundedCornerShape(6.dp))
                        .padding(6.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    // Grid lines
                    for (i in 1..4) {
                        val y = h * (i / 5f)
                        drawLine(
                            color = Color(0xFF1A2634),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                    }

                    // Draw Speed Line (max 200 km/h)
                    if (speedHistory.size > 1) {
                        val path = Path()
                        speedHistory.forEachIndexed { idx, pair ->
                            val x = w * (idx.toFloat() / (speedHistory.size - 1))
                            val y = h - (h * (pair.second.coerceIn(0, 200) / 200f))
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color = ElectricCyan, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                    }

                    // Draw RPM Line (max 7000 RPM)
                    if (rpmHistory.size > 1) {
                        val rpmPath = Path()
                        rpmHistory.forEachIndexed { idx, pair ->
                            val x = w * (idx.toFloat() / (rpmHistory.size - 1))
                            val y = h - (h * (pair.second.coerceIn(0, 7000) / 7000f))
                            if (idx == 0) rpmPath.moveTo(x, y) else rpmPath.lineTo(x, y)
                        }
                        drawPath(rpmPath, color = Color(0xFFF59E0B), style = Stroke(width = 2f, cap = StrokeCap.Round))
                    }
                }
            }
        }

        // 8 Detailed Engine Sensors Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SensorCard(title = "ENGINE RPM", value = rpm?.toString() ?: "--", unit = "RPM", color = Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                SensorCard(title = "COOLANT TEMP", value = coolant?.toString() ?: "--", unit = "°C", color = Color.White, modifier = Modifier.weight(1f))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SensorCard(title = "INTAKE AIR TEMP", value = intakeTemp?.toString() ?: "--", unit = "°C", color = ElectricCyan, modifier = Modifier.weight(1f))
                SensorCard(title = "ENGINE LOAD", value = load?.let { "%.1f".format(it) } ?: "--", unit = "%", color = TealAccent, modifier = Modifier.weight(1f))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SensorCard(title = "THROTTLE POS", value = throttle?.let { "%.1f".format(it) } ?: "--", unit = "%", color = ElectricCyan, modifier = Modifier.weight(1f))
                SensorCard(title = "FUEL TANK LEVEL", value = fuelLevel?.toString() ?: "--", unit = "%", color = Color(0xFF10B981), modifier = Modifier.weight(1f))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SensorCard(title = "CONTROL VOLTAGE", value = voltage?.let { "%.1f".format(it) } ?: "--", unit = "V", color = TealAccent, modifier = Modifier.weight(1f))
                SensorCard(title = "GEAR POSITION", value = if (gear == 0) "P/N" else "D$gear", unit = "AUTO", color = ElectricCyan, modifier = Modifier.weight(1f))
            }
        }

        // Fuel & Air Metering Grid (MAF-based economy sensors)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.28f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SensorCard(title = "MAF AIR FLOW", value = liveTelemetry?.mafGs?.let { "%.1f".format(it) } ?: "--", unit = "g/s", color = ElectricCyan, modifier = Modifier.weight(1f))
                SensorCard(title = "TIMING ADVANCE", value = liveTelemetry?.timingAdvanceDeg?.let { "%.1f".format(it) } ?: "--", unit = "°BTDC", color = Color.White, modifier = Modifier.weight(1f))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SensorCard(title = "SHORT TERM TRIM", value = liveTelemetry?.shortTermFuelTrimPct?.let { "%+.1f".format(it) } ?: "--", unit = "%", color = if ((liveTelemetry?.shortTermFuelTrimPct ?: 0f) > 10f || (liveTelemetry?.shortTermFuelTrimPct ?: 0f) < -10f) CriticalRed else TealAccent, modifier = Modifier.weight(1f))
                SensorCard(title = "LONG TERM TRIM", value = liveTelemetry?.longTermFuelTrimPct?.let { "%+.1f".format(it) } ?: "--", unit = "%", color = if ((liveTelemetry?.longTermFuelTrimPct ?: 0f) > 10f || (liveTelemetry?.longTermFuelTrimPct ?: 0f) < -10f) CriticalRed else TealAccent, modifier = Modifier.weight(1f))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SensorCard(title = "INSTANT ECONOMY", value = liveTelemetry?.fuelEconomyL100?.let { "%.1f".format(it) } ?: "--", unit = "L/100km", color = Color(0xFF10B981), modifier = Modifier.weight(1f))
                SensorCard(title = "TRIP FUEL USED", value = liveTelemetry?.fuelUsedLiters?.let { "%.2f".format(it) } ?: "--", unit = "L", color = Color(0xFF10B981), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SensorCard(
    title: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF090D14),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = SecondaryText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text(unit, color = SecondaryText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// =========================================================================
// SUB-VIEW: ECU DIAGNOSTICS & DTC SCANNER PAGE
// =========================================================================
@Composable
fun DiagnosticsPage(
    dtcCodes: List<DtcItem>,
    isScanning: Boolean,
    onScan: () -> Unit,
    onClear: () -> Unit,
    connectionState: ConnectionState,
    viewModel: ObdViewModel? = null
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Stored (03)", "Pending (07)", "Permanent (0A)", "Freeze Frame", "Readiness")

    val pendingCodes = viewModel?.pendingDtcList?.collectAsState()?.value ?: emptyList()
    val permanentCodes = viewModel?.permanentDtcList?.collectAsState()?.value ?: emptyList()
    val freezeFrame = viewModel?.freezeFrame?.collectAsState()?.value
    val readiness = viewModel?.readiness?.collectAsState()?.value
    val vin = viewModel?.vin?.collectAsState()?.value
    val supportedPids = viewModel?.supportedPids?.collectAsState()?.value ?: emptySet()
    val scope = rememberCoroutineScope()

    var auxScanning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Action Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF090D14),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ECU DIAGNOSTICS & TROUBLE CODE SCANNER",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "MODES 03 / 07 / 0A · FREEZE FRAME · READINESS MONITORS",
                        color = SecondaryText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (vin != null) {
                        Text(
                            text = "VIN: $vin",
                            color = ElectricCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onScan,
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFF05070B), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scanning...", color = Color(0xFF05070B), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF05070B), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan ECU (03)", color = Color(0xFF05070B), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = { showClearConfirm = true },
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Clear Codes (04)", color = CriticalRed, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF090D14),
            contentColor = ElectricCyan
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            title,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (selectedTab == idx) ElectricCyan else SecondaryText
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> DtcListContent(dtcCodes, emptyHint = "No Stored Diagnostic Trouble Codes Found")
            1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            auxScanning = true
                            scope.launch {
                                viewModel?.scanPendingDtcsNow()
                                auxScanning = false
                            }
                        },
                        enabled = !auxScanning && viewModel != null,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Scan Pending (07)", color = Color(0xFF05070B), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                DtcListContent(pendingCodes, emptyHint = "No Pending Codes · ECU monitors have not confirmed a fault yet")
            }
            2 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            auxScanning = true
                            scope.launch {
                                viewModel?.scanPermanentDtcsNow()
                                auxScanning = false
                            }
                        },
                        enabled = !auxScanning && viewModel != null,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Scan Permanent (0A)", color = Color(0xFF05070B), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                DtcListContent(permanentCodes, emptyHint = "No Permanent Codes · These survive a Mode 04 clear until the ECU re-verifies the repair")
            }
            3 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            auxScanning = true
                            scope.launch {
                                viewModel?.readFreezeFrameNow()
                                auxScanning = false
                            }
                        },
                        enabled = !auxScanning && viewModel != null,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Read Freeze Frame (02)", color = Color(0xFF05070B), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                if (freezeFrame == null || freezeFrame.items.isEmpty()) {
                    EmptyStateBox("No Freeze Frame Stored", "The ECU snapshots engine data when a fault is confirmed. None is currently stored.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Surface(
                                color = Color(0xFF0A0F17),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AmberWarning)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text("FREEZE FRAME SNAPSHOT", color = AmberWarning, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Spacer(Modifier.height(6.dp))
                                    freezeFrame.items.forEach {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(it.label, color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                            Text(it.value, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            4 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            auxScanning = true
                            scope.launch {
                                viewModel?.readReadinessNow()
                                auxScanning = false
                            }
                        },
                        enabled = !auxScanning && viewModel != null,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Read Readiness (0101)", color = Color(0xFF05070B), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                if (readiness == null) {
                    EmptyStateBox("Readiness Not Read", "Query the ECU to see MIL status and emissions monitor completion before an inspection.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Surface(
                                color = Color(0xFF0A0F17),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (readiness.milOn) CriticalRed else TealAccent)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            if (readiness.milOn) "CHECK ENGINE LIGHT: ON" else "CHECK ENGINE LIGHT: OFF",
                                            color = if (readiness.milOn) CriticalRed else TealAccent,
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            "Stored codes: ${readiness.storedDtcCount}",
                                            color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text("SPARK IGNITION MONITORS (GASOLINE)", color = ElectricCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    readiness.sparkIgnitionMonitors.forEach { m ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(m.name, color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                            Text(
                                                if (m.complete) "COMPLETE" else "NOT READY",
                                                color = if (m.complete) TealAccent else AmberWarning,
                                                fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (supportedPids.isNotEmpty()) {
                            item {
                                Surface(
                                    color = Color(0xFF0A0F17),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text("ECU SUPPORTED PIDs (${supportedPids.size} discovered)", color = ElectricCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            supportedPids.sorted().joinToString(" "),
                                            color = SecondaryText,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Fault Codes (Mode 04)?", fontFamily = FontFamily.Monospace, color = Color.White) },
            text = {
                Text(
                    "Warning: Clearing ECU codes resets emissions inspection readiness monitors and turns off the check engine light. If the physical fault is not repaired, the code will reoccur.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = SecondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClear()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRed)
                ) {
                    Text("Confirm Clear", fontFamily = FontFamily.Monospace, color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showClearConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Text("Cancel", fontFamily = FontFamily.Monospace, color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF0F1722)
        )
    }
}

@Composable
private fun DtcListContent(dtcCodes: List<DtcItem>, emptyHint: String) {
    if (dtcCodes.isEmpty()) {
        EmptyStateBox("No Trouble Codes Found", emptyHint)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(dtcCodes) { dtc ->
                Surface(
                    color = Color(0xFF0A0F17),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (dtc.severity == DtcSeverity.CRITICAL) CriticalRed else AmberWarning)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = dtc.code,
                                    color = if (dtc.severity == DtcSeverity.CRITICAL) CriticalRed else AmberWarning,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = dtc.system,
                                    color = SecondaryText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Text(
                                text = dtc.severity.name,
                                color = if (dtc.severity == DtcSeverity.CRITICAL) CriticalRed else AmberWarning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .background(
                                        (if (dtc.severity == DtcSeverity.CRITICAL) CriticalRed else AmberWarning).copy(alpha = 0.15f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = dtc.description, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Probable Cause: ${dtc.probableCause}", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateBox(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .background(Color(0xFF080C14), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TealAccent, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// =========================================================================
// SUB-VIEW: TRIP COMPUTER PAGE
// =========================================================================
@Composable
fun TripComputerPage(
    distanceKm: Double,
    durationSeconds: Long,
    maxSpeed: Int,
    avgSpeed: Int,
    currentSpeed: Int,
    speedUnit: String,
    currentLat: Double,
    currentLon: Double,
    onReset: () -> Unit,
    viewModel: ObdViewModel? = null
) {
    val hrs = durationSeconds / 3600
    val mins = (durationSeconds % 3600) / 60
    val secs = durationSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)

    val liveTelemetry = viewModel?.telemetry?.collectAsState()?.value
    val fuelUsedL = liveTelemetry?.fuelUsedLiters
    val instantEconomy = liveTelemetry?.fuelEconomyL100
    // Average economy for the trip = liters used / distance * 100
    val avgEconomy = if (fuelUsedL != null && distanceKm > 0.5) fuelUsedL / distanceKm * 100.0 else null
    // Range estimate from remaining tank fuel (62 L Sportage tank) and trip average
    val tankLiters = 62.0
    val rangeKm = if (avgEconomy != null && avgEconomy > 0.5 && liveTelemetry?.fuelLevelPct != null) {
        (tankLiters * liveTelemetry.fuelLevelPct / 100.0) / avgEconomy * 100.0
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("TRIP COMPUTER & ODOMETER", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("GPS TRAIL & VEHICLE DISTANCE LOG", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }

            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset Trip", color = ElectricCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }

        // 4 Large Metrics Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TripCard(
                title = "DISTANCE",
                value = "%.1f".format(if (speedUnit == "mph") distanceKm * 0.621371 else distanceKm),
                unit = if (speedUnit == "mph") "MILES" else "KM",
                accentColor = ElectricCyan,
                modifier = Modifier.weight(1f)
            )

            TripCard(
                title = "ELAPSED TIME",
                value = timeFormatted,
                unit = "HH:MM:SS",
                accentColor = TealAccent,
                modifier = Modifier.weight(1f)
            )

            TripCard(
                title = "AVG SPEED",
                value = "$avgSpeed",
                unit = speedUnit.uppercase(),
                accentColor = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f)
            )

            TripCard(
                title = "MAX SPEED",
                value = "$maxSpeed",
                unit = speedUnit.uppercase(),
                accentColor = CriticalRed,
                modifier = Modifier.weight(1f)
            )
        }

        // Fuel Economy Cards (MAF-based)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TripCard(
                title = "AVG ECONOMY",
                value = avgEconomy?.let { "%.1f".format(it) } ?: "--",
                unit = "L/100KM",
                accentColor = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )
            TripCard(
                title = "INSTANT ECONOMY",
                value = instantEconomy?.let { "%.1f".format(it) } ?: "--",
                unit = "L/100KM",
                accentColor = ElectricCyan,
                modifier = Modifier.weight(1f)
            )
            TripCard(
                title = "TRIP FUEL USED",
                value = fuelUsedL?.let { "%.2f".format(it) } ?: "--",
                unit = "LITERS",
                accentColor = TealAccent,
                modifier = Modifier.weight(1f)
            )
            TripCard(
                title = "RANGE ESTIMATE",
                value = rangeKm?.let { "%.0f".format(it) } ?: "--",
                unit = "KM TO EMPTY",
                accentColor = AmberWarning,
                modifier = Modifier.weight(1f)
            )
        }

        // Live GPS Status Box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF090D14),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(TealAccent, CircleShape))
                    Text("ACTIVE GPS RECORDING", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Text(
                    text = "COORDINATES: ${"%.5f".format(currentLat)}, ${"%.5f".format(currentLon)}",
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun TripCard(
    title: String,
    value: String,
    unit: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = Color(0xFF090D14),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = accentColor, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text(unit, color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// =========================================================================
// SUB-VIEW: MEDIA & RADIO PAGE
// =========================================================================
@Composable
fun MediaRadioPage(
    radioFreq: Float,
    onFreqChange: (Float) -> Unit,
    radioStationName: String,
    onStationChange: (String, Float) -> Unit,
    isMediaPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLaunchNativeRadio: () -> Unit,
    onLaunchLocalMusic: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Frequency Display & Tuner Dial
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF090D14),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "NOTE: Physical FM chip is managed by Head Unit MCU. Tap 'Open Radio App' below for tuner.",
                        color = Color.LightGray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "FM STEREO TUNER",
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "${"%.1f".format(radioFreq)} MHz",
                    color = ElectricCyan,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = radioStationName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Playback Controls Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val newF = (radioFreq - 0.1f).coerceAtLeast(87.5f)
                            onFreqChange(newF)
                            onPrev()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFF1E293B), CircleShape)
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White)
                    }

                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(54.dp)
                            .background(ElectricCyan, CircleShape)
                    ) {
                        Icon(
                            if (isMediaPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color(0xFF05070B),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val newF = (radioFreq + 0.1f).coerceAtMost(108.0f)
                            onFreqChange(newF)
                            onNext()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFF1E293B), CircleShape)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                    }
                }
            }
        }

        // Station Presets Grid
        Text("PRESET RADIO STATIONS", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SPORTAGE_RADIO_PRESETS.take(4).forEach { station ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onStationChange(station.name, station.freq) },
                    color = if (Math.abs(radioFreq - station.freq) < 0.1f) ElectricCyan.copy(alpha = 0.2f) else Color(0xFF090D14),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (Math.abs(radioFreq - station.freq) < 0.1f) ElectricCyan else Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${"%.1f".format(station.freq)}",
                            color = ElectricCyan,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = station.name,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = station.genre,
                            color = SecondaryText,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Launch Native Apps Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onLaunchNativeRadio,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Radio, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Head Unit Radio App", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }

            Button(
                onClick = onLaunchLocalMusic,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Local Music Player", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

// =========================================================================
// SUB-VIEW: SETTINGS & DEFAULT LAUNCHER ONBOARDING PAGE
// =========================================================================
@Composable
fun SettingsPage(
    speedUnit: String,
    onToggleSpeedUnit: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    isDemoMode: Boolean,
    onToggleDemoMode: (Boolean) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    isPollingPaused: Boolean = false,
    onTogglePausePolling: () -> Unit = {},
    onOpenAppDrawer: () -> Unit = {},
    viewModel: ObdViewModel? = null,
    autoNightDim: Boolean = true,
    nightDimLevel: Float = 0.55f,
    onSetAutoNightDim: (Boolean) -> Unit = {},
    onSetNightDimLevel: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    var thresholds by remember { mutableStateOf(viewModel?.alertThresholds() ?: AlertThresholds()) }
    var csvLogging by remember { mutableStateOf(false) }
    csvLogging = viewModel?.isCsvLogging?.collectAsState()?.value ?: false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("LAUNCHER PREFERENCES & SYSTEM SETUP", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

        // 1. Set as Default Launcher Card (Non-Negotiable P0 Launcher Requirement)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF090D14),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SET AS DEFAULT ANDROID HOME LAUNCHER", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Makes Sportage OBD the permanent car dashboard. Booting the head unit or pressing Home returns here.",
                        color = SecondaryText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Button(
                    onClick = onSetDefaultLauncher,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Set Default Home", color = Color(0xFF05070B), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }

        // 2. Units & Telemetry Settings Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF090D14),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Speed Unit
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Speed & Distance Units", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("Current: $speedUnit", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = onToggleSpeedUnit,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(text = if (speedUnit == "km/h") "Switch to MPH" else "Switch to KM/H", color = ElectricCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }

                // Installed Apps Drawer
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Installed App Drawer", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("Browse and launch all head unit installed apps", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = onOpenAppDrawer,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Apps", color = ElectricCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }

                // Pause OBD Polling Toggle (Ignition Off / Save Battery)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Pause OBD Polling (Engine Off)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("Stop Bluetooth querying when car engine is switched off", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }

                    Switch(
                        checked = isPollingPaused,
                        onCheckedChange = { onTogglePausePolling() },
                        colors = SwitchDefaults.colors(checkedThumbColor = AmberWarning, checkedTrackColor = AmberWarning.copy(alpha = 0.4f))
                    )
                }

                // Bench Test / Demo Telemetry
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Bench Test / Demo Simulation", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("Simulates live RPM, speed and sensors for indoor testing without car", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }

                    Switch(
                        checked = isDemoMode,
                        onCheckedChange = onToggleDemoMode,
                        colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan, checkedTrackColor = ElectricCyan.copy(alpha = 0.4f))
                    )
                }

                // Android Bluetooth Settings Shortcut
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Android Bluetooth System Settings", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("Pair new ELM327 adapters or view system devices", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = onOpenBluetoothSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Open Settings", color = Color.LightGray, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }

        // 3. Warning Thresholds Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF090D14),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("VEHICLE WARNING THRESHOLDS", color = AmberWarning, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("Audible beep + red banner when a value crosses its limit", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

                ThresholdStepper(
                    label = "Coolant Overheat Limit",
                    value = "${thresholds.coolantMaxC}°C",
                    onMinus = { thresholds = thresholds.copy(coolantMaxC = (thresholds.coolantMaxC - 5).coerceAtLeast(90)); viewModel?.updateAlertThresholds(thresholds) },
                    onPlus = { thresholds = thresholds.copy(coolantMaxC = (thresholds.coolantMaxC + 5).coerceAtMost(130)); viewModel?.updateAlertThresholds(thresholds) }
                )
                ThresholdStepper(
                    label = "Battery Low Limit",
                    value = "%.1fV".format(thresholds.batteryMinV),
                    onMinus = { thresholds = thresholds.copy(batteryMinV = (thresholds.batteryMinV - 0.5f).coerceAtLeast(9f)); viewModel?.updateAlertThresholds(thresholds) },
                    onPlus = { thresholds = thresholds.copy(batteryMinV = (thresholds.batteryMinV + 0.5f).coerceAtMost(14f)); viewModel?.updateAlertThresholds(thresholds) }
                )
                ThresholdStepper(
                    label = "RPM Redline Limit",
                    value = "${thresholds.rpmMax}",
                    onMinus = { thresholds = thresholds.copy(rpmMax = (thresholds.rpmMax - 500).coerceAtLeast(3000)); viewModel?.updateAlertThresholds(thresholds) },
                    onPlus = { thresholds = thresholds.copy(rpmMax = (thresholds.rpmMax + 500).coerceAtMost(7500)); viewModel?.updateAlertThresholds(thresholds) }
                )
                ThresholdStepper(
                    label = "Speed Limit",
                    value = "${thresholds.speedMaxKmh} km/h",
                    onMinus = { thresholds = thresholds.copy(speedMaxKmh = (thresholds.speedMaxKmh - 10).coerceAtLeast(60)); viewModel?.updateAlertThresholds(thresholds) },
                    onPlus = { thresholds = thresholds.copy(speedMaxKmh = (thresholds.speedMaxKmh + 10).coerceAtMost(220)); viewModel?.updateAlertThresholds(thresholds) }
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Audible Warning Beeps", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("Play a tone when a threshold is crossed", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Switch(
                        checked = thresholds.audibleAlerts,
                        onCheckedChange = {
                            thresholds = thresholds.copy(audibleAlerts = it)
                            viewModel?.updateAlertThresholds(thresholds)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = AmberWarning, checkedTrackColor = AmberWarning.copy(alpha = 0.4f))
                    )
                }
            }
        }

        // 4. CSV Data Logging Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF090D14),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("CSV TELEMETRY LOGGING", color = TealAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("Record every sensor sample to a CSV file on this drive", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }

                    Switch(
                        checked = csvLogging,
                        onCheckedChange = { enable ->
                            if (enable) viewModel?.startCsvLogging() else viewModel?.stopCsvLogging()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = TealAccent, checkedTrackColor = TealAccent.copy(alpha = 0.4f))
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Auto-Start With Connection", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("Begin logging every time the adapter connects", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    var csvAuto by remember { mutableStateOf(viewModel?.csvAutostart() ?: false) }
                    Switch(
                        checked = csvAuto,
                        onCheckedChange = {
                            csvAuto = it
                            viewModel?.setCsvAutostart(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = TealAccent, checkedTrackColor = TealAccent.copy(alpha = 0.4f))
                    )
                }

                Button(
                    onClick = {
                        val file = viewModel?.csvLogFile()
                        if (file != null && file.exists()) {
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    context.packageName + ".fileprovider",
                                    file
                                )
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Share OBD Log").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not share log: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "No active log file. Start logging first.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = TealAccent, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share Current Log", color = TealAccent, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }

        // 5. Night Dimming Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF090D14),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("AUTO NIGHT DIMMING", color = Color(0xFF8B5CF6), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("Dim the screen automatically after dark (follows system day/night)", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Switch(
                        checked = autoNightDim,
                        onCheckedChange = onSetAutoNightDim,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF8B5CF6), checkedTrackColor = Color(0xFF8B5CF6).copy(alpha = 0.4f))
                    )
                }

                if (autoNightDim) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("BRIGHTNESS", color = SecondaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Slider(
                            value = 1f - nightDimLevel,
                            onValueChange = { onSetNightDimLevel(1f - it) },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF8B5CF6), activeTrackColor = Color(0xFF8B5CF6).copy(alpha = 0.5f))
                        )
                        Text("${((1f - nightDimLevel) * 100).toInt()}%", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThresholdStepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = onMinus,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("-", color = AmberWarning, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
            Text(value, color = AmberWarning, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Button(
                onClick = onPlus,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("+", color = AmberWarning, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        }
    }
}

// =========================================================================
// HELPER: DOCK BUTTON COMPONENT
// =========================================================================
@Composable
fun DockButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(46.dp)
            .clickable { onClick() },
        color = if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isSelected) accentColor else Color.Transparent)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) accentColor else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = if (isSelected) accentColor else Color.Gray,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
