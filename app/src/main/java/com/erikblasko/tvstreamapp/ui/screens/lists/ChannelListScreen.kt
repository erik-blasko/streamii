package com.erikblasko.tvstreamapp.ui.screens.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erikblasko.tvstreamapp.data.model.Channel
import com.erikblasko.tvstreamapp.ui.components.ChannelRow
import com.erikblasko.tvstreamapp.ui.components.MenuTopBar
import com.erikblasko.tvstreamapp.ui.screens.channels.ChannelsViewModel

/** Favourites or recently watched: a plain list with the same rows as the channel list. */
@Composable
fun ChannelListScreen(
    title: String,
    channels: List<Channel>,
    emptyText: String,
    emptyHint: String?,
    viewModel: ChannelsViewModel,
    onOpenDrawer: () -> Unit,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val urlStatuses by viewModel.urlStatuses.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        MenuTopBar(title = title, onOpenDrawer = onOpenDrawer)

        if (channels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    if (emptyHint != null) {
                        Text(
                            text = emptyHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items = channels, key = { it.id }) { channel ->
                    ChannelRow(
                        channel = channel,
                        status = viewModel.streamChecker.channelStatus(channel, urlStatuses),
                        isFavorite = channel.id in favoriteIds,
                        onClick = { onChannelClick(channel) },
                        onFavoriteClick = { viewModel.toggleFavorite(channel.id) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
