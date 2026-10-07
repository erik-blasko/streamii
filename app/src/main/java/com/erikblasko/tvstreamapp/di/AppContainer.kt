package com.erikblasko.tvstreamapp.di

import android.content.Context
import com.erikblasko.tvstreamapp.data.network.StreamChecker
import com.erikblasko.tvstreamapp.data.parser.M3uParser
import com.erikblasko.tvstreamapp.data.preferences.AppPreferences
import com.erikblasko.tvstreamapp.data.repository.ChannelRepository
import com.erikblasko.tvstreamapp.data.repository.FavoritesRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val parser = M3uParser()

    val preferences = AppPreferences(context)

    val channelRepository = ChannelRepository(httpClient, parser, preferences)

    val favoritesRepository = FavoritesRepository(preferences)

    val streamChecker = StreamChecker(httpClient)
}
