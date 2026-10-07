package com.erikblasko.tvstreamapp.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * App language chosen in settings. Kept in SharedPreferences (not DataStore) because
 * it is needed synchronously in attachBaseContext, before any coroutine can run.
 */
object AppLocale {
    const val SYSTEM = "system"
    val supported = listOf("sk", "en")

    private const val PREFS = "app_locale"
    private const val KEY_LANGUAGE = "language"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM

    fun set(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    /** Wraps an activity's base context so its resources use the chosen language. */
    fun wrap(base: Context): Context {
        val language = get(base)
        if (language == SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(language))
        return base.createConfigurationContext(config)
    }
}
