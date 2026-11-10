package com.sportage.obd

import com.sportage.obd.ui.settings.settingsCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the 8-category Settings architecture and routes.
 */
class SettingsArchitectureTest {

    @Test
    fun verifyExactlyEightCategoriesExist() {
        assertEquals("Settings Home must have exactly 8 categories", 8, settingsCategories.size)
    }

    @Test
    fun verifyAllEightCategoriesHaveUniqueRoutesAndLabels() {
        val routes = settingsCategories.map { it.route }.toSet()
        assertEquals("Each category must have a unique route", 8, routes.size)

        val titles = settingsCategories.map { it.title }.toSet()
        assertEquals("Each category must have a unique title", 8, titles.size)

        settingsCategories.forEach { category ->
            assertTrue("Title should not be blank", category.title.isNotBlank())
            assertTrue("Description should not be blank", category.description.isNotBlank())
            assertTrue("Route should not be blank", category.route.isNotBlank())
            assertNotNull("Icon must be defined", category.icon)
        }
    }

    @Test
    fun verifyExpectedCategoryTitlesAndRoutes() {
        val expectedRoutes = listOf(
            "appearance" to "Appearance & Themes",
            "dashboard" to "Dashboard Customisation",
            "connectivity" to "OBD & Connectivity",
            "vehicle" to "Vehicle & Driving",
            "alerts" to "Alerts & Notifications",
            "navigation" to "Navigation & Launcher",
            "data" to "Data & Privacy",
            "advanced" to "About & Advanced"
        )

        expectedRoutes.forEachIndexed { index, (expectedRoute, expectedTitle) ->
            val cat = settingsCategories[index]
            assertEquals(expectedRoute, cat.route)
            assertEquals(expectedTitle, cat.title)
        }
    }
}
