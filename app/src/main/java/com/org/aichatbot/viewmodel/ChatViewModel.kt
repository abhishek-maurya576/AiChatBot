package com.org.aichatbot.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.org.aichatbot.data.ChatStorage
import com.org.aichatbot.data.GeminiService
import com.org.aichatbot.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.File
import java.net.UnknownHostException

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val chatStorage = ChatStorage(application)
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ChatMessage>>(emptyList())
    val searchResults: StateFlow<List<ChatMessage>> = _searchResults.asStateFlow()

    private val _conversationSummary = MutableStateFlow<String?>(null)
    val conversationSummary: StateFlow<String?> = _conversationSummary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isReasoningMode = MutableStateFlow(false)
    val isReasoningMode: StateFlow<Boolean> = _isReasoningMode.asStateFlow()

    private val _currentTypingMessage = MutableStateFlow<ChatMessage?>(null)
    val currentTypingMessage: StateFlow<ChatMessage?> = _currentTypingMessage.asStateFlow()

    private val _currentCategory = MutableStateFlow("general")
    val currentCategory: StateFlow<String> = _currentCategory.asStateFlow()

    private var geminiService: GeminiService? = null
    private var isInitialized = false

    init {
        viewModelScope.launch {
            _messages.value = chatStorage.loadMessages()
            // Set up search
            _searchQuery
                .debounce(300) // Wait for 300ms of no typing
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isNotBlank()) {
                        performSearch(query)
                    } else {
                        _searchResults.value = emptyList()
                    }
                }
        }
    }

    fun initializeGeminiService(apiKey: String) {
        if (!isInitialized) {
            geminiService = GeminiService.create(apiKey)
            isInitialized = true
        }
    }

    fun toggleReasoningMode() {
        _isReasoningMode.value = !_isReasoningMode.value
    }

    private suspend fun streamText(text: String) {
        val words = text.split(" ")
        var currentText = ""
        
        for (word in words) {
            currentText += "$word "
            _currentTypingMessage.value = _currentTypingMessage.value?.copy(text = currentText.trim())
            delay(30)
        }
    }

    private fun performSearch(query: String) {
        val searchTerms = query.lowercase().split(" ")
        _searchResults.value = _messages.value.filter { message ->
            searchTerms.any { term ->
                message.text.lowercase().contains(term)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun summarizeConversation() {
        if (_messages.value.isEmpty()) return

        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val conversationText = buildSummarizationPrompt()
                val request = GeminiRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(Part(text = conversationText))
                        )
                    )
                )
                
                val response = geminiService?.generateContent(request)
                val summary = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (summary != null) {
                    _conversationSummary.value = summary
                    // Save summary with chat history
                    chatStorage.saveSummary(summary)
                }
            } catch (e: Exception) {
                handleError("Failed to generate summary: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun buildSummarizationPrompt(): String {
        return """
            Please provide a concise summary of the following conversation, highlighting:
            1. Main topics discussed
            2. Key decisions or conclusions
            3. Any important action items
            
            Conversation:
            ${_messages.value.joinToString("\n") { 
                "${if (it.isUserMessage) "User" else "Assistant"}: ${it.text}"
            }}
            
            Summary:
        """.trimIndent()
    }

    private fun buildConversationContext(newMessage: String): String {
        val contextBuilder = StringBuilder()
        
        // Add category and any existing summary
        if (_currentCategory.value != "general") {
            contextBuilder.append("Category: ${_currentCategory.value}\n")
        }
        _conversationSummary.value?.let {
            contextBuilder.append("Previous Discussion Summary: $it\n")
        }
        contextBuilder.append("\n")

        // Get relevant context messages
        val relevantMessages = getRelevantContext(newMessage)
        
        if (relevantMessages.isNotEmpty()) {
            contextBuilder.append("Relevant conversation history:\n")
            relevantMessages.forEach { message ->
                contextBuilder.append("${if (message.isUserMessage) "User" else "Assistant"}: ${message.text}\n")
            }
            contextBuilder.append("\n")
        }

        contextBuilder.append("User: $newMessage\n")
        contextBuilder.append("\nAssistant: ")

        if (_isReasoningMode.value) {
            contextBuilder.append("""
                Please provide both reasoning and response, considering the conversation context above:
                🤔 First, explain your thought process and reasoning.
                ✨ Then, provide your response.
            """.trimIndent())
        }

        return contextBuilder.toString()
    }

    private fun getRelevantContext(newMessage: String): List<ChatMessage> {
        val allMessages = _messages.value
        if (allMessages.isEmpty()) return emptyList()

        // Always include the last 2 messages for immediate context
        val recentContext = allMessages.takeLast(2)
        
        // Calculate relevance scores for messages
        val messageScores = allMessages.map { message ->
            val score = calculateRelevanceScore(message.text, newMessage)
            message to score
        }

        // Get top 3 most relevant messages
        val relevantMessages = messageScores
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }

        return (relevantMessages + recentContext)
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }
            .takeLast(5)
    }

    private fun calculateRelevanceScore(messageText: String, query: String): Double {
        val messageWords = messageText.lowercase().split(" ").toSet()
        val queryWords = query.lowercase().split(" ").toSet()
        
        // Calculate word overlap
        val commonWords = messageWords.intersect(queryWords)
        
        // Basic relevance score based on word overlap and message length
        return commonWords.size.toDouble() / (messageWords.size + queryWords.size).toDouble()
    }

    fun setCategory(category: String) {
        _currentCategory.value = category
    }

    suspend fun exportCurrentChat(): File {
        return chatStorage.exportChat(_currentCategory.value)
    }

    suspend fun importChat(file: File) {
        _messages.value = chatStorage.importChat(file)
    }

    suspend fun getAvailableCategories(): List<String> {
        return chatStorage.getCategories()
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Add user message to chat
        val userMessage = ChatMessage(text = text, isUserMessage = true)
        _messages.value = _messages.value + userMessage

        // Send request to Gemini API
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val promptText = buildConversationContext(text)
                
                val request = GeminiRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(Part(text = promptText))
                        )
                    )
                )
                
                val response = geminiService?.generateContent(request)
                val aiResponse = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (aiResponse != null) {
                    // Create initial empty AI message
                    val aiMessage = ChatMessage(text = "", isUserMessage = false)
                    _currentTypingMessage.value = aiMessage
                    _messages.value = _messages.value + aiMessage

                    // Stream the response
                    streamText(aiResponse)
                    
                    // Update final message
                    _messages.value = _messages.value.dropLast(1) + (_currentTypingMessage.value ?: aiMessage)
                    _currentTypingMessage.value = null
                    
                    // Save chat history
                    saveChatHistory()
                } else {
                    handleError("No response from AI. Please try again.")
                }
            } catch (e: HttpException) {
                when (e.code()) {
                    404 -> handleError("Model not found. Please check API configuration.")
                    401 -> handleError("Invalid API key. Please check your credentials.")
                    429 -> handleError("Rate limit exceeded. Please try again later.")
                    500, 502, 503, 504 -> handleError("Server error. Please try again later.")
                    else -> handleError("Network error (${e.code()}): ${e.message()}")
                }
            } catch (e: UnknownHostException) {
                handleError("No internet connection. Please check your network.")
            } catch (e: Exception) {
                handleError("Error: ${e.message ?: "Unknown error occurred"}")
            } finally {
                _isLoading.value = false
                _currentTypingMessage.value = null
            }
        }
    }

    private fun handleError(errorMessage: String) {
        val errorChatMessage = ChatMessage(
            text = "🚫 $errorMessage",
            isUserMessage = false
        )
        _messages.value = _messages.value + errorChatMessage
    }

    fun clearChat() {
        _messages.value = emptyList()
        viewModelScope.launch {
            clearChatHistory()
        }
    }

    private fun saveChatHistory() {
        viewModelScope.launch {
            try {
                val chatId = System.currentTimeMillis().toString()
                chatStorage.saveChat(
                    chatId = chatId,
                    messages = _messages.value,
                    summary = _conversationSummary.value,
                    category = _currentCategory.value
                )
            } catch (e: Exception) {
                handleError("Failed to save chat: ${e.message}")
            }
        }
    }

    private fun clearChatHistory() {
        viewModelScope.launch {
            chatStorage.clearMessages()
        }
    }

    fun startNewChat() {
        viewModelScope.launch {
            // Save current chat if not empty
            if (_messages.value.isNotEmpty()) {
                saveChatHistory()
            }
            
            // Clear current messages
            _messages.value = emptyList()
            _currentTypingMessage.value = null
            _conversationSummary.value = null
            
            // Reset category to general
            _currentCategory.value = "general"
        }
    }

    fun loadPreviousChat(chatId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val previousChat = chatStorage.loadChatById(chatId)
                _messages.value = previousChat.messages
                _conversationSummary.value = previousChat.summary
                _currentCategory.value = previousChat.category
            } catch (e: Exception) {
                handleError("Failed to load chat: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
} 