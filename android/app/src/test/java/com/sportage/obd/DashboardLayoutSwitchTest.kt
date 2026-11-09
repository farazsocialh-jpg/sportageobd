package com.sportage.obd

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.ui.dashboard.DashboardActions
import com.sportage.obd.ui.dashboard.DashboardData
import com.sportage.obd.ui.dashboard.DashboardLayoutTheme
import com.sportage.obd.ui.dashboard.DashboardMedia
import com.sportage.obd.ui.dashboard.DashboardThemeHost
import com.sportage.obd.ui.theme.SportageObdTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Both layout themes must render from the same data bundle and expose the five-icon dock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w1024dp-h600dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardLayoutSwitchTest {

    @get:Rule val compose = createComposeRule()

    private fun launch(theme: DashboardLayoutTheme) {
        compose.setContent {
            SportageObdTheme {
                DashboardThemeHost(
                    theme = theme,
                    data = DashboardData(
                        connectionState = ConnectionState.Connected("ELM327", "00:11:22:33:44:55"),
                        measuredHz = 8.7f,
                        speed = 88,
                        speedUnit = "km/h",
                        rpm = 2400,
                        coolant = 89,
                        fuelLevel = 68,
                        voltage = 13.8f,
                        gear = 4,
                        transmissionGear = 4
                    ),
                    actions = DashboardActions(),
                    media = DashboardMedia()
                ) {
                    androidx.compose.material3.Text("classic")
                }
            }
        }
    }

    @Test fun circularThemeRendersGaugeCarMusicAndDock() {
        launch(DashboardLayoutTheme.CircularGauge)
        compose.onNodeWithTag("dashboard_circular_theme").assertExists()
        compose.onNodeWithTag("circular_speedometer").assertExists()
        compose.onNodeWithTag("circular_music_player").assertExists()
        compose.onNodeWithTag("dashboard_car_graphic").assertExists()
        compose.onNodeWithTag("dashboard_dock").assertExists()
        compose.onNodeWithText("88").assertExists()
        compose.onAllNodesWithContentDescription("Open navigation app").assertCountEquals(1)
    }

    @Test fun widgetThemeRendersSpeedClockMediaAndDock() {
        launch(DashboardLayoutTheme.RectangularWidgets)
        compose.onNodeWithTag("dashboard_widgets_theme").assertExists()
        compose.onNodeWithTag("widget_speed_panel").assertExists()
        compose.onNodeWithTag("widget_clock_panel").assertExists()
        compose.onNodeWithTag("widget_media_panel").assertExists()
        compose.onNodeWithTag("dashboard_car_graphic").assertExists()
        compose.onNodeWithTag("dashboard_dock").assertExists()
        compose.onNodeWithText("88").assertExists()
    }
}
