package com.sportage.obd

import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.ui.dashboard.DashboardData
import com.sportage.obd.ui.dashboard.DashboardLayoutTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Layout themes are persisted by id, so these values are a compatibility contract. */
class DashboardLayoutThemeTest {

    @Test fun persistedIdsAreUniqueAndStable() {
        assertEquals(3, DashboardLayoutTheme.all.size)
        assertEquals(
            DashboardLayoutTheme.all.size,
            DashboardLayoutTheme.all.map { it.id }.toSet().size
        )
        assertEquals("classic", DashboardLayoutTheme.Classic.id)
        assertEquals("circular-gauge", DashboardLayoutTheme.CircularGauge.id)
        assertEquals("widget-board", DashboardLayoutTheme.RectangularWidgets.id)
    }

    @Test fun defaultLayoutIsTheExistingDashboard() {
        assertEquals(DashboardLayoutTheme.Classic, DashboardLayoutTheme.fromId(null))
        assertEquals(DashboardLayoutTheme.Classic, DashboardLayoutTheme.fromId(""))
        assertEquals(DashboardLayoutTheme.Classic, DashboardLayoutTheme.fromId(DashboardLayoutTheme.DEFAULT_ID))
    }

    @Test fun unknownOrCorruptIdFallsBackInsteadOfCrashing() {
        assertEquals(DashboardLayoutTheme.Classic, DashboardLayoutTheme.fromId("not-a-layout"))
        assertEquals(DashboardLayoutTheme.Classic, DashboardLayoutTheme.fromId("CIRCULAR-GAUGE"))
    }

    @Test fun everyLayoutIsPresentableInSettings() {
        DashboardLayoutTheme.all.forEach { theme ->
            assertTrue("id must be stable and non-blank", theme.id.isNotBlank())
            assertTrue("${theme.id} needs a title", theme.title.isNotBlank())
            assertTrue("${theme.id} needs a description", theme.description.isNotBlank())
        }
    }

    @Test fun sharedDataDerivesConnectionAndGearForBothThemes() {
        val connected = DashboardData(
            connectionState = ConnectionState.Connected("ELM327", "00:11:22:33:44:55"),
            gear = 4,
            transmissionGear = 4
        )
        assertTrue(connected.connected)
        assertEquals("Connected · ELM327", connected.connectionLabel)
        assertEquals("D4", connected.gearText)
        assertEquals("--", connected.speedText)

        val disconnected = DashboardData(connectionState = ConnectionState.Disconnected)
        assertTrue(!disconnected.connected)
        assertEquals("Disconnected", disconnected.connectionLabel)
    }
}
