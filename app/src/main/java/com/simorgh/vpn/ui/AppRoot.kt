package com.simorgh.vpn.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.simorgh.vpn.R
import com.simorgh.vpn.ui.home.HomeScreen
import com.simorgh.vpn.ui.logs.LogsScreen
import com.simorgh.vpn.ui.profiles.ProfilesScreen
import com.simorgh.vpn.ui.settings.SettingsScreen

private data class Tab(
    val route: String,
    val labelRes: Int,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector
)

private val tabs = listOf(
    Tab("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    Tab("profiles", R.string.nav_profiles, Icons.Filled.Dns, Icons.Outlined.Dns),
    Tab("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
    Tab("logs", R.string.nav_logs, Icons.Filled.Terminal, Icons.Outlined.Terminal)
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = currentRoute == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == t.route) t.iconSelected else t.iconUnselected,
                                contentDescription = stringResource(t.labelRes)
                            )
                        },
                        label = { Text(stringResource(t.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") { HomeScreen(onOpenProfiles = { nav.navigate("profiles") }) }
            composable("profiles") { ProfilesScreen() }
            composable("settings") { SettingsScreen() }
            composable("logs") { LogsScreen() }
        }
    }
}
