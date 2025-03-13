package com.org.aichatbot.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.org.aichatbot.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class SavedChat(
    val id: String,
    val messages: List<ChatMessage>,
    val summary: String?,
    val category: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatStorage(private val context: Context) {
    private val sharedPreferences = context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    suspend fun saveChat(
        chatId: String,
        messages: List<ChatMessage>,
        summary: String?,
        category: String
    ) = withContext(Dispatchers.IO) {
        val savedChat = SavedChat(
            id = chatId,
            messages = messages,
            summary = summary,
            category = category
        )
        
        // Get existing chats
        val chats = loadAllChats().toMutableList()
        
        // Add or update chat
        val existingIndex = chats.indexOfFirst { it.id == chatId }
        if (existingIndex != -1) {
            chats[existingIndex] = savedChat
        } else {
            chats.add(savedChat)
        }
        
        // Save updated chats
        val json = gson.toJson(chats)
        sharedPreferences.edit()
            .putString("saved_chats", json)
            .apply()
    }

    suspend fun loadChatById(chatId: String): SavedChat = withContext(Dispatchers.IO) {
        val chats = loadAllChats()
        return@withContext chats.find { it.id == chatId }
            ?: throw IllegalArgumentException("Chat not found")
    }

    suspend fun loadAllChats(): List<SavedChat> = withContext(Dispatchers.IO) {
        val json = sharedPreferences.getString("saved_chats", null)
        if (json != null) {
            val type = object : TypeToken<List<SavedChat>>() {}.type
            return@withContext gson.fromJson(json, type)
        }
        return@withContext emptyList()
    }

    suspend fun deleteChat(chatId: String) = withContext(Dispatchers.IO) {
        val chats = loadAllChats().toMutableList()
        chats.removeAll { it.id == chatId }
        val json = gson.toJson(chats)
        sharedPreferences.edit()
            .putString("saved_chats", json)
            .apply()
    }

    suspend fun saveMessages(messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        val json = gson.toJson(messages)
        sharedPreferences.edit()
            .putString("messages", json)
            .putLong("last_updated", System.currentTimeMillis())
            .apply()
    }

    suspend fun loadMessages(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val json = sharedPreferences.getString("messages", null)
        if (json != null) {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            return@withContext gson.fromJson<List<ChatMessage>>(json, type)
        }
        return@withContext emptyList()
    }

    suspend fun saveSummary(summary: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .putString("conversation_summary", summary)
            .putLong("summary_timestamp", System.currentTimeMillis())
            .apply()
    }

    suspend fun loadSummary(): String? = withContext(Dispatchers.IO) {
        return@withContext sharedPreferences.getString("conversation_summary", null)
    }

    suspend fun clearMessages() = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .remove("messages")
            .remove("last_updated")
            .remove("conversation_summary")
            .remove("summary_timestamp")
            .apply()
    }

    suspend fun searchMessages(query: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val messages = loadMessages()
        val searchTerms = query.lowercase().split(" ")
        return@withContext messages.filter { message ->
            searchTerms.any { term ->
                message.text.lowercase().contains(term)
            }
        }
    }

    suspend fun getCategories(): List<String> = withContext(Dispatchers.IO) {
        return@withContext loadAllChats()
            .map { it.category }
            .distinct()
    }

    suspend fun exportChat(category: String = "general"): File = withContext(Dispatchers.IO) {
        val messages = loadMessages()
        val summary = loadSummary()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        
        val exportFile = File(context.getExternalFilesDir("chat_exports"), 
            "chat_${category}_$timestamp.json")
        exportFile.parentFile?.mkdirs()

        val exportData = mapOf(
            "category" to category,
            "timestamp" to System.currentTimeMillis(),
            "messages" to messages,
            "summary" to summary,
            "metadata" to mapOf(
                "messageCount" to messages.size,
                "lastUpdated" to sharedPreferences.getLong("last_updated", 0),
                "exportDate" to System.currentTimeMillis()
            )
        )
        
        exportFile.writeText(gson.toJson(exportData))
        return@withContext exportFile
    }

    suspend fun importChat(file: File): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val json = file.readText()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data = gson.fromJson<Map<String, Any>>(json, type)
            
            // Import summary if available
            (data["summary"] as? String)?.let { summary ->
                saveSummary(summary)
            }
            
            @Suppress("UNCHECKED_CAST")
            val messages = gson.fromJson<List<ChatMessage>>(
                gson.toJson(data["messages"]),
                object : TypeToken<List<ChatMessage>>() {}.type
            )
            
            saveMessages(messages)
            return@withContext messages
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid chat export file")
        }
    }
} 