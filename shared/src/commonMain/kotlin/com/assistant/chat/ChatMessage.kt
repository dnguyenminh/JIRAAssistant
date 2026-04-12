package com.assistant.chat

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: Long,
    val userId: String,
    val role: String,
    val message: String,
    val context: String? = null,
    val timestamp: String
)
