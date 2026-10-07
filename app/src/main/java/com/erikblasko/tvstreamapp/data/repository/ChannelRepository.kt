package com.erikblasko.tvstreamapp.data.repository

import com.erikblasko.tvstreamapp.data.model.Channel
import com.erikblasko.tvstreamapp.data.model.defaultPlaylists
import com.erikblasko.tvstreamapp.data.parser.M3uParser
import com.erikblasko.tvstreamapp.data.preferences.AppPreferences
import com.erikblasko.tvstreamapp.util.UNDEFINED_CATEGORY
import com.erikblasko.tvstreamapp.util.channelNameKey
import com.erikblasko.tvstreamapp.util.cleanChannelName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

class ChannelRepository(
    private val httpClient: OkHttpClient,
    private val parser: M3uParser,
    private val preferences: AppPreferences
) {

    private var cachedChannels: List<Channel> = emptyList()
    private var cachedPlaylistKey: Set<String> = emptySet()

    suspend fun loadChannels(forceRefresh: Boolean = false): Result<List<Channel>> {
        val enabledIds = preferences.enabledPlaylistIds.first()
        val customUrls = preferences.customPlaylistUrls.first()
        val playlistKey = enabledIds + customUrls

        if (!forceRefresh && cachedPlaylistKey == playlistKey && cachedChannels.isNotEmpty()) {
            return Result.success(cachedChannels)
        }

        return withContext(Dispatchers.IO) {
            try {
                data class PlaylistLoad(val url: String, val name: String)

                val loads = mutableListOf<PlaylistLoad>()
                defaultPlaylists.filter { it.id in enabledIds }.forEach {
                    loads.add(PlaylistLoad(it.url, it.sourceLabel))
                }
                customUrls.forEach { url ->
                    loads.add(PlaylistLoad(url, url.toHttpUrlOrNull()?.host ?: url))
                }

                if (loads.isEmpty()) {
                    cachedChannels = emptyList()
                    cachedPlaylistKey = playlistKey
                    return@withContext Result.success(emptyList())
                }

                val allChannels = coroutineScope {
                    loads.map { load ->
                        async { loadFromUrl(load.url, load.name) }
                    }.awaitAll()
                }.flatten()

                val merged = mergeChannels(allChannels)

                cachedChannels = merged
                cachedPlaylistKey = playlistKey
                Result.success(merged)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun loadFromUrl(url: String, playlistName: String): List<Channel> {
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body ?: return emptyList()
                parser.parse(body.byteStream(), extractCountryFromUrl(url), playlistName)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mergeChannels(channels: List<Channel>): List<Channel> {
        // 1. Group by station id (tvg-id without feed), shared across playlists even when
        //    names differ ("Doma" vs "Markíza Doma"). Channels without tvg-id have a name id.
        val byId = LinkedHashMap<String, MutableList<Channel>>()
        for (channel in channels) {
            byId.getOrPut(channel.id) { mutableListOf() }.add(channel)
        }

        // 2. Some playlists use old or custom ids for the same station ("stv1.sk" vs
        //    "jednotka.sk", "redbull_tv"). Merge groups with the same name if they are from
        //    the same country or one id is not a standard station id. Standard ids go first,
        //    so they become the id of the merged channel.
        val groups = LinkedHashMap<String, MutableList<Channel>>()
        val idsByName = HashMap<String, MutableList<String>>()
        for ((id, group) in byId.entries.sortedBy { !isStationId(it.key) }) {
            val nameKey = channelNameKey(bestName(group))
            val country = group.first().country
            val target = idsByName[nameKey]?.firstOrNull { other ->
                groups.getValue(other).first().country == country ||
                    !isStationId(id) || !isStationId(other)
            }
            if (target != null) {
                groups.getValue(target).addAll(group)
            } else {
                groups[id] = group
                idsByName.getOrPut(nameKey) { mutableListOf() }.add(id)
            }
        }

        return groups.map { (id, group) ->
            val categories = group.flatMap { it.categories }.distinct()
            group.first().copy(
                id = id,
                name = bestName(group),
                logoUrl = group.firstNotNullOfOrNull { it.logoUrl },
                categories = categories.filter { it != UNDEFINED_CATEGORY }.ifEmpty { categories },
                country = group.map { it.country }.firstOrNull { it != Channel.UNKNOWN_COUNTRY }
                    ?: Channel.UNKNOWN_COUNTRY,
                sources = group.flatMap { it.sources }.distinctBy { it.url },
                looksLikeRadio = group.any { it.looksLikeRadio }
            )
        }
    }

    /** iptv-org style id like "markiza.sk" (lowercase, ends with a country code) */
    private fun isStationId(id: String): Boolean = stationIdPattern.matches(id)

    private val stationIdPattern = Regex("^[a-z0-9+&-]+\\.[a-z]{2}$")

    /**
     * Most common name (ignoring case and diacritics); among its spellings prefer
     * one with diacritics ("Markíza" over "Markiza"), then the shorter one.
     */
    private fun bestName(group: List<Channel>): String {
        val names = group.map { cleanChannelName(it.name) }
        val counts = names.groupingBy { channelNameKey(it) }.eachCount()
        return names.maxWith(
            compareBy<String> { counts.getValue(channelNameKey(it)) }
                .thenBy { name -> name.any { it.code > 127 && it.isLetter() } }
                .thenByDescending { it.length }
        )
    }

    suspend fun loadFromStream(inputStream: InputStream, defaultCountry: String = ""): Result<List<Channel>> {
        return withContext(Dispatchers.IO) {
            try {
                val channels = parser.parse(inputStream, defaultCountry, "M3U")
                val merged = mergeChannels(cachedChannels + channels)
                cachedChannels = merged
                cachedPlaylistKey = emptySet()
                Result.success(cachedChannels)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun getCachedChannels(): List<Channel> = cachedChannels

    fun getChannel(id: String): Channel? = cachedChannels.firstOrNull { it.id == id }

    private val urlCountryPatterns = listOf(
        Regex("/countries/([a-z]{2})\\.m3u"),
        Regex("/playlist_([a-z]{2})\\.m3u", RegexOption.IGNORE_CASE),
        Regex("plutotv_([a-z]{2})\\.m3u"),
        Regex("plex_([a-z]{2})\\.m3u"),
        Regex("samsungtvplus_([a-z]{2})\\.m3u"),
        Regex("/([a-z]{2})lg\\.m3u"),
        Regex("ssung([a-z]{2,3})\\.m3u"),
    )

    private val urlCountryNames = mapOf(
        "playlist_slovakia" to "SK",
        "playlist_czech_republic" to "CZ",
        "playlist_germany" to "DE",
        "playlist_france" to "FR",
        "playlist_united_kingdom" to "GB",
        "playlist_spain" to "ES",
        "playlist_italy" to "IT",
        "playlist_netherlands" to "NL",
        "playlist_portugal" to "PT",
    )

    private fun extractCountryFromUrl(url: String): String {
        for ((name, code) in urlCountryNames) {
            if (url.contains(name, ignoreCase = true)) return code
        }
        for (pattern in urlCountryPatterns) {
            val match = pattern.find(url)
            if (match != null) {
                val code = match.groupValues[1].uppercase()
                if (code.length == 2) return code
            }
        }
        return ""
    }
}
