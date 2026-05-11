package com.livespeaker.app

import android.app.Application

class LiveSpeakerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: LiveSpeakerApp
            private set
    }
}
