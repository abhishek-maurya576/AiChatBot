package com.org.aichatbot.model

/**
 * Request model for Gemini API
 */
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig = GenerationConfig()
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 800,
    val topP: Float = 0.95f
)

/**
 * Response model for Gemini API
 */
data class GeminiResponse(
    val candidates: List<Candidate> = emptyList()
)

data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null
)

/**
 * Models for message display in chat UI
 */
data class ChatMessage(
    val text: String,
    val isUserMessage: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) 