package com.org.aichatbot.di

import android.content.Context
import com.org.aichatbot.data.db.ChatDatabase
import com.org.aichatbot.data.repository.ChatRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideChatDatabase(@ApplicationContext context: Context): ChatDatabase {
        return ChatDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideChatRepository(database: ChatDatabase): ChatRepository {
        return ChatRepository(database.chatDao(), database.messageDao())
    }
} 