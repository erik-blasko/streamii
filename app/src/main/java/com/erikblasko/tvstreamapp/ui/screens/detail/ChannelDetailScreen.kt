package com.erikblasko.tvstreamapp.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erikblasko.tvstreamapp.R
import com.erikblasko.tvstreamapp.data.model.Channel
import com.erikblasko.tvstreamapp.data.model.StreamSource
import com.erikblasko.tvstreamapp.data.model.StreamStatus
import com.erikblasko.tvstreamapp.ui.components.BackTopBar
import com.erikblasko.tvstreamapp.ui.components.ChannelLogo
import com.erikblasko.tvstreamapp.ui.components.CountryBadge
import com.erikblasko.tvstreamapp.ui.components.StatusIndicator
import com.erikblasko.tvstreamapp.ui.components.categoryName
import com.erikblasko.tvstreamapp.ui.components.countryName
import com.erikblasko.tvstreamapp.ui.screens.channels.ChannelsViewModel
import com.erikblasko.tvstreamapp.ui.theme.StreamOffline
import com.erikblasko.tvstreamapp.ui.theme.StreamOnline

@Composable
fun ChannelDetailScreen(
    channel: Channel?,
    viewModel: ChannelsViewModel,
    onBack: () -> Unit,
    onPlay: (Channel, StreamSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val urlStatuses by viewModel.urlStatuses.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    if (channel == null) {
        Column(modifier.fillMaxSize()) {
            BackTopBar(title = "", onBack = onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.detail_not_found))
            }
        }
        return
    }

    val isFavorite = channel.id in favoriteIds
    val sourceStatuses = channel.sources.map { urlStatuses[it.url] ?: StreamStatus.UNKNOWN }
    val channelStatus = viewModel.streamChecker.channelStatus(channel, urlStatuses)
    // Play the first source known to work, else the first one
    val bestSource = channel.sources.getOrNull(sourceStatuses.indexOf(StreamStatus.ONLINE))
        ?: channel.primarySource

    Column(modifier.fillMaxSize()) {
        BackTopBar(title = "", onBack = onBack) {
            IconButton(onClick = { viewModel.toggleFavorite(channel.id) }) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.favorite_remove else R.string.favorite_add
                    ),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(channel = channel, status = null, size = 72.dp)
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CountryBadge(channel.country)
                        Text(
                            text = (listOf(countryName(channel.country)) +
                                channel.categories.map { categoryName(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ChannelStatusLine(channelStatus, sourceStatuses)
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onPlay(channel, bestSource) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.detail_play), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.detail_sources).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            // Several sources from one playlist get a number: "Samsung TV Plus 2"
            val sameNameCount = channel.sources.groupingBy { it.playlistName }.eachCount()
            val seen = mutableMapOf<String, Int>()
            channel.sources.forEachIndexed { index, source ->
                val number = seen.merge(source.playlistName, 1, Int::plus)!!
                SourceRow(
                    index = index,
                    label = if ((sameNameCount[source.playlistName] ?: 0) > 1) {
                        "${source.playlistName} $number"
                    } else {
                        source.playlistName
                    },
                    source = source,
                    status = sourceStatuses[index],
                    onClick = { onPlay(channel, source) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChannelStatusLine(status: StreamStatus, sourceStatuses: List<StreamStatus>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusIndicator(status = status, size = 8.dp)
        Text(
            text = when (status) {
                StreamStatus.ONLINE -> stringResource(
                    R.string.detail_online_sources,
                    sourceStatuses.count { it == StreamStatus.ONLINE },
                    sourceStatuses.size
                )
                StreamStatus.OFFLINE -> stringResource(R.string.status_offline)
                StreamStatus.CHECKING -> stringResource(R.string.status_checking)
                StreamStatus.UNKNOWN -> stringResource(R.string.status_unknown)
            },
            style = MaterialTheme.typography.bodySmall,
            color = when (status) {
                StreamStatus.ONLINE -> StreamOnline
                StreamStatus.OFFLINE -> StreamOffline
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun SourceRow(index: Int, label: String, source: StreamSource, status: StreamStatus, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusIndicator(status = status, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label.ifBlank { stringResource(R.string.source_number, index + 1) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            val details = listOfNotNull(source.feed, source.quality)
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = when (status) {
                StreamStatus.ONLINE -> stringResource(R.string.status_online)
                StreamStatus.OFFLINE -> stringResource(R.string.status_offline)
                StreamStatus.CHECKING -> stringResource(R.string.status_checking)
                StreamStatus.UNKNOWN -> stringResource(R.string.status_unknown)
            },
            style = MaterialTheme.typography.bodySmall,
            color = when (status) {
                StreamStatus.ONLINE -> StreamOnline
                StreamStatus.OFFLINE -> StreamOffline
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
