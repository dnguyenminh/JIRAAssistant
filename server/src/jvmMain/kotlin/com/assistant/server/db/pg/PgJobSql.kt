package com.assistant.server.db.pg

/**
 * SQL constants for [PgJobRepository] (Req 2.1).
 */
internal object PgJobSql {

    const val INSERT = """
        INSERT INTO generation_jobs
            (job_id, ticket_id, document_type, status, progress_percent,
             phase, chain_id, created_by, created_at, updated_at, error_message, started_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """

    const val FIND_BY_ID = """
        SELECT job_id, ticket_id, document_type, status, progress_percent,
               phase, chain_id, created_by, created_at, updated_at, error_message, started_at
        FROM generation_jobs WHERE job_id = ?::uuid
    """

    const val FIND_ACTIVE_BY_TICKET_AND_TYPE = """
        SELECT job_id, ticket_id, document_type, status, progress_percent,
               phase, chain_id, created_by, created_at, updated_at, error_message, started_at
        FROM generation_jobs
        WHERE ticket_id = ? AND document_type = ? AND status IN ('QUEUED','RUNNING')
        LIMIT 1
    """

    const val FIND_ACTIVE_BY_TICKET = """
        SELECT job_id, ticket_id, document_type, status, progress_percent,
               phase, chain_id, created_by, created_at, updated_at, error_message, started_at
        FROM generation_jobs
        WHERE ticket_id = ? AND status IN ('QUEUED','RUNNING','PAUSED')
        ORDER BY created_at
    """

    const val FIND_BY_CHAIN = """
        SELECT job_id, ticket_id, document_type, status, progress_percent,
               phase, chain_id, created_by, created_at, updated_at, error_message, started_at
        FROM generation_jobs WHERE chain_id = ?::uuid ORDER BY created_at
    """

    const val UPDATE_STATUS = """
        UPDATE generation_jobs
        SET status = ?, progress_percent = ?, phase = ?,
            error_message = ?, updated_at = ?, started_at = COALESCE(?, started_at)
        WHERE job_id = ?::uuid
    """

    const val FIND_RUNNING = """
        SELECT job_id, ticket_id, document_type, status, progress_percent,
               phase, chain_id, created_by, created_at, updated_at, error_message, started_at
        FROM generation_jobs WHERE status = 'RUNNING'
    """
}
