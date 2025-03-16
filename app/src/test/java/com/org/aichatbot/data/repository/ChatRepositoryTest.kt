package com.org.aichatbot.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.org.aichatbot.data.db.ChatDatabase
import com.org.aichatbot.data.db.entity.ChatEntity
import com.org.aichatbot.data.db.entity.MessageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatRepositoryTest {
    private lateinit var database: ChatDatabase
    private lateinit var repository: ChatRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChatDatabase::class.java
        ).build()
        repository = ChatRepository(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testInsertChat() = runBlocking {
        val chat = ChatEntity(
            chatId = "1",
            title = "Test Chat",
            category = "General"
        )
        repository.createChat(chat)
        val retrievedChat = repository.getChatById("1")
        assertEquals(chat.title, retrievedChat?.title)
    }

    @Test
    fun testInsertMessage() = runBlocking {
        val chat = ChatEntity(
            chatId = "1",
            title = "Test Chat",
            category = "General"
        )
        repository.createChat(chat)

        val message = MessageEntity(
            chatId = "1",
            text = "Hello, World!",
            isUserMessage = true
        )
        repository.addMessage(message)

        val messages = repository.getMessagesForChat("1").toList()
        assertEquals(1, messages.size)
        assertEquals(message.text, messages[0].text)
    }

    @Test
    fun testUpdateChat() = runBlocking {
        val chat = ChatEntity(
            chatId = "1",
            title = "Test Chat",
            category = "General"
        )
        repository.createChat(chat)

        val updatedChat = chat.copy(title = "Updated Chat")
        repository.updateChatTitle(updatedChat.chatId, updatedChat.title)

        val retrievedChat = repository.getChatById("1")
        assertEquals(updatedChat.title, retrievedChat?.title)
    }

    @Test
    fun testDeleteChat() = runBlocking {
        val chat = ChatEntity(
            chatId = "1",
            title = "Test Chat",
            category = "General"
        )
        repository.createChat(chat)

        repository.deleteChat(chat.chatId)
        val retrievedChat = repository.getChatById("1")
        assertEquals(null, retrievedChat)
    }
}
