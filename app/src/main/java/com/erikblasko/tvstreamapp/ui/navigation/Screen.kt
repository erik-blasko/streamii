package com.erikblasko.tvstreamapp.ui.navigation

object Routes {
    const val CHANNELS = "channels"
    const val RADIO = "radio"
    const val FAVORITES = "favorites"
    const val RECENT = "recent"
    const val SETTINGS = "settings"
    const val PLAYLISTS = "playlists"
    const val DETAIL = "channel/{id}"

    fun detail(channelId: String) = "channel/" + android.net.Uri.encode(channelId)

    /** Screens reachable from the side menu; the menu can be swiped open on these. */
    val topLevel = setOf(CHANNELS, RADIO, FAVORITES, RECENT, SETTINGS)
}
