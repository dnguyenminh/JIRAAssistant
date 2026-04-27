package com.assistant.chat

import com.assistant.db.JiraDatabase
import kotlinx.datetime.Clock

/**
 * JVM implementation of [ChatRepository] backed by SQLDelight.
 * Requirements: 19.16, 19.22, 19.23
 */
class ChatRepositoryImpl(
    private val database: JiraDatabase
) : ChatRepository {

    override suspend fun saveMessage(
        userId: String, role: String, message: String, context: String?
    ): Long {
        return saveMessageWithConversation(userId, "", role, message, context)
    }

    override suspend fun saveMessageWithConversation(
        userId: String, conversationId: String,
        role: String, message: String, context: String?
    ): Long {
        val timestamp = Clock.System.now().toString()
        database.knowledgeBaseQueries.insertChatMessage(
            user_id = userId,
            conversation_id = conversationId,
            role = role,
            message = message,
            context = context,
            timestamp = timestamp
        )
        return database.knowledgeBaseQueries.lastInsertRowId().executeAsOne()
    }

    override suspend fun getHistory(userId: String, page: Int, size: Int): List<ChatMessage> {
        val offset = (page * size).toLong()
        return database.knowledgeBaseQueries
            .getChatHistory(user_id = userId, value_ = size.toLong(), value__ = offset)
            .executeAsList()
            .map { it.toChatMessage() }
    }

    override suspend fun getHistoryByConversation(
        userId: String, conversationId: String, page: Int, size: Int
    ): List<ChatMessage> {
        val offset = (page * size).toLong()
        return database.knowledgeBaseQueries
            .getChatHistoryByConversation(userId, conversationId, size.toLong(), offset)
            .executeAsList()
            .map { it.toChatMessage() }
    }

    override suspend fun getHistoryCount(userId: String): Long {
        return database.knowledgeBaseQueries.getChatHistoryCount(userId).executeAsOne()
    }

    override suspend fun getHistoryCountByConversation(userId: String, conversationId: String): Long {
        return database.knowledgeBaseQueries
            .getChatHistoryCountByConversation(userId, conversationId).executeAsOne()
    }

    override suspend fun deleteHistory(userId: String): Boolean {
        database.knowledgeBaseQueries.deleteChatHistory(userId)
        return true
    }

    override suspend fun deleteHistoryByConversation(conversationId: String) {
        database.knowledgeBaseQueries.deleteChatHistoryByConversation(conversationId)
    }

    override suspend fun getUserMessageList(userId: String): List<String> {
        return database.knowledgeBaseQueries
            .getUserMessages(user_id = userId)
            .executeAsList()
            .map { it.message }
    }
}

private fun com.assistant.db.Chat_messages.toChatMessage() = ChatMessage(
    id = id, userId = user_id, role = role,
    message = message, context = context, timestamp = timestamp
)
