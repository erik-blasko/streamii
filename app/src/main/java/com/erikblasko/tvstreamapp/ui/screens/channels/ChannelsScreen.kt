package com.erikblasko.tvstreamapp.ui.screens.channels

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erikblasko.tvstreamapp.R
import com.erikblasko.tvstreamapp.data.model.Channel
import com.erikblasko.tvstreamapp.data.model.MediaKind
import com.erikblasko.tvstreamapp.data.model.StreamStatus
import com.erikblasko.tvstreamapp.ui.components.ChannelRow
import com.erikblasko.tvstreamapp.ui.components.StatusIndicator
import com.erikblasko.tvstreamapp.util.removeDiacritics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun ChannelsScreen(
    viewModel: ChannelsViewModel,
    section: ChannelsViewModel.Section,
    onOpenDrawer: () -> Unit,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by section.uiState.collectAsState()
    val urlStatuses by viewModel.urlStatuses.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val statusCounts by section.statusCounts.collectAsState()
    val texts = SectionTexts.of(section.kind)

    LaunchedEffect(section) { section.onShown() }

    var showFilters by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu_open))
            }
            SearchField(
                query = uiState.searchQuery,
                hint = stringResource(texts.searchHint),
                onQueryChange = section::onSearchQueryChanged,
                onSearch = { focusManager.clearFocus() },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                focusManager.clearFocus()
                showFilters = true
            }) {
                Box {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = stringResource(
                            if (uiState.activeFilterCount > 0) R.string.filters_active else R.string.filters
                        )
                    )
                    if (uiState.activeFilterCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(9.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
            }
        }

        // Shown once availability checking has started
        if (!uiState.isLoading && !uiState.hasError && urlStatuses.isNotEmpty()) {
            StatsStrip(counts = statusCounts)
        }

        when {
            uiState.isLoading -> CenteredBox(Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(texts.loading),
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            uiState.hasError -> CenteredBox(Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.error ?: stringResource(R.string.error_unknown),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    TextButton(onClick = { viewModel.loadChannels(forceRefresh = true) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            uiState.channels.isEmpty() -> CenteredBox(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (uiState.totalCount > 0) texts.noneFiltered else texts.none
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            else -> ChannelListWithIndex(
                channels = uiState.channels,
                urlStatuses = urlStatuses,
                favoriteIds = favoriteIds,
                viewModel = viewModel,
                section = section,
                onChannelClick = onChannelClick,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showFilters) {
        FilterSheet(
            uiState = uiState,
            section = section,
            onDismiss = { showFilters = false }
        )
    }
}

/** Texts that say "channels" for TV and "stations" for radio. */
internal class SectionTexts(
    @StringRes val searchHint: Int,
    @StringRes val loading: Int,
    @StringRes val none: Int,
    @StringRes val noneFiltered: Int,
    @PluralsRes val showCount: Int
) {
    companion object {
        private val tv = SectionTexts(
            R.string.search_hint, R.string.loading_channels, R.string.no_channels,
            R.string.no_channels_filtered, R.plurals.filter_show_channels
        )
        private val radio = SectionTexts(
            R.string.search_hint_radio, R.string.loading_stations, R.string.no_stations,
            R.string.no_stations_filtered, R.plurals.filter_show_stations
        )

        fun of(kind: MediaKind) = when (kind) {
            MediaKind.TV -> tv
            MediaKind.RADIO -> radio
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    hint: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Confirming closes the keyboard and removes the cursor from the field
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(22.dp))
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_clear),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun StatsStrip(counts: StatusCounts) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusCount(StreamStatus.ONLINE, stringResource(R.string.stats_online, counts.online))
        StatusCount(StreamStatus.OFFLINE, stringResource(R.string.stats_offline, counts.offline))
        if (counts.checking > 0) {
            StatusCount(StreamStatus.CHECKING, stringResource(R.string.stats_checking, counts.checking))
        }
    }
}

@Composable
private fun StatusCount(status: StreamStatus, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusIndicator(status = status, size = 8.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ChannelListWithIndex(
    channels: List<Channel>,
    urlStatuses: Map<String, StreamStatus>,
    favoriteIds: Set<String>,
    viewModel: ChannelsViewModel,
    section: ChannelsViewModel.Section,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Sidebar letters ignore diacritics, so "Č" channels are found under "C"
    val letterIndexMap = remember(channels) {
        val map = linkedMapOf<Char, Int>()
        channels.forEachIndexed { index, channel ->
            val letter = channel.name.firstOrNull()?.toString()?.removeDiacritics()
                ?.firstOrNull()?.uppercaseChar() ?: return@forEachIndexed
            if (letter in 'A'..'Z' && letter !in map) map[letter] = index
        }
        map
    }
    val letters = remember { ('A'..'Z').toList() }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collectLatest { index ->
                delay(300) // wait until scrolling settles
                section.onFirstVisibleIndexChanged(index)
            }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) focusManager.clearFocus()
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 24.dp)
        ) {
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

        AlphabetSidebar(
            letters = letters,
            available = letterIndexMap.keys,
            modifier = Modifier.align(Alignment.CenterEnd),
            onLetterSelected = { letter ->
                // A letter without channels jumps to the next one that has some
                val index = letters.dropWhile { it < letter }.firstNotNullOfOrNull { letterIndexMap[it] }
                    ?: channels.lastIndex
                coroutineScope.launch { listState.scrollToItem(index) }
            }
        )
    }
}

@Composable
private fun CenteredBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun AlphabetSidebar(
    letters: List<Char>,
    available: Set<Char>,
    modifier: Modifier = Modifier,
    onLetterSelected: (Char) -> Unit
) {
    var sidebarHeight by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = 6.dp)
            .onSizeChanged { sidebarHeight = it.height }
            .pointerInput(letters, sidebarHeight) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    if (sidebarHeight > 0 && letters.isNotEmpty()) {
                        val index = ((change.position.y / sidebarHeight) * letters.size)
                            .toInt()
                            .coerceIn(0, letters.size - 1)
                        onLetterSelected(letters[index])
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Text(
                text = letter.toString(),
                fontSize = 11.sp,
                // Without this the text keeps the theme's 24sp line height and Y, Z get cut off
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (letter in available) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
                modifier = Modifier.clickable { onLetterSelected(letter) }
            )
        }
    }
}
