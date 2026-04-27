package com.assistant.server.db.pg

import com.assistant.chat.ChatMessage
import com.assistant.chat.ChatRepository
import java.sql.ResultSet
import java.sql.Statement
import java.time.Instant
import javax.sql.DataSource

/**
 * PostgreSQL-backed implementation of [ChatRepository].
 * Supports conversation_id column for multi-conversation.
 * Requirements: 6.1, 6.2
 */
class PgChatRepository(
    private val dataSource: DataSource
) : ChatRepository {

    override suspend fun saveMessage(
        userId: String, role: String, message: String, context: String?
    ): Long = saveMessageWithConversation(userId, "", role, message, context)

    override suspend fun saveMessageWithConversation(
        userId: String, conversationId: String,
        role: String, message: String, context: String?
    ): Long = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgChatSql.INSERT_MESSAGE, Statement.RETURN_GENERATED_KEYS).use { ps ->
                ps.setString(1, userId)
                ps.setString(2, conversationId)
                ps.setString(3, role)
                ps.setString(4, message)
                ps.setString(5, context)
                ps.setString(6, Instant.now().toString())
                ps.executeUpdate()
                ps.generatedKeys.use { rs -> if (rs.next()) rs.getLong(1) else 0L }
            }
        }
    } catch (e: Exception) {
        println("[PgChatRepository] saveMessage failed: ${e.message}")
        0L
    }

    override suspend fun getHistory(
        userId: String, page: Int, size: Int
    ): List<ChatMessage> = try {
        val offset = (page * size).toLong()
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgChatSql.GET_HISTORY).use { ps ->
                ps.setString(1, userId)
                ps.setLong(2, size.toLong())
                ps.setLong(3, offset)
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(mapRow(rs)) }
                }
            }
        }
    } catch (e: Exception) {
        println("[PgChatRepository] getHistory failed: ${e.message}")
        emptyList()
    }

    override suspend fun getHistoryByConversation(
        userId: String, conversationId: String, page: Int, size: Int
    ): List<ChatMessage> = try {
        val offset = (page * size).toLong()
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgChatSql.GET_HISTORY_BY_CONVERSATION).use { ps ->
                ps.setString(1, userId)
                ps.setString(2, conversationId)
                ps.setLong(3, size.toLong())
                ps.setLong(4, offset)
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(mapRow(rs)) }
                }
            }
        }
    } catch (e: Exception) {
        println("[PgChatRepository] getHistoryByConversation failed: ${e.message}")
        emptyList()
    }

    override suspend fun getHistoryCount(userId: String): Long = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgChatSql.COUNT_HISTORY).use { ps ->
                ps.setString(1, userId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.getLong(1) else 0L
                }
            }
        }
    } catch (e: Exception) {
        println("[PgChatRepository] getHistoryCount failed: ${e.message}")
        0L
    }

    override suspend fun getHistoryCountByConversation(userId: String, conversationId: String): Long = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgChatSql.COUNT_HISTORY_BY_CONVERSATION).use { ps ->
                ps.setString(1, userId)
                ps.setString(2, conversationId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.getLong(1) else 0L
                }
            }
        }
    } catch (e: Exception) {
        println("[PgChatRepository] getHistoryCountByConversation failed: ${e.message}")
        0L
    }

    override suspend fun deleteHistory(userId: String): Boolean = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgChatSql.DELETE_HISTORY).use { ps ->
                ps.setString(1, userId)
                ps.executeUpdate()
            }
        }
        true
    } catch (e: Exception) {
        println("[PgChatRepository] deleteHistory failed: ${e.message}")
        false
    }

    override suspend fun deleteHistoryByConversation(conversationId: String) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgChatSql.DELETE_BY_CONVERSATION).use { ps ->
                    ps.setString(1, conversationId)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgChatRepository] deleteHistoryByConversation failed: ${e.message}")
        }
    }

    override suspend fun getUserMessageList(userId: String): List<String> = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgChatSql.GET_USER_MESSAGES).use { ps ->
                ps.setString(1, userId)
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.getString("message")) }
                }
            }
        }
    } catch (e: Exception) {
        println("[PgChatRepository] getUserMessageList failed: ${e.message}")
        emptyList()
    }

    private fun mapRow(rs: ResultSet): ChatMessage = ChatMessage(
        id = rs.getLong("id"),
        userId = rs.getString("user_id"),
        role = rs.getString("role"),
        message = rs.getString("message"),
        context = rs.getString("context"),
        timestamp = rs.getString("timestamp")
    )
}
