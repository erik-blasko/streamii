package com.erikblasko.tvstreamapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.erikblasko.tvstreamapp.data.model.defaultEnabledIds
import com.erikblasko.tvstreamapp.data.model.defaultPlaylists
import com.erikblasko.tvstreamapp.data.model.replacedPlaylistIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tv_stream_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val ENABLED_PLAYLIST_IDS = stringSetPreferencesKey("enabled_playlist_ids")
        private val CUSTOM_PLAYLIST_URLS = stringSetPreferencesKey("custom_playlist_urls")
        private val CHECK_STREAMS_ON_START = booleanPreferencesKey("check_streams_on_start")
        private val FAVORITE_IDS = stringSetPreferencesKey("favorite_ids")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val HIDE_OFFLINE = booleanPreferencesKey("hide_offline")
        private val RECENT_IDS = stringPreferencesKey("recent_ids")
        // What the player found out: channels whose stream has only sound / has a picture
        private val AUDIO_ONLY_IDS = stringSetPreferencesKey("audio_only_ids")
        private val VIDEO_IDS = stringSetPreferencesKey("video_ids")
        private val LIVE_DELAY = booleanPreferencesKey("live_delay")

        private const val MAX_RECENT = 30
        private const val RECENT_SEPARATOR = '\n'
    }

    val enabledPlaylistIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val stored = prefs[ENABLED_PLAYLIST_IDS] ?: return@map defaultEnabledIds
        // Ids of playlists removed in 1.1 map to the playlist that contains them
        val knownIds = defaultPlaylists.map { it.id }.toSet()
        stored.map { replacedPlaylistIds[it] ?: it }.filter { it in knownIds }.toSet()
    }

    val customPlaylistUrls: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[CUSTOM_PLAYLIST_URLS] ?: emptySet()
    }

    val checkStreamsOnStart: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CHECK_STREAMS_ON_START] ?: true
    }

    val favoriteIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        // Older versions stored ids with the feed suffix ("Markiza.sk@HD")
        prefs[FAVORITE_IDS]?.map { it.substringBefore('@').lowercase() }?.toSet() ?: emptySet()
    }

    val hideOffline: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HIDE_OFFLINE] ?: false
    }

    /** Recently played channel ids, newest first */
    val recentIds: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[RECENT_IDS]?.split(RECENT_SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()
    }

    /** Channel id -> true when the player found only sound, false when it found a picture */
    val detectedRadio: Flow<Map<String, Boolean>> = context.dataStore.data.map { prefs ->
        (prefs[VIDEO_IDS] ?: emptySet()).associateWith { false } +
            (prefs[AUDIO_ONLY_IDS] ?: emptySet()).associateWith { true }
    }

    /** Live streams play ~30 s behind the live edge, so a slow connection stalls less */
    val liveDelay: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LIVE_DELAY] ?: false
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: "system"
    }

    suspend fun setEnabledPlaylistIds(ids: Set<String>) {
        context.dataStore.edit { it[ENABLED_PLAYLIST_IDS] = ids }
    }

    suspend fun setCustomPlaylistUrls(urls: Set<String>) {
        context.dataStore.edit { it[CUSTOM_PLAYLIST_URLS] = urls }
    }

    suspend fun setCheckStreamsOnStart(enabled: Boolean) {
        context.dataStore.edit { it[CHECK_STREAMS_ON_START] = enabled }
    }

    suspend fun setFavoriteIds(ids: Set<String>) {
        context.dataStore.edit { it[FAVORITE_IDS] = ids }
    }

    suspend fun setHideOffline(hide: Boolean) {
        context.dataStore.edit { it[HIDE_OFFLINE] = hide }
    }

    suspend fun addRecent(channelId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[RECENT_IDS]?.split(RECENT_SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()
            prefs[RECENT_IDS] = (listOf(channelId) + current.filter { it != channelId })
                .take(MAX_RECENT)
                .joinToString(RECENT_SEPARATOR.toString())
        }
    }

    suspend fun setDetectedRadio(channelId: String, isRadio: Boolean) {
        context.dataStore.edit { prefs ->
            val audio = prefs[AUDIO_ONLY_IDS] ?: emptySet()
            val video = prefs[VIDEO_IDS] ?: emptySet()
            prefs[AUDIO_ONLY_IDS] = if (isRadio) audio + channelId else audio - channelId
            prefs[VIDEO_IDS] = if (isRadio) video - channelId else video + channelId
        }
    }

    suspend fun setLiveDelay(delay: Boolean) {
        context.dataStore.edit { it[LIVE_DELAY] = delay }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }
}
