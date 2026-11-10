package com.sportage.obd.ui.settings

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.viewmodel.ObdViewModel

/**
 * Vehicle & Driving Settings Subpage:
 * - Speed units (km/h vs mph)
 * - Distance units (km vs miles)
 * - Vehicle profile configuration (Kia Sportage Nu 2.0 MPI, tank size, fuel calibration)
 * - Driving safety lockouts and distraction gating
 * - Trip computer controls
 */
@Composable
fun VehicleDrivingPage(
    speedUnit: String,
    onToggleSpeedUnit: () -> Unit,
    viewModel: ObdViewModel?,
    onResetTrip: () -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE) }
    val fuelPrefs = remember { context.getSharedPreferences("sportage_fuel_settings", Context.MODE_PRIVATE) }

    var tankCapacity by remember {
        mutableDoubleStateOf(
            fuelPrefs.getFloat("tank_capacity_liters", fuelPrefs.getFloat("tank_liters", 62.0f)).toDouble()
        )
    }
    var fuelPriceQar by remember {
        mutableDoubleStateOf(
            fuelPrefs.getFloat("fuel_price_qar_per_liter", fuelPrefs.getFloat("price_qar", 2.05f)).toDouble()
        )
    }
    var safetyLockout by remember {
        mutableStateOf(uiPrefs.getBoolean("driverSafetyLockout", true))
    }
    var autoSwitchToGaugesAtSpeed by remember {
        mutableStateOf(uiPrefs.getBoolean("autoSwitchToGaugesAtSpeed", false))
    }
    var fuelCalibrationFactor by remember {
        mutableFloatStateOf(uiPrefs.getFloat("fuelCalibrationFactor", 1.0f))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsTopBar(
            title = "Vehicle & Driving",
            subtitle = "Speed units, vehicle profile specs, and driver safety lockouts",
            onNavigateBack = onNavigateBack
        )

        // 1. UNITS & MEASUREMENTS
        SettingsSectionHeader(title = "Units & Measurements")

        SettingsValueItem(
            title = "Speed & Distance Units",
            valueText = speedUnit.uppercase(),
            description = "Toggle between Metric (km/h) and Imperial (mph) for gauges and navigation",
            icon = Icons.Default.Speed,
            onClick = onToggleSpeedUnit
        )

        SettingsValueItem(
            title = "Distance Unit",
            valueText = if (speedUnit == "km/h") "Kilometres (km)" else "Miles (mi)",
            description = "Controls odometer and trip distance calculations",
            icon = Icons.Default.Route,
            onClick = onToggleSpeedUnit
        )

        // 2. VEHICLE CONFIGURATION
        SettingsSectionHeader(title = "Vehicle Profile Configuration")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkSurfaceElevated())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = ElectricCyan(),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.padding(start = 12.dp))
                    Column {
                        Text("Active Vehicle Profile", color = PrimaryText(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Kia Sportage (SL / QL) · Nu 2.0L MPI Gasoline", color = SecondaryText(), fontSize = 11.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = DarkSurfaceElevated(),
                    border = BorderStroke(1.dp, ElectricCyan().copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Kia Nu 2.0",
                        color = ElectricCyan(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        SettingsStepperItem(
            title = "Fuel Tank Capacity",
            valueText = "%.1f L".format(tankCapacity),
            description = "Standard Kia Sportage fuel tank volume for percentage & range estimation",
            onMinus = {
                tankCapacity = (tankCapacity - 1.0).coerceAtLeast(30.0)
                fuelPrefs.edit()
                    .putFloat("tank_capacity_liters", tankCapacity.toFloat())
                    .putFloat("tank_liters", tankCapacity.toFloat())
                    .apply()
            },
            onPlus = {
                tankCapacity = (tankCapacity + 1.0).coerceAtMost(90.0)
                fuelPrefs.edit()
                    .putFloat("tank_capacity_liters", tankCapacity.toFloat())
                    .putFloat("tank_liters", tankCapacity.toFloat())
                    .apply()
            }
        )

        SettingsStepperItem(
            title = "Fuel Price (Local Currency / L)",
            valueText = "%.2f".format(fuelPriceQar),
            description = "Used to calculate trip cost and fuel expenditure per 100 km",
            onMinus = {
                fuelPriceQar = (fuelPriceQar - 0.05).coerceAtLeast(0.50)
                fuelPrefs.edit()
                    .putFloat("fuel_price_qar_per_liter", fuelPriceQar.toFloat())
                    .putFloat("price_qar", fuelPriceQar.toFloat())
                    .apply()
            },
            onPlus = {
                fuelPriceQar = (fuelPriceQar + 0.05).coerceAtMost(10.00)
                fuelPrefs.edit()
                    .putFloat("fuel_price_qar_per_liter", fuelPriceQar.toFloat())
                    .putFloat("price_qar", fuelPriceQar.toFloat())
                    .apply()
            }
        )

        SettingsStepperItem(
            title = "MAF Consumption Calibration",
            valueText = "%.2f×".format(fuelCalibrationFactor),
            description = "Fuel flow multiplier based on injector duty cycle and mass air flow",
            onMinus = {
                fuelCalibrationFactor = (fuelCalibrationFactor - 0.02f).coerceAtLeast(0.70f)
                uiPrefs.edit().putFloat("fuelCalibrationFactor", fuelCalibrationFactor).apply()
                viewModel?.setFuelCalibration(fuelCalibrationFactor)
            },
            onPlus = {
                fuelCalibrationFactor = (fuelCalibrationFactor + 0.02f).coerceAtMost(1.40f)
                uiPrefs.edit().putFloat("fuelCalibrationFactor", fuelCalibrationFactor).apply()
                viewModel?.setFuelCalibration(fuelCalibrationFactor)
            }
        )

        // 3. DRIVING PREFERENCES
        SettingsSectionHeader(title = "Driving Preferences & Safety")

        SettingsSwitchItem(
            title = "Driver Distraction Safety Lockout",
            description = "Disables deep diagnostics and terminal commands when vehicle speed exceeds 5 km/h",
            checked = safetyLockout,
            onCheckedChange = { enabled ->
                safetyLockout = enabled
                uiPrefs.edit().putBoolean("driverSafetyLockout", enabled).apply()
            },
            icon = Icons.Default.Lock
        )

        SettingsSwitchItem(
            title = "Auto-Switch to Gauges at Highway Speed",
            description = "Automatically transitions from media/settings back to HUD speedometer when speed > 60 km/h",
            checked = autoSwitchToGaugesAtSpeed,
            onCheckedChange = { enabled ->
                autoSwitchToGaugesAtSpeed = enabled
                uiPrefs.edit().putBoolean("autoSwitchToGaugesAtSpeed", enabled).apply()
            },
            icon = Icons.Default.Speed
        )

        SettingsActionItem(
            title = "Reset Trip Computer",
            description = "Resets current drive distance, average speed, and elapsed time to zero",
            buttonLabel = "Reset Trip",
            icon = Icons.Default.RestartAlt,
            onClick = {
                onResetTrip()
                viewModel?.resetTripFuel()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
