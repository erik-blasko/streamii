package com.erikblasko.tvstreamapp

import android.app.Application
import com.erikblasko.tvstreamapp.di.AppContainer

class StreamiiApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
