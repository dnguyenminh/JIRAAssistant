package com.assistant.server.db.pg

/**
 * SQL constants for [PgChatRepository].
 * Separated to keep each file under 200 lines.
 */
internal object PgChatSql {

    const val INSERT_MESSAGE = """
        INSERT INTO chat_messages
            (user_id, conversation_id, role, message, context, timestamp)
        VALUES (?, ?, ?, ?, ?, ?)
    """

    const val GET_HISTORY = """
        SELECT id, user_id, role, message, context, timestamp
        FROM chat_messages
        WHERE user_id = ?
        ORDER BY timestamp ASC
        LIMIT ? OFFSET ?
    """

    const val GET_HISTORY_BY_CONVERSATION = """
        SELECT id, user_id, role, message, context, timestamp
        FROM chat_messages
        WHERE user_id = ? AND conversation_id = ?
        ORDER BY timestamp ASC
        LIMIT ? OFFSET ?
    """

    const val COUNT_HISTORY = """
        SELECT COUNT(*) FROM chat_messages WHERE user_id = ?
    """

    const val COUNT_HISTORY_BY_CONVERSATION = """
        SELECT COUNT(*) FROM chat_messages WHERE user_id = ? AND conversation_id = ?
    """

    const val DELETE_HISTORY = """
        DELETE FROM chat_messages WHERE user_id = ?
    """

    const val DELETE_BY_CONVERSATION = """
        DELETE FROM chat_messages WHERE conversation_id = ?
    """

    const val GET_USER_MESSAGES = """
        SELECT message FROM chat_messages
        WHERE user_id = ? AND role = 'user'
        ORDER BY timestamp ASC
    """
}
