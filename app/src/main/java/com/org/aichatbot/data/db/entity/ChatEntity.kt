package com.org.aichatbot.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity representing a chat conversation in the database
 */
@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val summary: String?,
    val category: String,
    val messageCount: Int = 0,
    val timestamp: Date = Date(),
    val lastUpdated: Date = Date()
)
