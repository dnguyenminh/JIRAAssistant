package com.assistant.chat

import com.assistant.db.JiraDatabase
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/** Shared Json instance for tool permissions serialization. */
private val permissionsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * JVM implementation of [UserToolPermissionRepository].
 * Persists per-user MCP tool permissions as JSON in SQLDelight.
 * Requirements: 3.1, 3.2
 */
class UserToolPermissionRepositoryImpl(
    private val database: JiraDatabase
) : UserToolPermissionRepository {

    override suspend fun findByUserId(userId: String): Map<String, String>? {
        val row = database.knowledgeBaseQueries
            .findUserToolPermissions(userId)
            .executeAsOneOrNull() ?: return null
        return decodePermissions(row.permissions_json)
    }

    override suspend fun save(userId: String, permissions: Map<String, String>) {
        val now = Clock.System.now().toString()
        val json = permissionsJson.encodeToString(
            kotlinx.serialization.serializer<Map<String, String>>(),
            permissions
        )
        database.knowledgeBaseQueries.upsertUserToolPermissions(
            user_id = userId,
            permissions_json = json,
            updated_at = now
        )
    }

    override suspend fun delete(userId: String) {
        database.knowledgeBaseQueries.deleteUserToolPermissions(userId)
    }

    private fun decodePermissions(json: String): Map<String, String> {
        return runCatching {
            permissionsJson.decodeFromString<Map<String, String>>(json)
        }.getOrDefault(emptyMap())
    }
}
