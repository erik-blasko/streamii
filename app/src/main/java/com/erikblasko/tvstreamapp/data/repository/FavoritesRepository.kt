package com.erikblasko.tvstreamapp.data.repository

import com.erikblasko.tvstreamapp.data.preferences.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FavoritesRepository(private val preferences: AppPreferences) {

    val favoriteIds: Flow<Set<String>> = preferences.favoriteIds

    suspend fun toggleFavorite(channelId: String) {
        val current = preferences.favoriteIds.first().toMutableSet()
        if (channelId in current) {
            current.remove(channelId)
        } else {
            current.add(channelId)
        }
        preferences.setFavoriteIds(current)
    }

    suspend fun isFavorite(channelId: String): Boolean {
        return channelId in preferences.favoriteIds.first()
    }
}
