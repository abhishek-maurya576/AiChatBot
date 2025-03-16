package com.org.aichatbot.data.repository

import android.util.Log
import com.org.aichatbot.data.db.dao.ChatDao
import com.org.aichatbot.data.db.dao.MessageDao
import com.org.aichatbot.data.db.entity.ChatEntity
import com.org.aichatbot.data.db.entity.MessageEntity
import com.org.aichatbot.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID

/**
 * Repository for managing chat data using Room database
 */
class ChatRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) {
    companion object {
        private const val TAG = "ChatRepository"
    }

    // Chat operations
    suspend fun createNewChat(category: String = "general"): String {
        val chatId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val chat = ChatEntity(
            id = chatId,
            title = "Chat ${timestamp.toString().takeLast(4)}", // Generate a unique initial title
            summary = null,
            category = category,
            messageCount = 0,
            lastUpdated = Date(timestamp)
        )
        chatDao.insertChat(chat)
        Log.d(TAG, "Created new chat with ID: $chatId and title: ${chat.title}")
        return chatId
    }

    fun getAllChats(): Flow<List<ChatEntity>> = chatDao.getAllChats()

    fun getChatsByCategory(category: String): Flow<List<ChatEntity>> = 
        chatDao.getChatsByCategory(category)

    fun getAllCategories(): Flow<List<String>> {
        return chatDao.getAllCategories()
    }

    suspend fun getChatById(chatId: String): ChatEntity? {
        return chatDao.getChatById(chatId)
    }

    // Message operations
    fun getMessagesForChat(chatId: String): Flow<List<ChatMessage>> =
        messageDao.getMessagesForChat(chatId)
            .map { messages ->
                messages
                    .map { it.toChatMessage() }
                    .sortedByDescending { it.timestamp }  // Sort by newest first
            }

    suspend fun saveMessage(chatId: String, message: ChatMessage) {
        val messageEntity = MessageEntity(
            chatId = chatId,
            text = message.text,
            isUserMessage = message.isUserMessage,
            timestamp = Date(message.timestamp)
        )
        
        try {
            // Insert message
            messageDao.insertMessage(messageEntity)
            
            // Update chat's message count, last updated time, and title if it's the first user message
            val chat = chatDao.getChatById(chatId)
            if (chat != null) {
                val updatedChat = chat.copy(
                    messageCount = chat.messageCount + 1,
                    lastUpdated = Date(),
                    // Update title if this is the first user message
                    title = if (message.isUserMessage && chat.messageCount == 0) {
                        generateTitleFromMessage(message.text)
                    } else chat.title
                )
                chatDao.insertChat(updatedChat)
                Log.d(TAG, "Updated chat: ${updatedChat.title} with message count: ${updatedChat.messageCount}")
            }
            Log.d(TAG, "Message saved successfully: ${message.text.take(50)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving message: ${e.message}", e)
            throw e
        }
    }

    suspend fun saveChatSummary(chatId: String, summary: String) {
        chatDao.updateChatSummary(chatId, summary)
    }

    suspend fun updateChatTitle(chatId: String, title: String) {
        chatDao.updateChatTitle(chatId, title)
    }

    suspend fun deleteChat(chatId: String) {
        chatDao.getChatById(chatId)?.let {
            chatDao.deleteChat(it)
        }
    }

    // Helper method to generate a title for a chat based on its first few messages
    suspend fun generateChatTitle(chatId: String): String {
        val messages = messageDao.getMessagesByChatIdSync(chatId)
        if (messages.isEmpty()) {
            return "New Chat"
        }
        
        // Get the first user message, which typically indicates the topic
        val firstUserMessage = messages.find { it.isUserMessage }?.text ?: messages.first().text
        
        // Truncate and clean the title
        val title = if (firstUserMessage.length > 30) {
            firstUserMessage.substring(0, 30).trim() + "..."
        } else {
            firstUserMessage.trim()
        }
        
        // Update the chat title in the database
        chatDao.updateChatTitle(chatId, title)
        
        return title
    }

    private fun generateTitleFromMessage(text: String): String {
        // Clean and truncate the message to create a title
        val cleanText = text.trim()
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
        
        return if (cleanText.length > 30) {
            cleanText.substring(0, 30).trim() + "..."
        } else {
            cleanText
        }
    }

    private fun MessageEntity.toChatMessage() = ChatMessage(
        text = text,
        isUserMessage = isUserMessage,
        timestamp = timestamp.time
    )
}
