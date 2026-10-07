package com.erikblasko.tvstreamapp.util

import java.text.Normalizer

private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")

fun String.removeDiacritics(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return normalized.replace(diacriticsRegex, "")
}

/** Lowercase without diacritics, for fast repeated substring matching. */
fun String.toSearchKey(): String = removeDiacritics().lowercase()
