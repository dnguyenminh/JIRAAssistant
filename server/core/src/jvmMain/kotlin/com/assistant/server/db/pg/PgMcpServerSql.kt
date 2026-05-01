package com.assistant.server.db.pg

/**
 * SQL constants for [PgMcpServerRepository].
 * Separated to keep each file under 200 lines.
 */
internal object PgMcpServerSql {

    const val SELECT_ALL = """
        SELECT id, name, type, command, url, args, env,
               auto_approve, disabled, status,
               created_at, updated_at, internal
        FROM mcp_servers
    """

    const val FIND_BY_ID = """
        SELECT id, name, type, command, url, args, env,
               auto_approve, disabled, status,
               created_at, updated_at, internal
        FROM mcp_servers WHERE id = ?
    """

    const val FIND_BY_NAME = """
        SELECT id, name, type, command, url, args, env,
               auto_approve, disabled, status,
               created_at, updated_at, internal
        FROM mcp_servers WHERE LOWER(name) = LOWER(?)
    """

    const val IS_INTERNAL = """
        SELECT internal FROM mcp_servers WHERE id = ?
    """

    const val INSERT = """
        INSERT INTO mcp_servers
            (id, name, type, command, url, args, env,
             auto_approve, disabled, status,
             created_at, updated_at, internal)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """

    const val UPDATE = """
        UPDATE mcp_servers SET
            name = ?, type = ?, command = ?, url = ?,
            args = ?, env = ?, auto_approve = ?,
            disabled = ?, updated_at = ?
        WHERE id = ?
    """

    const val UPDATE_STATUS = """
        UPDATE mcp_servers SET status = ?, updated_at = ? WHERE id = ?
    """

    const val DELETE_BY_ID = """
        DELETE FROM mcp_servers WHERE id = ?
    """

    const val DELETE_ALL = """
        DELETE FROM mcp_servers
    """
}
