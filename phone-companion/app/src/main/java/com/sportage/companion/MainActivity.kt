package com.sportage.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sportage.companion.ui.theme.SportageCompanionTheme
import com.sportage.companion.ui.screens.connectionsetup.ConnectionSetupScreen
import com.sportage.companion.ui.screens.dashboard.DashboardScreen
import com.sportage.companion.ui.screens.findmycar.FindMyCarScreen
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SportageCompanionTheme {
                SportageCompanionRoot()
            }
        }
    }
}

@Composable
fun SportageCompanionRoot() {
    val navController = rememberNavController()
    val items = listOf("dashboard" to "Live Dashboard", "findcar" to "Find My Car")

    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val dest = backStack?.destination
            if (dest?.route in listOf("dashboard", "findcar")) {
                NavigationBar {
                    items.forEach { (route, label) ->
                        NavigationBarItem(
                            icon = { Text(label.take(1)) },
                            label = { Text(label) },
                            selected = dest?.hierarchy?.any { it.route == route } == true,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "setup",
            modifier = Modifier.padding(padding)
        ) {
            composable("setup") {
                ConnectionSetupScreen(
                    viewModel = koinViewModel(),
                    onConfigured = { navController.navigate("dashboard") { popUpTo("setup") { inclusive = true } } }
                )
            }
            composable("dashboard") { DashboardScreen(viewModel = koinViewModel()) }
            composable("findcar") { FindMyCarScreen(viewModel = koinViewModel()) }
        }
    }
}