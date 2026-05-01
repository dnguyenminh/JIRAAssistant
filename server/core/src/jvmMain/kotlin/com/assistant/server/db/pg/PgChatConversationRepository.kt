package com.assistant.server.db.pg

import com.assistant.chat.ChatConversation
import com.assistant.chat.ChatConversationRepository
import java.sql.ResultSet
import java.time.Instant
import javax.sql.DataSource

/**
 * PostgreSQL-backed implementation of [ChatConversationRepository].
 * Uses the chat_conversations table with index on (user_id, updated_at DESC).
 * Requirements: 6.1, 6.2
 */
class PgChatConversationRepository(
    private val dataSource: DataSource
) : ChatConversationRepository {

    override suspend fun create(userId: String, title: String): ChatConversation {
        val id = generateId()
        val now = Instant.now().toString()
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgChatConversationSql.INSERT).use { ps ->
                    ps.setString(1, id)
                    ps.setString(2, userId)
                    ps.setString(3, title)
                    ps.setString(4, now)
                    ps.setString(5, now)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgChatConversationRepository] create failed: ${e.message}")
        }
        return ChatConversation(id, userId, title, now, now)
    }

    override suspend fun getByUser(userId: String): List<ChatConversation> = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgChatConversationSql.GET_BY_USER).use { ps ->
                ps.setString(1, userId)
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(mapRow(rs)) }
                }
            }
        }
    } catch (e: Exception) {
        println("[PgChatConversationRepository] getByUser failed: ${e.message}")
        emptyList()
    }

    override suspend fun findById(id: String): ChatConversation? = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgChatConversationSql.FIND_BY_ID).use { ps ->
                ps.setString(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) mapRow(rs) else null
                }
            }
        }
    } catch (e: Exception) {
        println("[PgChatConversationRepository] findById failed: ${e.message}")
        null
    }

    override suspend fun updateTitle(id: String, title: String) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgChatConversationSql.UPDATE_TITLE).use { ps ->
                    ps.setString(1, title)
                    ps.setString(2, Instant.now().toString())
                    ps.setString(3, id)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgChatConversationRepository] updateTitle failed: ${e.message}")
        }
    }

    override suspend fun updateTimestamp(id: String) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgChatConversationSql.UPDATE_TIMESTAMP).use { ps ->
                    ps.setString(1, Instant.now().toString())
                    ps.setString(2, id)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgChatConversationRepository] updateTimestamp failed: ${e.message}")
        }
    }

    override suspend fun delete(id: String) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgChatConversationSql.DELETE).use { ps ->
                    ps.setString(1, id)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgChatConversationRepository] delete failed: ${e.message}")
        }
    }

    private fun generateId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }

    private fun mapRow(rs: ResultSet): ChatConversation = ChatConversation(
        id = rs.getString("id"),
        userId = rs.getString("user_id"),
        title = rs.getString("title"),
        createdAt = rs.getString("created_at"),
        updatedAt = rs.getString("updated_at")
    )
}
