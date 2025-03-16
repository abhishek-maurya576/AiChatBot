package com.org.aichatbot

import android.app.Application
import com.org.aichatbot.data.db.ChatDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AIChatBotApplication : Application() {
    lateinit var database: ChatDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = ChatDatabase.getDatabase(this)
    }
}
