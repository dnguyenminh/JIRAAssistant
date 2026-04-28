package com.assistant.server.db.pg

/**
 * SQL constants for [PgScanStateRepository].
 * Separated to keep each file under 200 lines.
 */
internal object PgScanStateSql {

    const val FIND_BY_PROJECT_KEY = """
        SELECT project_key, status, total_tickets, processed_count,
               current_ticket_id, ticket_ids, started_at, updated_at
        FROM scan_states
        WHERE project_key = ?
    """

    const val FIND_ALL_SCANNING = """
        SELECT project_key, status, total_tickets, processed_count,
               current_ticket_id, ticket_ids, started_at, updated_at
        FROM scan_states
        WHERE status = 'SCANNING'
    """

    const val UPSERT = """
        INSERT INTO scan_states
            (project_key, status, total_tickets, processed_count,
             current_ticket_id, ticket_ids, started_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (project_key) DO UPDATE SET
            status = EXCLUDED.status,
            total_tickets = EXCLUDED.total_tickets,
            processed_count = EXCLUDED.processed_count,
            current_ticket_id = EXCLUDED.current_ticket_id,
            ticket_ids = EXCLUDED.ticket_ids,
            started_at = EXCLUDED.started_at,
            updated_at = EXCLUDED.updated_at
    """

    const val DELETE = """
        DELETE FROM scan_states WHERE project_key = ?
    """
}
