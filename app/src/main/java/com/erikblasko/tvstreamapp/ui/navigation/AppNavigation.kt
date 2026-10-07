package com.erikblasko.tvstreamapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.erikblasko.tvstreamapp.R
import com.erikblasko.tvstreamapp.data.model.Channel
import com.erikblasko.tvstreamapp.data.model.StreamSource
import com.erikblasko.tvstreamapp.ui.screens.channels.ChannelsScreen
import com.erikblasko.tvstreamapp.ui.screens.channels.ChannelsViewModel
import com.erikblasko.tvstreamapp.ui.screens.detail.ChannelDetailScreen
import com.erikblasko.tvstreamapp.ui.screens.lists.ChannelListScreen
import com.erikblasko.tvstreamapp.ui.screens.playlists.PlaylistsScreen
import com.erikblasko.tvstreamapp.ui.screens.settings.SettingsScreen
import com.erikblasko.tvstreamapp.ui.screens.settings.SettingsViewModel

@Composable
fun AppNavigation(
    navController: NavHostController,
    channelsViewModel: ChannelsViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenDrawer: () -> Unit,
    onPlay: (Channel, StreamSource) -> Unit,
    onLanguageChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openDetail: (Channel) -> Unit = { navController.navigate(Routes.detail(it.id)) }

    NavHost(navController = navController, startDestination = Routes.CHANNELS, modifier = modifier) {
        composable(Routes.CHANNELS) {
            ChannelsScreen(
                viewModel = channelsViewModel,
                section = channelsViewModel.tv,
                onOpenDrawer = onOpenDrawer,
                onChannelClick = openDetail
            )
        }

        composable(Routes.RADIO) {
            ChannelsScreen(
                viewModel = channelsViewModel,
                section = channelsViewModel.radio,
                onOpenDrawer = onOpenDrawer,
                onChannelClick = openDetail
            )
        }

        composable(Routes.FAVORITES) {
            val favorites by channelsViewModel.favoriteChannels.collectAsState()
            ChannelListScreen(
                title = stringResource(R.string.nav_favorites),
                channels = favorites,
                emptyText = stringResource(R.string.no_favorites),
                emptyHint = stringResource(R.string.no_favorites_hint),
                viewModel = channelsViewModel,
                onOpenDrawer = onOpenDrawer,
                onChannelClick = openDetail
            )
        }

        composable(Routes.RECENT) {
            val recent by channelsViewModel.recentChannels.collectAsState()
            ChannelListScreen(
                title = stringResource(R.string.nav_recent),
                channels = recent,
                emptyText = stringResource(R.string.no_recent),
                emptyHint = null,
                viewModel = channelsViewModel,
                onOpenDrawer = onOpenDrawer,
                onChannelClick = openDetail
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onOpenDrawer = onOpenDrawer,
                onOpenPlaylists = { navController.navigate(Routes.PLAYLISTS) },
                onLanguageChanged = onLanguageChanged
            )
        }

        composable(Routes.PLAYLISTS) {
            PlaylistsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onPlaylistsChanged = { channelsViewModel.loadChannels(forceRefresh = true) }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ChannelDetailScreen(
                channel = channelsViewModel.getChannel(id),
                viewModel = channelsViewModel,
                onBack = { navController.popBackStack() },
                onPlay = onPlay
            )
        }
    }
}
