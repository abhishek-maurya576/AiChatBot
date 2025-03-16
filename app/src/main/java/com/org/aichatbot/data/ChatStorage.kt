package com.org.aichatbot.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.org.aichatbot.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

data class SavedChat(
    val id: String,
    val messages: List<ChatMessage>,
    val summary: String?,
    val category: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatStorage(private val context: Context) {
    companion object {
        private const val TAG = "ChatStorage"
        private const val PREFS_NAME = "chat_history"
        private const val KEY_SAVED_CHATS = "saved_chats"
        private const val KEY_MESSAGES = "messages"
        private const val KEY_LAST_UPDATED = "last_updated"
        private const val KEY_CONVERSATION_SUMMARY = "conversation_summary"
        private const val KEY_SUMMARY_TIMESTAMP = "summary_timestamp"
    }

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = GsonBuilder().setLenient().create()
    private val isWriting = AtomicBoolean(false)

    suspend fun saveChat(
        chatId: String,
        messages: List<ChatMessage>,
        summary: String?,
        category: String
    ) = withContext(Dispatchers.IO) {
        if (isWriting.getAndSet(true)) {
            Log.w(TAG, "Another write operation is in progress, waiting...")
            // Wait for a short time and check again
            kotlinx.coroutines.delay(100)
            if (isWriting.get()) {
                Log.w(TAG, "Still writing, will retry later")
                isWriting.set(false)
                return@withContext
            }
        }
        
        try {
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
            val editor = sharedPreferences.edit()
            editor.putString(KEY_SAVED_CHATS, json)
            val success = editor.commit() // Use commit() instead of apply() to ensure data is written
            
            if (!success) {
                Log.e(TAG, "Failed to save chat data")
            } else {
                Log.d(TAG, "Successfully saved chat with ID: $chatId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving chat: ${e.message}", e)
            throw e
        } finally {
            isWriting.set(false)
        }
    }

    suspend fun loadChatById(chatId: String): SavedChat = withContext(Dispatchers.IO) {
        try {
            val chats = loadAllChats()
            return@withContext chats.find { it.id == chatId }
                ?: throw IllegalArgumentException("Chat with ID $chatId not found")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading chat by ID: ${e.message}", e)
            throw e
        }
    }

    suspend fun loadAllChats(): List<SavedChat> = withContext(Dispatchers.IO) {
        try {
            val json = sharedPreferences.getString(KEY_SAVED_CHATS, null)
            if (json != null) {
                try {
                    val type = object : TypeToken<List<SavedChat>>() {}.type
                    return@withContext gson.fromJson(json, type) ?: emptyList()
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Error parsing saved chats JSON: ${e.message}", e)
                    return@withContext emptyList()
                }
            }
            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all chats: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    suspend fun deleteChat(chatId: String) = withContext(Dispatchers.IO) {
        if (isWriting.getAndSet(true)) {
            Log.w(TAG, "Another write operation is in progress, waiting...")
            kotlinx.coroutines.delay(100)
            if (isWriting.get()) {
                Log.w(TAG, "Still writing, will retry later")
                isWriting.set(false)
                return@withContext
            }
        }
        
        try {
            val chats = loadAllChats().toMutableList()
            val initialSize = chats.size
            chats.removeAll { it.id == chatId }
            
            if (chats.size < initialSize) {
                val json = gson.toJson(chats)
                val editor = sharedPreferences.edit()
                editor.putString(KEY_SAVED_CHATS, json)
                val success = editor.commit()
                
                if (success) {
                    Log.d(TAG, "Successfully deleted chat with ID: $chatId")
                } else {
                    Log.e(TAG, "Failed to delete chat with ID: $chatId")
                }
            } else {
                Log.w(TAG, "Chat with ID $chatId not found for deletion")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting chat: ${e.message}", e)
            throw e
        } finally {
            isWriting.set(false)
        }
    }

    suspend fun saveMessages(messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        if (isWriting.getAndSet(true)) {
            Log.w(TAG, "Another write operation is in progress, waiting...")
            kotlinx.coroutines.delay(100)
            if (isWriting.get()) {
                Log.w(TAG, "Still writing, will retry later")
                isWriting.set(false)
                return@withContext
            }
        }
        
        try {
            Log.d(TAG, "Saving ${messages.size} messages")
            val json = gson.toJson(messages)
            Log.d(TAG, "Messages JSON length: ${json.length}")
            
            val editor = sharedPreferences.edit()
            editor.putString(KEY_MESSAGES, json)
            editor.putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            val success = editor.commit()
            
            if (success) {
                Log.d(TAG, "Messages saved successfully")
            } else {
                Log.e(TAG, "Failed to save messages")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving messages: ${e.message}", e)
            throw e
        } finally {
            isWriting.set(false)
        }
    }

    suspend fun loadMessages(): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val json = sharedPreferences.getString(KEY_MESSAGES, null)
            Log.d(TAG, "Loaded messages JSON length: ${json?.length ?: 0}")
            
            if (json != null) {
                try {
                    val type = object : TypeToken<List<ChatMessage>>() {}.type
                    val messages = gson.fromJson<List<ChatMessage>>(json, type) ?: emptyList()
                    Log.d(TAG, "Loaded ${messages.size} messages")
                    return@withContext messages
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Error parsing messages JSON: ${e.message}", e)
                    return@withContext emptyList()
                }
            }
            Log.d(TAG, "No saved messages found")
            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading messages: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    suspend fun saveSummary(summary: String) = withContext(Dispatchers.IO) {
        if (isWriting.getAndSet(true)) {
            Log.w(TAG, "Another write operation is in progress, waiting...")
            kotlinx.coroutines.delay(100)
            if (isWriting.get()) {
                Log.w(TAG, "Still writing, will retry later")
                isWriting.set(false)
                return@withContext
            }
        }
        
        try {
            val editor = sharedPreferences.edit()
            editor.putString(KEY_CONVERSATION_SUMMARY, summary)
            editor.putLong(KEY_SUMMARY_TIMESTAMP, System.currentTimeMillis())
            val success = editor.commit()
            
            if (!success) {
                Log.e(TAG, "Failed to save summary")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving summary: ${e.message}", e)
        } finally {
            isWriting.set(false)
        }
    }

    suspend fun loadSummary(): String? = withContext(Dispatchers.IO) {
        return@withContext sharedPreferences.getString(KEY_CONVERSATION_SUMMARY, null)
    }

    suspend fun clearMessages() = withContext(Dispatchers.IO) {
        if (isWriting.getAndSet(true)) {
            Log.w(TAG, "Another write operation is in progress, waiting...")
            kotlinx.coroutines.delay(100)
            if (isWriting.get()) {
                Log.w(TAG, "Still writing, will retry later")
                isWriting.set(false)
                return@withContext
            }
        }
        
        try {
            val editor = sharedPreferences.edit()
            editor.remove(KEY_MESSAGES)
            editor.remove(KEY_LAST_UPDATED)
            editor.remove(KEY_CONVERSATION_SUMMARY)
            editor.remove(KEY_SUMMARY_TIMESTAMP)
            val success = editor.commit()
            
            if (success) {
                Log.d(TAG, "Messages cleared successfully")
            } else {
                Log.e(TAG, "Failed to clear messages")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing messages: ${e.message}", e)
        } finally {
            isWriting.set(false)
        }
    }

    suspend fun searchMessages(query: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) {
                return@withContext emptyList()
            }
            
            val messages = loadMessages()
            val searchTerms = query.lowercase().split(" ")
            return@withContext messages.filter { message ->
                searchTerms.any { term ->
                    message.text.lowercase().contains(term)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching messages: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    suspend fun getCategories(): List<String> = withContext(Dispatchers.IO) {
        try {
            return@withContext loadAllChats()
                .map { it.category }
                .distinct()
                .filter { it.isNotBlank() }
                .sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting categories: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    suspend fun exportChat(category: String = "general"): File = withContext(Dispatchers.IO) {
        try {
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
                    "lastUpdated" to sharedPreferences.getLong(KEY_LAST_UPDATED, 0),
                    "exportDate" to System.currentTimeMillis()
                )
            )
            
            try {
                exportFile.writeText(gson.toJson(exportData))
                Log.d(TAG, "Chat exported successfully to ${exportFile.absolutePath}")
                return@withContext exportFile
            } catch (e: IOException) {
                Log.e(TAG, "Error writing to export file: ${e.message}", e)
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting chat: ${e.message}", e)
            throw e
        }
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
            Log.d(TAG, "Chat imported successfully with ${messages.size} messages")
            return@withContext messages
        } catch (e: Exception) {
            Log.e(TAG, "Error importing chat: ${e.message}", e)
            throw IllegalArgumentException("Invalid chat export file: ${e.message}")
        }
    }
}