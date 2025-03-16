package com.org.aichatbot.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity representing a message within a chat conversation
 */
@Entity(
    tableName = "messages",
    indices = [Index(value = ["chatId"])],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: String,
    val text: String,
    val isUserMessage: Boolean,
    val timestamp: Date = Date()
)
