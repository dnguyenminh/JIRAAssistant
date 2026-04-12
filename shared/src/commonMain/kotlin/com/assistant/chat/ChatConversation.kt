package com.assistant.chat

import kotlinx.serialization.Serializable

/**
 * Represents a chat conversation (multi-conversation support).
 * Requirements: 19.47
 */
@Serializable
data class ChatConversation(
    val id: String,
    val userId: String,
    val title: String = "New Chat",
    val createdAt: String,
    val updatedAt: String
)
