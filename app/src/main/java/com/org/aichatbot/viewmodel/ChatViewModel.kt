package com.org.aichatbot.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.generationConfig
import com.org.aichatbot.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

        private const val TAG = "ChatViewModel"

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentTypingMessage = MutableStateFlow<ChatMessage?>(null)
    val currentTypingMessage: StateFlow<ChatMessage?> = _currentTypingMessage.asStateFlow()

    // Reasoning mode is enabled by default now
    private val _isReasoningMode = MutableStateFlow(true)
    val isReasoningMode: StateFlow<Boolean> = _isReasoningMode.asStateFlow()

    private var apiKey: String = ""
    private val client = OkHttpClient()
    private val backupResponseGenerator = BackupResponseGenerator()

    fun initializeGeminiService(apiKey: String) {
        if (apiKey.isBlank()) {
            // If no API key is provided, fall back to simulated responses
            simulateGeminiResponses()
            return
        }
        
        this.apiKey = apiKey
        
        try {
            // No need to create model instances here, we'll use OkHttp directly
            Log.d(TAG, "Gemini service initialized with API key")
            
            // Show welcome message with typing effect
            viewModelScope.launch {
                _isLoading.value = true
                delay(1000) // Simulate network delay for first connection
                _isLoading.value = false
                
                val welcomeMessage = ChatMessage(
                    text = if (_isReasoningMode.value) {
                        """
                        🤔 Thinking: 
                        This is the first interaction with the user. I should introduce myself properly, explain that I'm in reasoning mode, and set expectations for how I'll respond going forward.
                        
                        ✨ Answer: 
                        Hello! I'm your AI assistant powered by Gemini 2.0 Flash. I'm in reasoning mode, so I'll show my thinking process for more detailed answers. How can I help you today? 👋 I was created by a sasta Android Developer called Abhishek
                        """.trimIndent()
                    } else {
                        "Hello! I'm your AI assistant powered by Gemini 2.0 Flash. How can I help you today? 👋 I was created by a sasta Android Developer called Abhishek"
                    },
                    isUserMessage = false,
                    timestamp = System.currentTimeMillis()
                )
                
                // Simulate typing effect for welcome message
                simulateTypingEffect(welcomeMessage)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Gemini service", e)
            
            // Show error message to user
            viewModelScope.launch {
                val errorMessage = ChatMessage(
                    text = "Error initializing AI service: ${e.message}",
                    isUserMessage = false,
                    timestamp = System.currentTimeMillis()
                )
                _messages.update { currentMessages ->
                    listOf(errorMessage) + currentMessages
                }
            }
        }
    }
    
    private fun simulateGeminiResponses() {
        // Show welcome message with typing effect
        viewModelScope.launch {
            _isLoading.value = true
            delay(1000) // Simulate network delay for first connection
            _isLoading.value = false
            
            val welcomeMessage = ChatMessage(
                text = if (_isReasoningMode.value) {
                    """
                    🤔 Thinking: 
                    I'm in simulation mode since no API key is provided. I should let the user know I'm simulating responses while still maintaining the reasoning format structure.
                    
                    ✨ Answer: 
                    Hello! I'm your AI assistant (simulation mode). I'll be showing my reasoning process for each answer. How can I help you today?
                    """.trimIndent()
                } else {
                    "Hello! I'm your AI assistant (simulation mode). How can I help you today?"
                },
                isUserMessage = false,
                timestamp = System.currentTimeMillis()
            )
            
            // Simulate typing effect for welcome message
            simulateTypingEffect(welcomeMessage)
        }
    }

    fun toggleReasoningMode() {
        _isReasoningMode.value = !_isReasoningMode.value
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Add user message
                    val userMessage = ChatMessage(
                        text = text,
                        isUserMessage = true,
                        timestamp = System.currentTimeMillis()
                    )
                    
                // Add user message immediately with animation
                _messages.update { currentMessages ->
                    listOf(userMessage) + currentMessages
                }

                // Show loading state
                _isLoading.value = true
                
                // Generate response using Gemini API or fallback to simulation
                val responseText = if (apiKey.isNotBlank()) {
                    try {
                        callGeminiApi(text)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calling Gemini API, falling back to simulated response", e)
                        backupResponseGenerator.generateResponse(text)
                    }
                } else {
                    backupResponseGenerator.generateResponse(text)
                }
                
                // Hide loading
                _isLoading.value = false
                
                // Create AI message
                val aiMessage = ChatMessage(
                    text = responseText,
                            isUserMessage = false,
                            timestamp = System.currentTimeMillis()
                        )

                // Show typing effect for AI response
                simulateTypingEffect(aiMessage)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error generating response", e)
                _isLoading.value = false
                
                // Show error message with typing effect
                val errorMessage = ChatMessage(
                    text = "I'm sorry, something went wrong: ${e.message}. Please try again later.",
                                    isUserMessage = false,
                                    timestamp = System.currentTimeMillis()
                                )
                simulateTypingEffect(errorMessage)
            }
        }
    }
    
    private suspend fun callGeminiApi(prompt: String): String {
        return try {
            // Create the JSON request body as per the cURL example, but add reasoning instructions
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                // Add reasoning instructions to the prompt
                                val promptWithReasoning = if (_isReasoningMode.value) {
                                    """
                                    Please provide both your reasoning process and your final answer to the following question. 
                                    Start by showing your thought process with "🤔 Thinking:" followed by your reasoning.
                                    Then provide your final answer with "✨ Answer:" 
                                    
                                    User question: $prompt
                                    """.trimIndent()
                                } else {
                                    prompt
                                }
                                put("text", promptWithReasoning)
                            })
                        })
                    })
                })
                
                // Add generation configuration
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("topK", 40)
                    put("topP", 0.95)
                    put("maxOutputTokens", 1000)
                })
            }.toString()
            
            // Create the request
            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()
            
            // Make the API call using a coroutine-friendly approach
            val response = suspendedRequest(request)
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "API Response: $responseBody")
                
                // Parse the response JSON to extract the generated text
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.getJSONArray("candidates")
                
                if (candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    
                    if (parts.length() > 0) {
                        parts.getJSONObject(0).getString("text")
                            } else {
                        "Sorry, I couldn't generate a response from the API."
                            }
                        } else {
                    "Sorry, no response was generated by the API."
                }
            } else {
                val errorResponse = response.body?.string() ?: ""
                Log.e(TAG, "API Error Response: $errorResponse")
                "Sorry, there was an error: ${response.code} - ${response.message}"
            }
                    } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API", e)
            throw e
        }
    }
    
    private suspend fun suspendedRequest(request: Request): Response {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(e))
                    }
                }
                
                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(response))
                    }
                }
            })
            
            continuation.invokeOnCancellation {
                client.dispatcher.executorService.shutdown()
            }
        }
    }
    
    inner class BackupResponseGenerator {
        fun generateResponse(query: String): String {
            // Always include reasoning in the response
            val withReasoning = if (_isReasoningMode.value) {
                // If reasoning mode is enabled, add thinking section
                true
            } else {
                false
            }
            
            // Dictionary of canned responses for different query types
            val responses = mapOf(
                Regex("(?i).*help.*") to generateReasoningResponse(
                    "I need to determine what kind of help the user is asking for. Since the query is general, I should provide a friendly and open-ended response that encourages more specific questions.",
                    "I'm here to help! What would you like to know?"
                ),
                Regex("(?i).*hi|hello|hey.*") to generateReasoningResponse(
                    "The user is greeting me, so I should respond with a warm welcome and invite them to ask questions.",
                    "Hello there! What can I assist you with today?"
                ),
                Regex("(?i).*name.*") to generateReasoningResponse(
                    "The user is asking about my identity. I should identify myself as an AI assistant to set proper expectations.",
                    "I'm the AI Chat Bot, your friendly assistant!"
                ),
                Regex("(?i).*weather.*") to generateReasoningResponse(
                    "The user is asking about weather, but I don't have real-time data or location access. I should explain this limitation and suggest alternatives.",
                    "I don't have real-time weather data, but you can check a weather app or website for that information."
                ),
                Regex("(?i).*how are you.*") to generateReasoningResponse(
                    "The user is asking about my well-being, which is a common social courtesy. I should acknowledge this while gently reminding them I'm an AI program.",
                    "I'm just a program, but thanks for asking! How can I help you?"
                ),
                Regex("(?i).*thank.*") to generateReasoningResponse(
                    "The user is expressing gratitude. I should acknowledge it politely and ask if they need further assistance.",
                    "You're welcome! Is there anything else I can help with?"
                ),
                Regex("(?i).*bye.*") to generateReasoningResponse(
                    "The user is ending the conversation. I should respond with a polite farewell that leaves the door open for future interactions.",
                    "Goodbye! Feel free to chat again anytime you need assistance."
                )
            )
            
            // Find the first matching pattern
            for ((pattern, response) in responses) {
                if (pattern.containsMatchIn(query)) {
                    return response
                }
            }
            
            // Default responses if no pattern matches
            val defaultThinking = listOf(
                "I need to consider what the user is asking about \"$query\". Since it doesn't match my common patterns, I should give a thoughtful but somewhat general response that encourages more details.",
                "This query about \"$query\" requires me to think carefully. Without specific context, I should be helpful while acknowledging limitations.",
                "For this question about \"$query\", I should consider what information would be most useful. Without specifics, an open-ended response is best."
            )
            
            val defaultAnswers = listOf(
                "That's an interesting question. Can you tell me more about what you're looking for?",
                "I understand you're asking about \"$query\". Could you provide more details?",
                "I'd be happy to help with that. Could you elaborate a bit more?",
                "I'm still learning, but I'll try my best to assist with your question about \"$query\".",
                "Thanks for your question. Let me know if you need specific information about \"$query\"."
            )
            
            return generateReasoningResponse(
                defaultThinking.random(),
                defaultAnswers.random()
            )
        }
        
        private fun generateReasoningResponse(thinking: String, answer: String): String {
            return if (_isReasoningMode.value) {
                """
                🤔 Thinking: $thinking
                
                ✨ Answer: $answer
                """.trimIndent()
            } else {
                answer
            }
        }
    }

    fun startNewChat() {
        viewModelScope.launch {
            // Show visual feedback for new chat
            _isLoading.value = true
            delay(500)
            
            // Clear messages
            _messages.value = emptyList()
            _currentTypingMessage.value = null
            
            // Hide loading
            _isLoading.value = false
            
            // Show welcome message with typing effect after a short delay
            delay(500)
            
            // Create a proper reasoning-formatted welcome message
            val welcomeMessage = ChatMessage(
                text = if (_isReasoningMode.value) {
                    """
                    🤔 Thinking: 
                    This is a new chat session, so I should introduce myself properly and explain that I'm in reasoning mode. I want to be welcoming and set expectations about how my responses will be structured.
                    
                    ✨ Answer: 
                    Hello! I'm your AI assistant powered by Gemini 2.0 Flash. I'm in reasoning mode, so I'll show my thinking process for more detailed answers. How can I help you today?
                    """.trimIndent()
                } else {
                    "How can I help you today?"
                },
                isUserMessage = false,
                timestamp = System.currentTimeMillis()
            )
            
            // Simulate typing effect for welcome message
            simulateTypingEffect(welcomeMessage)
        }
    }
    
    private suspend fun simulateTypingEffect(message: ChatMessage) {
        // Get original message text
        val originalText = message.text
        
        // Break message into visible chunks with typing effect
        _currentTypingMessage.value = ChatMessage(
            text = "",
            isUserMessage = message.isUserMessage,
            timestamp = message.timestamp
        )
        
        // For short messages, type character by character
        if (originalText.length < 50) {
            for (i in originalText.indices) {
                // Update typing indicator with more text
                _currentTypingMessage.update { current ->
                    current?.copy(text = originalText.substring(0, i + 1))
                }
                
                // Random delay for natural typing appearance
                delay((30L..80L).random())
            }
        } 
        // For longer messages, type word by word
        else {
            val words = originalText.split(" ")
            var currentText = ""
            
            for (word in words) {
                currentText += if (currentText.isEmpty()) word else " $word"
                
                // Update typing indicator with more text
                _currentTypingMessage.update { current ->
                    current?.copy(text = currentText)
                }
                
                // Random delay for natural typing appearance
                delay((70L..150L).random())
            }
        }
        
        // Small pause at the end to make it feel more natural
        delay(200)
        
        // Add the full message to the list and remove typing indicator
        _messages.update { currentMessages ->
            listOf(message) + currentMessages
        }
        
        _currentTypingMessage.value = null
    }
}