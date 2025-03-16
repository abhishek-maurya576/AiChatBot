package com.org.aichatbot.data.db.dao

import androidx.room.*
import com.org.aichatbot.data.db.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the chats table
 */
@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY timestamp DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()

    @Query("SELECT * FROM chats WHERE category = :category ORDER BY timestamp DESC")
    fun getChatsByCategory(category: String): Flow<List<ChatEntity>>

    @Query("SELECT DISTINCT category FROM chats ORDER BY category")
    fun getAllCategories(): Flow<List<String>>

    @Query("UPDATE chats SET title = :title WHERE id = :chatId")
    suspend fun updateChatTitle(chatId: String, title: String)

    @Query("UPDATE chats SET summary = :summary WHERE id = :chatId")
    suspend fun updateChatSummary(chatId: String, summary: String?)

    @Query("UPDATE chats SET messageCount = :count, lastUpdated = :updatedAt WHERE id = :chatId")
    suspend fun updateMessageCount(chatId: String, count: Int, updatedAt: Long)
}
