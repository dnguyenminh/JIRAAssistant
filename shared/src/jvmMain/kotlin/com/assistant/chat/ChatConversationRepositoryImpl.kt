package com.assistant.chat

import com.assistant.db.JiraDatabase
import kotlinx.datetime.Clock

/**
 * JVM implementation of [ChatConversationRepository].
 * Requirements: 19.47, 19.48
 */
class ChatConversationRepositoryImpl(
    private val database: JiraDatabase
) : ChatConversationRepository {

    override suspend fun create(userId: String, title: String): ChatConversation {
        val id = generateId()
        val now = Clock.System.now().toString()
        database.knowledgeBaseQueries.insertConversation(
            id = id, user_id = userId, title = title,
            created_at = now, updated_at = now
        )
        return ChatConversation(id, userId, title, now, now)
    }

    override suspend fun getByUser(userId: String): List<ChatConversation> {
        return database.knowledgeBaseQueries
            .getConversationsByUser(userId)
            .executeAsList()
            .map { it.toConversation() }
    }

    override suspend fun findById(id: String): ChatConversation? {
        return database.knowledgeBaseQueries
            .findConversationById(id)
            .executeAsOneOrNull()
            ?.toConversation()
    }

    override suspend fun updateTitle(id: String, title: String) {
        val now = Clock.System.now().toString()
        database.knowledgeBaseQueries.updateConversationTitle(title, now, id)
    }

    override suspend fun updateTimestamp(id: String) {
        val now = Clock.System.now().toString()
        database.knowledgeBaseQueries.updateConversationTimestamp(now, id)
    }

    override suspend fun delete(id: String) {
        database.knowledgeBaseQueries.deleteConversation(id)
    }

    private fun generateId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }
}

private fun com.assistant.db.Chat_conversations.toConversation() =
    ChatConversation(id, user_id, title, created_at, updated_at)
