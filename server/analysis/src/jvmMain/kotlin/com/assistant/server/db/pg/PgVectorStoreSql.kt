package com.assistant.server.db.pg

/**
 * SQL constants for PgVectorStoreImpl.
 * Separated to keep each file under 200 lines.
 */
internal object PgVectorStoreSql {

    const val INSERT_SQL = """
        INSERT INTO attachment_chunks
            (ticket_id, attachment_id, filename, chunk_index,
             chunk_text, embedding, created_at, chunk_type)
        VALUES (?, ?, ?, ?, ?, ?::vector, ?, ?)
    """

    const val EXISTS_BY_ATTACHMENT_SQL = """
        SELECT COUNT(*) FROM attachment_chunks
        WHERE attachment_id = ?
    """

    const val SEARCH_ALL_SQL = """
        SELECT id, ticket_id, attachment_id, filename,
               chunk_index, chunk_text, embedding, created_at, chunk_type
        FROM attachment_chunks
        ORDER BY embedding <=> ?::vector
        LIMIT ?
    """

    const val SEARCH_ALL_WITH_SCORE_SQL = """
        SELECT id, ticket_id, attachment_id, filename,
               chunk_index, chunk_text, embedding, created_at, chunk_type,
               (embedding <=> ?::vector) AS distance
        FROM attachment_chunks
        ORDER BY distance
        LIMIT ?
    """

    const val SEARCH_BY_TYPE_SQL = """
        SELECT id, ticket_id, attachment_id, filename,
               chunk_index, chunk_text, embedding, created_at, chunk_type
        FROM attachment_chunks
        WHERE chunk_type = ?
        ORDER BY embedding <=> ?::vector
        LIMIT ?
    """

    const val SEARCH_BY_TYPE_WITH_SCORE_SQL = """
        SELECT id, ticket_id, attachment_id, filename,
               chunk_index, chunk_text, embedding, created_at, chunk_type,
               (embedding <=> ?::vector) AS distance
        FROM attachment_chunks
        WHERE chunk_type = ?
        ORDER BY distance
        LIMIT ?
    """

    const val DELETE_BY_TICKET_SQL = """
        DELETE FROM attachment_chunks WHERE ticket_id = ?
    """

    const val DELETE_BY_PROJECT_SQL = """
        DELETE FROM attachment_chunks
        WHERE ticket_id LIKE ? || '-%'
    """

    const val DELETE_BY_PROJECT_AND_TYPE_SQL = """
        DELETE FROM attachment_chunks
        WHERE ticket_id LIKE ? || '-%' AND chunk_type = ?
    """

    const val FIND_BY_TICKET_SQL = """
        SELECT id, ticket_id, attachment_id, filename,
               chunk_index, chunk_text, embedding, created_at, chunk_type
        FROM attachment_chunks
        WHERE ticket_id = ?
    """
}
