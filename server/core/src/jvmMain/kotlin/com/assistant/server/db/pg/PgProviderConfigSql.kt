package com.assistant.server.db.pg

/**
 * SQL constants for [PgProviderConfigRepository].
 * Separated to keep each file under 200 lines.
 */
internal object PgProviderConfigSql {

    const val SELECT_ALL = """
        SELECT provider_id, name, type, endpoint,
               api_key, model, priority, status
        FROM provider_configs
    """

    const val SELECT_BY_ID = """
        SELECT provider_id, name, type, endpoint,
               api_key, model, priority, status
        FROM provider_configs
        WHERE provider_id = ?
    """

    const val UPSERT = """
        INSERT INTO provider_configs
            (provider_id, name, type, endpoint,
             api_key, model, priority, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (provider_id) DO UPDATE SET
            name = ?,
            type = ?,
            endpoint = ?,
            api_key = ?,
            model = ?,
            priority = ?,
            status = ?
    """

    const val UPDATE_STATUS = """
        UPDATE provider_configs SET status = ? WHERE provider_id = ?
    """
}
