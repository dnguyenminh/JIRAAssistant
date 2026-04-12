package com.assistant.settings

import com.assistant.db.JiraDatabase
import kotlinx.datetime.Clock

/**
 * JVM implementation of [SettingsRepository] backed by SQLDelight [JiraDatabase].
 * Uses the app_settings table as a key-value store.
 */
class SettingsRepositoryImpl(
    private val database: JiraDatabase
) : SettingsRepository {

    override suspend fun getAll(): Map<String, String> {
        return database.knowledgeBaseQueries.getAllSettings()
            .executeAsList()
            .associate { it.setting_key to it.setting_value }
    }

    override suspend fun get(key: String): String? {
        return database.knowledgeBaseQueries.findSettingByKey(key)
            .executeAsOneOrNull()
            ?.setting_value
    }

    override suspend fun put(key: String, value: String) {
        database.knowledgeBaseQueries.upsertSetting(
            setting_key = key,
            setting_value = value,
            updated_at = Clock.System.now().toString()
        )
    }

    override suspend fun putAll(settings: Map<String, String>) {
        database.transaction {
            settings.forEach { (key, value) ->
                database.knowledgeBaseQueries.upsertSetting(
                    setting_key = key,
                    setting_value = value,
                    updated_at = Clock.System.now().toString()
                )
            }
        }
    }
}
