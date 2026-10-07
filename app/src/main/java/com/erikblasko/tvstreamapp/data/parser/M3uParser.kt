package com.erikblasko.tvstreamapp.data.parser

import com.erikblasko.tvstreamapp.data.model.Channel
import com.erikblasko.tvstreamapp.data.model.StreamSource
import com.erikblasko.tvstreamapp.util.GroupMeaning
import com.erikblasko.tvstreamapp.util.UNDEFINED_CATEGORY
import com.erikblasko.tvstreamapp.util.channelNameKey
import com.erikblasko.tvstreamapp.util.feedLabel
import com.erikblasko.tvstreamapp.util.interpretGroupTitle
import com.erikblasko.tvstreamapp.util.looksLikeRadio
import com.erikblasko.tvstreamapp.util.normalizeCountryCode
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class M3uParser {

    private companion object {
        const val RADIO_GROUP = "radio"
    }

    private val qualityPattern = Regex("\\((\\d{3,4}[pi])\\)")

    fun parse(inputStream: InputStream, defaultCountry: String = "", playlistName: String = ""): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var currentExtInf: String? = null
        // "#EXTVLCOPT:http-user-agent=..." lines between #EXTINF and the URL
        val vlcOptions = mutableMapOf<String, String>()

        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#EXTINF:") -> {
                        currentExtInf = trimmed
                        vlcOptions.clear()
                    }
                    trimmed.startsWith("#EXTVLCOPT:") -> {
                        val option = trimmed.removePrefix("#EXTVLCOPT:")
                        val separator = option.indexOf('=')
                        if (separator > 0) {
                            val value = option.substring(separator + 1).trim()
                            if (value.isNotEmpty()) vlcOptions[option.substring(0, separator).trim().lowercase()] = value
                        }
                    }
                    trimmed.isNotEmpty() && !trimmed.startsWith("#") && currentExtInf != null -> {
                        parseChannel(currentExtInf!!, trimmed, defaultCountry, playlistName, vlcOptions)?.let {
                            channels.add(it)
                        }
                        currentExtInf = null
                        vlcOptions.clear()
                    }
                }
            }
        }

        return channels
    }

    private fun parseChannel(
        extInf: String,
        url: String,
        defaultCountry: String,
        playlistName: String,
        vlcOptions: Map<String, String>
    ): Channel? {
        val name = extractName(extInf) ?: return null
        val tvgId = extractAttribute(extInf, "tvg-id")
        val tvgCountry = extractAttribute(extInf, "tvg-country")
        val logoUrl = extractAttribute(extInf, "tvg-logo")
        val rawGroup = extractAttribute(extInf, "group-title").orEmpty()
        var country = normalizeCountryCode(extractCountry(tvgCountry, tvgId, defaultCountry))
        val quality = extractQuality(name)
        // iptv-org ids look like "Markiza.sk@HD": the part before '@' identifies the
        // station across playlists, the part after it is the feed (quality, region, language).
        val stationId = tvgId?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() }
        val feed = tvgId?.substringAfter('@', "")?.trim()?.takeIf { it.isNotEmpty() }
        val id = stationId?.lowercase() ?: (Channel.NAME_ID_PREFIX + channelNameKey(name))

        // group-title holds categories ("Outdoor;Sports"), some playlists put the country there
        val categories = mutableListOf<String>()
        for (part in rawGroup.split(';')) {
            val value = part.trim()
            if (value.isEmpty()) continue
            when (val meaning = interpretGroupTitle(value)) {
                is GroupMeaning.Country -> if (country == Channel.UNKNOWN_COUNTRY) country = meaning.code
                is GroupMeaning.Category -> categories += meaning.key
            }
        }
        // Some playlists group stations under "Radio"; that is a menu section, not a category
        val radioGroup = categories.remove(RADIO_GROUP)
        if (categories.isEmpty()) categories += UNDEFINED_CATEGORY

        val source = StreamSource(
            url = url,
            quality = quality,
            playlistName = playlistName,
            feed = feed?.let { feedLabel(it) },
            userAgent = vlcOptions["http-user-agent"] ?: extractAttribute(extInf, "http-user-agent"),
            referrer = vlcOptions["http-referrer"] ?: extractAttribute(extInf, "http-referrer")
        )

        return Channel(
            id = id,
            name = name,
            logoUrl = logoUrl?.takeIf { it.isNotBlank() },
            categories = categories.distinct(),
            country = country,
            sources = listOf(source),
            looksLikeRadio = radioGroup || looksLikeRadio(name)
        )
    }

    private fun extractName(extInf: String): String? {
        val commaIndex = extInf.lastIndexOf(',')
        if (commaIndex == -1) return null
        return extInf.substring(commaIndex + 1).trim().takeIf { it.isNotEmpty() }
    }

    private fun extractAttribute(extInf: String, attr: String): String? {
        val key = "$attr=\""
        val startIndex = extInf.indexOf(key)
        if (startIndex == -1) return null
        val valueStart = startIndex + key.length
        val endIndex = extInf.indexOf('"', valueStart)
        if (endIndex == -1) return null
        return extInf.substring(valueStart, endIndex).takeIf { it.isNotBlank() }
    }

    private fun extractCountry(tvgCountry: String?, tvgId: String?, defaultCountry: String): String {
        if (!tvgCountry.isNullOrBlank()) {
            val code = tvgCountry.split(";", ",").first().trim().uppercase()
            if (code.length == 2) return code
        }
        if (tvgId != null) {
            val dotIndex = tvgId.lastIndexOf('.')
            if (dotIndex != -1 && dotIndex < tvgId.length - 1) {
                val suffix = tvgId.substring(dotIndex + 1).lowercase()
                val countryPart = suffix.split("@").first()
                if (countryPart.length == 2) {
                    return countryPart.uppercase()
                }
            }
        }
        return defaultCountry.uppercase().ifEmpty { Channel.UNKNOWN_COUNTRY }
    }

    private fun extractQuality(name: String): String? {
        return qualityPattern.find(name)?.groupValues?.get(1)
    }
}
