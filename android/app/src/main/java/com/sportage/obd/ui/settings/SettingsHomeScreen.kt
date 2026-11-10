package com.sportage.obd.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings Home Screen:
 * Displays ONLY the 8 authoritative settings categories.
 * Each category displays an icon, title, short description, right-facing chevron, and clickable row.
 * No individual setting controls appear on this screen.
 */
@Composable
fun SettingsHomeScreen(
    onNavigateToCategory: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsTopBar(
            title = "Settings",
            subtitle = "Vehicle dashboard preferences and configuration",
            onNavigateBack = onNavigateBack
        )

        Spacer(modifier = Modifier.height(4.dp))

        settingsCategories.forEach { category ->
            SettingsCategoryItem(
                category = category,
                onClick = { onNavigateToCategory(category.route) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
