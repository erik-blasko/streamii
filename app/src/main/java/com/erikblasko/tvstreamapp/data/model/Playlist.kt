package com.erikblasko.tvstreamapp.data.model

import androidx.annotation.StringRes
import com.erikblasko.tvstreamapp.R

data class Playlist(
    val id: String,
    /** Proper name shown in settings ("Pluto TV"); [nameRes] wins when set */
    val name: String,
    val url: String,
    /** Provider the playlist is grouped under in settings */
    val provider: String,
    @StringRes val nameRes: Int? = null
) {
    /** Shown at each stream source, so it stays a proper name in every language */
    val sourceLabel: String get() = if (name == provider || nameRes != null) provider else name
}

// One playlist per catalog: countries and categories are filters, not separate playlists.
val defaultPlaylists = listOf(
    Playlist("all", "iptv-org", "https://iptv-org.github.io/iptv/index.m3u", "iptv-org"),

    Playlist("freetv", "Free-TV", "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8", "Free-TV"),

    Playlist("pluto_all", "Pluto TV", "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plutotv_all.m3u", "FAST"),
    Playlist("samsung_tv", "Samsung TV Plus", "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/samsungtvplus_all.m3u", "FAST"),
    Playlist("plex", "Plex", "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plex_all.m3u", "FAST"),
    Playlist("roku", "Roku", "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/roku_all.m3u", "FAST"),
    Playlist("xumo", "Xumo", "https://raw.githubusercontent.com/BuddyChewChew/xumo-playlist-generator/refs/heads/main/playlists/xumo_playlist.m3u", "FAST"),

    Playlist("freecast_news", "FreeCastHub", "https://raw.githubusercontent.com/freecasthub/public-iptv/main/news.m3u", "FreeCastHub", R.string.playlist_news),
    Playlist("freecast_sports", "FreeCastHub", "https://raw.githubusercontent.com/freecasthub/public-iptv/main/sports.m3u", "FreeCastHub", R.string.playlist_sports),

    Playlist("distrotv", "DistroTV", "https://www.apsattv.com/distro.m3u", "apsattv.com"),
    Playlist("freelivesports", "FreeLiveSports", "https://www.apsattv.com/freelivesports.m3u", "apsattv.com"),
    Playlist("lg_us", "LG Channels US", "https://www.apsattv.com/uslg.m3u", "apsattv.com"),
    Playlist("lg_de", "LG Channels DE", "https://www.apsattv.com/delg.m3u", "apsattv.com"),
    Playlist("firetv", "Fire TV", "https://www.apsattv.com/firetv.m3u", "apsattv.com"),
)

val defaultEnabledIds = setOf("all", "freetv", "freecast_news", "freecast_sports")

/**
 * Playlists removed in 1.1 because they were subsets of another one
 * (a country of iptv-org, Pluto TV of one country, a duplicate with EPG).
 * Ids of playlists that no longer exist (Tubi, PBS, TVPass) are simply dropped.
 */
val replacedPlaylistIds = mapOf(
    "sk" to "all", "cz" to "all", "sports" to "all",
    "by_country" to "all", "by_category" to "all", "by_language" to "all",
    "freetv_sk" to "freetv", "freetv_cz" to "freetv", "freetv_news" to "freetv",
    "pluto_de" to "pluto_all", "pluto_gb" to "pluto_all", "mjh_pluto" to "pluto_all",
    "mjh_plex" to "plex", "mjh_samsung" to "samsung_tv", "mjh_roku" to "roku"
)
