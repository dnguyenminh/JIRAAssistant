package com.assistant.server.db.pg

import com.assistant.chat.UserToolPermissionRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.time.Instant
import javax.sql.DataSource

/** Shared Json instance for tool permissions serialization. */
private val permissionsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * PostgreSQL-backed implementation of [UserToolPermissionRepository].
 * Persists per-user MCP tool permissions as JSON.
 * Requirements: 6.1, 6.2
 */
class PgUserToolPermissionRepository(
    private val dataSource: DataSource
) : UserToolPermissionRepository {

    override suspend fun findByUserId(userId: String): Map<String, String>? = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgUserToolPermissionSql.FIND_BY_USER_ID).use { ps ->
                ps.setString(1, userId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) decodePermissions(rs.getString("permissions_json"))
                    else null
                }
            }
        }
    } catch (e: Exception) {
        println("[PgUserToolPermissionRepository] findByUserId failed: ${e.message}")
        null
    }

    override suspend fun save(userId: String, permissions: Map<String, String>) {
        try {
            val now = Instant.now().toString()
            val json = permissionsJson.encodeToString(
                serializer<Map<String, String>>(), permissions
            )
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgUserToolPermissionSql.UPSERT).use { ps ->
                    ps.setString(1, userId)
                    ps.setString(2, json)
                    ps.setString(3, now)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgUserToolPermissionRepository] save failed: ${e.message}")
        }
    }

    override suspend fun delete(userId: String) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgUserToolPermissionSql.DELETE_BY_USER_ID).use { ps ->
                    ps.setString(1, userId)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgUserToolPermissionRepository] delete failed: ${e.message}")
        }
    }

    private fun decodePermissions(json: String): Map<String, String> =
        runCatching { permissionsJson.decodeFromString<Map<String, String>>(json) }
            .getOrDefault(emptyMap())
}
