package com.assistant.server.db.pg

/**
 * SQL constants for [PgScanLogRepository].
 * Separated to keep each file under 200 lines.
 */
internal object PgScanLogSql {

    const val INSERT = """
        INSERT INTO scan_log (project_key, ticket_id, status, message, timestamp)
        VALUES (?, ?, ?, ?, ?)
    """

    const val SELECT_BY_PROJECT_KEY = """
        SELECT id, project_key, ticket_id, status, message, timestamp
        FROM scan_log
        WHERE project_key = ?
        ORDER BY timestamp DESC
        LIMIT ?
    """

    const val SELECT_BY_PROJECT_KEY_PAGED = """
        SELECT id, project_key, ticket_id, status, message, timestamp
        FROM scan_log
        WHERE project_key = ?
        ORDER BY timestamp DESC
        LIMIT ? OFFSET ?
    """

    const val COUNT_BY_PROJECT_KEY = """
        SELECT COUNT(*) FROM scan_log WHERE project_key = ?
    """

    const val DELETE_BY_PROJECT_KEY = """
        DELETE FROM scan_log WHERE project_key = ?
    """
}
