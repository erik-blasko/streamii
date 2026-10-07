package com.erikblasko.tvstreamapp.util

private val resolutionTag = Regex("\\(\\s*\\d{3,4}[pi]\\s*\\)", RegexOption.IGNORE_CASE)
private val bracketTag = Regex("\\[[^]]*]")
private val enclosedSymbol = Regex("[\\u2460-\\u24FF]") // e.g. Ⓢ, ⓢ
private val whitespace = Regex("\\s+")
private val qualitySuffix = Regex("\\s+(hd|sd|fhd|uhd|4k)$")
// Matched on the search key (no diacritics), so "Rádio" and "Rádió" count too; "Radiožurnál" as well
private val radioWord = Regex("(?<![a-z])(radio|radyo|radijo)")
// Radio and television broadcasters: "Radio Televizija Srbije", "Ici Radio-Canada Télé"
private val broadcasterWord = Regex("televi[sz]|(?<![a-z])tele(?![a-z])")
private val camelCaseBoundary = Regex("(?<=[a-z])(?=[A-Z])")

/** Channel name for display, without tags like "(1080p)", "[Geo-blocked]" or "ⓢ". */
fun cleanChannelName(name: String): String =
    name.replace(resolutionTag, " ")
        .replace(bracketTag, " ")
        .replace(enclosedSymbol, " ")
        .replace(whitespace, " ")
        .trim()
        .ifEmpty { name.trim() }

/** Key for merging channels that have no tvg-id: lowercase, no diacritics, no quality suffix. */
fun channelNameKey(name: String): String =
    cleanChannelName(name).toSearchKey().replace(qualitySuffix, "").trim()

/** "CzechRepublic" -> "Czech Republic" */
fun feedLabel(feed: String): String = feed.replace(camelCaseBoundary, " ")

/**
 * A station with "Radio" in the name is radio, also when it streams a studio camera ("Radio X TV").
 * Stations without it become radio once the player finds they have sound only.
 */
fun looksLikeRadio(name: String): Boolean {
    val key = cleanChannelName(name).toSearchKey()
    return radioWord.containsMatchIn(key) && !broadcasterWord.containsMatchIn(key)
}
