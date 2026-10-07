package com.erikblasko.tvstreamapp.ui.screens.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.erikblasko.tvstreamapp.data.model.Channel
import com.erikblasko.tvstreamapp.data.model.MediaKind
import com.erikblasko.tvstreamapp.data.model.StreamSource
import com.erikblasko.tvstreamapp.data.model.StreamStatus
import com.erikblasko.tvstreamapp.data.network.StreamChecker
import com.erikblasko.tvstreamapp.data.preferences.AppPreferences
import com.erikblasko.tvstreamapp.data.repository.ChannelRepository
import com.erikblasko.tvstreamapp.data.repository.FavoritesRepository
import com.erikblasko.tvstreamapp.util.toSearchKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

enum class StatusFilter { ALL, ONLINE, OFFLINE }

data class ChannelsUiState(
    val channels: List<Channel> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    /** Error message from loading, null when it has none (the UI shows a generic text) */
    val error: String? = null,
    val hasError: Boolean = false,
    val searchQuery: String = "",
    val selectedCountries: Set<String> = emptySet(),
    val selectedCategories: Set<String> = emptySet(),
    val selectedStatus: StatusFilter = StatusFilter.ALL,
    /** Country code -> number of channels, most channels first */
    val countries: List<Pair<String, Int>> = emptyList(),
    /** Category key -> number of channels, most channels first */
    val categories: List<Pair<String, Int>> = emptyList()
) {
    val activeFilterCount: Int
        get() = selectedCountries.size + selectedCategories.size +
            if (selectedStatus != StatusFilter.ALL) 1 else 0
}

data class StatusCounts(
    val online: Int = 0,
    val offline: Int = 0,
    val checking: Int = 0
)

class ChannelsViewModel(
    private val channelRepository: ChannelRepository,
    private val favoritesRepository: FavoritesRepository,
    val streamChecker: StreamChecker,
    private val preferences: AppPreferences
) : ViewModel() {

    /**
     * One menu section (TV channels or radio stations) with its own search and filters.
     * Both share the loaded channels, favourites and the stream checker.
     */
    inner class Section(val kind: MediaKind) {
        private val _uiState = MutableStateFlow(ChannelsUiState())
        val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

        // Counted off the main thread; recomputed only when statuses or the visible list change
        val statusCounts: StateFlow<StatusCounts> = combine(
            uiState.map { it.channels }.distinctUntilChanged(),
            urlStatuses
        ) { channels, statuses ->
            var online = 0
            var offline = 0
            var checking = 0
            for (channel in channels) {
                when (streamChecker.channelStatus(channel, statuses)) {
                    StreamStatus.ONLINE -> online++
                    StreamStatus.OFFLINE -> offline++
                    StreamStatus.CHECKING -> checking++
                    StreamStatus.UNKNOWN -> {}
                }
            }
            StatusCounts(online, offline, checking)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatusCounts())

        private var channels: List<Channel> = emptyList()

        // Normalized names (lowercase, no diacritics), same order as channels
        private var searchKeys: List<String> = emptyList()

        internal val hasStatusFilter: Boolean
            get() = _uiState.value.selectedStatus != StatusFilter.ALL

        internal fun setLoading() {
            _uiState.value = _uiState.value.copy(isLoading = true, hasError = false, error = null)
        }

        internal fun setError(message: String?) {
            _uiState.value = _uiState.value.copy(isLoading = false, hasError = true, error = message)
        }

        internal suspend fun setChannels(sectionChannels: List<Channel>) {
            val keys = withContext(Dispatchers.Default) { sectionChannels.map { it.name.toSearchKey() } }
            val (countries, categories) = withContext(Dispatchers.Default) {
                countsBy(sectionChannels) { listOf(it.country) } to countsBy(sectionChannels) { it.categories }
            }
            channels = sectionChannels
            searchKeys = keys
            val state = _uiState.value
            val countryCodes = countries.map { it.first }.toSet()
            val categoryKeys = categories.map { it.first }.toSet()
            _uiState.value = state.copy(
                isLoading = false,
                hasError = false,
                error = null,
                totalCount = sectionChannels.size,
                countries = countries,
                categories = categories,
                // A selected country or category the section no longer has would hide everything
                selectedCountries = state.selectedCountries intersect countryCodes,
                selectedCategories = state.selectedCategories intersect categoryKeys
            )
            applyFilters()
        }

        fun onSearchQueryChanged(query: String) {
            _uiState.value = _uiState.value.copy(searchQuery = query)
            applyFilters()
        }

        fun toggleCountry(country: String) {
            val current = _uiState.value.selectedCountries
            _uiState.value = _uiState.value.copy(
                selectedCountries = if (country in current) current - country else current + country
            )
            applyFilters()
        }

        fun toggleCategory(category: String) {
            val current = _uiState.value.selectedCategories
            _uiState.value = _uiState.value.copy(
                selectedCategories = if (category in current) current - category else current + category
            )
            applyFilters()
        }

        fun onStatusFilterChanged(status: StatusFilter) {
            _uiState.value = _uiState.value.copy(selectedStatus = status)
            applyFilters()
        }

        fun clearFilters() {
            _uiState.value = _uiState.value.copy(
                selectedCountries = emptySet(),
                selectedCategories = emptySet(),
                selectedStatus = StatusFilter.ALL
            )
            applyFilters()
        }

        /** Called when the section is shown, so its channels are checked first. */
        fun onShown() {
            activeSection = this
            streamChecker.prioritize(_uiState.value.channels)
        }

        /** Called when the list scrolls, so channels on screen are checked next. */
        fun onFirstVisibleIndexChanged(index: Int) {
            streamChecker.prioritize(_uiState.value.channels, index)
        }

        internal fun applyFilters() {
            val state = _uiState.value
            val query = state.searchQuery.trim().toSearchKey()
            val statuses = streamChecker.urlStatusMap.value
            // "Hide offline" would empty the list when the user explicitly asks for offline channels
            val skipOffline = hideOffline && state.selectedStatus != StatusFilter.OFFLINE
            val filtered = channels.filterIndexed { index, channel ->
                if (query.isNotEmpty() && !searchKeys[index].contains(query)) return@filterIndexed false
                if (state.selectedCountries.isNotEmpty() && channel.country !in state.selectedCountries) {
                    return@filterIndexed false
                }
                if (state.selectedCategories.isNotEmpty() &&
                    channel.categories.none { it in state.selectedCategories }
                ) {
                    return@filterIndexed false
                }
                val status = if (skipOffline || state.selectedStatus != StatusFilter.ALL) {
                    streamChecker.channelStatus(channel, statuses)
                } else {
                    null
                }
                if (skipOffline && status == StreamStatus.OFFLINE) return@filterIndexed false
                when (state.selectedStatus) {
                    StatusFilter.ALL -> true
                    StatusFilter.ONLINE -> status == StreamStatus.ONLINE
                    StatusFilter.OFFLINE -> status == StreamStatus.OFFLINE
                }
            }
            if (filtered == state.channels) return
            _uiState.value = state.copy(channels = filtered)
            if (activeSection === this) streamChecker.prioritize(filtered)
        }
    }

    val urlStatuses: StateFlow<Map<String, StreamStatus>> = streamChecker.urlStatusMap

    val tv = Section(MediaKind.TV)
    val radio = Section(MediaKind.RADIO)
    private val sections = listOf(tv, radio)

    fun section(kind: MediaKind): Section = when (kind) {
        MediaKind.TV -> tv
        MediaKind.RADIO -> radio
    }

    /** The section on screen; its channels are checked first */
    private var activeSection: Section = tv

    val favoriteIds: StateFlow<Set<String>> = favoritesRepository.favoriteIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val allChannelsFlow = MutableStateFlow<List<Channel>>(emptyList())

    val favoriteChannels: StateFlow<List<Channel>> = combine(allChannelsFlow, favoriteIds) { all, ids ->
        all.filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentChannels: StateFlow<List<Channel>> = combine(allChannelsFlow, preferences.recentIds) { all, ids ->
        val byId = all.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Channel id -> whether the player found sound only */
    private val detectedRadio: StateFlow<Map<String, Boolean>> = preferences.detectedRadio
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private var allChannels: List<Channel> = emptyList()

    private var hideOffline = false

    private var checkJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.hideOffline.collect {
                hideOffline = it
                sections.forEach { section -> section.applyFilters() }
            }
        }
        // Filters that depend on stream status follow the checks as they finish
        viewModelScope.launch {
            urlStatuses.collect {
                for (section in sections) {
                    if (hideOffline || section.hasStatusFilter) section.applyFilters()
                }
            }
        }
        // A channel moves to radio once the player finds it has sound only
        viewModelScope.launch {
            detectedRadio.drop(1).collect { if (allChannels.isNotEmpty()) splitIntoSections() }
        }
        loadChannels()
    }

    /** Radio by name, or because it streams sound only; a studio camera does not make a radio TV */
    fun isRadio(channel: Channel): Boolean = channel.looksLikeRadio || detectedRadio.value[channel.id] == true

    fun loadChannels(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            sections.forEach { it.setLoading() }

            val result = channelRepository.loadChannels(forceRefresh)

            result.fold(
                onSuccess = { channels ->
                    val sorted = withContext(Dispatchers.Default) {
                        // Alphabetical as people expect it ("ČT1" next to "CNN", not after "Z")
                        val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
                        channels.sortedWith(compareBy(collator) { it.name })
                    }
                    allChannels = sorted
                    allChannelsFlow.value = sorted
                    splitIntoSections()

                    if (preferences.checkStreamsOnStart.first()) {
                        startChecking(channels)
                    }
                },
                onFailure = { error ->
                    sections.forEach { it.setError(error.message) }
                }
            )
        }
    }

    private suspend fun splitIntoSections() {
        val (radioChannels, tvChannels) = allChannels.partition { isRadio(it) }
        tv.setChannels(tvChannels)
        radio.setChannels(radioChannels)
    }

    fun recheckStreams() {
        if (allChannels.isNotEmpty()) startChecking(allChannels)
    }

    private fun startChecking(channels: List<Channel>) {
        checkJob?.cancel()
        streamChecker.reset()
        checkJob = viewModelScope.launch { streamChecker.checkAll(channels) }
        streamChecker.prioritize(activeSection.uiState.value.channels)
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(channelId)
        }
    }

    fun onChannelPlayed(channelId: String) {
        viewModelScope.launch { preferences.addRecent(channelId) }
    }

    fun getChannel(id: String): Channel? = channelRepository.getChannel(id)

    /**
     * The chosen source first, then the others the player falls back to:
     * those found online first, the ones found offline last.
     */
    fun playbackOrder(channel: Channel, chosen: StreamSource): List<StreamSource> {
        val statuses = urlStatuses.value
        val rest = channel.sources.filter { it.url != chosen.url }.sortedBy {
            when (statuses[it.url]) {
                StreamStatus.ONLINE -> 0
                StreamStatus.OFFLINE -> 2
                else -> 1
            }
        }
        return listOf(chosen) + rest
    }

    private fun countsBy(channels: List<Channel>, keys: (Channel) -> List<String>): List<Pair<String, Int>> {
        val counts = HashMap<String, Int>()
        for (channel in channels) {
            for (key in keys(channel)) counts[key] = (counts[key] ?: 0) + 1
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    class Factory(
        private val channelRepository: ChannelRepository,
        private val favoritesRepository: FavoritesRepository,
        private val streamChecker: StreamChecker,
        private val preferences: AppPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChannelsViewModel(channelRepository, favoritesRepository, streamChecker, preferences) as T
        }
    }
}
