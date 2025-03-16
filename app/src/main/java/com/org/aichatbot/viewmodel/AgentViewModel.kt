package com.org.aichatbot.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.org.aichatbot.model.ChatMessage
import com.org.aichatbot.service.CommandProcessor
import com.org.aichatbot.service.CommandResult
import com.org.aichatbot.service.ProcessingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for AI Agent that handles command processing and chat
 */
class AgentViewModel(
    private val context: Context,
    private val apiKey: String
) : ViewModel() {

    private val commandProcessor = CommandProcessor(context, apiKey)
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _currentTypingMessage = MutableStateFlow<ChatMessage?>(null)
    val currentTypingMessage: StateFlow<ChatMessage?> = _currentTypingMessage.asStateFlow()
    
    private val _isCommand = MutableStateFlow(false)
    val isCommand: StateFlow<Boolean> = _isCommand.asStateFlow()
    
    companion object {
        private const val TAG = "AgentViewModel"
        
        // Command detection patterns
        private val COMMAND_PATTERNS = listOf(
            "open", "search", "go to", "send", "call", "find", "show", "turn on", "turn off", "enable", "disable",
            "bluetooth", "wifi", "whatsapp", "youtube", "chrome", "gmail"
        )
    }
    
    init {
        // Monitor processing status
        viewModelScope.launch {
            commandProcessor.processingStatus.collectLatest { status ->
                _isLoading.value = (status is ProcessingStatus.Processing)
            }
        }
        
        // Show welcome message
        viewModelScope.launch {
            delay(500)
            val welcomeMessage = ChatMessage(
                text = """
                    🤔 Thinking:
                    I should introduce myself and explain my capabilities as an AI Agent that can control apps, rather than just chat. I should let the user know what kinds of commands I can handle.
                    
                    ✨ Answer:
                    Hello! I'm your AI Agent. I can help you open apps and perform actions on your device. Try commands like:
                    
                    • "Open WhatsApp"
                    • "Search for Android development on YouTube"
                    • "Turn on Bluetooth"
                    • "Send a message to John on WhatsApp"
                    • "Open Google and search for AI agents"
                    👋 I was created by a sasta Android Developer called Abhishek
                    
                    What would you like me to do?
                """.trimIndent(),
                isUserMessage = false,
                timestamp = System.currentTimeMillis()
            )
            simulateTypingEffect(welcomeMessage)
        }
    }
    
    /**
     * Process user input (either as a command or regular chat)
     */
    fun processUserInput(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Add user message
                val userMessage = ChatMessage(
                    text = text,
                    isUserMessage = true,
                    timestamp = System.currentTimeMillis()
                )
                
                _messages.update { currentMessages ->
                    listOf(userMessage) + currentMessages
                }
                
                // Determine if this is likely a command
                val isCommand = isLikelyCommand(text)
                _isCommand.value = isCommand
                
                // Show loading state
                _isLoading.value = true
                
                if (isCommand) {
                    // Process as a command
                    val result = commandProcessor.processCommand(text)
                    
                    // Hide loading
                    _isLoading.value = false
                    
                    // Format the response based on command execution
                    val responseText = formatCommandResponse(text, result)
                    
                    // Create and show AI message
                    val aiMessage = ChatMessage(
                        text = responseText,
                        isUserMessage = false,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    simulateTypingEffect(aiMessage)
                } else {
                    // Treat as a regular chat message and send to the chat model via ChatViewModel
                    // For simplicity, we'll handle command detection again inside ChatViewModel
                    handleAsRegularChat(text)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing user input", e)
                _isLoading.value = false
                
                // Show error message
                val errorMessage = ChatMessage(
                    text = "Sorry, I encountered an error: ${e.message}",
                    isUserMessage = false,
                    timestamp = System.currentTimeMillis()
                )
                simulateTypingEffect(errorMessage)
            }
        }
    }
    
    /**
     * Check if the user input is likely a command
     */
    private fun isLikelyCommand(text: String): Boolean {
        val input = text.lowercase()
        
        for (pattern in COMMAND_PATTERNS) {
            if (input.contains(pattern)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Format the response to a command
     */
    private fun formatCommandResponse(command: String, result: CommandResult): String {
        val thinking = when (result.commandType) {
            CommandProcessor.COMMAND_OPEN_APP -> 
                "The user wants me to open an app. This requires accessing the installed applications using the package manager."
            CommandProcessor.COMMAND_OPEN_WEBSITE -> 
                "The user wants me to open a website. I'll need to create a proper URL and use the browser intent."
            CommandProcessor.COMMAND_SEARCH -> 
                "The user wants to search for something. I should detect if they want to use Google or YouTube."
            CommandProcessor.COMMAND_SEND_MESSAGE -> 
                "The user wants to send a message. I need to extract the recipient and message content."
            CommandProcessor.COMMAND_CALL -> 
                "The user wants to make a phone call. I need to extract the phone number or contact name."
            CommandProcessor.COMMAND_TOGGLE_WIFI -> 
                "The user wants to control the Wi-Fi. I need to determine if they want to turn it on or off."
            CommandProcessor.COMMAND_TOGGLE_BLUETOOTH -> 
                "The user wants to control Bluetooth. I need to determine if they want to turn it on or off."
            else -> 
                "I need to analyze if this is a command I can perform. I'll need to extract the action and relevant parameters."
        }
        
        val answer = if (result.wasExecuted) {
            "I've executed your command: \"$command\". ${result.message}"
        } else {
            if (result.commandType == CommandProcessor.COMMAND_UNKNOWN) {
                "I couldn't understand your command. Please try commands like \"Open WhatsApp\" or \"Search for Android development on YouTube\"."
            } else {
                "I understood your command but couldn't execute it: ${result.message}. This might require additional permissions or the app might not be installed."
            }
        }
        
        return """
            🤔 Thinking:
            $thinking
            
            ✨ Answer:
            $answer
        """.trimIndent()
    }
    
    /**
     * Handle the input as a regular chat message
     */
    private fun handleAsRegularChat(text: String) {
        viewModelScope.launch {
            // This is where we would normally delegate to the ChatViewModel
            // But for this quick implementation, we'll generate a simple response
            
            _isLoading.value = false
            
            val responseText = """
                🤔 Thinking:
                This appears to be a regular chat message rather than a command to perform an action. I should respond conversationally but remind the user that I can perform device actions.
                
                ✨ Answer:
                I'm primarily designed to help you control your device and apps. If you'd like to perform actions like opening apps or searching the web, try commands like "Open YouTube" or "Search for Android development." How can I assist you with your device?
            """.trimIndent()
            
            val aiMessage = ChatMessage(
                text = responseText,
                isUserMessage = false,
                timestamp = System.currentTimeMillis()
            )
            
            simulateTypingEffect(aiMessage)
        }
    }
    
    /**
     * Simulate typing effect for AI messages
     */
    private suspend fun simulateTypingEffect(message: ChatMessage) {
        // Original message text
        val originalText = message.text
        
        // Start with empty message
        _currentTypingMessage.value = ChatMessage(
            text = "",
            isUserMessage = message.isUserMessage,
            timestamp = message.timestamp
        )
        
        // For longer messages, type word by word
        val words = originalText.split(" ")
        var currentText = ""
        
        for (word in words) {
            currentText += if (currentText.isEmpty()) word else " $word"
            
            // Update typing indicator
            _currentTypingMessage.update { current ->
                current?.copy(text = currentText)
            }
            
            // Random delay for natural typing effect
            delay((50L..120L).random())
        }
        
        // Small pause at the end
        delay(200)
        
        // Add full message to the list and remove typing indicator
        _messages.update { currentMessages ->
            listOf(message) + currentMessages
        }
        
        _currentTypingMessage.value = null
    }
    
    /**
     * Reset the conversation
     */
    fun resetConversation() {
        viewModelScope.launch {
            _messages.value = emptyList()
            _currentTypingMessage.value = null
            _isLoading.value = false
            
            // Show welcome message again
            delay(500)
            val welcomeMessage = ChatMessage(
                text = """
                    🤔 Thinking:
                    The user has reset the conversation. I should reintroduce myself and my capabilities.
                    
                    ✨ Answer:
                    Hello again! I'm your AI Agent. I can help you control apps and perform actions on your device. What would you like me to do?
                """.trimIndent(),
                isUserMessage = false,
                timestamp = System.currentTimeMillis()
            )
            simulateTypingEffect(welcomeMessage)
        }
    }
    
    /**
     * Factory for creating AgentViewModel
     */
    class Factory(private val context: Context, private val apiKey: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AgentViewModel(context, apiKey) as T
        }
    }
} 