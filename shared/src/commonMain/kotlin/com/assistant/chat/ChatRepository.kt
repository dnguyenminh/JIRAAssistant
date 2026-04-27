package com.assistant.chat

/**
 * Repository interface for AI Chat per-user persistent history.
 * Manages chat messages (user + assistant) in SQLDelight.
 *
 * Requirements: 19.16 (per-user persistent history),
 *               19.17 (load history on sidebar open),
 *               19.23 (delete history endpoint)
 */
interface ChatRepository {
    suspend fun saveMessage(userId: String, role: String, message: String, context: String? = null): Long
    suspend fun saveMessageWithConversation(
        userId: String, conversationId: String, role: String, message: String, context: String? = null
    ): Long
    suspend fun getHistory(userId: String, page: Int = 0, size: Int = 50): List<ChatMessage>
    suspend fun getHistoryByConversation(
        userId: String, conversationId: String, page: Int = 0, size: Int = 50
    ): List<ChatMessage>
    suspend fun getHistoryCount(userId: String): Long
    suspend fun getHistoryCountByConversation(userId: String, conversationId: String): Long
    suspend fun deleteHistory(userId: String): Boolean
    suspend fun deleteHistoryByConversation(conversationId: String)
    suspend fun getUserMessageList(userId: String): List<String>  // For command history navigation
}
