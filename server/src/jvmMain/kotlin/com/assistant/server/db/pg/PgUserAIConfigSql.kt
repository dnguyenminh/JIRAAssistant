package com.assistant.server.db.pg

/**
 * SQL constants for [PgUserAIConfigRepository].
 * Separated to keep each file under 200 lines.
 */
internal object PgUserAIConfigSql {

    const val FIND_BY_USER_ID = """
        SELECT user_id, skills_json, workflow_json,
               instructions_json, rules_json, updated_at
        FROM user_ai_config
        WHERE user_id = ?
    """

    const val UPSERT = """
        INSERT INTO user_ai_config
            (user_id, skills_json, workflow_json,
             instructions_json, rules_json, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id) DO UPDATE SET
            skills_json = EXCLUDED.skills_json,
            workflow_json = EXCLUDED.workflow_json,
            instructions_json = EXCLUDED.instructions_json,
            rules_json = EXCLUDED.rules_json,
            updated_at = EXCLUDED.updated_at
    """
}
