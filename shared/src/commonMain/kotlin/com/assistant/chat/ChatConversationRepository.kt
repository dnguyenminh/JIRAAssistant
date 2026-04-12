package com.assistant.chat

/**
 * Repository for multi-conversation management.
 * Requirements: 19.47, 19.48
 */
interface ChatConversationRepository {
    suspend fun create(userId: String, title: String = "New Chat"): ChatConversation
    suspend fun getByUser(userId: String): List<ChatConversation>
    suspend fun findById(id: String): ChatConversation?
    suspend fun updateTitle(id: String, title: String)
    suspend fun updateTimestamp(id: String)
    suspend fun delete(id: String)
}
