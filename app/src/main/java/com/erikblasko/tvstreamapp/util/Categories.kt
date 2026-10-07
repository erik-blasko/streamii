package com.erikblasko.tvstreamapp.util

import androidx.annotation.StringRes
import com.erikblasko.tvstreamapp.R

const val UNDEFINED_CATEGORY = "undefined"

/** What a playlist's group-title means: most are categories, some playlists put the country there. */
sealed interface GroupMeaning {
    data class Country(val code: String) : GroupMeaning
    data class Category(val key: String) : GroupMeaning
}

private val isoCountries = java.util.Locale.getISOCountries().toSet()
private val languageSuffix = Regex("\\s*\\([a-z]{2}\\)$")

/**
 * "Sports" -> category "sports"; "Slovakia", "NZ", "VOD Italy", "日本 / Japan" -> a country;
 * "Local News", "News (AR)", "Food" -> the matching iptv-org category.
 */
fun interpretGroupTitle(groupTitle: String): GroupMeaning {
    val title = groupTitle.trim()
    // Plex uses the bare country code ("NZ")
    if (title.length == 2 && title.all { it in 'A'..'Z' } && title in isoCountries) {
        return GroupMeaning.Country(title)
    }
    // Free-TV: "日本 / Japan" (native / English name)
    val name = title.substringAfterLast(" / ")
    // Free-TV: "VOD Italy", "VOD Movies (EN)", "News (AR)"
    val key = name.lowercase().removePrefix("vod ").replace(languageSuffix, "").trim()
    countryCodeFromName(key)?.let { return GroupMeaning.Country(it) }
    return GroupMeaning.Category(categorySynonyms[key] ?: key)
}

/** Group names of FAST playlists (Xumo, Roku, Free-TV) mapped to iptv-org categories. */
private val categorySynonyms = mapOf(
    "local news" to "news",
    "movies" to "movies", "action & drama" to "series", "horror & sci-fi" to "movies",
    "romance" to "movies", "westerns & country" to "movies", "season of scares" to "movies",
    "crime" to "series", "crime tv" to "series", "crime drama" to "series", "law" to "series",
    "classic tv" to "classic",
    "tv & entertainment" to "entertainment", "reality tv" to "entertainment",
    "game shows" to "entertainment", "game show" to "entertainment", "daytime tv" to "entertainment",
    "pop culture" to "entertainment", "paranormal" to "entertainment", "card games" to "entertainment",
    "gaming & tech" to "entertainment", "latino" to "general",
    "home & design" to "lifestyle", "home improvement" to "lifestyle", "house/garden" to "lifestyle",
    "health" to "lifestyle", "travel & lifestyle" to "travel",
    "animals & nature" to "documentary", "animals" to "documentary", "environment" to "documentary",
    "documentaries" to "documentary", "history & learning" to "education", "educational" to "education",
    "faith & family" to "religious", "food" to "cooking",
    "music & radio" to "music", "children-music" to "music",
    "automotive" to "auto", "auto & motorsports" to "auto",
    "combat sports" to "sports", "pro wrestling" to "sports", "3x3 basketball" to "sports",
    "freelivesports" to "sports",
    "animated" to "animation", "anime" to "animation",
    "auction" to "shop", "shopping" to "shop", "art" to "culture",
    "other" to UNDEFINED_CATEGORY
)

/** Translations of the iptv-org categories; other playlists' own groups are shown as they are. */
private val categoryNames = mapOf(
    "animation" to R.string.category_animation,
    "auto" to R.string.category_auto,
    "business" to R.string.category_business,
    "classic" to R.string.category_classic,
    "comedy" to R.string.category_comedy,
    "cooking" to R.string.category_cooking,
    "culture" to R.string.category_culture,
    "documentary" to R.string.category_documentary,
    "education" to R.string.category_education,
    "entertainment" to R.string.category_entertainment,
    "family" to R.string.category_family,
    "general" to R.string.category_general,
    "interactive" to R.string.category_interactive,
    "kids" to R.string.category_kids,
    "legislative" to R.string.category_legislative,
    "lifestyle" to R.string.category_lifestyle,
    "movies" to R.string.category_movies,
    "music" to R.string.category_music,
    "news" to R.string.category_news,
    "outdoor" to R.string.category_outdoor,
    "public" to R.string.category_public,
    "relax" to R.string.category_relax,
    "religious" to R.string.category_religious,
    "science" to R.string.category_science,
    "series" to R.string.category_series,
    "shop" to R.string.category_shop,
    "sports" to R.string.category_sports,
    "travel" to R.string.category_travel,
    "weather" to R.string.category_weather,
    UNDEFINED_CATEGORY to R.string.category_undefined
)

@StringRes
fun categoryNameRes(key: String): Int? = categoryNames[key]

/** Label for a category without translation: "home & design" -> "Home & Design". */
fun categoryFallbackName(key: String): String =
    key.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
