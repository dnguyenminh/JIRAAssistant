package com.assistant.server.db.pg

import com.assistant.settings.SettingsRepository
import javax.sql.DataSource

/**
 * PostgreSQL-backed implementation of [SettingsRepository].
 * Uses the app_settings table as a key-value store.
 * Requirements: 6.1, 6.2
 */
class PgSettingsRepository(
    private val dataSource: DataSource
) : SettingsRepository {

    override suspend fun getAll(): Map<String, String> = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgSettingsSql.SELECT_ALL).use { ps ->
                ps.executeQuery().use { rs ->
                    buildMap {
                        while (rs.next()) {
                            put(rs.getString("setting_key"), rs.getString("setting_value"))
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        println("[PgSettingsRepository] getAll failed: ${e.message}")
        emptyMap()
    }

    override suspend fun get(key: String): String? = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgSettingsSql.SELECT_BY_KEY).use { ps ->
                ps.setString(1, key)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("setting_value") else null
                }
            }
        }
    } catch (e: Exception) {
        println("[PgSettingsRepository] get failed: ${e.message}")
        null
    }

    override suspend fun put(key: String, value: String) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgSettingsSql.UPSERT).use { ps ->
                    ps.setString(1, key)
                    ps.setString(2, value)
                    ps.setString(3, now())
                    ps.setString(4, value)
                    ps.setString(5, now())
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgSettingsRepository] put failed: ${e.message}")
        }
    }

    override suspend fun putAll(settings: Map<String, String>) {
        try {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    conn.prepareStatement(PgSettingsSql.UPSERT).use { ps ->
                        settings.forEach { (key, value) ->
                            ps.setString(1, key)
                            ps.setString(2, value)
                            ps.setString(3, now())
                            ps.setString(4, value)
                            ps.setString(5, now())
                            ps.addBatch()
                        }
                        ps.executeBatch()
                    }
                    conn.commit()
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        } catch (e: Exception) {
            println("[PgSettingsRepository] putAll failed: ${e.message}")
        }
    }

    private fun now(): String = java.time.Instant.now().toString()
}
