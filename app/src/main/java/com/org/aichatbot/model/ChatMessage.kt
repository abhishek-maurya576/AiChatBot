package com.org.aichatbot.model

data class ChatMessage(
    val text: String,
    val isUserMessage: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) 