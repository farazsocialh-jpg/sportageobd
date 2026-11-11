package com.sportage.obd.ui

import com.sportage.obd.radio.NativeRadioWidget as RadioNativeRadioWidget
import com.sportage.obd.radio.RadioBand as RadioRadioBand
import com.sportage.obd.radio.RadioHardwareController as RadioRadioHardwareController
import com.sportage.obd.radio.RadioTunerState as RadioRadioTunerState

typealias RadioBand = RadioRadioBand
typealias RadioTunerState = RadioRadioTunerState
typealias RadioHardwareController = RadioRadioHardwareController

@androidx.compose.runtime.Composable
fun NativeRadioWidget(
    controller: RadioHardwareController,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) = RadioNativeRadioWidget(controller = controller, modifier = modifier)