package com.erikblasko.tvstreamapp.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.erikblasko.tvstreamapp.data.model.defaultEnabledIds
import com.erikblasko.tvstreamapp.data.preferences.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: AppPreferences
) : ViewModel() {

    val enabledPlaylistIds: StateFlow<Set<String>> = preferences.enabledPlaylistIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultEnabledIds)

    val customPlaylistUrls: StateFlow<Set<String>> = preferences.customPlaylistUrls
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val checkStreamsOnStart: StateFlow<Boolean> = preferences.checkStreamsOnStart
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val hideOffline: StateFlow<Boolean> = preferences.hideOffline
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val themeMode: StateFlow<String> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    fun togglePlaylist(playlistId: String) {
        val current = enabledPlaylistIds.value
        setPlaylistsEnabled(listOf(playlistId), playlistId !in current)
    }

    /** Turns a whole provider group on or off. */
    fun setPlaylistsEnabled(playlistIds: List<String>, enabled: Boolean) {
        viewModelScope.launch {
            val current = enabledPlaylistIds.value
            preferences.setEnabledPlaylistIds(if (enabled) current + playlistIds else current - playlistIds.toSet())
        }
    }

    fun addCustomUrl(url: String) {
        viewModelScope.launch {
            preferences.setCustomPlaylistUrls(customPlaylistUrls.value + url)
        }
    }

    fun removeCustomUrl(url: String) {
        viewModelScope.launch {
            preferences.setCustomPlaylistUrls(customPlaylistUrls.value - url)
        }
    }

    fun setCheckStreamsOnStart(enabled: Boolean) {
        viewModelScope.launch { preferences.setCheckStreamsOnStart(enabled) }
    }

    fun setHideOffline(hide: Boolean) {
        viewModelScope.launch { preferences.setHideOffline(hide) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    class Factory(private val preferences: AppPreferences) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferences) as T
        }
    }
}
