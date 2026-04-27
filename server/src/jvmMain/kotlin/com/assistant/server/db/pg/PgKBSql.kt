package com.assistant.server.db.pg

/**
 * SQL constants for [PgKBRepository].
 * Separated to keep each file under 200 lines.
 */
internal object PgKBSql {

    const val FIND_BY_TICKET_ID = """
        SELECT ticket_id, requirement_summary, evolution_history,
               scrum_points, confidence_score, rationale,
               similar_ticket_refs, created_at, updated_at,
               deep_analysis_json
        FROM kb_records
        WHERE ticket_id = ?
    """

    const val UPSERT_RECORD = """
        INSERT INTO kb_records
            (ticket_id, requirement_summary, evolution_history,
             scrum_points, confidence_score, rationale,
             similar_ticket_refs, created_at, updated_at,
             deep_analysis_json)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (ticket_id) DO UPDATE SET
            requirement_summary = ?,
            evolution_history = ?,
            scrum_points = ?,
            confidence_score = ?,
            rationale = ?,
            similar_ticket_refs = ?,
            updated_at = ?,
            deep_analysis_json = ?
    """

    const val UPSERT_GRAPH = """
        INSERT INTO graph_data (project_key, graph_json, updated_at)
        VALUES (?, ?, ?)
        ON CONFLICT (project_key) DO UPDATE SET
            graph_json = ?,
            updated_at = ?
    """

    const val FIND_GRAPH = """
        SELECT graph_json FROM graph_data WHERE project_key = ?
    """
}
