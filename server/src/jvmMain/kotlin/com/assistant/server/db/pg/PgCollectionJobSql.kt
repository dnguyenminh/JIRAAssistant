package com.assistant.server.db.pg

/**
 * SQL constants for [PgCollectionJobRepository] (Req 13.3, 13.5, 13.6, 14.7).
 */
internal object PgCollectionJobSql {

    private const val COLUMNS = """
        job_id, parent_ticket_id, job_type, status,
        total_items, completed_items, failed_items,
        items_json, created_at, updated_at, version
    """

    const val INSERT = """
        INSERT INTO collection_jobs ($COLUMNS)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """

    const val FIND_BY_ID = """
        SELECT $COLUMNS FROM collection_jobs WHERE job_id = ?
    """

    const val FIND_BY_PARENT_TICKET = """
        SELECT $COLUMNS
        FROM collection_jobs
        WHERE parent_ticket_id = ?
        ORDER BY created_at DESC
    """

    const val FIND_ACTIVE = """
        SELECT $COLUMNS
        FROM collection_jobs
        WHERE status IN ('QUEUED', 'RUNNING')
        ORDER BY created_at ASC
    """

    const val UPDATE_STATUS = """
        UPDATE collection_jobs
        SET status = ?, updated_at = ?, version = version + 1
        WHERE job_id = ? AND version = ?
    """

    const val UPDATE_PROGRESS = """
        UPDATE collection_jobs
        SET completed_items = ?, failed_items = ?, items_json = ?,
            updated_at = ?, version = version + 1
        WHERE job_id = ? AND version = ?
    """

    const val DELETE = """
        DELETE FROM collection_jobs WHERE job_id = ?
    """
}
