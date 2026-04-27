package com.assistant.server.db.pg

/**
 * SQL constants for [PgChatConversationRepository].
 * Separated to keep each file under 200 lines.
 */
internal object PgChatConversationSql {

    const val INSERT = """
        INSERT INTO chat_conversations (id, user_id, title, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?)
    """

    const val GET_BY_USER = """
        SELECT id, user_id, title, created_at, updated_at
        FROM chat_conversations
        WHERE user_id = ?
        ORDER BY updated_at DESC
    """

    const val FIND_BY_ID = """
        SELECT id, user_id, title, created_at, updated_at
        FROM chat_conversations
        WHERE id = ?
    """

    const val UPDATE_TITLE = """
        UPDATE chat_conversations SET title = ?, updated_at = ? WHERE id = ?
    """

    const val UPDATE_TIMESTAMP = """
        UPDATE chat_conversations SET updated_at = ? WHERE id = ?
    """

    const val DELETE = """
        DELETE FROM chat_conversations WHERE id = ?
    """
}
