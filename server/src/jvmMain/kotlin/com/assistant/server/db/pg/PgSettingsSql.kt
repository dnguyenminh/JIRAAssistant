package com.assistant.server.db.pg

/**
 * SQL constants for [PgSettingsRepository].
 * Separated to keep each file under 200 lines.
 */
internal object PgSettingsSql {

    const val SELECT_ALL = """
        SELECT setting_key, setting_value FROM app_settings
    """

    const val SELECT_BY_KEY = """
        SELECT setting_value FROM app_settings WHERE setting_key = ?
    """

    const val UPSERT = """
        INSERT INTO app_settings (setting_key, setting_value, updated_at)
        VALUES (?, ?, ?)
        ON CONFLICT (setting_key) DO UPDATE SET
            setting_value = ?,
            updated_at = ?
    """
}
