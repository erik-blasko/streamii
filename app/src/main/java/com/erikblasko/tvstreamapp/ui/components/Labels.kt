package com.erikblasko.tvstreamapp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.erikblasko.tvstreamapp.R
import com.erikblasko.tvstreamapp.data.model.Channel
import com.erikblasko.tvstreamapp.util.categoryFallbackName
import com.erikblasko.tvstreamapp.util.categoryNameRes
import com.erikblasko.tvstreamapp.util.countryDisplayName
import java.util.Locale

/** Locale of the app's resources (follows the language chosen in settings). */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
@ReadOnlyComposable
fun countryName(code: String): String =
    countryDisplayName(code, appLocale()) ?: stringResource(R.string.country_unknown)

@Composable
@ReadOnlyComposable
fun categoryName(key: String): String =
    categoryNameRes(key)?.let { stringResource(it) } ?: categoryFallbackName(key)

/** "News · 3 sources" */
@Composable
@ReadOnlyComposable
fun channelMeta(channel: Channel): String {
    val categories = channel.categories.map { categoryName(it) }.joinToString(", ")
    if (!channel.hasMultipleSources) return categories
    val sources = pluralStringResource(R.plurals.sources_count, channel.sources.size, channel.sources.size)
    return "$categories · $sources"
}
