package com.erikblasko.tvstreamapp

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.erikblasko.tvstreamapp.ui.navigation.AppDrawerContent
import com.erikblasko.tvstreamapp.ui.navigation.AppNavigation
import com.erikblasko.tvstreamapp.ui.navigation.Routes
import com.erikblasko.tvstreamapp.ui.screens.channels.ChannelsViewModel
import com.erikblasko.tvstreamapp.ui.screens.settings.SettingsViewModel
import com.erikblasko.tvstreamapp.ui.theme.StreamiiTheme
import com.erikblasko.tvstreamapp.util.AppLocale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as StreamiiApp).container

        setContent {
            val themeMode by container.preferences.themeMode.collectAsState(initial = "system")

            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            // Status bar icons follow the app theme, which can differ from the system one
            LaunchedEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            StreamiiTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                val channelsViewModel: ChannelsViewModel = viewModel(
                    factory = ChannelsViewModel.Factory(
                        container.channelRepository,
                        container.favoritesRepository,
                        container.streamChecker,
                        container.preferences
                    )
                )
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(container.preferences)
                )
                val tvState by channelsViewModel.tv.uiState.collectAsState()
                val radioState by channelsViewModel.radio.uiState.collectAsState()
                val favoriteIds by channelsViewModel.favoriteIds.collectAsState()

                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                    gesturesEnabled = drawerState.isOpen || currentRoute in Routes.topLevel,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier.width(304.dp),
                            drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            AppDrawerContent(
                                currentRoute = currentRoute,
                                channelCount = tvState.totalCount,
                                stationCount = radioState.totalCount,
                                favoriteCount = favoriteIds.size,
                                onNavigate = { route ->
                                    scope.launch { drawerState.close() }
                                    if (route != currentRoute) {
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                onRecheck = {
                                    scope.launch { drawerState.close() }
                                    channelsViewModel.recheckStreams()
                                }
                            )
                        }
                    }
                ) {
                    Scaffold { innerPadding ->
                        AppNavigation(
                            navController = navController,
                            channelsViewModel = channelsViewModel,
                            settingsViewModel = settingsViewModel,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onPlay = { channel, source ->
                                channelsViewModel.onChannelPlayed(channel.id)
                                startActivity(
                                    PlayerActivity.intent(
                                        context = this@MainActivity,
                                        channelId = channel.id,
                                        sources = channelsViewModel.playbackOrder(channel, source),
                                        name = channel.name,
                                        logoUrl = channel.logoUrl,
                                        isRadio = channelsViewModel.isRadio(channel)
                                    )
                                )
                            },
                            // Resources are picked when the activity is created, so apply the language by recreating it
                            onLanguageChanged = { recreate() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
