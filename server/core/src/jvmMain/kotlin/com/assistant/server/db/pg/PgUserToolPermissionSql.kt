package com.assistant.server.db.pg

/**
 * SQL constants for [PgUserToolPermissionRepository].
 * Separated to keep each file under 200 lines.
 */
internal object PgUserToolPermissionSql {

    const val FIND_BY_USER_ID = """
        SELECT permissions_json FROM user_tool_permissions WHERE user_id = ?
    """

    const val UPSERT = """
        INSERT INTO user_tool_permissions (user_id, permissions_json, updated_at)
        VALUES (?, ?, ?)
        ON CONFLICT (user_id) DO UPDATE SET
            permissions_json = EXCLUDED.permissions_json,
            updated_at = EXCLUDED.updated_at
    """

    const val DELETE_BY_USER_ID = """
        DELETE FROM user_tool_permissions WHERE user_id = ?
    """
}
