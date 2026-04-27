package com.assistant.server.db.pg

/**
 * SQL constants for [PgDocumentRepository].
 * Updated for versioning + approval (Req 7.1, 7.2).
 */
internal object PgDocumentSql {

    const val INSERT = """
        INSERT INTO generated_documents
            (ticket_id, document_type, markdown_content,
             generated_at, source_ticket_ids, attachment_sources,
             ai_provider_used, approval_status, version_number,
             reject_reason, reviewed_by, reviewed_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """

    private const val ALL_COLS = """
        id, ticket_id, document_type, markdown_content,
        generated_at, source_ticket_ids, attachment_sources,
        ai_provider_used, approval_status, version_number,
        reject_reason, reviewed_by, reviewed_at
    """

    const val FIND_BY_TICKET_ID = """
        SELECT $ALL_COLS FROM generated_documents
        WHERE ticket_id = ? ORDER BY generated_at DESC
    """

    const val FIND_BY_TICKET_ID_AND_TYPE = """
        SELECT $ALL_COLS FROM generated_documents
        WHERE ticket_id = ? AND document_type = ?
        ORDER BY COALESCE(version_number, 0) DESC, generated_at DESC
        LIMIT 1
    """

    const val FIND_LATEST = """
        SELECT $ALL_COLS FROM generated_documents
        WHERE ticket_id = ? AND document_type = ?
          AND approval_status IN ('APPROVED','DRAFT')
        ORDER BY
          CASE approval_status WHEN 'APPROVED' THEN 0 ELSE 1 END,
          COALESCE(version_number, 0) DESC,
          generated_at DESC
        LIMIT 1
    """

    const val FIND_ALL_VERSIONS = """
        SELECT document_type, generated_at, ai_provider_used,
               approval_status, version_number,
               EXISTS(SELECT 1 FROM generated_documents d2
                      WHERE d2.ticket_id = generated_documents.ticket_id
                        AND d2.document_type = generated_documents.document_type
                        AND d2.approval_status = 'DRAFT') AS has_draft
        FROM generated_documents
        WHERE ticket_id = ? AND document_type = ? AND approval_status = 'APPROVED'
        ORDER BY version_number DESC
    """

    const val FIND_BY_VERSION = """
        SELECT $ALL_COLS FROM generated_documents
        WHERE ticket_id = ? AND document_type = ? AND version_number = ?
    """

    const val UPDATE_APPROVAL = """
        UPDATE generated_documents
        SET approval_status = ?, reviewed_by = ?, reviewed_at = ?,
            reject_reason = ?, version_number = ?
        WHERE id = ?
    """

    const val NEXT_VERSION = """
        SELECT COALESCE(MAX(version_number), 0) + 1
        FROM generated_documents
        WHERE ticket_id = ? AND document_type = ?
    """

    const val FIND_LATEST_DRAFT = """
        SELECT $ALL_COLS FROM generated_documents
        WHERE ticket_id = ? AND document_type = ?
          AND approval_status = 'DRAFT'
        ORDER BY generated_at DESC
        LIMIT 1
    """

    const val FIND_BY_ID = """
        SELECT $ALL_COLS FROM generated_documents WHERE id = ?
    """

    const val LIST_META_BY_TICKET_ID = """
        SELECT d.document_type, d.generated_at, d.ai_provider_used,
               d.approval_status, d.version_number,
               EXISTS(SELECT 1 FROM generated_documents d2
                      WHERE d2.ticket_id = d.ticket_id
                        AND d2.document_type = d.document_type
                        AND d2.approval_status = 'DRAFT') AS has_draft
        FROM generated_documents d
        WHERE d.ticket_id = ?
          AND d.id = (
              SELECT d3.id FROM generated_documents d3
              WHERE d3.ticket_id = d.ticket_id
                AND d3.document_type = d.document_type
              ORDER BY
                CASE d3.approval_status WHEN 'APPROVED' THEN 0 ELSE 1 END,
                COALESCE(d3.version_number, 0) DESC,
                d3.generated_at DESC
              LIMIT 1
          )
        ORDER BY d.generated_at DESC
    """
}
