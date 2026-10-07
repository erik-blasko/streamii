package com.erikblasko.tvstreamapp.data.model

data class StreamSource(
    val url: String,
    val quality: String?,
    val playlistName: String,
    /** Variant from the tvg-id suffix, e.g. "HD" or "Czech Republic" for "AMCEurope.uk@CzechRepublic" */
    val feed: String? = null,
    /** HTTP headers some streams require (http-user-agent / http-referrer in the playlist) */
    val userAgent: String? = null,
    val referrer: String? = null
) {
    companion object {
        /** Used by the player and the stream checker when the playlist sets no user agent */
        const val DEFAULT_USER_AGENT = "Streamii/1.0"
    }
}

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    /** Category keys, lowercase English as in iptv-org ("news", "sports"); see util/Categories.kt */
    val categories: List<String>,
    val country: String,
    val sources: List<StreamSource>,
    /** "Radio" in the name or group ("Rádio Expres"); sound-only streams are radio too, see [MediaKind] */
    val looksLikeRadio: Boolean = false
) {
    val primarySource: StreamSource get() = sources.first()
    val hasMultipleSources: Boolean get() = sources.size > 1

    companion object {
        /** Prefix of ids for channels without tvg-id, followed by the normalized name */
        const val NAME_ID_PREFIX = "name:"

        /** Country code for channels whose country is not known */
        const val UNKNOWN_COUNTRY = "XX"
    }
}

/** Which menu section a channel belongs to. */
enum class MediaKind { TV, RADIO }
